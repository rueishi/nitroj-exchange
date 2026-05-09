package ig.rueishi.nitroj.exchange.strategy;

import ig.rueishi.nitroj.exchange.cluster.InternalMarketView;
import ig.rueishi.nitroj.exchange.cluster.PortfolioEngine;
import ig.rueishi.nitroj.exchange.cluster.RecoveryCoordinator;
import ig.rueishi.nitroj.exchange.cluster.RiskDecision;
import ig.rueishi.nitroj.exchange.cluster.RiskEngine;
import ig.rueishi.nitroj.exchange.common.ConfigValidationException;
import ig.rueishi.nitroj.exchange.common.ExecutionStrategyIds;
import ig.rueishi.nitroj.exchange.common.Ids;
import ig.rueishi.nitroj.exchange.execution.ChildExecutionView;
import ig.rueishi.nitroj.exchange.execution.ExecutionStrategy;
import ig.rueishi.nitroj.exchange.execution.ExecutionStrategyContext;
import ig.rueishi.nitroj.exchange.execution.ExecutionStrategyEngine;
import ig.rueishi.nitroj.exchange.execution.ExecutionStrategyRegistry;
import ig.rueishi.nitroj.exchange.execution.ParentOrderRegistry;
import ig.rueishi.nitroj.exchange.execution.ParentOrderState;
import ig.rueishi.nitroj.exchange.execution.ParentOrderIntentView;
import ig.rueishi.nitroj.exchange.messages.EntryType;
import ig.rueishi.nitroj.exchange.messages.ExecType;
import ig.rueishi.nitroj.exchange.messages.ExecutionEventDecoder;
import ig.rueishi.nitroj.exchange.messages.MessageHeaderDecoder;
import ig.rueishi.nitroj.exchange.messages.MessageHeaderEncoder;
import ig.rueishi.nitroj.exchange.messages.NewOrderCommandEncoder;
import ig.rueishi.nitroj.exchange.messages.CancelOrderCommandEncoder;
import ig.rueishi.nitroj.exchange.messages.OrdType;
import ig.rueishi.nitroj.exchange.messages.ParentIntentType;
import ig.rueishi.nitroj.exchange.messages.ParentOrderTerminalDecoder;
import ig.rueishi.nitroj.exchange.messages.ParentOrderTerminalEncoder;
import ig.rueishi.nitroj.exchange.messages.ParentTerminalReason;
import ig.rueishi.nitroj.exchange.messages.Side;
import ig.rueishi.nitroj.exchange.messages.TimeInForce;
import ig.rueishi.nitroj.exchange.messages.BalanceQueryResponseDecoder;
import ig.rueishi.nitroj.exchange.messages.VenueStatusEventDecoder;
import ig.rueishi.nitroj.exchange.order.OrderManagerImpl;
import ig.rueishi.nitroj.exchange.registry.IdRegistry;
import io.aeron.ExclusivePublication;
import io.aeron.Image;
import io.aeron.Publication;
import io.aeron.cluster.service.Cluster;
import org.agrona.concurrent.UnsafeBuffer;
import org.agrona.concurrent.status.CountersManager;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Proxy;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

final class InventoryHedgeStrategyTest {
    private static final long BTC = Ids.SCALE;
    private static final long PRICE = 50_000L * Ids.SCALE;

    @Test
    void baseQuantityThreshold_emitsHedgeIntentWithVenueSetAndExecutionStrategy() {
        final Harness harness = new Harness(baseConfig());

        harness.position(Ids.VENUE_COINBASE, 2L * BTC);

        assertThat(harness.execution.lastIntent.intentType()).isEqualTo(ParentIntentType.HEDGE);
        assertThat(harness.execution.lastIntent.side()).isEqualTo(Side.SELL);
        assertThat(harness.execution.lastIntent.quantityScaled()).isEqualTo(BTC);
        assertThat(harness.execution.lastIntent.venueSetId()).isEqualTo(7);
        assertThat(harness.execution.lastIntent.executionStrategyId()).isEqualTo(ExecutionStrategyIds.PARALLEL_VENUE);
        assertThat(harness.strategy.activeParentOrderId()).isEqualTo(1_000L);
    }

