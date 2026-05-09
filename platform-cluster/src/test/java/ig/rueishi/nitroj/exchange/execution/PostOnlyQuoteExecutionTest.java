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
import ig.rueishi.nitroj.exchange.messages.NewOrderCommandDecoder;
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

final class PostOnlyQuoteExecutionTest {
    private static final long PARENT_ID = 19_001L;
    private static final long CHILD_ID = 20_001L;
    private static final long PRICE = 65_000L * Ids.SCALE;
    private static final long QTY = Ids.SCALE;

    @Test
    void submit_createsWorkingPostOnlyQuoteChild() {
        final Harness harness = submittedHarness(RiskDecision.APPROVED, 4, 4);

        assertThat(harness.registry.lookup(PARENT_ID).status()).isEqualTo(ParentOrderState.WORKING);
        assertThat(harness.registry.parentOrderIdByChild(CHILD_ID)).isEqualTo(PARENT_ID);
        assertThat(harness.orderManager.getOrder(CHILD_ID).parentOrderId()).isEqualTo(PARENT_ID);
        assertThat(newOrder(harness.commandBuffer).timeInForce()).isEqualTo(TimeInForce.GTC);
    }

    @Test
    void refreshTrigger_movesChildToPendingCancel() {
        final Harness harness = submittedHarness(RiskDecision.APPROVED, 4, 4);

        harness.strategy.onMarketDataTick(Ids.VENUE_COINBASE, Ids.INSTRUMENT_BTC_USD, 100L);

        assertThat(harness.strategy.refreshTriggers()).isEqualTo(1L);
        assertThat(harness.registry.lookup(PARENT_ID).status()).isEqualTo(ParentOrderState.CANCEL_PENDING);
        assertThat(harness.orderManager.getStatus(CHILD_ID)).isEqualTo(OrderStatus.PENDING_CANCEL);
    }

    @Test
    void cancelReplace_submitsReplacementAfterCancelAck() {
        final Harness harness = submittedHarness(RiskDecision.APPROVED, 4, 4);
        harness.strategy.onMarketDataTick(Ids.VENUE_COINBASE, Ids.INSTRUMENT_BTC_USD, 100L);

        harness.strategy.onChildExecution(child(event(CHILD_ID, ExecType.CANCELED, 0L, 0L, 0L, QTY, true, "cx-1"), PARENT_ID));

        assertThat(harness.registry.parentOrderIdByChild(CHILD_ID)).isZero();
        assertThat(harness.registry.parentOrderIdByChild(CHILD_ID + 1L)).isEqualTo(PARENT_ID);
        assertThat(harness.orderManager.getOrder(CHILD_ID + 1L)).isNotNull();
        assertThat(harness.strategy.cancelReplaceRequests()).isEqualTo(1L);
    }

    @Test
    void postOnlyRejectRetry_oneTickDeeper() {
        final Harness harness = submittedHarness(RiskDecision.APPROVED, 4, 4);

        harness.strategy.onChildExecution(child(event(CHILD_ID, ExecType.REJECTED, 0L, 0L, 0L, QTY, true, "rej-1"), PARENT_ID));

        assertThat(harness.strategy.retrySubmissions()).isEqualTo(1L);
        assertThat(harness.registry.parentOrderIdByChild(CHILD_ID + 1L)).isEqualTo(PARENT_ID);
        assertThat(newOrder(harness.commandBuffer).priceScaled()).isEqualTo(PRICE - 1L);
    }

    @Test
    void retryExhaustion_failsParent() {
        final Harness harness = submittedHarness(RiskDecision.APPROVED, 4, 4);
        harness.strategy.onChildExecution(child(event(CHILD_ID, ExecType.REJECTED, 0L, 0L, 0L, QTY, true, "rej-1"), PARENT_ID));

        harness.strategy.onChildExecution(child(event(CHILD_ID + 1L, ExecType.REJECTED, 0L, 0L, 0L, QTY, true, "rej-2"), PARENT_ID));

        assertThat(harness.registry.lookup(PARENT_ID).status()).isEqualTo(ParentOrderState.FAILED);
        assertThat(harness.registry.lookup(PARENT_ID).terminalReasonCode()).isEqualTo(ParentOrderState.REASON_CHILD_REJECTED);
        assertThat(harness.strategy.retryExhaustions()).isEqualTo(1L);
    }

