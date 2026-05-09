package ig.rueishi.nitroj.exchange.common;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Unit coverage for TASK-004 TOML loading and fixed-point parsing.
 *
 * <p>Responsibility: verifies the public ConfigManager entry points and the
 * static test factories owned by the configuration task card. Role in system:
 * this is the first guard that later gateway, cluster, and tooling cards can
 * depend on before consuming typed config records. Relationships: tests load the
 * repository TOML files that are shipped with the task and create narrow mutated
 * copies for validation failures. Lifecycle: executed by the platform-common
 * JUnit task during regular Gradle verification. Design intent: cover the exact
 * positive, negative, and edge paths listed in T-002 without pulling in gateway
 * or cluster runtime dependencies.
 */
final class ConfigManagerTest {
    private static final Path REPO_CONFIG_DIR = Path.of("..", "config");

    @TempDir
    private Path tempDir;

    /**
     * Verifies integer decimal strings are converted with the platform 1e8 scale.
     */
    @Test
    void parseScaled_wholeNumber_correct() {
        assertThat(ConfigManager.parseScaled("65000")).isEqualTo(6_500_000_000_000L);
    }

    /**
     * Verifies two-decimal values are padded to eight fractional digits.
     */
    @Test
    void parseScaled_decimal_correct() {
        assertThat(ConfigManager.parseScaled("0.01")).isEqualTo(1_000_000L);
    }

    /**
     * Verifies the minimum representable fractional unit is preserved exactly.
     */
    @Test
    void parseScaled_eightDecimalPlaces_exact() {
        assertThat(ConfigManager.parseScaled("0.00000001")).isEqualTo(1L);
    }

    /**
     * Verifies zero remains zero after fixed-point conversion.
     */
    @Test
    void parseScaled_zeroValue_returnsZero() {
        assertThat(ConfigManager.parseScaled("0")).isEqualTo(0L);
    }

    /**
     * Verifies trailing zero fractions normalize to the expected whole scaled value.
     */
    @Test
    void parseScaled_zeroFraction_correct() {
        assertThat(ConfigManager.parseScaled("1.0")).isEqualTo(100_000_000L);
    }

    /**
     * Verifies fractional precision beyond eight places is truncated, not rounded.
     */
    @Test
    void parseScaled_nineDecimalPlaces_truncatesAt8() {
        assertThat(ConfigManager.parseScaled("1.123456789")).isEqualTo(112_345_678L);
    }

    /**
     * Verifies a large but valid scaled value does not overflow.
     */
    @Test
    void parseScaled_largeValue_noOverflow() {
        assertThat(ConfigManager.parseScaled("99999999.99999999")).isEqualTo(9_999_999_999_999_999L);
    }

    /**
     * Loads the repository cluster TOML and checks representative fields from each
     * major section are populated with typed, scaled values.
     */
    @Test
    void loadCluster_validToml_allFieldsPopulated() {
        final ClusterNodeConfig config = ConfigManager.loadCluster(configPath("cluster-node-0.toml"));

        assertThat(config.nodeId()).isZero();
        assertThat(config.nodeRole()).isEqualTo("cluster");
        assertThat(config.risk().maxOrdersPerSecond()).isEqualTo(100);
        assertThat(config.risk().maxDailyLossUsd()).isEqualTo(1_000_000_000_000L);
        assertThat(config.risk().instruments().get(Ids.INSTRUMENT_BTC_USD).softLimitPct()).isEqualTo(80);
        assertThat(config.marketMaking().baseQuoteSizeScaled()).isEqualTo(1_000_000L);
        assertThat(config.marketMaking().executionStrategy().executionStrategyId())
            .isEqualTo(ExecutionStrategyIds.POST_ONLY_QUOTE);
        assertThat(config.marketMaking().executionStrategy().overrideCount()).isZero();
        assertThat(config.arb().takerFeeScaled()[Ids.VENUE_COINBASE]).isEqualTo(600_000L);
        assertThat(config.arb().executionStrategy().executionStrategyId())
            .isEqualTo(ExecutionStrategyIds.MULTI_LEG_CONTINGENT);
        assertThat(config.maxVenues()).isEqualTo(Ids.MAX_VENUES);
    }

