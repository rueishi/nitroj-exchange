package ig.rueishi.nitroj.exchange.execution;

import ig.rueishi.nitroj.exchange.cluster.InternalMarketView;
import ig.rueishi.nitroj.exchange.cluster.RiskDecision;
import ig.rueishi.nitroj.exchange.cluster.RiskEngine;
import ig.rueishi.nitroj.exchange.common.ExecutionStrategyIds;
import ig.rueishi.nitroj.exchange.common.Ids;
import ig.rueishi.nitroj.exchange.messages.BooleanType;
import ig.rueishi.nitroj.exchange.messages.CancelOrderCommandEncoder;
import ig.rueishi.nitroj.exchange.messages.EntryType;
import ig.rueishi.nitroj.exchange.messages.ExecType;
import ig.rueishi.nitroj.exchange.messages.ExecutionEventDecoder;
import ig.rueishi.nitroj.exchange.messages.ExecutionEventEncoder;
import ig.rueishi.nitroj.exchange.messages.MarketDataEventDecoder;
import ig.rueishi.nitroj.exchange.messages.MarketDataEventEncoder;
import ig.rueishi.nitroj.exchange.messages.MessageHeaderDecoder;
import ig.rueishi.nitroj.exchange.messages.MessageHeaderEncoder;
import ig.rueishi.nitroj.exchange.messages.NewOrderCommandEncoder;
import ig.rueishi.nitroj.exchange.messages.ParentIntentType;
import ig.rueishi.nitroj.exchange.messages.ParentOrderIntentDecoder;
import ig.rueishi.nitroj.exchange.messages.ParentOrderIntentEncoder;
import ig.rueishi.nitroj.exchange.messages.PriceMode;
import ig.rueishi.nitroj.exchange.messages.Side;
import ig.rueishi.nitroj.exchange.messages.TimeInForce;
import ig.rueishi.nitroj.exchange.messages.UpdateAction;
import ig.rueishi.nitroj.exchange.order.OrderManagerImpl;
import ig.rueishi.nitroj.exchange.registry.IdRegistry;
import io.aeron.ExclusivePublication;
import io.aeron.Image;
import io.aeron.cluster.service.Cluster;
import org.agrona.concurrent.UnsafeBuffer;
import org.agrona.concurrent.status.CountersManager;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

final class ParallelVenueExecutionTest {
    private static final long QTY = 100L;
    private static final long PRICE = 50_000L * Ids.SCALE;

    @Test
    void slicePlan_twoVenueFullDepthAndThinVenueAndMinimumFloor() {
        final Harness harness = new Harness(10L, 8, 8, false);
        harness.seedAsk(Ids.VENUE_COINBASE, 60L);
        harness.seedAsk(Ids.VENUE_BINANCE, 40L);

        final int count = harness.strategy.computeSlicePlan(intent(1L, 7, QTY, PRICE, 1_000L));

        assertThat(count).isEqualTo(2);
        assertThat(harness.strategy.plannedVenueId(0)).isEqualTo(Ids.VENUE_COINBASE);
        assertThat(harness.strategy.plannedQtyScaled(0)).isEqualTo(60L);
        assertThat(harness.strategy.plannedVenueId(1)).isEqualTo(Ids.VENUE_BINANCE);
        assertThat(harness.strategy.plannedQtyScaled(1)).isEqualTo(40L);

        harness.seedAsk(Ids.VENUE_BINANCE, 5L);
        assertThat(harness.strategy.computeSlicePlan(intent(2L, 7, QTY, PRICE, 1_000L))).isEqualTo(1);
    }

    @Test
    void singleVenueEmptyLiquidityAndSubmissionOrdering() {
        final Harness harness = new Harness(1L, 8, 8, false);
        assertThat(harness.strategy.computeSlicePlan(intent(1L, 1, QTY, PRICE, 1_000L))).isZero();

        harness.seedAsk(Ids.VENUE_COINBASE, QTY);
        harness.strategy.onParentIntent(intent(2L, 1, QTY, PRICE, 1_000L));

        assertThat(harness.orders.getOrder(2_001L).venueId()).isEqualTo(Ids.VENUE_COINBASE);
        assertThat(harness.parents.lookup(2L).status()).isEqualTo(ParentOrderState.WORKING);
        assertThat(harness.timerSchedules).isEqualTo(1);
    }