    @Test
    void fillCallback_updatesParentFill() {
        final Harness harness = submittedHarness(RiskDecision.APPROVED, 4, 4);

        harness.strategy.onChildExecution(child(fill(CHILD_ID, QTY / 2, QTY / 2, QTY / 2, false, "fill-1"), PARENT_ID));

        assertThat(harness.registry.lookup(PARENT_ID).status()).isEqualTo(ParentOrderState.PARTIALLY_FILLED);
        assertThat(harness.registry.lookup(PARENT_ID).filledQtyScaled()).isEqualTo(QTY / 2);
        assertThat(harness.strategy.fills()).isEqualTo(1L);
    }

    @Test
    void parentCancel_cancelsWorkingChildAndTerminalCancelAckCancelsParent() {
        final Harness harness = submittedHarness(RiskDecision.APPROVED, 4, 4);

        harness.strategy.onCancel(PARENT_ID, ParentOrderState.REASON_CANCELED_BY_PARENT);
        harness.strategy.onChildExecution(child(event(CHILD_ID, ExecType.CANCELED, 0L, 0L, 0L, QTY, true, "cx-1"), PARENT_ID));

        assertThat(harness.orderManager.getStatus(CHILD_ID)).isEqualTo(OrderStatus.PENDING_CANCEL);
        assertThat(harness.registry.lookup(PARENT_ID).status()).isEqualTo(ParentOrderState.CANCELED);
        assertThat(harness.strategy.parentCancels()).isEqualTo(1L);
    }

    @Test
    void staleMarketDataPath_ignoresDifferentInstrumentTick() {
        final Harness harness = submittedHarness(RiskDecision.APPROVED, 4, 4);

        harness.strategy.onMarketDataTick(Ids.VENUE_COINBASE, Ids.INSTRUMENT_ETH_USD, 100L);

        assertThat(harness.strategy.refreshTriggers()).isZero();
        assertThat(harness.registry.lookup(PARENT_ID).status()).isEqualTo(ParentOrderState.WORKING);
    }

    @Test
    void timerDuringPendingExecutionReport_isNoop() {
        final Harness harness = submittedHarness(RiskDecision.APPROVED, 4, 4);
        harness.strategy.onMarketDataTick(Ids.VENUE_COINBASE, Ids.INSTRUMENT_BTC_USD, 100L);

        harness.strategy.onTimer(44L);

        assertThat(harness.strategy.timerCallbacks()).isEqualTo(1L);
        assertThat(harness.registry.lookup(PARENT_ID).status()).isEqualTo(ParentOrderState.CANCEL_PENDING);
    }

    @Test
    void riskReject_failsBeforeChildCommand() {
        final Harness harness = submittedHarness(RiskDecision.REJECT_MAX_NOTIONAL, 4, 4);

        assertThat(harness.registry.lookup(PARENT_ID).status()).isEqualTo(ParentOrderState.FAILED);
        assertThat(harness.registry.lookup(PARENT_ID).terminalReasonCode()).isEqualTo(ParentOrderState.REASON_RISK_REJECTED);
        assertThat(harness.orderManager.getOrder(CHILD_ID)).isNull();
        assertThat(harness.strategy.riskRejects()).isEqualTo(1L);
    }

    @Test
    void capacityFull_rejectsSecondParent() {
        final Harness harness = submittedHarness(RiskDecision.APPROVED, 1, 2);

        harness.strategy.onParentIntent(intent(PARENT_ID + 1L, CHILD_ID + 10L, PRICE));

        assertThat(harness.registry.lookup(PARENT_ID + 1L)).isNull();
        assertThat(harness.strategy.capacityRejects()).isEqualTo(1L);
    }