    /**
     * Creates a cluster TOML variant without the optional arbitrage cooldown and
     * verifies the loader applies the documented zero default.
     *
     * @throws IOException if the temporary TOML copy cannot be written
     */
    @Test
    void loadCluster_missingOptionalField_usesDefault() throws IOException {
        final Path copy = tempDir.resolve("cluster-node-0-no-cooldown.toml");
        final String toml = Files.readString(REPO_CONFIG_DIR.resolve("cluster-node-0.toml"))
            .replace("cooldownAfterFailureMicros = 10000000\n", "");
        Files.writeString(copy, toml);

        final ClusterNodeConfig config = ConfigManager.loadCluster(copy.toString());

        assertThat(config.arb().cooldownAfterFailureMicros()).isZero();
    }

    @Test
    void loadCluster_missingExecutionStrategy_usesStrategyTypeDefault() throws IOException {
        final Path copy = tempDir.resolve("cluster-node-0-default-execution.toml");
        final String toml = Files.readString(REPO_CONFIG_DIR.resolve("cluster-node-0.toml"))
            .replace("executionStrategy                   = \"PostOnlyQuote\"\n", "")
            .replace("executionStrategy          = \"MultiLegContingent\"\n", "");
        Files.writeString(copy, toml);

        final ClusterNodeConfig config = ConfigManager.loadCluster(copy.toString());

        assertThat(config.marketMaking().executionStrategy().executionStrategyId())
            .isEqualTo(ExecutionStrategyIds.POST_ONLY_QUOTE);
        assertThat(config.arb().executionStrategy().executionStrategyId())
            .isEqualTo(ExecutionStrategyIds.MULTI_LEG_CONTINGENT);
        assertThat(config.marketMaking().executionStrategy().overrideCount()).isZero();
    }

    @Test
    void loadCluster_executionOverride_validInstrumentVenueOverride() throws IOException {
        final Path copy = tempDir.resolve("cluster-node-0-execution-override.toml");
        final String toml = Files.readString(REPO_CONFIG_DIR.resolve("cluster-node-0.toml"))
            + """

            [[strategy.market_making.executionOverride]]
            instrumentId = 2
            venueId = 1
            executionStrategy = "PostOnlyQuote"
            """;
        Files.writeString(copy, toml);

        final ClusterNodeConfig config = ConfigManager.loadCluster(copy.toString());
        final ExecutionStrategyOverrideConfig override =
            config.marketMaking().executionStrategy().overrides()[0];

        assertThat(config.marketMaking().executionStrategy().overrideCount()).isEqualTo(1);
        assertThat(override.instrumentId()).isEqualTo(2);
        assertThat(override.venueId()).isEqualTo(1);
        assertThat(override.executionStrategyId()).isEqualTo(ExecutionStrategyIds.POST_ONLY_QUOTE);
    }

    @Test
    void loadCluster_executionOverride_validVenueOnlyOverride() throws IOException {
        final Path copy = tempDir.resolve("cluster-node-0-venue-override.toml");
        final String toml = Files.readString(REPO_CONFIG_DIR.resolve("cluster-node-0.toml"))
            + """

            [[strategy.market_making.executionOverride]]
            venueId = 1
            executionStrategy = "PostOnlyQuote"
            """;
        Files.writeString(copy, toml);

        final ExecutionStrategyOverrideConfig override =
            ConfigManager.loadCluster(copy.toString()).marketMaking().executionStrategy().overrides()[0];

        assertThat(override.instrumentId()).isEqualTo(ExecutionStrategyOverrideConfig.ANY_INSTRUMENT);
        assertThat(override.venueId()).isEqualTo(1);
    }

    @Test
    void loadCluster_unknownExecutionStrategy_throwsAtStartup() throws IOException {
        final Path copy = tempDir.resolve("cluster-node-0-unknown-execution.toml");
        final String toml = Files.readString(REPO_CONFIG_DIR.resolve("cluster-node-0.toml"))
            .replace("executionStrategy                   = \"PostOnlyQuote\"",
                "executionStrategy                   = \"PeggedQuote\"");
        Files.writeString(copy, toml);

        assertThatThrownBy(() -> ConfigManager.loadCluster(copy.toString()))
            .isInstanceOf(ConfigValidationException.class)
            .hasMessageContaining("strategy.market_making.executionStrategy")
            .hasMessageContaining("PeggedQuote");
    }

