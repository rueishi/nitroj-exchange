package ig.rueishi.nitroj.exchange.execution;

import ig.rueishi.nitroj.exchange.cluster.InternalMarketView;
import ig.rueishi.nitroj.exchange.cluster.RiskDecision;
import ig.rueishi.nitroj.exchange.cluster.RiskEngine;
import ig.rueishi.nitroj.exchange.common.ExecutionStrategyIds;
import ig.rueishi.nitroj.exchange.common.Ids;
import ig.rueishi.nitroj.exchange.messages.*;
import ig.rueishi.nitroj.exchange.order.OrderManagerImpl;
import ig.rueishi.nitroj.exchange.registry.IdRegistry;
import io.aeron.ExclusivePublication;
import io.aeron.Image;
import io.aeron.cluster.service.Cluster;
import java.lang.management.ManagementFactory;
import java.util.concurrent.TimeUnit;
import org.agrona.concurrent.UnsafeBuffer;
import org.agrona.concurrent.status.CountersManager;
import org.openjdk.jmh.annotations.*;

@BenchmarkMode({Mode.AverageTime, Mode.SampleTime})
@OutputTimeUnit(TimeUnit.NANOSECONDS)
@Warmup(iterations = 2, time = 100, timeUnit = TimeUnit.MILLISECONDS)
@Measurement(iterations = 3, time = 100, timeUnit = TimeUnit.MILLISECONDS)
@Fork(value = 1, jvmArgsAppend = {"--enable-preview"})
public class SmartOrderRoutingExecutionBenchmark {
    private static final int ALLOCATION_CHECK_WARMUP_INVOCATIONS = 10_000;
    private static final long PRICE = 50_000L * Ids.SCALE;

    @Benchmark
    public int slicePlan(final BenchState state, final AllocationCounters counters) {
        final long before = state.threadBean.getThreadAllocatedBytes(state.threadId);
        final int count = state.strategy.computeSlicePlan(state.intentView);
        state.assertNoAllocation(counters, Math.max(0L,
            state.threadBean.getThreadAllocatedBytes(state.threadId) - before - state.allocationProbeOverheadBytes));
        return count;
    }

    @Benchmark
    public boolean parentIntentDispatch(final BenchState state, final AllocationCounters counters) {
        final long before = state.threadBean.getThreadAllocatedBytes(state.threadId);
        final boolean result = state.engine.submit(state.intentDecoder);
        state.assertNoAllocation(counters, Math.max(0L,
            state.threadBean.getThreadAllocatedBytes(state.threadId) - before - state.allocationProbeOverheadBytes));
        return result;
    }

    @Benchmark
    public long reSlicePath(final ResliceState state, final AllocationCounters counters) {
        final long before = state.threadBean.getThreadAllocatedBytes(state.threadId);
        state.strategy.onMarketDataTick(Ids.VENUE_BINANCE, Ids.INSTRUMENT_BTC_USD, state.time);
        state.assertNoAllocation(counters, Math.max(0L,
            state.threadBean.getThreadAllocatedBytes(state.threadId) - before - state.allocationProbeOverheadBytes));
        return state.strategy.resliceSuccesses();
    }

    @State(Scope.Thread)
    public static class BenchState {
        SmartOrderRoutingExecution strategy;
        ExecutionStrategyEngine engine;
        ParentOrderRegistry parents;
        OrderManagerImpl orders;
        ParentOrderIntentDecoder intentDecoder;
        ParentOrderIntentView intentView;
        InternalMarketView market;
        RiskStub risk;
        ExecutionStrategyContext context;
        ExecutionStrategyRegistry registry;
        SmartOrderRoutingExecution.Snapshot emptySnapshot;
        MarketDataEventDecoder coinbaseAsk;
        MarketDataEventDecoder binanceAskHigh;
        MarketDataEventDecoder binanceAskLow;
        MarketDataEventDecoder binanceAskLowDelete;
        com.sun.management.ThreadMXBean threadBean;
        long threadId;
        long allocationProbeOverheadBytes;
        long time;
        int allocationCheckWarmupInvocations;

