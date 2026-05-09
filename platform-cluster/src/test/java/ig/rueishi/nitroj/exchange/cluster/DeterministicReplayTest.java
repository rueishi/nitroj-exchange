package ig.rueishi.nitroj.exchange.cluster;

import ig.rueishi.nitroj.exchange.common.Ids;
import ig.rueishi.nitroj.exchange.common.OrderStatus;
import ig.rueishi.nitroj.exchange.common.RiskConfig;
import ig.rueishi.nitroj.exchange.common.ExecutionStrategyIds;
import ig.rueishi.nitroj.exchange.execution.ChildExecutionView;
import ig.rueishi.nitroj.exchange.execution.ExecutionStrategyContext;
import ig.rueishi.nitroj.exchange.execution.FeeSchedule;
import ig.rueishi.nitroj.exchange.execution.ImmediateLimitExecution;
import ig.rueishi.nitroj.exchange.execution.MultiLegContingentExecution;
import ig.rueishi.nitroj.exchange.execution.ParentOrderIntentView;
import ig.rueishi.nitroj.exchange.execution.ParentOrderRegistry;
import ig.rueishi.nitroj.exchange.execution.ParentOrderState;
import ig.rueishi.nitroj.exchange.execution.ParallelVenueExecution;
import ig.rueishi.nitroj.exchange.execution.PostOnlyQuoteExecution;
import ig.rueishi.nitroj.exchange.execution.SmartOrderRoutingExecution;
import ig.rueishi.nitroj.exchange.messages.BooleanType;
import ig.rueishi.nitroj.exchange.messages.CancelOrderCommandEncoder;
import ig.rueishi.nitroj.exchange.messages.EntryType;
import ig.rueishi.nitroj.exchange.messages.ExecType;
import ig.rueishi.nitroj.exchange.messages.ExecutionEventDecoder;
import ig.rueishi.nitroj.exchange.messages.ExecutionEventEncoder;
import ig.rueishi.nitroj.exchange.messages.MarketByOrderEventDecoder;
import ig.rueishi.nitroj.exchange.messages.MarketByOrderEventEncoder;
import ig.rueishi.nitroj.exchange.messages.MarketDataEventDecoder;
import ig.rueishi.nitroj.exchange.messages.MarketDataEventEncoder;
import ig.rueishi.nitroj.exchange.messages.MessageHeaderDecoder;
import ig.rueishi.nitroj.exchange.messages.MessageHeaderEncoder;
import ig.rueishi.nitroj.exchange.messages.NewOrderCommandEncoder;
import ig.rueishi.nitroj.exchange.messages.OrdType;
import ig.rueishi.nitroj.exchange.messages.ParentIntentType;
import ig.rueishi.nitroj.exchange.messages.ParentOrderIntentDecoder;
import ig.rueishi.nitroj.exchange.messages.ParentOrderIntentEncoder;
import ig.rueishi.nitroj.exchange.messages.PriceMode;
import ig.rueishi.nitroj.exchange.messages.Side;
import ig.rueishi.nitroj.exchange.messages.TimeInForce;
import ig.rueishi.nitroj.exchange.messages.UpdateAction;
import ig.rueishi.nitroj.exchange.order.OrderManagerImpl;
import ig.rueishi.nitroj.exchange.order.OrderState;
import ig.rueishi.nitroj.exchange.registry.IdRegistry;
import io.aeron.ExclusivePublication;
import io.aeron.Image;
import io.aeron.cluster.service.Cluster;
import org.agrona.concurrent.UnsafeBuffer;
import org.agrona.concurrent.status.CountersManager;
import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Replay-style determinism coverage for the cluster market/decision core.
 *
 * <p>Responsibility: apply ordered internal events twice from the same initial
 * state and compare externally visible state plus emitted command decisions.
 * Role in system: protects the V11 definition of determinism: identical ordered
 * SBE input streams plus the same snapshot produce identical L2/L3 books,
 * execution state, own-liquidity, risk decisions, timer effects, capacity
 * counters, and outbound command summaries. Relationships: uses real {@link
 * InternalMarketView}, {@link VenueL3Book}, {@link OwnOrderOverlay}, {@link
 * ExternalLiquidityView}, {@link OrderManagerImpl}, and {@link RiskEngineImpl}
 * while keeping the replay fixture intentionally small and local. Lifecycle:
 * unit test only; replay inputs carry deterministic timestamps and no wall-clock
 * state participates in comparisons.</p>
 */
final class DeterministicReplayTest {
    private static final int VENUE = Ids.VENUE_COINBASE;
    private static final int SECOND_VENUE = 2;
    private static final int INSTRUMENT = Ids.INSTRUMENT_BTC_USD;
    private static final long CL_ORD_ID = 9001L;

