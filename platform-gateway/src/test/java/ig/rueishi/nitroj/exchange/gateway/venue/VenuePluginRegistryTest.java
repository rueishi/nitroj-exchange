package ig.rueishi.nitroj.exchange.gateway.venue;

import ig.rueishi.nitroj.exchange.common.CredentialsConfig;
import ig.rueishi.nitroj.exchange.common.FixPluginId;
import ig.rueishi.nitroj.exchange.common.MarketDataModel;
import ig.rueishi.nitroj.exchange.common.VenueCapabilities;
import ig.rueishi.nitroj.exchange.common.VenueConfig;
import ig.rueishi.nitroj.exchange.gateway.venue.binance.BinanceVenuePlugin;
import ig.rueishi.nitroj.exchange.gateway.venue.coinbase.CoinbaseVenuePlugin;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Unit coverage for TASK-104 venue plugin lookup and Coinbase plugin shell.
 *
 * <p>Responsibility: verifies concrete Coinbase plugin selection, unsupported
 * venue failure, capability exposure, and logon customizer construction. Role in
 * system: protects the V11 venue-extension boundary before order-entry and
 * market-data adapters are added in later tasks. Relationships: uses real common
 * venue/credential records and the first concrete {@link CoinbaseVenuePlugin}.
 * Lifecycle: runs with gateway unit tests and does not open any FIX session.
 * Design intent: keep venue plugin behavior real and minimal, with no generic
 * runtime venue plugin or future-venue placeholder.
 */
final class VenuePluginRegistryTest {

    /**
     * Verifies the default registry resolves Coinbase by its config ID.
     */
    @Test
    void get_coinbase_returnsCoinbasePlugin() {
        final VenuePlugin plugin = new VenuePluginRegistry().get("COINBASE");

        assertThat(plugin).isInstanceOf(CoinbaseVenuePlugin.class);
        assertThat(plugin.id()).isEqualTo("COINBASE");
    }

    @Test
    void get_binance_returnsBinancePlugin() {
        final VenuePlugin plugin = new VenuePluginRegistry().get("BINANCE");

        assertThat(plugin).isInstanceOf(BinanceVenuePlugin.class);
        assertThat(plugin.id()).isEqualTo("BINANCE");
    }

    /**
     * Verifies unsupported venue IDs fail at startup lookup time.
     */
    @Test
    void get_unsupportedVenue_throwsIllegalArgumentException() {
        assertThatThrownBy(() -> new VenuePluginRegistry().get("UNKNOWN"))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("UNKNOWN");
    }

    /**
     * Verifies null IDs are reported as caller errors rather than unsupported venues.
     */
    @Test
    void get_nullVenueId_throwsNullPointerException() {
        assertThatThrownBy(() -> new VenuePluginRegistry().get(null))
            .isInstanceOf(NullPointerException.class)
            .hasMessageContaining("id");
    }

    @Test
    void get_blankVenueId_throwsIllegalArgumentException() {
        assertThatThrownBy(() -> new VenuePluginRegistry().get(" "))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("Unsupported venue plugin");
    }

    /**
     * Verifies explicit registry construction rejects key/plugin ID mismatches.
     */
    @Test
    void constructor_mismatchedPluginId_throwsIllegalArgumentException() {
        final VenuePlugin mismatched = new StubVenuePlugin("OTHER");

        assertThatThrownBy(() -> new VenuePluginRegistry(Map.of("COINBASE", mismatched)))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("COINBASE")
            .hasMessageContaining("OTHER");
    }

    /**
     * Verifies Coinbase capabilities are read from venue config without hidden defaults.
     */
    @Test
    void capabilities_coinbase_returnsConfiguredCapabilities() {
        final VenueCapabilities capabilities = new VenueCapabilities(true, true, false);
        final VenueConfig venue = new VenueConfig(1, "COINBASE", "localhost", 4198, false,
            FixPluginId.FIXT11_FIX50SP2, "COINBASE", MarketDataModel.L3, capabilities);

        assertThat(new CoinbaseVenuePlugin().capabilities(venue)).isSameAs(capabilities);
    }

    @Test
    void capabilities_binance_requiresVenueIdTwoFix44AndL2() {
        final VenueCapabilities capabilities = new VenueCapabilities(true, true, false);
        final VenueConfig venue = new VenueConfig(2, "BINANCE", "fix-oe.binance.com", 9000, false,
            FixPluginId.FIX_44, "BINANCE", MarketDataModel.L2, capabilities);

        assertThat(new BinanceVenuePlugin().capabilities(venue)).isSameAs(capabilities);
    }

    /**
     * Verifies Coinbase exposes a credential-backed logon customizer.
     */
    @Test
    void logonCustomizer_validCredentials_returnsCustomizer() {
        final CredentialsConfig credentials = CredentialsConfig.hardcoded("key", "dGVzdA==", "passphrase");

        assertThat(new CoinbaseVenuePlugin().logonCustomizer(credentials)).isNotNull();
    }

    /**
     * Verifies missing Coinbase credentials fail while constructing the customizer,
     * before any FIX logon is attempted.
     */
    @Test
    void logonCustomizer_missingCredential_throwsNullPointerException() {
        final CredentialsConfig credentials = new CredentialsConfig("vault", null, "dGVzdA==", "passphrase");

        assertThatThrownBy(() -> new CoinbaseVenuePlugin().logonCustomizer(credentials))
            .isInstanceOf(NullPointerException.class)
            .hasMessageContaining("apiKey");
    }

    private record StubVenuePlugin(String id) implements VenuePlugin {
        @Override
        public VenueCapabilities capabilities(final VenueConfig venue) {
            return venue.capabilities();
        }

        @Override
        public VenueLogonCustomizer logonCustomizer(final CredentialsConfig credentials) {
            return (logon, sessionId) -> { };
        }
    }
}