        @Setup(Level.Trial)
        public void setupTrial() {
            threadBean = (com.sun.management.ThreadMXBean)ManagementFactory.getThreadMXBean();
            if (!threadBean.isThreadAllocatedMemorySupported()) {
                throw new IllegalStateException("Thread allocation measurement is not supported by this JVM");
            }
            threadBean.setThreadAllocatedMemoryEnabled(true);
            threadId = Thread.currentThread().threadId();
            allocationProbeOverheadBytes = allocationProbeOverheadBytes();
            allocationCheckWarmupInvocations = ALLOCATION_CHECK_WARMUP_INVOCATIONS;
            intentDecoder = intent(1L);
            intentView = new ParentOrderIntentView().wrap(intentDecoder);
            time = 1_000L;
            market = new InternalMarketView(new int[] {1, 2}, new int[] {Ids.INSTRUMENT_BTC_USD});
            parents = new ParentOrderRegistry(16, 16);
            orders = new OrderManagerImpl();
            risk = new RiskStub();
            strategy = new SmartOrderRoutingExecution(1L, 0L, 16, zeroFees());
            context = context();
            strategy.init(context);
            registry = new ExecutionStrategyRegistry(8, 8);
            registry.register(strategy);
            registry.allowCompatibility(Ids.STRATEGY_INVENTORY_HEDGE, ExecutionStrategyIds.SMART_ORDER_ROUTING);
            engine = new ExecutionStrategyEngine(registry, context, 16);
            emptySnapshot = strategy.newSnapshot();
            coinbaseAsk = market(Ids.VENUE_COINBASE, PRICE, 100L, UpdateAction.NEW);
            binanceAskHigh = market(Ids.VENUE_BINANCE, PRICE + 10L, 100L, UpdateAction.NEW);
            binanceAskLow = market(Ids.VENUE_BINANCE, PRICE - 20L, 100L, UpdateAction.NEW);
            binanceAskLowDelete = market(Ids.VENUE_BINANCE, PRICE - 20L, 0L, UpdateAction.DELETE);
        }

        @Setup(Level.Invocation)
        public void setupInvocation() {
            time = 1_000L;
            parents.reset();
            orders.resetAll();
            strategy.loadFrom(emptySnapshot);
            market.apply(binanceAskLowDelete, time);
            market.apply(coinbaseAsk, time);
            market.apply(binanceAskHigh, time);
        }

        long assertNoAllocation(final AllocationCounters counters, final long allocatedBytes) {
            if (allocationCheckWarmupInvocations > 0) {
                allocationCheckWarmupInvocations--;
                return allocatedBytes;
            }
            if (allocatedBytes != 0L) {
                throw new AssertionError("SmartOrderRoutingExecution benchmark allocated " + allocatedBytes + " bytes");
            }
            counters.measuredThreadAllocatedBytes += allocatedBytes;
            return allocatedBytes;
        }

        private ExecutionStrategyContext context() {
            return new ExecutionStrategyContext(
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
        }

        private long allocationProbeOverheadBytes() {
            long overheadBytes = 0L;
            for (int i = 0; i < 16; i++) {
                final long before = threadBean.getThreadAllocatedBytes(threadId);
                overheadBytes = Math.max(overheadBytes, threadBean.getThreadAllocatedBytes(threadId) - before);
            }
            return overheadBytes;
        }
    }

    @State(Scope.Thread)
    public static class ResliceState extends BenchState {
        @Override
        @Setup(Level.Invocation)
        public void setupInvocation() {
            super.setupInvocation();
            strategy.onParentIntent(intentView);
            time = 2_000L;
            market.apply(binanceAskLow, time);
        }
    }

    @AuxCounters(AuxCounters.Type.EVENTS)
    @State(Scope.Thread)
    public static class AllocationCounters {
        public long measuredThreadAllocatedBytes;
    }

    private static FeeSchedule zeroFees() {
        final FeeSchedule fees = new FeeSchedule();
        fees.set(Ids.VENUE_COINBASE, 0L, 0L);
        fees.set(Ids.VENUE_BINANCE, 0L, 0L);
        return fees;
    }

    private static ParentOrderIntentDecoder intent(final long parentOrderId) {
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
            .quantityScaled(100L)
            .priceMode(PriceMode.LIMIT)
            .limitPriceScaled(PRICE + 50L)
            .referencePriceScaled(PRICE)
            .timeInForcePreference(TimeInForce.IOC)
            .urgencyHint((byte) 3)
            .postOnlyPreference(BooleanType.FALSE)
            .selfTradePolicy((byte) 1)
            .correlationId(parentOrderId * 1_000L)
            .legCount((byte) 1)
            .leg2Side(Side.NULL_VAL)
            .leg2LimitPriceScaled(0L)
            .parentTimeoutMicros(1_000L)
            .venueSetId(7);
        final ParentOrderIntentDecoder decoder = new ParentOrderIntentDecoder();
        decoder.wrapAndApplyHeader(buffer, 0, new MessageHeaderDecoder());
        return decoder;
    }

    private static MarketDataEventDecoder market(
        final int venueId,
        final long price,
        final long size,
        final UpdateAction updateAction) {
        final UnsafeBuffer buffer = new UnsafeBuffer(new byte[256]);
        new MarketDataEventEncoder()
            .wrapAndApplyHeader(buffer, 0, new MessageHeaderEncoder())
            .venueId(venueId)
            .instrumentId(Ids.INSTRUMENT_BTC_USD)
            .entryType(EntryType.ASK)
            .updateAction(updateAction)
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

    private static final class IdsStub implements IdRegistry {
        @Override public int venueId(final long sessionId) { return Ids.VENUE_COINBASE; }
        @Override public int instrumentId(final CharSequence symbol) { return Ids.INSTRUMENT_BTC_USD; }
        @Override public String symbolOf(final int instrumentId) { return "BTC-USD"; }
        @Override public String venueNameOf(final int venueId) { return "COINBASE"; }
        @Override public void registerSession(final int venueId, final long sessionId) { }
    }
}
