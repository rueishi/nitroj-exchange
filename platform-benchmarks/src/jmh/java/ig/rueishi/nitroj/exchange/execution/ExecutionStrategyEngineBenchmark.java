package ig.rueishi.nitroj.exchange.execution;

import ig.rueishi.nitroj.exchange.cluster.InternalMarketView;
import ig.rueishi.nitroj.exchange.cluster.RiskDecision;
import ig.rueishi.nitroj.exchange.cluster.RiskEngine;
import ig.rueishi.nitroj.exchange.messages.BooleanType;
import ig.rueishi.nitroj.exchange.messages.CancelOrderCommandEncoder;
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
import org.agrona.concurrent.UnsafeBuffer;
import org.agrona.concurrent.status.CountersManager;
import org.openjdk.jmh.annotations.Benchmark;
import org.openjdk.jmh.annotations.BenchmarkMode;
import org.openjdk.jmh.annotations.AuxCounters;
import org.openjdk.jmh.annotations.Fork;
import org.openjdk.jmh.annotations.Level;
import org.openjdk.jmh.annotations.Measurement;
import org.openjdk.jmh.annotations.Mode;
import org.openjdk.jmh.annotations.OutputTimeUnit;
import org.openjdk.jmh.annotations.Scope;
import org.openjdk.jmh.annotations.Setup;
import org.openjdk.jmh.annotations.State;
import org.openjdk.jmh.annotations.Warmup;

import java.lang.management.ManagementFactory;
import java.util.concurrent.TimeUnit;

@BenchmarkMode(Mode.AverageTime)
@OutputTimeUnit(TimeUnit.NANOSECONDS)
@Warmup(iterations = 2, time = 100, timeUnit = TimeUnit.MILLISECONDS)
@Measurement(iterations = 3, time = 100, timeUnit = TimeUnit.MILLISECONDS)
@Fork(value = 1, jvmArgsAppend = {"--enable-preview"})
public class ExecutionStrategyEngineBenchmark {
    private static final int ALLOCATION_CHECK_WARMUP_INVOCATIONS = 10_000;

    @Benchmark
    public boolean parentIntentDispatch(final EngineState state, final AllocationCounters counters) {
        final long before = state.threadBean.getThreadAllocatedBytes(state.threadId);
        final boolean result = state.engine.submit(state.intentDecoder);
        final long allocatedBytes = Math.max(
            0L,
            state.threadBean.getThreadAllocatedBytes(state.threadId) - before - state.allocationProbeOverheadBytes);
        state.assertNoAllocation(counters, allocatedBytes);
        return result;
    }

    @State(Scope.Thread)
    public static class EngineState {
        ExecutionStrategyEngine engine;
        ParentOrderIntentDecoder intentDecoder;
        com.sun.management.ThreadMXBean threadBean;
        long threadId;
        long allocationProbeOverheadBytes;
        int allocationCheckWarmupInvocations;

        @Setup(Level.Trial)
        public void setup() {
            final ParentOrderRegistry parentRegistry = new ParentOrderRegistry(16, 16);
            final ExecutionStrategyContext context = new ExecutionStrategyContext(
                new InternalMarketView(),
                new RiskStub(),
                new OrderManagerStub(),
                parentRegistry,
                new UnsafeBuffer(new byte[1024]),
                new MessageHeaderEncoder(),
                new NewOrderCommandEncoder(),
                new CancelOrderCommandEncoder(),
                () -> 1L,
                (correlationId, deadlineClusterMicros) -> true,
                new IdRegistryStub(),
                new CountersManager(new UnsafeBuffer(new byte[1024 * 1024]), new UnsafeBuffer(new byte[64 * 1024])));
            final ExecutionStrategyRegistry registry = new ExecutionStrategyRegistry(8, 16);
            registry.register(new NoopExecutionStrategy(2));
            registry.allowCompatibility(7, 2);
            engine = new ExecutionStrategyEngine(registry, context, 16);
            intentDecoder = intent(9001L, 7, 2);
            threadBean = (com.sun.management.ThreadMXBean)ManagementFactory.getThreadMXBean();
            if (!threadBean.isThreadAllocatedMemorySupported()) {
                throw new IllegalStateException("Thread allocation measurement is not supported by this JVM");
            }
            threadBean.setThreadAllocatedMemoryEnabled(true);
            threadId = Thread.currentThread().threadId();
            allocationProbeOverheadBytes = allocationProbeOverheadBytes();
            allocationCheckWarmupInvocations = ALLOCATION_CHECK_WARMUP_INVOCATIONS;
        }