    @Test
    void allChildrenRejectedCapacityFullCancelAndTimerResidual() {
        final Harness rejectHarness = new Harness(1L, 8, 8, true);
        rejectHarness.seedAsk(Ids.VENUE_COINBASE, QTY);
        rejectHarness.strategy.onParentIntent(intent(3L, 1, QTY, PRICE, 1_000L));
        assertThat(rejectHarness.parents.lookup(3L).terminalReasonCode()).isEqualTo(ParentOrderState.REASON_ALL_CHILDREN_REJECTED);

        final Harness capacityHarness = new Harness(1L, 8, 1, false);
        capacityHarness.seedAsk(Ids.VENUE_COINBASE, 50L);
        capacityHarness.seedAsk(Ids.VENUE_BINANCE, 50L);
        capacityHarness.strategy.onParentIntent(intent(4L, 7, QTY, PRICE, 1_000L));
        assertThat(capacityHarness.parents.lookup(4L).terminalReasonCode()).isEqualTo(ParentOrderState.REASON_CAPACITY_REJECTED);

        final Harness timerHarness = new Harness(1L, 8, 8, false);
        timerHarness.seedAsk(Ids.VENUE_COINBASE, QTY);
        timerHarness.strategy.onParentIntent(intent(5L, 1, QTY, PRICE, 1_000L));
        timerHarness.strategy.onChildExecution(new ChildExecutionView().wrap(exec(5_001L, ExecType.PARTIAL_FILL, 40L, 60L, false), 5L));
        timerHarness.strategy.onTimer(15_000L);
        assertThat(timerHarness.parents.lookup(5L).terminalReasonCode()).isEqualTo(ParentOrderState.REASON_LEG_TIMER_RESIDUAL_CANCELED);

        final Harness cancelHarness = new Harness(1L, 8, 8, false);
        cancelHarness.seedAsk(Ids.VENUE_COINBASE, QTY);
        cancelHarness.strategy.onParentIntent(intent(6L, 1, QTY, PRICE, 1_000L));
        cancelHarness.strategy.onCancel(6L, ParentOrderState.REASON_CANCELED_BY_PARENT);
        assertThat(cancelHarness.parents.lookup(6L).status()).isEqualTo(ParentOrderState.CANCELED);
    }

    @Test
    void fullFillReplayDeterminismAndSnapshotLoad() {
        assertThat(replaySummary()).isEqualTo(replaySummary());

        final Harness harness = new Harness(1L, 8, 8, false);
        harness.seedAsk(Ids.VENUE_COINBASE, QTY);
        harness.strategy.onParentIntent(intent(7L, 1, QTY, PRICE, 1_000L));
        final ParallelVenueExecution.Snapshot snapshot = harness.strategy.newSnapshot();
        harness.strategy.snapshotInto(snapshot);
        final ParallelVenueExecution restored = new ParallelVenueExecution(1L, 8);
        restored.loadFrom(snapshot);
        assertThat(snapshot.activeParentId(0)).isEqualTo(7L);

        harness.strategy.onChildExecution(new ChildExecutionView().wrap(exec(7_001L, ExecType.FILL, QTY, 0L, true), 7L));
        assertThat(harness.parents.lookup(7L).status()).isEqualTo(ParentOrderState.DONE);
    }

    private static String replaySummary() {
        final Harness harness = new Harness(1L, 8, 8, false);
        harness.seedAsk(Ids.VENUE_COINBASE, 60L);
        harness.seedAsk(Ids.VENUE_BINANCE, 40L);
        harness.strategy.onParentIntent(intent(9L, 7, QTY, PRICE, 1_000L));
        return harness.orders.getOrder(9_001L).venueId() + ":" + harness.orders.getOrder(9_001L).qtyScaled()
            + ":" + harness.orders.getOrder(9_002L).venueId() + ":" + harness.orders.getOrder(9_002L).qtyScaled();
    }

    private static ParentOrderIntentView intent(
        final long parentOrderId,
        final int venueSetId,
        final long qty,
        final long limit,
        final long timeoutMicros
    ) {
        final UnsafeBuffer buffer = new UnsafeBuffer(new byte[256]);
        new ParentOrderIntentEncoder()
            .wrapAndApplyHeader(buffer, 0, new MessageHeaderEncoder())
            .parentOrderId(parentOrderId)
            .strategyId((short) Ids.STRATEGY_INVENTORY_HEDGE)
            .executionStrategyId(ExecutionStrategyIds.PARALLEL_VENUE)
            .intentType(ParentIntentType.HEDGE)
            .side(Side.BUY)
            .instrumentId(Ids.INSTRUMENT_BTC_USD)
            .primaryVenueId(Ids.VENUE_COINBASE)
            .secondaryVenueId(0)
            .quantityScaled(qty)
            .priceMode(PriceMode.LIMIT)
            .limitPriceScaled(limit)
            .referencePriceScaled(limit)
            .timeInForcePreference(TimeInForce.IOC)
            .urgencyHint((byte) 3)
            .postOnlyPreference(BooleanType.FALSE)
            .selfTradePolicy((byte) 1)
            .correlationId(parentOrderId * 1_000L)
            .legCount((byte) 1)
            .leg2Side(Side.NULL_VAL)
            .leg2LimitPriceScaled(0L)
            .parentTimeoutMicros(timeoutMicros)
            .venueSetId(venueSetId);
        final ParentOrderIntentDecoder decoder = new ParentOrderIntentDecoder();
        decoder.wrapAndApplyHeader(buffer, 0, new MessageHeaderDecoder());
        return new ParentOrderIntentView().wrap(decoder);
    }

