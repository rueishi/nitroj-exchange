package ig.rueishi.nitroj.exchange.common;

import com.electronwill.nightconfig.core.Config;
import com.electronwill.nightconfig.toml.TomlParser;
import ig.rueishi.nitroj.exchange.common.config.StrategyConfigValidator;

import java.io.IOException;
import java.io.Reader;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Loads NitroJEx TOML files into immutable configuration records.
 *
 * <p>Responsibility: this class is the single TASK-004 boundary between raw
 * TOML and typed Java startup configuration. Role in system: gateway, cluster,
 * admin, and future registry wiring call these loaders before creating runtime
 * components. Relationships: it creates all config records in
 * {@code ig.rueishi.nitroj.exchange.common} and uses night-config's
 * {@link TomlParser}, as required by the execution plan. Lifecycle: loader
 * methods are called during process startup; parsed records are then reused
 * read-only by hot-path components. Design intent: perform validation and
 * fixed-point conversion once at startup, with precise field-path failures.
 */
public final class ConfigManager {
    private static final long SCALE = 100_000_000L;
    private static final int ARRAY_BY_VENUE_ID_SIZE = Ids.MAX_VENUES + 1;

    private ConfigManager() {
    }

    /**
     * Loads a cluster-node TOML file into a typed config aggregate.
     *
     * <p>The method parses the file with night-config, validates every required
     * field owned by TASK-004, converts decimal strings to scaled longs, and maps
     * arbitrage per-venue arrays into arrays indexed by venue ID. Missing optional
     * {@code cooldownAfterFailureMicros} defaults to zero. It is called by cluster
     * startup before constructing business services.
     *
     * @param path path to {@code cluster-node-N.toml}
     * @return immutable cluster-node configuration
     * @throws ConfigValidationException when the file is absent, malformed, or
     *                                   missing a required field
     */
    public static ClusterNodeConfig loadCluster(final String path) {
        final Config config = parseFile(path);
        final RiskConfig risk = loadRisk(config);
        final MarketMakingConfig marketMaking = loadMarketMaking(config);
        final ArbStrategyConfig arb = loadArb(config);

        return ClusterNodeConfig.builder()
            .nodeId(requiredInt(config, "process.nodeId"))
            .nodeRole(requiredString(config, "process.nodeRole"))
            .aeronDir(requiredString(config, "process.aeronDir"))
            .archiveDir(requiredString(config, "process.archiveDir"))
            .snapshotDir(requiredString(config, "process.snapshotDir"))
            .clusterMembers(requiredString(config, "cluster.members"))
            .ingressChannel(requiredString(config, "cluster.ingressChannel"))
            .logChannel(requiredString(config, "cluster.logChannel"))
            .archiveChannel(requiredString(config, "cluster.archiveChannel"))
            .snapshotIntervalS(requiredInt(config, "cluster.snapshotIntervalS"))
            .risk(risk)
            .marketMakingEnabled(optionalBoolean(config, "strategy.market_making.enabled", false))
            .marketMaking(marketMaking)
            .arbEnabled(optionalBoolean(config, "strategy.arb.enabled", false))
            .arb(arb)
            .cpu(new CpuConfig(0, 0, 0, requiredInt(config, "cpu.clusterServiceThread"), 0))
            .counterFileDir(requiredString(config, "metrics.counterFileDir"))
            .histogramOutputMs(requiredInt(config, "metrics.histogramOutputMs"))
            .maxVenues(requiredInt(config, "constants.maxVenues"))
            .maxInstruments(requiredInt(config, "constants.maxInstruments"))
            .build();
    }