        long assertNoAllocation(final AllocationCounters counters, final long allocatedBytes) {
            if (allocationCheckWarmupInvocations > 0) {
                allocationCheckWarmupInvocations--;
                return allocatedBytes;
            }
            if (allocatedBytes != 0L) {
                throw new AssertionError("ExecutionStrategyEngine parent intent dispatch allocated " + allocatedBytes + " bytes");
            }
            counters.measuredThreadAllocatedBytes += allocatedBytes;
            return allocatedBytes;
        }

        private long allocationProbeOverheadBytes() {
            long overheadBytes = 0L;
            for (int i = 0; i < 16; i++) {
                final long before = threadBean.getThreadAllocatedBytes(threadId);
                final long allocatedBytes = threadBean.getThreadAllocatedBytes(threadId) - before;
                overheadBytes = Math.max(overheadBytes, allocatedBytes);
            }
            return overheadBytes;
        }
    }

    @AuxCounters(AuxCounters.Type.EVENTS)
    @State(Scope.Thread)
    public static class AllocationCounters {
        public long measuredThreadAllocatedBytes;
    }

    private static ParentOrderIntentDecoder intent(final long parentOrderId, final int strategyId, final int executionStrategyId) {
        final UnsafeBuffer buffer = new UnsafeBuffer(new byte[256]);
        new ParentOrderIntentEncoder()
            .wrapAndApplyHeader(buffer, 0, new MessageHeaderEncoder())
            .parentOrderId(parentOrderId)
            .strategyId((short)strategyId)
            .executionStrategyId(executionStrategyId)
            .intentType(ParentIntentType.IMMEDIATE_LIMIT)
            .side(Side.BUY)
            .instrumentId(11)
            .primaryVenueId(1)
            .secondaryVenueId(0)
            .quantityScaled(100L)
            .priceMode(PriceMode.LIMIT)
            .limitPriceScaled(65_000L)
            .referencePriceScaled(0L)
            .timeInForcePreference(TimeInForce.IOC)
            .urgencyHint((byte)1)
            .postOnlyPreference(BooleanType.FALSE)
            .selfTradePolicy((byte)0)
            .correlationId(44L)
            .legCount((byte)1)
            .leg2Side(Side.SELL)
            .leg2LimitPriceScaled(0L)
            .parentTimeoutMicros(0L)
            .venueSetId(0);
        final ParentOrderIntentDecoder decoder = new ParentOrderIntentDecoder();
        decoder.wrapAndApplyHeader(buffer, 0, new MessageHeaderDecoder());
        return decoder;
    }

    private static final class NoopExecutionStrategy implements ExecutionStrategy {
        private final int id;
        NoopExecutionStrategy(final int id) { this.id = id; }
        @Override public int executionStrategyId() { return id; }
        @Override public void init(final ExecutionStrategyContext ctx) { }
        @Override public void onParentIntent(final ParentOrderIntentView intent) { }
        @Override public void onMarketDataTick(final int venueId, final int instrumentId, final long clusterTimeMicros) { }
        @Override public void onChildExecution(final ChildExecutionView execution) { }
        @Override public void onTimer(final long correlationId) { }
        @Override public void onCancel(final long parentOrderId, final byte reasonCode) { }
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
        @Override public void onFill(final ig.rueishi.nitroj.exchange.messages.ExecutionEventDecoder decoder) { }
        @Override public void resetAll() { }
    }

    private static final class OrderManagerStub implements OrderManager {
        @Override public void createPendingOrder(final long clOrdId, final int venueId, final int instrumentId, final byte side, final byte ordType, final byte timeInForce, final long priceScaled, final long qtyScaled, final int strategyId) { }
        @Override public boolean onExecution(final ig.rueishi.nitroj.exchange.messages.ExecutionEventDecoder decoder) { return true; }
        @Override public void cancelAllOrders() { }
        @Override public long[] getLiveOrderIds(final int venueId) { return new long[0]; }
        @Override public OrderState getOrder(final long clOrdId) { return null; }
        @Override public void forceTransitionToCanceled(final long clOrdId) { }
        @Override public void writeSnapshot(final ExclusivePublication pub) { }
        @Override public void loadSnapshot(final Image image) { }
        @Override public void setCluster(final Cluster cluster) { }
        @Override public void resetAll() { }
    }

    private static final class IdRegistryStub implements IdRegistry {
        @Override public int venueId(final long sessionId) { return 1; }
        @Override public int instrumentId(final CharSequence symbol) { return 11; }
        @Override public String symbolOf(final int instrumentId) { return "BTC-USD"; }
        @Override public String venueNameOf(final int venueId) { return "coinbase"; }
        @Override public void registerSession(final int venueId, final long sessionId) { }
    }
}
