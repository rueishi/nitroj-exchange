package ig.rueishi.nitroj.exchange.strategy;

import ig.rueishi.nitroj.exchange.cluster.InternalMarketView;
import ig.rueishi.nitroj.exchange.cluster.PortfolioEngine;
import ig.rueishi.nitroj.exchange.cluster.RecoveryCoordinator;
import ig.rueishi.nitroj.exchange.cluster.RiskDecision;
import ig.rueishi.nitroj.exchange.cluster.RiskEngine;
import ig.rueishi.nitroj.exchange.common.ExecutionStrategyIds;
import ig.rueishi.nitroj.exchange.common.Ids;
import ig.rueishi.nitroj.exchange.execution.ChildExecutionView;
import ig.rueishi.nitroj.exchange.execution.ExecutionStrategy;
import ig.rueishi.nitroj.exchange.execution.ExecutionStrategyContext;
import ig.rueishi.nitroj.exchange.execution.ExecutionStrategyEngine;
import ig.rueishi.nitroj.exchange.execution.ExecutionStrategyRegistry;
import ig.rueishi.nitroj.exchange.execution.ParentOrderIntentView;
import ig.rueishi.nitroj.exchange.execution.ParentOrderRegistry;
import ig.rueishi.nitroj.exchange.messages.BalanceQueryResponseDecoder;
import ig.rueishi.nitroj.exchange.messages.CancelOrderCommandEncoder;
import ig.rueishi.nitroj.exchange.messages.EntryType;
import ig.rueishi.nitroj.exchange.messages.ExecutionEventDecoder;
import ig.rueishi.nitroj.exchange.messages.MessageHeaderEncoder;
import ig.rueishi.nitroj.exchange.messages.NewOrderCommandEncoder;
import ig.rueishi.nitroj.exchange.messages.VenueStatusEventDecoder;
import ig.rueishi.nitroj.exchange.order.OrderManagerImpl;
import ig.rueishi.nitroj.exchange.registry.IdRegistry;
import io.aeron.ExclusivePublication;
import io.aeron.Image;
import io.aeron.Publication;
import io.aeron.cluster.service.Cluster;
import java.lang.management.ManagementFactory;
import java.util.concurrent.TimeUnit;
import org.agrona.concurrent.UnsafeBuffer;
import org.agrona.concurrent.status.CountersManager;
import org.openjdk.jmh.annotations.AuxCounters;
import org.openjdk.jmh.annotations.Benchmark;
import org.openjdk.jmh.annotations.BenchmarkMode;
import org.openjdk.jmh.annotations.Fork;
import org.openjdk.jmh.annotations.Level;
import org.openjdk.jmh.annotations.Measurement;
import org.openjdk.jmh.annotations.Mode;
import org.openjdk.jmh.annotations.OutputTimeUnit;
import org.openjdk.jmh.annotations.Scope;
import org.openjdk.jmh.annotations.Setup;
import org.openjdk.jmh.annotations.State;
import org.openjdk.jmh.annotations.Warmup;

@BenchmarkMode(Mode.AverageTime)
@OutputTimeUnit(TimeUnit.NANOSECONDS)
@Warmup(iterations = 2, time = 100, timeUnit = TimeUnit.MILLISECONDS)
@Measurement(iterations = 3, time = 100, timeUnit = TimeUnit.MILLISECONDS)
@Fork(value = 1, jvmArgsAppend = {"--enable-preview"})
public class InventoryHedgeStrategyBenchmark {
    private static final int ALLOCATION_CHECK_WARMUP_INVOCATIONS = 10_000;
    private static final long BTC = Ids.SCALE;
    private static final long PRICE = 50_000L * Ids.SCALE;

    @Benchmark
    public long baseQuantityTrigger(final HedgeState state, final AllocationCounters counters) {
        final long before = state.threadBean.getThreadAllocatedBytes(state.threadId);
        state.strategy.onMarketData(null);
        final long allocatedBytes = Math.max(
            0L,
            state.threadBean.getThreadAllocatedBytes(state.threadId) - before - state.allocationProbeOverheadBytes);
        state.assertNoAllocation(counters, allocatedBytes);
        return state.strategy.emittedParentCount();
    }

    @State(Scope.Thread)
    public static class HedgeState {
        InventoryHedgeStrategy strategy;
        InventoryHedgeStrategy.Snapshot emptySnapshot;
        PortfolioStub portfolio;
        com.sun.management.ThreadMXBean threadBean;
        long threadId;
        long allocationProbeOverheadBytes;
        int allocationCheckWarmupInvocations;

