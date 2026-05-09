package ig.rueishi.nitroj.exchange.common.config;

import com.electronwill.nightconfig.core.Config;
import com.electronwill.nightconfig.toml.TomlParser;
import ig.rueishi.nitroj.exchange.common.ConfigValidationException;
import ig.rueishi.nitroj.exchange.common.ExecutionStrategyIds;
import ig.rueishi.nitroj.exchange.common.ExecutionStrategyOverrideConfig;
import ig.rueishi.nitroj.exchange.common.Ids;
import java.io.IOException;
import java.io.Reader;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

/**
 * Cold-start validator for standalone strategy-to-execution configuration.
 *
 * <p>The validator owns clear operator-facing errors for `strategies.toml`.
 * Runtime dispatch remains primitive: the returned records contain canonical
 * numeric IDs and compatibility is checked here before any strategy or execution
 * plugin reaches the cluster hot path.</p>
 */
public final class StrategyConfigValidator {
    private StrategyConfigValidator() {
    }

    public static ValidatedStrategy[] validate(final Path path) {
        final Config config;
        try (Reader reader = Files.newBufferedReader(path)) {
            config = new TomlParser().parse(reader);
        } catch (IOException ex) {
            throw new ConfigValidationException(path.toString(), "unable to read strategy config: " + ex.getMessage());
        } catch (RuntimeException ex) {
            throw new ConfigValidationException(path.toString(), "invalid TOML: " + ex.getMessage());
        }
        return validate(config);
    }

    public static ValidatedStrategy[] validate(final Config config) {
        final Object value = config.get("strategy");
        if (!(value instanceof List<?> strategies)) {
            throw new ConfigValidationException("strategy", "expected array of strategy tables");
        }
        final ValidatedStrategy[] validated = new ValidatedStrategy[strategies.size()];
        for (int i = 0; i < strategies.size(); i++) {
            if (!(strategies.get(i) instanceof Config strategy)) {
                throw new ConfigValidationException("strategy[" + i + "]", "expected strategy table");
            }
            validated[i] = validateStrategy(strategy, "strategy[" + i + "]");
        }
        return validated;
    }

    public static void validateCompatibility(
        final int tradingStrategyId,
        final int executionStrategyId,
        final String fieldPath) {
        if (!ExecutionStrategyIds.isCompatible(tradingStrategyId, executionStrategyId)) {
            throw new ConfigValidationException(fieldPath,
                "execution strategy '" + ExecutionStrategyIds.nameOf(executionStrategyId)
                    + "' is not compatible with trading strategy '"
                    + tradingStrategyName(tradingStrategyId) + "'");
        }
    }

    private static ValidatedStrategy validateStrategy(final Config strategy, final String path) {
        final String id = requiredString(strategy, "id", path + ".id");
        final String type = requiredString(strategy, "type", path + ".type");
        final int tradingStrategyId = tradingStrategyId(type, path + ".type");
        final String executionName = optionalString(strategy, "executionStrategy");
        final int executionStrategyId = executionName == null
            ? ExecutionStrategyIds.defaultForTradingStrategy(tradingStrategyId)
            : ExecutionStrategyIds.parseCanonical(executionName, path + ".executionStrategy");
        validateCompatibility(tradingStrategyId, executionStrategyId, path + ".executionStrategy");
        validateTypeSpecific(strategy, type, path);
        final ExecutionStrategyOverrideConfig[] overrides = validateOverrides(
            strategy, path + ".executionOverride", tradingStrategyId, declaredInstrumentId(strategy),
            declaredVenueIds(strategy));
        return new ValidatedStrategy(id, type, tradingStrategyId, executionStrategyId, overrides);
    }

    private static void validateTypeSpecific(final Config strategy, final String type, final String path) {
        if ("InventoryHedge".equals(type)) {
            requiredString(strategy, "instrument", path + ".instrument");
            requiredInt(strategy, "venueSetId", path + ".venueSetId");
            requiredString(strategy, "thresholdMode", path + ".thresholdMode");
            requiredNumber(strategy, "thresholdValue", path + ".thresholdValue");
            requiredNumber(strategy, "safeBandValue", path + ".safeBandValue");
            requiredLong(strategy, "cooldownMicros", path + ".cooldownMicros");
        }
    }

