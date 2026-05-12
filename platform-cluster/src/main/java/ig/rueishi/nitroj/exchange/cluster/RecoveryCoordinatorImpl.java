package ig.rueishi.nitroj.exchange.cluster;

import ig.rueishi.nitroj.exchange.common.Ids;
import ig.rueishi.nitroj.exchange.common.OrderStatus;
import ig.rueishi.nitroj.exchange.messages.BalanceQueryRequestEncoder;
import ig.rueishi.nitroj.exchange.messages.BalanceQueryResponseDecoder;
import ig.rueishi.nitroj.exchange.messages.BooleanType;
import ig.rueishi.nitroj.exchange.messages.CancelOrderCommandEncoder;
import ig.rueishi.nitroj.exchange.messages.ExecType;
import ig.rueishi.nitroj.exchange.messages.ExecutionEventDecoder;
import ig.rueishi.nitroj.exchange.messages.FillEventEncoder;
import ig.rueishi.nitroj.exchange.messages.MessageHeaderEncoder;
import ig.rueishi.nitroj.exchange.messages.OrderStatusQueryCommandEncoder;
import ig.rueishi.nitroj.exchange.messages.RecoveryCompleteEventEncoder;
import ig.rueishi.nitroj.exchange.messages.Side;
import ig.rueishi.nitroj.exchange.messages.VenueStatus;
import ig.rueishi.nitroj.exchange.messages.VenueStatusEventDecoder;
import ig.rueishi.nitroj.exchange.execution.ParentOrderRegistry;
import ig.rueishi.nitroj.exchange.execution.ParentOrderState;
import ig.rueishi.nitroj.exchange.order.OrderManager;
import ig.rueishi.nitroj.exchange.order.OrderState;
import io.aeron.ExclusivePublication;
import io.aeron.Image;
import io.aeron.cluster.service.Cluster;
import java.nio.ByteOrder;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.Objects;
import org.agrona.DirectBuffer;
import org.agrona.concurrent.UnsafeBuffer;

/**
 * Recovery state machine for gateway reconnect reconciliation.
 *
 * <p>The coordinator owns one state slot per venue. A disconnect sets the risk
 * recovery lock and waits for reconnect. A reconnect queries live orders, starts
 * the order-query timer, then moves to balance reconciliation and finally clears
 * the lock with a RecoveryCompleteEvent. The implementation is single-threaded
 * with the rest of cluster business logic, so state changes are plain array
 * writes rather than synchronized operations.</p>
 *
 * <p>V13 parent-order recovery is integrated as an optional sibling state owner.
 * Parent snapshots are copied through {@link ParentOrderRegistry.Snapshot}: the
 * snapshot contains active parent slots, parent fill totals, terminal reasons,
 * timer correlation IDs, active child links, and registry counters. During
 * reconciliation, order-manager child state is loaded first, then parent
 * registry state, and only then are venue order-status reports applied. A child
 * with a parent ID but no recoverable parent is treated as unreconciled risk and
 * activates the kill switch with {@code parent_reconciliation_failed}. A missing
 * parent/child registry link is counted and repaired deterministically when the
 * parent itself exists.</p>
 *
 * <p>V14 venue-indifferent hedge parents use the same recovery contract whether
 * the active execution plugin is ParallelVenue or SOR: parent snapshots preserve
 * the strategy/execution IDs and active child links, order-manager snapshots
 * preserve venue-specific child state, and reconciliation counters expose
 * repaired links, synthetic fills, partial recoveries, hedge-pending recoveries,
 * and kill-switch activations. Recovery deliberately does not recompute routing
 * plans or re-price residual hedge quantity; after snapshot/load the owning
 * execution plugin resumes from its own bounded snapshot and the coordinator
 * only reconciles venue truth against the recovered order and parent state.</p>
 */
public final class RecoveryCoordinatorImpl implements RecoveryCoordinator {
    public enum RecoveryState {
        IDLE,
        AWAITING_RECONNECT,
        QUERYING_ORDERS,
        AWAITING_BALANCE,
        RECONCILING,
    }

    static final long BALANCE_TOLERANCE_SCALED = 10_000L;
    static final long ORDER_QUERY_TIMEOUT_MICROS = 10_000_000L;
    static final long BALANCE_QUERY_TIMEOUT_MICROS = 5_000_000L;