        @Setup(Level.Trial)
        public void setupTrial() {
            final InternalMarketView market = new InternalMarketView(
                new int[] {Ids.VENUE_COINBASE},
                new int[] {Ids.INSTRUMENT_BTC_USD});
            market.consolidatedBook(Ids.INSTRUMENT_BTC_USD)
                .applyVenueLevel(Ids.VENUE_COINBASE, EntryType.BID, PRICE - Ids.SCALE, 10L * BTC);
            market.consolidatedBook(Ids.INSTRUMENT_BTC_USD)
                .applyVenueLevel(Ids.VENUE_COINBASE, EntryType.ASK, PRICE + Ids.SCALE, 10L * BTC);
            portfolio = new PortfolioStub();
            final ParentOrderRegistry parents = new ParentOrderRegistry(16, 16);
            final ExecutionStrategyRegistry registry = new ExecutionStrategyRegistry(8, 8);
            registry.register(new NoopExecutionStrategy());
            registry.allowCompatibility(Ids.STRATEGY_INVENTORY_HEDGE, ExecutionStrategyIds.PARALLEL_VENUE);
            final ExecutionStrategyEngine executionEngine = new ExecutionStrategyEngine(
                registry,
                new ExecutionStrategyContext(
                    market,
                    new RiskStub(),
                    new OrderManagerImpl(),
                    parents,
                    new UnsafeBuffer(new byte[1024]),
                    new MessageHeaderEncoder(),
                    new NewOrderCommandEncoder(),
                    new CancelOrderCommandEncoder(),
                    () -> 1L,
                    (correlationId, deadlineClusterMicros) -> true,
                    new IdsStub(),
                    new CountersManager(new UnsafeBuffer(new byte[1024 * 1024]), new UnsafeBuffer(new byte[64 * 1024]))),
                16);
            strategy = new InventoryHedgeStrategy(new InventoryHedgeStrategy.Config(
                Ids.INSTRUMENT_BTC_USD,
                7,
                new int[] {Ids.VENUE_COINBASE},
                InventoryHedgeStrategy.ThresholdMode.BASE_QUANTITY,
                BTC + BTC / 2L,
                InventoryHedgeStrategy.ExposureMode.FILLED_ONLY,
                BTC,
                0L,
                0L,
                ExecutionStrategyIds.PARALLEL_VENUE,
                0L));
            strategy.init(new Context(market, portfolio, executionEngine));
            emptySnapshot = new InventoryHedgeStrategy.Snapshot();
            threadBean = (com.sun.management.ThreadMXBean)ManagementFactory.getThreadMXBean();
            if (!threadBean.isThreadAllocatedMemorySupported()) {
                throw new IllegalStateException("Thread allocation measurement is not supported by this JVM");
            }
            threadBean.setThreadAllocatedMemoryEnabled(true);
            threadId = Thread.currentThread().threadId();
            allocationProbeOverheadBytes = allocationProbeOverheadBytes();
            allocationCheckWarmupInvocations = ALLOCATION_CHECK_WARMUP_INVOCATIONS;
        }

        @Setup(Level.Invocation)
        public void setupInvocation() {
            strategy.loadFrom(emptySnapshot);
            portfolio.netQty[Ids.VENUE_COINBASE] = 2L * BTC;
        }

        long assertNoAllocation(final AllocationCounters counters, final long allocatedBytes) {
            if (allocationCheckWarmupInvocations > 0) {
                allocationCheckWarmupInvocations--;
                return allocatedBytes;
            }
            if (allocatedBytes != 0L) {
                throw new AssertionError("InventoryHedgeStrategy benchmark allocated " + allocatedBytes + " bytes");
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

    private record Context(
        InternalMarketView marketView,
        PortfolioEngine portfolioEngine,
        ExecutionStrategyEngine executionEngine
    ) implements StrategyContext {
        @Override public RiskEngine riskEngine() { return new RiskStub(); }
        @Override public ig.rueishi.nitroj.exchange.order.OrderManager orderManager() { return new OrderManagerImpl(); }
        @Override public RecoveryCoordinator recoveryCoordinator() { return new RecoveryStub(); }
        @Override public Cluster cluster() { return null; }
        @Override public UnsafeBuffer egressBuffer() { return new UnsafeBuffer(new byte[1024]); }
        @Override public MessageHeaderEncoder headerEncoder() { return new MessageHeaderEncoder(); }
        @Override public NewOrderCommandEncoder newOrderEncoder() { return new NewOrderCommandEncoder(); }
        @Override public CancelOrderCommandEncoder cancelOrderEncoder() { return new CancelOrderCommandEncoder(); }
        @Override public IdRegistry idRegistry() { return new IdsStub(); }
        @Override public CountersManager counters() { return new CountersManager(new UnsafeBuffer(new byte[1024 * 1024]), new UnsafeBuffer(new byte[64 * 1024])); }
    }

    private static final class NoopExecutionStrategy implements ExecutionStrategy {
        @Override public int executionStrategyId() { return ExecutionStrategyIds.PARALLEL_VENUE; }
        @Override public void init(final ExecutionStrategyContext ctx) { }
        @Override public void onParentIntent(final ParentOrderIntentView intent) { }
        @Override public void onMarketDataTick(final int venueId, final int instrumentId, final long clusterTimeMicros) { }
        @Override public void onChildExecution(final ChildExecutionView execution) { }
        @Override public void onTimer(final long correlationId) { }
        @Override public void onCancel(final long parentOrderId, final byte reasonCode) { }
    }

    private static final class PortfolioStub implements PortfolioEngine {
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
