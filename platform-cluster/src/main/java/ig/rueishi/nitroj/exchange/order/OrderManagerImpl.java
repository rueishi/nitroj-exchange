package ig.rueishi.nitroj.exchange.order;

import ig.rueishi.nitroj.exchange.common.BoundedTextIdentity;
import ig.rueishi.nitroj.exchange.common.BoundedTextIdentityStore;
import ig.rueishi.nitroj.exchange.common.OrderStatus;
import ig.rueishi.nitroj.exchange.common.ScaledMath;
import ig.rueishi.nitroj.exchange.cluster.OwnOrderOverlay;
import ig.rueishi.nitroj.exchange.messages.BooleanType;
import ig.rueishi.nitroj.exchange.messages.CancelOrderCommandEncoder;
import ig.rueishi.nitroj.exchange.messages.ExecType;
import ig.rueishi.nitroj.exchange.messages.ExecutionEventDecoder;
import ig.rueishi.nitroj.exchange.messages.MessageHeaderDecoder;
import ig.rueishi.nitroj.exchange.messages.MessageHeaderEncoder;
import ig.rueishi.nitroj.exchange.messages.OrdType;
import ig.rueishi.nitroj.exchange.messages.OrderStateSnapshotDecoder;
import ig.rueishi.nitroj.exchange.messages.OrderStateSnapshotEncoder;
import ig.rueishi.nitroj.exchange.messages.Side;
import ig.rueishi.nitroj.exchange.messages.TimeInForce;
import io.aeron.ExclusivePublication;
import io.aeron.Image;
import io.aeron.cluster.service.Cluster;
import java.nio.ByteOrder;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Objects;
import java.util.function.LongSupplier;
import org.agrona.DirectBuffer;
import org.agrona.MutableDirectBuffer;
import org.agrona.collections.Long2ObjectHashMap;
import org.agrona.concurrent.UnsafeBuffer;

/**
 * Deterministic order lifecycle manager for the Aeron Cluster service.
 *
 * <p>The implementation keeps all live orders in an Agrona primitive-key map and
 * sources order objects from {@link OrderStatePool}. It is designed for the
 * cluster-service thread: no locks, no boxed {@code Long} keys, and no queues
 * between state-machine stages. Execution reports are applied synchronously, and
 * fill reports return {@code true} so the router can immediately fan out to
 * portfolio and risk components in the required order.</p>
 *
 * <p>The implementation also owns duplicate {@code execId} detection. V12 stores
 * execution IDs in a fixed-capacity {@link BoundedTextIdentityStore} plus a ring
 * of store slots. Venue order IDs are copied into the reusable byte storage held
 * by {@link OrderState}. Snapshot and cancel command encoders write those bytes
 * directly, so normal order state transitions avoid per-event {@code String},
 * byte-array, and identity-object allocation.</p>
 */
public final class OrderManagerImpl implements OrderManager {
    static final int EXEC_ID_WINDOW_SIZE = 10_000;

    private final Long2ObjectHashMap<OrderState> liveOrders = new Long2ObjectHashMap<>();
    private final OrderStatePool pool;
    private final OrderMessageSink commandSink;
    private final LongSupplier fallbackClusterTimeMicros;
    private final BoundedTextIdentityStore seenExecIds = new BoundedTextIdentityStore(
        EXEC_ID_WINDOW_SIZE * 2,
        BoundedTextIdentity.DEFAULT_MAX_LENGTH);
    private final int[] execIdWindowSlots = new int[EXEC_ID_WINDOW_SIZE];
    private final MessageHeaderEncoder headerEncoder = new MessageHeaderEncoder();
    private final MessageHeaderDecoder headerDecoder = new MessageHeaderDecoder();
    private final CancelOrderCommandEncoder cancelOrderEncoder = new CancelOrderCommandEncoder();
    private final OrderStateSnapshotEncoder snapshotEncoder = new OrderStateSnapshotEncoder();
    private final OrderStateSnapshotDecoder snapshotDecoder = new OrderStateSnapshotDecoder();
    private final UnsafeBuffer commandBuffer = new UnsafeBuffer(new byte[512]);
    private final UnsafeBuffer snapshotBuffer = new UnsafeBuffer(new byte[512]);
    private final UnsafeBuffer countBuffer = new UnsafeBuffer(new byte[Integer.BYTES]);
    private final UnsafeBuffer identityScratchBuffer =
        new UnsafeBuffer(new byte[BoundedTextIdentity.DEFAULT_MAX_LENGTH]);
    private final UnsafeBuffer execIdScratchBuffer =
        new UnsafeBuffer(new byte[BoundedTextIdentity.DEFAULT_MAX_LENGTH]);
    private Cluster cluster;
    private int execIdHead;
    private long nextGeneratedClOrdId = 1L;
    private long invalidTransitionCount;
    private long duplicateExecutionReportCount;

