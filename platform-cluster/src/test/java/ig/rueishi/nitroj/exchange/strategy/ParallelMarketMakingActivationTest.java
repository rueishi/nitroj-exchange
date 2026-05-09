package ig.rueishi.nitroj.exchange.strategy;

import static org.assertj.core.api.Assertions.assertThat;

import ig.rueishi.nitroj.exchange.cluster.InternalMarketView;
import ig.rueishi.nitroj.exchange.cluster.PortfolioEngine;
import ig.rueishi.nitroj.exchange.cluster.RecoveryCoordinator;
import ig.rueishi.nitroj.exchange.cluster.RiskDecision;
import ig.rueishi.nitroj.exchange.cluster.RiskEngine;
import ig.rueishi.nitroj.exchange.common.ExecutionStrategyIds;
import ig.rueishi.nitroj.exchange.common.Ids;
import ig.rueishi.nitroj.exchange.common.MarketMakingConfig;
import ig.rueishi.nitroj.exchange.execution.ChildExecutionView;
import ig.rueishi.nitroj.exchange.execution.ExecutionStrategy;
import ig.rueishi.nitroj.exchange.execution.ExecutionStrategyContext;
import ig.rueishi.nitroj.exchange.execution.ExecutionStrategyEngine;
import ig.rueishi.nitroj.exchange.execution.ExecutionStrategyRegistry;
import ig.rueishi.nitroj.exchange.execution.ParentOrderIntentView;
import ig.rueishi.nitroj.exchange.execution.ParentOrderRegistry;
import ig.rueishi.nitroj.exchange.execution.PostOnlyQuoteExecution;
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
import ig.rueishi.nitroj.exchange.messages.OrdType;
import ig.rueishi.nitroj.exchange.messages.Side;
import ig.rueishi.nitroj.exchange.messages.TimeInForce;
import ig.rueishi.nitroj.exchange.messages.UpdateAction;
import ig.rueishi.nitroj.exchange.order.OrderManager;
import ig.rueishi.nitroj.exchange.order.OrderState;
import ig.rueishi.nitroj.exchange.registry.IdRegistry;
import io.aeron.ExclusivePublication;
import io.aeron.Image;
import io.aeron.Publication;
import io.aeron.cluster.service.Cluster;
import java.lang.reflect.Proxy;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.TimeUnit;
import org.agrona.concurrent.UnsafeBuffer;
import org.agrona.concurrent.status.CountersManager;
import org.junit.jupiter.api.Test;

/**
 * TASK-411 activation coverage for parallel Coinbase and Binance market making.
 *
 * <p>These tests intentionally change configuration and integration wiring only.
 * They run two independent {@link MarketMakingStrategy} instances through the
 * shared V13 {@link PostOnlyQuoteExecution} plugin and prove venue/instrument
 * state remains scoped. No real Coinbase/Binance QA or credentials are used;
 * real-venue UAT remains blocked until all local V14 gates pass.</p>
 */
final class ParallelMarketMakingActivationTest {
    private static final long QTY = Ids.SCALE;

    @Test
    void independentQuoteComputation_perVenueInstancesUseVenueBooks() {
        final Harness harness = productionHarness();

        quoteCoinbase(harness, 99_900L * Ids.SCALE, 100_100L * Ids.SCALE);
        harness.cluster.logPosition = 2_000L;
        quoteBinance(harness, 64_990L * Ids.SCALE, 65_010L * Ids.SCALE);

        assertThat(harness.orders.orders)
            .extracting(order -> order.venueId)
            .containsExactly(
                Ids.VENUE_COINBASE, Ids.VENUE_COINBASE,
                Ids.VENUE_BINANCE, Ids.VENUE_BINANCE);
        assertThat(harness.coinbase.lastBidPrice()).isEqualTo(99_950L * Ids.SCALE);
        assertThat(harness.coinbase.lastAskPrice()).isEqualTo(100_050L * Ids.SCALE);
        assertThat(harness.binance.lastBidPrice()).isEqualTo(64_960L * Ids.SCALE);
        assertThat(harness.binance.lastAskPrice()).isEqualTo(65_040L * Ids.SCALE);
    }

