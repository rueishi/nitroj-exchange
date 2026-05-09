package ig.rueishi.nitroj.exchange.common;

import java.util.Locale;
import java.util.Objects;

/**
 * Code-level allocation boundary for NitroJEx runtime paths.
 *
 * <p>Responsibility: names the paths that must be treated as steady-state
 * trading hot paths versus cold/control paths. Role in system: task cards,
 * benchmarks, and code reviews use this type as the executable counterpart to
 * the active spec's allocation policy. Relationships: the policy is intentionally
 * independent of gateway/cluster implementation classes so common tests can
 * detect documentation drift without loading runtime modules. Each hot path
 * records the benchmark owner or the future task placeholder that must replace
 * itself with a benchmark before a zero-GC release claim. Lifecycle: static
 * metadata initialized once at class load time. Design intent: keep the
 * zero-allocation claim precise: hot paths target zero allocation after warmup,
 * while startup, tooling, diagnostics, and simulator paths may allocate.</p>
 */
public final class AllocationPolicy {
    private AllocationPolicy() {
    }

    /**
     * Runtime classification used by reviewers and benchmark owners.
     */
    public enum PathClass {
        HOT_PATH,
        COLD_PATH
    }

    /**
     * Required steady-state paths that must be implemented and benchmarked with
     * zero-allocation as the target.
     */
    public enum HotPath {
        FIX_L2_PARSING("FIX L2 parsing", "FixL2NormalizerBenchmark"),
        FIX_L3_PARSING("FIX L3 parsing", "FixL3NormalizerBenchmark"),
        EXECUTION_REPORT_PARSING("FIX execution-report parsing", "ExecutionReportBenchmark"),
        SBE_ENCODE_DECODE("SBE encode/decode", "SbeCodecBenchmark"),
        GATEWAY_DISRUPTOR_HANDOFF("Gateway disruptor handoff", "GatewayHandoffBenchmark"),
        AERON_PUBLICATION_HANDOFF("Aeron publication handoff", "GatewayHandoffBenchmark"),
        VENUE_L2_BOOK_MUTATION("VenueL2Book mutation", "BookMutationBenchmark"),
        VENUE_L3_BOOK_MUTATION("VenueL3Book add/change/delete", "BookMutationBenchmark"),
        L3_TO_L2_DERIVATION("L3-to-L2 derivation", "BookMutationBenchmark"),
        CONSOLIDATED_L2_UPDATE_QUERY("ConsolidatedL2Book update/query", "BookMutationBenchmark"),
        OWN_ORDER_OVERLAY_UPDATE_QUERY("OwnOrderOverlay update/query", "OwnLiquidityBenchmark"),
        EXTERNAL_LIQUIDITY_QUERY("ExternalLiquidityView query", "OwnLiquidityBenchmark"),
        ORDER_STATE_TRANSITION("OrderManager state transition", "OrderManagerBenchmark"),
        RISK_DECISION("RiskEngine decision", "RiskDecisionBenchmark"),
        STRATEGY_ENGINE_DISPATCH("StrategyEngine dispatch", "StrategyTickBenchmark"),
        MARKET_MAKING_STRATEGY_TICK("MarketMakingStrategy tick", "StrategyTickBenchmark"),
        ARB_STRATEGY_TICK("ArbStrategy tick", "StrategyTickBenchmark"),
        PARENT_ORDER_REGISTRY_UPDATE_QUERY("ParentOrderRegistry update/query", "ParentOrderRegistryBenchmark"),
        PARENT_ACTIVE_CHILD_LOOKUP("parent-to-child lookup", "ParentOrderRegistryBenchmark"),
        EXECUTION_STRATEGY_ENGINE_DISPATCH("ExecutionStrategyEngine dispatch", "ExecutionStrategyEngineBenchmark"),
        IMMEDIATE_LIMIT_EXECUTION_CALLBACK("ImmediateLimitExecution callback", "ImmediateLimitExecutionBenchmark"),
        POST_ONLY_QUOTE_EXECUTION_CALLBACK("PostOnlyQuoteExecution callback", "PostOnlyQuoteExecutionBenchmark"),
        MULTI_LEG_CONTINGENT_EXECUTION_CALLBACK("MultiLegContingentExecution callback", "MultiLegContingentExecutionBenchmark"),
        INVENTORY_HEDGE_MARKET_DATA_TICK("InventoryHedgeStrategy.onMarketDataTick", "InventoryHedgeStrategyBenchmark"),
        INVENTORY_HEDGE_PORTFOLIO_UPDATE("InventoryHedgeStrategy.onPortfolioUpdate", "InventoryHedgeStrategyBenchmark"),
        INVENTORY_HEDGE_PARENT_INTENT_EMISSION("InventoryHedgeStrategy parent-intent emission", "InventoryHedgeStrategyBenchmark"),
        PARALLEL_VENUE_PARENT_INTENT("ParallelVenueExecution.onParentIntent", "ParallelVenueExecutionBenchmark"),
        PARALLEL_VENUE_SLICE_PLAN("ParallelVenueExecution slice-plan computation", "ParallelVenueExecutionBenchmark"),
        PARALLEL_VENUE_CHILD_EXECUTION("ParallelVenueExecution.onChildExecution", "ParallelVenueExecutionBenchmark"),
        PARALLEL_VENUE_TIMER("ParallelVenueExecution.onTimer", "ParallelVenueExecutionBenchmark"),
        SMART_ORDER_ROUTING_PARENT_INTENT("SmartOrderRoutingExecution.onParentIntent", "SmartOrderRoutingExecutionBenchmark"),
        SMART_ORDER_ROUTING_SLICE_PLAN("SmartOrderRoutingExecution slice-plan computation", "SmartOrderRoutingExecutionBenchmark"),
        SMART_ORDER_ROUTING_RESLICE("SmartOrderRoutingExecution.onMarketDataTick (re-slice path)", "SmartOrderRoutingExecutionBenchmark"),
        SMART_ORDER_ROUTING_CHILD_EXECUTION("SmartOrderRoutingExecution.onChildExecution", "SmartOrderRoutingExecutionBenchmark"),
        SMART_ORDER_ROUTING_TIMER("SmartOrderRoutingExecution.onTimer", "SmartOrderRoutingExecutionBenchmark"),
        MIXED_PRECISION_OWN_ORDER_OVERLAY_QUERY("Mixed-precision OwnOrderOverlay query path for L2 venues", "OwnLiquidityBenchmark"),
        BINANCE_L2_NORMALIZER_SBE_EVENT_PRODUCTION("BinanceL2MarketDataNormalizer SBE event production", "BinanceL2NormalizerBenchmark"),
        BINANCE_ORDER_ENTRY_POLICY_ENRICHMENT("BinanceVenuePlugin order-entry policy enrichment", "BinanceOrderEntryPolicyBenchmark"),
        ORDER_COMMAND_ENCODING("order command encoding", "OrderEncodingBenchmark");