    public OrderManagerImpl() {
        this(new OrderStatePool(), OrderMessageSink.NO_OP, () -> 0L);
    }

    OrderManagerImpl(
        final OrderStatePool pool,
        final OrderMessageSink commandSink,
        final LongSupplier fallbackClusterTimeMicros
    ) {
        this.pool = Objects.requireNonNull(pool, "pool");
        this.commandSink = Objects.requireNonNull(commandSink, "commandSink");
        this.fallbackClusterTimeMicros = Objects.requireNonNull(fallbackClusterTimeMicros, "fallbackClusterTimeMicros");
        Arrays.fill(execIdWindowSlots, BoundedTextIdentityStore.NOT_FOUND);
    }

    /**
     * Creates a new unparented live order in {@link OrderStatus#PENDING_NEW}.
     *
     * <p>This preserves the V12 external contract and stores parent ID {@code 0}
     * as the explicit unparented sentinel. V13 execution strategies call the
     * overload that supplies a real parent order ID.</p>
     */
    @Override
    public void createPendingOrder(
        final long clOrdId,
        final int venueId,
        final int instrumentId,
        final byte side,
        final byte ordType,
        final byte timeInForce,
        final long priceScaled,
        final long qtyScaled,
        final int strategyId
    ) {
        createPendingOrder(clOrdId, venueId, instrumentId, side, ordType, timeInForce,
            priceScaled, qtyScaled, strategyId, 0L);
    }

    /**
     * Creates a new live child order in {@link OrderStatus#PENDING_NEW}.
     *
     * <p>Strategies call this after risk approval and before the gateway reports
     * the venue acknowledgement. The method claims a pooled state object, fills
     * immutable order attributes, initializes leaves quantity to the order
     * quantity, records parent attribution, and records cluster time for later
     * snapshot/recovery use. Parent ID {@code 0} is reserved for unparented
     * children and remains valid for V12-compatible callers.</p>
     */
    @Override
    public void createPendingOrder(
        final long clOrdId,
        final int venueId,
        final int instrumentId,
        final byte side,
        final byte ordType,
        final byte timeInForce,
        final long priceScaled,
        final long qtyScaled,
        final int strategyId,
        final long parentOrderId
    ) {
        final OrderState order = pool.claim();
        order.clOrdId = clOrdId;
        order.parentOrderId = parentOrderId;
        order.venueId = venueId;
        order.instrumentId = instrumentId;
        order.strategyId = strategyId;
        order.side = side;
        order.ordType = ordType;
        order.timeInForce = timeInForce;
        order.priceScaled = priceScaled;
        order.qtyScaled = qtyScaled;
        order.leavesQtyScaled = qtyScaled;
        order.status = OrderStatus.PENDING_NEW;
        order.createdClusterTime = nowMicros();
        liveOrders.put(clOrdId, order);
        if (clOrdId >= nextGeneratedClOrdId) {
            nextGeneratedClOrdId = clOrdId + 1L;
        }
    }

