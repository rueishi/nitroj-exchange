package ig.rueishi.nitroj.exchange.strategy;

import static org.assertj.core.api.Assertions.assertThat;

import ig.rueishi.nitroj.exchange.cluster.InternalMarketView;
import ig.rueishi.nitroj.exchange.cluster.PortfolioEngine;
import ig.rueishi.nitroj.exchange.cluster.RecoveryCoordinator;
import ig.rueishi.nitroj.exchange.cluster.RiskDecision;
import ig.rueishi.nitroj.exchange.cluster.RiskEngine;
import ig.rueishi.nitroj.exchange.common.ArbStrategyConfig;
import ig.rueishi.nitroj.exchange.common.ExecutionStrategyIds;
import ig.rueishi.nitroj.exchange.common.Ids;
import ig.rueishi.nitroj.exchange.execution.ChildExecutionView;
import ig.rueishi.nitroj.exchange.execution.ExecutionStrategy;
import ig.rueishi.nitroj.exchange.execution.ExecutionStrategyContext;
import ig.rueishi.nitroj.exchange.execution.ExecutionStrategyEngine;
import ig.rueishi.nitroj.exchange.execution.ExecutionStrategyRegistry;
import ig.rueishi.nitroj.exchange.execution.ParentOrderIntentView;
import ig.rueishi.nitroj.exchange.execution.ParentOrderRegistry;
import ig.rueishi.nitroj.exchange.execution.ParentOrderState;
import ig.rueishi.nitroj.exchange.messages.BooleanType;
import ig.rueishi.nitroj.exchange.messages.EntryType;
import ig.rueishi.nitroj.exchange.messages.ExecType;
import ig.rueishi.nitroj.exchange.messages.ExecutionEventDecoder;
import ig.rueishi.nitroj.exchange.messages.ExecutionEventEncoder;
import ig.rueishi.nitroj.exchange.messages.MarketDataEventDecoder;
import ig.rueishi.nitroj.exchange.messages.MarketDataEventEncoder;
import ig.rueishi.nitroj.exchange.messages.MessageHeaderDecoder;
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
 * T-010 coverage for the cross-venue arbitrage strategy.
 *
 * <p>The harness drives the production strategy with synthetic L2 books and
 * records order, offer, timer, and risk effects. Assertions focus on the
 * algorithmic invariants owned by TASK-031: net-profit gating, single-offer
 * dual-leg submission, id derivation, IOC legs, timeout cancel/hedge behavior,
 * and hedge-failure kill switch activation.</p>
 */
final class ArbStrategyTest {
    @Test
    void opportunityDetection_highFees_noTradeExecuted() {
        final Harness harness = harness(config(1_000, 100_000_000L));
        seedOpportunity(harness);

        trigger(harness);

        assertThat(harness.order.orders).isEmpty();
        assertThat(harness.cluster.offerLengths).isEmpty();
    }

    @Test
    void metadataAccess_returnsCachedArrays() {
        final Harness harness = harness(config(1, 0L));

        assertThat(harness.strategy.subscribedInstrumentIds()).isSameAs(harness.strategy.subscribedInstrumentIds());
        assertThat(harness.strategy.activeVenueIds()).isSameAs(harness.strategy.activeVenueIds());
        assertThat(harness.strategy.subscribedInstrumentIds()).containsExactly(Ids.INSTRUMENT_BTC_USD);
        assertThat(harness.strategy.activeVenueIds()).containsExactly(Ids.VENUE_COINBASE, Ids.VENUE_COINBASE_SANDBOX);
    }

    @Test
    void opportunityDetection_sufficientSpread_tradeExecuted() {
        final Harness harness = harness(config(1, 0L));
        seedOpportunity(harness);

        trigger(harness);

        assertThat(harness.order.orders).hasSize(2);
        assertThat(harness.strategy.arbActive()).isTrue();
    }

    @Test
    void opportunityDetection_missingVenueQuote_noTradeExecuted() {
        final Harness harness = harness(config(1, 0L));
        apply(harness, Ids.VENUE_COINBASE, EntryType.ASK, 100_000L * Ids.SCALE);
        apply(harness, Ids.VENUE_COINBASE_SANDBOX, EntryType.ASK, 100_600L * Ids.SCALE);

        trigger(harness);

        assertThat(harness.order.orders).isEmpty();
        assertThat(harness.cluster.offerLengths).isEmpty();
    }