    /**
     * Loads a gateway TOML file into a typed config aggregate.
     *
     * <p>The loader validates process, cluster, FIX, credential, REST, CPU,
     * Disruptor, and metrics sections. Production gateway TOML does not carry a
     * warmup block in Section 22.4, so this method attaches
     * {@link WarmupConfig#production()} as the startup default. It is called by
     * gateway startup before Artio and Aeron components are created.
     *
     * @param path path to {@code gateway-<venueId>.toml}
     * @return immutable gateway configuration
     * @throws ConfigValidationException when the file is absent, malformed, or
     *                                   missing a required field
     */
    public static GatewayConfig loadGateway(final String path) {
        final Config config = parseFile(path);
        return GatewayConfig.builder()
            .venueId(requiredInt(config, "process.venueId"))
            .nodeRole(requiredString(config, "process.nodeRole"))
            .aeronDir(requiredString(config, "process.aeronDir"))
            .logDir(requiredString(config, "process.logDir"))
            .clusterIngressChannel(requiredString(config, "cluster.ingressChannel"))
            .clusterEgressChannel(requiredString(config, "cluster.egressChannel"))
            .clusterMembers(requiredStringList(config, "cluster.members"))
            .fix(loadFix(config))
            .credentials(new CredentialsConfig(requiredString(config, "credentials.vaultPath"), null, null, null))
            .rest(RestConfig.builder()
                .baseUrl(requiredString(config, "rest.baseUrl"))
                .pollIntervalMs(requiredInt(config, "rest.pollIntervalMs"))
                .timeoutMs(requiredInt(config, "rest.timeoutMs"))
                .build())
            .cpu(new CpuConfig(
                requiredInt(config, "cpu.artioLibraryThread"),
                requiredInt(config, "cpu.gatewayDisruptorThread"),
                requiredInt(config, "cpu.gatewayEgressThread"),
                0,
                0))
            .disruptor(DisruptorConfig.builder()
                .ringBufferSize(requiredInt(config, "disruptor.ringBufferSize"))
                .slotSizeBytes(requiredInt(config, "disruptor.slotSizeBytes"))
                .build())
            .counterFileDir(requiredString(config, "metrics.counterFileDir"))
            .histogramOutputMs(requiredInt(config, "metrics.histogramOutputMs"))
            .warmup(WarmupConfig.production())
            .build();
    }

    /**
     * Loads the shared venue registry TOML.
     *
     * @param path path to {@code venues.toml}
     * @return immutable list of venue records
     */
    public static List<VenueConfig> loadVenues(final String path) {
        final Config config = parseFile(path);
        final List<? extends Config> venues = required(config.get("venue"), "venue");
        return venues.stream()
            .map(venue -> new VenueConfig(
                requiredInt(venue, "id", "venue.id"),
                requiredString(venue, "name", "venue.name"),
                requiredString(venue, "fixHost", "venue.fixHost"),
                requiredInt(venue, "fixPort", "venue.fixPort"),
                requiredBoolean(venue, "sandbox", "venue.sandbox"),
                requiredEnum(FixPluginId.class, venue, "fixPlugin", "venue.fixPlugin"),
                requiredString(venue, "venuePlugin", "venue.venuePlugin"),
                requiredEnum(MarketDataModel.class, venue, "marketDataModel", "venue.marketDataModel"),
                new VenueCapabilities(
                    requiredBoolean(venue, "orderEntryEnabled", "venue.orderEntryEnabled"),
                    requiredBoolean(venue, "marketDataEnabled", "venue.marketDataEnabled"),
                    requiredBoolean(venue, "nativeReplaceSupported", "venue.nativeReplaceSupported"))))
            .toList();
    }

    /**
     * Loads the shared instrument registry TOML.
     *
     * @param path path to {@code instruments.toml}
     * @return immutable list of instrument records
     */
    public static List<InstrumentConfig> loadInstruments(final String path) {
        final Config config = parseFile(path);
        final List<? extends Config> instruments = required(config.get("instrument"), "instrument");
        return instruments.stream()
            .map(instrument -> new InstrumentConfig(
                requiredInt(instrument, "id", "instrument.id"),
                requiredString(instrument, "symbol", "instrument.symbol"),
                requiredString(instrument, "baseCurrency", "instrument.baseCurrency"),
                requiredString(instrument, "quoteCurrency", "instrument.quoteCurrency")))
            .toList();
    }

    /**
     * Loads admin CLI configuration.
     *
     * @param path path to {@code admin.toml}
     * @return immutable admin config
     */
    public static AdminConfig loadAdmin(final String path) {
        final Config config = parseFile(path);
        return new AdminConfig(
            requiredString(config, "connection.aeronDir"),
            requiredString(config, "connection.adminChannel"),
            requiredInt(config, "connection.adminStreamId"),
            requiredInt(config, "security.operatorId"),
            requiredString(config, "security.hmacKeyVaultPath"),
            requiredString(config, "security.hmacKeyBase64Dev"),
            requiredString(config, "security.nonceFile"),
            intArray(requiredList(config, "security.allowedOperatorIds"), "security.allowedOperatorIds"),
            optionalString(config, "security.newHmacKeyVaultPath", null));
    }