    /**
     * Applies one normalized execution report to the matching live order.
     *
     * <p>The method first de-duplicates non-empty {@code execId} values, then
     * dispatches through the Section 7.3 state table. It returns {@code true}
     * only when a fill quantity was applied, which is the router signal to update
     * portfolio and risk state.</p>
     *
     * @return {@code true} if this report represents a fill that downstream
     * components must process; otherwise {@code false}
     */
    @Override
    public boolean onExecution(final ExecutionEventDecoder decoder) {
        final int execIdLength = copyExecId(decoder);
        if (execIdLength > 0 && isDuplicateExecId(execIdLength)) {
            duplicateExecutionReportCount++;
            return false;
        }

        final OrderState order = liveOrders.get(decoder.clOrdId());
        if (order == null) {
            invalidTransitionCount++;
            return false;
        }

        final ExecType execType = decoder.execType();
        if (OrderStatus.isTerminal(order.status)) {
            duplicateExecutionReportCount++;
            if (isFill(execType)) {
                applyFill(order, decoder);
                return true;
            }
            return false;
        }

        return switch (order.status) {
            case OrderStatus.PENDING_NEW -> onPendingNew(order, decoder, execType);
            case OrderStatus.NEW -> onNew(order, decoder, execType);
            case OrderStatus.PARTIALLY_FILLED -> onPartiallyFilled(order, decoder, execType);
            case OrderStatus.PENDING_CANCEL -> onPendingCancel(order, decoder, execType);
            case OrderStatus.PENDING_REPLACE -> onPendingReplace(order, decoder, execType);
            default -> invalid(order, decoder, execType);
        };
    }

    /**
     * Publishes cancel commands for all live, non-terminal orders.
     *
     * <p>This is used by kill-switch and shutdown flows. Each canceled order is
     * moved to {@link OrderStatus#PENDING_CANCEL} immediately so subsequent fill
     * reports are treated as cancel races rather than ordinary fills.</p>
     */
    @Override
    public void cancelAllOrders() {
        for (OrderState order : liveOrders.values()) {
            if (!OrderStatus.isTerminal(order.status)) {
                publishCancel(order);
                order.status = OrderStatus.PENDING_CANCEL;
            }
        }
    }

    @Override
    public long[] getLiveOrderIds(final int venueId) {
        final long[] ids = new long[liveOrders.size()];
        int count = 0;
        for (OrderState order : liveOrders.values()) {
            if (order.venueId == venueId && !OrderStatus.isTerminal(order.status)) {
                ids[count++] = order.clOrdId;
            }
        }
        final long[] result = new long[count];
        System.arraycopy(ids, 0, result, 0, count);
        return result;
    }

    @Override
    public void forEachLiveOrder(final int venueId, final LiveOrderConsumer consumer) {
        Objects.requireNonNull(consumer, "consumer");
        for (OrderState order : liveOrders.values()) {
            if (order.venueId == venueId && !OrderStatus.isTerminal(order.status)) {
                consumer.onLiveOrder(order);
            }
        }
    }

    @Override
    public void forceTransitionToCanceled(final long clOrdId) {
        final OrderState order = liveOrders.get(clOrdId);
        if (order != null) {
            order.status = OrderStatus.CANCELED;
            releaseTerminal(order);
        }
    }

    /**
     * Writes live order snapshots to Aeron Archive publication.
     *
     * <p>The stream begins with a little-endian count fragment followed by one
     * SBE {@code OrderStateSnapshot} fragment per live order. Terminal orders are
     * normally removed immediately, but the status check is kept here to defend
     * recovery tooling and tests that install terminal states explicitly.</p>
     */
    @Override
    public void writeSnapshot(final ExclusivePublication pub) {
        if (pub == null) {
            return;
        }
        countBuffer.putInt(0, liveOrderCount(), ByteOrder.LITTLE_ENDIAN);
        offer(pub, countBuffer, Integer.BYTES);
        for (OrderState order : liveOrders.values()) {
            if (!OrderStatus.isTerminal(order.status)) {
                final int length = encodeSnapshot(order, snapshotBuffer, 0);
                offer(pub, snapshotBuffer, length);
            }
        }
    }

    /**
     * Loads order snapshots from an Aeron image.
     *
     * <p>The image is expected to contain the same count-plus-record sequence
     * emitted by {@link #writeSnapshot(ExclusivePublication)}. The method is only
     * invoked during cluster start, before normal message processing begins.</p>
     */
    @Override
    public void loadSnapshot(final Image image) {
        if (image == null) {
            return;
        }
        final List<byte[]> fragments = new ArrayList<>();
        while (fragments.isEmpty()) {
            final int polled = image.poll((buffer, offset, length, header) -> {
                final byte[] copy = new byte[length];
                buffer.getBytes(offset, copy);
                fragments.add(copy);
            }, Integer.MAX_VALUE);
            if (polled == 0) {
                Thread.onSpinWait();
            }
        }
        loadSnapshotFragments(fragments);
    }