    @Test
    void positive_sameOrderedReplayProducesIdenticalStateAndCommands() {
        final List<ReplayEvent> stream = List.of(
            ReplayEvent.market(VENUE, EntryType.BID, 100L, 10L),
            ReplayEvent.market(SECOND_VENUE, EntryType.ASK, 99L, 10L),
            ReplayEvent.l3("L3-1", Side.SELL, UpdateAction.NEW, 101L, 4L),
            ReplayEvent.createOrder(CL_ORD_ID),
            ReplayEvent.execution(CL_ORD_ID, ExecType.NEW, 0L, 0L, 5L, false, "EXEC-ACK"),
            ReplayEvent.execution(CL_ORD_ID, ExecType.PARTIAL_FILL, 100L, 2L, 3L, false, "EXEC-FILL-1"),
            ReplayEvent.execution(CL_ORD_ID, ExecType.PARTIAL_FILL, 100L, 2L, 3L, false, "EXEC-FILL-1"),
            ReplayEvent.riskDecision(false),
            ReplayEvent.capacityFull(),
            ReplayEvent.snapshotLoad(),
            ReplayEvent.timer(42L),
            ReplayEvent.strategyTick(),
            ReplayEvent.riskDecision(true));

        final ReplayResult first = ReplayHarness.apply(stream);
        final ReplayResult second = ReplayHarness.apply(stream);

        assertThat(second).isEqualTo(first);
        assertThat(first.commands).containsExactly(
            "RISK_REJECTED:3",
            "TIMER:42",
            "ARB_CHECK:" + 100L,
            "RISK_APPROVED");
        assertThat(first.duplicateExecutionReportCount).isEqualTo(1);
        assertThat(first.capacityDropCount).isEqualTo(1);
        assertThat(first.orderStatus).isEqualTo(OrderStatus.PARTIALLY_FILLED);
    }

    @Test
    void negative_changedEventOrderProducesDifferentResult() {
        final ReplayResult first = ReplayHarness.apply(List.of(
            ReplayEvent.market(VENUE, EntryType.BID, 100L, 10L),
            ReplayEvent.strategyTick()));
        final ReplayResult second = ReplayHarness.apply(List.of(
            ReplayEvent.strategyTick(),
            ReplayEvent.market(VENUE, EntryType.BID, 100L, 10L)));

        assertThat(second).isNotEqualTo(first);
    }

    @Test
    void edge_emptySingleDuplicateAndMaxIdsReplayDeterministically() {
        assertThat(ReplayHarness.apply(List.of())).isEqualTo(ReplayHarness.apply(List.of()));
        assertThat(ReplayHarness.apply(List.of(ReplayEvent.strategyTick())))
            .isEqualTo(ReplayHarness.apply(List.of(ReplayEvent.strategyTick())));
        assertThat(ReplayHarness.apply(List.of(
            ReplayEvent.market(Ids.MAX_VENUES, EntryType.BID, 101L, 1L),
            ReplayEvent.market(Ids.MAX_VENUES, EntryType.BID, 101L, 1L))))
            .isEqualTo(ReplayHarness.apply(List.of(
                ReplayEvent.market(Ids.MAX_VENUES, EntryType.BID, 101L, 1L),
                ReplayEvent.market(Ids.MAX_VENUES, EntryType.BID, 101L, 1L))));
    }

    @Test
    void exception_malformedReplayFixtureFailsClearly() {
        assertThatThrownBy(() -> ReplayHarness.apply(List.of(ReplayEvent.market(0, EntryType.BID, 100L, 1L))))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("venue");
    }

    @Test
    void failure_rejectedRiskAndStaleMarketDataProduceDeterministicCommands() {
        final List<ReplayEvent> stream = List.of(
            ReplayEvent.market(VENUE, EntryType.BID, 100L, 10L),
            ReplayEvent.riskDecision(false),
            ReplayEvent.staleMarketData(),
            ReplayEvent.strategyTick());

        final ReplayResult result = ReplayHarness.apply(stream);

        assertThat(result).isEqualTo(ReplayHarness.apply(stream));
        assertThat(result.commands).containsExactly("RISK_REJECTED:3", "STALE_MARKET_DATA", "ARB_CHECK:" + 100L);
    }

    @Test
    void v13ParentReplay_sameOrderedStreamProducesIdenticalParentStatesAndCommands() {
        final ParentReplayResult first = ParentReplayHarness.apply();
        final ParentReplayResult second = ParentReplayHarness.apply();

        assertThat(second).isEqualTo(first);
        assertThat(first.commands).containsExactly(
            "POST_SUBMIT:5001",
            "POST_PARTIAL:3",
            "POST_REJECT_RETRY:5002",
            "IMM_VENUE_SET:17",
            "IMM_RISK_REJECT:4",
            "MULTI_TIMER:7000",
            "MULTI_HEDGE_FAILED:6",
            "SNAPSHOT:3/2",
            "CAPACITY:1");
    }

    @Test
    void v14VenueIndifferentReplay_sameOrderedStreamProducesIdenticalHedgeSorAndCommandSummaries() {
        final V14ReplayResult first = V14ReplayHarness.apply();
        final V14ReplayResult second = V14ReplayHarness.apply();

        assertThat(second).isEqualTo(first);
        assertThat(first.commands).contains(
            "PAR_CHILD:90001:1:60",
            "PAR_CHILD:90002:2:40",
            "SOR_INITIAL:91001:1",
            "SOR_RESLICE:91101:2",
            "MM_CHILDREN:92000/93000",
            "SNAPSHOT_PARENT:9100",
            "CAPACITY_COUNTER:1");
        assertThat(first.sorReslices).isEqualTo(1L);
        assertThat(first.parallelParentStatus).isEqualTo(ParentOrderState.WORKING);
        assertThat(first.sorActiveChildren).isEqualTo(1);
        assertThat(first.coinbaseExternalSize).isEqualTo(7L);
        assertThat(first.binanceExternalSize).isEqualTo(11L);
    }

