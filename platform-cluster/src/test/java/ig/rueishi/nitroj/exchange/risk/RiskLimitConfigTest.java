package ig.rueishi.nitroj.exchange.risk;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import ig.rueishi.nitroj.exchange.cluster.InternalMarketView;
import ig.rueishi.nitroj.exchange.cluster.RiskDecision;
import ig.rueishi.nitroj.exchange.cluster.RiskEngine;
import ig.rueishi.nitroj.exchange.cluster.RiskEngineImpl;
import ig.rueishi.nitroj.exchange.common.ExecutionStrategyIds;
import ig.rueishi.nitroj.exchange.common.Ids;
import ig.rueishi.nitroj.exchange.common.RiskConfig;
import ig.rueishi.nitroj.exchange.execution.ExecutionStrategyContext;
import ig.rueishi.nitroj.exchange.execution.ParentOrderIntentView;
import ig.rueishi.nitroj.exchange.execution.ParentOrderRegistry;
import ig.rueishi.nitroj.exchange.execution.ParentOrderState;
import ig.rueishi.nitroj.exchange.execution.PostOnlyQuoteExecution;
import ig.rueishi.nitroj.exchange.messages.BooleanType;
import ig.rueishi.nitroj.exchange.messages.CancelOrderCommandEncoder;
import ig.rueishi.nitroj.exchange.messages.ExecType;
import ig.rueishi.nitroj.exchange.messages.ExecutionEventDecoder;
import ig.rueishi.nitroj.exchange.messages.MessageHeaderDecoder;
import ig.rueishi.nitroj.exchange.messages.MessageHeaderEncoder;
import ig.rueishi.nitroj.exchange.messages.NewOrderCommandEncoder;
import ig.rueishi.nitroj.exchange.messages.ParentIntentType;
import ig.rueishi.nitroj.exchange.messages.ParentOrderIntentDecoder;
import ig.rueishi.nitroj.exchange.messages.ParentOrderIntentEncoder;
import ig.rueishi.nitroj.exchange.messages.PriceMode;
import ig.rueishi.nitroj.exchange.messages.Side;
import ig.rueishi.nitroj.exchange.messages.TimeInForce;
import ig.rueishi.nitroj.exchange.order.OrderManager;
import ig.rueishi.nitroj.exchange.order.OrderState;
import ig.rueishi.nitroj.exchange.registry.IdRegistry;
import io.aeron.ExclusivePublication;
import io.aeron.Image;
import io.aeron.cluster.service.Cluster;
import java.util.Map;
import org.agrona.concurrent.UnsafeBuffer;
import org.agrona.concurrent.status.CountersManager;
import org.junit.jupiter.api.Test;

/**
 * TASK-412 coverage for V14 per-venue and explicit aggregate risk limits.
 *
 * <p>The tests exercise the new configuration surface before child submission
 * and include a V13 RiskEngine equivalence check. Snapshot/load, parser
 * malformed TOML, and allocation benchmark categories are non-applicable here:
 * this task creates no parser, persistent state format, or declared hot-path
 * benchmark owner. Runtime position snapshots are primitive and resettable by
 * construction, while real-venue QA/UAT remains blocked by the V14 release
 * gates rather than substituting for these local tests.</p>
 */
final class RiskLimitConfigTest {
    private static final int ASSET_BTC = 1;
    private static final long PRICE = scaled(65_000);
    private static final long QTY = Ids.SCALE;

    @Test
    void perVenueLimitEnforcedBeforeChildSubmission() {
        final RiskLimitConfig limits = limits(scaled(1), scaled(10), scaled(100_000), scaled(10), scaled(1_000_000));
        limits.updatePositionSnapshot(Ids.VENUE_COINBASE, Ids.INSTRUMENT_BTC_USD, scaled(1));
        final Harness harness = harness(new ConfigRisk(limits));

        harness.strategy.onParentIntent(new ParentOrderIntentView().wrap(intent(
            101L, Ids.VENUE_COINBASE, Ids.INSTRUMENT_BTC_USD, Side.BUY, QTY, PRICE)));

        assertThat(harness.order.createCalls).isZero();
        assertThat(harness.risk.lastDecision).isSameAs(RiskDecision.REJECT_MAX_LONG);
        assertThat(harness.registry.lookup(101L).status()).isEqualTo(ParentOrderState.FAILED);
        assertThat(harness.registry.lookup(101L).terminalReasonCode()).isEqualTo(ParentOrderState.REASON_RISK_REJECTED);
    }

