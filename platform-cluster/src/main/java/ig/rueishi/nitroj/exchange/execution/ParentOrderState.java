package ig.rueishi.nitroj.exchange.execution;

/**
 * Mutable cluster-thread parent-order lifecycle state.
 *
 * <p>Instances are owned by {@link ParentOrderRegistry} and preallocated during
 * registry construction. The class deliberately stores primitive fields only so
 * parent lifecycle transitions, replay, and snapshot copies do not allocate on
 * the steady-state execution path.</p>
 */
public final class ParentOrderState {
    public static final byte PENDING = 1;
    public static final byte WORKING = 2;
    public static final byte PARTIALLY_FILLED = 3;
    public static final byte HEDGING = 4;
    public static final byte CANCEL_PENDING = 5;
    public static final byte DONE = 6;
    public static final byte FAILED = 7;
    public static final byte CANCELED = 8;
    public static final byte EXPIRED = 9;

    public static final byte REASON_NONE = 0;
    public static final byte REASON_COMPLETED = 1;
    public static final byte REASON_CANCELED_BY_PARENT = 2;
    public static final byte REASON_EXPIRED = 3;
    public static final byte REASON_RISK_REJECTED = 4;
    public static final byte REASON_CHILD_REJECTED = 5;
    public static final byte REASON_HEDGE_FAILED = 6;
    public static final byte REASON_KILL_SWITCH = 7;
    public static final byte REASON_EXECUTION_ABORTED = 8;
    public static final byte REASON_CAPACITY_REJECTED = 9;
    public static final byte REASON_LEG_TIMER_RESIDUAL_CANCELED = 10;
    public static final byte REASON_ALL_CHILDREN_REJECTED = 11;
    public static final byte REASON_RESLICE_FAILED = 12;

    long parentOrderId;
    int strategyId;
    int executionStrategyId;
    long requestedQtyScaled;
    long filledQtyScaled;
    long remainingQtyScaled;
    long averageFillPriceScaled;
    byte status;
    byte terminalReasonCode;
    long lastTransitionClusterTime;
    long primaryTimerCorrelationId;
    boolean active;
    boolean terminalReported;

    ParentOrderState() {
        reset();
    }

    void init(
        final long parentOrderId,
        final int strategyId,
        final int executionStrategyId,
        final long requestedQtyScaled,
        final long clusterTimeMicros) {
        this.parentOrderId = parentOrderId;
        this.strategyId = strategyId;
        this.executionStrategyId = executionStrategyId;
        this.requestedQtyScaled = requestedQtyScaled;
        this.filledQtyScaled = 0L;
        this.remainingQtyScaled = requestedQtyScaled;
        this.averageFillPriceScaled = 0L;
        this.status = PENDING;
        this.terminalReasonCode = REASON_NONE;
        this.lastTransitionClusterTime = clusterTimeMicros;
        this.primaryTimerCorrelationId = 0L;
        this.active = true;
        this.terminalReported = false;
    }

    void reset() {
        parentOrderId = 0L;
        strategyId = 0;
        executionStrategyId = 0;
        requestedQtyScaled = 0L;
        filledQtyScaled = 0L;
        remainingQtyScaled = 0L;
        averageFillPriceScaled = 0L;
        status = PENDING;
        terminalReasonCode = REASON_NONE;
        lastTransitionClusterTime = 0L;
        primaryTimerCorrelationId = 0L;
        active = false;
        terminalReported = false;
    }

    void transition(final byte status, final byte reasonCode, final long clusterTimeMicros) {
        this.status = status;
        this.terminalReasonCode = reasonCode;
        this.lastTransitionClusterTime = clusterTimeMicros;
    }

    void fill(final long filledQtyScaled, final long remainingQtyScaled, final long averageFillPriceScaled) {
        this.filledQtyScaled = filledQtyScaled;
        this.remainingQtyScaled = remainingQtyScaled;
        this.averageFillPriceScaled = averageFillPriceScaled;
    }

    public long parentOrderId() {
        return parentOrderId;
    }

    public int strategyId() {
        return strategyId;
    }

    public int executionStrategyId() {
        return executionStrategyId;
    }

    public long requestedQtyScaled() {
        return requestedQtyScaled;
    }

    public long filledQtyScaled() {
        return filledQtyScaled;
    }

    public long remainingQtyScaled() {
        return remainingQtyScaled;
    }

    public long averageFillPriceScaled() {
        return averageFillPriceScaled;
    }

    public byte status() {
        return status;
    }

    public byte terminalReasonCode() {
        return terminalReasonCode;
    }

    public long lastTransitionClusterTime() {
        return lastTransitionClusterTime;
    }

    public long primaryTimerCorrelationId() {
        return primaryTimerCorrelationId;
    }

    public boolean active() {
        return active;
    }

    public boolean terminal() {
        return status == DONE || status == FAILED || status == CANCELED || status == EXPIRED;
    }

    boolean terminalReported() {
        return terminalReported;
    }

    void markTerminalReported() {
        terminalReported = true;
    }
}
