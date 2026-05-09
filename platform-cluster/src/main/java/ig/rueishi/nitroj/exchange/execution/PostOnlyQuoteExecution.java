package ig.rueishi.nitroj.exchange.execution;

import ig.rueishi.nitroj.exchange.cluster.RiskDecision;
import ig.rueishi.nitroj.exchange.common.ExecutionStrategyIds;
import ig.rueishi.nitroj.exchange.messages.CancelOrderCommandEncoder;
import ig.rueishi.nitroj.exchange.messages.ExecType;
import ig.rueishi.nitroj.exchange.messages.MessageHeaderEncoder;
import ig.rueishi.nitroj.exchange.messages.NewOrderCommandEncoder;
import ig.rueishi.nitroj.exchange.messages.OrdType;
import ig.rueishi.nitroj.exchange.messages.ParentIntentType;
import ig.rueishi.nitroj.exchange.messages.Side;
import ig.rueishi.nitroj.exchange.messages.TimeInForce;

import java.util.Objects;

/**
 * Post-only quote execution for market-making parent intents.
 *
 * <p>V13 quote intents are worked as one live child quote per parent side. The
 * strategy owns child submission, refresh cancel/replace, parent cancel races,
 * and one-tick-deeper retry after a post-only-style child reject. Retry is
 * deliberately bounded to one attempt so replay produces the same terminal
 * state and command sequence under the same ordered events.</p>
 *
 * <p>The implementation is called only from the deterministic cluster thread;
 * the bounded parent table below is for multiple open quote lifecycles, not for
 * thread synchronization. A single market-making strategy can have bid and ask
 * parents live at the same time, and V14 can run independent venue instances
 * through this one registered execution plugin. Timer callbacks are
 * deterministic no-ops except for the observable counter; market-data callbacks
 * trigger cancel/replace for each matching active child.</p>
 */
public final class PostOnlyQuoteExecution implements ExecutionStrategy {
    public static final int DEFAULT_ACTIVE_PARENT_CAPACITY = 256;
    private static final byte[] EMPTY_BYTES = new byte[0];
    private static final long ONE_TICK_SCALED = 1L;
    private static final int MAX_POST_ONLY_RETRIES = 1;

    private ExecutionStrategyContext ctx;
    private final long[] parentOrderIds;
    private final long[] childClOrdIds;
    private final int[] strategyIds;
    private final int[] venueIds;
    private final int[] instrumentIds;
    private final byte[] sides;
    private final long[] priceScaled;
    private final long[] qtyScaled;
    private final int[] retryCounts;
    private final boolean[] refreshPending;
    private final boolean[] parentCancelPending;

    private long parentIntents;
    private long refreshTriggers;
    private long cancelReplaceRequests;
    private long retrySubmissions;
    private long retryExhaustions;
    private long fills;
    private long parentCancels;
    private long riskRejects;
    private long capacityRejects;
    private long timerCallbacks;
    private long missingCallbackDrops;

    public PostOnlyQuoteExecution() {
        this(DEFAULT_ACTIVE_PARENT_CAPACITY);
    }

    PostOnlyQuoteExecution(final int activeParentCapacity) {
        if (activeParentCapacity <= 0) {
            throw new IllegalArgumentException("activeParentCapacity must be positive");
        }
        parentOrderIds = new long[activeParentCapacity];
        childClOrdIds = new long[activeParentCapacity];
        strategyIds = new int[activeParentCapacity];
        venueIds = new int[activeParentCapacity];
        instrumentIds = new int[activeParentCapacity];
        sides = new byte[activeParentCapacity];
        priceScaled = new long[activeParentCapacity];
        qtyScaled = new long[activeParentCapacity];
        retryCounts = new int[activeParentCapacity];
        refreshPending = new boolean[activeParentCapacity];
        parentCancelPending = new boolean[activeParentCapacity];
    }

    @Override
    public int executionStrategyId() {
        return ExecutionStrategyIds.POST_ONLY_QUOTE;
    }

    @Override
    public void init(final ExecutionStrategyContext ctx) {
        this.ctx = Objects.requireNonNull(ctx, "ctx");
    }