    @Test
    void loadCluster_incompatibleExecutionStrategy_throwsAtStartup() throws IOException {
        final Path copy = tempDir.resolve("cluster-node-0-incompatible-execution.toml");
        final String toml = Files.readString(REPO_CONFIG_DIR.resolve("cluster-node-0.toml"))
            .replace("executionStrategy                   = \"PostOnlyQuote\"",
                "executionStrategy                   = \"MultiLegContingent\"");
        Files.writeString(copy, toml);

        assertThatThrownBy(() -> ConfigManager.loadCluster(copy.toString()))
            .isInstanceOf(ConfigValidationException.class)
            .hasMessageContaining("not compatible");
    }

    @Test
    void loadCluster_duplicateExecutionOverride_throwsAtStartup() throws IOException {
        final Path copy = tempDir.resolve("cluster-node-0-duplicate-override.toml");
        final String toml = Files.readString(REPO_CONFIG_DIR.resolve("cluster-node-0.toml"))
            + """

            [[strategy.market_making.executionOverride]]
            instrumentId = 2
            venueId = 1
            executionStrategy = "PostOnlyQuote"

            [[strategy.market_making.executionOverride]]
            instrumentId = 2
            venueId = 1
            executionStrategy = "PostOnlyQuote"
            """;
        Files.writeString(copy, toml);

        assertThatThrownBy(() -> ConfigManager.loadCluster(copy.toString()))
            .isInstanceOf(ConfigValidationException.class)
            .hasMessageContaining("duplicate execution strategy override");
    }

    @Test
    void loadCluster_emptyExecutionStrategy_throwsAtStartup() throws IOException {
        final Path copy = tempDir.resolve("cluster-node-0-empty-execution.toml");
        final String toml = Files.readString(REPO_CONFIG_DIR.resolve("cluster-node-0.toml"))
            .replace("executionStrategy                   = \"PostOnlyQuote\"",
                "executionStrategy                   = \"\"");
        Files.writeString(copy, toml);

        assertThatThrownBy(() -> ConfigManager.loadCluster(copy.toString()))
            .isInstanceOf(ConfigValidationException.class)
            .hasMessageContaining("must not be blank");
    }

    @Test
    void loadCluster_nonCanonicalExecutionStrategy_throwsAtStartup() throws IOException {
        final Path copy = tempDir.resolve("cluster-node-0-noncanonical-execution.toml");
        final String toml = Files.readString(REPO_CONFIG_DIR.resolve("cluster-node-0.toml"))
            .replace("executionStrategy                   = \"PostOnlyQuote\"",
                "executionStrategy                   = \"postOnlyQuote\"");
        Files.writeString(copy, toml);

        assertThatThrownBy(() -> ConfigManager.loadCluster(copy.toString()))
            .isInstanceOf(ConfigValidationException.class)
            .hasMessageContaining("unknown execution strategy ID")
            .hasMessageContaining("postOnlyQuote");
    }

    @Test
    void loadCluster_executionOverrideWithoutScope_throwsAtStartup() throws IOException {
        final Path copy = tempDir.resolve("cluster-node-0-unscoped-override.toml");
        final String toml = Files.readString(REPO_CONFIG_DIR.resolve("cluster-node-0.toml"))
            + """

            [[strategy.market_making.executionOverride]]
            executionStrategy = "PostOnlyQuote"
            """;
        Files.writeString(copy, toml);

        assertThatThrownBy(() -> ConfigManager.loadCluster(copy.toString()))
            .isInstanceOf(ConfigValidationException.class)
            .hasMessageContaining("override must set instrumentId, venueId, or both");
    }

    /**
     * Creates a cluster TOML variant missing a required field and verifies the
     * exception message names that field path for startup diagnostics.
     *
     * @throws IOException if the temporary TOML copy cannot be written
     */
    @Test
    void loadCluster_missingRequiredField_throwsConfigValidationException() throws IOException {
        final Path copy = tempDir.resolve("cluster-node-0-missing-risk.toml");
        final String toml = Files.readString(REPO_CONFIG_DIR.resolve("cluster-node-0.toml"))
            .replace("maxDailyLossUsd    = \"10000.00\"\n", "");
        Files.writeString(copy, toml);

        assertThatThrownBy(() -> ConfigManager.loadCluster(copy.toString()))
            .isInstanceOf(ConfigValidationException.class)
            .hasMessageContaining("risk.global.maxDailyLossUsd");
    }

