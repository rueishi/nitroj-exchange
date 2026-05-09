package ig.rueishi.nitroj.exchange.execution;

import java.util.Objects;

/**
 * Startup-populated registry for execution strategy plugins and compatibility.
 *
 * <p>IDs are array indexes so hot-path dispatch never uses String keys or
 * general-purpose maps. Registration and compatibility configuration are cold
 * startup operations. Runtime lookups are primitive array reads.</p>
 */
public final class ExecutionStrategyRegistry {
    public static final int DEFAULT_EXECUTION_STRATEGY_CAPACITY = 128;
    public static final int DEFAULT_TRADING_STRATEGY_CAPACITY = 256;

    private final ExecutionStrategy[] strategies;
    private final boolean[][] compatibility;
    private int registeredCount;

    public ExecutionStrategyRegistry() {
        this(DEFAULT_EXECUTION_STRATEGY_CAPACITY, DEFAULT_TRADING_STRATEGY_CAPACITY);
    }

    public ExecutionStrategyRegistry(final int executionStrategyCapacity, final int tradingStrategyCapacity) {
        if (executionStrategyCapacity <= 0) {
            throw new IllegalArgumentException("executionStrategyCapacity must be positive");
        }
        if (tradingStrategyCapacity <= 0) {
            throw new IllegalArgumentException("tradingStrategyCapacity must be positive");
        }
        strategies = new ExecutionStrategy[executionStrategyCapacity];
        compatibility = new boolean[tradingStrategyCapacity][executionStrategyCapacity];
    }

    public void register(final ExecutionStrategy strategy) {
        Objects.requireNonNull(strategy, "strategy");
        final int id = strategy.executionStrategyId();
        validateExecutionStrategyId(id);
        if (strategies[id] != null) {
            throw new IllegalArgumentException("duplicate execution strategy id: " + id);
        }
        strategies[id] = strategy;
        registeredCount++;
    }

    public void allowCompatibility(final int tradingStrategyId, final int executionStrategyId) {
        validateTradingStrategyId(tradingStrategyId);
        validateExecutionStrategyId(executionStrategyId);
        compatibility[tradingStrategyId][executionStrategyId] = true;
    }

    public ExecutionStrategy lookup(final int executionStrategyId) {
        if (executionStrategyId < 0 || executionStrategyId >= strategies.length) {
            return null;
        }
        return strategies[executionStrategyId];
    }

    public boolean isCompatible(final int tradingStrategyId, final int executionStrategyId) {
        return tradingStrategyId >= 0
            && tradingStrategyId < compatibility.length
            && executionStrategyId >= 0
            && executionStrategyId < strategies.length
            && compatibility[tradingStrategyId][executionStrategyId];
    }

    public int registeredCount() {
        return registeredCount;
    }

    public int executionStrategyCapacity() {
        return strategies.length;
    }

    public void allowV14DefaultCompatibility() {
        allowCompatibility(ig.rueishi.nitroj.exchange.common.Ids.STRATEGY_INVENTORY_HEDGE,
            ig.rueishi.nitroj.exchange.common.ExecutionStrategyIds.PARALLEL_VENUE);
        allowCompatibility(ig.rueishi.nitroj.exchange.common.Ids.STRATEGY_INVENTORY_HEDGE,
            ig.rueishi.nitroj.exchange.common.ExecutionStrategyIds.SMART_ORDER_ROUTING);
    }

    private void validateTradingStrategyId(final int id) {
        if (id < 0 || id >= compatibility.length) {
            throw new IllegalArgumentException("trading strategy id out of range: " + id);
        }
    }

    private void validateExecutionStrategyId(final int id) {
        if (id < 0 || id >= strategies.length) {
            throw new IllegalArgumentException("execution strategy id out of range: " + id);
        }
    }
}
