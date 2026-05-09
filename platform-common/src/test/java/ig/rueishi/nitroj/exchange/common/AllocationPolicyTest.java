package ig.rueishi.nitroj.exchange.common;

import org.junit.jupiter.api.Test;

import java.util.EnumSet;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Tests the executable hot/cold allocation boundary and benchmark ownership gate.
 */
final class AllocationPolicyTest {
    private static final Set<String> REQUIRED_IMPLEMENTED_BENCHMARK_OWNERS = Set.of(
        "BookMutationBenchmark",
        "BinanceL2NormalizerBenchmark",
        "BinanceOrderEntryPolicyBenchmark",
        "ExecutionReportBenchmark",
        "ExecutionStrategyEngineBenchmark",
        "FixL2NormalizerBenchmark",
        "FixL3NormalizerBenchmark",
        "GatewayHandoffBenchmark",
        "InventoryHedgeStrategyBenchmark",
        "OrderEncodingBenchmark",
        "OrderManagerBenchmark",
        "OwnLiquidityBenchmark",
        "ParentOrderRegistryBenchmark",
        "ParallelVenueExecutionBenchmark",
        "RiskDecisionBenchmark",
        "SbeCodecBenchmark",
        "SmartOrderRoutingExecutionBenchmark",
        "StrategyTickBenchmark"
    );

    private static final Set<String> REQUIRED_PLACEHOLDER_OWNERS = Set.of();

    @Test
    void positive_knownHotAndColdPathsClassifyCorrectly() {
        assertThat(AllocationPolicy.classifyHot(AllocationPolicy.HotPath.FIX_L2_PARSING))
            .isEqualTo(AllocationPolicy.PathClass.HOT_PATH);
        assertThat(AllocationPolicy.classifyCold(AllocationPolicy.ColdPath.CONFIG_PARSING))
            .isEqualTo(AllocationPolicy.PathClass.COLD_PATH);
        assertThat(AllocationPolicy.classify("StrategyEngine dispatch")).isEqualTo(AllocationPolicy.PathClass.HOT_PATH);
        assertThat(AllocationPolicy.classify("rest-polling")).isEqualTo(AllocationPolicy.PathClass.COLD_PATH);
    }

    @Test
    void negative_unknownOrBlankNamesFailClearly() {
        assertThatThrownBy(() -> AllocationPolicy.classify("unknown path"))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("Unknown allocation policy path");
        assertThatThrownBy(() -> AllocationPolicy.classify(" "))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("must not be blank");
    }

    @Test
    void edge_mixedCaseAliasesNormalizeToPolicyNames() {
        assertThat(AllocationPolicy.classify("Fix L2 Parsing")).isEqualTo(AllocationPolicy.PathClass.HOT_PATH);
        assertThat(AllocationPolicy.classify("VenueL2Book mutation")).isEqualTo(AllocationPolicy.PathClass.HOT_PATH);
        assertThat(AllocationPolicy.classify("STARTUP-validation")).isEqualTo(AllocationPolicy.PathClass.COLD_PATH);
    }

    @Test
    void exception_nullInputFailsClearly() {
        assertThatThrownBy(() -> AllocationPolicy.classify(null))
            .isInstanceOf(NullPointerException.class)
            .hasMessageContaining("name");
        assertThatThrownBy(() -> AllocationPolicy.classifyHot(null))
            .isInstanceOf(NullPointerException.class)
            .hasMessageContaining("path");
    }

