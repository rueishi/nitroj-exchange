package ig.rueishi.nitroj.exchange.common.config;

import ig.rueishi.nitroj.exchange.common.ConfigValidationException;
import ig.rueishi.nitroj.exchange.common.ExecutionStrategyIds;
import ig.rueishi.nitroj.exchange.common.Ids;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

final class StrategyConfigValidatorTest {
    @TempDir
    Path tempDir;

    @Test
    void repositoryStrategiesToml_validatesV14DefaultsAndSorOverride() {
        final StrategyConfigValidator.ValidatedStrategy[] strategies =
            StrategyConfigValidator.validate(Path.of("../config/strategies.toml"));

        assertThat(strategies).extracting(StrategyConfigValidator.ValidatedStrategy::executionStrategyId)
            .contains(
                ExecutionStrategyIds.POST_ONLY_QUOTE,
                ExecutionStrategyIds.MULTI_LEG_CONTINGENT,
                ExecutionStrategyIds.PARALLEL_VENUE,
                ExecutionStrategyIds.SMART_ORDER_ROUTING);
    }

    @Test
    void supportedPairingsAndDefaultMappingResolve() throws Exception {
        final StrategyConfigValidator.ValidatedStrategy[] strategies = validate("""
            [[strategy]]
            id = "mm"
            type = "MarketMaking"
            instrumentId = 1
            venueId = 1

            [[strategy]]
            id = "arb"
            type = "Arb"
            instrumentId = 1
            venueIds = [1, 2]

            [[strategy]]
            id = "one-shot"
            type = "OneShot"
            instrumentId = 1
            venueId = 1

            [[strategy]]
            id = "hedge-parallel"
            type = "InventoryHedge"
            instrument = "BTC-USD-COINBASE"
            venueSetId = 7
            thresholdMode = "base_quantity"
            thresholdValue = 1.0
            safeBandValue = 0.5
            cooldownMicros = 1000

            [[strategy]]
            id = "hedge-sor"
            type = "InventoryHedge"
            instrument = "BTC-USD-COINBASE"
            venueSetId = 7
            thresholdMode = "base_quantity"
            thresholdValue = 1.0
            safeBandValue = 0.5
            cooldownMicros = 1000
            executionStrategy = "SOR"
            """);

        assertThat(strategies[0].executionStrategyId()).isEqualTo(ExecutionStrategyIds.POST_ONLY_QUOTE);
        assertThat(strategies[1].executionStrategyId()).isEqualTo(ExecutionStrategyIds.MULTI_LEG_CONTINGENT);
        assertThat(strategies[2].executionStrategyId()).isEqualTo(ExecutionStrategyIds.IMMEDIATE_LIMIT);
        assertThat(strategies[3].executionStrategyId()).isEqualTo(ExecutionStrategyIds.PARALLEL_VENUE);
        assertThat(strategies[4].executionStrategyId()).isEqualTo(ExecutionStrategyIds.SMART_ORDER_ROUTING);
    }

    @Test
    void unsupportedPairingsFailStartupWithClearErrors() {
        assertIncompatible("MarketMaking", "ParallelVenue", "MarketMaking");
        assertIncompatible("MarketMaking", "SOR", "MarketMaking");
        assertIncompatible("MarketMaking", "MultiLegContingent", "MarketMaking");
        assertIncompatible("MarketMaking", "ImmediateLimit", "MarketMaking");
        assertIncompatible("Arb", "ParallelVenue", "Arb");
        assertIncompatible("Arb", "SOR", "Arb");
        assertIncompatible("Arb", "PostOnlyQuote", "Arb");
        assertIncompatible("Arb", "ImmediateLimit", "Arb");
        assertIncompatible("InventoryHedge", "PostOnlyQuote", "InventoryHedge");
        assertIncompatible("InventoryHedge", "MultiLegContingent", "InventoryHedge");
        assertIncompatible("OneShot", "SOR", "OneShot");
    }