    @Override
    public void onParentIntent(final ParentOrderIntentView intent) {
        requireInitialized();
        parentIntents++;
        if (intent.parentOrderId() <= 0L || intent.quantityScaled() <= 0L || intent.intentType() != ParentIntentType.QUOTE) {
            return;
        }

        int slot = findByParent(intent.parentOrderId());
        if (slot >= 0 && childClOrdIds[slot] != 0L) {
            requestCancelReplace(slot);
            return;
        }

        final ParentOrderState parent = ctx.parentOrderRegistry().claim(
            intent.parentOrderId(),
            intent.strategyId(),
            executionStrategyId(),
            intent.quantityScaled(),
            ctx.clock().clusterTimeMicros());
        if (parent == null) {
            capacityRejects++;
            return;
        }

        slot = allocateSlot(intent.parentOrderId());
        if (slot < 0) {
            capacityRejects++;
            ctx.parentOrderRegistry().transition(intent.parentOrderId(), ParentOrderState.FAILED,
                ParentOrderState.REASON_CAPACITY_REJECTED, ctx.clock().clusterTimeMicros());
            return;
        }

        strategyIds[slot] = intent.strategyId();
        venueIds[slot] = intent.primaryVenueId();
        instrumentIds[slot] = intent.instrumentId();
        sides[slot] = intent.side().value();
        priceScaled[slot] = intent.limitPriceScaled();
        qtyScaled[slot] = intent.quantityScaled();
        retryCounts[slot] = 0;
        refreshPending[slot] = false;
        parentCancelPending[slot] = false;
        submitChild(slot, childClOrdId(intent), priceScaled[slot], ParentOrderState.REASON_NONE);
    }

    @Override
    public void onMarketDataTick(final int venueId, final int instrumentId, final long clusterTimeMicros) {
        requireInitialized();
        for (int slot = 0; slot < parentOrderIds.length; slot++) {
            if (parentOrderIds[slot] != 0L
                && childClOrdIds[slot] != 0L
                && venueId == venueIds[slot]
                && instrumentId == instrumentIds[slot]) {
                refreshTriggers++;
                requestCancelReplace(slot);
            }
        }
    }

    @Override
    public void onChildExecution(final ChildExecutionView execution) {
        requireInitialized();
        final int slot = findByParent(execution.parentOrderId());
        if (slot < 0) {
            missingCallbackDrops++;
            return;
        }

        switch (execution.execType()) {
            case NEW -> ctx.parentOrderRegistry().transition(parentOrderIds[slot], ParentOrderState.WORKING,
                ParentOrderState.REASON_NONE, ctx.clock().clusterTimeMicros());
            case FILL, PARTIAL_FILL -> onFill(slot, execution);
            case REJECTED -> onPostOnlyReject(slot, execution.childClOrdId());
            case CANCELED -> onChildCanceled(slot, execution.childClOrdId());
            case EXPIRED -> terminal(slot, ParentOrderState.EXPIRED, ParentOrderState.REASON_EXPIRED, execution.childClOrdId());
            default -> {
                if (execution.finalExecution()) {
                    terminal(slot, ParentOrderState.FAILED, ParentOrderState.REASON_EXECUTION_ABORTED, execution.childClOrdId());
                }
            }
        }
    }

    @Override
    public void onTimer(final long correlationId) {
        timerCallbacks++;
    }

    @Override
    public void onCancel(final long parentOrderId, final byte reasonCode) {
        requireInitialized();
        final int slot = findByParent(parentOrderId);
        if (slot < 0 || childClOrdIds[slot] == 0L) {
            missingCallbackDrops++;
            return;
        }
        parentCancels++;
        parentCancelPending[slot] = true;
        encodeCancel(slot, childClOrdIds[slot]);
        ctx.orderManager().markCancelSent(childClOrdIds[slot]);
        ctx.parentOrderRegistry().transition(parentOrderId, ParentOrderState.CANCEL_PENDING,
            reasonCode, ctx.clock().clusterTimeMicros());
    }

    public long refreshTriggers() {
        return refreshTriggers;
    }

    public long cancelReplaceRequests() {
        return cancelReplaceRequests;
    }

