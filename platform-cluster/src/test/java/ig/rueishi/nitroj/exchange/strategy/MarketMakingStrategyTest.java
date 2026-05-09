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
import ig.rueishi.nitroj.exchange.messages.EntryType;
import ig.rueishi.nitroj.exchange.messages.ExecType;
import ig.rueishi.nitroj.exchange.messages.ExecutionEventDecoder;
import ig.rueishi.nitroj.exchange.messages.ExecutionEventEncoder;
import ig.rueishi.nitroj.exchange.messages.MarketDataEventDecoder;
import ig.rueishi.nitroj.exchange.messages.MarketDataEventEncoder;
import ig.rueishi.nitroj.exchange.messages.MessageHeaderEncoder;
import ig.rueishi.nitroj.exchange.messages.OrdType;
import ig.rueishi.nitroj.exchange.messages.ParentOrderTerminalDecoder;
import ig.rueishi.nitroj.exchange.messages.ParentOrderTerminalEncoder;
import ig.rueishi.nitroj.exchange.messages.ParentTerminalReason;
import ig.rueishi.nitroj.exchange.messages.Side;
import ig.rueishi.nitroj.exchange.messages.TimeInForce;
import ig.rueishi.nitroj.exchange.messages.UpdateAction;
import ig.rueishi.nitroj.exchange.order.OrderManager;
import ig.rueishi.nitroj.exchange.order.OrderState;
import io.aeron.ExclusivePublication;
import io.aeron.Image;
import io.aeron.Publication;
import io.aeron.cluster.service.Cluster;
import java.lang.reflect.Proxy;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.TimeUnit;
import org.agrona.concurrent.UnsafeBuffer;
import org.junit.jupiter.api.Test;

/**
 * T-009 coverage for the fixed-point market-making algorithm.
 *
 * <p>The harness applies synthetic book updates to {@link InternalMarketView}
 * and invokes MarketMakingStrategy directly, while recording order manager and
 * cluster-offer side effects. This keeps the tests deterministic and isolates
 * the quote calculation, suppression, refresh, and lifecycle behavior owned by
 * TASK-030.</p>
 */
final class MarketMakingStrategyTest {
    @Test
    void fairValue_symmetricSpread_midprice() {
        final Harness harness = harness(config());

        quote(harness, 99_900L * Ids.SCALE, 100_100L * Ids.SCALE);

        assertThat(harness.strategy.lastBidPrice()).isEqualTo(99_950L * Ids.SCALE);
        assertThat(harness.strategy.lastAskPrice()).isEqualTo(100_050L * Ids.SCALE);
    }

    @Test
    void metadataAccess_returnsCachedArrays() {
        final Harness harness = harness(config());

        assertThat(harness.strategy.subscribedInstrumentIds()).isSameAs(harness.strategy.subscribedInstrumentIds());
        assertThat(harness.strategy.activeVenueIds()).isSameAs(harness.strategy.activeVenueIds());
        assertThat(harness.strategy.subscribedInstrumentIds()).containsExactly(Ids.INSTRUMENT_BTC_USD);
        assertThat(harness.strategy.activeVenueIds()).containsExactly(Ids.VENUE_COINBASE);
    }

    @Test
    void inventorySkew_longPosition_lowerBidAndAsk() {
        final Harness harness = harness(config());
        harness.strategy.onPositionUpdate(Ids.VENUE_COINBASE, Ids.INSTRUMENT_BTC_USD, 5L * Ids.SCALE, 0L);

        quote(harness, 99_900L * Ids.SCALE, 100_100L * Ids.SCALE);

        assertThat(harness.strategy.lastBidPrice()).isLessThan(99_950L * Ids.SCALE);
        assertThat(harness.strategy.lastAskPrice()).isLessThan(100_050L * Ids.SCALE);
    }

    @Test
    void inventorySkew_shortPosition_higherBidAndAsk() {
        final Harness harness = harness(config());
        harness.strategy.onPositionUpdate(Ids.VENUE_COINBASE, Ids.INSTRUMENT_BTC_USD, -5L * Ids.SCALE, 0L);

        quote(harness, 99_900L * Ids.SCALE, 100_100L * Ids.SCALE);

        assertThat(harness.strategy.lastBidPrice()).isGreaterThan(99_950L * Ids.SCALE);
        assertThat(harness.strategy.lastAskPrice()).isGreaterThan(100_050L * Ids.SCALE);
    }