    @Test
    void overrideResolutionAndUndeclaredInstrumentVenueValidation() throws Exception {
        final StrategyConfigValidator.ValidatedStrategy[] strategies = validate("""
            [[strategy]]
            id = "mm"
            type = "MarketMaking"
            instrumentId = 1
            venueId = 1
            executionStrategy = "PostOnlyQuote"

              [[strategy.executionOverride]]
              instrumentId = 1
              venueId = 1
              executionStrategy = "PostOnlyQuote"
            """);

        assertThat(strategies[0].overrides()).hasSize(1);
        assertThat(strategies[0].overrides()[0].executionStrategyId()).isEqualTo(ExecutionStrategyIds.POST_ONLY_QUOTE);

        assertThatThrownBy(() -> validate("""
            [[strategy]]
            id = "mm"
            type = "MarketMaking"
            instrumentId = 1
            venueId = 1
              [[strategy.executionOverride]]
              instrumentId = 99
              executionStrategy = "PostOnlyQuote"
            """))
            .isInstanceOf(ConfigValidationException.class)
            .hasMessageContaining("undeclared instrumentId 99");

        assertThatThrownBy(() -> validate("""
            [[strategy]]
            id = "mm"
            type = "MarketMaking"
            instrumentId = 1
            venueId = 1
              [[strategy.executionOverride]]
              venueId = 2
              executionStrategy = "PostOnlyQuote"
            """))
            .isInstanceOf(ConfigValidationException.class)
            .hasMessageContaining("undeclared venueId 2");
    }

    @Test
    void missingRequiredHedgeConfigFailsWithFieldPath() {
        assertThatThrownBy(() -> validate("""
            [[strategy]]
            id = "hedge"
            type = "InventoryHedge"
            instrument = "BTC-USD-COINBASE"
            venueSetId = 7
            thresholdValue = 1.0
            safeBandValue = 0.5
            cooldownMicros = 1000
            """))
            .isInstanceOf(ConfigValidationException.class)
            .hasMessageContaining("thresholdMode");

        assertThatThrownBy(() -> validate("""
            [[strategy]]
            id = "hedge"
            type = "InventoryHedge"
            instrument = "BTC-USD-COINBASE"
            venueSetId = 7
            thresholdMode = "base_quantity"
            thresholdValue = 1.0
            cooldownMicros = 1000
            """))
            .isInstanceOf(ConfigValidationException.class)
            .hasMessageContaining("safeBandValue");

        assertThatThrownBy(() -> validate("""
            [[strategy]]
            id = "hedge"
            type = "InventoryHedge"
            instrument = "BTC-USD-COINBASE"
            venueSetId = 7
            thresholdMode = "base_quantity"
            thresholdValue = 1.0
            safeBandValue = 0.5
            """))
            .isInstanceOf(ConfigValidationException.class)
            .hasMessageContaining("cooldownMicros");
    }