    /**
     * Converts a decimal string into the platform's 1e8 fixed-point long format.
     *
     * <p>The method follows the spec sequence exactly: split on a decimal point,
     * scale the whole part, right-pad the fractional part to eight digits, and
     * truncate additional fractional precision rather than rounding. Negative
     * values and empty fractions are rejected because this config card only owns
     * positive limits, prices, and sizes.
     *
     * @param decimal non-negative decimal string
     * @return scaled long value
     * @throws ConfigValidationException when the value is negative, blank,
     *                                   malformed, or has an empty fraction
     */
    public static long parseScaled(final String decimal) {
        if (decimal == null || decimal.isBlank()) {
            throw new ConfigValidationException("scaled decimal", "value must not be blank");
        }
        if (decimal.startsWith("-")) {
            throw new ConfigValidationException("scaled decimal", "negative values are not allowed: " + decimal);
        }
        final String[] parts = decimal.split("\\.", -1);
        if (parts.length > 2 || parts[0].isEmpty() || (parts.length == 2 && parts[1].isEmpty())) {
            throw new ConfigValidationException("scaled decimal", "invalid decimal value: " + decimal);
        }
        try {
            final long whole = Math.multiplyExact(Long.parseLong(parts[0]), SCALE);
            if (parts.length == 1) {
                return whole;
            }
            String frac = parts[1];
            while (frac.length() < 8) {
                frac += "0";
            }
            frac = frac.substring(0, 8);
            return Math.addExact(whole, Long.parseLong(frac));
        } catch (final ArithmeticException | NumberFormatException ex) {
            throw new ConfigValidationException("scaled decimal", "invalid decimal value: " + decimal);
        }
    }

    /**
     * Validates a required value.
     *
     * @param value parsed value
     * @param fieldPath dotted TOML path
     * @param <T> value type
     * @return the non-null value
     * @throws ConfigValidationException when {@code value} is {@code null}
     */
    public static <T> T required(final T value, final String fieldPath) {
        if (value == null) {
            throw new ConfigValidationException(fieldPath, "required field is missing");
        }
        return value;
    }

    private static Config parseFile(final String path) {
        final Path configPath = Path.of(path);
        if (!Files.exists(configPath)) {
            throw new ConfigValidationException("File not found: " + path);
        }
        try (Reader reader = Files.newBufferedReader(configPath)) {
            return new TomlParser().parse(reader);
        } catch (final IOException | RuntimeException ex) {
            if (ex instanceof ConfigValidationException validationException) {
                throw validationException;
            }
            throw new ConfigValidationException(path, "unable to parse TOML: " + ex.getMessage());
        }
    }

    private static RiskConfig loadRisk(final Config config) {
        final Map<Integer, RiskConfig.InstrumentRisk> instruments = new HashMap<>();
        instruments.put(Ids.INSTRUMENT_BTC_USD, loadInstrumentRisk(config, Ids.INSTRUMENT_BTC_USD, "Btc"));
        instruments.put(Ids.INSTRUMENT_ETH_USD, loadInstrumentRisk(config, Ids.INSTRUMENT_ETH_USD, "Eth"));
        return new RiskConfig(
            requiredInt(config, "risk.global.maxOrdersPerSecond"),
            requiredScaled(config, "risk.global.maxDailyLossUsd"),
            instruments);
    }

    private static RiskConfig.InstrumentRisk loadInstrumentRisk(
        final Config config,
        final int instrumentId,
        final String suffix
    ) {
        final String prefix = "risk.instrument." + instrumentId + ".";
        return new RiskConfig.InstrumentRisk(
            instrumentId,
            requiredScaled(config, prefix + "maxOrderSize" + suffix),
            requiredScaled(config, prefix + "maxLongPosition" + suffix),
            requiredScaled(config, prefix + "maxShortPosition" + suffix),
            requiredScaled(config, prefix + "maxNotionalUsd"),
            requiredInt(config, prefix + "softLimitPct"));
    }

