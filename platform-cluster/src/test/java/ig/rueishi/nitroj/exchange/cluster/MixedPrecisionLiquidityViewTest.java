package ig.rueishi.nitroj.exchange.cluster;

import ig.rueishi.nitroj.exchange.common.Ids;
import ig.rueishi.nitroj.exchange.messages.EntryType;
import ig.rueishi.nitroj.exchange.messages.MarketByOrderEventDecoder;
import ig.rueishi.nitroj.exchange.messages.MarketByOrderEventEncoder;
import ig.rueishi.nitroj.exchange.messages.MarketDataEventDecoder;
import ig.rueishi.nitroj.exchange.messages.MarketDataEventEncoder;
import ig.rueishi.nitroj.exchange.messages.MessageHeaderDecoder;
import ig.rueishi.nitroj.exchange.messages.MessageHeaderEncoder;
import ig.rueishi.nitroj.exchange.messages.Side;
import ig.rueishi.nitroj.exchange.messages.UpdateAction;
import org.agrona.concurrent.UnsafeBuffer;
import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * TASK-409 mixed-precision validation for Coinbase L3 plus Binance L2.
 *
 * <p>Applicable coverage categories: positive mixed precision, negative no
 * cross-instrument consolidation, edge own-size exceeds gross, conservative L2
 * subtraction, exact L3 own-order matching, and regression protection for V13
 * Coinbase precision. Non-applicable categories: allocation/latency evidence
 * and snapshot/load, because this task adds validation coverage only and changes
 * no runtime hot path or persisted state format.</p>
 */
final class MixedPrecisionLiquidityViewTest {
    private static final long COINBASE_PRICE = 65_000L * Ids.SCALE;
    private static final long BINANCE_PRICE = 65_010L * Ids.SCALE;
    private static final long ONE = Ids.SCALE;

    @Test
    void coinbaseL3ExactOwnMatchingAndBinanceL2ConservativeSubtraction_shareExternalLiquidityView() {
        final InternalMarketView marketView = new InternalMarketView();
        final VenueL3Book coinbaseL3 = new VenueL3Book(Ids.VENUE_COINBASE, Ids.INSTRUMENT_BTC_USD);
        final L2OrderBook coinbaseL2 = marketView.book(Ids.VENUE_COINBASE, Ids.INSTRUMENT_BTC_USD);
        final OwnOrderOverlay overlay = marketView.ownOrderOverlay();

        overlay.upsert(1L, "CB-OWN", Ids.VENUE_COINBASE, Ids.INSTRUMENT_BTC_USD,
            EntryType.ASK, COINBASE_PRICE, 2L * ONE, true);
        assertThat(coinbaseL3.apply(l3("CB-EXT", Side.SELL, UpdateAction.NEW, COINBASE_PRICE, 3L * ONE),
            coinbaseL2, 1L)).isTrue();
        assertThat(coinbaseL3.apply(l3("CB-OWN", Side.SELL, UpdateAction.NEW, COINBASE_PRICE, 2L * ONE),
            coinbaseL2, 2L)).isTrue();
        marketView.consolidatedBook(Ids.INSTRUMENT_BTC_USD).applyVenueLevel(
            Ids.VENUE_COINBASE, EntryType.ASK, COINBASE_PRICE, coinbaseL3.levelSize(Side.SELL, COINBASE_PRICE));

        marketView.apply(l2(Ids.VENUE_BINANCE, Ids.INSTRUMENT_BTC_USDT, EntryType.BID,
            BINANCE_PRICE, 5L * ONE), 3L);
        overlay.upsert(2L, null, Ids.VENUE_BINANCE, Ids.INSTRUMENT_BTC_USDT,
            EntryType.BID, BINANCE_PRICE, 2L * ONE, true);

        final ExternalLiquidityView external = marketView.externalLiquidityView();
        assertThat(external.externalSizeAtL3Order(Ids.VENUE_COINBASE, Ids.INSTRUMENT_BTC_USD,
            EntryType.ASK, COINBASE_PRICE, 2L * ONE, "CB-OWN")).isZero();
        assertThat(external.externalSizeAtL3Order(Ids.VENUE_COINBASE, Ids.INSTRUMENT_BTC_USD,
            EntryType.ASK, COINBASE_PRICE, 3L * ONE, "CB-EXT")).isEqualTo(3L * ONE);
        assertThat(external.externalSizeAt(Ids.VENUE_BINANCE, Ids.INSTRUMENT_BTC_USDT,
            EntryType.BID, BINANCE_PRICE)).isEqualTo(3L * ONE);
        assertThat(marketView.book(Ids.VENUE_BINANCE, Ids.INSTRUMENT_BTC_USDT).bidSizeAt(0))
            .isEqualTo(5L * ONE);
    }