    @Override
    public void setCluster(final Cluster cluster) {
        this.cluster = cluster;
    }

    /**
     * Returns every live order to the pool before clearing maps and dedupe state.
     *
     * <p>The release-before-clear order is critical: clearing the map first would
     * lose references to pooled objects and permanently reduce warm-pool capacity
     * after a warmup reset.</p>
     */
    @Override
    public void resetAll() {
        for (OrderState order : liveOrders.values()) {
            pool.release(order);
        }
        liveOrders.clear();
        seenExecIds.clear();
        Arrays.fill(execIdWindowSlots, BoundedTextIdentityStore.NOT_FOUND);
        execIdHead = 0;
        invalidTransitionCount = 0L;
        duplicateExecutionReportCount = 0L;
        nextGeneratedClOrdId = 1L;
    }

    public long nextClOrdId() {
        return nextGeneratedClOrdId++;
    }

    public byte getStatus(final long clOrdId) {
        final OrderState order = liveOrders.get(clOrdId);
        return order == null ? Byte.MIN_VALUE : order.status;
    }

    @Override
    public OrderState getOrder(final long clOrdId) {
        return liveOrders.get(clOrdId);
    }

    public int liveOrderCount() {
        int count = 0;
        for (OrderState order : liveOrders.values()) {
            if (!OrderStatus.isTerminal(order.status)) {
                count++;
            }
        }
        return count;
    }

    public int poolAvailable() {
        return pool.available();
    }

    public long invalidTransitionCount() {
        return invalidTransitionCount;
    }

    public long duplicateExecutionReportCount() {
        return duplicateExecutionReportCount;
    }

    /**
     * Projects current live orders into an own-order overlay.
     *
     * <p>The order manager remains the source of truth for order state. This
     * method gives strategy-facing market views a deterministic way to rebuild
     * self-liquidity after replay, snapshot load, or test setup without placing
     * private ownership flags inside gross market books.</p>
     *
     * @param overlay destination overlay to replace
     */
    public void publishOwnOrdersTo(final OwnOrderOverlay overlay) {
        Objects.requireNonNull(overlay, "overlay");
        overlay.clear();
        for (OrderState order : liveOrders.values()) {
            overlay.updateFrom(order);
        }
    }

    /**
     * Builds a fresh own-order overlay from current order-manager state.
     *
     * <p>This is a convenience hook for reconciliation flows that need a
     * point-in-time private ownership view before comparing against L3 market
     * data. The returned overlay is derived solely from live execution-report
     * state; L3 visibility and queue position remain advisory and must not
     * mutate {@link OrderState}.</p>
     *
     * @return newly populated own-order overlay
     */
    public OwnOrderOverlay ownOrderOverlaySnapshot() {
        final OwnOrderOverlay overlay = new OwnOrderOverlay();
        publishOwnOrdersTo(overlay);
        return overlay;
    }

    public int seenExecIdCount() {
        return seenExecIds.size();
    }

    @Override
    public void markCancelSent(final long clOrdId) {
        final OrderState order = liveOrders.get(clOrdId);
        if (order != null && !OrderStatus.isTerminal(order.status)) {
            order.status = OrderStatus.PENDING_CANCEL;
        }
    }

    public void markReplaceSent(final long clOrdId, final long replacedByClOrdId) {
        final OrderState order = liveOrders.get(clOrdId);
        if (order != null && !OrderStatus.isTerminal(order.status)) {
            order.status = OrderStatus.PENDING_REPLACE;
            order.replacedByClOrdId = replacedByClOrdId;
        }
    }

