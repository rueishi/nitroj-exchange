package ig.rueishi.nitroj.exchange.execution;

import ig.rueishi.nitroj.exchange.cluster.InternalMarketView;
import ig.rueishi.nitroj.exchange.cluster.RiskDecision;
import ig.rueishi.nitroj.exchange.cluster.RiskEngine;
import ig.rueishi.nitroj.exchange.messages.BooleanType;
import ig.rueishi.nitroj.exchange.messages.CancelOrderCommandEncoder;
import ig.rueishi.nitroj.exchange.messages.ExecType;
import ig.rueishi.nitroj.exchange.messages.ExecutionEventDecoder;
import ig.rueishi.nitroj.exchange.messages.ExecutionEventEncoder;
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
import ig.rueishi.nitroj.exchange.strategy.StrategyContext;
import ig.rueishi.nitroj.exchange.strategy.StrategyContextImpl;
import io.aeron.ExclusivePublication;
import io.aeron.Image;
import io.aeron.cluster.service.Cluster;
import org.agrona.concurrent.UnsafeBuffer;
import org.agrona.concurrent.status.CountersManager;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Proxy;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

final class ExecutionStrategyEngineTest {
    @Test
    void registryRejectsInvalidDuplicateAndSupportsCompatibility() {
        final ExecutionStrategyRegistry registry = new ExecutionStrategyRegistry(4, 8);
        final RecordingExecutionStrategy strategy = new RecordingExecutionStrategy(2);

        registry.register(strategy);
        registry.allowCompatibility(7, 2);

        assertThat(registry.lookup(2)).isSameAs(strategy);
        assertThat(registry.isCompatible(7, 2)).isTrue();
        assertThat(registry.registeredCount()).isEqualTo(1);
        assertThatThrownBy(() -> registry.register(strategy))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("duplicate");
        assertThatThrownBy(() -> registry.allowCompatibility(99, 2))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("trading strategy id");
    }

    @Test
    void submitDispatchesCompatibleIntentAndCountsUnknownAndIncompatible() {
        final Harness harness = harness(4);

        assertThat(harness.engine.submit(parentIntent(1001L, 7, 2))).isTrue();
        assertThat(harness.strategy.events).containsExactly("intent:1001");
        assertThat(harness.engine.parentIntentDispatches()).isEqualTo(1L);

        assertThat(harness.engine.submit(parentIntent(1002L, 7, 3))).isFalse();
        assertThat(harness.engine.unknownExecutionStrategyRejects()).isEqualTo(1L);

        assertThat(harness.engine.submit(parentIntent(1003L, 8, 2))).isFalse();
        assertThat(harness.engine.incompatibleExecutionStrategyRejects()).isEqualTo(1L);
    }

    @Test
    void childExecutionCancelTimerAndMarketDataDispatchInDeterministicOrder() {
        final Harness harness = harness(4);
        harness.parentRegistry.claim(9001L, 7, 2, 100L, 1L);

        assertThat(harness.engine.onChildExecution(execution(55L), 9001L)).isTrue();
        assertThat(harness.engine.registerTimerOwner(77L, 2)).isTrue();
        assertThat(harness.engine.onTimer(77L)).isTrue();
        assertThat(harness.engine.cancelParent(9001L, ParentOrderState.REASON_CANCELED_BY_PARENT)).isTrue();
        harness.engine.onMarketDataTick(1, 11, 123L);

        assertThat(harness.strategy.events).containsExactly(
            "child:55:9001",
            "timer:77",
            "cancel:9001:2",
            "market:1:11:123");
        assertThat(harness.engine.childExecutionDispatches()).isEqualTo(1L);
        assertThat(harness.engine.timerDispatches()).isEqualTo(1L);
        assertThat(harness.engine.cancelDispatches()).isEqualTo(1L);
        assertThat(harness.engine.marketDataDispatches()).isEqualTo(1L);
    }

    @Test
    void terminalParentStateEmitsCallbackOnce() {
        final Harness harness = harness(4);
        final List<Long> terminalParents = new ArrayList<>();
        harness.engine.initRegisteredStrategies();
        harness.engine.setParentCallbackSink(decoder -> terminalParents.add(decoder.parentOrderId()));
        harness.strategy.terminalOnIntent = true;

        assertThat(harness.engine.submit(parentIntent(1001L, 7, 2))).isTrue();

        assertThat(terminalParents).containsExactly(1001L);
    }

    @Test
    void timerAndParentFailuresUseCountersNotExceptions() {
        final Harness harness = harness(1);

        assertThat(harness.engine.onTimer(99L)).isFalse();
        assertThat(harness.engine.unknownTimerRejects()).isEqualTo(1L);
        assertThat(harness.engine.registerTimerOwner(1L, 2)).isTrue();
        assertThat(harness.engine.registerTimerOwner(2L, 2)).isFalse();
        assertThat(harness.engine.timerCapacityRejects()).isEqualTo(1L);

        assertThat(harness.engine.cancelParent(44L, ParentOrderState.REASON_EXECUTION_ABORTED)).isFalse();
        assertThat(harness.engine.onChildExecution(execution(66L), 44L)).isFalse();
        assertThat(harness.engine.missingParentRejects()).isEqualTo(2L);
    }