    private record ReplayEvent(
        EventType type,
        int venueId,
        EntryType side,
        Side orderSide,
        UpdateAction updateAction,
        long price,
        long size,
        long fillQty,
        long leavesQty,
        long timerCorrelationId,
        long clOrdId,
        String venueOrderId,
        String execId,
        ExecType execType,
        boolean approved) {

        static ReplayEvent market(final int venueId, final EntryType side, final long price, final long size) {
            return new ReplayEvent(EventType.MARKET, venueId, side, Side.NULL_VAL, UpdateAction.NEW,
                price, size, 0L, 0L, 0L, 0L, null, null, null, false);
        }

        static ReplayEvent l3(
            final String venueOrderId,
            final Side side,
            final UpdateAction updateAction,
            final long price,
            final long size) {
            return new ReplayEvent(EventType.MARKET_L3, VENUE, EntryType.NULL_VAL, side, updateAction,
                price, size, 0L, 0L, 0L, 0L, venueOrderId, null, null, false);
        }

        static ReplayEvent createOrder(final long clOrdId) {
            return new ReplayEvent(EventType.CREATE_ORDER, VENUE, EntryType.NULL_VAL, Side.BUY, UpdateAction.NEW,
                100L, 5L, 0L, 5L, 0L, clOrdId, "OWN-" + clOrdId, null, null, false);
        }

        static ReplayEvent execution(
            final long clOrdId,
            final ExecType execType,
            final long fillPrice,
            final long fillQty,
            final long leavesQty,
            final boolean isFinal,
            final String execId) {
            return new ReplayEvent(EventType.EXECUTION_REPORT, VENUE, EntryType.NULL_VAL, Side.BUY, UpdateAction.NEW,
                fillPrice, 0L, fillQty, leavesQty, isFinal ? 1L : 0L, clOrdId, "OWN-" + clOrdId, execId, execType, false);
        }

        static ReplayEvent strategyTick() {
            return new ReplayEvent(EventType.STRATEGY_TICK, 0, EntryType.NULL_VAL, Side.NULL_VAL, UpdateAction.NEW,
                0L, 0L, 0L, 0L, 0L, 0L, null, null, null, false);
        }

        static ReplayEvent riskDecision(final boolean approved) {
            return new ReplayEvent(EventType.RISK_DECISION, 0, EntryType.NULL_VAL, Side.NULL_VAL, UpdateAction.NEW,
                0L, 0L, 0L, 0L, 0L, 0L, null, null, null, approved);
        }

        static ReplayEvent staleMarketData() {
            return new ReplayEvent(EventType.STALE_MARKET_DATA, 0, EntryType.NULL_VAL, Side.NULL_VAL, UpdateAction.NEW,
                0L, 0L, 0L, 0L, 0L, 0L, null, null, null, false);
        }

        static ReplayEvent timer(final long correlationId) {
            return new ReplayEvent(EventType.TIMER, 0, EntryType.NULL_VAL, Side.NULL_VAL, UpdateAction.NEW,
                0L, 0L, 0L, 0L, correlationId, 0L, null, null, null, false);
        }

        static ReplayEvent snapshotLoad() {
            return new ReplayEvent(EventType.SNAPSHOT_LOAD, 0, EntryType.NULL_VAL, Side.NULL_VAL, UpdateAction.NEW,
                0L, 0L, 0L, 0L, 0L, 0L, null, null, null, false);
        }

        static ReplayEvent capacityFull() {
            return new ReplayEvent(EventType.CAPACITY_FULL, 0, EntryType.NULL_VAL, Side.NULL_VAL, UpdateAction.NEW,
                0L, 0L, 0L, 0L, 0L, 0L, null, null, null, false);
        }
    }

    private enum EventType {
        MARKET,
        MARKET_L3,
        CREATE_ORDER,
        EXECUTION_REPORT,
        STRATEGY_TICK,
        RISK_DECISION,
        STALE_MARKET_DATA,
        TIMER,
        SNAPSHOT_LOAD,
        CAPACITY_FULL
    }

    private record ReplayResult(
        long bestBid,
        long bestAsk,
        long consolidatedBestBid,
        long l3AskSize,
        long externalBidSize,
        int ownOrderCount,
        int l3ActiveOrderCount,
        int liveOrderCount,
        byte orderStatus,
        long invalidTransitionCount,
        long duplicateExecutionReportCount,
        byte lastRiskRejectCode,
        long timerCount,
        long capacityDropCount,
        List<String> commands) {
    }

    private record ParentReplayResult(
        byte postStatus,
        long postFilled,
        byte immediateStatus,
        byte multiStatus,
        byte multiReason,
        long hedgeRejects,
        long capacityRejects,
        List<String> commands) {
    }

    private record V14ReplayResult(
        byte parallelParentStatus,
        byte sorParentStatus,
        long sorReslices,
        int sorActiveChildren,
        long coinbaseExternalSize,
        long binanceExternalSize,
        long capacityRejects,
        List<String> commands) {
    }

    private static final class V14ReplayHarness {
        private final InternalMarketView marketView = new InternalMarketView(new int[] {1, 2}, new int[] {INSTRUMENT});
        private final ParentOrderRegistry registry = new ParentOrderRegistry(16, 16);
        private final OrderManagerImpl orderManager = new OrderManagerImpl();
        private final ReplayRisk risk = new ReplayRisk();
        private final UnsafeBuffer commandBuffer = new UnsafeBuffer(new byte[1024]);
        private final ParallelVenueExecution parallel = new ParallelVenueExecution(1L, 8);
        private final SmartOrderRoutingExecution sor = new SmartOrderRoutingExecution(1L, 0L, 8, zeroFees());
        private final PostOnlyQuoteExecution postOnly = new PostOnlyQuoteExecution();
        private final List<String> commands = new ArrayList<>();
        private long time = 1L;

        static V14ReplayResult apply() {
            final V14ReplayHarness harness = new V14ReplayHarness();
            harness.init();
            harness.run();
            return harness.result();
        }