    @Test
    void independentStalenessExpiry_staleCoinbaseDoesNotSuppressFreshBinance() {
        final Harness harness = productionHarness();
        harness.marketView.apply(marketData(Ids.VENUE_COINBASE, Ids.INSTRUMENT_BTC_USD, EntryType.BID, 99_900L * Ids.SCALE), 1L);
        harness.marketView.apply(marketData(Ids.VENUE_COINBASE, Ids.INSTRUMENT_BTC_USD, EntryType.ASK, 100_100L * Ids.SCALE), 1L);
        harness.cluster.time = 20_000_001L;

        harness.coinbase.onMarketData(marketData(Ids.VENUE_COINBASE, Ids.INSTRUMENT_BTC_USD, EntryType.ASK, 100_100L * Ids.SCALE));
        harness.cluster.logPosition = 2_000L;
        quoteBinance(harness, 64_990L * Ids.SCALE, 65_010L * Ids.SCALE);

        assertThat(harness.orders.orders)
            .extracting(order -> order.venueId)
            .containsExactly(Ids.VENUE_BINANCE, Ids.VENUE_BINANCE);
    }

    @Test
    void independentPostOnlyRetry_rejectOnBinanceDoesNotTouchCoinbaseParents() {
        final Harness harness = productionHarness();
        quoteCoinbase(harness, 99_900L * Ids.SCALE, 100_100L * Ids.SCALE);
        harness.cluster.logPosition = 2_000L;
        quoteBinance(harness, 64_990L * Ids.SCALE, 65_010L * Ids.SCALE);
        final OrderRecord binanceAsk = harness.orders.orders.stream()
            .filter(order -> order.venueId == Ids.VENUE_BINANCE && order.side == Side.SELL)
            .findFirst()
            .orElseThrow();

        harness.engine.onChildExecution(
            rejected(binanceAsk.clOrdId, Ids.VENUE_BINANCE, Ids.INSTRUMENT_BTC_USDT, Side.SELL),
            binanceAsk.parentOrderId);

        assertThat(harness.orders.orders)
            .filteredOn(order -> order.venueId == Ids.VENUE_BINANCE && order.clOrdId == binanceAsk.clOrdId + 1L)
            .singleElement()
            .satisfies(order -> assertThat(order.priceScaled).isEqualTo(binanceAsk.priceScaled + 1L));
        assertThat(harness.parents.lookup(harness.coinbase.liveBidClOrdId())).isNotNull();
        assertThat(harness.parents.lookup(harness.coinbase.liveAskClOrdId())).isNotNull();
        assertThat(harness.postOnly.retrySubmissions()).isEqualTo(1L);
    }

    @Test
    void perVenueInventoryAccounting_fillOnCoinbaseDoesNotSkewBinanceQuotes() {
        final Harness harness = productionHarness();
        harness.portfolio.coinbaseNetQty = 5L * Ids.SCALE;

        harness.coinbase.onFill(fill(Ids.VENUE_COINBASE, Ids.INSTRUMENT_BTC_USD, Side.BUY));
        harness.binance.onFill(fill(Ids.VENUE_COINBASE, Ids.INSTRUMENT_BTC_USD, Side.BUY));
        quoteCoinbase(harness, 99_900L * Ids.SCALE, 100_100L * Ids.SCALE);
        harness.cluster.logPosition = 2_000L;
        quoteBinance(harness, 64_990L * Ids.SCALE, 65_010L * Ids.SCALE);

        assertThat(harness.coinbase.lastBidPrice()).isLessThan(99_950L * Ids.SCALE);
        assertThat(harness.coinbase.lastAskPrice()).isLessThan(100_050L * Ids.SCALE);
        assertThat(harness.binance.lastBidPrice()).isEqualTo(64_960L * Ids.SCALE);
        assertThat(harness.binance.lastAskPrice()).isEqualTo(65_040L * Ids.SCALE);
    }

    @Test
    void perVenueStpPolicy_parentIntentsCarryVenueScopedSelfTradePolicy() {
        final CapturingExecutionStrategy capture = new CapturingExecutionStrategy();
        final Harness harness = harness(capture);

        quoteCoinbase(harness, 99_900L * Ids.SCALE, 100_100L * Ids.SCALE);
        harness.cluster.logPosition = 2_000L;
        quoteBinance(harness, 64_990L * Ids.SCALE, 65_010L * Ids.SCALE);

        assertThat(capture.intents)
            .extracting(intent -> intent.venueId)
            .containsExactly(
                Ids.VENUE_COINBASE, Ids.VENUE_COINBASE,
                Ids.VENUE_BINANCE, Ids.VENUE_BINANCE);
        assertThat(capture.intents)
            .extracting(intent -> intent.selfTradePolicy)
            .containsOnly((byte) 0);
    }

