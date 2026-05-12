package ig.rueishi.nitroj.exchange.execution;

import ig.rueishi.nitroj.exchange.cluster.InternalMarketView;
import ig.rueishi.nitroj.exchange.cluster.RiskDecision;
import ig.rueishi.nitroj.exchange.cluster.RiskEngine;
import ig.rueishi.nitroj.exchange.common.ExecutionStrategyIds;
import ig.rueishi.nitroj.exchange.common.Ids;
import ig.rueishi.nitroj.exchange.common.OrderStatus;
import ig.rueishi.nitroj.exchange.messages.BooleanType;
import ig.rueishi.nitroj.exchange.messages.CancelOrderCommandEncoder;
import ig.rueishi.nitroj.exchange.messages.ExecType;
import ig.rueishi.nitroj.exchange.messages.ExecutionEventDecoder;
import ig.rueishi.nitroj.exchange.messages.ExecutionEventEncoder;
import ig.rueishi.nitroj.exchange.messages.MessageHeaderDecoder;
import ig.rueishi.nitroj.exchange.messages.MessageHeaderEncoder;
import ig.rueishi.nitroj.exchange.messages.NewOrderCommandEncoder;
import ig.rueishi.nitroj.exchange.messages.ParentIntentType;
import ig.rueishi.nitroj.exchange.messages.ParentOrderIntentDecoder;
import ig.rueishi.nitroj.exchange.messages.ParentOrderIntentEncoder;
import ig.rueishi.nitroj.exchange.messages.PriceMode;
import ig.rueishi.nitroj.exchange.messages.Side;
import ig.rueishi.nitroj.exchange.messages.TimeInForce;
import ig.rueishi.nitroj.exchange.order.OrderManagerImpl;
import ig.rueishi.nitroj.exchange.registry.IdRegistry;
import io.aeron.ExclusivePublication;
import io.aeron.Image;
import io.aeron.cluster.service.Cluster;
import org.agrona.concurrent.UnsafeBuffer;
import org.agrona.concurrent.status.CountersManager;
import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;

import static org.assertj.core.api.Assertions.assertThat;

final class MultiLegContingentExecutionTest {
    private static final long PARENT_ID = 29_001L;
    private static final long BASE_ID = 30_000L;
    private static final long LEG1_ID = BASE_ID + 1L;
    private static final long LEG2_ID = BASE_ID + 2L;
    private static final long HEDGE_ID = BASE_ID + 3L;
    private static final long BUY_PRICE = 65_000L * Ids.SCALE;
    private static final long SELL_PRICE = 65_050L * Ids.SCALE;
    private static final long QTY = Ids.SCALE;

    @Test
    void bothLegsFilled_completesParent() {
        final Harness harness = submitted(false);

        harness.strategy.onChildExecution(child(fill(LEG1_ID, QTY, QTY, 0L, true, "f1"), PARENT_ID));
        harness.strategy.onChildExecution(child(fill(LEG2_ID, QTY, QTY, 0L, true, "f2"), PARENT_ID));

        assertThat(harness.registry.lookup(PARENT_ID).status()).isEqualTo(ParentOrderState.DONE);
        assertThat(harness.registry.lookup(PARENT_ID).terminalReasonCode()).isEqualTo(ParentOrderState.REASON_COMPLETED);
        assertThat(harness.strategy.bothLegsFilled()).isEqualTo(1L);
    }

    @Test
    void oneLegRejected_failsParentWithCooldownReason() {
        final Harness harness = submitted(false);

        harness.strategy.onChildExecution(child(event(LEG2_ID, ExecType.REJECTED, 0L, 0L, 0L, QTY, true, "r2"), PARENT_ID));

        assertThat(harness.registry.lookup(PARENT_ID).status()).isEqualTo(ParentOrderState.FAILED);
        assertThat(harness.registry.lookup(PARENT_ID).terminalReasonCode()).isEqualTo(ParentOrderState.REASON_CHILD_REJECTED);
        assertThat(harness.strategy.legRejects()).isEqualTo(1L);
    }