    @Test
    void clusterBackedTimerRegistersOwnerBeforeSchedulingAndSkipsClusterOnCapacityFailure() {
        final Harness harness = harness(1);
        final ClusterBackedExecutionClock clock = new ClusterBackedExecutionClock();
        final AtomicInteger scheduleCalls = new AtomicInteger();
        clock.setExecutionStrategyEngine(harness.engine);
        clock.setCluster(cluster(scheduleCalls, true));

        assertThat(clock.scheduleTimer(1L, 55L, 2)).isTrue();
        assertThat(clock.scheduleTimer(2L, 66L, 2)).isFalse();

        assertThat(scheduleCalls).hasValue(1);
        assertThat(harness.engine.timerCapacityRejects()).isEqualTo(1L);
        assertThat(harness.engine.onTimer(1L)).isTrue();
        assertThat(harness.strategy.events).containsExactly("timer:1");
    }

    @Test
    void duplicateActiveTimerCorrelationRejectedWithoutReplacingOwner() {
        final Harness harness = harness(2);

        assertThat(harness.engine.registerTimerOwner(77L, 2)).isTrue();
        assertThat(harness.engine.registerTimerOwner(77L, 2)).isFalse();

        assertThat(harness.engine.timerCapacityRejects()).isEqualTo(1L);
        assertThat(harness.engine.onTimer(77L)).isTrue();
        assertThat(harness.strategy.events).containsExactly("timer:77");
        assertThat(harness.engine.onTimer(77L)).isFalse();
    }

    @Test
    void clusterBackedTimerUnregistersOwnerWhenClusterSchedulingFails() {
        final Harness harness = harness(1);
        final ClusterBackedExecutionClock clock = new ClusterBackedExecutionClock();
        final AtomicInteger scheduleCalls = new AtomicInteger();
        clock.setExecutionStrategyEngine(harness.engine);
        clock.setCluster(cluster(scheduleCalls, false));

        assertThat(clock.scheduleTimer(1L, 55L, 2)).isFalse();

        assertThat(scheduleCalls).hasValue(1);
        assertThat(harness.engine.routeTimerIfOwned(1L)).isFalse();
        assertThat(harness.engine.unknownTimerRejects()).isZero();
    }

    @Test
    void initRegisteredStrategiesSuppliesContextAndStrategyContextAccessorExists() {
        final Harness harness = harness(4);
        harness.engine.initRegisteredStrategies();

        final StrategyContext strategyContext = new StrategyContextImpl(
            new InternalMarketView(),
            new RiskStub(),
            new OrderManagerStub(),
            null,
            null,
            harness.engine,
            null,
            new UnsafeBuffer(new byte[128]),
            new MessageHeaderEncoder(),
            new NewOrderCommandEncoder(),
            new CancelOrderCommandEncoder(),
            new IdRegistryStub(),
            counters());

        assertThat(harness.strategy.ctx).isSameAs(harness.context);
        assertThat(strategyContext.executionEngine()).isSameAs(harness.engine);
    }

    @Test
    void snapshotLoadPreservesCountersAndTimerOwnerTableAcrossRestartBoundary() {
        final Harness source = harness(4);
        source.parentRegistry.claim(9001L, 7, 2, 100L, 1L);

        assertThat(source.engine.submit(parentIntent(1001L, 7, 2))).isTrue();
        assertThat(source.engine.submit(parentIntent(1002L, 7, 3))).isFalse();
        assertThat(source.engine.submit(parentIntent(1003L, 8, 2))).isFalse();
        assertThat(source.engine.onChildExecution(execution(55L), 9001L)).isTrue();
        assertThat(source.engine.cancelParent(9001L, ParentOrderState.REASON_CANCELED_BY_PARENT)).isTrue();
        source.engine.onMarketDataTick(1, 11, 123L);
        assertThat(source.engine.registerTimerOwner(77L, 2)).isTrue();
        assertThat(source.engine.onTimer(99L)).isFalse();

        final ExecutionStrategyEngine.Snapshot snapshot = source.engine.newSnapshot();
        source.engine.snapshotInto(snapshot);

        final Harness restored = harness(4);
        restored.engine.loadFrom(snapshot);

        assertThat(restored.engine.parentIntentDispatches()).isEqualTo(1L);
        assertThat(restored.engine.childExecutionDispatches()).isEqualTo(1L);
        assertThat(restored.engine.cancelDispatches()).isEqualTo(1L);
        assertThat(restored.engine.marketDataDispatches()).isEqualTo(1L);
        assertThat(restored.engine.unknownExecutionStrategyRejects()).isEqualTo(1L);
        assertThat(restored.engine.incompatibleExecutionStrategyRejects()).isEqualTo(1L);
        assertThat(restored.engine.unknownTimerRejects()).isEqualTo(1L);
        assertThat(snapshot.timerActive(0)).isTrue();
        assertThat(snapshot.timerCorrelationId(0)).isEqualTo(77L);
        assertThat(snapshot.timerExecutionStrategyId(0)).isEqualTo(2);

        assertThat(restored.engine.onTimer(77L)).isTrue();
        assertThat(restored.strategy.events).containsExactly("timer:77");
        assertThat(restored.engine.timerDispatches()).isEqualTo(1L);
    }