    private static MarketMakingConfig loadMarketMaking(final Config config) {
        return new MarketMakingConfig(
            requiredInt(config, "strategy.market_making.instrumentId"),
            requiredInt(config, "strategy.market_making.venueId"),
            requiredLong(config, "strategy.market_making.targetSpreadBps"),
            requiredLong(config, "strategy.market_making.inventorySkewFactorBps"),
            requiredScaled(config, "strategy.market_making.baseQuoteSizeBtc"),
            requiredScaled(config, "strategy.market_making.maxQuoteSizeBtc"),
            requiredScaled(config, "risk.instrument.1.maxLongPositionBtc"),
            requiredScaled(config, "risk.instrument.1.maxShortPositionBtc"),
            requiredLong(config, "strategy.market_making.refreshThresholdBps"),
            requiredLong(config, "strategy.market_making.maxQuoteAgeMicros"),
            requiredLong(config, "strategy.market_making.marketDataStalenessThresholdMicros"),
            requiredLong(config, "strategy.market_making.wideSpreadThresholdBps"),
            requiredLong(config, "strategy.market_making.maxTolerableSpreadBps"),
            parseScaled("0.01"),
            parseScaled("0.00001"),
            requiredLong(config, "strategy.market_making.minQuoteSizeFractionBps"),
            loadExecutionStrategySelection(config, "strategy.market_making", Ids.STRATEGY_MARKET_MAKING));
    }

    private static ArbStrategyConfig loadArb(final Config config) {
        final int[] venueIds = intArray(requiredList(config, "strategy.arb.venueIds"), "strategy.arb.venueIds");
        final List<?> takerFees = requiredList(config, "strategy.arb.takerFeeScaled");
        final List<?> baseSlippage = requiredList(config, "strategy.arb.baseSlippageBps");
        final List<?> slope = requiredList(config, "strategy.arb.slippageSlopeBps");
        validateAligned(venueIds, takerFees, "strategy.arb.takerFeeScaled");
        validateAligned(venueIds, baseSlippage, "strategy.arb.baseSlippageBps");
        validateAligned(venueIds, slope, "strategy.arb.slippageSlopeBps");

        final long[] takerFeeByVenue = new long[ARRAY_BY_VENUE_ID_SIZE];
        final long[] baseSlippageByVenue = new long[ARRAY_BY_VENUE_ID_SIZE];
        final long[] slopeByVenue = new long[ARRAY_BY_VENUE_ID_SIZE];
        for (int i = 0; i < venueIds.length; i++) {
            final int venueId = venueIds[i];
            if (venueId < 0 || venueId >= ARRAY_BY_VENUE_ID_SIZE) {
                throw new ConfigValidationException("strategy.arb.venueIds", "venue ID out of supported range: " + venueId);
            }
            takerFeeByVenue[venueId] = parseScaled(asString(takerFees.get(i), "strategy.arb.takerFeeScaled"));
            baseSlippageByVenue[venueId] = asLong(baseSlippage.get(i), "strategy.arb.baseSlippageBps");
            slopeByVenue[venueId] = asLong(slope.get(i), "strategy.arb.slippageSlopeBps");
        }

        return new ArbStrategyConfig(
            requiredInt(config, "strategy.arb.instrumentId"),
            venueIds,
            requiredLong(config, "strategy.arb.minNetProfitBps"),
            takerFeeByVenue,
            baseSlippageByVenue,
            slopeByVenue,
            requiredScaled(config, "strategy.arb.referenceSize"),
            requiredScaled(config, "strategy.arb.maxArbPositionBtc"),
            requiredLong(config, "strategy.arb.maxLegSubmissionGapMicros"),
            requiredLong(config, "strategy.arb.legTimeoutClusterMicros"),
            optionalLong(config, "strategy.arb.cooldownAfterFailureMicros", 0L),
            loadExecutionStrategySelection(config, "strategy.arb", Ids.STRATEGY_ARB));
    }