    @Test
    void notionalThreshold_usesAggregateExposureAndMidPrice() {
        final Harness harness = new Harness(notionalConfig());

        harness.position(Ids.VENUE_COINBASE, 2L * BTC);

        assertThat(harness.execution.lastIntent.side()).isEqualTo(Side.SELL);
        assertThat(harness.execution.lastIntent.quantityScaled()).isEqualTo(BTC);
        assertThat(harness.strategy.lastThresholdMetricScaled()).isEqualTo(100_000L * Ids.SCALE);
    }

    @Test
    void exposureModeFilledOnly_ignoresWorkingOrders() {
        final Harness harness = new Harness(config(
            InventoryHedgeStrategy.ThresholdMode.BASE_QUANTITY,
            InventoryHedgeStrategy.ExposureMode.FILLED_ONLY,
            BTC,
            0L,
            ExecutionStrategyIds.PARALLEL_VENUE));
        harness.workingOrder(100L, Side.SELL, BTC);

        harness.position(Ids.VENUE_COINBASE, BTC / 2L);

        assertThat(harness.execution.parentIntents).isZero();
        assertThat(harness.strategy.lastExposureQtyScaled()).isEqualTo(BTC / 2L);
    }

    @Test
    void exposureModeFilledPlusWorking_countsWorkingFlattenOrders() {
        final Harness harness = new Harness(baseConfig());
        harness.workingOrder(100L, Side.SELL, BTC);

        harness.position(Ids.VENUE_COINBASE, 2L * BTC);

        assertThat(harness.execution.parentIntents).isZero();
        assertThat(harness.strategy.lastExposureQtyScaled()).isEqualTo(BTC);
    }

    @Test
    void safeBand_nonTriggerActiveParentAndCooldownAreEnforced() {
        final Harness harness = new Harness(baseConfig());

        harness.position(Ids.VENUE_COINBASE, BTC);
        assertThat(harness.execution.parentIntents).isZero();

        harness.position(Ids.VENUE_COINBASE, 2L * BTC);
        harness.position(Ids.VENUE_COINBASE, 3L * BTC);
        assertThat(harness.execution.parentIntents).isEqualTo(1L);
        assertThat(harness.strategy.suppressedActiveParentCount()).isEqualTo(1L);

        harness.terminal(ParentTerminalReason.COMPLETED);
        harness.cluster.time = 1_100L;
        harness.position(Ids.VENUE_COINBASE, 3L * BTC);
        assertThat(harness.execution.parentIntents).isEqualTo(1L);
        assertThat(harness.strategy.suppressedCooldownCount()).isEqualTo(1L);

        harness.cluster.time = 2_001L;
        harness.position(Ids.VENUE_COINBASE, 3L * BTC);
        assertThat(harness.execution.parentIntents).isEqualTo(2L);
    }

    @Test
    void failureTerminal_extendsCooldown() {
        final Harness harness = new Harness(baseConfig());

        harness.position(Ids.VENUE_COINBASE, 2L * BTC);
        harness.terminal(ParentTerminalReason.CHILD_REJECTED);

        assertThat(harness.strategy.cooldownUntilMicros()).isEqualTo(3_000L);
    }

    @Test
    void synchronousSubmitReject_doesNotSetActiveParentAndAppliesFailureCooldown() {
        final Harness harness = new Harness(baseConfig(), false);

        harness.position(Ids.VENUE_COINBASE, 2L * BTC);

        assertThat(harness.strategy.activeParentOrderId()).isZero();
        assertThat(harness.strategy.failedSubmitCount()).isEqualTo(1L);
        assertThat(harness.strategy.cooldownUntilMicros()).isEqualTo(3_000L);
    }

    @Test
    void replayDeterminism_sameInputsProduceSameIntentSummary() {
        final String first = replaySummary();
        final String second = replaySummary();

        assertThat(second).isEqualTo(first);
    }

