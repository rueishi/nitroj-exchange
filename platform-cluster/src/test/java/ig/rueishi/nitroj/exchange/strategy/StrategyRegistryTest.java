package ig.rueishi.nitroj.exchange.strategy;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import ig.rueishi.nitroj.exchange.common.ConfigValidationException;
import ig.rueishi.nitroj.exchange.common.Ids;
import ig.rueishi.nitroj.exchange.messages.ExecutionEventDecoder;
import ig.rueishi.nitroj.exchange.messages.MarketDataEventDecoder;
import org.junit.jupiter.api.Test;

/**
 * TASK-413A coverage for the cold-path strategy registry used by V14 strategy
 * registration work. Runtime fan-out remains owned by {@link StrategyEngine}.
 */
final class StrategyRegistryTest {
    @Test
    void registersAndLooksUpInventoryHedgeStrategyIdWithoutAffectingExistingIds() {
        final StrategyRegistry registry = new StrategyRegistry(2);
        final NoopStrategy hedge = new NoopStrategy(StrategyRegistry.STRATEGY_INVENTORY_HEDGE);

        registry.register(hedge);

        assertThat(registry.lookup(StrategyRegistry.STRATEGY_INVENTORY_HEDGE)).isSameAs(hedge);
        assertThat(registry.isKnownBuiltIn(Ids.STRATEGY_MARKET_MAKING)).isTrue();
        assertThat(registry.isKnownBuiltIn(Ids.STRATEGY_ARB)).isTrue();
        assertThat(registry.isKnownBuiltIn(StrategyRegistry.STRATEGY_INVENTORY_HEDGE)).isTrue();
        assertThat(registry.registeredCount()).isEqualTo(1);
    }

    @Test
    void duplicateInvalidAndCapacityFailuresAreClear() {
        final StrategyRegistry registry = new StrategyRegistry(1);
        registry.register(new NoopStrategy(StrategyRegistry.STRATEGY_INVENTORY_HEDGE));

        assertThatThrownBy(() -> registry.register(new NoopStrategy(StrategyRegistry.STRATEGY_INVENTORY_HEDGE)))
            .isInstanceOf(ConfigValidationException.class)
            .hasMessageContaining("duplicate");
        assertThatThrownBy(() -> new StrategyRegistry(0))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("capacity");
        assertThatThrownBy(() -> new StrategyRegistry(1).register(new NoopStrategy(0)))
            .isInstanceOf(ConfigValidationException.class)
            .hasMessageContaining("positive");
        assertThatThrownBy(() -> registry.register(new NoopStrategy(Ids.STRATEGY_ARB)))
            .isInstanceOf(ConfigValidationException.class)
            .hasMessageContaining("capacity");
    }

    private record NoopStrategy(int strategyId) implements Strategy {
        @Override public void init(final StrategyContext ctx) { }
        @Override public void onMarketData(final MarketDataEventDecoder decoder) { }
        @Override public void onFill(final ExecutionEventDecoder decoder) { }
        @Override public void onOrderRejected(final long clOrdId, final byte rejectCode, final int venueId) { }
        @Override public void onKillSwitch() { }
        @Override public void onKillSwitchCleared() { }
        @Override public void onVenueRecovered(final int venueId) { }
        @Override public void onPositionUpdate(final int venueId, final int instrumentId, final long netQtyScaled, final long avgEntryScaled) { }
        @Override public int[] subscribedInstrumentIds() { return new int[0]; }
        @Override public int[] activeVenueIds() { return new int[0]; }
        @Override public void shutdown() { }
    }
}