    @Test
    void oneLegFillImbalanceTimer_submitsHedge() {
        final Harness harness = submitted(false);
        harness.strategy.onChildExecution(child(fill(LEG1_ID, QTY, QTY, 0L, true, "f1"), PARENT_ID));

        harness.strategy.onTimer(harness.strategy.timerCorrelationId());

        assertThat(harness.registry.lookup(PARENT_ID).status()).isEqualTo(ParentOrderState.HEDGING);
        assertThat(harness.registry.parentOrderIdByChild(HEDGE_ID)).isEqualTo(PARENT_ID);
        assertThat(harness.strategy.imbalanceHedges()).isEqualTo(1L);
        assertThat(harness.orderManager.getStatus(LEG2_ID)).isEqualTo(OrderStatus.PENDING_CANCEL);
    }

    @Test
    void hedgeRiskReject_activatesKillSwitchAndFailsParent() {
        final Harness harness = submitted(true);
        harness.strategy.onChildExecution(child(fill(LEG1_ID, QTY, QTY, 0L, true, "f1"), PARENT_ID));

        harness.strategy.onTimer(harness.strategy.timerCorrelationId());

        assertThat(harness.risk.killSwitchActive).isTrue();
        assertThat(harness.registry.lookup(PARENT_ID).status()).isEqualTo(ParentOrderState.FAILED);
        assertThat(harness.registry.lookup(PARENT_ID).terminalReasonCode()).isEqualTo(ParentOrderState.REASON_HEDGE_FAILED);
        assertThat(harness.strategy.hedgeRiskRejects()).isEqualTo(1L);
    }

    @Test
    void hedgeVenueReject_activatesKillSwitchAndFailsParent() {
        final Harness harness = submitted(false);
        harness.strategy.onChildExecution(child(fill(LEG1_ID, QTY, QTY, 0L, true, "f1"), PARENT_ID));
        harness.strategy.onTimer(harness.strategy.timerCorrelationId());

        harness.strategy.onChildExecution(child(event(HEDGE_ID, ExecType.REJECTED, 0L, 0L, 0L, QTY, true, "hr"), PARENT_ID));

        assertThat(harness.risk.killSwitchActive).isTrue();
        assertThat(harness.registry.lookup(PARENT_ID).terminalReasonCode()).isEqualTo(ParentOrderState.REASON_HEDGE_FAILED);
        assertThat(harness.strategy.hedgeVenueRejects()).isEqualTo(1L);
    }

    @Test
    void legTimer_cancelsPendingLegs() {
        final Harness harness = submitted(false);

        harness.strategy.onTimer(harness.strategy.timerCorrelationId());

        assertThat(harness.strategy.timerFirings()).isEqualTo(1L);
        assertThat(harness.orderManager.getStatus(LEG1_ID)).isEqualTo(OrderStatus.PENDING_CANCEL);
        assertThat(harness.orderManager.getStatus(LEG2_ID)).isEqualTo(OrderStatus.PENDING_CANCEL);
    }

    @Test
    void timerScheduleFailure_failsParentDeterministically() {
        final Harness harness = new Harness(false, 8, 8, false);

        harness.strategy.onParentIntent(intent());

        assertThat(harness.registry.lookup(PARENT_ID).status()).isEqualTo(ParentOrderState.FAILED);
        assertThat(harness.registry.lookup(PARENT_ID).terminalReasonCode())
            .isEqualTo(ParentOrderState.REASON_EXECUTION_ABORTED);
        assertThat(harness.strategy.timerScheduleRejects()).isEqualTo(1L);
        assertThat(harness.registry.parentOrderIdByChild(LEG1_ID)).isZero();
        assertThat(harness.registry.parentOrderIdByChild(LEG2_ID)).isZero();
        assertThat(harness.orderManager.getStatus(LEG1_ID)).isEqualTo(Byte.MIN_VALUE);
        assertThat(harness.orderManager.getStatus(LEG2_ID)).isEqualTo(Byte.MIN_VALUE);

        harness.strategy.onChildExecution(child(fill(LEG1_ID, QTY, QTY, 0L, true, "late-f1"), PARENT_ID));

        assertThat(harness.registry.lookup(PARENT_ID).status()).isEqualTo(ParentOrderState.FAILED);
        assertThat(harness.registry.lookup(PARENT_ID).terminalReasonCode())
            .isEqualTo(ParentOrderState.REASON_EXECUTION_ABORTED);
    }