    /**
     * Loads the repository gateway TOML and checks process, protocol, CPU, and
     * warmup fields are populated as the gateway startup contract expects.
     */
    @Test
    void loadGateway_validToml_allFieldsPopulated() {
        final GatewayConfig config = ConfigManager.loadGateway(configPath("gateway-1.toml"));

        assertThat(config.venueId()).isEqualTo(Ids.VENUE_COINBASE);
        assertThat(config.fix().senderCompId()).isEqualTo("YOUR_SENDER_COMP_ID");
        assertThat(config.credentials().vaultPath()).isEqualTo("secret/trading/coinbase/venue-1");
        assertThat(config.rest().baseUrl()).isEqualTo("https://api.exchange.coinbase.com");
        assertThat(config.cpu().artioLibraryThread()).isEqualTo(2);
        assertThat(config.disruptor().ringBufferSize()).isEqualTo(4096);
        assertThat(config.warmup().iterations()).isEqualTo(100_000);
        assertThat(config.warmup().requireC2Verified()).isTrue();
    }

    @Test
    void loadGateway_binanceConfig_usesVenueTwoAndBinanceVaultPath() {
        final GatewayConfig config = ConfigManager.loadGateway(configPath("gateway-2.toml"));

        assertThat(config.venueId()).isEqualTo(Ids.VENUE_BINANCE);
        assertThat(config.fix().senderCompId()).isEqualTo("YOUR_BINANCE_SENDER_COMP_ID");
        assertThat(config.fix().targetCompId()).isEqualTo("SPOT");
        assertThat(config.fix().resetSeqNumOnLogon()).isTrue();
        assertThat(config.credentials().vaultPath()).isEqualTo("secret/trading/binance/venue-2");
        assertThat(config.rest().baseUrl()).isEqualTo("https://api.binance.com");
        assertThat(config.counterFileDir()).isEqualTo("/dev/shm/metrics/gateway-2");
    }

    /**
     * Loads the repository venue registry and verifies the V11 plugin and
     * market-data capability fields are parsed for Coinbase's configured L3
     * rollout path.
     */
    @Test
    void loadVenues_coinbaseL3Config_pluginFieldsPopulated() {
        final List<VenueConfig> venues = ConfigManager.loadVenues(configPath("venues.toml"));
        final VenueConfig coinbase = venues.getFirst();

        assertThat(coinbase.fixPlugin()).isEqualTo(FixPluginId.FIXT11_FIX50SP2);
        assertThat(coinbase.venuePlugin()).isEqualTo("COINBASE");
        assertThat(coinbase.marketDataModel()).isEqualTo(MarketDataModel.L3);
        assertThat(coinbase.capabilities().orderEntryEnabled()).isTrue();
        assertThat(coinbase.capabilities().marketDataEnabled()).isTrue();
        assertThat(coinbase.capabilities().nativeReplaceSupported()).isFalse();
    }

    @Test
    void loadVenues_binanceV14VenueIdTwo_fix44L2ConfigLoaded() {
        final List<VenueConfig> venues = ConfigManager.loadVenues(configPath("venues.toml"));
        final VenueConfig binance = venues.stream()
            .filter(venue -> venue.id() == Ids.VENUE_BINANCE)
            .findFirst()
            .orElseThrow();

        assertThat(binance.name()).isEqualTo("BINANCE");
        assertThat(binance.fixHost()).isEqualTo("fix-oe.binance.com");
        assertThat(binance.fixPort()).isEqualTo(9000);
        assertThat(binance.fixPlugin()).isEqualTo(FixPluginId.FIX_44);
        assertThat(binance.venuePlugin()).isEqualTo("BINANCE");
        assertThat(binance.marketDataModel()).isEqualTo(MarketDataModel.L2);
        assertThat(binance.capabilities().orderEntryEnabled()).isTrue();
        assertThat(binance.capabilities().marketDataEnabled()).isTrue();
        assertThat(binance.capabilities().nativeReplaceSupported()).isFalse();
    }

    /**
     * Verifies the venue loader accepts L2 as an explicit market-data model for
     * venues that supply price-level updates directly.
     *
     * @throws IOException if the temporary TOML copy cannot be written
     */
    @Test
    void loadVenues_l2MarketDataModel_valid() throws IOException {
        final Path copy = tempDir.resolve("venues-l2.toml");
        final String toml = Files.readString(REPO_CONFIG_DIR.resolve("venues.toml"))
            .replace("marketDataModel = \"L3\"", "marketDataModel = \"L2\"");
        Files.writeString(copy, toml);

        final List<VenueConfig> venues = ConfigManager.loadVenues(copy.toString());

        assertThat(venues).allMatch(venue -> venue.marketDataModel() == MarketDataModel.L2);
    }

