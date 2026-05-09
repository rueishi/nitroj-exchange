package ig.rueishi.nitroj.exchange.gateway.venue.binance;

import ig.rueishi.nitroj.exchange.fix.fix44.builder.NewOrderSingleEncoder;
import ig.rueishi.nitroj.exchange.fix.fix44.builder.OrderCancelReplaceRequestEncoder;
import ig.rueishi.nitroj.exchange.fix.fix44.builder.OrderCancelRequestEncoder;
import ig.rueishi.nitroj.exchange.fix.fix44.builder.OrderStatusRequestEncoder;
import ig.rueishi.nitroj.exchange.gateway.ExecutionRouterImpl;
import ig.rueishi.nitroj.exchange.gateway.venue.VenueOrderEntryAdapter;
import ig.rueishi.nitroj.exchange.messages.CancelOrderCommandDecoder;
import ig.rueishi.nitroj.exchange.messages.NewOrderCommandDecoder;
import ig.rueishi.nitroj.exchange.messages.OrderStatusQueryCommandDecoder;
import ig.rueishi.nitroj.exchange.messages.ReplaceOrderCommandDecoder;
import ig.rueishi.nitroj.exchange.messages.Side;
import ig.rueishi.nitroj.exchange.registry.IdRegistry;
import uk.co.real_logic.artio.builder.Encoder;

import java.time.Clock;
import java.util.Objects;
import java.util.concurrent.locks.LockSupport;

/**
 * Binance FIX 4.4 order-entry adapter.
 */
final class BinanceFix44OrderEntryAdapter implements VenueOrderEntryAdapter {
    private static final int MAX_SEND_ATTEMPTS = 3;
    private static final long BACK_PRESSURE_SLEEP_NANOS = 1_000L;
    private static final int DECIMAL_SCALE = 8;
    private static final int MAX_LONG_ASCII_LENGTH = 20;
    private static final int MAX_SYMBOL_BYTES = 64;
    private static final int MAX_VENUE_ORDER_ID_BYTES = 64;
    private static final int FIX_TIMESTAMP_LENGTH = 21;
    private static final long MILLIS_PER_DAY = 86_400_000L;
    private static final long MILLIS_PER_HOUR = 3_600_000L;
    private static final long MILLIS_PER_MINUTE = 60_000L;
    private static final long MILLIS_PER_SECOND = 1_000L;
    private static final byte[] EMPTY_BYTES = new byte[0];

    private final ExecutionRouterImpl.FixSender sender;
    private final IdRegistry idRegistry;
    private final String account;
    private final Runnable backPressureCounter;
    private final ExecutionRouterImpl.RejectPublisher rejectPublisher;
    private final Clock clock;
    private final BinanceOrderEntryPolicy policy;
    private final NewOrderSingleEncoder newOrderEncoder = new NewOrderSingleEncoder();
    private final OrderCancelRequestEncoder cancelEncoder = new OrderCancelRequestEncoder();
    private final OrderCancelReplaceRequestEncoder replaceEncoder = new OrderCancelReplaceRequestEncoder();
    private final OrderStatusRequestEncoder statusEncoder = new OrderStatusRequestEncoder();
    private final byte[] primaryIdBytes = new byte[MAX_LONG_ASCII_LENGTH];
    private final byte[] secondaryIdBytes = new byte[MAX_LONG_ASCII_LENGTH];
    private final byte[] symbolBytes = new byte[MAX_SYMBOL_BYTES];
    private final byte[] timestampBytes = new byte[FIX_TIMESTAMP_LENGTH];
    private final byte[] venueOrderIdBytes = new byte[MAX_VENUE_ORDER_ID_BYTES];

    BinanceFix44OrderEntryAdapter(
        final ExecutionRouterImpl.FixSender sender,
        final IdRegistry idRegistry,
        final String account,
        final Runnable backPressureCounter,
        final ExecutionRouterImpl.RejectPublisher rejectPublisher,
        final Clock clock,
        final BinanceOrderEntryPolicy policy) {

        this.sender = Objects.requireNonNull(sender, "sender");
        this.idRegistry = Objects.requireNonNull(idRegistry, "idRegistry");
        this.account = Objects.requireNonNull(account, "account");
        this.backPressureCounter = Objects.requireNonNull(backPressureCounter, "backPressureCounter");
        this.rejectPublisher = Objects.requireNonNull(rejectPublisher, "rejectPublisher");
        this.clock = Objects.requireNonNull(clock, "clock");
        this.policy = Objects.requireNonNull(policy, "policy");
    }