    private static ExecutionStrategySelectionConfig loadExecutionStrategySelection(
        final Config config,
        final String strategyPath,
        final int tradingStrategyId
    ) {
        final String executionStrategyPath = strategyPath + ".executionStrategy";
        final String configuredName = optionalString(config, executionStrategyPath, null);
        final int executionStrategyId = configuredName == null
            ? ExecutionStrategyIds.defaultForTradingStrategy(tradingStrategyId)
            : ExecutionStrategyIds.parseCanonical(configuredName, executionStrategyPath);
        validateExecutionStrategyCompatibility(tradingStrategyId, executionStrategyId, executionStrategyPath);

        final ExecutionStrategyOverrideConfig[] overrides = loadExecutionStrategyOverrides(
            config,
            strategyPath + ".executionOverride",
            tradingStrategyId);
        return new ExecutionStrategySelectionConfig(executionStrategyId, overrides);
    }

    private static ExecutionStrategyOverrideConfig[] loadExecutionStrategyOverrides(
        final Config config,
        final String fieldPath,
        final int tradingStrategyId
    ) {
        final Object value = config.get(fieldPath);
        if (value == null) {
            return new ExecutionStrategyOverrideConfig[0];
        }
        if (!(value instanceof List<?> list)) {
            throw new ConfigValidationException(fieldPath, "expected array of override tables");
        }

        final ExecutionStrategyOverrideConfig[] overrides = new ExecutionStrategyOverrideConfig[list.size()];
        for (int i = 0; i < list.size(); i++) {
            if (!(list.get(i) instanceof Config override)) {
                throw new ConfigValidationException(fieldPath, "expected override table at index " + i);
            }
            final String indexedPath = fieldPath + "[" + i + "]";
            final int instrumentId = optionalInt(override, "instrumentId",
                indexedPath + ".instrumentId", ExecutionStrategyOverrideConfig.ANY_INSTRUMENT);
            final int venueId = optionalInt(override, "venueId",
                indexedPath + ".venueId", ExecutionStrategyOverrideConfig.ANY_VENUE);
            if (instrumentId == ExecutionStrategyOverrideConfig.ANY_INSTRUMENT
                && venueId == ExecutionStrategyOverrideConfig.ANY_VENUE) {
                throw new ConfigValidationException(indexedPath,
                    "override must set instrumentId, venueId, or both");
            }
            final int executionStrategyId = ExecutionStrategyIds.parseCanonical(
                requiredString(override, "executionStrategy", indexedPath + ".executionStrategy"),
                indexedPath + ".executionStrategy");
            validateExecutionStrategyCompatibility(tradingStrategyId, executionStrategyId,
                indexedPath + ".executionStrategy");
            for (int existing = 0; existing < i; existing++) {
                if (overrides[existing].instrumentId() == instrumentId && overrides[existing].venueId() == venueId) {
                    throw new ConfigValidationException(indexedPath,
                        "duplicate execution strategy override for instrumentId=" + instrumentId
                            + ", venueId=" + venueId);
                }
            }
            overrides[i] = new ExecutionStrategyOverrideConfig(instrumentId, venueId, executionStrategyId);
        }
        return overrides;
    }

    private static void validateExecutionStrategyCompatibility(
        final int tradingStrategyId,
        final int executionStrategyId,
        final String fieldPath
    ) {
        StrategyConfigValidator.validateCompatibility(tradingStrategyId, executionStrategyId, fieldPath);
    }

    private static FixConfig loadFix(final Config config) {
        return FixConfig.builder()
            .senderCompId(requiredString(config, "fix.senderCompId"))
            .targetCompId(requiredString(config, "fix.targetCompId"))
            .heartbeatIntervalS(requiredInt(config, "fix.heartbeatIntervalS"))
            .reconnectIntervalMs(requiredInt(config, "fix.reconnectIntervalMs"))
            .resetSeqNumOnLogon(requiredBoolean(config, "fix.resetSeqNumOnLogon"))
            .artioLogDir(requiredString(config, "fix.artioLogDir"))
            .artioReplayCapacity(requiredInt(config, "fix.artioReplayCapacity"))
            .build();
    }

    private static void validateAligned(final int[] venueIds, final List<?> values, final String fieldPath) {
        if (values.size() != venueIds.length) {
            throw new ConfigValidationException(fieldPath, "must have the same length as strategy.arb.venueIds");
        }
    }