    private static ExecutionStrategyOverrideConfig[] validateOverrides(
        final Config strategy,
        final String path,
        final int tradingStrategyId,
        final int declaredInstrumentId,
        final int[] declaredVenueIds) {
        final Object value = strategy.get("executionOverride");
        if (value == null) {
            return new ExecutionStrategyOverrideConfig[0];
        }
        if (!(value instanceof List<?> list)) {
            throw new ConfigValidationException(path, "expected array of override tables");
        }
        final ExecutionStrategyOverrideConfig[] overrides = new ExecutionStrategyOverrideConfig[list.size()];
        for (int i = 0; i < list.size(); i++) {
            if (!(list.get(i) instanceof Config override)) {
                throw new ConfigValidationException(path, "expected override table at index " + i);
            }
            final String indexed = path + "[" + i + "]";
            final int instrumentId = optionalInt(override, "instrumentId", ExecutionStrategyOverrideConfig.ANY_INSTRUMENT);
            final int venueId = optionalInt(override, "venueId", ExecutionStrategyOverrideConfig.ANY_VENUE);
            if (instrumentId == ExecutionStrategyOverrideConfig.ANY_INSTRUMENT
                && venueId == ExecutionStrategyOverrideConfig.ANY_VENUE) {
                throw new ConfigValidationException(indexed, "override must set instrumentId, venueId, or both");
            }
            if (instrumentId != ExecutionStrategyOverrideConfig.ANY_INSTRUMENT
                && declaredInstrumentId != ExecutionStrategyOverrideConfig.ANY_INSTRUMENT
                && instrumentId != declaredInstrumentId) {
                throw new ConfigValidationException(indexed + ".instrumentId",
                    "override references undeclared instrumentId " + instrumentId);
            }
            if (venueId != ExecutionStrategyOverrideConfig.ANY_VENUE
                && declaredVenueIds.length > 0
                && !contains(declaredVenueIds, venueId)) {
                throw new ConfigValidationException(indexed + ".venueId",
                    "override references undeclared venueId " + venueId);
            }
            final int executionStrategyId = ExecutionStrategyIds.parseCanonical(
                requiredString(override, "executionStrategy", indexed + ".executionStrategy"),
                indexed + ".executionStrategy");
            validateCompatibility(tradingStrategyId, executionStrategyId, indexed + ".executionStrategy");
            overrides[i] = new ExecutionStrategyOverrideConfig(instrumentId, venueId, executionStrategyId);
        }
        return overrides;
    }

    private static int tradingStrategyId(final String type, final String path) {
        return switch (type) {
            case "MarketMaking" -> Ids.STRATEGY_MARKET_MAKING;
            case "Arb" -> Ids.STRATEGY_ARB;
            case "OneShot" -> Ids.STRATEGY_ARB_HEDGE;
            case "InventoryHedge" -> Ids.STRATEGY_INVENTORY_HEDGE;
            default -> throw new ConfigValidationException(path, "unknown strategy type '" + type + "'");
        };
    }

    private static String tradingStrategyName(final int tradingStrategyId) {
        return switch (tradingStrategyId) {
            case Ids.STRATEGY_MARKET_MAKING -> "MarketMaking";
            case Ids.STRATEGY_ARB -> "Arb";
            case Ids.STRATEGY_ARB_HEDGE -> "OneShot";
            case Ids.STRATEGY_INVENTORY_HEDGE -> "InventoryHedge";
            default -> "ID " + tradingStrategyId;
        };
    }

    private static int declaredInstrumentId(final Config strategy) {
        return optionalInt(strategy, "instrumentId", ExecutionStrategyOverrideConfig.ANY_INSTRUMENT);
    }

    private static int[] declaredVenueIds(final Config strategy) {
        final Integer venueId = optionalInteger(strategy, "venueId");
        if (venueId != null) {
            return new int[] {venueId};
        }
        final Object venueIds = strategy.get("venueIds");
        if (venueIds instanceof List<?> list) {
            final int[] values = new int[list.size()];
            for (int i = 0; i < list.size(); i++) {
                values[i] = asInt(list.get(i), "strategy.venueIds[" + i + "]");
            }
            return values;
        }
        return new int[0];
    }

    private static boolean contains(final int[] values, final int target) {
        for (int value : values) {
            if (value == target) {
                return true;
            }
        }
        return false;
    }

    private static String requiredString(final Config config, final String key, final String path) {
        final Object value = config.get(key);
        if (!(value instanceof String string) || string.isBlank()) {
            throw new ConfigValidationException(path, "expected non-blank string");
        }
        return string;
    }

    private static String optionalString(final Config config, final String key) {
        final Object value = config.get(key);
        if (value == null) {
            return null;
        }
        if (!(value instanceof String string) || string.isBlank()) {
            throw new ConfigValidationException(key, "expected non-blank string");
        }
        return string;
    }

    private static int requiredInt(final Config config, final String key, final String path) {
        final Object value = config.get(key);
        return asInt(value, path);
    }

    private static long requiredLong(final Config config, final String key, final String path) {
        final Object value = config.get(key);
        if (value instanceof Number number) {
            return number.longValue();
        }
        throw new ConfigValidationException(path, "expected integer");
    }

    private static void requiredNumber(final Config config, final String key, final String path) {
        if (!(config.get(key) instanceof Number)) {
            throw new ConfigValidationException(path, "expected number");
        }
    }

    private static int optionalInt(final Config config, final String key, final int defaultValue) {
        final Integer value = optionalInteger(config, key);
        return value == null ? defaultValue : value;
    }

    private static Integer optionalInteger(final Config config, final String key) {
        final Object value = config.get(key);
        return value == null ? null : asInt(value, key);
    }

    private static int asInt(final Object value, final String path) {
        if (value instanceof Number number) {
            return number.intValue();
        }
        throw new ConfigValidationException(path, "expected integer");
    }

    public record ValidatedStrategy(
        String id,
        String type,
        int tradingStrategyId,
        int executionStrategyId,
        ExecutionStrategyOverrideConfig[] overrides) {
    }
}