        private void init() {
            final ExecutionStrategyContext ctx = new ExecutionStrategyContext(
                marketView,
                risk,
                orderManager,
                registry,
                commandBuffer,
                new MessageHeaderEncoder(),
                new NewOrderCommandEncoder(),
                new CancelOrderCommandEncoder(),
                () -> time,
                (correlationId, deadlineClusterMicros) -> true,
                new ReplayIds(),
                new CountersManager(new UnsafeBuffer(new byte[1024 * 1024]), new UnsafeBuffer(new byte[64 * 1024])));
            parallel.init(ctx);
            sor.init(ctx);
            postOnly.init(ctx);
        }

        private void run() {
            marketView.apply(marketData(Ids.VENUE_COINBASE, EntryType.ASK, 100L, 60L), ++time);
            marketView.apply(marketData(Ids.VENUE_BINANCE, EntryType.ASK, 101L, 40L), ++time);
            parallel.onParentIntent(hedgeIntent(9_000L, 90_000L, 100L, 101L, ExecutionStrategyIds.PARALLEL_VENUE));
            commands.add("PAR_CHILD:" + orderManager.getOrder(90_001L).clOrdId() + ":"
                + orderManager.getOrder(90_001L).venueId() + ":" + orderManager.getOrder(90_001L).qtyScaled());
            commands.add("PAR_CHILD:" + orderManager.getOrder(90_002L).clOrdId() + ":"
                + orderManager.getOrder(90_002L).venueId() + ":" + orderManager.getOrder(90_002L).qtyScaled());

            marketView.apply(marketData(Ids.VENUE_COINBASE, EntryType.ASK, 100L, 100L), ++time);
            marketView.apply(marketData(Ids.VENUE_BINANCE, EntryType.ASK, 105L, 100L), ++time);
            sor.onParentIntent(hedgeIntent(9_100L, 91_000L, 100L, 110L, ExecutionStrategyIds.SMART_ORDER_ROUTING));
            commands.add("SOR_INITIAL:" + orderManager.getOrder(91_001L).clOrdId() + ":"
                + orderManager.getOrder(91_001L).venueId());
            marketView.apply(marketData(Ids.VENUE_BINANCE, EntryType.ASK, 99L, 100L), ++time);
            sor.onMarketDataTick(Ids.VENUE_BINANCE, INSTRUMENT, time);
            commands.add("SOR_RESLICE:" + orderManager.getOrder(91_101L).clOrdId() + ":"
                + orderManager.getOrder(91_101L).venueId());

            postOnly.onParentIntent(quoteIntent(9_200L, 92_000L, Ids.VENUE_COINBASE, 99L));
            postOnly.onParentIntent(quoteIntent(9_300L, 93_000L, Ids.VENUE_BINANCE, 98L));
            commands.add("MM_CHILDREN:" + orderManager.getOrder(92_000L).clOrdId()
                + "/" + orderManager.getOrder(93_000L).clOrdId());

            marketView.apply(marketData(Ids.VENUE_COINBASE, EntryType.BID, 200L, 7L), ++time);
            marketView.apply(marketData(Ids.VENUE_BINANCE, EntryType.BID, 201L, 11L), ++time);

            final ParentOrderRegistry.Snapshot snapshot = registry.newSnapshot();
            registry.snapshotInto(snapshot);
            final ParentOrderRegistry restored = new ParentOrderRegistry(16, 16);
            restored.loadFrom(snapshot);
            commands.add("SNAPSHOT_PARENT:" + restored.lookup(9_100L).parentOrderId());

            final ParentOrderRegistry small = new ParentOrderRegistry(1, 1);
            small.claim(1L, Ids.STRATEGY_INVENTORY_HEDGE, ExecutionStrategyIds.PARALLEL_VENUE, 1L, time);
            small.claim(2L, Ids.STRATEGY_INVENTORY_HEDGE, ExecutionStrategyIds.PARALLEL_VENUE, 1L, time);
            commands.add("CAPACITY_COUNTER:" + small.parentCapacityRejects());
        }

        private V14ReplayResult result() {
            return new V14ReplayResult(
                registry.lookup(9_000L).status(),
                registry.lookup(9_100L).status(),
                sor.resliceSuccesses(),
                registry.activeChildCount(9_100L),
                marketView.externalLiquidityView().externalSizeAt(Ids.VENUE_COINBASE, INSTRUMENT, EntryType.BID, 200L),
                marketView.externalLiquidityView().externalSizeAt(Ids.VENUE_BINANCE, INSTRUMENT, EntryType.BID, 201L),
                registry.parentCapacityRejects(),
                List.copyOf(commands));
        }
    }

    private static final class ParentReplayHarness {
        private final InternalMarketView marketView = new InternalMarketView();
        private final ParentOrderRegistry registry = new ParentOrderRegistry(8, 8);
        private final OrderManagerImpl orderManager = new OrderManagerImpl();
        private final ReplayRisk risk = new ReplayRisk();
        private final UnsafeBuffer commandBuffer = new UnsafeBuffer(new byte[1024]);
        private final List<String> commands = new ArrayList<>();
        private final PostOnlyQuoteExecution postOnly = new PostOnlyQuoteExecution();
        private final ImmediateLimitExecution immediate = new ImmediateLimitExecution();
        private final MultiLegContingentExecution multiLeg = new MultiLegContingentExecution();

        static ParentReplayResult apply() {
            final ParentReplayHarness harness = new ParentReplayHarness();
            harness.initStrategies();
            harness.runStream();
            return harness.result();
        }

