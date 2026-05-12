package ig.rueishi.nitroj.exchange.execution;

import ig.rueishi.nitroj.exchange.messages.ExecutionEventDecoder;
import ig.rueishi.nitroj.exchange.messages.MessageHeaderDecoder;
import ig.rueishi.nitroj.exchange.messages.MessageHeaderEncoder;
import ig.rueishi.nitroj.exchange.messages.ParentOrderIntentDecoder;
import ig.rueishi.nitroj.exchange.messages.ParentOrderTerminalDecoder;
import ig.rueishi.nitroj.exchange.messages.ParentOrderTerminalEncoder;
import ig.rueishi.nitroj.exchange.messages.ParentTerminalReason;
import org.agrona.concurrent.UnsafeBuffer;

import java.util.Objects;

/**
 * Deterministic dispatcher for execution strategy plugins.
 *
 * <p>The engine validates the parent intent's execution strategy ID and
 * strategy/execution compatibility before dispatch using the startup-populated
 * primitive matrix. Clear operator-facing compatibility errors are produced by
 * cold config validation; this hot-path engine only increments primitive reject
 * counters and returns {@code false}. Child execution and cancel dispatch use
 * {@link ParentOrderRegistry} parent state to recover the owning execution
 * strategy. Timer dispatch uses a bounded primitive correlation-owner table
 * registered by execution strategies when they schedule timers through the
 * cluster-thread path.</p>
 */
public final class ExecutionStrategyEngine {
    private final ExecutionStrategyRegistry registry;
    private final ExecutionStrategyContext context;
    private final ParentOrderIntentView parentIntentView = new ParentOrderIntentView();
    private final ChildExecutionView childExecutionView = new ChildExecutionView();
    private final UnsafeBuffer parentCallbackBuffer = new UnsafeBuffer(new byte[128]);
    private final MessageHeaderEncoder parentCallbackHeaderEncoder = new MessageHeaderEncoder();
    private final MessageHeaderDecoder parentCallbackHeaderDecoder = new MessageHeaderDecoder();
    private final ParentOrderTerminalEncoder parentTerminalEncoder = new ParentOrderTerminalEncoder();
    private final ParentOrderTerminalDecoder parentTerminalDecoder = new ParentOrderTerminalDecoder();
    private final long[] timerCorrelationIds;
    private final int[] timerExecutionStrategyIds;
    private final boolean[] timerActive;
    private ParentCallbackSink parentCallbackSink = ParentCallbackSink.NO_OP;

    private long parentIntentDispatches;
    private long childExecutionDispatches;
    private long timerDispatches;
    private long cancelDispatches;
    private long marketDataDispatches;
    private long unknownExecutionStrategyRejects;
    private long incompatibleExecutionStrategyRejects;
    private long missingParentRejects;
    private long timerCapacityRejects;
    private long unknownTimerRejects;

    public ExecutionStrategyEngine(final ExecutionStrategyRegistry registry, final ExecutionStrategyContext context) {
        this(registry, context, 4096);
    }

    public ExecutionStrategyEngine(
        final ExecutionStrategyRegistry registry,
        final ExecutionStrategyContext context,
        final int timerCapacity) {
        this.registry = Objects.requireNonNull(registry, "registry");
        this.context = Objects.requireNonNull(context, "context");
        if (timerCapacity <= 0) {
            throw new IllegalArgumentException("timerCapacity must be positive");
        }
        timerCorrelationIds = new long[timerCapacity];
        timerExecutionStrategyIds = new int[timerCapacity];
        timerActive = new boolean[timerCapacity];
    }

    public void initRegisteredStrategies() {
        for (int id = 0; id < registry.executionStrategyCapacity(); id++) {
            final ExecutionStrategy strategy = registry.lookup(id);
            if (strategy != null) {
                strategy.init(context);
            }
        }
    }

    public void setParentCallbackSink(final ParentCallbackSink parentCallbackSink) {
        this.parentCallbackSink = Objects.requireNonNull(parentCallbackSink, "parentCallbackSink");
    }