    @Test
    void snapshotLoad_preservesActiveParentAndCooldownState() {
        final Harness first = new Harness(baseConfig());
        first.position(Ids.VENUE_COINBASE, 2L * BTC);
        first.terminal(ParentTerminalReason.COMPLETED);
        final InventoryHedgeStrategy.Snapshot snapshot = new InventoryHedgeStrategy.Snapshot();
        first.strategy.snapshotInto(snapshot);

        final Harness restored = new Harness(baseConfig());
        restored.strategy.loadFrom(snapshot);

        assertThat(restored.strategy.cooldownUntilMicros()).isEqualTo(first.strategy.cooldownUntilMicros());
        assertThat(snapshot.lastExposureQtyScaled()).isEqualTo(2L * BTC);
    }

    @Test
    void configValidation_rejectsInvalidModesThresholdsVenueSetAndExecutionStrategy() {
        assertThatThrownBy(() -> InventoryHedgeStrategy.Config.of(
            Ids.INSTRUMENT_BTC_USD, 1, new int[] { Ids.VENUE_COINBASE }, "bad", BTC, "filled_only", 0L, 0L, 0L, "ParallelVenue", 0L))
            .isInstanceOf(ConfigValidationException.class)
            .hasMessageContaining("thresholdMode");
        assertThatThrownBy(() -> InventoryHedgeStrategy.Config.of(
            Ids.INSTRUMENT_BTC_USD, 1, new int[] { Ids.VENUE_COINBASE }, "base_quantity", 0L, "filled_only", 0L, 0L, 0L, "ParallelVenue", 0L))
            .isInstanceOf(ConfigValidationException.class)
            .hasMessageContaining("thresholdValue");
        assertThatThrownBy(() -> InventoryHedgeStrategy.Config.of(
            Ids.INSTRUMENT_BTC_USD, 1, new int[] { Ids.VENUE_COINBASE }, "base_quantity", BTC, "bad", 0L, 0L, 0L, "ParallelVenue", 0L))
            .isInstanceOf(ConfigValidationException.class)
            .hasMessageContaining("exposureMode");
        assertThatThrownBy(() -> InventoryHedgeStrategy.Config.of(
            Ids.INSTRUMENT_BTC_USD, 0, new int[0], "base_quantity", BTC, "filled_only", 0L, 0L, 0L, "ParallelVenue", 0L))
            .isInstanceOf(ConfigValidationException.class)
            .hasMessageContaining("venueSetId");
        assertThatThrownBy(() -> InventoryHedgeStrategy.Config.of(
            Ids.INSTRUMENT_BTC_USD, 1, new int[] { Ids.VENUE_COINBASE }, "base_quantity", BTC, "filled_only", 0L, 0L, 0L, "PostOnlyQuote", 0L))
            .isInstanceOf(ConfigValidationException.class)
            .hasMessageContaining("execution");
    }

    private static String replaySummary() {
        final Harness harness = new Harness(baseConfig());
        harness.position(Ids.VENUE_COINBASE, 2L * BTC);
        return harness.execution.lastIntent.parentOrderId()
            + ":" + harness.execution.lastIntent.side()
            + ":" + harness.execution.lastIntent.quantityScaled()
            + ":" + harness.execution.lastIntent.venueSetId()
            + ":" + harness.strategy.activeParentOrderId();
    }

    private static InventoryHedgeStrategy.Config baseConfig() {
        return config(
            InventoryHedgeStrategy.ThresholdMode.BASE_QUANTITY,
            InventoryHedgeStrategy.ExposureMode.FILLED_PLUS_WORKING,
            BTC + BTC / 2L,
            BTC,
            ExecutionStrategyIds.PARALLEL_VENUE);
    }

    private static InventoryHedgeStrategy.Config notionalConfig() {
        return config(
            InventoryHedgeStrategy.ThresholdMode.NOTIONAL,
            InventoryHedgeStrategy.ExposureMode.FILLED_ONLY,
            75_000L * Ids.SCALE,
            50_000L * Ids.SCALE,
            ExecutionStrategyIds.SMART_ORDER_ROUTING);
    }