        private void initStrategies() {
            final ExecutionStrategyContext ctx = new ExecutionStrategyContext(
                marketView,
                risk,
                orderManager,
                registry,
                commandBuffer,
                new MessageHeaderEncoder(),
                new NewOrderCommandEncoder(),
                new CancelOrderCommandEncoder(),
                () -> 1L,
                (correlationId, deadlineClusterMicros) -> true,
                new ReplayIds(),
                new CountersManager(new UnsafeBuffer(new byte[1024 * 1024]), new UnsafeBuffer(new byte[64 * 1024])));
            postOnly.init(ctx);
            immediate.init(ctx);
            multiLeg.init(ctx);
        }

        private void runStream() {
            postOnly.onParentIntent(parentIntent(4_001L, 5_001L, ParentIntentType.QUOTE, 2, 1_000L, 0, 0L));
            commands.add("POST_SUBMIT:" + orderManager.getOrder(5_001L).clOrdId());
            postOnly.onChildExecution(new ChildExecutionView().wrap(exec(5_001L, ExecType.FILL, 4L, 1L, 3L, false, "p-fill"), 4_001L));
            commands.add("POST_PARTIAL:" + registry.lookup(4_001L).remainingQtyScaled());
            postOnly.onChildExecution(new ChildExecutionView().wrap(exec(5_001L, ExecType.REJECTED, 0L, 0L, 3L, true, "p-rej"), 4_001L));
            commands.add("POST_REJECT_RETRY:" + orderManager.getOrder(5_002L).clOrdId());

            risk.rejectNonHedge = true;
            final ParentOrderIntentView immediateIntent = parentIntent(
                4_002L, 5_010L, ParentIntentType.IMMEDIATE_LIMIT, 1, 2_000L, 0, 0L, 17);
            commands.add("IMM_VENUE_SET:" + immediateIntent.venueSetId());
            immediate.onParentIntent(immediateIntent);
            commands.add("IMM_RISK_REJECT:" + registry.lookup(4_002L).terminalReasonCode());
            risk.rejectNonHedge = false;

            multiLeg.onParentIntent(parentIntent(6_001L, 7_000L, ParentIntentType.MULTI_LEG, 3, 3_000L, 2, 3_100L));
            multiLeg.onChildExecution(new ChildExecutionView().wrap(exec(7_001L, ExecType.FILL, 3_000L, 1L, 0L, true, "m-fill"), 6_001L));
            risk.rejectHedge = true;
            multiLeg.onTimer(multiLeg.timerCorrelationId());
            commands.add("MULTI_TIMER:" + 7_000L);
            commands.add("MULTI_HEDGE_FAILED:" + registry.lookup(6_001L).terminalReasonCode());

            final ParentOrderRegistry.Snapshot snapshot = registry.newSnapshot();
            registry.snapshotInto(snapshot);
            final ParentOrderRegistry restored = new ParentOrderRegistry(8, 8);
            restored.loadFrom(snapshot);
            commands.add("SNAPSHOT:" + restored.activeParents() + "/" + restored.activeChildLinks());

            final ParentOrderRegistry small = new ParentOrderRegistry(1, 1);
            small.claim(8_001L, Ids.STRATEGY_MARKET_MAKING, 2, 1L, 1L);
            small.claim(8_002L, Ids.STRATEGY_MARKET_MAKING, 2, 1L, 1L);
            commands.add("CAPACITY:" + small.parentCapacityRejects());
        }

        private ParentReplayResult result() {
            return new ParentReplayResult(
                registry.lookup(4_001L).status(),
                registry.lookup(4_001L).filledQtyScaled(),
                registry.lookup(4_002L).status(),
                registry.lookup(6_001L).status(),
                registry.lookup(6_001L).terminalReasonCode(),
                multiLeg.hedgeRiskRejects(),
                registry.parentCapacityRejects(),
                List.copyOf(commands));
        }
    }

    private static ParentOrderIntentView parentIntent(
        final long parentOrderId,
        final long correlationId,
        final ParentIntentType intentType,
        final int executionStrategyId,
        final long price,
        final int secondaryVenueId,
        final long leg2Price
    ) {
        return parentIntent(parentOrderId, correlationId, intentType, executionStrategyId, price, secondaryVenueId, leg2Price, 0);
    }

    private static ParentOrderIntentView parentIntent(
        final long parentOrderId,
        final long correlationId,
        final ParentIntentType intentType,
        final int executionStrategyId,
        final long price,
        final int secondaryVenueId,
        final long leg2Price,
        final int venueSetId
    ) {
        final UnsafeBuffer buffer = new UnsafeBuffer(new byte[256]);
        new ParentOrderIntentEncoder()
            .wrapAndApplyHeader(buffer, 0, new MessageHeaderEncoder())
            .parentOrderId(parentOrderId)
            .strategyId((short) (intentType == ParentIntentType.MULTI_LEG ? Ids.STRATEGY_ARB : Ids.STRATEGY_MARKET_MAKING))
            .executionStrategyId(executionStrategyId)
            .intentType(intentType)
            .side(Side.BUY)
            .instrumentId(INSTRUMENT)
            .primaryVenueId(VENUE)
            .secondaryVenueId(secondaryVenueId)
            .quantityScaled(1L)
            .priceMode(PriceMode.LIMIT)
            .limitPriceScaled(price)
            .referencePriceScaled(0L)
            .timeInForcePreference(intentType == ParentIntentType.QUOTE ? TimeInForce.GTC : TimeInForce.IOC)
            .urgencyHint((byte) 1)
            .postOnlyPreference(intentType == ParentIntentType.QUOTE ? BooleanType.TRUE : BooleanType.FALSE)
            .selfTradePolicy((byte) 0)
            .correlationId(correlationId)
            .legCount((byte) (intentType == ParentIntentType.MULTI_LEG ? 2 : 1))
            .leg2Side(Side.SELL)
            .leg2LimitPriceScaled(leg2Price)
            .parentTimeoutMicros(1_000L)
            .venueSetId(venueSetId);
        final ParentOrderIntentDecoder decoder = new ParentOrderIntentDecoder();
        decoder.wrapAndApplyHeader(buffer, 0, new MessageHeaderDecoder());
        return new ParentOrderIntentView().wrap(decoder);
    }