    @Test
    void opportunityDetection_minProfitBoundary_tradesAtThresholdOnly() {
        final Harness atThreshold = harness(config(39, 0L));
        seedOpportunity(atThreshold);

        trigger(atThreshold);

        assertThat(atThreshold.order.orders).hasSize(2);

        final Harness aboveThreshold = harness(config(40, 0L));
        seedOpportunity(aboveThreshold);

        trigger(aboveThreshold);

        assertThat(aboveThreshold.order.orders).isEmpty();
    }

    @Test
    void opportunityDetection_maxPositionCap_limitsExecutableSize() {
        final Harness harness = harness(config(1, 0L, 5000_0000L));
        seedOpportunity(harness);

        trigger(harness);

        assertThat(harness.order.orders).hasSize(2);
        assertThat(harness.order.orders.get(0).qtyScaled).isEqualTo(5000_0000L);
    }

    @Test
    void opportunityDetection_ownLiquidityOnlyFalsePositive_noTradeExecuted() {
        final Harness harness = harness(config(1, 0L));
        seedOpportunity(harness);
        harness.marketView.ownOrderOverlay().upsert(
            10L, "OWN-ASK", Ids.VENUE_COINBASE, Ids.INSTRUMENT_BTC_USD,
            EntryType.ASK, 100_000L * Ids.SCALE, 10L * Ids.SCALE, true);

        trigger(harness);

        assertThat(harness.order.orders).isEmpty();
        assertThat(harness.cluster.offerLengths).isEmpty();
    }

    @Test
    void opportunityDetection_partialOwnLiquidity_reducesExecutableSize() {
        final Harness harness = harness(config(1, 0L));
        seedOpportunity(harness);
        harness.marketView.ownOrderOverlay().upsert(
            10L, "OWN-ASK", Ids.VENUE_COINBASE, Ids.INSTRUMENT_BTC_USD,
            EntryType.ASK, 100_000L * Ids.SCALE, 9_5000_0000L, true);

        trigger(harness);

        assertThat(harness.order.orders).hasSize(2);
        assertThat(harness.order.orders.get(0).qtyScaled).isEqualTo(5000_0000L);
    }

    @Test
    void selfCross_buyLegAgainstOwnSell_skipsTrade() {
        final Harness harness = harness(config(1, 0L));
        seedOpportunity(harness);
        harness.marketView.ownOrderOverlay().upsert(
            10L, "OWN-LOWER-ASK", Ids.VENUE_COINBASE, Ids.INSTRUMENT_BTC_USD,
            EntryType.ASK, 99_000L * Ids.SCALE, Ids.SCALE, true);

        trigger(harness);

        assertThat(harness.order.orders).isEmpty();
    }

    @Test
    void selfCross_sellLegAgainstOwnBuy_skipsTrade() {
        final Harness harness = harness(config(1, 0L));
        seedOpportunity(harness);
        harness.marketView.ownOrderOverlay().upsert(
            10L, "OWN-HIGHER-BID", Ids.VENUE_COINBASE_SANDBOX, Ids.INSTRUMENT_BTC_USD,
            EntryType.BID, 101_000L * Ids.SCALE, Ids.SCALE, true);

        trigger(harness);

        assertThat(harness.order.orders).isEmpty();
    }

    @Test
    void preTradeRiskRejected_noTradeAndCooldown() {
        final Harness harness = harness(config(1, 0L));
        harness.risk.decision = RiskDecision.REJECT_ORDER_TOO_LARGE;
        seedOpportunity(harness);

        trigger(harness);
        deliverRiskRejectedParentTerminal(harness);

        assertThat(harness.order.orders).isEmpty();
        assertThat(harness.strategy.cooldownUntilMicros()).isGreaterThan(harness.cluster.time);
    }

    @Test
    void synchronousSubmitReject_resetsArbStateAndAppliesCooldown() {
        final Harness harness = harness(config(1, 0L), false);
        seedOpportunity(harness);

        trigger(harness);

        assertThat(harness.strategy.arbActive()).isFalse();
        assertThat(harness.strategy.arbAttemptId()).isZero();
        assertThat(harness.strategy.cooldownUntilMicros()).isGreaterThan(harness.cluster.time);
        assertThat(harness.order.orders).isEmpty();
    }