    @Test
    void twoQuoteParentsFromOneMarketMaker_remainIndependentlyActive() {
        final Harness harness = new Harness(RiskDecision.APPROVED, 8, 8);

        harness.strategy.onParentIntent(intent(PARENT_ID, CHILD_ID, PRICE, Ids.VENUE_COINBASE,
            Ids.INSTRUMENT_BTC_USD, Side.BUY));
        harness.strategy.onParentIntent(intent(PARENT_ID + 1L, CHILD_ID + 1L, PRICE + 10L, Ids.VENUE_COINBASE,
            Ids.INSTRUMENT_BTC_USD, Side.SELL));

        assertThat(harness.registry.parentOrderIdByChild(CHILD_ID)).isEqualTo(PARENT_ID);
        assertThat(harness.registry.parentOrderIdByChild(CHILD_ID + 1L)).isEqualTo(PARENT_ID + 1L);
        assertThat(harness.registry.lookup(PARENT_ID).status()).isEqualTo(ParentOrderState.WORKING);
        assertThat(harness.registry.lookup(PARENT_ID + 1L).status()).isEqualTo(ParentOrderState.WORKING);
    }

    @Test
    void fourQuoteParentsAcrossCoinbaseAndBinance_refreshOnlyMatchingVenue() {
        final Harness harness = new Harness(RiskDecision.APPROVED, 8, 8);
        submitFourVenueQuotes(harness);

        harness.strategy.onMarketDataTick(Ids.VENUE_COINBASE, Ids.INSTRUMENT_BTC_USD, 100L);

        assertThat(harness.strategy.refreshTriggers()).isEqualTo(2L);
        assertThat(harness.registry.lookup(PARENT_ID).status()).isEqualTo(ParentOrderState.CANCEL_PENDING);
        assertThat(harness.registry.lookup(PARENT_ID + 1L).status()).isEqualTo(ParentOrderState.CANCEL_PENDING);
        assertThat(harness.registry.lookup(PARENT_ID + 2L).status()).isEqualTo(ParentOrderState.WORKING);
        assertThat(harness.registry.lookup(PARENT_ID + 3L).status()).isEqualTo(ParentOrderState.WORKING);
        assertThat(harness.orderManager.getStatus(CHILD_ID + 20L)).isEqualTo(OrderStatus.PENDING_NEW);
        assertThat(harness.orderManager.getStatus(CHILD_ID + 30L)).isEqualTo(OrderStatus.PENDING_NEW);
    }

    @Test
    void parentCancel_isScopedToOneParent() {
        final Harness harness = new Harness(RiskDecision.APPROVED, 8, 8);
        submitFourVenueQuotes(harness);

        harness.strategy.onCancel(PARENT_ID + 2L, ParentOrderState.REASON_CANCELED_BY_PARENT);
        harness.strategy.onChildExecution(child(event(CHILD_ID + 20L, ExecType.CANCELED, 0L, 0L, 0L, QTY,
            true, "bn-cx-1", Ids.VENUE_BINANCE, Ids.INSTRUMENT_BTC_USDT, Side.BUY), PARENT_ID + 2L));

        assertThat(harness.registry.lookup(PARENT_ID + 2L).status()).isEqualTo(ParentOrderState.CANCELED);
        assertThat(harness.registry.lookup(PARENT_ID).status()).isEqualTo(ParentOrderState.WORKING);
        assertThat(harness.registry.lookup(PARENT_ID + 1L).status()).isEqualTo(ParentOrderState.WORKING);
        assertThat(harness.registry.lookup(PARENT_ID + 3L).status()).isEqualTo(ParentOrderState.WORKING);
        assertThat(harness.strategy.parentCancels()).isEqualTo(1L);
    }