    private static ParentOrderIntentView hedgeIntent(
        final long parentOrderId,
        final long correlationId,
        final long qty,
        final long limit,
        final int executionStrategyId
    ) {
        final UnsafeBuffer buffer = new UnsafeBuffer(new byte[256]);
        new ParentOrderIntentEncoder()
            .wrapAndApplyHeader(buffer, 0, new MessageHeaderEncoder())
            .parentOrderId(parentOrderId)
            .strategyId((short) Ids.STRATEGY_INVENTORY_HEDGE)
            .executionStrategyId(executionStrategyId)
            .intentType(ParentIntentType.HEDGE)
            .side(Side.BUY)
            .instrumentId(INSTRUMENT)
            .primaryVenueId(VENUE)
            .secondaryVenueId(0)
            .quantityScaled(qty)
            .priceMode(PriceMode.LIMIT)
            .limitPriceScaled(limit)
            .referencePriceScaled(limit)
            .timeInForcePreference(TimeInForce.IOC)
            .urgencyHint((byte) 3)
            .postOnlyPreference(BooleanType.FALSE)
            .selfTradePolicy((byte) 1)
            .correlationId(correlationId)
            .legCount((byte) 1)
            .leg2Side(Side.NULL_VAL)
            .leg2LimitPriceScaled(0L)
            .parentTimeoutMicros(1_000L)
            .venueSetId(7);
        final ParentOrderIntentDecoder decoder = new ParentOrderIntentDecoder();
        decoder.wrapAndApplyHeader(buffer, 0, new MessageHeaderDecoder());
        return new ParentOrderIntentView().wrap(decoder);
    }

    private static ParentOrderIntentView quoteIntent(
        final long parentOrderId,
        final long correlationId,
        final int venueId,
        final long price
    ) {
        final UnsafeBuffer buffer = new UnsafeBuffer(new byte[256]);
        new ParentOrderIntentEncoder()
            .wrapAndApplyHeader(buffer, 0, new MessageHeaderEncoder())
            .parentOrderId(parentOrderId)
            .strategyId((short) Ids.STRATEGY_MARKET_MAKING)
            .executionStrategyId(ExecutionStrategyIds.POST_ONLY_QUOTE)
            .intentType(ParentIntentType.QUOTE)
            .side(Side.BUY)
            .instrumentId(INSTRUMENT)
            .primaryVenueId(venueId)
            .secondaryVenueId(0)
            .quantityScaled(1L)
            .priceMode(PriceMode.LIMIT)
            .limitPriceScaled(price)
            .referencePriceScaled(price)
            .timeInForcePreference(TimeInForce.GTC)
            .urgencyHint((byte) 1)
            .postOnlyPreference(BooleanType.TRUE)
            .selfTradePolicy((byte) 1)
            .correlationId(correlationId)
            .legCount((byte) 1)
            .leg2Side(Side.NULL_VAL)
            .leg2LimitPriceScaled(0L)
            .parentTimeoutMicros(1_000L)
            .venueSetId(0);
        final ParentOrderIntentDecoder decoder = new ParentOrderIntentDecoder();
        decoder.wrapAndApplyHeader(buffer, 0, new MessageHeaderDecoder());
        return new ParentOrderIntentView().wrap(decoder);
    }

    private static FeeSchedule zeroFees() {
        final FeeSchedule fees = new FeeSchedule();
        fees.set(Ids.VENUE_COINBASE, 0L, 0L);
        fees.set(Ids.VENUE_BINANCE, 0L, 0L);
        return fees;
    }

    private static MarketDataEventDecoder marketData(
        final int venueId,
        final EntryType side,
        final long price,
        final long size) {
        final UnsafeBuffer buffer = new UnsafeBuffer(new byte[128]);
        new MarketDataEventEncoder()
            .wrapAndApplyHeader(buffer, 0, new MessageHeaderEncoder())
            .venueId(venueId)
            .instrumentId(INSTRUMENT)
            .entryType(side)
            .updateAction(size == 0L ? UpdateAction.DELETE : UpdateAction.NEW)
            .priceScaled(price)
            .sizeScaled(size)
            .priceLevel(0)
            .ingressTimestampNanos(1L)
            .exchangeTimestampNanos(1L)
            .fixSeqNum(1);
        final MarketDataEventDecoder decoder = new MarketDataEventDecoder();
        decoder.wrapAndApplyHeader(buffer, 0, new MessageHeaderDecoder());
        return decoder;
    }

