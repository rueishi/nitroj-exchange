package ig.rueishi.nitroj.exchange.gateway.venue.binance;

import ig.rueishi.nitroj.exchange.common.CredentialsConfig;
import ig.rueishi.nitroj.exchange.common.FixPluginId;
import ig.rueishi.nitroj.exchange.common.MarketDataModel;
import ig.rueishi.nitroj.exchange.common.VenueCapabilities;
import ig.rueishi.nitroj.exchange.common.VenueConfig;
import ig.rueishi.nitroj.exchange.common.credentials.CredentialResolver;
import ig.rueishi.nitroj.exchange.gateway.ExecutionRouterImpl;
import ig.rueishi.nitroj.exchange.gateway.GatewayDisruptor;
import ig.rueishi.nitroj.exchange.gateway.marketdata.MarketDataNormalizer;
import ig.rueishi.nitroj.exchange.gateway.venue.VenueLogonCustomizer;
import ig.rueishi.nitroj.exchange.gateway.venue.VenueOrderEntryAdapter;
import ig.rueishi.nitroj.exchange.gateway.venue.VenuePlugin;
import ig.rueishi.nitroj.exchange.registry.IdRegistry;

import java.time.Clock;
import java.util.Map;
import java.util.Objects;

/**
 * Venue plugin for Binance Spot FIX 4.4 behavior.
 */
public final class BinanceVenuePlugin implements VenuePlugin {
    public static final String ID = "BINANCE";
    public static final int VENUE_ID = 2;

    private final BinanceCredentialResolver credentialResolver = new BinanceCredentialResolver();

    @Override
    public String id() {
        return ID;
    }

    @Override
    public VenueCapabilities capabilities(final VenueConfig venue) {
        final VenueConfig required = Objects.requireNonNull(venue, "venue");
        if (required.fixPlugin() != FixPluginId.FIX_44) {
            throw new IllegalArgumentException("Binance Spot requires FIX_44 protocol plugin");
        }
        if (required.marketDataModel() != MarketDataModel.L2) {
            throw new IllegalArgumentException("Binance Spot requires L2 market data");
        }
        return BinanceVenueCapabilities.validate(required);
    }

    @Override
    public CredentialsConfig resolveCredentials(
        final CredentialsConfig credentials,
        final Map<String, String> environment) {

        final CredentialResolver.ResolvedCredential resolved = credentialResolver.resolve(credentials, environment);
        return resolved.credentials();
    }

    @Override
    public VenueLogonCustomizer logonCustomizer(final CredentialsConfig credentials) {
        final BinanceLogonCustomization customization = new BinanceLogonCustomization(credentials);
        return customization::configureLogon;
    }

    @Override
    public MarketDataNormalizer marketDataNormalizer(
        final VenueConfig venue,
        final IdRegistry idRegistry,
        final GatewayDisruptor disruptor) {

        capabilities(venue);
        return new BinanceL2MarketDataNormalizer(idRegistry, disruptor);
    }

    @Override
    public VenueOrderEntryAdapter orderEntryAdapter(
        final VenueConfig venue,
        final ExecutionRouterImpl.FixSender sender,
        final IdRegistry idRegistry,
        final String account,
        final Runnable backPressureCounter,
        final ExecutionRouterImpl.RejectPublisher rejectPublisher) {

        return new BinanceFix44OrderEntryAdapter(
            sender,
            idRegistry,
            account,
            backPressureCounter,
            rejectPublisher,
            Clock.systemUTC(),
            new BinanceOrderEntryPolicy(capabilities(venue)));
    }
}