    private static Harness productionHarness() {
        return harness(new PostOnlyQuoteExecution());
    }

    private static Harness harness(final ExecutionStrategy executionStrategy) {
        final InternalMarketView marketView = new InternalMarketView();
        final RiskStub risk = new RiskStub();
        final RecordingOrderManager orders = new RecordingOrderManager();
        final PortfolioStub portfolio = new PortfolioStub();
        final RecoveryStub recovery = new RecoveryStub();
        final RecordingCluster cluster = new RecordingCluster();
        final ParentOrderRegistry parents = new ParentOrderRegistry(64, 128);
        final ExecutionStrategyRegistry registry = new ExecutionStrategyRegistry(8, 8);
        registry.register(executionStrategy);
        registry.allowCompatibility(Ids.STRATEGY_MARKET_MAKING, ExecutionStrategyIds.POST_ONLY_QUOTE);
        final ExecutionStrategyEngine engine = new ExecutionStrategyEngine(
            registry,
            new ExecutionStrategyContext(
                marketView,
                risk,
                orders,
                parents,
                new UnsafeBuffer(new byte[1024]),
                new MessageHeaderEncoder(),
                new NewOrderCommandEncoder(),
                new CancelOrderCommandEncoder(),
                () -> cluster.time,
                (correlationId, deadlineClusterMicros) -> true,
                idRegistry(),
                counters()));
        engine.initRegisteredStrategies();
        final MarketMakingStrategy coinbase = new MarketMakingStrategy(config(Ids.VENUE_COINBASE, Ids.INSTRUMENT_BTC_USD));
        final MarketMakingStrategy binance = new MarketMakingStrategy(config(Ids.VENUE_BINANCE, Ids.INSTRUMENT_BTC_USDT));
        final StrategyContextImpl context = new StrategyContextImpl(
            marketView,
            risk,
            orders,
            portfolio,
            recovery,
            engine,
            cluster.proxy,
            new UnsafeBuffer(new byte[1024]),
            new MessageHeaderEncoder(),
            new NewOrderCommandEncoder(),
            new CancelOrderCommandEncoder(),
            idRegistry(),
            counters());
        coinbase.init(context);
        binance.init(context);
        return new Harness(marketView, risk, orders, portfolio, recovery, cluster, parents, engine,
            executionStrategy instanceof PostOnlyQuoteExecution postOnly ? postOnly : null, coinbase, binance);
    }

    private static MarketMakingConfig config(final int venueId, final int instrumentId) {
        return new MarketMakingConfig(
            instrumentId, venueId, 10, 1_000, 10L * Ids.SCALE, 20L * Ids.SCALE,
            10L * Ids.SCALE, 10L * Ids.SCALE, 5, 10_000_000L, 10_000_000L,
            500, 8_000, 10L * Ids.SCALE, Ids.SCALE, 1_000);
    }

    private static void quoteCoinbase(final Harness harness, final long bid, final long ask) {
        quote(harness, harness.coinbase, Ids.VENUE_COINBASE, Ids.INSTRUMENT_BTC_USD, bid, ask);
    }

    private static void quoteBinance(final Harness harness, final long bid, final long ask) {
        quote(harness, harness.binance, Ids.VENUE_BINANCE, Ids.INSTRUMENT_BTC_USDT, bid, ask);
    }

    private static void quote(
        final Harness harness,
        final MarketMakingStrategy strategy,
        final int venueId,
        final int instrumentId,
        final long bid,
        final long ask) {
        onMarketData(harness, strategy, venueId, instrumentId, EntryType.BID, bid);
        onMarketData(harness, strategy, venueId, instrumentId, EntryType.ASK, ask);
    }

    private static void onMarketData(
        final Harness harness,
        final MarketMakingStrategy strategy,
        final int venueId,
        final int instrumentId,
        final EntryType entryType,
        final long price) {
        final MarketDataEventDecoder decoder = marketData(venueId, instrumentId, entryType, price);
        harness.marketView.apply(decoder, harness.cluster.time);
        strategy.onMarketData(decoder);
    }

