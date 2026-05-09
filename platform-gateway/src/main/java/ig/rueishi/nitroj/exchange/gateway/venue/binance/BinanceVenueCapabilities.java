package ig.rueishi.nitroj.exchange.gateway.venue.binance;

import ig.rueishi.nitroj.exchange.common.MarketDataModel;
import ig.rueishi.nitroj.exchange.common.VenueCapabilities;
import ig.rueishi.nitroj.exchange.common.VenueConfig;

import java.util.Objects;

/**
 * Binance venue capability validation.
 *
 * <p>Responsibility: keeps V14's Binance static capability declaration in one
 * testable place. Role in system: {@link BinanceVenuePlugin} calls this at
 * startup after {@code venues.toml} parsing and before gateway wiring.
 * Relationships: shared {@link VenueCapabilities} remains the immutable common
 * record consumed by gateway code. Lifecycle: cold-path validation only.</p>
 */
public final class BinanceVenueCapabilities {
    private BinanceVenueCapabilities() {
    }

    public static VenueCapabilities validate(final VenueConfig venue) {
        final VenueConfig required = Objects.requireNonNull(venue, "venue");
        if (!BinanceVenuePlugin.ID.equals(required.venuePlugin())) {
            throw new IllegalArgumentException("Binance venue plugin cannot serve venuePlugin="
                + required.venuePlugin());
        }
        if (required.id() != BinanceVenuePlugin.VENUE_ID) {
            throw new IllegalArgumentException("Binance venue must use immutable venue ID "
                + BinanceVenuePlugin.VENUE_ID + ": " + required.id());
        }
        if (required.marketDataModel() != MarketDataModel.L2) {
            throw new IllegalArgumentException("Binance Spot FIX marketDataModel must be L2");
        }
        return required.capabilities();
    }
}
