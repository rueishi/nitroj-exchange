package ig.rueishi.nitroj.exchange.execution;

import ig.rueishi.nitroj.exchange.cluster.RiskDecision;
import ig.rueishi.nitroj.exchange.common.ExecutionStrategyIds;
import ig.rueishi.nitroj.exchange.common.Ids;
import ig.rueishi.nitroj.exchange.messages.CancelOrderCommandEncoder;
import ig.rueishi.nitroj.exchange.messages.ExecType;
import ig.rueishi.nitroj.exchange.messages.NewOrderCommandEncoder;
import ig.rueishi.nitroj.exchange.messages.OrdType;
import ig.rueishi.nitroj.exchange.messages.ParentIntentType;
import ig.rueishi.nitroj.exchange.messages.Side;
import ig.rueishi.nitroj.exchange.messages.TimeInForce;

import java.util.Objects;

/**
 * Contingent two-leg execution strategy for V13 arbitrage parents.
 *
 * <p>A multi-leg parent submits two IOC children deterministically from the
 * parent correlation ID. The strategy tracks primitive leg fill state, schedules
 * the parent leg timer through the cluster timer surface, cancels pending legs
 * on timeout/cancel, and hedges any one-leg imbalance. Hedge risk or venue
 * rejection escalates the risk kill switch and terminates the parent with the
 * cooldown-driving {@link ParentOrderState#REASON_HEDGE_FAILED} reason.</p>
 */
public final class MultiLegContingentExecution implements ExecutionStrategy {
    private static final byte[] EMPTY_BYTES = new byte[0];
    private static final byte LEG_PENDING = 0;
    private static final byte LEG_FILLED = 1;
    private static final byte LEG_CANCELED = 2;
    private static final byte LEG_REJECTED = 3;

    private ExecutionStrategyContext ctx;
    private long parentOrderId;
    private int strategyId;
    private int instrumentId;
    private long leg1ClOrdId;
    private long leg2ClOrdId;
    private long hedgeClOrdId;
    private int leg1VenueId;
    private int leg2VenueId;
    private byte leg1Side;
    private byte leg2Side;
    private long leg1PriceScaled;
    private long leg2PriceScaled;
    private long qtyScaled;
    private long leg1FillQtyScaled;
    private long leg2FillQtyScaled;
    private byte leg1Status;
    private byte leg2Status;
    private boolean parentCancelPending;
    private long timerCorrelationId;

    private long parentIntents;
    private long bothLegsFilled;
    private long legRejects;
    private long imbalanceHedges;
    private long hedgeRiskRejects;
    private long hedgeVenueRejects;
    private long timerFirings;
    private long parentCancels;
    private long childFillDuringCancel;
    private long capacityRejects;
    private long timerScheduleRejects;

    @Override
    public int executionStrategyId() {
        return ExecutionStrategyIds.MULTI_LEG_CONTINGENT;
    }

    @Override
    public void init(final ExecutionStrategyContext ctx) {
        this.ctx = Objects.requireNonNull(ctx, "ctx");
    }