    private final RecoveryState[] venueState = new RecoveryState[Ids.MAX_VENUES + 1];
    private final RiskEngine riskEngine;
    private final OrderManager orderManager;
    private final PortfolioEngine portfolioEngine;
    private final ParentOrderRegistry parentOrderRegistry;
    private final RecoverySink sink;
    private final MessageHeaderEncoder headerEncoder = new MessageHeaderEncoder();
    private final OrderStatusQueryCommandEncoder orderStatusQueryEncoder = new OrderStatusQueryCommandEncoder();
    private final BalanceQueryRequestEncoder balanceQueryRequestEncoder = new BalanceQueryRequestEncoder();
    private final RecoveryCompleteEventEncoder recoveryCompleteEncoder = new RecoveryCompleteEventEncoder();
    private final FillEventEncoder fillEventEncoder = new FillEventEncoder();
    private final CancelOrderCommandEncoder cancelOrderEncoder = new CancelOrderCommandEncoder();
    private final UnsafeBuffer buffer = new UnsafeBuffer(new byte[512]);
    private final UnsafeBuffer snapshotBuffer = new UnsafeBuffer(new byte[Ids.MAX_VENUES + 1]);
    private Cluster cluster;
    private long lastScheduledTimerId;
    private long lastCanceledTimerId;
    private long parentSnapshotWrites;
    private long parentSnapshotLoads;
    private long parentReconciliationMismatches;
    private long parentSyntheticFills;
    private long parentPartialRecoveries;
    private long parentHedgePendingRecoveries;
    private long unreconciledParentKillSwitches;

    public RecoveryCoordinatorImpl(
        final OrderManager orderManager,
        final PortfolioEngine portfolioEngine,
        final RiskEngine riskEngine
    ) {
        this(orderManager, portfolioEngine, riskEngine, null, RecoverySink.NO_OP);
    }

    public RecoveryCoordinatorImpl(
        final OrderManager orderManager,
        final PortfolioEngine portfolioEngine,
        final RiskEngine riskEngine,
        final ParentOrderRegistry parentOrderRegistry
    ) {
        this(orderManager, portfolioEngine, riskEngine, parentOrderRegistry, RecoverySink.NO_OP);
    }

    RecoveryCoordinatorImpl(
        final OrderManager orderManager,
        final PortfolioEngine portfolioEngine,
        final RiskEngine riskEngine,
        final RecoverySink sink
    ) {
        this(orderManager, portfolioEngine, riskEngine, null, sink);
    }

    RecoveryCoordinatorImpl(
        final OrderManager orderManager,
        final PortfolioEngine portfolioEngine,
        final RiskEngine riskEngine,
        final ParentOrderRegistry parentOrderRegistry,
        final RecoverySink sink
    ) {
        this.orderManager = Objects.requireNonNull(orderManager, "orderManager");
        this.portfolioEngine = Objects.requireNonNull(portfolioEngine, "portfolioEngine");
        this.riskEngine = Objects.requireNonNull(riskEngine, "riskEngine");
        this.parentOrderRegistry = parentOrderRegistry;
        this.sink = Objects.requireNonNull(sink, "sink");
        Arrays.fill(venueState, RecoveryState.IDLE);
    }

    /**
     * Applies a venue status transition from the gateway.
     *
     * <p>Disconnects always restart recovery for that venue and set the risk
     * lock. Connected events only start reconciliation if the venue was waiting
     * for reconnect; an idle CONNECTED heartbeat is ignored.</p>
     */
    @Override
    public void onVenueStatus(final VenueStatusEventDecoder decoder) {
        onVenueStatus(decoder.venueId(), decoder.status());
    }

    public void onVenueStatus(final int venueId, final VenueStatus status) {
        if (status == VenueStatus.DISCONNECTED) {
            venueState[venueId] = RecoveryState.AWAITING_RECONNECT;
            riskEngine.setRecoveryLock(venueId, true);
        } else if (status == VenueStatus.CONNECTED && venueState[venueId] == RecoveryState.AWAITING_RECONNECT) {
            startOrderQuery(venueId);
        }
    }