    private static MarketDataEventDecoder marketData(
        final int venueId,
        final int instrumentId,
        final EntryType type,
        final long price) {
        final UnsafeBuffer buffer = new UnsafeBuffer(new byte[128]);
        new MarketDataEventEncoder()
            .wrapAndApplyHeader(buffer, 0, new MessageHeaderEncoder())
            .venueId(venueId)
            .instrumentId(instrumentId)
            .entryType(type)
            .updateAction(UpdateAction.NEW)
            .priceScaled(price)
            .sizeScaled(10L * Ids.SCALE)
            .priceLevel(0)
            .ingressTimestampNanos(1L)
            .exchangeTimestampNanos(2L)
            .fixSeqNum(1);
        final MarketDataEventDecoder decoder = new MarketDataEventDecoder();
        decoder.wrap(buffer, MessageHeaderEncoder.ENCODED_LENGTH,
            MarketDataEventEncoder.BLOCK_LENGTH, MarketDataEventEncoder.SCHEMA_VERSION);
        return decoder;
    }

    private static ExecutionEventDecoder rejected(
        final long clOrdId,
        final int venueId,
        final int instrumentId,
        final Side side) {
        return execution(clOrdId, venueId, instrumentId, side, ExecType.REJECTED, 0L, QTY, BooleanType.TRUE);
    }

    private static ExecutionEventDecoder fill(final int venueId, final int instrumentId, final Side side) {
        return execution(1L, venueId, instrumentId, side, ExecType.FILL, QTY, 0L, BooleanType.TRUE);
    }

    private static ExecutionEventDecoder execution(
        final long clOrdId,
        final int venueId,
        final int instrumentId,
        final Side side,
        final ExecType execType,
        final long cumQty,
        final long leavesQty,
        final BooleanType isFinal) {
        final UnsafeBuffer buffer = new UnsafeBuffer(new byte[256]);
        new ExecutionEventEncoder()
            .wrapAndApplyHeader(buffer, 0, new MessageHeaderEncoder())
            .clOrdId(clOrdId)
            .venueId(venueId)
            .instrumentId(instrumentId)
            .execType(execType)
            .side(side)
            .fillPriceScaled(65_000L * Ids.SCALE)
            .fillQtyScaled(cumQty)
            .cumQtyScaled(cumQty)
            .leavesQtyScaled(leavesQty)
            .rejectCode(execType == ExecType.REJECTED ? 1 : 0)
            .ingressTimestampNanos(1L)
            .exchangeTimestampNanos(2L)
            .fixSeqNum(1)
            .isFinal(isFinal)
            .putVenueOrderId(new byte[0], 0, 0)
            .putExecId(new byte[0], 0, 0);
        final ExecutionEventDecoder decoder = new ExecutionEventDecoder();
        decoder.wrap(buffer, MessageHeaderEncoder.ENCODED_LENGTH,
            ExecutionEventEncoder.BLOCK_LENGTH, ExecutionEventEncoder.SCHEMA_VERSION);
        return decoder;
    }

    private static CountersManager counters() {
        return new CountersManager(new UnsafeBuffer(new byte[1024 * 1024]), new UnsafeBuffer(new byte[64 * 1024]));
    }

    private static IdRegistry idRegistry() {
        return new IdRegistry() {
            @Override public int venueId(final long sessionId) { return Ids.VENUE_COINBASE; }
            @Override public int instrumentId(final CharSequence symbol) {
                if ("BTC-USD".contentEquals(symbol)) {
                    return Ids.INSTRUMENT_BTC_USD;
                }
                return "BTCUSDT".contentEquals(symbol) ? Ids.INSTRUMENT_BTC_USDT : 0;
            }
            @Override public String symbolOf(final int instrumentId) {
                return instrumentId == Ids.INSTRUMENT_BTC_USDT ? "BTCUSDT" : "BTC-USD";
            }
            @Override public String venueNameOf(final int venueId) {
                return venueId == Ids.VENUE_BINANCE ? "binance" : "coinbase";
            }
            @Override public void registerSession(final int venueId, final long sessionId) { }
        };
    }

    private record Harness(
        InternalMarketView marketView,
        RiskStub risk,
        RecordingOrderManager orders,
        PortfolioStub portfolio,
        RecoveryStub recovery,
        RecordingCluster cluster,
        ParentOrderRegistry parents,
        ExecutionStrategyEngine engine,
        PostOnlyQuoteExecution postOnly,
        MarketMakingStrategy coinbase,
        MarketMakingStrategy binance) {
    }

    private record OrderRecord(
        long clOrdId,
        long parentOrderId,
        int venueId,
        int instrumentId,
        Side side,
        long priceScaled,
        long qtyScaled) {
    }

    private record CapturedIntent(int venueId, int instrumentId, byte selfTradePolicy) {
    }