    public boolean submit(final ParentOrderIntentDecoder intent) {
        Objects.requireNonNull(intent, "intent");
        final int executionStrategyId = intent.executionStrategyId();
        final ExecutionStrategy strategy = registry.lookup(executionStrategyId);
        if (strategy == null) {
            unknownExecutionStrategyRejects++;
            return false;
        }
        if (!registry.isCompatible(intent.strategyId(), executionStrategyId)) {
            incompatibleExecutionStrategyRejects++;
            return false;
        }
        strategy.onParentIntent(parentIntentView.wrap(intent));
        parentIntentDispatches++;
        emitUnreportedTerminalCallbacks();
        return true;
    }

    public boolean onChildExecution(final ExecutionEventDecoder execution, final long parentOrderId) {
        Objects.requireNonNull(execution, "execution");
        final ParentOrderState parent = context.parentOrderRegistry().lookup(parentOrderId);
        if (parent == null) {
            missingParentRejects++;
            return false;
        }
        final ExecutionStrategy strategy = registry.lookup(parent.executionStrategyId());
        if (strategy == null) {
            unknownExecutionStrategyRejects++;
            return false;
        }
        strategy.onChildExecution(childExecutionView.wrap(execution, parentOrderId));
        childExecutionDispatches++;
        emitUnreportedTerminalCallbacks();
        return true;
    }

    public void onMarketDataTick(final int venueId, final int instrumentId, final long clusterTimeMicros) {
        for (int id = 0; id < registry.executionStrategyCapacity(); id++) {
            final ExecutionStrategy strategy = registry.lookup(id);
            if (strategy != null) {
                strategy.onMarketDataTick(venueId, instrumentId, clusterTimeMicros);
                marketDataDispatches++;
            }
        }
        emitUnreportedTerminalCallbacks();
    }

    public boolean registerTimerOwner(final long correlationId, final int executionStrategyId) {
        if (registry.lookup(executionStrategyId) == null) {
            unknownExecutionStrategyRejects++;
            return false;
        }
        for (int i = 0; i < timerActive.length; i++) {
            if (timerActive[i] && timerCorrelationIds[i] == correlationId) {
                timerCapacityRejects++;
                return false;
            }
        }
        for (int i = 0; i < timerActive.length; i++) {
            if (!timerActive[i]) {
                timerCorrelationIds[i] = correlationId;
                timerExecutionStrategyIds[i] = executionStrategyId;
                timerActive[i] = true;
                return true;
            }
        }
        timerCapacityRejects++;
        return false;
    }

    public boolean unregisterTimerOwner(final long correlationId) {
        for (int i = 0; i < timerActive.length; i++) {
            if (timerActive[i] && timerCorrelationIds[i] == correlationId) {
                timerActive[i] = false;
                return true;
            }
        }
        return false;
    }

    public boolean onTimer(final long correlationId) {
        return onTimer(correlationId, true);
    }

    public boolean routeTimerIfOwned(final long correlationId) {
        return onTimer(correlationId, false);
    }

    private boolean onTimer(final long correlationId, final boolean countUnknown) {
        for (int i = 0; i < timerActive.length; i++) {
            if (timerActive[i] && timerCorrelationIds[i] == correlationId) {
                final ExecutionStrategy strategy = registry.lookup(timerExecutionStrategyIds[i]);
                timerActive[i] = false;
                if (strategy == null) {
                    unknownExecutionStrategyRejects++;
                    return false;
                }
                strategy.onTimer(correlationId);
                timerDispatches++;
                emitUnreportedTerminalCallbacks();
                return true;
            }
        }
        if (countUnknown) {
            unknownTimerRejects++;
        }
        return false;
    }

    public boolean cancelParent(final long parentOrderId, final byte reasonCode) {
        final ParentOrderState parent = context.parentOrderRegistry().lookup(parentOrderId);
        if (parent == null) {
            missingParentRejects++;
            return false;
        }
        final ExecutionStrategy strategy = registry.lookup(parent.executionStrategyId());
        if (strategy == null) {
            unknownExecutionStrategyRejects++;
            return false;
        }
        strategy.onCancel(parentOrderId, reasonCode);
        cancelDispatches++;
        emitUnreportedTerminalCallbacks();
        return true;
    }