    public long retrySubmissions() {
        return retrySubmissions;
    }

    public long retryExhaustions() {
        return retryExhaustions;
    }

    public long fills() {
        return fills;
    }

    public long parentCancels() {
        return parentCancels;
    }

    public long riskRejects() {
        return riskRejects;
    }

    public long capacityRejects() {
        return capacityRejects;
    }

    public long timerCallbacks() {
        return timerCallbacks;
    }

    public long missingCallbackDrops() {
        return missingCallbackDrops;
    }

    private void requestCancelReplace(final int slot) {
        cancelReplaceRequests++;
        refreshPending[slot] = true;
        encodeCancel(slot, childClOrdIds[slot]);
        ctx.orderManager().markCancelSent(childClOrdIds[slot]);
        ctx.parentOrderRegistry().transition(parentOrderIds[slot], ParentOrderState.CANCEL_PENDING,
            ParentOrderState.REASON_NONE, ctx.clock().clusterTimeMicros());
    }

    private void onFill(final int slot, final ChildExecutionView execution) {
        fills++;
        ctx.parentOrderRegistry().updateFill(
            parentOrderIds[slot],
            execution.cumQtyScaled(),
            execution.leavesQtyScaled(),
            execution.fillPriceScaled());
        if (execution.finalExecution() || execution.leavesQtyScaled() == 0L) {
            terminal(slot, ParentOrderState.DONE, ParentOrderState.REASON_COMPLETED, execution.childClOrdId());
        } else {
            ctx.parentOrderRegistry().transition(parentOrderIds[slot], ParentOrderState.PARTIALLY_FILLED,
                ParentOrderState.REASON_NONE, ctx.clock().clusterTimeMicros());
        }
    }

    private void onPostOnlyReject(final int slot, final long rejectedChildClOrdId) {
        ctx.parentOrderRegistry().unlinkChild(rejectedChildClOrdId);
        if (retryCounts[slot] >= MAX_POST_ONLY_RETRIES) {
            retryExhaustions++;
            terminalWithoutUnlink(slot, ParentOrderState.FAILED, ParentOrderState.REASON_CHILD_REJECTED);
            return;
        }
        retryCounts[slot]++;
        retrySubmissions++;
        priceScaled[slot] = sides[slot] == Side.BUY.value()
            ? Math.max(0L, priceScaled[slot] - ONE_TICK_SCALED)
            : priceScaled[slot] + ONE_TICK_SCALED;
        submitChild(slot, rejectedChildClOrdId + 1L, priceScaled[slot], ParentOrderState.REASON_NONE);
    }

    private void onChildCanceled(final int slot, final long canceledChildClOrdId) {
        ctx.parentOrderRegistry().unlinkChild(canceledChildClOrdId);
        if (parentCancelPending[slot]) {
            terminalWithoutUnlink(slot, ParentOrderState.CANCELED, ParentOrderState.REASON_CANCELED_BY_PARENT);
            return;
        }
        if (refreshPending[slot]) {
            refreshPending[slot] = false;
            submitChild(slot, canceledChildClOrdId + 1L, priceScaled[slot], ParentOrderState.REASON_NONE);
        }
    }

    private void submitChild(final int slot, final long childClOrdId, final long childPriceScaled, final byte reasonCode) {
        final RiskDecision risk = ctx.riskEngine().preTradeCheck(
            venueIds[slot],
            instrumentIds[slot],
            sides[slot],
            childPriceScaled,
            qtyScaled[slot],
            strategyIds[slot]);
        if (!risk.approved()) {
            riskRejects++;
            ctx.parentOrderRegistry().transition(parentOrderIds[slot], ParentOrderState.FAILED,
                ParentOrderState.REASON_RISK_REJECTED, ctx.clock().clusterTimeMicros());
            childClOrdIds[slot] = 0L;
            releaseSlot(slot);
            return;
        }
        if (!ctx.parentOrderRegistry().linkChild(parentOrderIds[slot], childClOrdId)) {
            capacityRejects++;
            ctx.parentOrderRegistry().transition(parentOrderIds[slot], ParentOrderState.FAILED,
                ParentOrderState.REASON_CAPACITY_REJECTED, ctx.clock().clusterTimeMicros());
            childClOrdIds[slot] = 0L;
            releaseSlot(slot);
            return;
        }
        childClOrdIds[slot] = childClOrdId;
        ctx.orderManager().createPendingOrder(
            childClOrdId,
            venueIds[slot],
            instrumentIds[slot],
            sides[slot],
            OrdType.LIMIT.value(),
            TimeInForce.GTC.value(),
            childPriceScaled,
            qtyScaled[slot],
            strategyIds[slot],
            parentOrderIds[slot]);
        encodeNew(slot, childClOrdId, childPriceScaled);
        ctx.parentOrderRegistry().transition(parentOrderIds[slot], ParentOrderState.WORKING,
            reasonCode, ctx.clock().clusterTimeMicros());
    }