    @Test
    void postOnlyRejectRetry_isScopedToOneParent() {
        final Harness harness = new Harness(RiskDecision.APPROVED, 8, 8);
        submitFourVenueQuotes(harness);

        harness.strategy.onChildExecution(child(event(CHILD_ID + 20L, ExecType.REJECTED, 0L, 0L, 0L, QTY,
            true, "bn-rej-1", Ids.VENUE_BINANCE, Ids.INSTRUMENT_BTC_USDT, Side.BUY), PARENT_ID + 2L));

        assertThat(harness.strategy.retrySubmissions()).isEqualTo(1L);
        assertThat(harness.registry.parentOrderIdByChild(CHILD_ID)).isEqualTo(PARENT_ID);
        assertThat(harness.registry.parentOrderIdByChild(CHILD_ID + 30L)).isEqualTo(PARENT_ID + 3L);
        assertThat(harness.registry.parentOrderIdByChild(CHILD_ID + 21L)).isEqualTo(PARENT_ID + 2L);
        assertThat(newOrder(harness.commandBuffer).venueId()).isEqualTo(Ids.VENUE_BINANCE);
        assertThat(newOrder(harness.commandBuffer).instrumentId()).isEqualTo(Ids.INSTRUMENT_BTC_USDT);
    }

    @Test
    void finalFill_releasesOnlyFilledParent() {
        final Harness harness = new Harness(RiskDecision.APPROVED, 8, 8);
        submitFourVenueQuotes(harness);

        harness.strategy.onChildExecution(child(event(CHILD_ID, ExecType.FILL, PRICE, QTY, QTY, 0L,
            true, "cb-fill-final", Ids.VENUE_COINBASE, Ids.INSTRUMENT_BTC_USD, Side.BUY), PARENT_ID));

        assertThat(harness.registry.lookup(PARENT_ID).status()).isEqualTo(ParentOrderState.DONE);
        assertThat(harness.registry.lookup(PARENT_ID + 1L).status()).isEqualTo(ParentOrderState.WORKING);
        assertThat(harness.registry.lookup(PARENT_ID + 2L).status()).isEqualTo(ParentOrderState.WORKING);
        assertThat(harness.strategy.fills()).isEqualTo(1L);

        harness.strategy.onParentIntent(intent(PARENT_ID + 4L, CHILD_ID + 40L, PRICE, Ids.VENUE_COINBASE,
            Ids.INSTRUMENT_BTC_USD, Side.BUY));
        assertThat(harness.registry.lookup(PARENT_ID + 4L).status()).isEqualTo(ParentOrderState.WORKING);
    }

    @Test
    void missingParentCallbacks_areDroppedAndCounted() {
        final Harness harness = submittedHarness(RiskDecision.APPROVED, 4, 4);

        harness.strategy.onChildExecution(child(event(CHILD_ID + 99L, ExecType.NEW, 0L, 0L, 0L, QTY,
            false, "missing-new"), PARENT_ID + 99L));
        harness.strategy.onCancel(PARENT_ID + 99L, ParentOrderState.REASON_CANCELED_BY_PARENT);

        assertThat(harness.strategy.missingCallbackDrops()).isEqualTo(2L);
        assertThat(harness.registry.lookup(PARENT_ID).status()).isEqualTo(ParentOrderState.WORKING);
    }

    @Test
    void strategySlotCapacityFull_failsSecondParentDeterministically() {
        final Harness harness = new Harness(RiskDecision.APPROVED, 4, 4, 1);

        harness.strategy.onParentIntent(intent(PARENT_ID, CHILD_ID, PRICE));
        harness.strategy.onParentIntent(intent(PARENT_ID + 1L, CHILD_ID + 1L, PRICE));

        assertThat(harness.registry.lookup(PARENT_ID).status()).isEqualTo(ParentOrderState.WORKING);
        assertThat(harness.registry.lookup(PARENT_ID + 1L).status()).isEqualTo(ParentOrderState.FAILED);
        assertThat(harness.registry.lookup(PARENT_ID + 1L).terminalReasonCode())
            .isEqualTo(ParentOrderState.REASON_CAPACITY_REJECTED);
        assertThat(harness.strategy.capacityRejects()).isEqualTo(1L);
    }