    public ParentOrderRegistry parentOrderRegistry() {
        return context.parentOrderRegistry();
    }

    public long parentIntentDispatches() {
        return parentIntentDispatches;
    }

    public long childExecutionDispatches() {
        return childExecutionDispatches;
    }

    public long timerDispatches() {
        return timerDispatches;
    }

    public long cancelDispatches() {
        return cancelDispatches;
    }

    public long marketDataDispatches() {
        return marketDataDispatches;
    }

    public long unknownExecutionStrategyRejects() {
        return unknownExecutionStrategyRejects;
    }

    public long incompatibleExecutionStrategyRejects() {
        return incompatibleExecutionStrategyRejects;
    }

    public long missingParentRejects() {
        return missingParentRejects;
    }

    public long timerCapacityRejects() {
        return timerCapacityRejects;
    }

    public long unknownTimerRejects() {
        return unknownTimerRejects;
    }

    public Snapshot newSnapshot() {
        return new Snapshot(timerActive.length);
    }

    public void snapshotInto(final Snapshot snapshot) {
        Objects.requireNonNull(snapshot, "snapshot");
        if (snapshot.timerCorrelationIds.length != timerCorrelationIds.length) {
            throw new IllegalArgumentException("snapshot timer capacity mismatch");
        }
        System.arraycopy(timerCorrelationIds, 0, snapshot.timerCorrelationIds, 0, timerCorrelationIds.length);
        System.arraycopy(timerExecutionStrategyIds, 0, snapshot.timerExecutionStrategyIds, 0, timerExecutionStrategyIds.length);
        System.arraycopy(timerActive, 0, snapshot.timerActive, 0, timerActive.length);
        snapshot.parentIntentDispatches = parentIntentDispatches;
        snapshot.childExecutionDispatches = childExecutionDispatches;
        snapshot.timerDispatches = timerDispatches;
        snapshot.cancelDispatches = cancelDispatches;
        snapshot.marketDataDispatches = marketDataDispatches;
        snapshot.unknownExecutionStrategyRejects = unknownExecutionStrategyRejects;
        snapshot.incompatibleExecutionStrategyRejects = incompatibleExecutionStrategyRejects;
        snapshot.missingParentRejects = missingParentRejects;
        snapshot.timerCapacityRejects = timerCapacityRejects;
        snapshot.unknownTimerRejects = unknownTimerRejects;
    }

    public void loadFrom(final Snapshot snapshot) {
        Objects.requireNonNull(snapshot, "snapshot");
        if (snapshot.timerCorrelationIds.length != timerCorrelationIds.length) {
            throw new IllegalArgumentException("snapshot timer capacity mismatch");
        }
        System.arraycopy(snapshot.timerCorrelationIds, 0, timerCorrelationIds, 0, timerCorrelationIds.length);
        System.arraycopy(snapshot.timerExecutionStrategyIds, 0, timerExecutionStrategyIds, 0, timerExecutionStrategyIds.length);
        System.arraycopy(snapshot.timerActive, 0, timerActive, 0, timerActive.length);
        parentIntentDispatches = snapshot.parentIntentDispatches;
        childExecutionDispatches = snapshot.childExecutionDispatches;
        timerDispatches = snapshot.timerDispatches;
        cancelDispatches = snapshot.cancelDispatches;
        marketDataDispatches = snapshot.marketDataDispatches;
        unknownExecutionStrategyRejects = snapshot.unknownExecutionStrategyRejects;
        incompatibleExecutionStrategyRejects = snapshot.incompatibleExecutionStrategyRejects;
        missingParentRejects = snapshot.missingParentRejects;
        timerCapacityRejects = snapshot.timerCapacityRejects;
        unknownTimerRejects = snapshot.unknownTimerRejects;
    }