    private static ExecutionEventDecoder exec(
        final long clOrdId,
        final ExecType execType,
        final long price,
        final long fillQty,
        final long leavesQty,
        final boolean isFinal,
        final String execId
    ) {
        final UnsafeBuffer buffer = new UnsafeBuffer(new byte[256]);
        final byte[] execBytes = execId.getBytes(StandardCharsets.US_ASCII);
        new ExecutionEventEncoder()
            .wrapAndApplyHeader(buffer, 0, new MessageHeaderEncoder())
            .clOrdId(clOrdId)
            .venueId(VENUE)
            .instrumentId(INSTRUMENT)
            .execType(execType)
            .side(Side.BUY)
            .fillPriceScaled(price)
            .fillQtyScaled(fillQty)
            .cumQtyScaled(fillQty)
            .leavesQtyScaled(leavesQty)
            .rejectCode(0)
            .ingressTimestampNanos(1L)
            .exchangeTimestampNanos(1L)
            .fixSeqNum(1)
            .isFinal(isFinal ? BooleanType.TRUE : BooleanType.FALSE)
            .putVenueOrderId(new byte[0], 0, 0)
            .putExecId(execBytes, 0, execBytes.length);
        final ExecutionEventDecoder decoder = new ExecutionEventDecoder();
        decoder.wrapAndApplyHeader(buffer, 0, new MessageHeaderDecoder());
        return decoder;
    }

    private static final class ReplayRisk implements RiskEngine {
        boolean rejectNonHedge;
        boolean rejectHedge;
        boolean killSwitch;

        @Override
        public RiskDecision preTradeCheck(final int venueId, final int instrumentId, final byte side, final long priceScaled, final long qtyScaled, final int strategyId) {
            if (rejectHedge && strategyId == Ids.STRATEGY_ARB_HEDGE) {
                return RiskDecision.REJECT_MAX_NOTIONAL;
            }
            return rejectNonHedge ? RiskDecision.REJECT_MAX_NOTIONAL : RiskDecision.APPROVED;
        }

        @Override public void updatePositionSnapshot(final int venueId, final int instrumentId, final long netQtyScaled) { }
        @Override public void updateDailyPnl(final long realizedPnlDeltaScaled) { }
        @Override public void setRecoveryLock(final int venueId, final boolean locked) { }
        @Override public long getDailyPnlScaled() { return 0L; }
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

    private static final class ReplayIds implements IdRegistry {
        @Override public int venueId(final long sessionId) { return VENUE; }
        @Override public int instrumentId(final CharSequence symbol) { return INSTRUMENT; }
        @Override public String symbolOf(final int instrumentId) { return "BTC-USD"; }
        @Override public String venueNameOf(final int venueId) { return "coinbase"; }
        @Override public void registerSession(final int venueId, final long sessionId) { }
    }

    private static final class ReplayHarness {
        private final InternalMarketView marketView = new InternalMarketView();
        private final VenueL3Book l3Book = new VenueL3Book(VENUE, INSTRUMENT, 4);
        private final OwnOrderOverlay capacityOverlay = new OwnOrderOverlay(1);
        private OrderManagerImpl orderManager = new OrderManagerImpl();
        private final RiskEngineImpl riskEngine = new RiskEngineImpl(riskConfig(), RiskEventSink.NO_OP, () -> { }, () -> 1_000_000L);
        private final List<String> commands = new ArrayList<>();
        private long timerCount;
        private long capacityDropCount;
        private long duplicateReportCount;
        private byte lastRiskRejectCode;

        static ReplayResult apply(final List<ReplayEvent> stream) {
            final ReplayHarness harness = new ReplayHarness();
            harness.riskEngine.setVenueConnected(VENUE, true);
            for (ReplayEvent event : stream) {
                harness.apply(event);
            }
            final OrderState order = harness.orderManager.getOrder(CL_ORD_ID);
            return new ReplayResult(
                harness.marketView.getBestBid(VENUE, INSTRUMENT),
                harness.marketView.getBestAsk(VENUE, INSTRUMENT),
                harness.marketView.consolidatedBestBid(INSTRUMENT),
                harness.l3Book.levelSize(Side.SELL, 101L),
                harness.marketView.externalLiquidityView().externalSizeAt(
                    VENUE, INSTRUMENT, EntryType.BID, 100L),
                harness.marketView.ownOrderOverlay().orderCount(),
                harness.l3Book.activeOrderCount(),
                harness.orderManager.liveOrderCount(),
                order == null ? (byte)-1 : order.status(),
                harness.orderManager.invalidTransitionCount(),
                harness.duplicateReportCount,
                harness.lastRiskRejectCode,
                harness.timerCount,
                harness.capacityDropCount,
                List.copyOf(harness.commands));
        }