    @Test
    void quoteSize_longInventory_smallerBid_largerAsk() {
        final Harness harness = harness(config());
        harness.strategy.onPositionUpdate(Ids.VENUE_COINBASE, Ids.INSTRUMENT_BTC_USD, 5L * Ids.SCALE, 0L);

        quote(harness, 99_900L * Ids.SCALE, 100_100L * Ids.SCALE);

        assertThat(harness.order.orders.get(0).qtyScaled).isLessThan(harness.order.orders.get(1).qtyScaled);
    }

    @Test
    void tickRounding_bidRoundedDown_askRoundedUp() {
        final Harness harness = harness(config());

        quote(harness, 99_901L * Ids.SCALE, 100_103L * Ids.SCALE);

        assertThat(harness.strategy.lastBidPrice() % (10L * Ids.SCALE)).isZero();
        assertThat(harness.strategy.lastAskPrice() % (10L * Ids.SCALE)).isZero();
    }

    @Test
    void bidAskNonCrossing_skewedPricesFixed() {
        final Harness harness = harness(new MarketMakingConfig(Ids.INSTRUMENT_BTC_USD, Ids.VENUE_COINBASE, 0, 0, Ids.SCALE, 10L * Ids.SCALE, 10L * Ids.SCALE, 10L * Ids.SCALE, 1, 10_000_000, 10_000_000, 1_000, 2_000, 10L * Ids.SCALE, Ids.SCALE, 1_000));

        quote(harness, 100_000L * Ids.SCALE, 100_001L * Ids.SCALE);

        assertThat(harness.strategy.lastAskPrice()).isGreaterThan(harness.strategy.lastBidPrice());
    }

    @Test
    void suppression_killSwitch_noQuotesSubmitted() {
        final Harness harness = harness(config());
        harness.risk.killSwitch = true;

        quote(harness, 99_900L * Ids.SCALE, 100_100L * Ids.SCALE);

        assertThat(harness.order.orders).isEmpty();
    }

    @Test
    void suppression_recoveryLock_noQuotesSubmitted() {
        final Harness harness = harness(config());
        harness.recovery.inRecovery = true;

        quote(harness, 99_900L * Ids.SCALE, 100_100L * Ids.SCALE);

        assertThat(harness.order.orders).isEmpty();
    }

    @Test
    void suppression_staleMarketData_noQuotesSubmitted() {
        final Harness harness = harness(config());
        final MarketDataEventDecoder bid = decoder(Ids.VENUE_COINBASE, Ids.INSTRUMENT_BTC_USD, EntryType.BID, 99_900L * Ids.SCALE);
        final MarketDataEventDecoder ask = decoder(Ids.VENUE_COINBASE, Ids.INSTRUMENT_BTC_USD, EntryType.ASK, 100_100L * Ids.SCALE);
        harness.marketView.apply(bid, 1L);
        harness.marketView.apply(ask, 1L);
        harness.cluster.time = 20_000_001L;

        harness.strategy.onMarketData(ask);

        assertThat(harness.order.orders).isEmpty();
    }

    @Test
    void suppression_wideSpread_noQuotesSubmitted() {
        final Harness harness = harness(config());

        quote(harness, 95_000L * Ids.SCALE, 105_000L * Ids.SCALE);

        assertThat(harness.order.orders).isEmpty();
        assertThat(harness.strategy.suppressedUntilMicros()).isGreaterThan(0L);
    }

    @Test
    void suppression_cleared_quotesResume() {
        final Harness harness = harness(config());
        harness.strategy.onKillSwitch();
        harness.strategy.onKillSwitchCleared();

        quote(harness, 99_900L * Ids.SCALE, 100_100L * Ids.SCALE);

        assertThat(harness.order.orders).hasSize(2);
    }

    @Test
    void zeroSize_bidSideAtMaxLong_bidNotSubmitted() {
        final Harness harness = harness(config());
        harness.strategy.onPositionUpdate(Ids.VENUE_COINBASE, Ids.INSTRUMENT_BTC_USD, 10L * Ids.SCALE, 0L);

        quote(harness, 99_900L * Ids.SCALE, 100_100L * Ids.SCALE);

        assertThat(harness.order.orders).extracting(order -> order.side).containsOnly(Side.SELL);
    }