    int encodeSnapshot(final OrderState order, final MutableDirectBuffer buffer, final int offset) {
        snapshotEncoder
            .wrapAndApplyHeader(buffer, offset, headerEncoder)
            .clOrdId(order.clOrdId)
            .parentOrderId(order.parentOrderId)
            .venueId(order.venueId)
            .instrumentId(order.instrumentId)
            .strategyId((short) order.strategyId)
            .side(Side.get(order.side))
            .ordType(OrdType.get(order.ordType))
            .timeInForce(TimeInForce.get(order.timeInForce))
            .status(order.status)
            .priceScaled(order.priceScaled)
            .qtyScaled(order.qtyScaled)
            .cumFillQtyScaled(order.cumFillQtyScaled)
            .leavesQtyScaled(order.leavesQtyScaled)
            .avgFillPriceScaled(order.avgFillPriceScaled)
            .createdClusterTime(order.createdClusterTime)
            .putVenueOrderId(order.venueOrderIdBytes, 0, order.venueOrderIdLength);
        return MessageHeaderEncoder.ENCODED_LENGTH + snapshotEncoder.encodedLength();
    }

    void loadSnapshotRecord(final DirectBuffer buffer, final int offset) {
        snapshotDecoder.wrapAndApplyHeader(buffer, offset, headerDecoder);
        final OrderState order = pool.claim();
        order.clOrdId = snapshotDecoder.clOrdId();
        order.parentOrderId = snapshotDecoder.parentOrderId();
        order.venueId = snapshotDecoder.venueId();
        order.instrumentId = snapshotDecoder.instrumentId();
        order.strategyId = snapshotDecoder.strategyId();
        order.side = snapshotDecoder.sideRaw();
        order.ordType = snapshotDecoder.ordTypeRaw();
        order.timeInForce = snapshotDecoder.timeInForceRaw();
        order.status = snapshotDecoder.status();
        order.priceScaled = snapshotDecoder.priceScaled();
        order.qtyScaled = snapshotDecoder.qtyScaled();
        order.cumFillQtyScaled = snapshotDecoder.cumFillQtyScaled();
        order.leavesQtyScaled = snapshotDecoder.leavesQtyScaled();
        order.avgFillPriceScaled = snapshotDecoder.avgFillPriceScaled();
        order.createdClusterTime = snapshotDecoder.createdClusterTime();
        copySnapshotVenueOrderId(snapshotDecoder, order);
        liveOrders.put(order.clOrdId, order);
        if (order.clOrdId >= nextGeneratedClOrdId) {
            nextGeneratedClOrdId = order.clOrdId + 1L;
        }
    }

    public void loadSnapshotFragments(final List<byte[]> fragments) {
        if (fragments.isEmpty()) {
            return;
        }
        final int count = new UnsafeBuffer(fragments.get(0)).getInt(0, ByteOrder.LITTLE_ENDIAN);
        for (int i = 0; i < count; i++) {
            loadSnapshotRecord(new UnsafeBuffer(fragments.get(i + 1)), 0);
        }
    }

    public List<byte[]> snapshotFragments() {
        final List<byte[]> fragments = new ArrayList<>();
        countBuffer.putInt(0, liveOrderCount(), ByteOrder.LITTLE_ENDIAN);
        fragments.add(copy(countBuffer, 0, Integer.BYTES));
        for (OrderState order : liveOrders.values()) {
            if (!OrderStatus.isTerminal(order.status)) {
                final int length = encodeSnapshot(order, snapshotBuffer, 0);
                fragments.add(copy(snapshotBuffer, 0, length));
            }
        }
        return fragments;
    }

    private boolean onPendingNew(final OrderState order, final ExecutionEventDecoder decoder, final ExecType execType) {
        return switch (execType) {
            case NEW -> {
                order.status = OrderStatus.NEW;
                copyVenueOrderId(decoder, order);
                order.firstAckNanos = decoder.ingressTimestampNanos();
                order.lastUpdateNanos = decoder.ingressTimestampNanos();
                yield false;
            }
            case REJECTED -> {
                order.status = OrderStatus.REJECTED;
                order.rejectCode = decoder.rejectCode();
                order.lastUpdateNanos = decoder.ingressTimestampNanos();
                releaseTerminal(order);
                yield false;
            }
            case FILL, PARTIAL_FILL -> applyFillTransition(order, decoder);
            case ORDER_STATUS -> false;
            default -> invalid(order, decoder, execType);
        };
    }