        private void apply(final ReplayEvent event) {
            switch (event.type) {
                case MARKET -> {
                    if (event.venueId <= 0) {
                        throw new IllegalArgumentException("venue must be positive");
                    }
                    marketView.apply(market(event.venueId, event.side, event.price, event.size), commands.size() + 1L);
                }
                case MARKET_L3 -> l3Book.apply(l3(event), marketView.book(VENUE, INSTRUMENT), commands.size() + 1L);
                case CREATE_ORDER -> orderManager.createPendingOrder(
                    event.clOrdId,
                    VENUE,
                    INSTRUMENT,
                    Side.BUY.value(),
                    OrdType.LIMIT.value(),
                    TimeInForce.GTC.value(),
                    event.price,
                    event.size,
                    Ids.STRATEGY_MARKET_MAKING);
                case EXECUTION_REPORT -> {
                    final long duplicatesBefore = orderManager.duplicateExecutionReportCount();
                    orderManager.onExecution(execution(event));
                    duplicateReportCount += orderManager.duplicateExecutionReportCount() - duplicatesBefore;
                    final OrderState order = orderManager.getOrder(event.clOrdId);
                    if (order != null) {
                        marketView.ownOrderOverlay().updateFrom(order);
                    }
                }
                case STRATEGY_TICK -> commands.add("ARB_CHECK:" + marketView.getBestBid(1, Ids.INSTRUMENT_BTC_USD));
                case RISK_DECISION -> {
                    final RiskDecision decision = riskEngine.preTradeCheck(
                        VENUE,
                        INSTRUMENT,
                        Side.BUY.value(),
                        100L,
                        event.approved ? 1L : 1_000_000_000_000L,
                        Ids.STRATEGY_MARKET_MAKING);
                    lastRiskRejectCode = decision.rejectCode();
                    commands.add(decision.approved() ? "RISK_APPROVED" : "RISK_REJECTED:" + decision.rejectCode());
                }
                case STALE_MARKET_DATA -> commands.add("STALE_MARKET_DATA");
                case TIMER -> {
                    timerCount++;
                    commands.add("TIMER:" + event.timerCorrelationId);
                }
                case SNAPSHOT_LOAD -> {
                    final OrderManagerImpl restored = new OrderManagerImpl();
                    restored.loadSnapshotFragments(orderManager.snapshotFragments());
                    orderManager = restored;
                    final OrderState order = orderManager.getOrder(CL_ORD_ID);
                    if (order != null) {
                        marketView.ownOrderOverlay().updateFrom(order);
                    }
                }
                case CAPACITY_FULL -> {
                    capacityOverlay.upsert(1L, "CAP-1", VENUE, INSTRUMENT, EntryType.BID, 1L, 1L, true);
                    final int before = capacityOverlay.orderCount();
                    capacityOverlay.upsert(2L, "CAP-2", VENUE, INSTRUMENT, EntryType.BID, 1L, 1L, true);
                    if (capacityOverlay.orderCount() == before) {
                        capacityDropCount++;
                    }
                }
            }
        }

        private static MarketDataEventDecoder market(
            final int venueId,
            final EntryType side,
            final long price,
            final long size) {

            final UnsafeBuffer buffer = new UnsafeBuffer(new byte[128]);
            new MarketDataEventEncoder()
                .wrapAndApplyHeader(buffer, 0, new MessageHeaderEncoder())
                .venueId(venueId)
                .instrumentId(Ids.INSTRUMENT_BTC_USD)
                .entryType(side)
                .updateAction(size == 0L ? UpdateAction.DELETE : UpdateAction.NEW)
                .priceScaled(price)
                .sizeScaled(size)
                .priceLevel(0)
                .ingressTimestampNanos(1L)
                .exchangeTimestampNanos(1L)
                .fixSeqNum(1);
            final MarketDataEventDecoder decoder = new MarketDataEventDecoder();
            decoder.wrap(buffer, MessageHeaderEncoder.ENCODED_LENGTH,
                MarketDataEventEncoder.BLOCK_LENGTH, MarketDataEventEncoder.SCHEMA_VERSION);
            return decoder;
        }

        private static MarketByOrderEventDecoder l3(final ReplayEvent event) {
            final UnsafeBuffer buffer = new UnsafeBuffer(new byte[256]);
            final byte[] venueOrderId = event.venueOrderId.getBytes(StandardCharsets.US_ASCII);
            new MarketByOrderEventEncoder()
                .wrapAndApplyHeader(buffer, 0, new MessageHeaderEncoder())
                .venueId(VENUE)
                .instrumentId(INSTRUMENT)
                .side(event.orderSide)
                .updateAction(event.updateAction)
                .priceScaled(event.price)
                .sizeScaled(event.size)
                .ingressTimestampNanos(1L)
                .exchangeTimestampNanos(1L)
                .fixSeqNum(1)
                .putVenueOrderId(venueOrderId, 0, venueOrderId.length);
            final MarketByOrderEventDecoder decoder = new MarketByOrderEventDecoder();
            decoder.wrapAndApplyHeader(buffer, 0, new MessageHeaderDecoder());
            return decoder;
        }

        private static ExecutionEventDecoder execution(final ReplayEvent event) {
            final UnsafeBuffer buffer = new UnsafeBuffer(new byte[512]);
            final byte[] venueOrderId = event.venueOrderId.getBytes(StandardCharsets.US_ASCII);
            final byte[] execId = event.execId.getBytes(StandardCharsets.US_ASCII);
            new ExecutionEventEncoder()
                .wrapAndApplyHeader(buffer, 0, new MessageHeaderEncoder())
                .clOrdId(event.clOrdId)
                .venueId(VENUE)
                .instrumentId(INSTRUMENT)
                .execType(event.execType)
                .side(event.orderSide)
                .fillPriceScaled(event.price)
                .fillQtyScaled(event.fillQty)
                .cumQtyScaled(5L - event.leavesQty)
                .leavesQtyScaled(event.leavesQty)
                .rejectCode(0)
                .ingressTimestampNanos(1L)
                .exchangeTimestampNanos(1L)
                .fixSeqNum(1)
                .isFinal(event.timerCorrelationId == 1L ? BooleanType.TRUE : BooleanType.FALSE)
                .putVenueOrderId(venueOrderId, 0, venueOrderId.length)
                .putExecId(execId, 0, execId.length);
            final ExecutionEventDecoder decoder = new ExecutionEventDecoder();
            decoder.wrapAndApplyHeader(buffer, 0, new MessageHeaderDecoder());
            return decoder;
        }

        private static RiskConfig riskConfig() {
            return new RiskConfig(
                100,
                1_000_000_000_000L,
                Map.of(INSTRUMENT, new RiskConfig.InstrumentRisk(
                    INSTRUMENT,
                    10L,
                    10_000_000_000L,
                    10_000_000_000L,
                    10_000_000_000L,
                    80)));
        }
    }
}
