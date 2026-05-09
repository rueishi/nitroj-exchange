package ig.rueishi.nitroj.exchange.gateway.venue;

import java.util.Map;
import java.util.Objects;
import ig.rueishi.nitroj.exchange.gateway.venue.binance.BinanceVenuePlugin;
import ig.rueishi.nitroj.exchange.gateway.venue.coinbase.CoinbaseVenuePlugin;

/**
 * Registry for concrete venue plugins.
 *
 * <p>Responsibility: resolves configured venue plugin IDs to concrete
 * implementations. Role in system: gateway startup calls this registry after
 * loading {@code venues.toml}, then composes the selected venue plugin with the
 * selected FIX protocol plugin. Relationships: currently registers
 * {@link CoinbaseVenuePlugin}; additional real venues are added by registering
 * concrete plugins, not by creating placeholder future plugins. Lifecycle:
 * created once during gateway wiring or tests and used read-only. Design intent:
 * fail fast on unsupported venue IDs and keep plugin selection deterministic.
 */
public final class VenuePluginRegistry {
    private final Map<String, VenuePlugin> plugins;

    /**
     * Creates the default registry with currently supported venue plugins.
     */
    public VenuePluginRegistry() {
        this(Map.of(
            CoinbaseVenuePlugin.ID, new CoinbaseVenuePlugin(),
            BinanceVenuePlugin.ID, new BinanceVenuePlugin()));
    }

    /**
     * Creates a registry from explicit plugins for tests and future dependency injection.
     *
     * @param registeredPlugins plugins keyed by their declared ID
     * @throws NullPointerException when the map, a key, or a plugin is null
     * @throws IllegalArgumentException when a key does not match the plugin's ID
     */
    public VenuePluginRegistry(final Map<String, VenuePlugin> registeredPlugins) {
        Objects.requireNonNull(registeredPlugins, "registeredPlugins");
        for (Map.Entry<String, VenuePlugin> entry : registeredPlugins.entrySet()) {
            final String id = Objects.requireNonNull(entry.getKey(), "plugin id");
            final VenuePlugin plugin = Objects.requireNonNull(entry.getValue(), "plugin");
            if (!id.equals(plugin.id())) {
                throw new IllegalArgumentException("Venue plugin registered under " + id
                    + " but declares " + plugin.id());
            }
        }
        this.plugins = Map.copyOf(registeredPlugins);
    }

    /**
     * Resolves a venue plugin by config ID.
     *
     * @param id configured venue plugin ID
     * @return matching venue plugin
     * @throws IllegalArgumentException when no plugin is registered for {@code id}
     * @throws NullPointerException when {@code id} is null
     */
    public VenuePlugin get(final String id) {
        final String requiredId = Objects.requireNonNull(id, "id");
        if (requiredId.isBlank()) {
            throw new IllegalArgumentException("Unsupported venue plugin: " + requiredId);
        }
        final VenuePlugin plugin = plugins.get(requiredId);
        if (plugin == null) {
            throw new IllegalArgumentException("Unsupported venue plugin: " + requiredId);
        }
        return plugin;
    }
}