    private static InventoryHedgeStrategy.Config config(
        final InventoryHedgeStrategy.ThresholdMode thresholdMode,
        final InventoryHedgeStrategy.ExposureMode exposureMode,
        final long threshold,
        final long safeBand,
        final int executionStrategyId
    ) {
        return new InventoryHedgeStrategy.Config(
            Ids.INSTRUMENT_BTC_USD,
            7,
            new int[] { Ids.VENUE_COINBASE },
            thresholdMode,
            threshold,
            exposureMode,
            safeBand,
            1_000L,
            2_000L,
            executionStrategyId,
            250L);
    }

    private static final class Harness {
        final InternalMarketView market = new InternalMarketView(
            new int[] { Ids.VENUE_COINBASE },
            new int[] { Ids.INSTRUMENT_BTC_USD });
        final Portfolio portfolio = new Portfolio();
        final OrderManagerImpl orders = new OrderManagerImpl();
        final RecordingCluster cluster = new RecordingCluster();
        final RecordingExecutionStrategy execution;
        final InventoryHedgeStrategy strategy;

        Harness(final InventoryHedgeStrategy.Config config) {
            this(config, true);
        }

        Harness(final InventoryHedgeStrategy.Config config, final boolean allowCompatibility) {
            market.consolidatedBook(Ids.INSTRUMENT_BTC_USD)
                .applyVenueLevel(Ids.VENUE_COINBASE, EntryType.BID, PRICE - Ids.SCALE, 10L * BTC);
            market.consolidatedBook(Ids.INSTRUMENT_BTC_USD)
                .applyVenueLevel(Ids.VENUE_COINBASE, EntryType.ASK, PRICE + Ids.SCALE, 10L * BTC);
            final ParentOrderRegistry parents = new ParentOrderRegistry(16, 16);
            final ExecutionStrategyRegistry registry = new ExecutionStrategyRegistry(8, 8);
            execution = new RecordingExecutionStrategy(config.executionStrategyId());
            registry.register(execution);
            if (allowCompatibility) {
                registry.allowCompatibility(Ids.STRATEGY_INVENTORY_HEDGE, config.executionStrategyId());
            }
            final ExecutionStrategyContext executionContext = new ExecutionStrategyContext(
                market,
                new RiskStub(),
                orders,
                parents,
                new UnsafeBuffer(new byte[1024]),
                new MessageHeaderEncoder(),
                new NewOrderCommandEncoder(),
                new CancelOrderCommandEncoder(),
                () -> cluster.time,
                (correlationId, deadlineClusterMicros) -> true,
                new IdsStub(),
                new CountersManager(new UnsafeBuffer(new byte[1024 * 1024]), new UnsafeBuffer(new byte[64 * 1024])));
            final ExecutionStrategyEngine executionEngine = new ExecutionStrategyEngine(registry, executionContext, 16);
            strategy = new InventoryHedgeStrategy(config);
            strategy.init(new Context(market, portfolio, orders, executionEngine, cluster.proxy));
        }

        void position(final int venueId, final long qty) {
            portfolio.netQty[venueId] = qty;
            strategy.onPositionUpdate(venueId, Ids.INSTRUMENT_BTC_USD, qty, PRICE);
        }

        void workingOrder(final long clOrdId, final Side side, final long qty) {
            orders.createPendingOrder(clOrdId, Ids.VENUE_COINBASE, Ids.INSTRUMENT_BTC_USD,
                side.value(), OrdType.LIMIT.value(), TimeInForce.GTC.value(), PRICE, qty, Ids.STRATEGY_MARKET_MAKING);
        }

        void terminal(final ParentTerminalReason reason) {
            strategy.onParentTerminal(InventoryHedgeStrategyTest.terminal(strategy.activeParentOrderId(), reason));
        }
    }