    /**
     * Reconciles one order-status response.
     *
     * <p>The execution type is used as the venue's view of the order. Matching
     * open orders need no action, venue-canceled orders are force-canceled
     * internally, venue-filled orders synthesize a FillEvent for audit and update
     * portfolio, and unknown internal orders are treated as venue orphans that
     * must be canceled.</p>
     */
    @Override
    public void reconcileOrder(final ExecutionEventDecoder decoder) {
        final int venueId = decoder.venueId();
        final OrderState internal = orderManager.getOrder(decoder.clOrdId());
        if (internal == null) {
            if (decoder.execType() == ExecType.NEW || decoder.execType() == ExecType.ORDER_STATUS) {
                publishCancel(decoder.clOrdId(), venueId, decoder.instrumentId(), decoder.side(), decoder.fillQtyScaled(), "");
            }
            return;
        }
        if (!reconcileParentOrder(decoder, internal)) {
            return;
        }

        if (decoder.execType() == ExecType.CANCELED) {
            orderManager.forceTransitionToCanceled(decoder.clOrdId());
        } else if (isFill(decoder.execType())) {
            publishSyntheticFill(decoder, internal);
            portfolioEngine.onFill(decoder);
        }
        startBalanceQuery(venueId, decoder.instrumentId());
    }

    @Override
    public void onBalanceResponse(final BalanceQueryResponseDecoder decoder) {
        reconcileBalance(decoder.venueId(), decoder.instrumentId(), decoder.balanceScaled());
    }

    public void reconcileBalance(final int venueId, final int instrumentId, final long venueBalanceScaled) {
        final long internalBalance = portfolioEngine.getNetQtyScaled(venueId, instrumentId);
        final long discrepancy = Math.abs(venueBalanceScaled - internalBalance);
        if (discrepancy <= BALANCE_TOLERANCE_SCALED) {
            portfolioEngine.adjustPosition(venueId, instrumentId, (double) venueBalanceScaled / Ids.SCALE);
            completeRecovery(venueId);
        } else {
            riskEngine.activateKillSwitch("reconciliation_failed");
        }
    }

    @Override
    public void onTimer(final long correlationId, final long timestamp) {
        if (correlationId >= 2000 && correlationId < 2000 + venueState.length) {
            final int venueId = (int) (correlationId - 2000);
            if (venueState[venueId] == RecoveryState.QUERYING_ORDERS) {
                riskEngine.activateKillSwitch("recovery_timeout");
            }
        } else if (correlationId >= 3000 && correlationId < 3000 + venueState.length) {
            final int venueId = (int) (correlationId - 3000);
            if (venueState[venueId] == RecoveryState.AWAITING_BALANCE) {
                riskEngine.activateKillSwitch("recovery_timeout");
            }
        }
    }

    @Override
    public boolean isInRecovery(final int venueId) {
        return venueState[venueId] != RecoveryState.IDLE;
    }

    @Override
    public void writeSnapshot(final ExclusivePublication snapshotPublication) {
        if (snapshotPublication == null) {
            return;
        }
        final int length = encodeSnapshot(snapshotBuffer, 0);
        while (snapshotPublication.offer(snapshotBuffer, 0, length) < 0) {
            Thread.onSpinWait();
        }
    }

    @Override
    public void loadSnapshot(final Image snapshotImage) {
        if (snapshotImage == null) {
            return;
        }
        while (snapshotImage.poll((buffer, offset, length, header) -> loadSnapshot(buffer, offset), 1) == 0) {
            Thread.onSpinWait();
        }
    }

    @Override
    public void resetAll() {
        Arrays.fill(venueState, RecoveryState.IDLE);
        lastScheduledTimerId = 0L;
        lastCanceledTimerId = 0L;
        parentSnapshotWrites = 0L;
        parentSnapshotLoads = 0L;
        parentReconciliationMismatches = 0L;
        parentSyntheticFills = 0L;
        parentPartialRecoveries = 0L;
        parentHedgePendingRecoveries = 0L;
        unreconciledParentKillSwitches = 0L;
    }

    @Override
    public void setCluster(final Cluster cluster) {
        this.cluster = cluster;
    }

    public RecoveryState state(final int venueId) {
        return venueState[venueId];
    }

    public long lastScheduledTimerId() {
        return lastScheduledTimerId;
    }

    public long lastCanceledTimerId() {
        return lastCanceledTimerId;
    }

    public ParentOrderRegistry.Snapshot newParentSnapshot() {
        return parentOrderRegistry == null ? null : parentOrderRegistry.newSnapshot();
    }

    public void snapshotParentOrders(final ParentOrderRegistry.Snapshot snapshot) {
        if (parentOrderRegistry == null || snapshot == null) {
            return;
        }
        parentOrderRegistry.snapshotInto(snapshot);
        parentSnapshotWrites++;
    }

    public void loadParentSnapshot(final ParentOrderRegistry.Snapshot snapshot) {
        if (parentOrderRegistry == null || snapshot == null) {
            return;
        }
        parentOrderRegistry.loadFrom(snapshot);
        parentSnapshotLoads++;
    }

