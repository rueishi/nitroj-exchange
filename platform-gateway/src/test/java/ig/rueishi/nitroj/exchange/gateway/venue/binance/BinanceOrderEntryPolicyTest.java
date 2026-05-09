package ig.rueishi.nitroj.exchange.gateway.venue.binance;

import ig.rueishi.nitroj.exchange.common.VenueCapabilities;
import ig.rueishi.nitroj.exchange.fix.fix44.builder.NewOrderSingleEncoder;
import ig.rueishi.nitroj.exchange.gateway.venue.binance.BinanceOrderEntryPolicy.SelfTradePreventionMode;
import ig.rueishi.nitroj.exchange.messages.OrdType;
import ig.rueishi.nitroj.exchange.messages.TimeInForce;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

final class BinanceOrderEntryPolicyTest {
    @Test
    void selfTradePrevention_allV14ModesMapToFixChars() {
        assertThat(BinanceOrderEntryPolicy.toFixSelfTradeType(SelfTradePreventionMode.STP_NONE)).isEqualTo('1');
        assertThat(BinanceOrderEntryPolicy.toFixSelfTradeType(SelfTradePreventionMode.EXPIRE_TAKER)).isEqualTo('2');
        assertThat(BinanceOrderEntryPolicy.toFixSelfTradeType(SelfTradePreventionMode.EXPIRE_MAKER)).isEqualTo('3');
        assertThat(BinanceOrderEntryPolicy.toFixSelfTradeType(SelfTradePreventionMode.EXPIRE_BOTH)).isEqualTo('4');
        assertThat(BinanceOrderEntryPolicy.toFixSelfTradeType(SelfTradePreventionMode.DECREMENT)).isEqualTo('5');
        assertThat(BinanceOrderEntryPolicy.toFixSelfTradeType(SelfTradePreventionMode.TRANSFER)).isEqualTo('6');
    }

    @Test
    void enrichNewOrder_setsConfiguredStpOnFix44Encoder() {
        final BinanceOrderEntryPolicy policy = new BinanceOrderEntryPolicy(
            new VenueCapabilities(true, true, false), SelfTradePreventionMode.EXPIRE_BOTH);
        final NewOrderSingleEncoder encoder = new NewOrderSingleEncoder();

        policy.enrichNewOrder(null, encoder);

        assertThat(encoder.hasSelfTradePreventionMode()).isTrue();
        assertThat(encoder.selfTradePreventionMode()).isEqualTo('4');
    }

    @Test
    void nativeReplaceSupported_preservesVenueCapability() {
        assertThat(new BinanceOrderEntryPolicy(new VenueCapabilities(true, true, false)).nativeReplaceSupported())
            .isFalse();
        assertThat(new BinanceOrderEntryPolicy(new VenueCapabilities(true, true, true)).nativeReplaceSupported())
            .isTrue();
    }

    @Test
    void orderTypesAndTimeInForce_mapToStandardFix44Values() {
        assertThat(BinanceOrderEntryPolicy.toFixOrdType(OrdType.MARKET)).isEqualTo('1');
        assertThat(BinanceOrderEntryPolicy.toFixOrdType(OrdType.LIMIT)).isEqualTo('2');
        assertThat(BinanceOrderEntryPolicy.toFixTimeInForce(TimeInForce.DAY)).isEqualTo('0');
        assertThat(BinanceOrderEntryPolicy.toFixTimeInForce(TimeInForce.GTC)).isEqualTo('1');
        assertThat(BinanceOrderEntryPolicy.toFixTimeInForce(TimeInForce.IOC)).isEqualTo('3');
        assertThat(BinanceOrderEntryPolicy.toFixTimeInForce(TimeInForce.FOK)).isEqualTo('4');
    }

    @Test
    void invalidInputsFailClearly() {
        assertThatThrownBy(() -> BinanceOrderEntryPolicy.toFixOrdType(OrdType.NULL_VAL))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("order type");
        assertThatThrownBy(() -> BinanceOrderEntryPolicy.toFixTimeInForce(TimeInForce.NULL_VAL))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("time-in-force");
    }
}