    private void terminal(final int slot, final byte status, final byte reasonCode, final long childClOrdId) {
        ctx.parentOrderRegistry().unlinkChild(childClOrdId);
        terminalWithoutUnlink(slot, status, reasonCode);
    }

    private void terminalWithoutUnlink(final int slot, final byte status, final byte reasonCode) {
        ctx.parentOrderRegistry().transition(parentOrderIds[slot], status, reasonCode, ctx.clock().clusterTimeMicros());
        releaseSlot(slot);
    }

    private void encodeNew(final int slot, final long childClOrdId, final long childPriceScaled) {
        final NewOrderCommandEncoder encoder = ctx.newOrderEncoder();
        encoder.wrapAndApplyHeader(ctx.commandBuffer(), 0, ctx.headerEncoder())
            .clOrdId(childClOrdId)
            .venueId(venueIds[slot])
            .instrumentId(instrumentIds[slot])
            .side(Side.get(sides[slot]))
            .ordType(OrdType.LIMIT)
            .timeInForce(TimeInForce.GTC)
            .priceScaled(childPriceScaled)
            .qtyScaled(qtyScaled[slot])
            .strategyId((short) strategyIds[slot])
            .parentOrderId(parentOrderIds[slot]);
    }

    private void encodeCancel(final int slot, final long childClOrdId) {
        final CancelOrderCommandEncoder encoder = ctx.cancelOrderEncoder();
        encoder.wrapAndApplyHeader(ctx.commandBuffer(), 0, ctx.headerEncoder())
            .cancelClOrdId(childClOrdId + 1L)
            .origClOrdId(childClOrdId)
            .venueId(venueIds[slot])
            .instrumentId(instrumentIds[slot])
            .side(Side.get(sides[slot]))
            .originalQtyScaled(qtyScaled[slot])
            .putVenueOrderId(EMPTY_BYTES, 0, 0);
    }

    private int allocateSlot(final long parentOrderId) {
        for (int i = 0; i < parentOrderIds.length; i++) {
            if (parentOrderIds[i] == 0L) {
                parentOrderIds[i] = parentOrderId;
                return i;
            }
        }
        return -1;
    }

    private int findByParent(final long parentOrderId) {
        if (parentOrderId <= 0L) {
            return -1;
        }
        for (int i = 0; i < parentOrderIds.length; i++) {
            if (parentOrderIds[i] == parentOrderId) {
                return i;
            }
        }
        return -1;
    }

    private void releaseSlot(final int slot) {
        parentOrderIds[slot] = 0L;
        childClOrdIds[slot] = 0L;
        strategyIds[slot] = 0;
        venueIds[slot] = 0;
        instrumentIds[slot] = 0;
        sides[slot] = 0;
        priceScaled[slot] = 0L;
        qtyScaled[slot] = 0L;
        retryCounts[slot] = 0;
        refreshPending[slot] = false;
        parentCancelPending[slot] = false;
    }

    private static long childClOrdId(final ParentOrderIntentView intent) {
        return intent.correlationId() > 0L ? intent.correlationId() : intent.parentOrderId();
    }

    private void requireInitialized() {
        if (ctx == null) {
            throw new IllegalStateException("PostOnlyQuoteExecution is not initialized");
        }
    }
}