    @Test
    void replay_sameRefreshSequenceMatchesState() {
        final Harness first = submittedHarness(RiskDecision.APPROVED, 4, 4);
        final Harness second = submittedHarness(RiskDecision.APPROVED, 4, 4);

        first.strategy.onMarketDataTick(Ids.VENUE_COINBASE, Ids.INSTRUMENT_BTC_USD, 100L);
        second.strategy.onMarketDataTick(Ids.VENUE_COINBASE, Ids.INSTRUMENT_BTC_USD, 100L);

        assertThat(second.registry.lookup(PARENT_ID).status()).isEqualTo(first.registry.lookup(PARENT_ID).status());
        assertThat(second.registry.activeChildLinks()).isEqualTo(first.registry.activeChildLinks());
    }

    @Test
    void snapshotLoad_restoresWorkingQuoteParent() {
        final Harness harness = submittedHarness(RiskDecision.APPROVED, 4, 4);
        final ParentOrderRegistry.Snapshot snapshot = harness.registry.newSnapshot();
        harness.registry.snapshotInto(snapshot);

        final ParentOrderRegistry restored = new ParentOrderRegistry(4, 4);
        restored.loadFrom(snapshot);

        assertThat(restored.lookup(PARENT_ID).status()).isEqualTo(ParentOrderState.WORKING);
        assertThat(restored.parentOrderIdByChild(CHILD_ID)).isEqualTo(PARENT_ID);
    }

    private static Harness submittedHarness(final RiskDecision riskDecision, final int parentCapacity, final int childCapacity) {
        final Harness harness = new Harness(riskDecision, parentCapacity, childCapacity);
        harness.strategy.onParentIntent(intent(PARENT_ID, CHILD_ID, PRICE));
        return harness;
    }

    private static NewOrderCommandDecoder newOrder(final UnsafeBuffer buffer) {
        final NewOrderCommandDecoder decoder = new NewOrderCommandDecoder();
        decoder.wrapAndApplyHeader(buffer, 0, new MessageHeaderDecoder());
        return decoder;
    }

    private static ParentOrderIntentView intent(final long parentOrderId, final long childOrderId, final long price) {
        return intent(parentOrderId, childOrderId, price, Ids.VENUE_COINBASE, Ids.INSTRUMENT_BTC_USD, Side.BUY);
    }

    private static ParentOrderIntentView intent(
        final long parentOrderId,
        final long childOrderId,
        final long price,
        final int venueId,
        final int instrumentId,
        final Side side
    ) {
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
            .quantityScaled(QTY)
            .priceMode(PriceMode.LIMIT)
            .limitPriceScaled(price)
            .referencePriceScaled(0L)
            .timeInForcePreference(TimeInForce.GTC)
            .urgencyHint((byte) 1)
            .postOnlyPreference(BooleanType.TRUE)
            .selfTradePolicy((byte) 0)
            .correlationId(childOrderId)
            .legCount((byte) 1)
            .leg2Side(Side.SELL)
            .leg2LimitPriceScaled(0L)
            .parentTimeoutMicros(0L);
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
        return event(childClOrdId, ExecType.FILL, PRICE, fillQty, cumQty, leavesQty, isFinal, execId);
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
        return event(childClOrdId, execType, fillPrice, fillQty, cumQty, leavesQty, isFinal, execId,
            Ids.VENUE_COINBASE, Ids.INSTRUMENT_BTC_USD, Side.BUY);
    }