    @Test
    void netProfitFormula_includesFeesAndSlippage() {
        final Harness harness = harness(config(1, 0L));
        seedOpportunity(harness);

        final long slip = harness.strategy.slippageScaled(Ids.VENUE_COINBASE, Ids.SCALE);

        assertThat(slip).isEqualTo(4_997_500_000L);
    }

    @Test
    void arbAttemptId_andClOrdIds_derivedFromSingleLogPosition() {
        final Harness harness = harness(config(1, 0L));
        harness.cluster.logPosition = 900L;
        seedOpportunity(harness);

        trigger(harness);

        assertThat(harness.strategy.arbAttemptId()).isEqualTo(900L);
        assertThat(harness.strategy.leg1ClOrdId()).isEqualTo(901L);
        assertThat(harness.strategy.leg2ClOrdId()).isEqualTo(902L);
    }

    @Test
    void bothLegsInSingleEgressOffer_andIoc() {
        final Harness harness = harness(config(1, 0L));
        seedOpportunity(harness);

        trigger(harness);

        assertThat(harness.cluster.offerLengths).hasSize(1);
        assertThat(harness.order.orders).extracting(order -> order.timeInForce).containsOnly(TimeInForce.IOC);
    }

    @Test
    void leg1IsBuyOnCheaperVenue_leg2SellOnExpensiveVenue() {
        final Harness harness = harness(config(1, 0L));
        seedOpportunity(harness);

        trigger(harness);

        assertThat(harness.order.orders.get(0).venueId).isEqualTo(Ids.VENUE_COINBASE);
        assertThat(harness.order.orders.get(0).side).isEqualTo(Side.BUY);
        assertThat(harness.order.orders.get(1).venueId).isEqualTo(Ids.VENUE_COINBASE_SANDBOX);
        assertThat(harness.order.orders.get(1).side).isEqualTo(Side.SELL);
    }

    @Test
    void fill_bothLegsFinal_noImbalance_arbReset() {
        final Harness harness = executedHarness();

        harness.strategy.onFill(execution(harness.strategy.leg1ClOrdId(), Ids.SCALE, BooleanType.TRUE));
        harness.strategy.onFill(execution(harness.strategy.leg2ClOrdId(), Ids.SCALE, BooleanType.TRUE));

        assertThat(harness.strategy.arbActive()).isFalse();
    }

    @Test
    void fill_imbalanced_hedgeSubmitted() {
        final Harness harness = executedHarness();
        harness.cluster.offerLengths.clear();

        harness.strategy.onFill(execution(harness.strategy.leg1ClOrdId(), 3L * Ids.SCALE, BooleanType.TRUE));
        harness.strategy.onFill(execution(harness.strategy.leg2ClOrdId(), Ids.SCALE, BooleanType.TRUE));

        assertThat(harness.order.orders).extracting(order -> order.strategyId).contains(Ids.STRATEGY_ARB_HEDGE);
        assertThat(harness.cluster.offerLengths).hasSize(1);
    }

    @Test
    void hedgeFailure_killSwitchActivated() {
        final Harness harness = executedHarness();
        harness.risk.decision = RiskDecision.REJECT_KILL_SWITCH;

        harness.strategy.onFill(execution(harness.strategy.leg1ClOrdId(), 3L * Ids.SCALE, BooleanType.TRUE));
        harness.strategy.onFill(execution(harness.strategy.leg2ClOrdId(), Ids.SCALE, BooleanType.TRUE));

        assertThat(harness.risk.killSwitchReason).isEqualTo("hedge_failure");
    }

    @Test
    void legTimeout_pendingLegCanceled_hedgeSubmitted() {
        final Harness harness = executedHarness();
        harness.strategy.onFill(execution(harness.strategy.leg1ClOrdId(), 3L * Ids.SCALE, BooleanType.TRUE));
        harness.cluster.offerLengths.clear();

        harness.strategy.onTimer(harness.strategy.activeArbTimeoutCorrelId());

        assertThat(harness.cluster.offerKinds).contains("cancel", "hedge");
        assertThat(harness.strategy.arbActive()).isFalse();
    }