    @Test
    void refresh_liveQuotesIdentical_noNewQuote() {
        final Harness harness = harness(config());
        quote(harness, 99_900L * Ids.SCALE, 100_100L * Ids.SCALE);
        harness.order.orders.clear();
        harness.cluster.offerCount = 0;

        quote(harness, 99_900L * Ids.SCALE, 100_100L * Ids.SCALE);

        assertThat(harness.order.orders).isEmpty();
        assertThat(harness.cluster.offerCount).isZero();
    }

    @Test
    void refresh_cancelSentBeforeNewQuote() {
        final Harness harness = harness(config());
        quote(harness, 99_900L * Ids.SCALE, 100_100L * Ids.SCALE);
        harness.cluster.offerKinds.clear();
        harness.cluster.time = 2L;

        quote(harness, 100_900L * Ids.SCALE, 101_100L * Ids.SCALE);

        assertThat(harness.cluster.offerKinds).startsWith("cancel", "cancel", "new", "new");
    }

    @Test
    void wrongVenueId_ignored() {
        final Harness harness = harness(config());

        onMarketData(harness, Ids.VENUE_COINBASE_SANDBOX, Ids.INSTRUMENT_BTC_USD, EntryType.BID, 100_000L * Ids.SCALE);

        assertThat(harness.order.orders).isEmpty();
    }

    @Test
    void clOrdId_usesClusterLogPosition_andEachQuoteUnique() {
        final Harness harness = harness(config());
        harness.cluster.logPosition = 777L;

        quote(harness, 99_900L * Ids.SCALE, 100_100L * Ids.SCALE);

        assertThat(harness.order.orders).extracting(order -> order.clOrdId).containsExactly(777L, 778L);
    }

    @Test
    void quoteIntent_noDirectChildClusterOffer() {
        final Harness harness = harness(config());

        quote(harness, 99_900L * Ids.SCALE, 100_100L * Ids.SCALE);

        assertThat(harness.cluster.offerCount).isZero();
        assertThat(harness.executionStrategy.parentIntents).isEqualTo(2);
    }

    @Test
    void parentTerminal_clearsLiveParentId() {
        final Harness harness = harness(config());
        quote(harness, 99_900L * Ids.SCALE, 100_100L * Ids.SCALE);

        harness.strategy.onParentTerminal(parentTerminal(harness.strategy.liveBidClOrdId()));

        assertThat(harness.strategy.liveBidClOrdId()).isZero();
        assertThat(harness.strategy.liveAskClOrdId()).isNotZero();
    }

    @Test
    void synchronousSubmitReject_doesNotRecordLiveParentAndSuppresses() {
        final Harness harness = harness(config(), false);

        quote(harness, 99_900L * Ids.SCALE, 100_100L * Ids.SCALE);

        assertThat(harness.strategy.liveBidClOrdId()).isZero();
        assertThat(harness.strategy.liveAskClOrdId()).isZero();
        assertThat(harness.strategy.parentSubmitRejects()).isEqualTo(2L);
        assertThat(harness.strategy.suppressedUntilMicros()).isEqualTo(harness.cluster.time + 5_000_000L);
    }

    @Test
    void synchronousSubmitReject_replayDeterministicState() {
        assertThat(submitRejectSummary()).isEqualTo(submitRejectSummary());
    }

    @Test
    void lastTradePrice_updatedFromTradeTick_andNotFromBidAsk() {
        final Harness harness = harness(config());

        onMarketData(harness, Ids.VENUE_COINBASE, Ids.INSTRUMENT_BTC_USD, EntryType.BID, 100_000L * Ids.SCALE);
        assertThat(harness.strategy.lastTradePriceScaled()).isZero();
        onMarketData(harness, Ids.VENUE_COINBASE, Ids.INSTRUMENT_BTC_USD, EntryType.TRADE, 101_000L * Ids.SCALE);

        assertThat(harness.strategy.lastTradePriceScaled()).isEqualTo(101_000L * Ids.SCALE);
    }

