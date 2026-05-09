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
 * Adaptive venue-indifferent IOC router.
 *
 * <p>SOR ranks executable venue liquidity by taker-fee-adjusted price, greedily
 * fills from best effective price to worst, and re-slices residual quantity on
 * deterministic market-data ticks. Re-slice is deliberately simple and replay
 * safe: cancels for current active children are encoded before any replacement
 * child submission, attempts are rate-limited by cluster time, and failure to
 * produce replacement working children terminates the parent with
 * {@link ParentOrderState#REASON_RESLICE_FAILED}.</p>
 */
public final class SmartOrderRoutingExecution implements ExecutionStrategy {
    private static final byte[] EMPTY_BYTES = new byte[0];
    private static final int DEFAULT_MAX_ACTIVE_PARENTS = 16;
    private static final int MAX_VENUE_SETS = 32;
    private static final int MAX_VENUES_PER_SET = 8;

    private final long minimumSliceQtyScaled;
    private final long minimumResliceIntervalMicros;
    private final FeeSchedule feeSchedule;
    private final long[] activeParentIds;
    private final int[] activeInstrumentIds;
    private final int[] activeStrategyIds;
    private final int[] activeVenueSetIds;
    private final int[] activePrimaryVenueIds;
    private final byte[] activeSides;
    private final long[] activeLimitPrices;
    private final long[] activeRequestedQty;
    private final long[] activeFilledQty;
    private final long[] activeBaseCorrelationIds;
    private final long[] activeTimerCorrelationIds;
    private final long[] activeLastResliceMicros;
    private final int[] activeGenerations;
    private final boolean[] activeCancelPending;
    private final int[] venueSetCounts = new int[MAX_VENUE_SETS];
    private final int[][] venueSets = new int[MAX_VENUE_SETS][MAX_VENUES_PER_SET];
    private final int[] planVenueIds = new int[MAX_VENUES_PER_SET];
    private final long[] planPrices = new long[MAX_VENUES_PER_SET];
    private final long[] planEffectivePrices = new long[MAX_VENUES_PER_SET];
    private final long[] planDepths = new long[MAX_VENUES_PER_SET];
    private final long[] planQtys = new long[MAX_VENUES_PER_SET];
    private final long[] childScratch = new long[MAX_VENUES_PER_SET];

    private ExecutionStrategyContext ctx;
    private long parentIntents;
    private long childSubmissions;
    private long resliceAttempts;
    private long resliceSuccesses;
    private long resliceFailures;
    private long resliceIntervalSkips;
    private long capacityRejects;
    private long malformedRejects;
    private long emptyLiquidityRejects;
    private long allChildrenRejected;
    private long parentCancels;

    public SmartOrderRoutingExecution() {
        this(1L, 1_000L, DEFAULT_MAX_ACTIVE_PARENTS, FeeSchedule.defaults());
    }

    public SmartOrderRoutingExecution(
        final long minimumSliceQtyScaled,
        final long minimumResliceIntervalMicros,
        final int maxActiveParents,
        final FeeSchedule feeSchedule) {
        if (minimumSliceQtyScaled <= 0L) {
            throw new IllegalArgumentException("minimumSliceQtyScaled must be positive");
        }
        if (minimumResliceIntervalMicros < 0L) {
            throw new IllegalArgumentException("minimumResliceIntervalMicros must be non-negative");
        }
        if (maxActiveParents <= 0) {
            throw new IllegalArgumentException("maxActiveParents must be positive");
        }
        this.minimumSliceQtyScaled = minimumSliceQtyScaled;
        this.minimumResliceIntervalMicros = minimumResliceIntervalMicros;
        this.feeSchedule = Objects.requireNonNull(feeSchedule, "feeSchedule");
        activeParentIds = new long[maxActiveParents];
        activeInstrumentIds = new int[maxActiveParents];
        activeStrategyIds = new int[maxActiveParents];
        activeVenueSetIds = new int[maxActiveParents];
        activePrimaryVenueIds = new int[maxActiveParents];
        activeSides = new byte[maxActiveParents];
        activeLimitPrices = new long[maxActiveParents];
        activeRequestedQty = new long[maxActiveParents];
        activeFilledQty = new long[maxActiveParents];
        activeBaseCorrelationIds = new long[maxActiveParents];
        activeTimerCorrelationIds = new long[maxActiveParents];
        activeLastResliceMicros = new long[maxActiveParents];
        activeGenerations = new int[maxActiveParents];
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

    @Override public int executionStrategyId() { return ExecutionStrategyIds.SMART_ORDER_ROUTING; }

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
            intent.parentOrderId(), intent.strategyId(), executionStrategyId(),
            intent.quantityScaled(), ctx.clock().clusterTimeMicros());
        if (parent == null) {
            capacityRejects++;
            return;
        }
        final int slot = claimActiveSlot(intent);
        if (slot < 0) {
            capacityRejects++;
            terminal(intent.parentOrderId(), ParentOrderState.FAILED, ParentOrderState.REASON_CAPACITY_REJECTED);
            return;
        }
        final int planCount = computeSlicePlan(intent);
        if (planCount == 0) {
            emptyLiquidityRejects++;
            releaseActiveSlot(slot);
            terminal(intent.parentOrderId(), ParentOrderState.FAILED, ParentOrderState.REASON_CHILD_REJECTED);
            return;
        }
        final int submitted = submitPlan(slot, planCount, false);
        if (submitted == 0) {
            allChildrenRejected++;
            releaseActiveSlot(slot);
            terminal(intent.parentOrderId(), ParentOrderState.FAILED, ParentOrderState.REASON_ALL_CHILDREN_REJECTED);
            return;
        }
        if (intent.parentTimeoutMicros() > 0L) {
            final long timerCorrelationId = activeBaseCorrelationIds[slot] + 10_000L;
            if (!ctx.timerScheduler().scheduleTimer(timerCorrelationId,
                ctx.clock().clusterTimeMicros() + intent.parentTimeoutMicros(), executionStrategyId())) {
                capacityRejects++;
                cancelChildren(intent.parentOrderId());
                releaseActiveSlot(slot);
                terminal(intent.parentOrderId(), ParentOrderState.FAILED, ParentOrderState.REASON_EXECUTION_ABORTED);
                return;
            }
            activeTimerCorrelationIds[slot] = timerCorrelationId;
        }
        ctx.parentOrderRegistry().transition(intent.parentOrderId(), ParentOrderState.WORKING,
            ParentOrderState.REASON_NONE, ctx.clock().clusterTimeMicros());
    }

    @Override
    public void onMarketDataTick(final int venueId, final int instrumentId, final long clusterTimeMicros) {
        requireInitialized();
        for (int slot = 0; slot < activeParentIds.length; slot++) {
            if (activeParentIds[slot] == 0L || activeInstrumentIds[slot] != instrumentId || activeCancelPending[slot]) {
                continue;
            }
            final long remaining = Math.max(0L, activeRequestedQty[slot] - activeFilledQty[slot]);
            if (remaining < minimumSliceQtyScaled) {
                continue;
            }
            if (activeLastResliceMicros[slot] != 0L
                && clusterTimeMicros - activeLastResliceMicros[slot] < minimumResliceIntervalMicros) {
                resliceIntervalSkips++;
                continue;
            }
            final int planCount = computeSlicePlan(slot, remaining);
            if (planCount == 0 || !planDiffers(slot, planCount)) {
                continue;
            }
            resliceAttempts++;
            activeLastResliceMicros[slot] = clusterTimeMicros;
            cancelChildren(activeParentIds[slot]);
            activeGenerations[slot]++;
            final int submitted = submitPlan(slot, planCount, true);
            if (submitted == 0) {
                resliceFailures++;
                final long parentOrderId = activeParentIds[slot];
                releaseActiveSlot(slot);
                terminal(parentOrderId, ParentOrderState.FAILED, ParentOrderState.REASON_RESLICE_FAILED);
                return;
            }
            resliceSuccesses++;
            ctx.parentOrderRegistry().transition(activeParentIds[slot], ParentOrderState.WORKING,
                ParentOrderState.REASON_NONE, ctx.clock().clusterTimeMicros());
        }
    }

    @Override
    public void onChildExecution(final ChildExecutionView execution) {
        requireInitialized();
        final int slot = activeSlot(execution.parentOrderId());
        if (slot < 0) {
            return;
        }
        final ExecType execType = execution.execType();
        if (execType == ExecType.REJECTED) {
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
            final long requested = parent == null ? activeRequestedQty[slot] : parent.requestedQtyScaled();
            final long remaining = Math.max(0L, requested - activeFilledQty[slot]);
            ctx.parentOrderRegistry().updateFill(execution.parentOrderId(), activeFilledQty[slot], remaining, execution.fillPriceScaled());
            if (execution.finalExecution() || execution.leavesQtyScaled() == 0L) {
                ctx.parentOrderRegistry().unlinkChild(execution.childClOrdId());
            }
            if (remaining == 0L) {
                releaseActiveSlot(slot);
                terminal(execution.parentOrderId(), ParentOrderState.DONE, ParentOrderState.REASON_COMPLETED);
            } else if (ctx.parentOrderRegistry().activeChildCount(execution.parentOrderId()) == 0) {
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
        final long parentOrderId = activeParentIds[slot];
        cancelChildren(parentOrderId);
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

    public int computeSlicePlan(final ParentOrderIntentView intent) {
        final int setIndex = venueSetIndex(intent.venueSetId(), intent.primaryVenueId());
        return computeSlicePlan(setIndex, intent.instrumentId(), intent.side(), intent.limitPriceScaled(), intent.quantityScaled());
    }

    public int plannedVenueId(final int index) { return planVenueIds[index]; }
    public long plannedQtyScaled(final int index) { return planQtys[index]; }
    public long plannedEffectivePriceScaled(final int index) { return planEffectivePrices[index]; }
    public long parentIntents() { return parentIntents; }
    public long childSubmissions() { return childSubmissions; }
    public long resliceAttempts() { return resliceAttempts; }
    public long resliceSuccesses() { return resliceSuccesses; }
    public long resliceFailures() { return resliceFailures; }
    public long resliceIntervalSkips() { return resliceIntervalSkips; }
    public long capacityRejects() { return capacityRejects; }
    public long malformedRejects() { return malformedRejects; }
    public long emptyLiquidityRejects() { return emptyLiquidityRejects; }
    public long allChildrenRejected() { return allChildrenRejected; }
    public long parentCancels() { return parentCancels; }

    public Snapshot newSnapshot() {
        return new Snapshot(activeParentIds.length);
    }

    public void snapshotInto(final Snapshot snapshot) {
        System.arraycopy(activeParentIds, 0, snapshot.activeParentIds, 0, activeParentIds.length);
        System.arraycopy(activeInstrumentIds, 0, snapshot.activeInstrumentIds, 0, activeInstrumentIds.length);
        System.arraycopy(activeStrategyIds, 0, snapshot.activeStrategyIds, 0, activeStrategyIds.length);
        System.arraycopy(activeVenueSetIds, 0, snapshot.activeVenueSetIds, 0, activeVenueSetIds.length);
        System.arraycopy(activePrimaryVenueIds, 0, snapshot.activePrimaryVenueIds, 0, activePrimaryVenueIds.length);
        System.arraycopy(activeSides, 0, snapshot.activeSides, 0, activeSides.length);
        System.arraycopy(activeLimitPrices, 0, snapshot.activeLimitPrices, 0, activeLimitPrices.length);
        System.arraycopy(activeRequestedQty, 0, snapshot.activeRequestedQty, 0, activeRequestedQty.length);
        System.arraycopy(activeFilledQty, 0, snapshot.activeFilledQty, 0, activeFilledQty.length);
        System.arraycopy(activeBaseCorrelationIds, 0, snapshot.activeBaseCorrelationIds, 0, activeBaseCorrelationIds.length);
        System.arraycopy(activeTimerCorrelationIds, 0, snapshot.activeTimerCorrelationIds, 0, activeTimerCorrelationIds.length);
        System.arraycopy(activeLastResliceMicros, 0, snapshot.activeLastResliceMicros, 0, activeLastResliceMicros.length);
        System.arraycopy(activeGenerations, 0, snapshot.activeGenerations, 0, activeGenerations.length);
        System.arraycopy(activeCancelPending, 0, snapshot.activeCancelPending, 0, activeCancelPending.length);
    }

    public void loadFrom(final Snapshot snapshot) {
        System.arraycopy(snapshot.activeParentIds, 0, activeParentIds, 0, activeParentIds.length);
        System.arraycopy(snapshot.activeInstrumentIds, 0, activeInstrumentIds, 0, activeInstrumentIds.length);
        System.arraycopy(snapshot.activeStrategyIds, 0, activeStrategyIds, 0, activeStrategyIds.length);
        System.arraycopy(snapshot.activeVenueSetIds, 0, activeVenueSetIds, 0, activeVenueSetIds.length);
        System.arraycopy(snapshot.activePrimaryVenueIds, 0, activePrimaryVenueIds, 0, activePrimaryVenueIds.length);
        System.arraycopy(snapshot.activeSides, 0, activeSides, 0, activeSides.length);
        System.arraycopy(snapshot.activeLimitPrices, 0, activeLimitPrices, 0, activeLimitPrices.length);
        System.arraycopy(snapshot.activeRequestedQty, 0, activeRequestedQty, 0, activeRequestedQty.length);
        System.arraycopy(snapshot.activeFilledQty, 0, activeFilledQty, 0, activeFilledQty.length);
        System.arraycopy(snapshot.activeBaseCorrelationIds, 0, activeBaseCorrelationIds, 0, activeBaseCorrelationIds.length);
        System.arraycopy(snapshot.activeTimerCorrelationIds, 0, activeTimerCorrelationIds, 0, activeTimerCorrelationIds.length);
        System.arraycopy(snapshot.activeLastResliceMicros, 0, activeLastResliceMicros, 0, activeLastResliceMicros.length);
        System.arraycopy(snapshot.activeGenerations, 0, activeGenerations, 0, activeGenerations.length);
        System.arraycopy(snapshot.activeCancelPending, 0, activeCancelPending, 0, activeCancelPending.length);
    }

    private int computeSlicePlan(final int slot, final long quantityScaled) {
        final int setIndex = venueSetIndex(activeVenueSetIds[slot], activePrimaryVenueIds[slot]);
        return computeSlicePlan(setIndex, activeInstrumentIds[slot], Side.get(activeSides[slot]),
            activeLimitPrices[slot], quantityScaled);
    }

    private int computeSlicePlan(
        final int setIndex,
        final int instrumentId,
        final Side side,
        final long limitPriceScaled,
        final long quantityScaled) {
        final int count = venueSetCounts[setIndex];
        int planCount = 0;
        for (int i = 0; i < count; i++) {
            final int venueId = venueSets[setIndex][i];
            final long price = bestPrice(venueId, instrumentId, side);
            if (price == Ids.INVALID_PRICE || !priceExecutable(side, limitPriceScaled, price)) {
                continue;
            }
            final EntryType bookSide = side == Side.BUY ? EntryType.ASK : EntryType.BID;
            final long depth = ctx.externalLiquidityView().externalSizeAt(venueId, instrumentId, bookSide, price);
            if (depth < minimumSliceQtyScaled) {
                continue;
            }
            planVenueIds[planCount] = venueId;
            planPrices[planCount] = price;
            planEffectivePrices[planCount] = effectivePrice(side, price, feeSchedule.takerFeeBps(venueId));
            planDepths[planCount] = depth;
            planCount++;
        }
        sortPlan(side, planCount);
        long remaining = quantityScaled;
        int compact = 0;
        for (int i = 0; i < planCount && remaining > 0L; i++) {
            final long qty = Math.min(remaining, planDepths[i]);
            if (qty >= minimumSliceQtyScaled) {
                planVenueIds[compact] = planVenueIds[i];
                planPrices[compact] = planPrices[i];
                planEffectivePrices[compact] = planEffectivePrices[i];
                planDepths[compact] = planDepths[i];
                planQtys[compact] = qty;
                remaining -= qty;
                compact++;
            }
        }
        return compact;
    }

    private void sortPlan(final Side side, final int count) {
        for (int i = 1; i < count; i++) {
            final int venueId = planVenueIds[i];
            final long price = planPrices[i];
            final long effectivePrice = planEffectivePrices[i];
            final long depth = planDepths[i];
            int j = i - 1;
            while (j >= 0 && worseThan(side, planEffectivePrices[j], planDepths[j], effectivePrice, depth)) {
                planVenueIds[j + 1] = planVenueIds[j];
                planPrices[j + 1] = planPrices[j];
                planEffectivePrices[j + 1] = planEffectivePrices[j];
                planDepths[j + 1] = planDepths[j];
                j--;
            }
            planVenueIds[j + 1] = venueId;
            planPrices[j + 1] = price;
            planEffectivePrices[j + 1] = effectivePrice;
            planDepths[j + 1] = depth;
        }
    }

    private boolean worseThan(
        final Side side,
        final long leftEffective,
        final long leftDepth,
        final long rightEffective,
        final long rightDepth) {
        if (leftEffective == rightEffective) {
            return leftDepth < rightDepth;
        }
        return side == Side.BUY ? leftEffective > rightEffective : leftEffective < rightEffective;
    }

    private long effectivePrice(final Side side, final long price, final long takerFeeBps) {
        final long fee = price * takerFeeBps / 10_000L;
        return side == Side.BUY ? price + fee : price - fee;
    }

    private int submitPlan(final int slot, final int planCount, final boolean reslice) {
        int submitted = 0;
        final long parentOrderId = activeParentIds[slot];
        for (int i = 0; i < planCount; i++) {
            final long childClOrdId = activeBaseCorrelationIds[slot] + activeGenerations[slot] * 100L + i + 1L;
            if (!ctx.parentOrderRegistry().linkChild(parentOrderId, childClOrdId)) {
                capacityRejects++;
                continue;
            }
            final int venueId = planVenueIds[i];
            final long price = planPrices[i];
            final long qty = planQtys[i];
            final RiskDecision risk = ctx.riskEngine().preTradeCheck(
                venueId, activeInstrumentIds[slot], activeSides[slot], price, qty, activeStrategyIds[slot]);
            if (!risk.approved()) {
                ctx.parentOrderRegistry().unlinkChild(childClOrdId);
                continue;
            }
            createChild(parentOrderId, childClOrdId, venueId, activeInstrumentIds[slot],
                Side.get(activeSides[slot]), price, qty, activeStrategyIds[slot]);
            childSubmissions++;
            submitted++;
        }
        return submitted;
    }

    private boolean planDiffers(final int slot, final int planCount) {
        final int activeCount = ctx.parentOrderRegistry().copyActiveChildIds(activeParentIds[slot], childScratch);
        if (activeCount != planCount) {
            return true;
        }
        for (int i = 0; i < planCount; i++) {
            final var child = ctx.orderManager().getOrder(childScratch[i]);
            if (child == null || child.venueId() != planVenueIds[i] || child.priceScaled() != planPrices[i]
                || child.qtyScaled() != planQtys[i]) {
                return true;
            }
        }
        return false;
    }

    private long bestPrice(final int venueId, final int instrumentId, final Side side) {
        final ExternalLiquidityView view = ctx.externalLiquidityView();
        return side == Side.BUY
            ? view.externalBestAsk(venueId, instrumentId)
            : view.externalBestBid(venueId, instrumentId);
    }

    private boolean priceExecutable(final Side side, final long limit, final long price) {
        return limit <= 0L || (side == Side.BUY ? price <= limit : price >= limit);
    }

    private int venueSetIndex(final int venueSetId, final int fallbackVenueId) {
        if (venueSetId > 0 && venueSetId < MAX_VENUE_SETS && venueSetCounts[venueSetId] > 0) {
            return venueSetId;
        }
        venueSets[0][0] = fallbackVenueId;
        venueSetCounts[0] = fallbackVenueId > 0 ? 1 : 0;
        return 0;
    }

    private void createChild(
        final long parentOrderId,
        final long childClOrdId,
        final int venueId,
        final int instrumentId,
        final Side side,
        final long price,
        final long qty,
        final int strategyId) {
        ctx.orderManager().createPendingOrder(childClOrdId, venueId, instrumentId, side.value(),
            OrdType.LIMIT.value(), TimeInForce.IOC.value(), price, qty, strategyId, parentOrderId);
        final NewOrderCommandEncoder encoder = ctx.newOrderEncoder();
        encoder.wrapAndApplyHeader(ctx.commandBuffer(), 0, ctx.headerEncoder())
            .clOrdId(childClOrdId)
            .venueId(venueId)
            .instrumentId(instrumentId)
            .side(side)
            .ordType(OrdType.LIMIT)
            .timeInForce(TimeInForce.IOC)
            .priceScaled(price)
            .qtyScaled(qty)
            .strategyId((short) strategyId)
            .parentOrderId(parentOrderId);
    }

    private void cancelChildren(final long parentOrderId) {
        final int count = ctx.parentOrderRegistry().copyActiveChildIds(parentOrderId, childScratch);
        for (int i = 0; i < count; i++) {
            final long childClOrdId = childScratch[i];
            final var child = ctx.orderManager().getOrder(childClOrdId);
            if (child != null) {
                final CancelOrderCommandEncoder encoder = ctx.cancelOrderEncoder();
                encoder.wrapAndApplyHeader(ctx.commandBuffer(), 0, ctx.headerEncoder())
                    .cancelClOrdId(childClOrdId + 50_000L)
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

    private int claimActiveSlot(final ParentOrderIntentView intent) {
        for (int i = 0; i < activeParentIds.length; i++) {
            if (activeParentIds[i] == 0L) {
                activeParentIds[i] = intent.parentOrderId();
                activeInstrumentIds[i] = intent.instrumentId();
                activeStrategyIds[i] = intent.strategyId();
                activeVenueSetIds[i] = intent.venueSetId();
                activePrimaryVenueIds[i] = intent.primaryVenueId();
                activeSides[i] = intent.side().value();
                activeLimitPrices[i] = intent.limitPriceScaled();
                activeRequestedQty[i] = intent.quantityScaled();
                activeFilledQty[i] = 0L;
                activeBaseCorrelationIds[i] = intent.correlationId() > 0L ? intent.correlationId() : intent.parentOrderId();
                activeTimerCorrelationIds[i] = 0L;
                activeLastResliceMicros[i] = 0L;
                activeGenerations[i] = 0;
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
        activeInstrumentIds[slot] = 0;
        activeStrategyIds[slot] = 0;
        activeVenueSetIds[slot] = 0;
        activePrimaryVenueIds[slot] = 0;
        activeSides[slot] = 0;
        activeLimitPrices[slot] = 0L;
        activeRequestedQty[slot] = 0L;
        activeFilledQty[slot] = 0L;
        activeBaseCorrelationIds[slot] = 0L;
        activeTimerCorrelationIds[slot] = 0L;
        activeLastResliceMicros[slot] = 0L;
        activeGenerations[slot] = 0;
        activeCancelPending[slot] = false;
    }

    private void terminal(final long parentOrderId, final byte status, final byte reasonCode) {
        ctx.parentOrderRegistry().transition(parentOrderId, status, reasonCode, ctx.clock().clusterTimeMicros());
    }

    private void requireInitialized() {
        if (ctx == null) {
            throw new IllegalStateException("SmartOrderRoutingExecution is not initialized");
        }
    }

    public static final class Snapshot {
        private final long[] activeParentIds;
        private final int[] activeInstrumentIds;
        private final int[] activeStrategyIds;
        private final int[] activeVenueSetIds;
        private final int[] activePrimaryVenueIds;
        private final byte[] activeSides;
        private final long[] activeLimitPrices;
        private final long[] activeRequestedQty;
        private final long[] activeFilledQty;
        private final long[] activeBaseCorrelationIds;
        private final long[] activeTimerCorrelationIds;
        private final long[] activeLastResliceMicros;
        private final int[] activeGenerations;
        private final boolean[] activeCancelPending;

        private Snapshot(final int capacity) {
            activeParentIds = new long[capacity];
            activeInstrumentIds = new int[capacity];
            activeStrategyIds = new int[capacity];
            activeVenueSetIds = new int[capacity];
            activePrimaryVenueIds = new int[capacity];
            activeSides = new byte[capacity];
            activeLimitPrices = new long[capacity];
            activeRequestedQty = new long[capacity];
            activeFilledQty = new long[capacity];
            activeBaseCorrelationIds = new long[capacity];
            activeTimerCorrelationIds = new long[capacity];
            activeLastResliceMicros = new long[capacity];
            activeGenerations = new int[capacity];
            activeCancelPending = new boolean[capacity];
        }

        public long activeParentId(final int index) { return activeParentIds[index]; }
        public long activeLastResliceMicros(final int index) { return activeLastResliceMicros[index]; }
    }
}