    @Test
    void parentCancel_cancelsBothLegs() {
        final Harness harness = submitted(false);

        harness.strategy.onCancel(PARENT_ID, ParentOrderState.REASON_CANCELED_BY_PARENT);

        assertThat(harness.registry.lookup(PARENT_ID).status()).isEqualTo(ParentOrderState.CANCEL_PENDING);
        assertThat(harness.orderManager.getStatus(LEG1_ID)).isEqualTo(OrderStatus.PENDING_CANCEL);
        assertThat(harness.orderManager.getStatus(LEG2_ID)).isEqualTo(OrderStatus.PENDING_CANCEL);
        assertThat(harness.strategy.parentCancels()).isEqualTo(1L);
    }

    @Test
    void childFillDuringCancel_isCountedAndCanComplete() {
        final Harness harness = submitted(false);
        harness.strategy.onCancel(PARENT_ID, ParentOrderState.REASON_CANCELED_BY_PARENT);

        harness.strategy.onChildExecution(child(fill(LEG1_ID, QTY, QTY, 0L, true, "f1"), PARENT_ID));

        assertThat(harness.strategy.childFillDuringCancel()).isEqualTo(1L);
        assertThat(harness.registry.lookup(PARENT_ID).status()).isEqualTo(ParentOrderState.PARTIALLY_FILLED);
    }

    @Test
    void replay_sameFillSequenceMatchesParentState() {
        final Harness first = submitted(false);
        final Harness second = submitted(false);

        first.strategy.onChildExecution(child(fill(LEG1_ID, QTY, QTY, 0L, true, "f1"), PARENT_ID));
        second.strategy.onChildExecution(child(fill(LEG1_ID, QTY, QTY, 0L, true, "f1"), PARENT_ID));

        assertThat(second.registry.lookup(PARENT_ID).status()).isEqualTo(first.registry.lookup(PARENT_ID).status());
        assertThat(second.registry.lookup(PARENT_ID).filledQtyScaled())
            .isEqualTo(first.registry.lookup(PARENT_ID).filledQtyScaled());
    }

    @Test
    void snapshotLoad_restoresParentAndLegLinks() {
        final Harness harness = submitted(false);
        harness.strategy.onChildExecution(child(fill(LEG1_ID, QTY / 2, QTY / 2, QTY / 2, false, "f1"), PARENT_ID));
        final ParentOrderRegistry.Snapshot snapshot = harness.registry.newSnapshot();
        harness.registry.snapshotInto(snapshot);
        final MultiLegContingentExecution.Snapshot strategySnapshot = harness.strategy.newSnapshot();
        harness.strategy.snapshotInto(strategySnapshot);

        final ParentOrderRegistry restored = new ParentOrderRegistry(8, 8);
        restored.loadFrom(snapshot);
        final MultiLegContingentExecution restoredStrategy = new MultiLegContingentExecution();
        restoredStrategy.loadFrom(strategySnapshot);

        assertThat(restored.lookup(PARENT_ID).status()).isEqualTo(ParentOrderState.PARTIALLY_FILLED);
        assertThat(restored.parentOrderIdByChild(LEG1_ID)).isEqualTo(PARENT_ID);
        assertThat(restored.parentOrderIdByChild(LEG2_ID)).isEqualTo(PARENT_ID);
        assertThat(strategySnapshot.parentOrderId()).isEqualTo(PARENT_ID);
        assertThat(strategySnapshot.leg1FillQtyScaled()).isEqualTo(QTY / 2);
        assertThat(restoredStrategy.timerCorrelationId()).isEqualTo(strategySnapshot.timerCorrelationId());
    }