    @Test
    void wideSpread_withLastTradePrice_quotesAtTradePrice() {
        final Harness harness = harness(config());
        onMarketData(harness, Ids.VENUE_COINBASE, Ids.INSTRUMENT_BTC_USD, EntryType.TRADE, 101_000L * Ids.SCALE);

        quote(harness, 98_000L * Ids.SCALE, 104_000L * Ids.SCALE);

        assertThat(harness.strategy.lastBidPrice()).isEqualTo(100_940L * Ids.SCALE);
        assertThat(harness.strategy.lastAskPrice()).isEqualTo(101_060L * Ids.SCALE);
    }

    @Test
    void onOrderRejected_threeConsecutive_suppressionSet_andFillResetsCounter() {
        final Harness harness = harness(config());

        harness.strategy.onOrderRejected(1, (byte)1, Ids.VENUE_COINBASE);
        harness.strategy.onOrderRejected(2, (byte)1, Ids.VENUE_COINBASE);
        assertThat(harness.strategy.consecutiveRejections()).isEqualTo(2);
        harness.strategy.onOrderRejected(3, (byte)1, Ids.VENUE_COINBASE);
        assertThat(harness.strategy.suppressedUntilMicros()).isEqualTo(harness.cluster.time + 5_000_000L);
        harness.strategy.onFill(execution());
        assertThat(harness.strategy.consecutiveRejections()).isZero();
    }

    @Test
    void minQuoteSizeFractionBps_configurable_floorRespected() {
        final Harness harness = harness(config());
        harness.strategy.onPositionUpdate(Ids.VENUE_COINBASE, Ids.INSTRUMENT_BTC_USD, 9L * Ids.SCALE, 0L);

        quote(harness, 99_900L * Ids.SCALE, 100_100L * Ids.SCALE);

        assertThat(harness.order.orders.get(0).qtyScaled).isEqualTo(1L * Ids.SCALE);
    }

    static MarketMakingConfig config() {
        return new MarketMakingConfig(Ids.INSTRUMENT_BTC_USD, Ids.VENUE_COINBASE, 10, 1_000, 10L * Ids.SCALE, 20L * Ids.SCALE, 10L * Ids.SCALE, 10L * Ids.SCALE, 5, 10_000_000, 10_000_000, 500, 8_000, 10L * Ids.SCALE, Ids.SCALE, 1_000);
    }

    static Harness harness(final MarketMakingConfig config) {
        return harness(config, true);
    }

    static Harness harness(final MarketMakingConfig config, final boolean allowCompatibility) {
        final InternalMarketView marketView = new InternalMarketView();
        final RecordingRisk risk = new RecordingRisk();
        final RecordingOrder order = new RecordingOrder();
        final RecordingPortfolio portfolio = new RecordingPortfolio();
        final RecordingRecovery recovery = new RecordingRecovery();
        final RecordingCluster cluster = new RecordingCluster();
        final ParentOrderRegistry parentRegistry = new ParentOrderRegistry(16, 16);
        final ExecutionStrategyRegistry executionRegistry = new ExecutionStrategyRegistry(8, 8);
        final RecordingExecutionStrategy executionStrategy = new RecordingExecutionStrategy(order, cluster);
        executionRegistry.register(executionStrategy);
        if (allowCompatibility) {
            executionRegistry.allowCompatibility(Ids.STRATEGY_MARKET_MAKING, ExecutionStrategyIds.POST_ONLY_QUOTE);
        }
        final ExecutionStrategyEngine executionEngine = new ExecutionStrategyEngine(
            executionRegistry,
            new ExecutionStrategyContext(
                marketView,
                risk,
                order,
                parentRegistry,
                new UnsafeBuffer(new byte[512]),
                new MessageHeaderEncoder(),
                new ig.rueishi.nitroj.exchange.messages.NewOrderCommandEncoder(),
                new ig.rueishi.nitroj.exchange.messages.CancelOrderCommandEncoder(),
                () -> cluster.time,
                (correlationId, deadlineClusterMicros) -> true,
                nullIdRegistry(),
                new org.agrona.concurrent.status.CountersManager(
                    new UnsafeBuffer(new byte[1024 * 1024]),
                    new UnsafeBuffer(new byte[64 * 1024]))));
        executionEngine.initRegisteredStrategies();
        final MarketMakingStrategy strategy = new MarketMakingStrategy(config);
        strategy.init(new StrategyContextImpl(
            marketView, risk, order, portfolio, recovery, executionEngine, cluster.proxy,
            new UnsafeBuffer(new byte[512]), new MessageHeaderEncoder(),
            new ig.rueishi.nitroj.exchange.messages.NewOrderCommandEncoder(),
            new ig.rueishi.nitroj.exchange.messages.CancelOrderCommandEncoder(), null, null));
        return new Harness(strategy, marketView, risk, order, portfolio, recovery, cluster, executionStrategy);
    }

