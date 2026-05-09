package ig.rueishi.nitroj.exchange.execution;

import ig.rueishi.nitroj.exchange.cluster.InternalMarketView;
import ig.rueishi.nitroj.exchange.cluster.RiskDecision;
import ig.rueishi.nitroj.exchange.cluster.RiskEngine;
import ig.rueishi.nitroj.exchange.common.ExecutionStrategyIds;
import ig.rueishi.nitroj.exchange.common.Ids;
import ig.rueishi.nitroj.exchange.common.OrderStatus;
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
import ig.rueishi.nitroj.exchange.order.OrderManager;
import ig.rueishi.nitroj.exchange.order.OrderManagerImpl;
import ig.rueishi.nitroj.exchange.order.OrderState;
import ig.rueishi.nitroj.exchange.registry.IdRegistry;
import io.aeron.ExclusivePublication;
import io.aeron.Image;
import io.aeron.cluster.service.Cluster;
import java.nio.file.Files;
import java.nio.file.Path;
import org.agrona.concurrent.UnsafeBuffer;
import org.agrona.concurrent.status.CountersManager;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

final class SmartOrderRoutingExecutionTest {
    private static final long PRICE = 50_000L * Ids.SCALE;
    private static final long QTY = 100L;

    @Test
    void slicePlan_feeScoringGreedyFillAndMinimumFloor() {
        final FeeSchedule fees = new FeeSchedule();
        fees.set(Ids.VENUE_COINBASE, 0L, 0L);
        fees.set(Ids.VENUE_BINANCE, 0L, 10L);
        final Harness harness = new Harness(10L, 1_000L, 8, 8, false, fees);
        harness.seedAsk(Ids.VENUE_COINBASE, PRICE, 70L);
        harness.seedAsk(Ids.VENUE_BINANCE, PRICE - 10L, 70L);

        final int count = harness.strategy.computeSlicePlan(intent(1L, QTY, PRICE, 1_000L));

        assertThat(count).isEqualTo(2);
        assertThat(harness.strategy.plannedVenueId(0)).isEqualTo(Ids.VENUE_COINBASE);
        assertThat(harness.strategy.plannedQtyScaled(0)).isEqualTo(70L);
        assertThat(harness.strategy.plannedVenueId(1)).isEqualTo(Ids.VENUE_BINANCE);
        assertThat(harness.strategy.plannedQtyScaled(1)).isEqualTo(30L);
        assertThat(harness.strategy.plannedEffectivePriceScaled(0))
            .isLessThan(harness.strategy.plannedEffectivePriceScaled(1));

        harness.seedAsk(Ids.VENUE_BINANCE, PRICE - 10L, 5L);
        assertThat(harness.strategy.computeSlicePlan(intent(2L, QTY, PRICE, 1_000L))).isEqualTo(1);
    }

    @Test
    void slicePlan_tiedEffectivePriceBreaksByDepth() {
        final FeeSchedule fees = new FeeSchedule();
        fees.set(Ids.VENUE_COINBASE, 0L, 0L);
        fees.set(Ids.VENUE_BINANCE, 0L, 0L);
        final Harness harness = new Harness(1L, 1_000L, 8, 8, false, fees);
        harness.seedAsk(Ids.VENUE_COINBASE, PRICE, 40L);
        harness.seedAsk(Ids.VENUE_BINANCE, PRICE, 90L);

        assertThat(harness.strategy.computeSlicePlan(intent(3L, QTY, PRICE, 1_000L))).isEqualTo(2);
        assertThat(harness.strategy.plannedVenueId(0)).isEqualTo(Ids.VENUE_BINANCE);
    }

    @Test
    void feeSchedule_loadsStartupFileAndRejectsMalformed(@TempDir final Path tempDir) throws Exception {
        final Path path = tempDir.resolve("fees.toml");
        Files.writeString(path, """
            [venue.1]
            makerFeeBps = 0
            takerFeeBps = 0
            [venue.2]
            makerFeeBps = 25
            takerFeeBps = 250
            """);

        final FeeSchedule schedule = FeeSchedule.load(path);

        assertThat(schedule.configured(Ids.VENUE_COINBASE)).isTrue();
        assertThat(schedule.makerFeeBps(Ids.VENUE_BINANCE)).isEqualTo(25L);
        assertThat(schedule.takerFeeBps(Ids.VENUE_BINANCE)).isEqualTo(250L);
        assertThat(FeeSchedule.load(Path.of("../config/fees.toml")).configured(Ids.VENUE_BINANCE)).isTrue();

        final Path malformed = tempDir.resolve("bad-fees.toml");
        Files.writeString(malformed, "makerFeeBps = 1\n");
        assertThatThrownBy(() -> FeeSchedule.load(malformed))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("before venue header");
    }