        private final String documentedName;
        private final String benchmarkOwner;

        HotPath(final String documentedName, final String benchmarkOwner) {
            this.documentedName = documentedName;
            this.benchmarkOwner = benchmarkOwner;
        }

        public String documentedName() {
            return documentedName;
        }

        public String benchmarkOwner() {
            return benchmarkOwner;
        }

        public boolean hasImplementedBenchmarkOwner() {
            return benchmarkOwner.endsWith("Benchmark");
        }
    }

    /**
     * Paths allowed to allocate because they are outside normal trading-event
     * processing.
     */
    public enum ColdPath {
        CONFIG_PARSING,
        STARTUP_VALIDATION,
        PLUGIN_CONSTRUCTION,
        ADMIN_CLI,
        SIMULATOR,
        REPLAY_TOOLING,
        REST_POLLING,
        DIAGNOSTICS,
        TESTS
    }

    public static PathClass classifyHot(final HotPath path) {
        Objects.requireNonNull(path, "path");
        return PathClass.HOT_PATH;
    }

    public static PathClass classifyCold(final ColdPath path) {
        Objects.requireNonNull(path, "path");
        return PathClass.COLD_PATH;
    }

    /**
     * Looks up a documented path name for simple policy assertions and operator
     * documentation checks.
     *
     * @param name enum-style or hyphen/space separated path name
     * @return hot or cold classification
     * @throws IllegalArgumentException when the path is not documented
     */
    public static PathClass classify(final String name) {
        final String normalized = normalize(name);
        for (HotPath path : HotPath.values()) {
            if (path.name().equals(normalized) || normalize(path.documentedName()).equals(normalized)) {
                return PathClass.HOT_PATH;
            }
        }
        for (ColdPath path : ColdPath.values()) {
            if (path.name().equals(normalized)) {
                return PathClass.COLD_PATH;
            }
        }
        throw new IllegalArgumentException("Unknown allocation policy path: " + name);
    }

    private static String normalize(final String name) {
        Objects.requireNonNull(name, "name");
        if (name.isBlank()) {
            throw new IllegalArgumentException("allocation policy path name must not be blank");
        }
        return name.trim()
            .replace('-', '_')
            .replace(' ', '_')
            .toUpperCase(Locale.ROOT);
    }
}