    @Test
    void counters_capacityRejectRecorded() {
            final Harness harness = new Harness(false, 1, 1);

        harness.strategy.onParentIntent(intent());

        assertThat(harness.registry.lookup(PARENT_ID).status()).isEqualTo(ParentOrderState.FAILED);
        assertThat(harness.strategy.capacityRejects()).isEqualTo(1L);
    }

    private static Harness submitted(final boolean rejectHedgeRisk) {
        final Harness harness = new Harness(rejectHedgeRisk, 8, 8);
        harness.strategy.onParentIntent(intent());
        return harness;
    }

    private static ParentOrderIntentView intent() {
        final UnsafeBuffer buffer = new UnsafeBuffer(new byte[256]);
        new ParentOrderIntentEncoder()
            .wrapAndApplyHeader(buffer, 0, new MessageHeaderEncoder())
            .parentOrderId(PARENT_ID)
            .strategyId((short) Ids.STRATEGY_ARB)
            .executionStrategyId(ExecutionStrategyIds.MULTI_LEG_CONTINGENT)
            .intentType(ParentIntentType.MULTI_LEG)
            .side(Side.BUY)
            .instrumentId(Ids.INSTRUMENT_BTC_USD)
            .primaryVenueId(Ids.VENUE_COINBASE)
            .secondaryVenueId(Ids.VENUE_COINBASE_SANDBOX)
            .quantityScaled(QTY)
            .priceMode(PriceMode.LIMIT)
            .limitPriceScaled(BUY_PRICE)
            .referencePriceScaled(0L)
            .timeInForcePreference(TimeInForce.IOC)
            .urgencyHint((byte) 1)
            .postOnlyPreference(BooleanType.FALSE)
            .selfTradePolicy((byte) 0)
            .correlationId(BASE_ID)
            .legCount((byte) 2)
            .leg2Side(Side.SELL)
            .leg2LimitPriceScaled(SELL_PRICE)
            .parentTimeoutMicros(1_000L);
        final ParentOrderIntentDecoder decoder = new ParentOrderIntentDecoder();
        decoder.wrapAndApplyHeader(buffer, 0, new MessageHeaderDecoder());
        return new ParentOrderIntentView().wrap(decoder);
    }

    private static ChildExecutionView child(final ExecutionEventDecoder decoder, final long parentOrderId) {
        return new ChildExecutionView().wrap(decoder, parentOrderId);
    }

    private static ExecutionEventDecoder fill(
        final long childClOrdId,
        final long fillQty,
        final long cumQty,
        final long leavesQty,
        final boolean isFinal,
        final String execId
    ) {
        return event(childClOrdId, ExecType.FILL, BUY_PRICE, fillQty, cumQty, leavesQty, isFinal, execId);
    }

    private static ExecutionEventDecoder event(
        final long childClOrdId,
        final ExecType execType,
        final long fillPrice,
        final long fillQty,
        final long cumQty,
        final long leavesQty,
        final boolean isFinal,
        final String execId
    ) {
        final UnsafeBuffer buffer = new UnsafeBuffer(new byte[256]);
        final byte[] venueOrderId = ("venue-" + childClOrdId).getBytes(StandardCharsets.US_ASCII);
        final byte[] execIdBytes = execId.getBytes(StandardCharsets.US_ASCII);
        new ExecutionEventEncoder()
            .wrapAndApplyHeader(buffer, 0, new MessageHeaderEncoder())
            .clOrdId(childClOrdId)
            .venueId(Ids.VENUE_COINBASE)
            .instrumentId(Ids.INSTRUMENT_BTC_USD)
            .execType(execType)
            .side(Side.BUY)
            .fillPriceScaled(fillPrice)
            .fillQtyScaled(fillQty)
            .cumQtyScaled(cumQty)
            .leavesQtyScaled(leavesQty)
            .rejectCode(0)
            .ingressTimestampNanos(1L)
            .exchangeTimestampNanos(2L)
            .fixSeqNum(3)
            .isFinal(isFinal ? BooleanType.TRUE : BooleanType.FALSE)
            .putVenueOrderId(venueOrderId, 0, venueOrderId.length)
            .putExecId(execIdBytes, 0, execIdBytes.length);
        final ExecutionEventDecoder decoder = new ExecutionEventDecoder();
        decoder.wrapAndApplyHeader(buffer, 0, new MessageHeaderDecoder());
        return decoder;
    }