    private boolean onNew(final OrderState order, final ExecutionEventDecoder decoder, final ExecType execType) {
        return switch (execType) {
            case FILL, PARTIAL_FILL -> applyFillTransition(order, decoder);
            case EXPIRED -> {
                order.status = OrderStatus.EXPIRED;
                order.lastUpdateNanos = decoder.ingressTimestampNanos();
                releaseTerminal(order);
                yield false;
            }
            case ORDER_STATUS -> false;
            default -> invalid(order, decoder, execType);
        };
    }

    private boolean onPartiallyFilled(final OrderState order, final ExecutionEventDecoder decoder, final ExecType execType) {
        return switch (execType) {
            case FILL, PARTIAL_FILL -> applyFillTransition(order, decoder);
            case EXPIRED -> {
                order.status = OrderStatus.EXPIRED;
                order.lastUpdateNanos = decoder.ingressTimestampNanos();
                releaseTerminal(order);
                yield false;
            }
            case ORDER_STATUS -> false;
            default -> invalid(order, decoder, execType);
        };
    }

    private boolean onPendingCancel(final OrderState order, final ExecutionEventDecoder decoder, final ExecType execType) {
        return switch (execType) {
            case CANCELED -> {
                order.status = OrderStatus.CANCELED;
                order.lastUpdateNanos = decoder.ingressTimestampNanos();
                releaseTerminal(order);
                yield false;
            }
            case FILL, PARTIAL_FILL -> applyFillTransition(order, decoder);
            case ORDER_STATUS -> false;
            default -> invalid(order, decoder, execType);
        };
    }

    private boolean onPendingReplace(final OrderState order, final ExecutionEventDecoder decoder, final ExecType execType) {
        return switch (execType) {
            case CANCELED -> {
                order.status = OrderStatus.PENDING_NEW;
                order.lastUpdateNanos = decoder.ingressTimestampNanos();
                yield false;
            }
            case REPLACED -> {
                order.status = OrderStatus.REPLACED;
                order.lastUpdateNanos = decoder.ingressTimestampNanos();
                releaseTerminal(order);
                yield false;
            }
            case REJECTED -> {
                order.status = OrderStatus.NEW;
                order.rejectCode = decoder.rejectCode();
                order.lastUpdateNanos = decoder.ingressTimestampNanos();
                yield false;
            }
            case FILL, PARTIAL_FILL -> applyFillTransition(order, decoder);
            case ORDER_STATUS -> false;
            default -> invalid(order, decoder, execType);
        };
    }

    private boolean invalid(final OrderState order, final ExecutionEventDecoder decoder, final ExecType execType) {
        invalidTransitionCount++;
        if (isFill(execType)) {
            applyFill(order, decoder);
            return true;
        }
        return false;
    }

    private boolean applyFillTransition(final OrderState order, final ExecutionEventDecoder decoder) {
        applyFill(order, decoder);
        if (decoder.isFinal() == BooleanType.TRUE || order.leavesQtyScaled == 0L) {
            order.status = OrderStatus.FILLED;
            releaseTerminal(order);
        } else {
            order.status = OrderStatus.PARTIALLY_FILLED;
        }
        return true;
    }

    private void applyFill(final OrderState order, final ExecutionEventDecoder decoder) {
        final long previousCumQty = order.cumFillQtyScaled;
        final long fillQty = decoder.fillQtyScaled();
        final long fillPrice = decoder.fillPriceScaled();
        final long decoderCumQty = decoder.cumQtyScaled();
        order.cumFillQtyScaled = decoderCumQty > 0L ? decoderCumQty : previousCumQty + fillQty;
        order.leavesQtyScaled = Math.max(0L, decoder.leavesQtyScaled());
        order.avgFillPriceScaled = previousCumQty == 0L
            ? fillPrice
            : ScaledMath.vwap(order.avgFillPriceScaled, previousCumQty, fillPrice, fillQty);
        order.exchangeTimestampNanos = decoder.exchangeTimestampNanos();
        order.lastUpdateNanos = decoder.ingressTimestampNanos();
        copyVenueOrderIdIfPresent(decoder, order);
    }