    @Test
    void parentIntentDispatch_reSliceCancelsBeforeReplacementAndEnforcesInterval() {
        final Harness harness = new Harness(1L, 500L, 8, 8, false, zeroFees());
        harness.seedAsk(Ids.VENUE_COINBASE, PRICE, QTY);
        harness.seedAsk(Ids.VENUE_BINANCE, PRICE + 10L, QTY);
        harness.strategy.onParentIntent(intent(10L, QTY, PRICE + 20L, 1_000L));
        assertThat(harness.orders.getOrder(10_001L).venueId()).isEqualTo(Ids.VENUE_COINBASE);

        harness.time = 1_200L;
        harness.seedAsk(Ids.VENUE_BINANCE, PRICE - 20L, QTY);
        harness.strategy.onMarketDataTick(Ids.VENUE_BINANCE, Ids.INSTRUMENT_BTC_USD, harness.time);

        assertThat(harness.orders.events()).containsSubsequence("cancel:10001", "new:10101");
        assertThat(harness.orders.getOrder(10_001L).status()).isEqualTo(OrderStatus.PENDING_CANCEL);
        assertThat(harness.orders.getOrder(10_101L).venueId()).isEqualTo(Ids.VENUE_BINANCE);
        assertThat(harness.strategy.resliceSuccesses()).isEqualTo(1L);

        harness.time = 1_300L;
        harness.seedAsk(Ids.VENUE_COINBASE, PRICE - 40L, QTY);
        harness.strategy.onMarketDataTick(Ids.VENUE_COINBASE, Ids.INSTRUMENT_BTC_USD, harness.time);
        assertThat(harness.strategy.resliceIntervalSkips()).isEqualTo(1L);
        assertThat(harness.orders.getOrder(10_201L)).isNull();
    }

    @Test
    void reSliceFailureTerminalLeavesNoOrphanWorkingChild() {
        final Harness harness = new Harness(1L, 0L, 8, 8, false, zeroFees());
        harness.seedAsk(Ids.VENUE_COINBASE, PRICE, QTY);
        harness.seedAsk(Ids.VENUE_BINANCE, PRICE + 20L, QTY);
        harness.strategy.onParentIntent(intent(11L, QTY, PRICE + 50L, 1_000L));

        harness.risk.reject = true;
        harness.time = 2_000L;
        harness.seedAsk(Ids.VENUE_BINANCE, PRICE - 30L, QTY);
        harness.strategy.onMarketDataTick(Ids.VENUE_BINANCE, Ids.INSTRUMENT_BTC_USD, harness.time);

        assertThat(harness.parents.lookup(11L).status()).isEqualTo(ParentOrderState.FAILED);
        assertThat(harness.parents.lookup(11L).terminalReasonCode()).isEqualTo(ParentOrderState.REASON_RESLICE_FAILED);
        assertThat(harness.parents.activeChildCount(11L)).isZero();
        assertThat(harness.strategy.resliceFailures()).isEqualTo(1L);
    }

    @Test
    void childExecutionDuringReSliceAndParentCancelAreDeterministic() {
        final Harness harness = new Harness(1L, 0L, 8, 8, false, zeroFees());
        harness.seedAsk(Ids.VENUE_COINBASE, PRICE, QTY);
        harness.seedAsk(Ids.VENUE_BINANCE, PRICE + 20L, QTY);
        harness.strategy.onParentIntent(intent(12L, QTY, PRICE + 50L, 1_000L));
        harness.time = 2_000L;
        harness.seedAsk(Ids.VENUE_BINANCE, PRICE - 30L, QTY);
        harness.strategy.onMarketDataTick(Ids.VENUE_BINANCE, Ids.INSTRUMENT_BTC_USD, harness.time);

        harness.strategy.onChildExecution(new ChildExecutionView().wrap(exec(12_001L, ExecType.PARTIAL_FILL, 25L, 75L, false), 12L));

        assertThat(harness.parents.lookup(12L).filledQtyScaled()).isEqualTo(25L);
        assertThat(harness.parents.lookup(12L).remainingQtyScaled()).isEqualTo(75L);
        harness.strategy.onCancel(12L, ParentOrderState.REASON_CANCELED_BY_PARENT);
        assertThat(harness.parents.lookup(12L).status()).isEqualTo(ParentOrderState.CANCELED);
        harness.strategy.onMarketDataTick(Ids.VENUE_BINANCE, Ids.INSTRUMENT_BTC_USD, 3_000L);
        assertThat(harness.strategy.resliceSuccesses()).isEqualTo(1L);
    }

