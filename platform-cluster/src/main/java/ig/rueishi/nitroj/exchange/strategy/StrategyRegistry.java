package ig.rueishi.nitroj.exchange.strategy;

import ig.rueishi.nitroj.exchange.common.ConfigValidationException;
import ig.rueishi.nitroj.exchange.common.Ids;
import java.util.Objects;

/**
 * Bounded cold-path registry for strategy plugins and strategy IDs.
 *
 * <p>{@link StrategyEngine} remains the runtime fan-out owner. This registry is
 * the explicit startup/configuration surface V14 tasks use to identify built-in
 * strategy IDs, validate future InventoryHedge registration, and keep strategy
 * discovery out of the hot path. It stores references in simple arrays and does
 * not allocate during lookup after construction.</p>
 */
public final class StrategyRegistry {
    public static final int STRATEGY_INVENTORY_HEDGE = Ids.STRATEGY_INVENTORY_HEDGE;
    private static final int DEFAULT_CAPACITY = 16;

    private final int[] strategyIds;
    private final Strategy[] strategies;
    private int registered;

    public StrategyRegistry() {
        this(DEFAULT_CAPACITY);
    }

    public StrategyRegistry(final int capacity) {
        if (capacity <= 0) {
            throw new IllegalArgumentException("capacity must be positive");
        }
        strategyIds = new int[capacity];
        strategies = new Strategy[capacity];
    }

    public void register(final Strategy strategy) {
        final Strategy checked = Objects.requireNonNull(strategy, "strategy");
        final int strategyId = checked.strategyId();
        if (strategyId <= 0) {
            throw new ConfigValidationException("strategy.id", "strategyId must be positive");
        }
        if (lookup(strategyId) != null) {
            throw new ConfigValidationException("strategy.id", "duplicate strategy ID: " + strategyId);
        }
        for (int i = 0; i < strategies.length; i++) {
            if (strategies[i] == null) {
                strategyIds[i] = strategyId;
                strategies[i] = checked;
                registered++;
                return;
            }
        }
        throw new ConfigValidationException("strategy.registry", "strategy registry capacity exceeded");
    }

    public Strategy lookup(final int strategyId) {
        for (int i = 0; i < strategies.length; i++) {
            if (strategyIds[i] == strategyId) {
                return strategies[i];
            }
        }
        return null;
    }

    public boolean isKnownBuiltIn(final int strategyId) {
        return strategyId == Ids.STRATEGY_MARKET_MAKING
            || strategyId == Ids.STRATEGY_ARB
            || strategyId == Ids.STRATEGY_ARB_HEDGE
            || strategyId == STRATEGY_INVENTORY_HEDGE;
    }

    public int registeredCount() {
        return registered;
    }
}