    private void releaseTerminal(final OrderState order) {
        liveOrders.remove(order.clOrdId);
        pool.release(order);
    }

    private boolean isDuplicateExecId(final int execIdLength) {
        if (seenExecIds.contains(execIdScratchBuffer, 0, execIdLength)) {
            return true;
        }
        final int oldest = execIdWindowSlots[execIdHead];
        if (oldest >= 0) {
            final int length = seenExecIds.copySlotTo(oldest, identityScratchBuffer.byteArray(), 0);
            seenExecIds.remove(identityScratchBuffer, 0, length);
        }
        final int slot = seenExecIds.put(execIdScratchBuffer, 0, execIdLength);
        execIdWindowSlots[execIdHead] = slot;
        execIdHead = (execIdHead + 1) % EXEC_ID_WINDOW_SIZE;
        return false;
    }

    private void publishCancel(final OrderState order) {
        cancelOrderEncoder
            .wrapAndApplyHeader(commandBuffer, 0, headerEncoder)
            .cancelClOrdId(nextClOrdId())
            .origClOrdId(order.clOrdId)
            .venueId(order.venueId)
            .instrumentId(order.instrumentId)
            .side(Side.get(order.side))
            .originalQtyScaled(order.qtyScaled)
            .putVenueOrderId(order.venueOrderIdBytes, 0, order.venueOrderIdLength);
        commandSink.offer(commandBuffer, 0, MessageHeaderEncoder.ENCODED_LENGTH + cancelOrderEncoder.encodedLength());
    }

    private static boolean isFill(final ExecType execType) {
        return execType == ExecType.FILL || execType == ExecType.PARTIAL_FILL;
    }

    private int copyExecId(final ExecutionEventDecoder decoder) {
        decoder.sbeRewind();
        decoder.skipVenueOrderId();
        final int length = decoder.execIdLength();
        if (length <= 0 || length > BoundedTextIdentity.DEFAULT_MAX_LENGTH) {
            decoder.skipExecId();
            return 0;
        }
        decoder.getExecId(execIdScratchBuffer, 0, length);
        return length;
    }

    private void copyVenueOrderId(final ExecutionEventDecoder decoder, final OrderState order) {
        decoder.sbeRewind();
        final int length = decoder.venueOrderIdLength();
        if (length <= 0 || length > BoundedTextIdentity.DEFAULT_MAX_LENGTH) {
            decoder.skipVenueOrderId();
            order.venueOrderIdLength = 0;
            return;
        }
        order.venueOrderIdLength = decoder.getVenueOrderId(order.venueOrderIdBytes, 0, length);
    }

    private void copyVenueOrderIdIfPresent(final ExecutionEventDecoder decoder, final OrderState order) {
        decoder.sbeRewind();
        final int length = decoder.venueOrderIdLength();
        if (length <= 0 || length > BoundedTextIdentity.DEFAULT_MAX_LENGTH) {
            decoder.skipVenueOrderId();
            return;
        }
        order.venueOrderIdLength = decoder.getVenueOrderId(order.venueOrderIdBytes, 0, length);
    }

    private void copySnapshotVenueOrderId(final OrderStateSnapshotDecoder decoder, final OrderState order) {
        decoder.sbeRewind();
        final int length = decoder.venueOrderIdLength();
        if (length <= 0 || length > BoundedTextIdentity.DEFAULT_MAX_LENGTH) {
            decoder.skipVenueOrderId();
            order.venueOrderIdLength = 0;
            return;
        }
        order.venueOrderIdLength = decoder.getVenueOrderId(order.venueOrderIdBytes, 0, length);
    }

    private static byte[] copy(final DirectBuffer buffer, final int offset, final int length) {
        final byte[] bytes = new byte[length];
        buffer.getBytes(offset, bytes);
        return bytes;
    }

    private long nowMicros() {
        return cluster == null ? fallbackClusterTimeMicros.getAsLong() : cluster.time();
    }

    private static void offer(final ExclusivePublication publication, final DirectBuffer buffer, final int length) {
        while (publication.offer(buffer, 0, length) < 0) {
            Thread.onSpinWait();
        }
    }
}