    private static CountersManager counters() {
        return new CountersManager(new UnsafeBuffer(new byte[1024 * 1024]), new UnsafeBuffer(new byte[64 * 1024]));
    }

    private static final class Harness {
        final ParentOrderRegistry registry;
        final OrderManagerImpl orderManager = new OrderManagerImpl();
        final UnsafeBuffer commandBuffer = new UnsafeBuffer(new byte[1024]);
        final MultiLegContingentExecution strategy = new MultiLegContingentExecution();
        final RiskStub risk;
        final boolean timerScheduleResult;

        Harness(final boolean rejectHedgeRisk, final int parentCapacity, final int childCapacity) {
            this(rejectHedgeRisk, parentCapacity, childCapacity, true);
        }

        Harness(
            final boolean rejectHedgeRisk,
            final int parentCapacity,
            final int childCapacity,
            final boolean timerScheduleResult) {
            registry = new ParentOrderRegistry(parentCapacity, childCapacity);
            risk = new RiskStub(rejectHedgeRisk);
            this.timerScheduleResult = timerScheduleResult;
            strategy.init(new ExecutionStrategyContext(
                new InternalMarketView(),
                risk,
                orderManager,
                registry,
                commandBuffer,
                new MessageHeaderEncoder(),
                new NewOrderCommandEncoder(),
                new CancelOrderCommandEncoder(),
                () -> 1L,
                (correlationId, deadlineClusterMicros) -> timerScheduleResult,
                new IdRegistryStub(),
                counters()));
        }
    }

    private static final class RiskStub implements RiskEngine {
        final boolean rejectHedgeRisk;
        boolean killSwitchActive;

        RiskStub(final boolean rejectHedgeRisk) {
            this.rejectHedgeRisk = rejectHedgeRisk;
        }

        @Override public RiskDecision preTradeCheck(final int venueId, final int instrumentId, final byte side, final long priceScaled, final long qtyScaled, final int strategyId) {
            return rejectHedgeRisk && strategyId == Ids.STRATEGY_ARB_HEDGE ? RiskDecision.REJECT_MAX_NOTIONAL : RiskDecision.APPROVED;
        }
        @Override public void updatePositionSnapshot(final int venueId, final int instrumentId, final long netQtyScaled) { }
        @Override public void updateDailyPnl(final long realizedPnlDeltaScaled) { }
        @Override public void setRecoveryLock(final int venueId, final boolean locked) { }
        @Override public long getDailyPnlScaled() { return 0L; }
        @Override public void activateKillSwitch(final String reason) { killSwitchActive = true; }
        @Override public void deactivateKillSwitch() { killSwitchActive = false; }
        @Override public boolean isKillSwitchActive() { return killSwitchActive; }
        @Override public void writeSnapshot(final ExclusivePublication snapshotPublication) { }
        @Override public void loadSnapshot(final Image snapshotImage) { }
        @Override public void resetDailyCounters() { }
        @Override public void setCluster(final Cluster cluster) { }
        @Override public void onFill(final ExecutionEventDecoder decoder) { }
        @Override public void resetAll() { }
    }

    private static final class IdRegistryStub implements IdRegistry {
        @Override public int venueId(final long sessionId) { return Ids.VENUE_COINBASE; }
        @Override public int instrumentId(final CharSequence symbol) { return Ids.INSTRUMENT_BTC_USD; }
        @Override public String symbolOf(final int instrumentId) { return "BTC-USD"; }
        @Override public String venueNameOf(final int venueId) { return "coinbase"; }
        @Override public void registerSession(final int venueId, final long sessionId) { }
    }
}