    @Override
    public void sendNewOrder(final NewOrderCommandDecoder command) {
        newOrderEncoder.resetMessage();
        newOrderEncoder.account(account);
        newOrderEncoder.clOrdID(primaryIdBytes, 0, putUnsignedLongAscii(command.clOrdId(), primaryIdBytes));
        newOrderEncoder.handlInst('1');
        newOrderEncoder.symbol(symbolBytes, 0, copySymbol(command.instrumentId()));
        newOrderEncoder.side(fixSide(command.side()));
        newOrderEncoder.transactTime(timestampBytes, 0, formatTimestamp());
        newOrderEncoder.orderQty(command.qtyScaled(), DECIMAL_SCALE);
        newOrderEncoder.ordType(BinanceOrderEntryPolicy.toFixOrdType(command.ordType()));
        if (command.priceScaled() > 0) {
            newOrderEncoder.price(command.priceScaled(), DECIMAL_SCALE);
        }
        newOrderEncoder.timeInForce(BinanceOrderEntryPolicy.toFixTimeInForce(command.timeInForce()));
        policy.enrichNewOrder(command, newOrderEncoder);
        sendWithRetry(newOrderEncoder, command.clOrdId(), command.venueId(), command.instrumentId());
    }

    @Override
    public void sendCancel(final CancelOrderCommandDecoder command) {
        cancelEncoder.resetMessage();
        cancelEncoder.origClOrdID(primaryIdBytes, 0, putUnsignedLongAscii(command.origClOrdId(), primaryIdBytes));
        copyVenueOrderId(command, cancelEncoder);
        cancelEncoder.clOrdID(secondaryIdBytes, 0, putUnsignedLongAscii(command.cancelClOrdId(), secondaryIdBytes));
        cancelEncoder.symbol(symbolBytes, 0, copySymbol(command.instrumentId()));
        cancelEncoder.side(fixSide(command.side()));
        cancelEncoder.transactTime(timestampBytes, 0, formatTimestamp());
        cancelEncoder.orderQty(command.originalQtyScaled(), DECIMAL_SCALE);
        sendWithRetry(cancelEncoder, command.cancelClOrdId(), command.venueId(), command.instrumentId());
    }

    @Override
    public void sendReplace(final ReplaceOrderCommandDecoder command) {
        if (!policy.nativeReplaceSupported()) {
            return;
        }
        replaceEncoder.resetMessage();
        copyVenueOrderId(command, replaceEncoder);
        replaceEncoder.origClOrdID(primaryIdBytes, 0, putUnsignedLongAscii(command.origClOrdId(), primaryIdBytes));
        replaceEncoder.clOrdID(secondaryIdBytes, 0, putUnsignedLongAscii(command.newClOrdId(), secondaryIdBytes));
        replaceEncoder.handlInst('1');
        replaceEncoder.symbol(symbolBytes, 0, copySymbol(command.instrumentId()));
        replaceEncoder.side(fixSide(command.side()));
        replaceEncoder.transactTime(timestampBytes, 0, formatTimestamp());
        replaceEncoder.orderQty(command.newQtyScaled(), DECIMAL_SCALE);
        replaceEncoder.ordType('2');
        replaceEncoder.price(command.newPriceScaled(), DECIMAL_SCALE);
        replaceEncoder.timeInForce('1');
        sendWithRetry(replaceEncoder, command.newClOrdId(), command.venueId(), command.instrumentId());
    }

    @Override
    public void sendStatusQuery(final OrderStatusQueryCommandDecoder command) {
        statusEncoder.resetMessage();
        statusEncoder.clOrdID(primaryIdBytes, 0, putUnsignedLongAscii(command.clOrdId(), primaryIdBytes));
        copyVenueOrderId(command, statusEncoder);
        statusEncoder.symbol(symbolBytes, 0, copySymbol(command.instrumentId()));
        statusEncoder.side(fixSide(command.side()));
        sendWithRetry(statusEncoder, command.clOrdId(), command.venueId(), command.instrumentId());
    }

    private boolean sendWithRetry(final Encoder encoder, final long clOrdId, final int venueId, final int instrumentId) {
        for (int attempt = 0; attempt < MAX_SEND_ATTEMPTS; attempt++) {
            final long result = sender.trySend(encoder);
            if (result >= 0) {
                return true;
            }
            LockSupport.parkNanos(BACK_PRESSURE_SLEEP_NANOS);
        }
        backPressureCounter.run();
        rejectPublisher.publishRejected(clOrdId, venueId, instrumentId);
        return false;
    }

    private int copySymbol(final int instrumentId) {
        final String symbol = idRegistry.symbolOf(instrumentId);
        if (symbol == null) {
            throw new IllegalArgumentException("Unknown instrumentId: " + instrumentId);
        }
        final int length = symbol.length();
        if (length > symbolBytes.length) {
            throw new IllegalArgumentException("FIX symbol too long for reusable buffer: " + symbol);
        }
        for (int i = 0; i < length; i++) {
            symbolBytes[i] = (byte)symbol.charAt(i);
        }
        return length;
    }

    private int formatTimestamp() {
        formatUtcMillis(clock.millis(), timestampBytes);
        return FIX_TIMESTAMP_LENGTH;
    }