    @Test
    void capacityRiskTimerReplayAndSnapshotCoverage() {
        final Harness capacityHarness = new Harness(1L, 1_000L, 1, 8, false, zeroFees());
        capacityHarness.seedAsk(Ids.VENUE_COINBASE, PRICE, QTY);
        capacityHarness.strategy.onParentIntent(intent(20L, QTY, PRICE, 1_000L));
        capacityHarness.strategy.onParentIntent(intent(21L, QTY, PRICE, 1_000L));
        assertThat(capacityHarness.parents.lookup(21L).terminalReasonCode()).isEqualTo(ParentOrderState.REASON_CAPACITY_REJECTED);

        final Harness riskHarness = new Harness(1L, 1_000L, 8, 8, true, zeroFees());
        riskHarness.seedAsk(Ids.VENUE_COINBASE, PRICE, QTY);
        riskHarness.strategy.onParentIntent(intent(22L, QTY, PRICE, 1_000L));
        assertThat(riskHarness.parents.lookup(22L).terminalReasonCode()).isEqualTo(ParentOrderState.REASON_ALL_CHILDREN_REJECTED);

        final Harness timerHarness = new Harness(1L, 1_000L, 8, 8, false, zeroFees());
        timerHarness.seedAsk(Ids.VENUE_COINBASE, PRICE, QTY);
        timerHarness.strategy.onParentIntent(intent(23L, QTY, PRICE, 1_000L));
        timerHarness.strategy.onTimer(33_000L);
        assertThat(timerHarness.parents.lookup(23L).terminalReasonCode())
            .isEqualTo(ParentOrderState.REASON_LEG_TIMER_RESIDUAL_CANCELED);

        assertThat(replaySummary()).isEqualTo(replaySummary());

        final Harness snapshotHarness = new Harness(1L, 0L, 8, 8, false, zeroFees());
        snapshotHarness.seedAsk(Ids.VENUE_COINBASE, PRICE, QTY);
        snapshotHarness.seedAsk(Ids.VENUE_BINANCE, PRICE + 20L, QTY);
        snapshotHarness.strategy.onParentIntent(intent(24L, QTY, PRICE + 50L, 1_000L));
        snapshotHarness.time = 2_000L;
        snapshotHarness.seedAsk(Ids.VENUE_BINANCE, PRICE - 30L, QTY);
        snapshotHarness.strategy.onMarketDataTick(Ids.VENUE_BINANCE, Ids.INSTRUMENT_BTC_USD, snapshotHarness.time);
        final SmartOrderRoutingExecution.Snapshot snapshot = snapshotHarness.strategy.newSnapshot();
        snapshotHarness.strategy.snapshotInto(snapshot);
        final SmartOrderRoutingExecution restored = new SmartOrderRoutingExecution(1L, 0L, 8, zeroFees());
        restored.loadFrom(snapshot);
        assertThat(snapshot.activeParentId(0)).isEqualTo(24L);
        assertThat(snapshot.activeLastResliceMicros(0)).isEqualTo(2_000L);
    }

    @Test
    void snapshotLoadRestoresPendingParentTimerAndExpiresSorParent() {
        final Harness harness = new Harness(1L, 1_000L, 8, 8, false, zeroFees());
        harness.seedAsk(Ids.VENUE_COINBASE, PRICE, QTY);
        harness.strategy.onParentIntent(intent(40L, QTY, PRICE, 1_000L));
        final SmartOrderRoutingExecution.Snapshot snapshot = harness.strategy.newSnapshot();
        harness.strategy.snapshotInto(snapshot);
        final SmartOrderRoutingExecution restored = new SmartOrderRoutingExecution(1L, 1_000L, 8, zeroFees());
        restored.init(harness.ctx);
        restored.loadFrom(snapshot);

        restored.onTimer(50_000L);

        assertThat(harness.orders.getOrder(40_001L).status()).isEqualTo(OrderStatus.PENDING_CANCEL);
        assertThat(harness.parents.activeChildCount(40L)).isZero();
        assertThat(harness.parents.lookup(40L).status()).isEqualTo(ParentOrderState.EXPIRED);
        assertThat(harness.parents.lookup(40L).terminalReasonCode())
            .isEqualTo(ParentOrderState.REASON_LEG_TIMER_RESIDUAL_CANCELED);
    }

