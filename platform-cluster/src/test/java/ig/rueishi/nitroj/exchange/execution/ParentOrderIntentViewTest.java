package ig.rueishi.nitroj.exchange.execution;

import static org.assertj.core.api.Assertions.assertThat;

import ig.rueishi.nitroj.exchange.common.ExecutionStrategyIds;
import ig.rueishi.nitroj.exchange.common.Ids;
import ig.rueishi.nitroj.exchange.messages.BooleanType;
import ig.rueishi.nitroj.exchange.messages.MessageHeaderDecoder;
import ig.rueishi.nitroj.exchange.messages.MessageHeaderEncoder;
import ig.rueishi.nitroj.exchange.messages.ParentIntentType;
import ig.rueishi.nitroj.exchange.messages.ParentOrderIntentDecoder;
import ig.rueishi.nitroj.exchange.messages.ParentOrderIntentEncoder;
import ig.rueishi.nitroj.exchange.messages.PriceMode;
import ig.rueishi.nitroj.exchange.messages.Side;
import ig.rueishi.nitroj.exchange.messages.TimeInForce;
import org.agrona.concurrent.UnsafeBuffer;
import org.junit.jupiter.api.Test;

/**
 * TASK-413A coverage for parent-intent view schema plumbing.
 *
 * <p>The view remains a reusable decoder facade; adding venueSetId must not
 * disturb existing primary/secondary venue behavior used by V13 strategies.</p>
 */
final class ParentOrderIntentViewTest {
    @Test
    void venueSetIdAccessorPreservesExistingVenueFields() {
        final UnsafeBuffer buffer = new UnsafeBuffer(new byte[256]);
        new ParentOrderIntentEncoder()
            .wrapAndApplyHeader(buffer, 0, new MessageHeaderEncoder())
            .parentOrderId(1L)
            .strategyId((short) Ids.STRATEGY_MARKET_MAKING)
            .executionStrategyId(ExecutionStrategyIds.POST_ONLY_QUOTE)
            .intentType(ParentIntentType.QUOTE)
            .side(Side.BUY)
            .instrumentId(Ids.INSTRUMENT_BTC_USD)
            .primaryVenueId(Ids.VENUE_COINBASE)
            .secondaryVenueId(Ids.VENUE_BINANCE)
            .quantityScaled(Ids.SCALE)
            .priceMode(PriceMode.LIMIT)
            .limitPriceScaled(65_000L * Ids.SCALE)
            .referencePriceScaled(0L)
            .timeInForcePreference(TimeInForce.GTC)
            .urgencyHint((byte) 1)
            .postOnlyPreference(BooleanType.TRUE)
            .selfTradePolicy((byte) 0)
            .correlationId(1L)
            .legCount((byte) 1)
            .leg2Side(Side.SELL)
            .leg2LimitPriceScaled(0L)
            .parentTimeoutMicros(0L)
            .venueSetId(17);
        final ParentOrderIntentDecoder decoder = new ParentOrderIntentDecoder();
        decoder.wrapAndApplyHeader(buffer, 0, new MessageHeaderDecoder());

        final ParentOrderIntentView view = new ParentOrderIntentView().wrap(decoder);

        assertThat(view.primaryVenueId()).isEqualTo(Ids.VENUE_COINBASE);
        assertThat(view.secondaryVenueId()).isEqualTo(Ids.VENUE_BINANCE);
        assertThat(view.venueSetId()).isEqualTo(17);
    }
}
