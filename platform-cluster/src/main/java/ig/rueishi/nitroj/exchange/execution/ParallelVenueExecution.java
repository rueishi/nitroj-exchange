package ig.rueishi.nitroj.exchange.execution;

import ig.rueishi.nitroj.exchange.cluster.ExternalLiquidityView;
import ig.rueishi.nitroj.exchange.cluster.RiskDecision;
import ig.rueishi.nitroj.exchange.common.ExecutionStrategyIds;
import ig.rueishi.nitroj.exchange.common.Ids;
import ig.rueishi.nitroj.exchange.messages.CancelOrderCommandEncoder;
import ig.rueishi.nitroj.exchange.messages.EntryType;
import ig.rueishi.nitroj.exchange.messages.ExecType;
import ig.rueishi.nitroj.exchange.messages.NewOrderCommandEncoder;
import ig.rueishi.nitroj.exchange.messages.OrdType;
import ig.rueishi.nitroj.exchange.messages.ParentIntentType;
import ig.rueishi.nitroj.exchange.messages.Side;
import ig.rueishi.nitroj.exchange.messages.TimeInForce;
import java.util.Arrays;
import java.util.Objects;

/**
 * Conservative venue-indifferent execution strategy.
 *
 * <p>The strategy maps an intent {@code venueSetId} to a bounded configured set
 * of venues, reads executable best-level external liquidity, allocates child
 * IOC quantities proportionally to available depth, and submits one child per
 * qualifying venue. It does not re-slice, fee-weight, or learn from fill
 * quality; those behaviors belong to separate V14 execution plugins.</p>
 */
public final class ParallelVenueExecution implements ExecutionStrategy {
    private static final byte[] EMPTY_BYTES = new byte[0];
    private static final int DEFAULT_MAX_ACTIVE_PARENTS = 16;
    private static final int MAX_VENUE_SETS = 32;
    private static final int MAX_VENUES_PER_SET = 8;

    private final long minimumSliceQtyScaled;
    private final long[] activeParentIds;
    private final int[] activeChildCounts;
    private final int[] activeRejectedCounts;
    private final long[] activeFilledQty;
    private final long[] activeTimerCorrelationIds;
    private final boolean[] activeCancelPending;
    private final int[] venueSetCounts = new int[MAX_VENUE_SETS];
    private final int[][] venueSets = new int[MAX_VENUE_SETS][MAX_VENUES_PER_SET];
    private final int[] planVenueIds = new int[MAX_VENUES_PER_SET];
    private final long[] planDepths = new long[MAX_VENUES_PER_SET];
    private final long[] planQtys = new long[MAX_VENUES_PER_SET];
    private final long[] childScratch = new long[MAX_VENUES_PER_SET];

    private ExecutionStrategyContext ctx;
    private long parentIntents;
    private long childSubmissions;
    private long timerSchedules;
    private long timerFirings;
    private long parentCancels;
    private long capacityRejects;
    private long allChildrenRejected;
    private long residualCancels;
    private long malformedRejects;
    private long emptyLiquidityRejects;

    public ParallelVenueExecution() {
        this(1L, DEFAULT_MAX_ACTIVE_PARENTS);
    }

    public ParallelVenueExecution(final long minimumSliceQtyScaled, final int maxActiveParents) {
        if (minimumSliceQtyScaled <= 0L) {
            throw new IllegalArgumentException("minimumSliceQtyScaled must be positive");
        }
        if (maxActiveParents <= 0) {
            throw new IllegalArgumentException("maxActiveParents must be positive");
        }
        this.minimumSliceQtyScaled = minimumSliceQtyScaled;
        activeParentIds = new long[maxActiveParents];
        activeChildCounts = new int[maxActiveParents];
        activeRejectedCounts = new int[maxActiveParents];
        activeFilledQty = new long[maxActiveParents];
        activeTimerCorrelationIds = new long[maxActiveParents];
        activeCancelPending = new boolean[maxActiveParents];
        configureVenueSet(1, Ids.VENUE_COINBASE);
        configureVenueSet(2, Ids.VENUE_BINANCE);
        configureVenueSet(7, Ids.VENUE_COINBASE, Ids.VENUE_BINANCE);
    }