    private static ParentOrderTerminalDecoder terminal(final long parentOrderId, final ParentTerminalReason reason) {
        final UnsafeBuffer buffer = new UnsafeBuffer(new byte[256]);
        new ParentOrderTerminalEncoder()
            .wrapAndApplyHeader(buffer, 0, new MessageHeaderEncoder())
            .parentOrderId(parentOrderId)
            .strategyId((short) Ids.STRATEGY_INVENTORY_HEDGE)
            .executionStrategyId(ExecutionStrategyIds.PARALLEL_VENUE)
            .terminalReason(reason)
            .finalCumFillQtyScaled(0L)
            .avgFillPriceScaled(0L)
            .lastChildClOrdId(0L)
            .eventClusterTime(1L);
        final ParentOrderTerminalDecoder decoder = new ParentOrderTerminalDecoder();
        decoder.wrapAndApplyHeader(buffer, 0, new MessageHeaderDecoder());
        return decoder;
    }

    private static final class RecordingCluster {
        Cluster proxy;
        long time = 1_000L;
        long logPosition = 1_000L;

        RecordingCluster() {
            proxy = (Cluster) Proxy.newProxyInstance(Cluster.class.getClassLoader(), new Class<?>[] { Cluster.class }, (p, m, a) -> switch (m.getName()) {
                case "time" -> time;
                case "logPosition" -> logPosition++;
                case "timeUnit" -> TimeUnit.MICROSECONDS;
                case "scheduleTimer", "cancelTimer" -> true;
                case "toString" -> "RecordingCluster";
                default -> null;
            });
        }
    }

    private record Context(
        InternalMarketView marketView,
        PortfolioEngine portfolioEngine,
        OrderManagerImpl orderManager,
        ExecutionStrategyEngine executionEngine,
        Cluster cluster
    ) implements StrategyContext {
        @Override public RiskEngine riskEngine() { return new RiskStub(); }
        @Override public RecoveryCoordinator recoveryCoordinator() { return new RecoveryStub(); }
        @Override public UnsafeBuffer egressBuffer() { return new UnsafeBuffer(new byte[1024]); }
        @Override public MessageHeaderEncoder headerEncoder() { return new MessageHeaderEncoder(); }
        @Override public NewOrderCommandEncoder newOrderEncoder() { return new NewOrderCommandEncoder(); }
        @Override public CancelOrderCommandEncoder cancelOrderEncoder() { return new CancelOrderCommandEncoder(); }
        @Override public IdRegistry idRegistry() { return new IdsStub(); }
        @Override public CountersManager counters() {
            return new CountersManager(new UnsafeBuffer(new byte[1024 * 1024]), new UnsafeBuffer(new byte[64 * 1024]));
        }
    }

    private static final class RecordingExecutionStrategy implements ExecutionStrategy {
        private final int id;
        long parentIntents;
        ParentOrderIntentView lastIntent;

        RecordingExecutionStrategy(final int id) {
            this.id = id;
        }

        @Override public int executionStrategyId() { return id; }
        @Override public void init(final ExecutionStrategyContext ctx) { }
        @Override public void onParentIntent(final ParentOrderIntentView intent) {
            parentIntents++;
            lastIntent = copy(intent);
        }
        @Override public void onMarketDataTick(final int venueId, final int instrumentId, final long clusterTimeMicros) { }
        @Override public void onChildExecution(final ChildExecutionView execution) { }
        @Override public void onTimer(final long correlationId) { }
        @Override public void onCancel(final long parentOrderId, final byte reasonCode) { }