    @Test
    void failure_documentationDrift_requiredCategoriesRemainRepresented() {
        assertThat(EnumSet.allOf(AllocationPolicy.HotPath.class))
            .contains(
                AllocationPolicy.HotPath.FIX_L2_PARSING,
                AllocationPolicy.HotPath.FIX_L3_PARSING,
                AllocationPolicy.HotPath.EXECUTION_REPORT_PARSING,
                AllocationPolicy.HotPath.SBE_ENCODE_DECODE,
                AllocationPolicy.HotPath.GATEWAY_DISRUPTOR_HANDOFF,
                AllocationPolicy.HotPath.AERON_PUBLICATION_HANDOFF,
                AllocationPolicy.HotPath.VENUE_L2_BOOK_MUTATION,
                AllocationPolicy.HotPath.VENUE_L3_BOOK_MUTATION,
                AllocationPolicy.HotPath.L3_TO_L2_DERIVATION,
                AllocationPolicy.HotPath.CONSOLIDATED_L2_UPDATE_QUERY,
                AllocationPolicy.HotPath.OWN_ORDER_OVERLAY_UPDATE_QUERY,
                AllocationPolicy.HotPath.EXTERNAL_LIQUIDITY_QUERY,
                AllocationPolicy.HotPath.ORDER_STATE_TRANSITION,
                AllocationPolicy.HotPath.RISK_DECISION,
                AllocationPolicy.HotPath.STRATEGY_ENGINE_DISPATCH,
                AllocationPolicy.HotPath.MARKET_MAKING_STRATEGY_TICK,
                AllocationPolicy.HotPath.ARB_STRATEGY_TICK,
                AllocationPolicy.HotPath.PARENT_ORDER_REGISTRY_UPDATE_QUERY,
                AllocationPolicy.HotPath.PARENT_ACTIVE_CHILD_LOOKUP,
                AllocationPolicy.HotPath.EXECUTION_STRATEGY_ENGINE_DISPATCH,
                AllocationPolicy.HotPath.INVENTORY_HEDGE_MARKET_DATA_TICK,
                AllocationPolicy.HotPath.INVENTORY_HEDGE_PORTFOLIO_UPDATE,
                AllocationPolicy.HotPath.INVENTORY_HEDGE_PARENT_INTENT_EMISSION,
                AllocationPolicy.HotPath.PARALLEL_VENUE_PARENT_INTENT,
                AllocationPolicy.HotPath.PARALLEL_VENUE_SLICE_PLAN,
                AllocationPolicy.HotPath.PARALLEL_VENUE_CHILD_EXECUTION,
                AllocationPolicy.HotPath.PARALLEL_VENUE_TIMER,
                AllocationPolicy.HotPath.SMART_ORDER_ROUTING_PARENT_INTENT,
                AllocationPolicy.HotPath.SMART_ORDER_ROUTING_SLICE_PLAN,
                AllocationPolicy.HotPath.SMART_ORDER_ROUTING_RESLICE,
                AllocationPolicy.HotPath.SMART_ORDER_ROUTING_CHILD_EXECUTION,
                AllocationPolicy.HotPath.SMART_ORDER_ROUTING_TIMER,
                AllocationPolicy.HotPath.MIXED_PRECISION_OWN_ORDER_OVERLAY_QUERY,
                AllocationPolicy.HotPath.BINANCE_L2_NORMALIZER_SBE_EVENT_PRODUCTION,
                AllocationPolicy.HotPath.BINANCE_ORDER_ENTRY_POLICY_ENRICHMENT,
                AllocationPolicy.HotPath.ORDER_COMMAND_ENCODING);
        assertThat(EnumSet.allOf(AllocationPolicy.ColdPath.class))
            .contains(
                AllocationPolicy.ColdPath.CONFIG_PARSING,
                AllocationPolicy.ColdPath.STARTUP_VALIDATION,
                AllocationPolicy.ColdPath.ADMIN_CLI,
                AllocationPolicy.ColdPath.SIMULATOR,
                AllocationPolicy.ColdPath.REST_POLLING,
                AllocationPolicy.ColdPath.TESTS);
    }

    @Test
    void failure_everyHotPathHasDocumentedNameAndBenchmarkOwner() {
        assertThat(AllocationPolicy.HotPath.values())
            .allSatisfy(path -> {
                assertThat(path.documentedName()).as(path.name()).isNotBlank();
                assertThat(path.benchmarkOwner()).as(path.name()).isNotBlank();
            });
    }

    @Test
    void failure_benchmarkOwnershipCoversImplementedAndPlaceholderSurfaces() {
        assertThat(AllocationPolicy.HotPath.values())
            .extracting(AllocationPolicy.HotPath::benchmarkOwner)
            .containsAll(REQUIRED_IMPLEMENTED_BENCHMARK_OWNERS)
            .containsAll(REQUIRED_PLACEHOLDER_OWNERS);
    }

    @Test
    void failure_implementedBenchmarkOwnersUseBenchmarkClassNames() {
        assertThat(AllocationPolicy.HotPath.values())
            .filteredOn(AllocationPolicy.HotPath::hasImplementedBenchmarkOwner)
            .extracting(AllocationPolicy.HotPath::benchmarkOwner)
            .allSatisfy(owner -> assertThat(owner).endsWith("Benchmark"));
    }

    @Test
    void failure_placeholdersRemainTraceableUntilFutureTaskAddsBenchmarkClass() {
        assertThat(AllocationPolicy.HotPath.values())
            .filteredOn(path -> !path.hasImplementedBenchmarkOwner())
            .extracting(AllocationPolicy.HotPath::benchmarkOwner)
            .allSatisfy(owner -> assertThat(REQUIRED_PLACEHOLDER_OWNERS).contains(owner));
    }
}