    private void emitUnreportedTerminalCallbacks() {
        final ParentOrderState[] states = context.parentOrderRegistry().statesForEngine();
        for (ParentOrderState state : states) {
            if (state.active() && state.terminal() && !state.terminalReported()) {
                state.markTerminalReported();
                parentTerminalEncoder.wrapAndApplyHeader(parentCallbackBuffer, 0, parentCallbackHeaderEncoder)
                    .parentOrderId(state.parentOrderId())
                    .strategyId((short) state.strategyId())
                    .executionStrategyId(state.executionStrategyId())
                    .finalCumFillQtyScaled(state.filledQtyScaled())
                    .terminalReason(reason(state.terminalReasonCode()));
                parentTerminalDecoder.wrapAndApplyHeader(parentCallbackBuffer, 0, parentCallbackHeaderDecoder);
                parentCallbackSink.onParentTerminal(parentTerminalDecoder);
            }
        }
    }

    private static ParentTerminalReason reason(final byte reasonCode) {
        return switch (reasonCode) {
            case ParentOrderState.REASON_COMPLETED -> ParentTerminalReason.COMPLETED;
            case ParentOrderState.REASON_CANCELED_BY_PARENT -> ParentTerminalReason.CANCELED_BY_PARENT;
            case ParentOrderState.REASON_EXPIRED -> ParentTerminalReason.EXPIRED;
            case ParentOrderState.REASON_RISK_REJECTED -> ParentTerminalReason.RISK_REJECTED;
            case ParentOrderState.REASON_CHILD_REJECTED -> ParentTerminalReason.CHILD_REJECTED;
            case ParentOrderState.REASON_HEDGE_FAILED -> ParentTerminalReason.HEDGE_FAILED;
            case ParentOrderState.REASON_KILL_SWITCH -> ParentTerminalReason.KILL_SWITCH;
            case ParentOrderState.REASON_EXECUTION_ABORTED -> ParentTerminalReason.EXECUTION_ABORTED;
            case ParentOrderState.REASON_CAPACITY_REJECTED -> ParentTerminalReason.CAPACITY_REJECTED;
            default -> ParentTerminalReason.EXECUTION_ABORTED;
        };
    }

    @FunctionalInterface
    public interface ParentCallbackSink {
        ParentCallbackSink NO_OP = decoder -> { };

        void onParentTerminal(ParentOrderTerminalDecoder decoder);
    }

    public static final class Snapshot {
        private final long[] timerCorrelationIds;
        private final int[] timerExecutionStrategyIds;
        private final boolean[] timerActive;
        private long parentIntentDispatches;
        private long childExecutionDispatches;
        private long timerDispatches;
        private long cancelDispatches;
        private long marketDataDispatches;
        private long unknownExecutionStrategyRejects;
        private long incompatibleExecutionStrategyRejects;
        private long missingParentRejects;
        private long timerCapacityRejects;
        private long unknownTimerRejects;

        private Snapshot(final int timerCapacity) {
            timerCorrelationIds = new long[timerCapacity];
            timerExecutionStrategyIds = new int[timerCapacity];
            timerActive = new boolean[timerCapacity];
        }

        public boolean timerActive(final int index) { return timerActive[index]; }
        public long timerCorrelationId(final int index) { return timerCorrelationIds[index]; }
        public int timerExecutionStrategyId(final int index) { return timerExecutionStrategyIds[index]; }
        public long parentIntentDispatches() { return parentIntentDispatches; }
        public long childExecutionDispatches() { return childExecutionDispatches; }
        public long timerDispatches() { return timerDispatches; }
        public long cancelDispatches() { return cancelDispatches; }
        public long marketDataDispatches() { return marketDataDispatches; }
        public long unknownExecutionStrategyRejects() { return unknownExecutionStrategyRejects; }
        public long incompatibleExecutionStrategyRejects() { return incompatibleExecutionStrategyRejects; }
        public long missingParentRejects() { return missingParentRejects; }
        public long timerCapacityRejects() { return timerCapacityRejects; }
        public long unknownTimerRejects() { return unknownTimerRejects; }
    }
}