    @Test
    void legTimeout_wrongCorrelId_ignored() {
        final Harness harness = executedHarness();

        harness.strategy.onTimer(999L);

        assertThat(harness.strategy.arbActive()).isTrue();
    }

    @Test
    void noNewArb_whileArbActive() {
        final Harness harness = executedHarness();
        harness.order.orders.clear();

        trigger(harness);

        assertThat(harness.order.orders).isEmpty();
    }

    @Test
    void timerRegistered_onEachArbExecution() {
        final Harness harness = executedHarness();

        assertThat(harness.cluster.scheduledCorrelationId).isEqualTo(harness.strategy.activeArbTimeoutCorrelId());
    }

    @Test
    void cooldown_afterHedgeFailure_newArbSuppressed() {
        final Harness harness = executedHarness();
        harness.risk.decision = RiskDecision.REJECT_KILL_SWITCH;
        harness.strategy.onFill(execution(harness.strategy.leg1ClOrdId(), 3L * Ids.SCALE, BooleanType.TRUE));
        harness.strategy.onFill(execution(harness.strategy.leg2ClOrdId(), Ids.SCALE, BooleanType.TRUE));
        harness.order.orders.clear();

        trigger(harness);

        assertThat(harness.order.orders).isEmpty();
        assertThat(harness.strategy.cooldownUntilMicros()).isGreaterThan(harness.cluster.time);
    }

    @Test
    void cooldown_exactBoundary_allowsNewArb() {
        final Harness harness = harness(config(1, 0L));
        harness.risk.decision = RiskDecision.REJECT_ORDER_TOO_LARGE;
        seedOpportunity(harness);
        trigger(harness);
        deliverRiskRejectedParentTerminal(harness);
        harness.order.orders.clear();
        harness.risk.decision = RiskDecision.APPROVED;
        harness.cluster.time = harness.strategy.cooldownUntilMicros();

        trigger(harness);

        assertThat(harness.order.orders).hasSize(2);
    }

    static Harness executedHarness() {
        final Harness harness = harness(config(1, 0L));
        seedOpportunity(harness);
        trigger(harness);
        return harness;
    }

    static ArbStrategyConfig config(final long minNetProfitBps, final long feeScaled) {
        return config(minNetProfitBps, feeScaled, 10L * Ids.SCALE);
    }

    static ArbStrategyConfig config(final long minNetProfitBps, final long feeScaled, final long maxPositionScaled) {
        final long[] fees = new long[Ids.MAX_VENUES + 1];
        final long[] baseSlip = new long[Ids.MAX_VENUES + 1];
        final long[] slope = new long[Ids.MAX_VENUES + 1];
        fees[Ids.VENUE_COINBASE] = feeScaled;
        fees[Ids.VENUE_COINBASE_SANDBOX] = feeScaled;
        baseSlip[Ids.VENUE_COINBASE] = 5;
        baseSlip[Ids.VENUE_COINBASE_SANDBOX] = 5;
        return new ArbStrategyConfig(Ids.INSTRUMENT_BTC_USD, new int[]{Ids.VENUE_COINBASE, Ids.VENUE_COINBASE_SANDBOX}, minNetProfitBps, fees, baseSlip, slope, Ids.SCALE, maxPositionScaled, 100, 5_000_000, 10_000_000);
    }

    static Harness harness(final ArbStrategyConfig config) {
        return harness(config, true);
    }