    private static final class RecordingOrderManager implements OrderManager {
        final List<OrderRecord> orders = new ArrayList<>();
        final List<Long> cancels = new ArrayList<>();

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
            final long parentOrderId) {
            assertThat(ordType).isEqualTo(OrdType.LIMIT.value());
            assertThat(timeInForce).isEqualTo(TimeInForce.GTC.value());
            orders.add(new OrderRecord(clOrdId, parentOrderId, venueId, instrumentId, Side.get(side), priceScaled, qtyScaled));
        }

        @Override public void createPendingOrder(final long clOrdId, final int venueId, final int instrumentId, final byte side, final byte ordType, final byte timeInForce, final long priceScaled, final long qtyScaled, final int strategyId) {
            createPendingOrder(clOrdId, venueId, instrumentId, side, ordType, timeInForce, priceScaled, qtyScaled, strategyId, 0L);
        }
        @Override public boolean onExecution(final ExecutionEventDecoder decoder) { return false; }
        @Override public void cancelAllOrders() { }
        @Override public long[] getLiveOrderIds(final int venueId) { return new long[0]; }
        @Override public OrderState getOrder(final long clOrdId) { return null; }
        @Override public void forceTransitionToCanceled(final long clOrdId) { }
        @Override public void markCancelSent(final long clOrdId) { cancels.add(clOrdId); }
        @Override public void writeSnapshot(final ExclusivePublication pub) { }
        @Override public void loadSnapshot(final Image image) { }
        @Override public void setCluster(final Cluster cluster) { }
        @Override public void resetAll() { }
    }

    private static final class CapturingExecutionStrategy implements ExecutionStrategy {
        final List<CapturedIntent> intents = new ArrayList<>();

        @Override public int executionStrategyId() { return ExecutionStrategyIds.POST_ONLY_QUOTE; }
        @Override public void init(final ExecutionStrategyContext ctx) { }
        @Override public void onParentIntent(final ParentOrderIntentView intent) {
            intents.add(new CapturedIntent(intent.primaryVenueId(), intent.instrumentId(),
                intent.decoder().selfTradePolicy()));
        }
        @Override public void onMarketDataTick(final int venueId, final int instrumentId, final long clusterTimeMicros) { }
        @Override public void onChildExecution(final ChildExecutionView execution) { }
        @Override public void onTimer(final long correlationId) { }
        @Override public void onCancel(final long parentOrderId, final byte reasonCode) { }
    }

    private static final class RecordingCluster {
        final Cluster proxy;
        long time = 1L;
        long logPosition = 1_000L;

        RecordingCluster() {
            proxy = (Cluster)Proxy.newProxyInstance(Cluster.class.getClassLoader(), new Class<?>[] {Cluster.class},
                (p, m, a) -> switch (m.getName()) {
                    case "time" -> time;
                    case "logPosition" -> logPosition;
                    case "timeUnit" -> TimeUnit.MICROSECONDS;
                    case "offer" -> ((Number)a[2]).longValue();
                    case "scheduleTimer", "cancelTimer" -> true;
                    case "toString" -> "ParallelMarketMakingCluster";
                    default -> null;
                });
        }
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

    private static final class PortfolioStub implements PortfolioEngine {
        long coinbaseNetQty;
        long binanceNetQty;

        @Override public void initPosition(final int venueId, final int instrumentId) { }
        @Override public void onFill(final ExecutionEventDecoder decoder) { }
        @Override public void refreshUnrealizedPnl(final int venueId, final int instrumentId, final long markPriceScaled) { }
        @Override public long getNetQtyScaled(final int venueId, final int instrumentId) {
            return venueId == Ids.VENUE_BINANCE ? binanceNetQty : coinbaseNetQty;
        }
        @Override public long getAvgEntryPriceScaled(final int venueId, final int instrumentId) { return 0L; }
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

    private static final class RecoveryStub implements RecoveryCoordinator {
        @Override public void onVenueStatus(final ig.rueishi.nitroj.exchange.messages.VenueStatusEventDecoder decoder) { }
        @Override public void onBalanceResponse(final ig.rueishi.nitroj.exchange.messages.BalanceQueryResponseDecoder decoder) { }
        @Override public void onTimer(final long correlationId, final long timestamp) { }
        @Override public boolean isInRecovery(final int venueId) { return false; }
        @Override public void reconcileOrder(final ExecutionEventDecoder decoder) { }
        @Override public void writeSnapshot(final ExclusivePublication snapshotPublication) { }
        @Override public void loadSnapshot(final Image snapshotImage) { }
        @Override public void resetAll() { }
        @Override public void setCluster(final Cluster cluster) { }
    }
}