    @Override
    public void onParentIntent(final ParentOrderIntentView intent) {
        requireInitialized();
        parentIntents++;
        if (intent.parentOrderId() <= 0L || intent.quantityScaled() <= 0L
            || intent.intentType() != ParentIntentType.MULTI_LEG || intent.legCount() != 2) {
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

        parentOrderId = intent.parentOrderId();
        strategyId = intent.strategyId();
        instrumentId = intent.instrumentId();
        leg1VenueId = intent.primaryVenueId();
        leg2VenueId = intent.secondaryVenueId();
        leg1Side = intent.side().value();
        leg2Side = intent.decoder().leg2SideRaw();
        leg1PriceScaled = intent.limitPriceScaled();
        leg2PriceScaled = intent.decoder().leg2LimitPriceScaled();
        qtyScaled = intent.quantityScaled();
        final long base = intent.correlationId() > 0L ? intent.correlationId() : parentOrderId;
        leg1ClOrdId = base + 1L;
        leg2ClOrdId = base + 2L;
        hedgeClOrdId = base + 3L;
        leg1FillQtyScaled = 0L;
        leg2FillQtyScaled = 0L;
        leg1Status = LEG_PENDING;
        leg2Status = LEG_PENDING;
        parentCancelPending = false;
        timerCorrelationId = base + 10_000L;

        if (!riskApproved(leg1VenueId, leg1Side, leg1PriceScaled, qtyScaled, strategyId)
            || !riskApproved(leg2VenueId, leg2Side, leg2PriceScaled, qtyScaled, strategyId)) {
            ctx.parentOrderRegistry().transition(parentOrderId, ParentOrderState.FAILED,
                ParentOrderState.REASON_RISK_REJECTED, ctx.clock().clusterTimeMicros());
            return;
        }
        if (!ctx.parentOrderRegistry().linkChild(parentOrderId, leg1ClOrdId)
            || !ctx.parentOrderRegistry().linkChild(parentOrderId, leg2ClOrdId)) {
            capacityRejects++;
            ctx.parentOrderRegistry().unlinkChild(leg1ClOrdId);
            ctx.parentOrderRegistry().unlinkChild(leg2ClOrdId);
            ctx.parentOrderRegistry().transition(parentOrderId, ParentOrderState.FAILED,
                ParentOrderState.REASON_CAPACITY_REJECTED, ctx.clock().clusterTimeMicros());
            return;
        }

        if (intent.parentTimeoutMicros() > 0L) {
            if (!ctx.timerScheduler().scheduleTimer(timerCorrelationId,
                ctx.clock().clusterTimeMicros() + intent.parentTimeoutMicros(),
                executionStrategyId())) {
                timerScheduleRejects++;
                ctx.parentOrderRegistry().unlinkChild(leg1ClOrdId);
                ctx.parentOrderRegistry().unlinkChild(leg2ClOrdId);
                leg1Status = LEG_CANCELED;
                leg2Status = LEG_CANCELED;
                terminal(ParentOrderState.FAILED, ParentOrderState.REASON_EXECUTION_ABORTED);
                return;
            }
        }
        createLeg(leg1ClOrdId, leg1VenueId, leg1Side, leg1PriceScaled, strategyId);
        createLeg(leg2ClOrdId, leg2VenueId, leg2Side, leg2PriceScaled, strategyId);
        ctx.parentOrderRegistry().transition(parentOrderId, ParentOrderState.WORKING,
            ParentOrderState.REASON_NONE, ctx.clock().clusterTimeMicros());
    }

    @Override
    public void onMarketDataTick(final int venueId, final int instrumentId, final long clusterTimeMicros) {
        // Multi-leg IOC execution is driven by child reports and timers.
    }

    @Override
    public void onChildExecution(final ChildExecutionView execution) {
        requireInitialized();
        if (execution.parentOrderId() != parentOrderId || parentOrderId == 0L) {
            return;
        }
        if (parentTerminal()) {
            return;
        }
        if (execution.childClOrdId() == hedgeClOrdId) {
            onHedgeExecution(execution);
            return;
        }
        switch (execution.execType()) {
            case FILL, PARTIAL_FILL -> onLegFill(execution);
            case REJECTED -> {
                legRejects++;
                markLeg(execution.childClOrdId(), LEG_REJECTED);
                terminal(ParentOrderState.FAILED, ParentOrderState.REASON_CHILD_REJECTED);
            }
            case CANCELED -> markLeg(execution.childClOrdId(), LEG_CANCELED);
            default -> { }
        }
    }

    @Override
    public void onTimer(final long correlationId) {
        requireInitialized();
        if (correlationId != timerCorrelationId || parentOrderId == 0L) {
            return;
        }
        if (parentTerminal()) {
            return;
        }
        timerFirings++;
        cancelPendingLegs();
        hedgeImbalanceOrComplete();
    }

    @Override
    public void onCancel(final long parentOrderId, final byte reasonCode) {
        requireInitialized();
        if (parentOrderId != this.parentOrderId || parentOrderId == 0L) {
            return;
        }
        if (parentTerminal()) {
            return;
        }
        parentCancels++;
        parentCancelPending = true;
        cancelPendingLegs();
        ctx.parentOrderRegistry().transition(parentOrderId, ParentOrderState.CANCEL_PENDING,
            reasonCode, ctx.clock().clusterTimeMicros());
    }

    public long bothLegsFilled() { return bothLegsFilled; }
    public long legRejects() { return legRejects; }
    public long imbalanceHedges() { return imbalanceHedges; }
    public long hedgeRiskRejects() { return hedgeRiskRejects; }
    public long hedgeVenueRejects() { return hedgeVenueRejects; }
    public long timerFirings() { return timerFirings; }
    public long parentCancels() { return parentCancels; }
    public long childFillDuringCancel() { return childFillDuringCancel; }
    public long capacityRejects() { return capacityRejects; }
    public long timerScheduleRejects() { return timerScheduleRejects; }
    public long timerCorrelationId() { return timerCorrelationId; }

    public Snapshot newSnapshot() {
        return new Snapshot();
    }

    public void snapshotInto(final Snapshot snapshot) {
        snapshot.parentOrderId = parentOrderId;
        snapshot.strategyId = strategyId;
        snapshot.instrumentId = instrumentId;
        snapshot.leg1ClOrdId = leg1ClOrdId;
        snapshot.leg2ClOrdId = leg2ClOrdId;
        snapshot.hedgeClOrdId = hedgeClOrdId;
        snapshot.leg1VenueId = leg1VenueId;
        snapshot.leg2VenueId = leg2VenueId;
        snapshot.leg1Side = leg1Side;
        snapshot.leg2Side = leg2Side;
        snapshot.leg1PriceScaled = leg1PriceScaled;
        snapshot.leg2PriceScaled = leg2PriceScaled;
        snapshot.qtyScaled = qtyScaled;
        snapshot.leg1FillQtyScaled = leg1FillQtyScaled;
        snapshot.leg2FillQtyScaled = leg2FillQtyScaled;
        snapshot.leg1Status = leg1Status;
        snapshot.leg2Status = leg2Status;
        snapshot.parentCancelPending = parentCancelPending;
        snapshot.timerCorrelationId = timerCorrelationId;
        snapshot.parentIntents = parentIntents;
        snapshot.bothLegsFilled = bothLegsFilled;
        snapshot.legRejects = legRejects;
        snapshot.imbalanceHedges = imbalanceHedges;
        snapshot.hedgeRiskRejects = hedgeRiskRejects;
        snapshot.hedgeVenueRejects = hedgeVenueRejects;
        snapshot.timerFirings = timerFirings;
        snapshot.parentCancels = parentCancels;
        snapshot.childFillDuringCancel = childFillDuringCancel;
        snapshot.capacityRejects = capacityRejects;
        snapshot.timerScheduleRejects = timerScheduleRejects;
    }

    public void loadFrom(final Snapshot snapshot) {
        parentOrderId = snapshot.parentOrderId;
        strategyId = snapshot.strategyId;
        instrumentId = snapshot.instrumentId;
        leg1ClOrdId = snapshot.leg1ClOrdId;
        leg2ClOrdId = snapshot.leg2ClOrdId;
        hedgeClOrdId = snapshot.hedgeClOrdId;
        leg1VenueId = snapshot.leg1VenueId;
        leg2VenueId = snapshot.leg2VenueId;
        leg1Side = snapshot.leg1Side;
        leg2Side = snapshot.leg2Side;
        leg1PriceScaled = snapshot.leg1PriceScaled;
        leg2PriceScaled = snapshot.leg2PriceScaled;
        qtyScaled = snapshot.qtyScaled;
        leg1FillQtyScaled = snapshot.leg1FillQtyScaled;
        leg2FillQtyScaled = snapshot.leg2FillQtyScaled;
        leg1Status = snapshot.leg1Status;
        leg2Status = snapshot.leg2Status;
        parentCancelPending = snapshot.parentCancelPending;
        timerCorrelationId = snapshot.timerCorrelationId;
        parentIntents = snapshot.parentIntents;
        bothLegsFilled = snapshot.bothLegsFilled;
        legRejects = snapshot.legRejects;
        imbalanceHedges = snapshot.imbalanceHedges;
        hedgeRiskRejects = snapshot.hedgeRiskRejects;
        hedgeVenueRejects = snapshot.hedgeVenueRejects;
        timerFirings = snapshot.timerFirings;
        parentCancels = snapshot.parentCancels;
        childFillDuringCancel = snapshot.childFillDuringCancel;
        capacityRejects = snapshot.capacityRejects;
        timerScheduleRejects = snapshot.timerScheduleRejects;
    }

    private void onLegFill(final ChildExecutionView execution) {
        if (parentCancelPending) {
            childFillDuringCancel++;
        }
        if (execution.childClOrdId() == leg1ClOrdId) {
            leg1FillQtyScaled = execution.cumQtyScaled() > 0L ? execution.cumQtyScaled() : leg1FillQtyScaled + execution.fillQtyScaled();
            if (execution.finalExecution()) {
                leg1Status = LEG_FILLED;
                ctx.parentOrderRegistry().unlinkChild(leg1ClOrdId);
            }
        } else if (execution.childClOrdId() == leg2ClOrdId) {
            leg2FillQtyScaled = execution.cumQtyScaled() > 0L ? execution.cumQtyScaled() : leg2FillQtyScaled + execution.fillQtyScaled();
            if (execution.finalExecution()) {
                leg2Status = LEG_FILLED;
                ctx.parentOrderRegistry().unlinkChild(leg2ClOrdId);
            }
        }
        ctx.parentOrderRegistry().updateFill(parentOrderId, Math.min(leg1FillQtyScaled, leg2FillQtyScaled),
            Math.max(0L, qtyScaled - Math.min(leg1FillQtyScaled, leg2FillQtyScaled)), execution.fillPriceScaled());
        if (leg1Status == LEG_FILLED && leg2Status == LEG_FILLED) {
            bothLegsFilled++;
            hedgeImbalanceOrComplete();
        } else {
            ctx.parentOrderRegistry().transition(parentOrderId, ParentOrderState.PARTIALLY_FILLED,
                ParentOrderState.REASON_NONE, ctx.clock().clusterTimeMicros());
        }
    }

    private void onHedgeExecution(final ChildExecutionView execution) {
        if (execution.execType() == ExecType.REJECTED) {
            hedgeVenueRejects++;
            ctx.riskEngine().activateKillSwitch("hedge_venue_reject");
            terminal(ParentOrderState.FAILED, ParentOrderState.REASON_HEDGE_FAILED);
        } else if ((execution.execType() == ExecType.FILL || execution.execType() == ExecType.PARTIAL_FILL)
            && execution.finalExecution()) {
            ctx.parentOrderRegistry().unlinkChild(hedgeClOrdId);
            terminal(ParentOrderState.DONE, ParentOrderState.REASON_COMPLETED);
        }
    }

    private void hedgeImbalanceOrComplete() {
        final long imbalance = leg1FillQtyScaled - leg2FillQtyScaled;
        if (imbalance == 0L) {
            terminal(ParentOrderState.DONE, ParentOrderState.REASON_COMPLETED);
            return;
        }
        submitHedge(imbalance);
    }

    private void submitHedge(final long imbalance) {
        imbalanceHedges++;
        final byte hedgeSide = imbalance > 0L ? Side.SELL.value() : Side.BUY.value();
        final int hedgeVenue = imbalance > 0L ? leg2VenueId : leg1VenueId;
        final long hedgePrice = imbalance > 0L ? leg2PriceScaled : leg1PriceScaled;
        final long hedgeQty = Math.abs(imbalance);
        if (!riskApproved(hedgeVenue, hedgeSide, hedgePrice, hedgeQty, Ids.STRATEGY_ARB_HEDGE)) {
            hedgeRiskRejects++;
            ctx.riskEngine().activateKillSwitch("hedge_risk_reject");
            terminal(ParentOrderState.FAILED, ParentOrderState.REASON_HEDGE_FAILED);
            return;
        }
        if (!ctx.parentOrderRegistry().linkChild(parentOrderId, hedgeClOrdId)) {
            capacityRejects++;
            terminal(ParentOrderState.FAILED, ParentOrderState.REASON_CAPACITY_REJECTED);
            return;
        }
        createLeg(hedgeClOrdId, hedgeVenue, hedgeSide, hedgePrice, Ids.STRATEGY_ARB_HEDGE);
        ctx.parentOrderRegistry().transition(parentOrderId, ParentOrderState.HEDGING,
            ParentOrderState.REASON_NONE, ctx.clock().clusterTimeMicros());
    }

    private void cancelPendingLegs() {
        if (leg1Status == LEG_PENDING) {
            encodeCancel(leg1ClOrdId, leg1VenueId, leg1Side);
            ctx.orderManager().markCancelSent(leg1ClOrdId);
        }
        if (leg2Status == LEG_PENDING) {
            encodeCancel(leg2ClOrdId, leg2VenueId, leg2Side);
            ctx.orderManager().markCancelSent(leg2ClOrdId);
        }
    }

    private void terminal(final byte status, final byte reasonCode) {
        ctx.parentOrderRegistry().transition(parentOrderId, status, reasonCode, ctx.clock().clusterTimeMicros());
    }

    private boolean parentTerminal() {
        final ParentOrderState parent = ctx.parentOrderRegistry().lookup(parentOrderId);
        return parent == null || parent.terminal();
    }

    private void markLeg(final long childClOrdId, final byte status) {
        if (childClOrdId == leg1ClOrdId) {
            leg1Status = status;
            ctx.parentOrderRegistry().unlinkChild(leg1ClOrdId);
        } else if (childClOrdId == leg2ClOrdId) {
            leg2Status = status;
            ctx.parentOrderRegistry().unlinkChild(leg2ClOrdId);
        }
    }

    private boolean riskApproved(
        final int venueId,
        final byte side,
        final long priceScaled,
        final long qtyScaled,
        final int strategyId
    ) {
        final RiskDecision decision = ctx.riskEngine().preTradeCheck(
            venueId, instrumentId, side, priceScaled, qtyScaled, strategyId);
        return decision.approved();
    }

    private void createLeg(
        final long childClOrdId,
        final int venueId,
        final byte side,
        final long priceScaled,
        final int strategyId
    ) {
        ctx.orderManager().createPendingOrder(childClOrdId, venueId, instrumentId, side,
            OrdType.LIMIT.value(), TimeInForce.IOC.value(), priceScaled, qtyScaled, strategyId, parentOrderId);
        final NewOrderCommandEncoder encoder = ctx.newOrderEncoder();
        encoder.wrapAndApplyHeader(ctx.commandBuffer(), 0, ctx.headerEncoder())
            .clOrdId(childClOrdId)
            .venueId(venueId)
            .instrumentId(instrumentId)
            .side(Side.get(side))
            .ordType(OrdType.LIMIT)
            .timeInForce(TimeInForce.IOC)
            .priceScaled(priceScaled)
            .qtyScaled(qtyScaled)
            .strategyId((short) strategyId)
            .parentOrderId(parentOrderId);
    }

    private void encodeCancel(final long childClOrdId, final int venueId, final byte side) {
        final CancelOrderCommandEncoder encoder = ctx.cancelOrderEncoder();
        encoder.wrapAndApplyHeader(ctx.commandBuffer(), 0, ctx.headerEncoder())
            .cancelClOrdId(childClOrdId + 100L)
            .origClOrdId(childClOrdId)
            .venueId(venueId)
            .instrumentId(instrumentId)
            .side(Side.get(side))
            .originalQtyScaled(qtyScaled)
            .putVenueOrderId(EMPTY_BYTES, 0, 0);
    }

    private void requireInitialized() {
        if (ctx == null) {
            throw new IllegalStateException("MultiLegContingentExecution is not initialized");
        }
    }

    public static final class Snapshot {
        private long parentOrderId;
        private int strategyId;
        private int instrumentId;
        private long leg1ClOrdId;
        private long leg2ClOrdId;
        private long hedgeClOrdId;
        private int leg1VenueId;
        private int leg2VenueId;
        private byte leg1Side;
        private byte leg2Side;
        private long leg1PriceScaled;
        private long leg2PriceScaled;
        private long qtyScaled;
        private long leg1FillQtyScaled;
        private long leg2FillQtyScaled;
        private byte leg1Status;
        private byte leg2Status;
        private boolean parentCancelPending;
        private long timerCorrelationId;
        private long parentIntents;
        private long bothLegsFilled;
        private long legRejects;
        private long imbalanceHedges;
        private long hedgeRiskRejects;
        private long hedgeVenueRejects;
        private long timerFirings;
        private long parentCancels;
        private long childFillDuringCancel;
        private long capacityRejects;
        private long timerScheduleRejects;

        public long parentOrderId() { return parentOrderId; }
        public long timerCorrelationId() { return timerCorrelationId; }
        public long leg1FillQtyScaled() { return leg1FillQtyScaled; }
        public long leg2FillQtyScaled() { return leg2FillQtyScaled; }
        public long parentIntents() { return parentIntents; }
        public long bothLegsFilled() { return bothLegsFilled; }
        public long legRejects() { return legRejects; }
        public long imbalanceHedges() { return imbalanceHedges; }
        public long hedgeRiskRejects() { return hedgeRiskRejects; }
        public long hedgeVenueRejects() { return hedgeVenueRejects; }
        public long timerFirings() { return timerFirings; }
        public long parentCancels() { return parentCancels; }
        public long childFillDuringCancel() { return childFillDuringCancel; }
        public long capacityRejects() { return capacityRejects; }
        public long timerScheduleRejects() { return timerScheduleRejects; }
    }
}