    static String submitRejectSummary() {
        final Harness harness = harness(config(), false);
        quote(harness, 99_900L * Ids.SCALE, 100_100L * Ids.SCALE);
        return harness.strategy.liveBidClOrdId() + ":" + harness.strategy.liveAskClOrdId()
            + ":" + harness.strategy.parentSubmitRejects() + ":" + harness.strategy.suppressedUntilMicros();
    }

    static void quote(final Harness harness, final long bid, final long ask) {
        onMarketData(harness, Ids.VENUE_COINBASE, Ids.INSTRUMENT_BTC_USD, EntryType.BID, bid);
        onMarketData(harness, Ids.VENUE_COINBASE, Ids.INSTRUMENT_BTC_USD, EntryType.ASK, ask);
    }

    static void onMarketData(final Harness harness, final int venueId, final int instrumentId, final EntryType type, final long price) {
        final MarketDataEventDecoder decoder = decoder(venueId, instrumentId, type, price);
        harness.marketView.apply(decoder, harness.cluster.time);
        harness.strategy.onMarketData(decoder);
    }

    static MarketDataEventDecoder decoder(final int venueId, final int instrumentId, final EntryType type, final long price) {
        final UnsafeBuffer buffer = new UnsafeBuffer(new byte[128]);
        new MarketDataEventEncoder()
            .wrapAndApplyHeader(buffer, 0, new MessageHeaderEncoder())
            .venueId(venueId).instrumentId(instrumentId).entryType(type).updateAction(UpdateAction.NEW)
            .priceScaled(price).sizeScaled(10L * Ids.SCALE).priceLevel(0)
            .ingressTimestampNanos(1L).exchangeTimestampNanos(2L).fixSeqNum(1);
        final MarketDataEventDecoder decoder = new MarketDataEventDecoder();
        decoder.wrap(buffer, MessageHeaderEncoder.ENCODED_LENGTH, MarketDataEventEncoder.BLOCK_LENGTH, MarketDataEventEncoder.SCHEMA_VERSION);
        return decoder;
    }

    static ExecutionEventDecoder execution() {
        final UnsafeBuffer buffer = new UnsafeBuffer(new byte[256]);
        new ExecutionEventEncoder().wrapAndApplyHeader(buffer, 0, new MessageHeaderEncoder())
            .clOrdId(1L).venueId(Ids.VENUE_COINBASE).instrumentId(Ids.INSTRUMENT_BTC_USD)
            .execType(ExecType.FILL).side(Side.BUY).fillPriceScaled(100_000L * Ids.SCALE)
            .fillQtyScaled(Ids.SCALE).cumQtyScaled(Ids.SCALE).leavesQtyScaled(0L)
            .rejectCode(0).ingressTimestampNanos(1L).exchangeTimestampNanos(2L).fixSeqNum(1)
            .isFinal(ig.rueishi.nitroj.exchange.messages.BooleanType.TRUE).putVenueOrderId(new byte[0], 0, 0).putExecId(new byte[0], 0, 0);
        final ExecutionEventDecoder decoder = new ExecutionEventDecoder();
        decoder.wrap(buffer, MessageHeaderEncoder.ENCODED_LENGTH, ExecutionEventEncoder.BLOCK_LENGTH, ExecutionEventEncoder.SCHEMA_VERSION);
        return decoder;
    }

    static ParentOrderTerminalDecoder parentTerminal(final long parentOrderId) {
        final UnsafeBuffer buffer = new UnsafeBuffer(new byte[128]);
        new ParentOrderTerminalEncoder().wrapAndApplyHeader(buffer, 0, new MessageHeaderEncoder())
            .parentOrderId(parentOrderId)
            .strategyId((short) Ids.STRATEGY_MARKET_MAKING)
            .executionStrategyId(ExecutionStrategyIds.POST_ONLY_QUOTE)
            .finalCumFillQtyScaled(0L)
            .terminalReason(ParentTerminalReason.COMPLETED);
        final ParentOrderTerminalDecoder decoder = new ParentOrderTerminalDecoder();
        decoder.wrapAndApplyHeader(buffer, 0, new ig.rueishi.nitroj.exchange.messages.MessageHeaderDecoder());
        return decoder;
    }