    static Harness harness(final ArbStrategyConfig config, final boolean allowCompatibility) {
        final InternalMarketView marketView = new InternalMarketView();
        final RecordingRisk risk = new RecordingRisk();
        final RecordingOrder order = new RecordingOrder();
        final RecordingCluster cluster = new RecordingCluster();
        final ParentOrderRegistry parentRegistry = new ParentOrderRegistry(16, 16);
        final ExecutionStrategyRegistry executionRegistry = new ExecutionStrategyRegistry(8, 8);
        final RecordingExecutionStrategy executionStrategy = new RecordingExecutionStrategy(order, cluster);
        executionRegistry.register(executionStrategy);
        if (allowCompatibility) {
            executionRegistry.allowCompatibility(Ids.STRATEGY_ARB, ExecutionStrategyIds.MULTI_LEG_CONTINGENT);
        }
        final ExecutionStrategyEngine executionEngine = new ExecutionStrategyEngine(
            executionRegistry,
            new ExecutionStrategyContext(
                marketView,
                risk,
                order,
                parentRegistry,
                new UnsafeBuffer(new byte[1024]),
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
        final ArbStrategy strategy = new ArbStrategy(config);
        strategy.init(new StrategyContextImpl(marketView, risk, order, new RecordingPortfolio(), new RecordingRecovery(), executionEngine, cluster.proxy,
            new UnsafeBuffer(new byte[1024]), new MessageHeaderEncoder(),
            new ig.rueishi.nitroj.exchange.messages.NewOrderCommandEncoder(),
            new ig.rueishi.nitroj.exchange.messages.CancelOrderCommandEncoder(), null, null));
        return new Harness(strategy, marketView, risk, order, cluster);
    }

    static void seedOpportunity(final Harness harness) {
        apply(harness, Ids.VENUE_COINBASE, EntryType.BID, 99_900L * Ids.SCALE);
        apply(harness, Ids.VENUE_COINBASE, EntryType.ASK, 100_000L * Ids.SCALE);
        apply(harness, Ids.VENUE_COINBASE_SANDBOX, EntryType.BID, 100_500L * Ids.SCALE);
        apply(harness, Ids.VENUE_COINBASE_SANDBOX, EntryType.ASK, 100_600L * Ids.SCALE);
    }

    static void trigger(final Harness harness) {
        final MarketDataEventDecoder decoder = decoder(Ids.VENUE_COINBASE_SANDBOX, EntryType.BID, 100_500L * Ids.SCALE);
        harness.strategy.onMarketData(decoder);
    }

    static void deliverRiskRejectedParentTerminal(final Harness harness) {
        final long parentOrderId = harness.strategy.arbAttemptId();
        final UnsafeBuffer buffer = new UnsafeBuffer(new byte[128]);
        new ParentOrderTerminalEncoder().wrapAndApplyHeader(buffer, 0, new MessageHeaderEncoder())
            .parentOrderId(parentOrderId)
            .strategyId((short) Ids.STRATEGY_ARB)
            .executionStrategyId(ExecutionStrategyIds.MULTI_LEG_CONTINGENT)
            .finalCumFillQtyScaled(0L)
            .terminalReason(ParentTerminalReason.RISK_REJECTED);
        final ParentOrderTerminalDecoder decoder = new ParentOrderTerminalDecoder();
        decoder.wrapAndApplyHeader(buffer, 0, new MessageHeaderDecoder());
        harness.strategy.onParentTerminal(decoder);
    }

    static void apply(final Harness harness, final int venueId, final EntryType type, final long price) {
        harness.marketView.apply(decoder(venueId, type, price), harness.cluster.time);
    }

    static MarketDataEventDecoder decoder(final int venueId, final EntryType type, final long price) {
        final UnsafeBuffer buffer = new UnsafeBuffer(new byte[128]);
        new MarketDataEventEncoder().wrapAndApplyHeader(buffer, 0, new MessageHeaderEncoder())
            .venueId(venueId).instrumentId(Ids.INSTRUMENT_BTC_USD).entryType(type).updateAction(UpdateAction.NEW)
            .priceScaled(price).sizeScaled(10L * Ids.SCALE).priceLevel(0)
            .ingressTimestampNanos(1).exchangeTimestampNanos(1).fixSeqNum(1);
        final MarketDataEventDecoder decoder = new MarketDataEventDecoder();
        decoder.wrap(buffer, MessageHeaderEncoder.ENCODED_LENGTH, MarketDataEventEncoder.BLOCK_LENGTH, MarketDataEventEncoder.SCHEMA_VERSION);
        return decoder;
    }

    static ExecutionEventDecoder execution(final long clOrdId, final long fillQty, final BooleanType isFinal) {
        final UnsafeBuffer buffer = new UnsafeBuffer(new byte[256]);
        new ExecutionEventEncoder().wrapAndApplyHeader(buffer, 0, new MessageHeaderEncoder())
            .clOrdId(clOrdId).venueId(Ids.VENUE_COINBASE).instrumentId(Ids.INSTRUMENT_BTC_USD)
            .execType(ExecType.FILL).side(Side.BUY).fillPriceScaled(100_000L * Ids.SCALE)
            .fillQtyScaled(fillQty).cumQtyScaled(fillQty).leavesQtyScaled(0)
            .rejectCode(0).ingressTimestampNanos(1).exchangeTimestampNanos(1).fixSeqNum(1)
            .isFinal(isFinal).putVenueOrderId(new byte[0], 0, 0).putExecId(new byte[0], 0, 0);
        final ExecutionEventDecoder decoder = new ExecutionEventDecoder();
        decoder.wrap(buffer, MessageHeaderEncoder.ENCODED_LENGTH, ExecutionEventEncoder.BLOCK_LENGTH, ExecutionEventEncoder.SCHEMA_VERSION);
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

    record Harness(ArbStrategy strategy, InternalMarketView marketView, RecordingRisk risk, RecordingOrder order, RecordingCluster cluster) {
    }

    static final class RecordingCluster {
        final Cluster proxy;
        long time = 1_000L;
        long logPosition = 800L;
        long scheduledCorrelationId;
        final List<Integer> offerLengths = new ArrayList<>();
        final List<String> offerKinds = new ArrayList<>();

        RecordingCluster() {
            proxy = (Cluster)Proxy.newProxyInstance(Cluster.class.getClassLoader(), new Class<?>[]{Cluster.class}, (p, m, a) -> switch (m.getName()) {
                case "time" -> time;
                case "logPosition" -> logPosition;
                case "scheduleTimer" -> { scheduledCorrelationId = (Long)a[0]; yield true; }
                case "cancelTimer" -> true;
                case "offer" -> {
                    final int length = ((Number)a[2]).intValue();
                    offerLengths.add(length);
                    final org.agrona.DirectBuffer buffer = (org.agrona.DirectBuffer)a[0];
                    final int offset = ((Number)a[1]).intValue();
                    final int templateId = buffer.getShort(offset + 2, java.nio.ByteOrder.LITTLE_ENDIAN);
                    if (templateId == ig.rueishi.nitroj.exchange.messages.CancelOrderCommandEncoder.TEMPLATE_ID) {
                        offerKinds.add("cancel");
                    } else {
                        offerKinds.add(length > MessageHeaderEncoder.ENCODED_LENGTH + ig.rueishi.nitroj.exchange.messages.NewOrderCommandEncoder.BLOCK_LENGTH ? "arb" : "hedge");
                    }
                    yield (long)length;
                }
                case "timeUnit" -> TimeUnit.MICROSECONDS;
                case "toString" -> "ArbCluster";
                default -> null;
            });
        }
    }

    static final class OrderRecord {
        final long clOrdId; final int venueId; final Side side; final TimeInForce timeInForce; final int strategyId; final long qtyScaled;
        OrderRecord(final long clOrdId, final int venueId, final Side side, final TimeInForce timeInForce, final int strategyId, final long qtyScaled) {
            this.clOrdId = clOrdId; this.venueId = venueId; this.side = side; this.timeInForce = timeInForce; this.strategyId = strategyId; this.qtyScaled = qtyScaled;
        }
    }

    static final class RecordingExecutionStrategy implements ExecutionStrategy {
        final RecordingOrder order;
        final RecordingCluster cluster;
        ExecutionStrategyContext ctx;

        RecordingExecutionStrategy(final RecordingOrder order, final RecordingCluster cluster) {
            this.order = order;
            this.cluster = cluster;
        }

        @Override public int executionStrategyId() { return ExecutionStrategyIds.MULTI_LEG_CONTINGENT; }
        @Override public void init(final ExecutionStrategyContext ctx) { this.ctx = ctx; }
        @Override public void onParentIntent(final ParentOrderIntentView intent) {
            ctx.parentOrderRegistry().claim(intent.parentOrderId(), intent.strategyId(), executionStrategyId(), intent.quantityScaled(), 1L);
            final RiskDecision leg1Risk = ctx.riskEngine().preTradeCheck(
                intent.primaryVenueId(),
                intent.instrumentId(),
                Side.BUY.value(),
                intent.limitPriceScaled(),
                intent.quantityScaled(),
                Ids.STRATEGY_ARB);
            if (!leg1Risk.approved()) {
                ctx.parentOrderRegistry().transition(intent.parentOrderId(), ParentOrderState.FAILED,
                    ParentOrderState.REASON_RISK_REJECTED, 1L);
                return;
            }
            final RiskDecision leg2Risk = ctx.riskEngine().preTradeCheck(
                intent.secondaryVenueId(),
                intent.instrumentId(),
                Side.SELL.value(),
                intent.decoder().leg2LimitPriceScaled(),
                intent.quantityScaled(),
                Ids.STRATEGY_ARB);
            if (!leg2Risk.approved()) {
                ctx.parentOrderRegistry().transition(intent.parentOrderId(), ParentOrderState.FAILED,
                    ParentOrderState.REASON_RISK_REJECTED, 1L);
                return;
            }
            order.orders.add(new OrderRecord(intent.correlationId() + 1L, intent.primaryVenueId(), Side.BUY, TimeInForce.IOC, Ids.STRATEGY_ARB, intent.quantityScaled()));
            order.orders.add(new OrderRecord(intent.correlationId() + 2L, intent.secondaryVenueId(), Side.SELL, TimeInForce.IOC, Ids.STRATEGY_ARB, intent.quantityScaled()));
            cluster.offerLengths.add(128);
            cluster.offerKinds.add("arb");
        }
        @Override public void onMarketDataTick(final int venueId, final int instrumentId, final long clusterTimeMicros) { }
        @Override public void onChildExecution(final ChildExecutionView execution) { }
        @Override public void onTimer(final long correlationId) { }
        @Override public void onCancel(final long parentOrderId, final byte reasonCode) { cluster.offerKinds.add("cancel"); }
    }

    static final class RecordingOrder implements OrderManager {
        final List<OrderRecord> orders = new ArrayList<>();
        @Override public void createPendingOrder(final long clOrdId, final int venueId, final int instrumentId, final byte side, final byte ordType, final byte timeInForce, final long priceScaled, final long qtyScaled, final int strategyId) { orders.add(new OrderRecord(clOrdId, venueId, Side.get(side), TimeInForce.get(timeInForce), strategyId, qtyScaled)); assertThat(ordType).isEqualTo(OrdType.LIMIT.value()); }
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

    static final class RecordingRisk implements RiskEngine {
        RiskDecision decision = RiskDecision.APPROVED;
        String killSwitchReason;
        int preTradeChecks;
        @Override public RiskDecision preTradeCheck(final int venueId, final int instrumentId, final byte side, final long priceScaled, final long qtyScaled, final int strategyId) { preTradeChecks++; return decision; }
        @Override public void updatePositionSnapshot(final int venueId, final int instrumentId, final long netQtyScaled) { }
        @Override public void updateDailyPnl(final long realizedPnlDeltaScaled) { }
        @Override public void setRecoveryLock(final int venueId, final boolean locked) { }
        @Override public long getDailyPnlScaled() { return 0; }
        @Override public void activateKillSwitch(final String reason) { killSwitchReason = reason; }
        @Override public void deactivateKillSwitch() { }
        @Override public boolean isKillSwitchActive() { return false; }
        @Override public void writeSnapshot(final ExclusivePublication snapshotPublication) { }
        @Override public void loadSnapshot(final Image snapshotImage) { }
        @Override public void resetDailyCounters() { }
        @Override public void setCluster(final Cluster cluster) { }
        @Override public void onFill(final ExecutionEventDecoder decoder) { }
        @Override public void resetAll() { }
    }

    static final class RecordingPortfolio implements PortfolioEngine {
        @Override public void initPosition(final int venueId, final int instrumentId) { }
        @Override public void onFill(final ExecutionEventDecoder decoder) { }
        @Override public void refreshUnrealizedPnl(final int venueId, final int instrumentId, final long markPriceScaled) { }
        @Override public long getNetQtyScaled(final int venueId, final int instrumentId) { return 0; }
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

    static final class RecordingRecovery implements RecoveryCoordinator {
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
