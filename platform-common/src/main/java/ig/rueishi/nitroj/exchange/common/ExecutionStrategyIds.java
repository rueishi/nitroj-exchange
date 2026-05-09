package ig.rueishi.nitroj.exchange.common;

/**
 * Canonical V13 execution-strategy identifiers used by startup config.
 */
public final class ExecutionStrategyIds {
    public static final int IMMEDIATE_LIMIT = 1;
    public static final int POST_ONLY_QUOTE = 2;
    public static final int MULTI_LEG_CONTINGENT = 3;
    public static final int PARALLEL_VENUE = 4;
    public static final int SMART_ORDER_ROUTING = 5;

    private ExecutionStrategyIds() {
    }

    public static int defaultForTradingStrategy(final int tradingStrategyId) {
        return switch (tradingStrategyId) {
            case Ids.STRATEGY_MARKET_MAKING -> POST_ONLY_QUOTE;
            case Ids.STRATEGY_ARB -> MULTI_LEG_CONTINGENT;
            case Ids.STRATEGY_ARB_HEDGE -> IMMEDIATE_LIMIT;
            case Ids.STRATEGY_INVENTORY_HEDGE -> PARALLEL_VENUE;
            default -> throw new ConfigValidationException("strategy.executionStrategy",
                "unknown trading strategy ID: " + tradingStrategyId);
        };
    }

    public static int parseCanonical(final String value, final String fieldPath) {
        if (value == null || value.isBlank()) {
            throw new ConfigValidationException(fieldPath, "execution strategy ID must not be blank");
        }
        return switch (value) {
            case "ImmediateLimit" -> IMMEDIATE_LIMIT;
            case "PostOnlyQuote" -> POST_ONLY_QUOTE;
            case "MultiLegContingent" -> MULTI_LEG_CONTINGENT;
            case "ParallelVenue" -> PARALLEL_VENUE;
            case "SOR", "SmartOrderRouting" -> SMART_ORDER_ROUTING;
            default -> throw new ConfigValidationException(fieldPath,
                "unknown execution strategy ID '" + value + "'");
        };
    }

    public static String nameOf(final int executionStrategyId) {
        return switch (executionStrategyId) {
            case IMMEDIATE_LIMIT -> "ImmediateLimit";
            case POST_ONLY_QUOTE -> "PostOnlyQuote";
            case MULTI_LEG_CONTINGENT -> "MultiLegContingent";
            case PARALLEL_VENUE -> "ParallelVenue";
            case SMART_ORDER_ROUTING -> "SOR";
            default -> throw new IllegalArgumentException("unknown execution strategy ID: " + executionStrategyId);
        };
    }

    public static boolean isCompatible(final int tradingStrategyId, final int executionStrategyId) {
        return switch (tradingStrategyId) {
            case Ids.STRATEGY_MARKET_MAKING -> executionStrategyId == POST_ONLY_QUOTE;
            case Ids.STRATEGY_ARB -> executionStrategyId == MULTI_LEG_CONTINGENT;
            case Ids.STRATEGY_ARB_HEDGE -> executionStrategyId == IMMEDIATE_LIMIT;
            case Ids.STRATEGY_INVENTORY_HEDGE -> executionStrategyId == PARALLEL_VENUE
                || executionStrategyId == SMART_ORDER_ROUTING;
            default -> false;
        };
    }
}