    @Test
    void aggregateLimitEnforcedBeforeChildSubmissionAcrossVenues() {
        final RiskLimitConfig limits = limits(scaled(10), scaled(10), scaled(100_000), scaled(2), scaled(1_000_000));
        limits.updatePositionSnapshot(Ids.VENUE_COINBASE, Ids.INSTRUMENT_BTC_USD, QTY);
        limits.updatePositionSnapshot(Ids.VENUE_BINANCE, Ids.INSTRUMENT_BTC_USDT, QTY);
        final Harness harness = harness(new ConfigRisk(limits));

        harness.strategy.onParentIntent(new ParentOrderIntentView().wrap(intent(
            102L, Ids.VENUE_COINBASE, Ids.INSTRUMENT_BTC_USD, Side.BUY, QTY, PRICE)));

        assertThat(harness.order.createCalls).isZero();
        assertThat(harness.risk.lastDecision).isSameAs(RiskDecision.REJECT_MAX_LONG);
        assertThat(harness.risk.lastDecision.rejectCode()).isEqualTo(RiskDecision.REJECT_MAX_LONG.rejectCode());
    }

    @Test
    void aggregateComputationIsExplicitAndDoesNotAutoNetUsdAndUsdtSymbols() {
        final RiskLimitConfig explicitCoinbaseOnly = new RiskLimitConfig(
            new RiskLimitConfig.VenueLimit[] {
                venueLimit(Ids.VENUE_COINBASE, Ids.INSTRUMENT_BTC_USD, scaled(10), scaled(10), scaled(100_000)),
                venueLimit(Ids.VENUE_BINANCE, Ids.INSTRUMENT_BTC_USDT, scaled(10), scaled(10), scaled(100_000))
            },
            new RiskLimitConfig.AggregateLimit[] {
                new RiskLimitConfig.AggregateLimit(
                    ASSET_BTC,
                    new int[] {Ids.INSTRUMENT_BTC_USD},
                    scaled(3),
                    scaled(3),
                    scaled(1_000_000))
            });
        explicitCoinbaseOnly.updatePositionSnapshot(Ids.VENUE_COINBASE, Ids.INSTRUMENT_BTC_USD, QTY);
        explicitCoinbaseOnly.updatePositionSnapshot(Ids.VENUE_BINANCE, Ids.INSTRUMENT_BTC_USDT, scaled(10));

        assertThat(explicitCoinbaseOnly.aggregatePosition(ASSET_BTC)).isEqualTo(QTY);
        assertThat(explicitCoinbaseOnly.preChildSubmitCheck(
            Ids.VENUE_COINBASE, Ids.INSTRUMENT_BTC_USD, Side.BUY.value(), PRICE, QTY))
            .isSameAs(RiskDecision.APPROVED);
    }

    @Test
    void missingLimitRejectsBeforeChildSubmission() {
        final RiskLimitConfig limits = new RiskLimitConfig(
            new RiskLimitConfig.VenueLimit[] {
                venueLimit(Ids.VENUE_COINBASE, Ids.INSTRUMENT_BTC_USD, scaled(10), scaled(10), scaled(100_000))
            },
            new RiskLimitConfig.AggregateLimit[0]);

        assertThat(limits.preChildSubmitCheck(
            Ids.VENUE_BINANCE, Ids.INSTRUMENT_BTC_USDT, Side.BUY.value(), PRICE, QTY))
            .isSameAs(RiskDecision.REJECT_MAX_NOTIONAL);
    }