    @Test
    void consolidatedBooksRemainInstrumentScopedForUsdAndUsdtProducts() {
        final InternalMarketView marketView = new InternalMarketView();

        marketView.apply(l2(Ids.VENUE_COINBASE, Ids.INSTRUMENT_BTC_USD, EntryType.BID,
            COINBASE_PRICE, 4L * ONE), 1L);
        marketView.apply(l2(Ids.VENUE_BINANCE, Ids.INSTRUMENT_BTC_USDT, EntryType.BID,
            BINANCE_PRICE, 7L * ONE), 2L);

        assertThat(marketView.consolidatedBook(Ids.INSTRUMENT_BTC_USD)
            .sizeAt(EntryType.BID, COINBASE_PRICE)).isEqualTo(4L * ONE);
        assertThat(marketView.consolidatedBook(Ids.INSTRUMENT_BTC_USD)
            .sizeAt(EntryType.BID, BINANCE_PRICE)).isZero();
        assertThat(marketView.consolidatedBook(Ids.INSTRUMENT_BTC_USDT)
            .sizeAt(EntryType.BID, BINANCE_PRICE)).isEqualTo(7L * ONE);
        assertThat(marketView.consolidatedBook(Ids.INSTRUMENT_BTC_USDT)
            .sizeAt(EntryType.BID, COINBASE_PRICE)).isZero();
    }

    @Test
    void coinbasePrecisionRegression_exactL3MatchingStillBeatsLevelApproximation() {
        final InternalMarketView marketView = new InternalMarketView();
        final VenueL3Book coinbaseL3 = new VenueL3Book(Ids.VENUE_COINBASE, Ids.INSTRUMENT_BTC_USD);
        final L2OrderBook coinbaseL2 = marketView.book(Ids.VENUE_COINBASE, Ids.INSTRUMENT_BTC_USD);
        final OwnOrderOverlay overlay = marketView.ownOrderOverlay();
        overlay.upsert(100L, "CB-OWN", Ids.VENUE_COINBASE, Ids.INSTRUMENT_BTC_USD,
            EntryType.ASK, COINBASE_PRICE, ONE, true);

        coinbaseL3.apply(l3("CB-OWN", Side.SELL, UpdateAction.NEW, COINBASE_PRICE, ONE), coinbaseL2, 1L);
        coinbaseL3.apply(l3("CB-EXT", Side.SELL, UpdateAction.NEW, COINBASE_PRICE, ONE), coinbaseL2, 2L);

        assertThat(coinbaseL3.externalOrderSize("CB-OWN", overlay)).isZero();
        assertThat(coinbaseL3.externalOrderSize("CB-EXT", overlay)).isEqualTo(ONE);
        assertThat(marketView.externalLiquidityView().externalSizeAt(Ids.VENUE_COINBASE,
            Ids.INSTRUMENT_BTC_USD, EntryType.ASK, COINBASE_PRICE)).isEqualTo(ONE);
        assertThat(coinbaseL2.askSizeAt(0)).isEqualTo(2L * ONE);
    }

    @Test
    void binanceL2OwnSizeGreaterThanGrossClampsToZeroWithoutChangingGrossBook() {
        final InternalMarketView marketView = new InternalMarketView();
        marketView.apply(l2(Ids.VENUE_BINANCE, Ids.INSTRUMENT_BTC_USDT, EntryType.ASK,
            BINANCE_PRICE, ONE), 1L);
        marketView.ownOrderOverlay().upsert(200L, null, Ids.VENUE_BINANCE, Ids.INSTRUMENT_BTC_USDT,
            EntryType.ASK, BINANCE_PRICE, 2L * ONE, true);

        assertThat(marketView.externalLiquidityView().externalSizeAt(Ids.VENUE_BINANCE,
            Ids.INSTRUMENT_BTC_USDT, EntryType.ASK, BINANCE_PRICE)).isZero();
        assertThat(marketView.book(Ids.VENUE_BINANCE, Ids.INSTRUMENT_BTC_USDT).askSizeAt(0))
            .isEqualTo(ONE);
    }

    private static MarketDataEventDecoder l2(
        final int venueId,
        final int instrumentId,
        final EntryType side,
        final long price,
        final long size) {

        final UnsafeBuffer buffer = new UnsafeBuffer(new byte[128]);
        new MarketDataEventEncoder()
            .wrapAndApplyHeader(buffer, 0, new MessageHeaderEncoder())
            .venueId(venueId)
            .instrumentId(instrumentId)
            .entryType(side)
            .updateAction(UpdateAction.NEW)
            .priceScaled(price)
            .sizeScaled(size)
            .priceLevel(1)
            .ingressTimestampNanos(1L)
            .exchangeTimestampNanos(1L);
        final MarketDataEventDecoder decoder = new MarketDataEventDecoder();
        decoder.wrapAndApplyHeader(buffer, 0, new MessageHeaderDecoder());
        return decoder;
    }

    private static MarketByOrderEventDecoder l3(
        final String orderId,
        final Side side,
        final UpdateAction action,
        final long price,
        final long size) {

        final UnsafeBuffer buffer = new UnsafeBuffer(new byte[256]);
        final byte[] orderIdBytes = orderId.getBytes(StandardCharsets.US_ASCII);
        new MarketByOrderEventEncoder()
            .wrapAndApplyHeader(buffer, 0, new MessageHeaderEncoder())
            .venueId(Ids.VENUE_COINBASE)
            .instrumentId(Ids.INSTRUMENT_BTC_USD)
            .side(side)
            .updateAction(action)
            .priceScaled(price)
            .sizeScaled(size)
            .ingressTimestampNanos(1L)
            .exchangeTimestampNanos(1L)
            .fixSeqNum(1)
            .putVenueOrderId(orderIdBytes, 0, orderIdBytes.length);
        final MarketByOrderEventDecoder decoder = new MarketByOrderEventDecoder();
        decoder.wrapAndApplyHeader(buffer, 0, new MessageHeaderDecoder());
        return decoder;
    }
}