    @Test
    void snapshotLoadRestoresSorParentDuringCancelAndResubmit() {
        final Harness harness = new Harness(1L, 0L, 8, 8, false, zeroFees());
        harness.seedAsk(Ids.VENUE_COINBASE, PRICE, QTY);
        harness.seedAsk(Ids.VENUE_BINANCE, PRICE + 20L, QTY);
        harness.strategy.onParentIntent(intent(41L, QTY, PRICE + 50L, 1_000L));
        harness.time = 2_000L;
        harness.seedAsk(Ids.VENUE_BINANCE, PRICE - 30L, QTY);
        harness.strategy.onMarketDataTick(Ids.VENUE_BINANCE, Ids.INSTRUMENT_BTC_USD, harness.time);
        final SmartOrderRoutingExecution.Snapshot snapshot = harness.strategy.newSnapshot();
        harness.strategy.snapshotInto(snapshot);
        final SmartOrderRoutingExecution restored = new SmartOrderRoutingExecution(1L, 0L, 8, zeroFees());
        restored.init(harness.ctx);
        restored.loadFrom(snapshot);

        restored.onChildExecution(new ChildExecutionView().wrap(exec(41_101L, ExecType.FILL, QTY, 0L, true), 41L));

        assertThat(harness.orders.events()).containsSubsequence("cancel:41001", "new:41101");
        assertThat(harness.orders.getOrder(41_001L).status()).isEqualTo(OrderStatus.PENDING_CANCEL);
        assertThat(harness.parents.lookup(41L).status()).isEqualTo(ParentOrderState.DONE);
        assertThat(harness.parents.activeChildCount(41L)).isZero();
    }

    @Test
    void registryDefaultCompatibilityIncludesSorForInventoryOnly() {
        final ExecutionStrategyRegistry registry = new ExecutionStrategyRegistry(8, 8);
        registry.allowV14DefaultCompatibility();

        assertThat(registry.isCompatible(Ids.STRATEGY_INVENTORY_HEDGE, ExecutionStrategyIds.SMART_ORDER_ROUTING)).isTrue();
        assertThat(registry.isCompatible(Ids.STRATEGY_MARKET_MAKING, ExecutionStrategyIds.SMART_ORDER_ROUTING)).isFalse();
    }

    private static String replaySummary() {
        final Harness harness = new Harness(1L, 0L, 8, 8, false, zeroFees());
        harness.seedAsk(Ids.VENUE_COINBASE, PRICE, QTY);
        harness.seedAsk(Ids.VENUE_BINANCE, PRICE + 10L, QTY);
        harness.strategy.onParentIntent(intent(30L, QTY, PRICE + 20L, 1_000L));
        harness.time = 2_000L;
        harness.seedAsk(Ids.VENUE_BINANCE, PRICE - 20L, QTY);
        harness.strategy.onMarketDataTick(Ids.VENUE_BINANCE, Ids.INSTRUMENT_BTC_USD, harness.time);
        return harness.orders.events() + ":" + harness.parents.lookup(30L).status()
            + ":" + harness.strategy.resliceSuccesses() + ":" + harness.parents.activeChildCount(30L);
    }

    private static FeeSchedule zeroFees() {
        final FeeSchedule fees = new FeeSchedule();
        fees.set(Ids.VENUE_COINBASE, 0L, 0L);
        fees.set(Ids.VENUE_BINANCE, 0L, 0L);
        return fees;
    }

    private static ParentOrderIntentView intent(
        final long parentOrderId,
        final long qty,
        final long limit,
        final long timeoutMicros) {
        final UnsafeBuffer buffer = new UnsafeBuffer(new byte[256]);
        new ParentOrderIntentEncoder()
            .wrapAndApplyHeader(buffer, 0, new MessageHeaderEncoder())
            .parentOrderId(parentOrderId)
            .strategyId((short) Ids.STRATEGY_INVENTORY_HEDGE)
            .executionStrategyId(ExecutionStrategyIds.SMART_ORDER_ROUTING)
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
            .venueSetId(7);
        final ParentOrderIntentDecoder decoder = new ParentOrderIntentDecoder();
        decoder.wrapAndApplyHeader(buffer, 0, new MessageHeaderDecoder());
        return new ParentOrderIntentView().wrap(decoder);
    }