    public long parentSnapshotWrites() {
        return parentSnapshotWrites;
    }

    public long parentSnapshotLoads() {
        return parentSnapshotLoads;
    }

    public long parentReconciliationMismatches() {
        return parentReconciliationMismatches;
    }

    public long parentSyntheticFills() {
        return parentSyntheticFills;
    }

    public long parentPartialRecoveries() {
        return parentPartialRecoveries;
    }

    public long parentHedgePendingRecoveries() {
        return parentHedgePendingRecoveries;
    }

    public long unreconciledParentKillSwitches() {
        return unreconciledParentKillSwitches;
    }

    int encodeSnapshot(final UnsafeBuffer target, final int offset) {
        target.putByte(offset, (byte) venueState.length);
        for (int i = 0; i < venueState.length; i++) {
            target.putByte(offset + 1 + i, (byte) venueState[i].ordinal());
        }
        return 1 + venueState.length;
    }

    void loadSnapshot(final DirectBuffer source, final int offset) {
        final int count = source.getByte(offset) & 0xff;
        for (int i = 0; i < count && i < venueState.length; i++) {
            venueState[i] = RecoveryState.values()[source.getByte(offset + 1 + i)];
        }
    }

    private boolean reconcileParentOrder(final ExecutionEventDecoder decoder, final OrderState childOrder) {
        if (parentOrderRegistry == null || childOrder.parentOrderId() == 0L) {
            return true;
        }

        final long parentOrderId = childOrder.parentOrderId();
        final ParentOrderState parent = parentOrderRegistry.lookup(parentOrderId);
        if (parent == null) {
            parentReconciliationMismatches++;
            unreconciledParentKillSwitches++;
            riskEngine.activateKillSwitch("parent_reconciliation_failed");
            return false;
        }

        if (parentOrderRegistry.parentOrderIdByChild(decoder.clOrdId()) == 0L) {
            parentReconciliationMismatches++;
            parentOrderRegistry.linkChild(parentOrderId, decoder.clOrdId());
        }

        if (decoder.execType() == ExecType.CANCELED) {
            parentOrderRegistry.unlinkChild(decoder.clOrdId());
            if (parentOrderRegistry.activeChildCount(parentOrderId) == 0) {
                parentOrderRegistry.transition(parentOrderId, ParentOrderState.CANCELED,
                    ParentOrderState.REASON_EXECUTION_ABORTED, decoder.ingressTimestampNanos());
            }
        } else if (isFill(decoder.execType())) {
            parentSyntheticFills++;
            parentOrderRegistry.updateFill(parentOrderId, decoder.cumQtyScaled(), decoder.leavesQtyScaled(),
                decoder.fillPriceScaled());
            if (parent.status() == ParentOrderState.HEDGING) {
                parentHedgePendingRecoveries++;
            }
            if (decoder.execType() == ExecType.PARTIAL_FILL || decoder.leavesQtyScaled() > 0L
                || decoder.isFinal() != BooleanType.TRUE) {
                parentPartialRecoveries++;
                parentOrderRegistry.transition(parentOrderId, ParentOrderState.PARTIALLY_FILLED,
                    ParentOrderState.REASON_NONE, decoder.ingressTimestampNanos());
            } else {
                parentOrderRegistry.unlinkChild(decoder.clOrdId());
                parentOrderRegistry.transition(parentOrderId, ParentOrderState.DONE,
                    ParentOrderState.REASON_COMPLETED, decoder.ingressTimestampNanos());
            }
        }
        return true;
    }

    private void startOrderQuery(final int venueId) {
        venueState[venueId] = RecoveryState.QUERYING_ORDERS;
        final long[] liveOrderIds = orderManager.getLiveOrderIds(venueId);
        for (long clOrdId : liveOrderIds) {
            final OrderState order = orderManager.getOrder(clOrdId);
            if (order != null) {
                publishOrderStatusQuery(order);
            }
        }
        scheduleTimer(2000L + venueId, ORDER_QUERY_TIMEOUT_MICROS);
        if (liveOrderIds.length == 0) {
            startBalanceQuery(venueId, 0);
        }
    }