    @Test
    void canonicalExecutionStrategyIdsHaveValidationEvidence() throws Exception {
        assertThat(ExecutionStrategyIds.parseCanonical("ImmediateLimit", "x")).isEqualTo(ExecutionStrategyIds.IMMEDIATE_LIMIT);
        assertThat(ExecutionStrategyIds.parseCanonical("PostOnlyQuote", "x")).isEqualTo(ExecutionStrategyIds.POST_ONLY_QUOTE);
        assertThat(ExecutionStrategyIds.parseCanonical("MultiLegContingent", "x")).isEqualTo(ExecutionStrategyIds.MULTI_LEG_CONTINGENT);
        assertThat(ExecutionStrategyIds.parseCanonical("ParallelVenue", "x")).isEqualTo(ExecutionStrategyIds.PARALLEL_VENUE);
        assertThat(ExecutionStrategyIds.parseCanonical("SOR", "x")).isEqualTo(ExecutionStrategyIds.SMART_ORDER_ROUTING);

        final StrategyConfigValidator.ValidatedStrategy[] strategies = validate("""
            [[strategy]]
            id = "one-shot"
            type = "OneShot"
            instrumentId = 1
            venueId = 1
            executionStrategy = "ImmediateLimit"

            [[strategy]]
            id = "mm"
            type = "MarketMaking"
            instrumentId = 1
            venueId = 1
            executionStrategy = "PostOnlyQuote"

            [[strategy]]
            id = "arb"
            type = "Arb"
            instrumentId = 1
            venueIds = [1, 2]
            executionStrategy = "MultiLegContingent"

            [[strategy]]
            id = "hedge-parallel"
            type = "InventoryHedge"
            instrument = "BTC-USD-COINBASE"
            venueSetId = 7
            thresholdMode = "base_quantity"
            thresholdValue = 1.0
            safeBandValue = 0.5
            cooldownMicros = 1000
            executionStrategy = "ParallelVenue"

            [[strategy]]
            id = "hedge-sor"
            type = "InventoryHedge"
            instrument = "BTC-USD-COINBASE"
            venueSetId = 7
            thresholdMode = "base_quantity"
            thresholdValue = 1.0
            safeBandValue = 0.5
            cooldownMicros = 1000
            executionStrategy = "SOR"
            """);

        assertThat(strategies).hasSize(5);
    }

    @Test
    void numericCompatibilityHelpersMatchV14Matrix() {
        assertThat(ExecutionStrategyIds.defaultForTradingStrategy(Ids.STRATEGY_INVENTORY_HEDGE))
            .isEqualTo(ExecutionStrategyIds.PARALLEL_VENUE);
        assertThat(ExecutionStrategyIds.defaultForTradingStrategy(Ids.STRATEGY_ARB_HEDGE))
            .isEqualTo(ExecutionStrategyIds.IMMEDIATE_LIMIT);
        assertThat(ExecutionStrategyIds.isCompatible(Ids.STRATEGY_INVENTORY_HEDGE, ExecutionStrategyIds.SMART_ORDER_ROUTING))
            .isTrue();
        assertThat(ExecutionStrategyIds.isCompatible(Ids.STRATEGY_MARKET_MAKING, ExecutionStrategyIds.SMART_ORDER_ROUTING))
            .isFalse();
    }

    private void assertIncompatible(final String type, final String executionStrategy, final String strategyName) {
        assertThatThrownBy(() -> validate(strategyToml(type, executionStrategy)))
            .isInstanceOf(ConfigValidationException.class)
            .hasMessageContaining("not compatible")
            .hasMessageContaining(strategyName)
            .hasMessageContaining(executionStrategy);
    }

    private static String strategyToml(final String type, final String executionStrategy) {
        final String common = switch (type) {
            case "MarketMaking" -> """
                id = "mm"
                type = "MarketMaking"
                instrumentId = 1
                venueId = 1
                """;
            case "Arb" -> """
                id = "arb"
                type = "Arb"
                instrumentId = 1
                venueIds = [1, 2]
                """;
            case "OneShot" -> """
                id = "one-shot"
                type = "OneShot"
                instrumentId = 1
                venueId = 1
                """;
            case "InventoryHedge" -> """
                id = "hedge"
                type = "InventoryHedge"
                instrument = "BTC-USD-COINBASE"
                venueSetId = 7
                thresholdMode = "base_quantity"
                thresholdValue = 1.0
                safeBandValue = 0.5
                cooldownMicros = 1000
                """;
            default -> throw new IllegalArgumentException(type);
        };
        return "[[strategy]]\n" + common + "executionStrategy = \"" + executionStrategy + "\"\n";
    }

    private StrategyConfigValidator.ValidatedStrategy[] validate(final String toml) throws Exception {
        final Path path = tempDir.resolve("strategies.toml");
        Files.writeString(path, toml);
        return StrategyConfigValidator.validate(path);
    }
}