    private static ExecutionEventDecoder exec(
        final long childClOrdId,
        final ExecType execType,
        final long fillQty,
        final long leaves,
        final boolean fin) {
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

    private static MarketDataEventDecoder market(final int venueId, final long price, final long size) {
        final UnsafeBuffer buffer = new UnsafeBuffer(new byte[256]);
        new MarketDataEventEncoder()
            .wrapAndApplyHeader(buffer, 0, new MessageHeaderEncoder())
            .venueId(venueId)
            .instrumentId(Ids.INSTRUMENT_BTC_USD)
            .entryType(EntryType.ASK)
            .priceScaled(price)
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
        final TrackingOrderManager orders = new TrackingOrderManager();
        final RiskStub risk;
        final SmartOrderRoutingExecution strategy;
        final ExecutionStrategyContext ctx;
        long time = 1_000L;

        Harness(
            final long minSlice,
            final long minResliceInterval,
            final int activeParentCapacity,
            final int childCapacity,
            final boolean rejectRisk,
            final FeeSchedule fees) {
            parents = new ParentOrderRegistry(8, childCapacity);
            risk = new RiskStub(rejectRisk);
            strategy = new SmartOrderRoutingExecution(minSlice, minResliceInterval, activeParentCapacity, fees);
            ctx = new ExecutionStrategyContext(
                market,
                risk,
                orders,
                parents,
                new UnsafeBuffer(new byte[1024]),
                new MessageHeaderEncoder(),
                new NewOrderCommandEncoder(),
                new CancelOrderCommandEncoder(),
                () -> time,
                (correlationId, deadlineClusterMicros) -> true,
                new IdsStub(),
                new CountersManager(new UnsafeBuffer(new byte[1024 * 1024]), new UnsafeBuffer(new byte[64 * 1024])));
            strategy.init(ctx);
        }

        void seedAsk(final int venueId, final long price, final long size) {
            market.apply(market(venueId, price, size), time);
        }
    }

    private static final class TrackingOrderManager implements OrderManager {
        private final OrderManagerImpl delegate = new OrderManagerImpl();
        private final StringBuilder events = new StringBuilder();

        @Override public void createPendingOrder(final long clOrdId, final int venueId, final int instrumentId, final byte side, final byte ordType, final byte timeInForce, final long priceScaled, final long qtyScaled, final int strategyId) {
            events.append("new:").append(clOrdId).append(';');
            delegate.createPendingOrder(clOrdId, venueId, instrumentId, side, ordType, timeInForce, priceScaled, qtyScaled, strategyId);
        }
        @Override public void createPendingOrder(final long clOrdId, final int venueId, final int instrumentId, final byte side, final byte ordType, final byte timeInForce, final long priceScaled, final long qtyScaled, final int strategyId, final long parentOrderId) {
            events.append("new:").append(clOrdId).append(';');
            delegate.createPendingOrder(clOrdId, venueId, instrumentId, side, ordType, timeInForce, priceScaled, qtyScaled, strategyId, parentOrderId);
        }
        @Override public void markCancelSent(final long clOrdId) {
            events.append("cancel:").append(clOrdId).append(';');
            delegate.markCancelSent(clOrdId);
        }
        String events() { return events.toString(); }
        @Override public boolean onExecution(final ExecutionEventDecoder decoder) { return delegate.onExecution(decoder); }
        @Override public void cancelAllOrders() { delegate.cancelAllOrders(); }
        @Override public long[] getLiveOrderIds(final int venueId) { return delegate.getLiveOrderIds(venueId); }
        @Override public OrderState getOrder(final long clOrdId) { return delegate.getOrder(clOrdId); }
        @Override public void forceTransitionToCanceled(final long clOrdId) { delegate.forceTransitionToCanceled(clOrdId); }
        @Override public void writeSnapshot(final ExclusivePublication pub) { delegate.writeSnapshot(pub); }
        @Override public void loadSnapshot(final Image image) { delegate.loadSnapshot(image); }
        @Override public void setCluster(final Cluster cluster) { delegate.setCluster(cluster); }
        @Override public void resetAll() { delegate.resetAll(); events.setLength(0); }
    }

    private static final class RiskStub implements RiskEngine {
        boolean reject;
        RiskStub(final boolean reject) { this.reject = reject; }
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