    public void configureVenueSet(final int venueSetId, final int... venueIds) {
        if (venueSetId <= 0 || venueSetId >= MAX_VENUE_SETS) {
            throw new IllegalArgumentException("venueSetId out of range: " + venueSetId);
        }
        if (venueIds == null || venueIds.length == 0 || venueIds.length > MAX_VENUES_PER_SET) {
            throw new IllegalArgumentException("venue set must contain 1.." + MAX_VENUES_PER_SET + " venues");
        }
        venueSetCounts[venueSetId] = venueIds.length;
        Arrays.fill(venueSets[venueSetId], 0);
        for (int i = 0; i < venueIds.length; i++) {
            if (venueIds[i] <= 0 || venueIds[i] > Ids.MAX_VENUES) {
                throw new IllegalArgumentException("venue id out of range: " + venueIds[i]);
            }
            venueSets[venueSetId][i] = venueIds[i];
        }
    }

    @Override public int executionStrategyId() { return ExecutionStrategyIds.PARALLEL_VENUE; }

    @Override
    public void init(final ExecutionStrategyContext ctx) {
        this.ctx = Objects.requireNonNull(ctx, "ctx");
    }

    @Override
    public void onParentIntent(final ParentOrderIntentView intent) {
        requireInitialized();
        parentIntents++;
        if (intent.parentOrderId() <= 0L || intent.quantityScaled() <= 0L || intent.intentType() != ParentIntentType.HEDGE) {
            malformedRejects++;
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

        final int activeSlot = claimActiveSlot(intent.parentOrderId());
        if (activeSlot < 0) {
            capacityRejects++;
            terminal(intent.parentOrderId(), ParentOrderState.FAILED, ParentOrderState.REASON_CAPACITY_REJECTED);
            return;
        }

        final int planCount = computeSlicePlan(intent);
        if (planCount == 0) {
            emptyLiquidityRejects++;
            releaseActiveSlot(activeSlot);
            terminal(intent.parentOrderId(), ParentOrderState.FAILED, ParentOrderState.REASON_CHILD_REJECTED);
            return;
        }

        final long base = intent.correlationId() > 0L ? intent.correlationId() : intent.parentOrderId();
        for (int i = 0; i < planCount; i++) {
            final long childClOrdId = base + i + 1L;
            if (!ctx.parentOrderRegistry().linkChild(intent.parentOrderId(), childClOrdId)) {
                capacityRejects++;
                releaseActiveSlot(activeSlot);
                terminal(intent.parentOrderId(), ParentOrderState.FAILED, ParentOrderState.REASON_CAPACITY_REJECTED);
                return;
            }
            final int venueId = planVenueIds[i];
            final long childQty = planQtys[i];
            final long price = priceFor(intent, venueId);
            final RiskDecision risk = ctx.riskEngine().preTradeCheck(
                venueId, intent.instrumentId(), intent.side().value(), price, childQty, intent.strategyId());
            if (!risk.approved()) {
                ctx.parentOrderRegistry().unlinkChild(childClOrdId);
                activeRejectedCounts[activeSlot]++;
                continue;
            }
            createChild(intent, childClOrdId, venueId, price, childQty);
            activeChildCounts[activeSlot]++;
            childSubmissions++;
        }
        if (activeChildCounts[activeSlot] == 0) {
            allChildrenRejected++;
            releaseActiveSlot(activeSlot);
            terminal(intent.parentOrderId(), ParentOrderState.FAILED, ParentOrderState.REASON_ALL_CHILDREN_REJECTED);
            return;
        }
        if (intent.parentTimeoutMicros() > 0L) {
            final long timerCorrelationId = base + 10_000L;
            if (!ctx.timerScheduler().scheduleTimer(timerCorrelationId,
                ctx.clock().clusterTimeMicros() + intent.parentTimeoutMicros(), executionStrategyId())) {
                capacityRejects++;
                cancelChildren(intent.parentOrderId());
                releaseActiveSlot(activeSlot);
                terminal(intent.parentOrderId(), ParentOrderState.FAILED, ParentOrderState.REASON_EXECUTION_ABORTED);
                return;
            }
            activeTimerCorrelationIds[activeSlot] = timerCorrelationId;
            timerSchedules++;
        }
        ctx.parentOrderRegistry().transition(intent.parentOrderId(), ParentOrderState.WORKING,
            ParentOrderState.REASON_NONE, ctx.clock().clusterTimeMicros());
    }

    @Override public void onMarketDataTick(final int venueId, final int instrumentId, final long clusterTimeMicros) { }

    @Override
    public void onChildExecution(final ChildExecutionView execution) {
        requireInitialized();
        final int slot = activeSlot(execution.parentOrderId());
        if (slot < 0) {
            return;
        }
        final ExecType execType = execution.execType();
        if (execType == ExecType.REJECTED) {
            activeRejectedCounts[slot]++;
            ctx.parentOrderRegistry().unlinkChild(execution.childClOrdId());
            if (ctx.parentOrderRegistry().activeChildCount(execution.parentOrderId()) == 0 && activeFilledQty[slot] == 0L) {
                allChildrenRejected++;
                releaseActiveSlot(slot);
                terminal(execution.parentOrderId(), ParentOrderState.FAILED, ParentOrderState.REASON_ALL_CHILDREN_REJECTED);
            }
            return;
        }
        if (execType == ExecType.FILL || execType == ExecType.PARTIAL_FILL) {
            activeFilledQty[slot] += execution.fillQtyScaled();
            final ParentOrderState parent = ctx.parentOrderRegistry().lookup(execution.parentOrderId());
            final long remaining = Math.max(0L, parent.requestedQtyScaled() - activeFilledQty[slot]);
            ctx.parentOrderRegistry().updateFill(execution.parentOrderId(), activeFilledQty[slot], remaining, execution.fillPriceScaled());
            if (execution.finalExecution() || execution.leavesQtyScaled() == 0L) {
                ctx.parentOrderRegistry().unlinkChild(execution.childClOrdId());
            }
            if (remaining == 0L || ctx.parentOrderRegistry().activeChildCount(execution.parentOrderId()) == 0) {
                releaseActiveSlot(slot);
                terminal(execution.parentOrderId(), ParentOrderState.DONE, ParentOrderState.REASON_COMPLETED);
            } else {
                ctx.parentOrderRegistry().transition(execution.parentOrderId(), ParentOrderState.PARTIALLY_FILLED,
                    ParentOrderState.REASON_NONE, ctx.clock().clusterTimeMicros());
            }
        }
    }

    @Override
    public void onTimer(final long correlationId) {
        requireInitialized();
        final int slot = activeTimerSlot(correlationId);
        if (slot < 0) {
            return;
        }
        timerFirings++;
        final long parentOrderId = activeParentIds[slot];
        cancelChildren(parentOrderId);
        residualCancels++;
        releaseActiveSlot(slot);
        terminal(parentOrderId, ParentOrderState.EXPIRED, ParentOrderState.REASON_LEG_TIMER_RESIDUAL_CANCELED);
    }

    @Override
    public void onCancel(final long parentOrderId, final byte reasonCode) {
        requireInitialized();
        final int slot = activeSlot(parentOrderId);
        if (slot < 0) {
            return;
        }
        parentCancels++;
        activeCancelPending[slot] = true;
        cancelChildren(parentOrderId);
        releaseActiveSlot(slot);
        terminal(parentOrderId, ParentOrderState.CANCELED, ParentOrderState.REASON_CANCELED_BY_PARENT);
    }

    public void snapshotInto(final Snapshot snapshot) {
        System.arraycopy(activeParentIds, 0, snapshot.activeParentIds, 0, activeParentIds.length);
        System.arraycopy(activeChildCounts, 0, snapshot.activeChildCounts, 0, activeChildCounts.length);
        System.arraycopy(activeRejectedCounts, 0, snapshot.activeRejectedCounts, 0, activeRejectedCounts.length);
        System.arraycopy(activeFilledQty, 0, snapshot.activeFilledQty, 0, activeFilledQty.length);
        System.arraycopy(activeTimerCorrelationIds, 0, snapshot.activeTimerCorrelationIds, 0, activeTimerCorrelationIds.length);
        System.arraycopy(activeCancelPending, 0, snapshot.activeCancelPending, 0, activeCancelPending.length);
        snapshot.parentIntents = parentIntents;
        snapshot.childSubmissions = childSubmissions;
        snapshot.timerSchedules = timerSchedules;
        snapshot.timerFirings = timerFirings;
        snapshot.parentCancels = parentCancels;
        snapshot.capacityRejects = capacityRejects;
        snapshot.allChildrenRejected = allChildrenRejected;
        snapshot.residualCancels = residualCancels;
        snapshot.malformedRejects = malformedRejects;
        snapshot.emptyLiquidityRejects = emptyLiquidityRejects;
    }

    public void loadFrom(final Snapshot snapshot) {
        System.arraycopy(snapshot.activeParentIds, 0, activeParentIds, 0, activeParentIds.length);
        System.arraycopy(snapshot.activeChildCounts, 0, activeChildCounts, 0, activeChildCounts.length);
        System.arraycopy(snapshot.activeRejectedCounts, 0, activeRejectedCounts, 0, activeRejectedCounts.length);
        System.arraycopy(snapshot.activeFilledQty, 0, activeFilledQty, 0, activeFilledQty.length);
        System.arraycopy(snapshot.activeTimerCorrelationIds, 0, activeTimerCorrelationIds, 0, activeTimerCorrelationIds.length);
        System.arraycopy(snapshot.activeCancelPending, 0, activeCancelPending, 0, activeCancelPending.length);
        parentIntents = snapshot.parentIntents;
        childSubmissions = snapshot.childSubmissions;
        timerSchedules = snapshot.timerSchedules;
        timerFirings = snapshot.timerFirings;
        parentCancels = snapshot.parentCancels;
        capacityRejects = snapshot.capacityRejects;
        allChildrenRejected = snapshot.allChildrenRejected;
        residualCancels = snapshot.residualCancels;
        malformedRejects = snapshot.malformedRejects;
        emptyLiquidityRejects = snapshot.emptyLiquidityRejects;
    }

    public Snapshot newSnapshot() {
        return new Snapshot(activeParentIds.length);
    }

    public int computeSlicePlan(final ParentOrderIntentView intent) {
        final int setIndex = venueSetIndex(intent.venueSetId(), intent.primaryVenueId());
        final int count = venueSetCounts[setIndex];
        long totalDepth = 0L;
        int planCount = 0;
        for (int i = 0; i < count; i++) {
            final int venueId = venueSets[setIndex][i];
            final long depth = executableDepth(intent, venueId);
            if (depth >= minimumSliceQtyScaled) {
                planVenueIds[planCount] = venueId;
                planDepths[planCount] = depth;
                totalDepth += depth;
                planCount++;
            }
        }
        long remaining = intent.quantityScaled();
        for (int i = 0; i < planCount; i++) {
            long qty = i == planCount - 1
                ? remaining
                : Math.min(planDepths[i], intent.quantityScaled() * planDepths[i] / totalDepth);
            if (qty < minimumSliceQtyScaled) {
                qty = 0L;
            }
            planQtys[i] = qty;
            remaining -= qty;
        }
        int compact = 0;
        for (int i = 0; i < planCount; i++) {
            if (planQtys[i] > 0L) {
                planVenueIds[compact] = planVenueIds[i];
                planDepths[compact] = planDepths[i];
                planQtys[compact] = planQtys[i];
                compact++;
            }
        }
        return compact;
    }

    public int plannedVenueId(final int index) { return planVenueIds[index]; }
    public long plannedQtyScaled(final int index) { return planQtys[index]; }
    public long parentIntents() { return parentIntents; }
    public long childSubmissions() { return childSubmissions; }
    public long timerSchedules() { return timerSchedules; }
    public long timerFirings() { return timerFirings; }
    public long parentCancels() { return parentCancels; }
    public long capacityRejects() { return capacityRejects; }
    public long allChildrenRejected() { return allChildrenRejected; }
    public long residualCancels() { return residualCancels; }
    public long malformedRejects() { return malformedRejects; }
    public long emptyLiquidityRejects() { return emptyLiquidityRejects; }

    private int venueSetIndex(final int venueSetId, final int fallbackVenueId) {
        if (venueSetId > 0 && venueSetId < MAX_VENUE_SETS && venueSetCounts[venueSetId] > 0) {
            return venueSetId;
        }
        venueSets[0][0] = fallbackVenueId;
        venueSetCounts[0] = fallbackVenueId > 0 ? 1 : 0;
        return 0;
    }

    private long executableDepth(final ParentOrderIntentView intent, final int venueId) {
        final ExternalLiquidityView view = ctx.externalLiquidityView();
        final EntryType side = intent.side() == Side.BUY ? EntryType.ASK : EntryType.BID;
        final long best = intent.side() == Side.BUY
            ? view.externalBestAsk(venueId, intent.instrumentId())
            : view.externalBestBid(venueId, intent.instrumentId());
        if (best == Ids.INVALID_PRICE || !priceExecutable(intent, best)) {
            return 0L;
        }
        return view.externalSizeAt(venueId, intent.instrumentId(), side, best);
    }

    private boolean priceExecutable(final ParentOrderIntentView intent, final long price) {
        final long limit = intent.limitPriceScaled();
        return limit <= 0L || (intent.side() == Side.BUY ? price <= limit : price >= limit);
    }

    private long priceFor(final ParentOrderIntentView intent, final int venueId) {
        final long best = intent.side() == Side.BUY
            ? ctx.externalLiquidityView().externalBestAsk(venueId, intent.instrumentId())
            : ctx.externalLiquidityView().externalBestBid(venueId, intent.instrumentId());
        return best == Ids.INVALID_PRICE ? intent.limitPriceScaled() : best;
    }

    private void createChild(
        final ParentOrderIntentView intent,
        final long childClOrdId,
        final int venueId,
        final long price,
        final long qty
    ) {
        ctx.orderManager().createPendingOrder(childClOrdId, venueId, intent.instrumentId(), intent.side().value(),
            OrdType.LIMIT.value(), TimeInForce.IOC.value(), price, qty, intent.strategyId(), intent.parentOrderId());
        final NewOrderCommandEncoder encoder = ctx.newOrderEncoder();
        encoder.wrapAndApplyHeader(ctx.commandBuffer(), 0, ctx.headerEncoder())
            .clOrdId(childClOrdId)
            .venueId(venueId)
            .instrumentId(intent.instrumentId())
            .side(intent.side())
            .ordType(OrdType.LIMIT)
            .timeInForce(TimeInForce.IOC)
            .priceScaled(price)
            .qtyScaled(qty)
            .strategyId((short) intent.strategyId())
            .parentOrderId(intent.parentOrderId());
    }

    private void cancelChildren(final long parentOrderId) {
        final int count = ctx.parentOrderRegistry().copyActiveChildIds(parentOrderId, childScratch);
        for (int i = 0; i < count; i++) {
            final long childClOrdId = childScratch[i];
            final var child = ctx.orderManager().getOrder(childClOrdId);
            if (child != null) {
                final CancelOrderCommandEncoder encoder = ctx.cancelOrderEncoder();
                encoder.wrapAndApplyHeader(ctx.commandBuffer(), 0, ctx.headerEncoder())
                    .cancelClOrdId(childClOrdId + 1L)
                    .origClOrdId(childClOrdId)
                    .venueId(child.venueId())
                    .instrumentId(child.instrumentId())
                    .side(Side.get(child.side()))
                    .originalQtyScaled(child.qtyScaled())
                    .putVenueOrderId(EMPTY_BYTES, 0, 0);
                ctx.orderManager().markCancelSent(childClOrdId);
            }
            ctx.parentOrderRegistry().unlinkChild(childClOrdId);
        }
    }

    private int claimActiveSlot(final long parentOrderId) {
        for (int i = 0; i < activeParentIds.length; i++) {
            if (activeParentIds[i] == 0L) {
                activeParentIds[i] = parentOrderId;
                activeChildCounts[i] = 0;
                activeRejectedCounts[i] = 0;
                activeFilledQty[i] = 0L;
                activeTimerCorrelationIds[i] = 0L;
                activeCancelPending[i] = false;
                return i;
            }
        }
        return -1;
    }

    private int activeSlot(final long parentOrderId) {
        for (int i = 0; i < activeParentIds.length; i++) {
            if (activeParentIds[i] == parentOrderId) {
                return i;
            }
        }
        return -1;
    }

    private int activeTimerSlot(final long correlationId) {
        for (int i = 0; i < activeTimerCorrelationIds.length; i++) {
            if (activeParentIds[i] != 0L && activeTimerCorrelationIds[i] == correlationId) {
                return i;
            }
        }
        return -1;
    }

    private void releaseActiveSlot(final int slot) {
        activeParentIds[slot] = 0L;
        activeChildCounts[slot] = 0;
        activeRejectedCounts[slot] = 0;
        activeFilledQty[slot] = 0L;
        activeTimerCorrelationIds[slot] = 0L;
        activeCancelPending[slot] = false;
    }

    private void terminal(final long parentOrderId, final byte status, final byte reasonCode) {
        ctx.parentOrderRegistry().transition(parentOrderId, status, reasonCode, ctx.clock().clusterTimeMicros());
    }

    private void requireInitialized() {
        if (ctx == null) {
            throw new IllegalStateException("ParallelVenueExecution is not initialized");
        }
    }

    public static final class Snapshot {
        private final long[] activeParentIds;
        private final int[] activeChildCounts;
        private final int[] activeRejectedCounts;
        private final long[] activeFilledQty;
        private final long[] activeTimerCorrelationIds;
        private final boolean[] activeCancelPending;
        private long parentIntents;
        private long childSubmissions;
        private long timerSchedules;
        private long timerFirings;
        private long parentCancels;
        private long capacityRejects;
        private long allChildrenRejected;
        private long residualCancels;
        private long malformedRejects;
        private long emptyLiquidityRejects;

        private Snapshot(final int capacity) {
            activeParentIds = new long[capacity];
            activeChildCounts = new int[capacity];
            activeRejectedCounts = new int[capacity];
            activeFilledQty = new long[capacity];
            activeTimerCorrelationIds = new long[capacity];
            activeCancelPending = new boolean[capacity];
        }

        public long activeParentId(final int index) {
            return activeParentIds[index];
        }

        public int activeChildCount(final int index) { return activeChildCounts[index]; }
        public long activeFilledQty(final int index) { return activeFilledQty[index]; }
        public long activeTimerCorrelationId(final int index) { return activeTimerCorrelationIds[index]; }
        public long parentIntents() { return parentIntents; }
        public long childSubmissions() { return childSubmissions; }
        public long timerSchedules() { return timerSchedules; }
        public long timerFirings() { return timerFirings; }
        public long parentCancels() { return parentCancels; }
        public long capacityRejects() { return capacityRejects; }
        public long allChildrenRejected() { return allChildrenRejected; }
        public long residualCancels() { return residualCancels; }
        public long malformedRejects() { return malformedRejects; }
        public long emptyLiquidityRejects() { return emptyLiquidityRejects; }
    }
}