    private static char fixSide(final Side side) {
        return side == Side.BUY ? '1' : '2';
    }

    private void copyVenueOrderId(final CancelOrderCommandDecoder command, final OrderCancelRequestEncoder encoder) {
        final int length = copyVenueOrderId(command.venueOrderIdLength(), command::getVenueOrderId);
        if (length == 0) {
            encoder.orderID(EMPTY_BYTES);
        } else {
            encoder.orderID(venueOrderIdBytes, 0, length);
        }
    }

    private void copyVenueOrderId(final ReplaceOrderCommandDecoder command, final OrderCancelReplaceRequestEncoder encoder) {
        final int length = copyVenueOrderId(command.venueOrderIdLength(), command::getVenueOrderId);
        if (length == 0) {
            encoder.orderID(EMPTY_BYTES);
        } else {
            encoder.orderID(venueOrderIdBytes, 0, length);
        }
    }

    private void copyVenueOrderId(final OrderStatusQueryCommandDecoder command, final OrderStatusRequestEncoder encoder) {
        final int length = copyVenueOrderId(command.venueOrderIdLength(), command::getVenueOrderId);
        if (length == 0) {
            encoder.orderID(EMPTY_BYTES);
        } else {
            encoder.orderID(venueOrderIdBytes, 0, length);
        }
    }

    private int copyVenueOrderId(final int length, final VenueOrderIdReader reader) {
        if (length > MAX_VENUE_ORDER_ID_BYTES) {
            throw new IllegalArgumentException("venue order ID too long for reusable buffer: " + length);
        }
        reader.read(venueOrderIdBytes, 0, length);
        return length;
    }

    private static int putUnsignedLongAscii(final long value, final byte[] target) {
        if (value < 0) {
            throw new IllegalArgumentException("FIX numeric ID must be non-negative: " + value);
        }
        long remaining = value;
        int length = 0;
        do {
            target[length++] = (byte)('0' + (remaining % 10));
            remaining /= 10;
        } while (remaining != 0);
        for (int left = 0, right = length - 1; left < right; left++, right--) {
            final byte swap = target[left];
            target[left] = target[right];
            target[right] = swap;
        }
        return length;
    }

    private static void formatUtcMillis(final long epochMillis, final byte[] target) {
        final long epochDay = Math.floorDiv(epochMillis, MILLIS_PER_DAY);
        int millisOfDay = (int)Math.floorMod(epochMillis, MILLIS_PER_DAY);
        final long z = epochDay + 719_468L;
        final long era = Math.floorDiv(z, 146_097L);
        final long doe = z - era * 146_097L;
        final long yoe = (doe - doe / 1_460L + doe / 36_524L - doe / 146_096L) / 365L;
        int year = (int)(yoe + era * 400L);
        final long doy = doe - (365L * yoe + yoe / 4L - yoe / 100L);
        final long mp = (5L * doy + 2L) / 153L;
        final int day = (int)(doy - (153L * mp + 2L) / 5L + 1L);
        final int month = (int)(mp + (mp < 10L ? 3L : -9L));
        if (month <= 2) {
            year++;
        }
        final int hour = (int)(millisOfDay / MILLIS_PER_HOUR);
        millisOfDay -= hour * (int)MILLIS_PER_HOUR;
        final int minute = (int)(millisOfDay / MILLIS_PER_MINUTE);
        millisOfDay -= minute * (int)MILLIS_PER_MINUTE;
        final int second = (int)(millisOfDay / MILLIS_PER_SECOND);
        final int millis = millisOfDay - second * (int)MILLIS_PER_SECOND;
        put4(target, 0, year);
        put2(target, 4, month);
        put2(target, 6, day);
        target[8] = '-';
        put2(target, 9, hour);
        target[11] = ':';
        put2(target, 12, minute);
        target[14] = ':';
        put2(target, 15, second);
        target[17] = '.';
        put3(target, 18, millis);
    }

    private static void put4(final byte[] target, final int offset, final int value) {
        target[offset] = (byte)('0' + ((value / 1000) % 10));
        target[offset + 1] = (byte)('0' + ((value / 100) % 10));
        target[offset + 2] = (byte)('0' + ((value / 10) % 10));
        target[offset + 3] = (byte)('0' + (value % 10));
    }

    private static void put3(final byte[] target, final int offset, final int value) {
        target[offset] = (byte)('0' + ((value / 100) % 10));
        target[offset + 1] = (byte)('0' + ((value / 10) % 10));
        target[offset + 2] = (byte)('0' + (value % 10));
    }

    private static void put2(final byte[] target, final int offset, final int value) {
        target[offset] = (byte)('0' + ((value / 10) % 10));
        target[offset + 1] = (byte)('0' + (value % 10));
    }

    @FunctionalInterface
    private interface VenueOrderIdReader {
        void read(byte[] dst, int offset, int length);
    }
}