    static ig.rueishi.nitroj.exchange.registry.IdRegistry nullIdRegistry() {
        return new ig.rueishi.nitroj.exchange.registry.IdRegistry() {
            @Override public int venueId(final long sessionId) { return Ids.VENUE_COINBASE; }
            @Override public int instrumentId(final CharSequence symbol) { return Ids.INSTRUMENT_BTC_USD; }
            @Override public String symbolOf(final int instrumentId) { return "BTC-USD"; }
            @Override public String venueNameOf(final int venueId) { return "coinbase"; }
            @Override public void registerSession(final int venueId, final long sessionId) { }
        };
    }

    record Harness(MarketMakingStrategy strategy, InternalMarketView marketView, RecordingRisk risk, RecordingOrder order, RecordingPortfolio portfolio, RecordingRecovery recovery, RecordingCluster cluster, RecordingExecutionStrategy executionStrategy) {
    }

    static final class RecordingCluster {
        Cluster proxy;
        long time = 1L;
        long logPosition = 100L;
        int offerCount;
        List<String> offerKinds = new ArrayList<>();

        RecordingCluster() {
            proxy = (Cluster)Proxy.newProxyInstance(Cluster.class.getClassLoader(), new Class<?>[]{Cluster.class}, (p, m, a) -> switch (m.getName()) {
                case "time" -> time;
                case "logPosition" -> logPosition;
                case "offer" -> {
                    offerCount++;
                    final org.agrona.DirectBuffer buffer = (org.agrona.DirectBuffer)a[0];
                    final int templateId = buffer.getShort(((Integer)a[1]) + 2, java.nio.ByteOrder.LITTLE_ENDIAN);
                    offerKinds.add(templateId == ig.rueishi.nitroj.exchange.messages.CancelOrderCommandEncoder.TEMPLATE_ID ? "cancel" : "new");
                    yield ((Number)a[2]).longValue();
                }
                case "timeUnit" -> TimeUnit.MICROSECONDS;
                case "scheduleTimer", "cancelTimer" -> true;
                case "toString" -> "RecordingCluster";
                default -> null;
            });
        }
    }

    static final class OrderRecord {
        long clOrdId; Side side; long priceScaled; long qtyScaled;
        OrderRecord(final long clOrdId, final Side side, final long priceScaled, final long qtyScaled) {
            this.clOrdId = clOrdId; this.side = side; this.priceScaled = priceScaled; this.qtyScaled = qtyScaled;
        }
    }

    static final class RecordingExecutionStrategy implements ExecutionStrategy {
        final RecordingOrder order;
        final RecordingCluster cluster;
        ExecutionStrategyContext ctx;
        long parentIntents;

        RecordingExecutionStrategy(final RecordingOrder order, final RecordingCluster cluster) {
            this.order = order;
            this.cluster = cluster;
        }

        @Override public int executionStrategyId() { return ExecutionStrategyIds.POST_ONLY_QUOTE; }
        @Override public void init(final ExecutionStrategyContext ctx) { this.ctx = ctx; }
        @Override public void onParentIntent(final ParentOrderIntentView intent) {
            parentIntents++;
            ctx.parentOrderRegistry().claim(intent.parentOrderId(), intent.strategyId(), executionStrategyId(), intent.quantityScaled(), 1L);
            order.orders.add(new OrderRecord(intent.parentOrderId(), intent.side(), intent.limitPriceScaled(), intent.quantityScaled()));
            cluster.offerKinds.add("new");
        }
        @Override public void onMarketDataTick(final int venueId, final int instrumentId, final long clusterTimeMicros) { }
        @Override public void onChildExecution(final ChildExecutionView execution) { }
        @Override public void onTimer(final long correlationId) { }
        @Override public void onCancel(final long parentOrderId, final byte reasonCode) {
            cluster.offerKinds.add("cancel");
        }
    }