    /**
     * Verifies unsupported FIX protocol plugin IDs fail during config loading,
     * before gateway startup can open an incorrectly configured session.
     *
     * @throws IOException if the temporary TOML copy cannot be written
     */
    @Test
    void loadVenues_unsupportedFixPlugin_throwsConfigValidationException() throws IOException {
        final Path copy = tempDir.resolve("venues-bad-fix-plugin.toml");
        final String toml = Files.readString(REPO_CONFIG_DIR.resolve("venues.toml"))
            .replaceFirst("fixPlugin = \"FIXT11_FIX50SP2\"", "fixPlugin = \"FIX_50\"");
        Files.writeString(copy, toml);

        assertThatThrownBy(() -> ConfigManager.loadVenues(copy.toString()))
            .isInstanceOf(ConfigValidationException.class)
            .hasMessageContaining("venue.fixPlugin")
            .hasMessageContaining("FIX_50");
    }

    /**
     * Verifies unsupported market-data model names fail with the exact TOML path
     * so operators can repair venue registry entries quickly.
     *
     * @throws IOException if the temporary TOML copy cannot be written
     */
    @Test
    void loadVenues_unsupportedMarketDataModel_throwsConfigValidationException() throws IOException {
        final Path copy = tempDir.resolve("venues-bad-market-data-model.toml");
        final String toml = Files.readString(REPO_CONFIG_DIR.resolve("venues.toml"))
            .replaceFirst("marketDataModel = \"L3\"", "marketDataModel = \"L4\"");
        Files.writeString(copy, toml);

        assertThatThrownBy(() -> ConfigManager.loadVenues(copy.toString()))
            .isInstanceOf(ConfigValidationException.class)
            .hasMessageContaining("venue.marketDataModel")
            .hasMessageContaining("L4");
    }

    @Test
    void loadVenues_unknownVenuePlugin_isAcceptedForGatewayRegistryResolution() throws IOException {
        final Path copy = tempDir.resolve("venues-unknown-venue-plugin.toml");
        final String toml = Files.readString(REPO_CONFIG_DIR.resolve("venues.toml"))
            .replaceFirst("venuePlugin = \"COINBASE\"", "venuePlugin = \"UNKNOWN\"");
        Files.writeString(copy, toml);

        assertThat(ConfigManager.loadVenues(copy.toString()).getFirst().venuePlugin()).isEqualTo("UNKNOWN");
    }

    /**
     * Verifies missing V11 venue plugin IDs fail as required fields instead of
     * falling back to hidden defaults.
     *
     * @throws IOException if the temporary TOML copy cannot be written
     */
    @Test
    void loadVenues_missingVenuePlugin_throwsConfigValidationException() throws IOException {
        final Path copy = tempDir.resolve("venues-missing-venue-plugin.toml");
        final String toml = Files.readString(REPO_CONFIG_DIR.resolve("venues.toml"))
            .replaceFirst("venuePlugin = \"COINBASE\"\\n", "");
        Files.writeString(copy, toml);

        assertThatThrownBy(() -> ConfigManager.loadVenues(copy.toString()))
            .isInstanceOf(ConfigValidationException.class)
            .hasMessageContaining("venue.venuePlugin");
    }

    /**
     * Verifies the gateway test factory avoids CPU pinning, uses development
     * warmup, and scopes Aeron paths by process ID for parallel tests.
     */
    @Test
    void forTest_noCpuPinning_developmentWarmup() {
        final GatewayConfig config = GatewayConfig.forTest();
        final long pid = ProcessHandle.current().pid();

        assertThat(config.cpu()).isEqualTo(CpuConfig.noPinning());
        assertThat(config.warmup().iterations()).isEqualTo(10_000);
        assertThat(config.warmup().requireC2Verified()).isFalse();
        assertThat(config.aeronDir()).contains(Long.toString(pid));
    }

    /**
     * Verifies the AMB-005 quote-size floor is loaded from TOML into the market
     * making record instead of being hidden as an algorithm constant.
     */
    @Test
    void minQuoteSizeFractionBps_loadedFromToml() {
        final ClusterNodeConfig config = ConfigManager.loadCluster(configPath("cluster-node-0.toml"));

        assertThat(config.marketMaking().minQuoteSizeFractionBps()).isEqualTo(1000L);
    }

    private static String configPath(final String fileName) {
        return REPO_CONFIG_DIR.resolve(fileName).toString();
    }
}