    private static long requiredScaled(final Config config, final String fieldPath) {
        return parseScaled(asString(required(config.get(fieldPath), fieldPath), fieldPath));
    }

    private static String requiredString(final Config config, final String fieldPath) {
        return asString(required(config.get(fieldPath), fieldPath), fieldPath);
    }

    private static String requiredString(final Config config, final String key, final String fieldPath) {
        return asString(required(config.get(key), fieldPath), fieldPath);
    }

    private static String optionalString(final Config config, final String fieldPath, final String defaultValue) {
        final Object value = config.get(fieldPath);
        return value == null ? defaultValue : asString(value, fieldPath);
    }

    private static int requiredInt(final Config config, final String fieldPath) {
        return Math.toIntExact(requiredLong(config, fieldPath));
    }

    private static int requiredInt(final Config config, final String key, final String fieldPath) {
        return Math.toIntExact(asLong(required(config.get(key), fieldPath), fieldPath));
    }

    private static int optionalInt(
        final Config config,
        final String key,
        final String fieldPath,
        final int defaultValue
    ) {
        final Object value = config.get(key);
        return value == null ? defaultValue : Math.toIntExact(asLong(value, fieldPath));
    }

    private static long requiredLong(final Config config, final String fieldPath) {
        return asLong(required(config.get(fieldPath), fieldPath), fieldPath);
    }

    private static long optionalLong(final Config config, final String fieldPath, final long defaultValue) {
        final Object value = config.get(fieldPath);
        return value == null ? defaultValue : asLong(value, fieldPath);
    }

    private static boolean requiredBoolean(final Config config, final String fieldPath) {
        return asBoolean(required(config.get(fieldPath), fieldPath), fieldPath);
    }

    private static boolean requiredBoolean(final Config config, final String key, final String fieldPath) {
        return asBoolean(required(config.get(key), fieldPath), fieldPath);
    }

    private static <E extends Enum<E>> E requiredEnum(
        final Class<E> enumType,
        final Config config,
        final String key,
        final String fieldPath
    ) {
        final String value = requiredString(config, key, fieldPath);
        try {
            return Enum.valueOf(enumType, value);
        } catch (final IllegalArgumentException ex) {
            throw new ConfigValidationException(fieldPath,
                "unsupported value '" + value + "' for " + enumType.getSimpleName());
        }
    }

    private static boolean optionalBoolean(final Config config, final String fieldPath, final boolean defaultValue) {
        final Object value = config.get(fieldPath);
        return value == null ? defaultValue : asBoolean(value, fieldPath);
    }

    private static List<String> requiredStringList(final Config config, final String fieldPath) {
        return requiredList(config, fieldPath).stream()
            .map(value -> asString(value, fieldPath))
            .toList();
    }

    private static List<?> requiredList(final Config config, final String fieldPath) {
        final Object value = required(config.get(fieldPath), fieldPath);
        if (value instanceof List<?> list) {
            return list;
        }
        throw new ConfigValidationException(fieldPath, "expected array");
    }

    private static int[] intArray(final List<?> values, final String fieldPath) {
        final int[] result = new int[values.size()];
        for (int i = 0; i < values.size(); i++) {
            result[i] = Math.toIntExact(asLong(values.get(i), fieldPath));
        }
        return result;
    }

    private static String asString(final Object value, final String fieldPath) {
        if (value instanceof String string) {
            return string;
        }
        if (value instanceof Number number) {
            return number.toString();
        }
        throw new ConfigValidationException(fieldPath, "expected string");
    }

    private static long asLong(final Object value, final String fieldPath) {
        if (value instanceof Number number) {
            return number.longValue();
        }
        if (value instanceof String string) {
            try {
                return Long.parseLong(string);
            } catch (final NumberFormatException ex) {
                throw new ConfigValidationException(fieldPath, "expected integer");
            }
        }
        throw new ConfigValidationException(fieldPath, "expected integer");
    }

    private static boolean asBoolean(final Object value, final String fieldPath) {
        if (value instanceof Boolean bool) {
            return bool;
        }
        throw new ConfigValidationException(fieldPath, "expected boolean");
    }
}