    static final class RecordingOrder implements OrderManager {
        final List<OrderRecord> orders = new ArrayList<>();
        @Override public void createPendingOrder(final long clOrdId, final int venueId, final int instrumentId, final byte side, final byte ordType, final byte timeInForce, final long priceScaled, final long qtyScaled, final int strategyId) { orders.add(new OrderRecord(clOrdId, Side.get(side), priceScaled, qtyScaled)); assertThat(ordType).isEqualTo(OrdType.LIMIT.value()); assertThat(timeInForce).isEqualTo(TimeInForce.GTC.value()); }
        @Override public boolean onExecution(final ExecutionEventDecoder decoder) { return false; }
        @Override public void cancelAllOrders() { }
        @Override public long[] getLiveOrderIds(final int venueId) { return new long[0]; }
        @Override public OrderState getOrder(final long clOrdId) { return null; }
        @Override public void forceTransitionToCanceled(final long clOrdId) { }
        @Override public void writeSnapshot(final ExclusivePublication pub) { }
        @Override public void loadSnapshot(final Image image) { }
        @Override public void setCluster(final Cluster cluster) { }
        @Override public void resetAll() { }
    }

    static final class RecordingRecovery implements RecoveryCoordinator {
        boolean inRecovery;
        @Override public void onVenueStatus(final ig.rueishi.nitroj.exchange.messages.VenueStatusEventDecoder decoder) { }
        @Override public void onBalanceResponse(final ig.rueishi.nitroj.exchange.messages.BalanceQueryResponseDecoder decoder) { }
        @Override public void onTimer(final long correlationId, final long timestamp) { }
        @Override public boolean isInRecovery(final int venueId) { return inRecovery; }
        @Override public void reconcileOrder(final ExecutionEventDecoder decoder) { }
        @Override public void writeSnapshot(final ExclusivePublication snapshotPublication) { }
        @Override public void loadSnapshot(final Image snapshotImage) { }
        @Override public void resetAll() { }
        @Override public void setCluster(final Cluster cluster) { }
    }

    static final class RecordingPortfolio implements PortfolioEngine {
        long netQty;
        @Override public void initPosition(final int venueId, final int instrumentId) { }
        @Override public void onFill(final ExecutionEventDecoder decoder) { netQty += decoder.fillQtyScaled(); }
        @Override public void refreshUnrealizedPnl(final int venueId, final int instrumentId, final long markPriceScaled) { }
        @Override public long getNetQtyScaled(final int venueId, final int instrumentId) { return netQty; }
        @Override public long getAvgEntryPriceScaled(final int venueId, final int instrumentId) { return 0; }
        @Override public long unrealizedPnl(final int venueId, final int instrumentId, final long markPriceScaled) { return 0; }
        @Override public void adjustPosition(final int venueId, final int instrumentId, final double balanceUnscaled) { }
        @Override public long getTotalRealizedPnlScaled() { return 0; }
        @Override public long getTotalUnrealizedPnlScaled() { return 0; }
        @Override public void writeSnapshot(final ExclusivePublication snapshotPublication) { }
        @Override public void loadSnapshot(final Image snapshotImage) { }
        @Override public void archiveDailyPnl(final Publication egressPublication) { }
        @Override public void setCluster(final Cluster cluster) { }
        @Override public void resetAll() { }
    }

    static final class RecordingRisk implements RiskEngine {
        boolean killSwitch;
        @Override public RiskDecision preTradeCheck(final int venueId, final int instrumentId, final byte side, final long priceScaled, final long qtyScaled, final int strategyId) { return RiskDecision.APPROVED; }
        @Override public void updatePositionSnapshot(final int venueId, final int instrumentId, final long netQtyScaled) { }
        @Override public void updateDailyPnl(final long realizedPnlDeltaScaled) { }
        @Override public void setRecoveryLock(final int venueId, final boolean locked) { }
        @Override public long getDailyPnlScaled() { return 0; }
        @Override public void activateKillSwitch(final String reason) { killSwitch = true; }
        @Override public void deactivateKillSwitch() { killSwitch = false; }
        @Override public boolean isKillSwitchActive() { return killSwitch; }
        @Override public void writeSnapshot(final ExclusivePublication snapshotPublication) { }
        @Override public void loadSnapshot(final Image snapshotImage) { }
        @Override public void resetDailyCounters() { }
        @Override public void setCluster(final Cluster cluster) { }
        @Override public void onFill(final ExecutionEventDecoder decoder) { }
        @Override public void resetAll() { }
    }
}
