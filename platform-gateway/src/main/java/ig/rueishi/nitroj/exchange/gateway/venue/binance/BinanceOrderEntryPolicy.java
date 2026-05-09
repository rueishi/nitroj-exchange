package ig.rueishi.nitroj.exchange.gateway.venue.binance;

import ig.rueishi.nitroj.exchange.common.VenueCapabilities;
import ig.rueishi.nitroj.exchange.fix.fix44.builder.NewOrderSingleEncoder;
import ig.rueishi.nitroj.exchange.messages.NewOrderCommandDecoder;
import ig.rueishi.nitroj.exchange.messages.OrdType;
import ig.rueishi.nitroj.exchange.messages.TimeInForce;

import java.util.EnumMap;
import java.util.Map;
import java.util.Objects;

/**
 * Binance Spot order-entry policy for generated FIX 4.4 messages.
 */
public final class BinanceOrderEntryPolicy {
    public enum SelfTradePreventionMode {
        STP_NONE,
        EXPIRE_TAKER,
        EXPIRE_MAKER,
        EXPIRE_BOTH,
        DECREMENT,
        TRANSFER
    }

    private static final Map<SelfTradePreventionMode, Character> FIX_STP = new EnumMap<>(SelfTradePreventionMode.class);

    static {
        FIX_STP.put(SelfTradePreventionMode.STP_NONE, '1');
        FIX_STP.put(SelfTradePreventionMode.EXPIRE_TAKER, '2');
        FIX_STP.put(SelfTradePreventionMode.EXPIRE_MAKER, '3');
        FIX_STP.put(SelfTradePreventionMode.EXPIRE_BOTH, '4');
        FIX_STP.put(SelfTradePreventionMode.DECREMENT, '5');
        FIX_STP.put(SelfTradePreventionMode.TRANSFER, '6');
    }

    private final VenueCapabilities capabilities;
    private final SelfTradePreventionMode stpMode;

    public BinanceOrderEntryPolicy(final VenueCapabilities capabilities) {
        this(capabilities, SelfTradePreventionMode.EXPIRE_TAKER);
    }

    public BinanceOrderEntryPolicy(
        final VenueCapabilities capabilities,
        final SelfTradePreventionMode stpMode) {

        this.capabilities = Objects.requireNonNull(capabilities, "capabilities");
        this.stpMode = Objects.requireNonNull(stpMode, "stpMode");
    }

    public boolean nativeReplaceSupported() {
        return capabilities.nativeReplaceSupported();
    }

    public SelfTradePreventionMode selfTradePreventionMode() {
        return stpMode;
    }

    public void enrichNewOrder(final NewOrderCommandDecoder command, final NewOrderSingleEncoder encoder) {
        Objects.requireNonNull(encoder, "encoder");
        encoder.selfTradePreventionMode(toFixSelfTradeType(stpMode));
        if (command != null) {
            encoder.ordType(toFixOrdType(command.ordType()));
            encoder.timeInForce(toFixTimeInForce(command.timeInForce()));
        }
    }

    static char toFixSelfTradeType(final SelfTradePreventionMode mode) {
        final Character mapped = FIX_STP.get(Objects.requireNonNull(mode, "mode"));
        if (mapped == null) {
            throw new IllegalArgumentException("Unsupported Binance STP mode: " + mode);
        }
        return mapped;
    }

    static char toFixOrdType(final OrdType ordType) {
        return switch (Objects.requireNonNull(ordType, "ordType")) {
            case MARKET -> '1';
            case LIMIT -> '2';
            default -> throw new IllegalArgumentException("Unsupported Binance order type: " + ordType);
        };
    }

    static char toFixTimeInForce(final TimeInForce timeInForce) {
        return switch (Objects.requireNonNull(timeInForce, "timeInForce")) {
            case DAY -> '0';
            case GTC -> '1';
            case IOC -> '3';
            case FOK -> '4';
            default -> throw new IllegalArgumentException("Unsupported Binance time-in-force: " + timeInForce);
        };
    }
}