    @Test
    void malformedLimitConfigRejectedAtConstructionOrUpdate() {
        assertThatThrownBy(() -> venueLimit(0, Ids.INSTRUMENT_BTC_USD, QTY, QTY, PRICE))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("venueId");
        assertThatThrownBy(() -> venueLimit(Ids.VENUE_COINBASE, Ids.INSTRUMENT_BTC_USD, 0L, QTY, PRICE))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("positive");
        assertThatThrownBy(() -> new RiskLimitConfig.AggregateLimit(ASSET_BTC, new int[0], QTY, QTY, PRICE))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("instrumentIds");

        final RiskLimitConfig limits = limits(scaled(10), scaled(10), scaled(100_000), scaled(10), scaled(1_000_000));
        assertThatThrownBy(() -> limits.updatePositionSnapshot(99, Ids.INSTRUMENT_BTC_USD, QTY))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("venueId");
    }

    @Test
    void limitBreachReasonCodePropagatesFromConfigRiskToExecutionReject() {
        final RiskLimitConfig limits = limits(scaled(10), scaled(10), scaled(1), scaled(10), scaled(1_000_000));
        final Harness harness = harness(new ConfigRisk(limits));

        harness.strategy.onParentIntent(new ParentOrderIntentView().wrap(intent(
            103L, Ids.VENUE_COINBASE, Ids.INSTRUMENT_BTC_USD, Side.BUY, QTY, PRICE)));

        assertThat(harness.risk.lastDecision).isSameAs(RiskDecision.REJECT_MAX_NOTIONAL);
        assertThat(harness.risk.lastDecision.rejectCode()).isEqualTo((byte) 6);
        assertThat(harness.order.createCalls).isZero();
    }

    @Test
    void v13RiskEngineSingleVenueSemanticsRemainUnchanged() {
        final RiskEngineImpl engine = new RiskEngineImpl(new RiskConfig(
            100,
            scaled(1_000),
            Map.of(Ids.INSTRUMENT_BTC_USD, new RiskConfig.InstrumentRisk(
                Ids.INSTRUMENT_BTC_USD,
                scaled(10),
                scaled(100),
                scaled(100),
                scaled(1_000_000),
                80))));
        engine.setVenueConnected(Ids.VENUE_COINBASE, true);

        assertThat(engine.preTradeCheck(
            Ids.VENUE_COINBASE, Ids.INSTRUMENT_BTC_USD, Side.BUY.value(), scaled(100), QTY,
            Ids.STRATEGY_MARKET_MAKING))
            .isSameAs(RiskDecision.APPROVED);
        assertThat(engine.preTradeCheck(
            Ids.VENUE_COINBASE, Ids.INSTRUMENT_BTC_USD, Side.BUY.value(), scaled(100), scaled(11),
            Ids.STRATEGY_MARKET_MAKING))
            .isSameAs(RiskDecision.REJECT_ORDER_TOO_LARGE);
    }

    private static RiskLimitConfig limits(
        final long venueMaxPosition,
        final long venueMaxShort,
        final long venueMaxNotional,
        final long aggregateMaxPosition,
        final long aggregateMaxNotional) {
        return new RiskLimitConfig(
            new RiskLimitConfig.VenueLimit[] {
                venueLimit(Ids.VENUE_COINBASE, Ids.INSTRUMENT_BTC_USD, venueMaxPosition, venueMaxShort, venueMaxNotional),
                venueLimit(Ids.VENUE_BINANCE, Ids.INSTRUMENT_BTC_USDT, venueMaxPosition, venueMaxShort, venueMaxNotional)
            },
            new RiskLimitConfig.AggregateLimit[] {
                new RiskLimitConfig.AggregateLimit(
                    ASSET_BTC,
                    new int[] {Ids.INSTRUMENT_BTC_USD, Ids.INSTRUMENT_BTC_USDT},
                    aggregateMaxPosition,
                    aggregateMaxPosition,
                    aggregateMaxNotional)
            });
    }

    private static RiskLimitConfig.VenueLimit venueLimit(
        final int venueId,
        final int instrumentId,
        final long maxLong,
        final long maxShort,
        final long maxNotional) {
        return new RiskLimitConfig.VenueLimit(venueId, instrumentId, maxLong, maxShort, maxNotional);
    }