    private static ExecutionEventDecoder exec(final long childClOrdId, final ExecType execType, final long fillQty, final long leaves, final boolean fin) {
        final UnsafeBuffer buffer = new UnsafeBuffer(new byte[256]);
        new ExecutionEventEncoder()
            .wrapAndApplyHeader(buffer, 0, new MessageHeaderEncoder())
            .clOrdId(childClOrdId)
            .venueId(Ids.VENUE_COINBASE)
            .instrumentId(Ids.INSTRUMENT_BTC_USD)
            .execType(execType)
            .side(Side.BUY)
            .fillPriceScaled(PRICE)
            .fillQtyScaled(fillQty)
            .cumQtyScaled(fillQty)
            .leavesQtyScaled(leaves)
            .rejectCode(0)
            .ingressTimestampNanos(1L)
            .exchangeTimestampNanos(1L)
            .fixSeqNum(1)
            .isFinal(fin ? BooleanType.TRUE : BooleanType.FALSE)
            .putVenueOrderId(new byte[0], 0, 0)
            .putExecId(new byte[0], 0, 0);
        final ExecutionEventDecoder decoder = new ExecutionEventDecoder();
        decoder.wrapAndApplyHeader(buffer, 0, new MessageHeaderDecoder());
        return decoder;
    }

    private static MarketDataEventDecoder market(final int venueId, final long size) {
        final UnsafeBuffer buffer = new UnsafeBuffer(new byte[256]);
        new MarketDataEventEncoder()
            .wrapAndApplyHeader(buffer, 0, new MessageHeaderEncoder())
            .venueId(venueId)
            .instrumentId(Ids.INSTRUMENT_BTC_USD)
            .entryType(EntryType.ASK)
            .priceScaled(PRICE)
            .sizeScaled(size)
            .priceLevel(0)
            .exchangeTimestampNanos(1L)
            .ingressTimestampNanos(1L)
            .fixSeqNum(1)
            .updateAction(UpdateAction.NEW);
        final MarketDataEventDecoder decoder = new MarketDataEventDecoder();
        decoder.wrapAndApplyHeader(buffer, 0, new MessageHeaderDecoder());
        return decoder;
    }

    private static final class Harness {
        final InternalMarketView market = new InternalMarketView(new int[] {1, 2}, new int[] {Ids.INSTRUMENT_BTC_USD});
        final ParentOrderRegistry parents;
        final OrderManagerImpl orders = new OrderManagerImpl();
        final ParallelVenueExecution strategy;
        final RiskStub risk;
        long time = 1_000L;
        int timerSchedules;

        Harness(final long minSlice, final int parentCapacity, final int childCapacity, final boolean rejectRisk) {
            parents = new ParentOrderRegistry(parentCapacity, childCapacity);
            risk = new RiskStub(rejectRisk);
            strategy = new ParallelVenueExecution(minSlice, 8);
            strategy.init(new ExecutionStrategyContext(
                market,
                risk,
                orders,
                parents,
                new UnsafeBuffer(new byte[1024]),
                new MessageHeaderEncoder(),
                new NewOrderCommandEncoder(),
                new CancelOrderCommandEncoder(),
                () -> time,
                (correlationId, deadlineClusterMicros) -> {
                    timerSchedules++;
                    return true;
                },
                new IdsStub(),
                new CountersManager(new UnsafeBuffer(new byte[1024 * 1024]), new UnsafeBuffer(new byte[64 * 1024]))));
        }

        void seedAsk(final int venueId, final long size) {
            market.apply(market(venueId, size), time);
        }
    }

    private record RiskStub(boolean reject) implements RiskEngine {
        @Override public RiskDecision preTradeCheck(final int venueId, final int instrumentId, final byte side, final long priceScaled, final long qtyScaled, final int strategyId) {
            return reject ? RiskDecision.REJECT_MAX_NOTIONAL : RiskDecision.APPROVED;
        }
        @Override public void updatePositionSnapshot(final int venueId, final int instrumentId, final long netQtyScaled) { }
        @Override public void updateDailyPnl(final long realizedPnlDeltaScaled) { }
        @Override public void setRecoveryLock(final int venueId, final boolean locked) { }
        @Override public long getDailyPnlScaled() { return 0L; }
        @Override public void activateKillSwitch(final String reason) { }
        @Override public void deactivateKillSwitch() { }
        @Override public boolean isKillSwitchActive() { return false; }
        @Override public void writeSnapshot(final ExclusivePublication snapshotPublication) { }
        @Override public void loadSnapshot(final Image snapshotImage) { }
        @Override public void resetDailyCounters() { }
        @Override public void setCluster(final Cluster cluster) { }
        @Override public void onFill(final ExecutionEventDecoder decoder) { }
        @Override public void resetAll() { }
    }

    private static final class IdsStub implements IdRegistry {
        @Override public int venueId(final long sessionId) { return Ids.VENUE_COINBASE; }
        @Override public int instrumentId(final CharSequence symbol) { return Ids.INSTRUMENT_BTC_USD; }
        @Override public String symbolOf(final int instrumentId) { return "BTC-USD"; }
        @Override public String venueNameOf(final int venueId) { return "COINBASE"; }
        @Override public void registerSession(final int venueId, final long sessionId) { }
    }
}