        private static ParentOrderIntentView copy(final ParentOrderIntentView intent) {
            final UnsafeBuffer buffer = new UnsafeBuffer(new byte[256]);
            new ig.rueishi.nitroj.exchange.messages.ParentOrderIntentEncoder()
                .wrapAndApplyHeader(buffer, 0, new MessageHeaderEncoder())
                .parentOrderId(intent.parentOrderId())
                .strategyId((short) intent.strategyId())
                .executionStrategyId(intent.executionStrategyId())
                .intentType(intent.intentType())
                .side(intent.side())
                .instrumentId(intent.instrumentId())
                .primaryVenueId(intent.primaryVenueId())
                .secondaryVenueId(intent.secondaryVenueId())
                .quantityScaled(intent.quantityScaled())
                .priceMode(intent.priceMode())
                .limitPriceScaled(intent.limitPriceScaled())
                .referencePriceScaled(intent.referencePriceScaled())
                .timeInForcePreference(intent.timeInForcePreference())
                .urgencyHint(intent.urgencyHint())
                .postOnlyPreference(intent.postOnlyPreference())
                .selfTradePolicy(intent.selfTradePolicy())
                .correlationId(intent.correlationId())
                .legCount(intent.legCount())
                .leg2Side(intent.leg2Side())
                .leg2LimitPriceScaled(intent.leg2LimitPriceScaled())
                .parentTimeoutMicros(intent.parentTimeoutMicros())
                .venueSetId(intent.venueSetId());
            final ig.rueishi.nitroj.exchange.messages.ParentOrderIntentDecoder decoder =
                new ig.rueishi.nitroj.exchange.messages.ParentOrderIntentDecoder();
            decoder.wrapAndApplyHeader(buffer, 0, new MessageHeaderDecoder());
            return new ParentOrderIntentView().wrap(decoder);
        }
    }

    private static final class Portfolio implements PortfolioEngine {
        final long[] netQty = new long[Ids.MAX_VENUES + 1];
        @Override public void initPosition(final int venueId, final int instrumentId) { }
        @Override public void onFill(final ExecutionEventDecoder decoder) { }
        @Override public void refreshUnrealizedPnl(final int venueId, final int instrumentId, final long markPriceScaled) { }
        @Override public long getNetQtyScaled(final int venueId, final int instrumentId) { return netQty[venueId]; }
        @Override public long getAvgEntryPriceScaled(final int venueId, final int instrumentId) { return PRICE; }
        @Override public long unrealizedPnl(final int venueId, final int instrumentId, final long markPriceScaled) { return 0L; }
        @Override public void adjustPosition(final int venueId, final int instrumentId, final double balanceUnscaled) { }
        @Override public long getTotalRealizedPnlScaled() { return 0L; }
        @Override public long getTotalUnrealizedPnlScaled() { return 0L; }
        @Override public void writeSnapshot(final ExclusivePublication snapshotPublication) { }
        @Override public void loadSnapshot(final Image snapshotImage) { }
        @Override public void archiveDailyPnl(final Publication egressPublication) { }
        @Override public void setCluster(final Cluster cluster) { }
        @Override public void resetAll() { }
    }

    private static final class RiskStub implements RiskEngine {
        @Override public RiskDecision preTradeCheck(final int venueId, final int instrumentId, final byte side, final long priceScaled, final long qtyScaled, final int strategyId) { return RiskDecision.APPROVED; }
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

    private static final class RecoveryStub implements RecoveryCoordinator {
        @Override public void onVenueStatus(final VenueStatusEventDecoder decoder) { }
        @Override public void onBalanceResponse(final BalanceQueryResponseDecoder decoder) { }
        @Override public void onTimer(final long correlationId, final long timestamp) { }
        @Override public boolean isInRecovery(final int venueId) { return false; }
        @Override public void reconcileOrder(final ExecutionEventDecoder decoder) { }
        @Override public void writeSnapshot(final ExclusivePublication snapshotPublication) { }
        @Override public void loadSnapshot(final Image snapshotImage) { }
        @Override public void resetAll() { }
        @Override public void setCluster(final Cluster cluster) { }
    }

    private static final class IdsStub implements IdRegistry {
        @Override public int venueId(final long sessionId) { return Ids.VENUE_COINBASE; }
        @Override public int instrumentId(final CharSequence symbol) { return Ids.INSTRUMENT_BTC_USD; }
        @Override public String symbolOf(final int instrumentId) { return "BTC-USD"; }
        @Override public String venueNameOf(final int venueId) { return "COINBASE"; }
        @Override public void registerSession(final int venueId, final long sessionId) { }
    }
}