    private static ExecutionEventDecoder event(
        final long childClOrdId,
        final ExecType execType,
        final long fillPrice,
        final long fillQty,
        final long cumQty,
        final long leavesQty,
        final boolean isFinal,
        final String execId,
        final int venueId,
        final int instrumentId,
        final Side side
    ) {
        final UnsafeBuffer buffer = new UnsafeBuffer(new byte[256]);
        final byte[] venueOrderId = ("venue-" + childClOrdId).getBytes(StandardCharsets.US_ASCII);
        final byte[] execIdBytes = execId.getBytes(StandardCharsets.US_ASCII);
        new ExecutionEventEncoder()
            .wrapAndApplyHeader(buffer, 0, new MessageHeaderEncoder())
            .clOrdId(childClOrdId)
            .venueId(venueId)
            .instrumentId(instrumentId)
            .execType(execType)
            .side(side)
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

    private static void submitFourVenueQuotes(final Harness harness) {
        harness.strategy.onParentIntent(intent(PARENT_ID, CHILD_ID, PRICE, Ids.VENUE_COINBASE,
            Ids.INSTRUMENT_BTC_USD, Side.BUY));
        harness.strategy.onParentIntent(intent(PARENT_ID + 1L, CHILD_ID + 10L, PRICE + 10L, Ids.VENUE_COINBASE,
            Ids.INSTRUMENT_BTC_USD, Side.SELL));
        harness.strategy.onParentIntent(intent(PARENT_ID + 2L, CHILD_ID + 20L, PRICE, Ids.VENUE_BINANCE,
            Ids.INSTRUMENT_BTC_USDT, Side.BUY));
        harness.strategy.onParentIntent(intent(PARENT_ID + 3L, CHILD_ID + 30L, PRICE + 10L, Ids.VENUE_BINANCE,
            Ids.INSTRUMENT_BTC_USDT, Side.SELL));
    }

    private static CountersManager counters() {
        return new CountersManager(new UnsafeBuffer(new byte[1024 * 1024]), new UnsafeBuffer(new byte[64 * 1024]));
    }

    private static final class Harness {
        final ParentOrderRegistry registry;
        final OrderManagerImpl orderManager = new OrderManagerImpl();
        final UnsafeBuffer commandBuffer = new UnsafeBuffer(new byte[1024]);
        final PostOnlyQuoteExecution strategy;

        Harness(final RiskDecision riskDecision, final int parentCapacity, final int childCapacity) {
            this(riskDecision, parentCapacity, childCapacity, PostOnlyQuoteExecution.DEFAULT_ACTIVE_PARENT_CAPACITY);
        }

        Harness(
            final RiskDecision riskDecision,
            final int parentCapacity,
            final int childCapacity,
            final int activeParentCapacity
        ) {
            registry = new ParentOrderRegistry(parentCapacity, childCapacity);
            strategy = new PostOnlyQuoteExecution(activeParentCapacity);
            strategy.init(new ExecutionStrategyContext(
                new InternalMarketView(),
                new RiskStub(riskDecision),
                orderManager,
                registry,
                commandBuffer,
                new MessageHeaderEncoder(),
                new NewOrderCommandEncoder(),
                new CancelOrderCommandEncoder(),
                () -> 1L,
                (correlationId, deadlineClusterMicros) -> true,
                new IdRegistryStub(),
                counters()));
        }
    }

    private record RiskStub(RiskDecision decision) implements RiskEngine {
        @Override public RiskDecision preTradeCheck(final int venueId, final int instrumentId, final byte side, final long priceScaled, final long qtyScaled, final int strategyId) { return decision; }
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

    private static final class IdRegistryStub implements IdRegistry {
        @Override public int venueId(final long sessionId) { return Ids.VENUE_COINBASE; }
        @Override public int instrumentId(final CharSequence symbol) { return Ids.INSTRUMENT_BTC_USD; }
        @Override public String symbolOf(final int instrumentId) { return "BTC-USD"; }
        @Override public String venueNameOf(final int venueId) { return "coinbase"; }
        @Override public void registerSession(final int venueId, final long sessionId) { }
    }
}
