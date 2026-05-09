package ig.rueishi.nitroj.exchange.common.instruments;

import com.electronwill.nightconfig.core.Config;
import com.electronwill.nightconfig.toml.TomlParser;
import ig.rueishi.nitroj.exchange.common.ConfigValidationException;
import ig.rueishi.nitroj.exchange.common.InstrumentConfig;
import ig.rueishi.nitroj.exchange.common.VenueConfig;

import java.io.IOException;
import java.io.Reader;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Validated instrument-to-venue identity registry.
 *
 * <p>Responsibility: loads immutable instrument IDs plus explicit venue symbol
 * mappings from {@code instruments.toml}. Role in system: V14 uses this guard
 * to make the BTC-USD Coinbase product and BTCUSDT Binance product visibly
 * distinct, preventing accidental cross-venue book consolidation or automatic
 * arbitrage between USD and USDT products. Relationships: the loader validates
 * venue mappings against already loaded {@link VenueConfig} entries while
 * preserving the existing {@link InstrumentConfig} startup shape for older code.
 * Lifecycle: built at startup or by tests, then treated as read-only. Design
 * intent: identity decisions live in configuration and are enforced once at
 * startup, with hot paths continuing to use primitive instrument IDs.</p>
 */
public final class InstrumentRegistry {
    private final Map<Integer, InstrumentConfig> byId;
    private final Map<String, VenueInstrument> byIdentityKey;
    private final Map<VenueSymbolKey, VenueInstrument> byVenueSymbol;

    private InstrumentRegistry(
        final Map<Integer, InstrumentConfig> byId,
        final Map<String, VenueInstrument> byIdentityKey,
        final Map<VenueSymbolKey, VenueInstrument> byVenueSymbol) {

        this.byId = Map.copyOf(byId);
        this.byIdentityKey = Map.copyOf(byIdentityKey);
        this.byVenueSymbol = Map.copyOf(byVenueSymbol);
    }

    public static InstrumentRegistry load(final String instrumentsPath, final List<VenueConfig> venues) {
        final Set<Integer> declaredVenueIds = new HashSet<>();
        for (VenueConfig venue : venues) {
            declaredVenueIds.add(venue.id());
        }

        final Config root = parseFile(instrumentsPath);
        final List<?> instruments = requiredList(root, "instrument");
        final Map<Integer, InstrumentConfig> byId = new HashMap<>();
        final Map<String, VenueInstrument> byIdentityKey = new HashMap<>();
        final Map<VenueSymbolKey, VenueInstrument> byVenueSymbol = new HashMap<>();

        for (Object instrumentValue : instruments) {
            final Config instrument = asConfig(instrumentValue, "instrument");
            final int instrumentId = requiredInt(instrument, "id");
            if (byId.containsKey(instrumentId)) {
                throw new ConfigValidationException("duplicate instrumentId: " + instrumentId);
            }
            final InstrumentConfig config = new InstrumentConfig(
                instrumentId,
                requiredString(instrument, "symbol"),
                requiredString(instrument, "baseCurrency"),
                requiredString(instrument, "quoteCurrency"));
            byId.put(instrumentId, config);

            final Object venueMappingsValue = instrument.get("venue");
            if (!(venueMappingsValue instanceof List<?> venueMappings) || venueMappings.isEmpty()) {
                throw new ConfigValidationException("instrument " + instrumentId + " is missing venue mapping");
            }
            for (Object mappingValue : venueMappings) {
                final Config mapping = asConfig(mappingValue, "instrument.venue");
                final int venueId = requiredInt(mapping, "venueId");
                if (!declaredVenueIds.contains(venueId)) {
                    throw new ConfigValidationException("instrument " + instrumentId
                        + " maps to undeclared venueId: " + venueId);
                }
                final String venueSymbol = requiredString(mapping, "venueSymbol");
                final String identityKey = requiredString(mapping, "identityKey");
                final VenueInstrument venueInstrument =
                    new VenueInstrument(instrumentId, venueId, venueSymbol, identityKey);
                if (byIdentityKey.putIfAbsent(identityKey, venueInstrument) != null) {
                    throw new ConfigValidationException("duplicate instrument identityKey: " + identityKey);
                }
                final VenueSymbolKey venueSymbolKey = new VenueSymbolKey(venueId, venueSymbol);
                if (byVenueSymbol.putIfAbsent(venueSymbolKey, venueInstrument) != null) {
                    throw new ConfigValidationException("duplicate venue instrument mapping: venueId="
                        + venueId + " symbol=" + venueSymbol);
                }
            }
        }
        return new InstrumentRegistry(byId, byIdentityKey, byVenueSymbol);
    }

    public InstrumentConfig instrument(final int instrumentId) {
        return byId.get(instrumentId);
    }

    public VenueInstrument byIdentityKey(final String identityKey) {
        return byIdentityKey.get(identityKey);
    }

    public VenueInstrument byVenueSymbol(final int venueId, final String venueSymbol) {
        return byVenueSymbol.get(new VenueSymbolKey(venueId, venueSymbol));
    }

    public int size() {
        return byId.size();
    }

    public record VenueInstrument(int instrumentId, int venueId, String venueSymbol, String identityKey) {
    }

    private record VenueSymbolKey(int venueId, String venueSymbol) {
    }

    private static Config parseFile(final String path) {
        final Path configPath = Path.of(path);
        if (!Files.exists(configPath)) {
            throw new ConfigValidationException("File not found: " + path);
        }
        try (Reader reader = Files.newBufferedReader(configPath)) {
            return new TomlParser().parse(reader);
        } catch (IOException | RuntimeException ex) {
            if (ex instanceof ConfigValidationException validationException) {
                throw validationException;
            }
            throw new ConfigValidationException(path, "unable to parse TOML: " + ex.getMessage());
        }
    }

    private static List<?> requiredList(final Config config, final String fieldPath) {
        final Object value = config.get(fieldPath);
        if (value instanceof List<?> list) {
            return list;
        }
        throw new ConfigValidationException(fieldPath, "expected array");
    }

    private static Config asConfig(final Object value, final String fieldPath) {
        if (value instanceof Config config) {
            return config;
        }
        throw new ConfigValidationException(fieldPath, "expected table");
    }

    private static int requiredInt(final Config config, final String fieldPath) {
        final Object value = config.get(fieldPath);
        if (value instanceof Number number) {
            return number.intValue();
        }
        throw new ConfigValidationException(fieldPath, "expected integer");
    }

    private static String requiredString(final Config config, final String fieldPath) {
        final Object value = config.get(fieldPath);
        if (value instanceof String string && !string.isBlank()) {
            return string;
        }
        throw new ConfigValidationException(fieldPath, "expected non-blank string");
    }
}