    private void startBalanceQuery(final int venueId, final int instrumentId) {
        venueState[venueId] = RecoveryState.AWAITING_BALANCE;
        lastCanceledTimerId = 2000L + venueId;
        if (cluster != null) {
            cluster.cancelTimer(lastCanceledTimerId);
        }
        balanceQueryRequestEncoder
            .wrapAndApplyHeader(buffer, 0, headerEncoder)
            .venueId(venueId)
            .instrumentId(instrumentId)
            .requestId(cluster == null ? 0L : cluster.logPosition());
        sink.offer(buffer, 0, MessageHeaderEncoder.ENCODED_LENGTH + balanceQueryRequestEncoder.encodedLength());
        scheduleTimer(3000L + venueId, BALANCE_QUERY_TIMEOUT_MICROS);
    }

    private void completeRecovery(final int venueId) {
        venueState[venueId] = RecoveryState.IDLE;
        lastCanceledTimerId = 3000L + venueId;
        if (cluster != null) {
            cluster.cancelTimer(lastCanceledTimerId);
        }
        riskEngine.setRecoveryLock(venueId, false);
        recoveryCompleteEncoder
            .wrapAndApplyHeader(buffer, 0, headerEncoder)
            .venueId(venueId)
            .recoveryCompleteClusterTime(cluster == null ? 0L : cluster.time());
        sink.offer(buffer, 0, MessageHeaderEncoder.ENCODED_LENGTH + recoveryCompleteEncoder.encodedLength());
    }

    private void publishOrderStatusQuery(final OrderState order) {
        final byte[] venueOrderId = bytes(order.venueOrderId());
        orderStatusQueryEncoder
            .wrapAndApplyHeader(buffer, 0, headerEncoder)
            .clOrdId(order.clOrdId())
            .venueId(order.venueId())
            .instrumentId(order.instrumentId())
            .side(Side.get(order.side()))
            .putVenueOrderId(venueOrderId, 0, venueOrderId.length);
        sink.offer(buffer, 0, MessageHeaderEncoder.ENCODED_LENGTH + orderStatusQueryEncoder.encodedLength());
    }

    private void publishCancel(
        final long clOrdId,
        final int venueId,
        final int instrumentId,
        final Side side,
        final long qtyScaled,
        final String venueOrderId
    ) {
        final byte[] venueOrderIdBytes = bytes(venueOrderId);
        cancelOrderEncoder
            .wrapAndApplyHeader(buffer, 0, headerEncoder)
            .cancelClOrdId(clOrdId + 1_000_000L)
            .origClOrdId(clOrdId)
            .venueId(venueId)
            .instrumentId(instrumentId)
            .side(side)
            .originalQtyScaled(qtyScaled)
            .putVenueOrderId(venueOrderIdBytes, 0, venueOrderIdBytes.length);
        sink.offer(buffer, 0, MessageHeaderEncoder.ENCODED_LENGTH + cancelOrderEncoder.encodedLength());
    }

    private void publishSyntheticFill(final ExecutionEventDecoder decoder, final OrderState order) {
        fillEventEncoder
            .wrapAndApplyHeader(buffer, 0, headerEncoder)
            .clOrdId(decoder.clOrdId())
            .venueId(decoder.venueId())
            .instrumentId(decoder.instrumentId())
            .strategyId((short) order.strategyId())
            .side(decoder.side())
            .fillPriceScaled(decoder.fillPriceScaled())
            .fillQtyScaled(decoder.fillQtyScaled())
            .cumFillQtyScaled(decoder.cumQtyScaled())
            .leavesQtyScaled(decoder.leavesQtyScaled())
            .isFinal(BooleanType.TRUE)
            .isSynthetic(BooleanType.TRUE)
            .fillTimestampNanos(decoder.exchangeTimestampNanos())
            .ingressTimestampNanos(decoder.ingressTimestampNanos());
        sink.offer(buffer, 0, MessageHeaderEncoder.ENCODED_LENGTH + fillEventEncoder.encodedLength());
    }

    private void scheduleTimer(final long correlationId, final long delayMicros) {
        lastScheduledTimerId = correlationId;
        if (cluster != null) {
            cluster.scheduleTimer(correlationId, cluster.time() + delayMicros);
        }
    }

    private static byte[] bytes(final String value) {
        return value == null ? new byte[0] : value.getBytes(StandardCharsets.US_ASCII);
    }

    private static boolean isFill(final ExecType execType) {
        return execType == ExecType.FILL || execType == ExecType.PARTIAL_FILL;
    }

    @FunctionalInterface
    interface RecoverySink {
        RecoverySink NO_OP = (buffer, offset, length) -> length;

        long offer(DirectBuffer buffer, int offset, int length);
    }
}
