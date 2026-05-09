package ig.rueishi.nitroj.exchange.common.instruments;

import ig.rueishi.nitroj.exchange.common.ConfigManager;
import ig.rueishi.nitroj.exchange.common.ConfigValidationException;
import ig.rueishi.nitroj.exchange.common.Ids;
import ig.rueishi.nitroj.exchange.common.VenueConfig;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * TASK-407 coverage for V14 instrument-to-venue identity.
 *
 * <p>Applicable categories: positive loading, negative validation, malformed
 * mapping input, duplicate capacity-like ID protection, and documentation drift
 * around USD/USDT identity. Non-applicable categories: allocation/latency
 * evidence, snapshot/load, and strategy integration, because this task changes
 * startup configuration validation only; strategy and E2E behavior is owned by
 * later V14 task cards.</p>
 */
final class InstrumentRegistryTest {
    private static final Path CONFIG_DIR = Path.of("..", "config");

    @TempDir
    private Path tempDir;

    @Test
    void loadRepositoryInstruments_keepsCoinbaseUsdAndBinanceUsdtDistinct() {
        final List<VenueConfig> venues = ConfigManager.loadVenues(CONFIG_DIR.resolve("venues.toml").toString());
        final InstrumentRegistry registry =
            InstrumentRegistry.load(CONFIG_DIR.resolve("instruments.toml").toString(), venues);

        assertThat(registry.size()).isGreaterThanOrEqualTo(3);
        assertThat(registry.byIdentityKey("BTC-USD-COINBASE").instrumentId())
            .isEqualTo(Ids.INSTRUMENT_BTC_USD);
        assertThat(registry.byIdentityKey("BTC-USDT-BINANCE").instrumentId())
            .isEqualTo(Ids.INSTRUMENT_BTC_USDT);
        assertThat(registry.byVenueSymbol(Ids.VENUE_COINBASE, "BTC-USD").instrumentId())
            .isEqualTo(Ids.INSTRUMENT_BTC_USD);
        assertThat(registry.byVenueSymbol(Ids.VENUE_BINANCE, "BTCUSDT").instrumentId())
            .isEqualTo(Ids.INSTRUMENT_BTC_USDT);
        assertThat(registry.byIdentityKey("BTC-USD-COINBASE").instrumentId())
            .isNotEqualTo(registry.byIdentityKey("BTC-USDT-BINANCE").instrumentId());
    }

    @Test
    void duplicateInstrumentId_rejected() throws IOException {
        final Path file = write("""
            [[instrument]]
            id = 1
            symbol = "BTC-USD"
            baseCurrency = "BTC"
            quoteCurrency = "USD"
            [[instrument.venue]]
            venueId = 1
            venueSymbol = "BTC-USD"
            identityKey = "BTC-USD-COINBASE"

            [[instrument]]
            id = 1
            symbol = "BTCUSDT"
            baseCurrency = "BTC"
            quoteCurrency = "USDT"
            [[instrument.venue]]
            venueId = 2
            venueSymbol = "BTCUSDT"
            identityKey = "BTC-USDT-BINANCE"
            """);

        assertThatThrownBy(() -> InstrumentRegistry.load(file.toString(), venues()))
            .isInstanceOf(ConfigValidationException.class)
            .hasMessageContaining("duplicate instrumentId");
    }

    @Test
    void missingVenueMapping_rejected() throws IOException {
        final Path file = write("""
            [[instrument]]
            id = 7
            symbol = "BTCUSDT"
            baseCurrency = "BTC"
            quoteCurrency = "USDT"
            """);

        assertThatThrownBy(() -> InstrumentRegistry.load(file.toString(), venues()))
            .isInstanceOf(ConfigValidationException.class)
            .hasMessageContaining("missing venue mapping");
    }

    @Test
    void undeclaredVenueMapping_rejected() throws IOException {
        final Path file = write("""
            [[instrument]]
            id = 7
            symbol = "BTCUSDT"
            baseCurrency = "BTC"
            quoteCurrency = "USDT"
            [[instrument.venue]]
            venueId = 99
            venueSymbol = "BTCUSDT"
            identityKey = "BTC-USDT-BINANCE"
            """);

        assertThatThrownBy(() -> InstrumentRegistry.load(file.toString(), venues()))
            .isInstanceOf(ConfigValidationException.class)
            .hasMessageContaining("undeclared venueId");
    }

    private Path write(final String toml) throws IOException {
        final Path file = tempDir.resolve("instruments.toml");
        Files.writeString(file, toml);
        return file;
    }

    private static List<VenueConfig> venues() {
        return ConfigManager.loadVenues(CONFIG_DIR.resolve("venues.toml").toString());
    }
}