    private static Harness harness(final ConfigRisk risk) {
        final ParentOrderRegistry registry = new ParentOrderRegistry(8, 8);
        final RecordingOrderManager order = new RecordingOrderManager();
        final PostOnlyQuoteExecution strategy = new PostOnlyQuoteExecution();
        strategy.init(new ExecutionStrategyContext(
            new InternalMarketView(),
            risk,
            order,
            registry,
            new UnsafeBuffer(new byte[1024]),
            new MessageHeaderEncoder(),
            new NewOrderCommandEncoder(),
            new CancelOrderCommandEncoder(),
            () -> 1L,
            (correlationId, deadlineClusterMicros) -> true,
            idRegistry(),
            new CountersManager(new UnsafeBuffer(new byte[1024 * 1024]), new UnsafeBuffer(new byte[64 * 1024]))));
        return new Harness(strategy, registry, order, risk);
    }

    private static ParentOrderIntentDecoder intent(
        final long parentOrderId,
        final int venueId,
        final int instrumentId,
        final Side side,
        final long qtyScaled,
        final long priceScaled) {
        final UnsafeBuffer buffer = new UnsafeBuffer(new byte[256]);
        new ParentOrderIntentEncoder()
            .wrapAndApplyHeader(buffer, 0, new MessageHeaderEncoder())
            .parentOrderId(parentOrderId)
            .strategyId((short) Ids.STRATEGY_MARKET_MAKING)
            .executionStrategyId(ExecutionStrategyIds.POST_ONLY_QUOTE)
            .intentType(ParentIntentType.QUOTE)
            .side(side)
            .instrumentId(instrumentId)
            .primaryVenueId(venueId)
            .secondaryVenueId(0)
            .quantityScaled(qtyScaled)
            .priceMode(PriceMode.LIMIT)
            .limitPriceScaled(priceScaled)
            .referencePriceScaled(0L)
            .timeInForcePreference(TimeInForce.GTC)
            .urgencyHint((byte) 1)
            .postOnlyPreference(BooleanType.TRUE)
            .selfTradePolicy((byte) 0)
            .correlationId(parentOrderId)
            .legCount((byte) 1)
            .leg2Side(Side.SELL)
            .leg2LimitPriceScaled(0L)
            .parentTimeoutMicros(0L);
        final ParentOrderIntentDecoder decoder = new ParentOrderIntentDecoder();
        decoder.wrapAndApplyHeader(buffer, 0, new MessageHeaderDecoder());
        return decoder;
    }

    private static IdRegistry idRegistry() {
        return new IdRegistry() {
            @Override public int venueId(final long sessionId) { return Ids.VENUE_COINBASE; }
            @Override public int instrumentId(final CharSequence symbol) { return Ids.INSTRUMENT_BTC_USD; }
            @Override public String symbolOf(final int instrumentId) { return "BTC-USD"; }
            @Override public String venueNameOf(final int venueId) { return "coinbase"; }
            @Override public void registerSession(final int venueId, final long sessionId) { }
        };
    }

    private static long scaled(final long value) {
        return value * Ids.SCALE;
    }

    private record Harness(
        PostOnlyQuoteExecution strategy,
        ParentOrderRegistry registry,
        RecordingOrderManager order,
        ConfigRisk risk) {
    }

    private static final class ConfigRisk implements RiskEngine {
        private final RiskLimitConfig limits;
        RiskDecision lastDecision = RiskDecision.APPROVED;

        ConfigRisk(final RiskLimitConfig limits) {
            this.limits = limits;
        }

        @Override
        public RiskDecision preTradeCheck(
            final int venueId,
            final int instrumentId,
            final byte side,
            final long priceScaled,
            final long qtyScaled,
            final int strategyId) {
            lastDecision = limits.preChildSubmitCheck(venueId, instrumentId, side, priceScaled, qtyScaled);
            return lastDecision;
        }

        @Override public void updatePositionSnapshot(final int venueId, final int instrumentId, final long netQtyScaled) { limits.updatePositionSnapshot(venueId, instrumentId, netQtyScaled); }
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

    private static final class RecordingOrderManager implements OrderManager {
        int createCalls;

        @Override public void createPendingOrder(final long clOrdId, final int venueId, final int instrumentId, final byte side, final byte ordType, final byte timeInForce, final long priceScaled, final long qtyScaled, final int strategyId) {
            createCalls++;
        }
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
}