    private static Harness harness(final int timerCapacity) {
        final ParentOrderRegistry parentRegistry = new ParentOrderRegistry(8, 8);
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
            counters());
        final ExecutionStrategyRegistry registry = new ExecutionStrategyRegistry(4, 16);
        final RecordingExecutionStrategy strategy = new RecordingExecutionStrategy(2);
        registry.register(strategy);
        registry.allowCompatibility(7, 2);
        return new Harness(new ExecutionStrategyEngine(registry, context, timerCapacity), context, parentRegistry, strategy);
    }

    private static ParentOrderIntentDecoder parentIntent(final long parentOrderId, final int strategyId, final int executionStrategyId) {
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
            .parentTimeoutMicros(0L);
        final ParentOrderIntentDecoder decoder = new ParentOrderIntentDecoder();
        decoder.wrapAndApplyHeader(buffer, 0, new MessageHeaderDecoder());
        return decoder;
    }

    private static ExecutionEventDecoder execution(final long childClOrdId) {
        final UnsafeBuffer buffer = new UnsafeBuffer(new byte[256]);
        new ExecutionEventEncoder()
            .wrapAndApplyHeader(buffer, 0, new MessageHeaderEncoder())
            .clOrdId(childClOrdId)
            .venueId(1)
            .instrumentId(11)
            .execType(ExecType.FILL)
            .side(Side.BUY)
            .fillPriceScaled(65_000L)
            .fillQtyScaled(100L)
            .cumQtyScaled(100L)
            .leavesQtyScaled(0L)
            .rejectCode(0)
            .ingressTimestampNanos(1L)
            .exchangeTimestampNanos(2L)
            .fixSeqNum(3)
            .isFinal(BooleanType.TRUE)
            .putVenueOrderId(new byte[0], 0, 0)
            .putExecId(new byte[0], 0, 0);
        final ExecutionEventDecoder decoder = new ExecutionEventDecoder();
        decoder.wrapAndApplyHeader(buffer, 0, new MessageHeaderDecoder());
        return decoder;
    }

    private static CountersManager counters() {
        return new CountersManager(new UnsafeBuffer(new byte[1024 * 1024]), new UnsafeBuffer(new byte[64 * 1024]));
    }

    private static Cluster cluster(final AtomicInteger scheduleCalls, final boolean scheduleResult) {
        return (Cluster) Proxy.newProxyInstance(
            Cluster.class.getClassLoader(),
            new Class<?>[]{Cluster.class},
            (proxy, method, args) -> switch (method.getName()) {
                case "scheduleTimer" -> {
                    scheduleCalls.incrementAndGet();
                    yield scheduleResult;
                }
                case "time", "logPosition" -> 1L;
                case "toString" -> "ExecutionStrategyEngineTestCluster";
                default -> null;
            });
    }

    private record Harness(
        ExecutionStrategyEngine engine,
        ExecutionStrategyContext context,
        ParentOrderRegistry parentRegistry,
        RecordingExecutionStrategy strategy) {
    }

    private static final class RecordingExecutionStrategy implements ExecutionStrategy {
        private final int id;
        private final List<String> events = new ArrayList<>();
        private ExecutionStrategyContext ctx;
        private boolean terminalOnIntent;

        RecordingExecutionStrategy(final int id) {
            this.id = id;
        }

        @Override public int executionStrategyId() { return id; }
        @Override public void init(final ExecutionStrategyContext ctx) { this.ctx = ctx; }
        @Override public void onParentIntent(final ParentOrderIntentView intent) {
            events.add("intent:" + intent.parentOrderId());
            if (terminalOnIntent) {
                ctx.parentOrderRegistry().claim(intent.parentOrderId(), intent.strategyId(), executionStrategyId(), intent.quantityScaled(), 1L);
                ctx.parentOrderRegistry().transition(intent.parentOrderId(), ParentOrderState.FAILED, ParentOrderState.REASON_RISK_REJECTED, 1L);
            }
        }
        @Override public void onMarketDataTick(final int venueId, final int instrumentId, final long clusterTimeMicros) { events.add("market:" + venueId + ":" + instrumentId + ":" + clusterTimeMicros); }
        @Override public void onChildExecution(final ChildExecutionView execution) { events.add("child:" + execution.childClOrdId() + ":" + execution.parentOrderId()); }
        @Override public void onTimer(final long correlationId) { events.add("timer:" + correlationId); }
        @Override public void onCancel(final long parentOrderId, final byte reasonCode) { events.add("cancel:" + parentOrderId + ":" + reasonCode); }
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

    private static final class OrderManagerStub implements OrderManager {
        @Override public void createPendingOrder(final long clOrdId, final int venueId, final int instrumentId, final byte side, final byte ordType, final byte timeInForce, final long priceScaled, final long qtyScaled, final int strategyId) { }
        @Override public boolean onExecution(final ExecutionEventDecoder decoder) { return true; }
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
