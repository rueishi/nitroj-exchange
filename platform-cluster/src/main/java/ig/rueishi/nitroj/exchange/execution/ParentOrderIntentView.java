package ig.rueishi.nitroj.exchange.execution;

import ig.rueishi.nitroj.exchange.messages.ParentIntentType;
import ig.rueishi.nitroj.exchange.messages.ParentOrderIntentDecoder;
import ig.rueishi.nitroj.exchange.messages.BooleanType;
import ig.rueishi.nitroj.exchange.messages.PriceMode;
import ig.rueishi.nitroj.exchange.messages.Side;
import ig.rueishi.nitroj.exchange.messages.TimeInForce;

import java.util.Objects;

/**
 * Reusable primitive view over a decoded V13 parent intent.
 *
 * <p>The view stores only a decoder reference and exposes primitive/enumerated
 * fields. Execution engines should reuse one instance per dispatch loop instead
 * of allocating intent wrappers for each parent message.</p>
 */
public final class ParentOrderIntentView {
    private ParentOrderIntentDecoder decoder;

    public ParentOrderIntentView wrap(final ParentOrderIntentDecoder decoder) {
        this.decoder = Objects.requireNonNull(decoder, "decoder");
        return this;
    }

    public ParentOrderIntentDecoder decoder() {
        return decoder;
    }

    public long parentOrderId() {
        return decoder.parentOrderId();
    }

    public int strategyId() {
        return decoder.strategyId();
    }

    public int executionStrategyId() {
        return decoder.executionStrategyId();
    }

    public ParentIntentType intentType() {
        return decoder.intentType();
    }

    public Side side() {
        return decoder.side();
    }

    public int instrumentId() {
        return decoder.instrumentId();
    }

    public int primaryVenueId() {
        return decoder.primaryVenueId();
    }

    public int secondaryVenueId() {
        return decoder.secondaryVenueId();
    }

    public int venueSetId() {
        return decoder.venueSetId();
    }

    public long quantityScaled() {
        return decoder.quantityScaled();
    }

    public PriceMode priceMode() {
        return decoder.priceMode();
    }

    public long limitPriceScaled() {
        return decoder.limitPriceScaled();
    }

    public long referencePriceScaled() {
        return decoder.referencePriceScaled();
    }

    public TimeInForce timeInForcePreference() {
        return decoder.timeInForcePreference();
    }

    public byte urgencyHint() {
        return decoder.urgencyHint();
    }

    public BooleanType postOnlyPreference() {
        return decoder.postOnlyPreference();
    }

    public byte selfTradePolicy() {
        return decoder.selfTradePolicy();
    }

    public long correlationId() {
        return decoder.correlationId();
    }

    public byte legCount() {
        return decoder.legCount();
    }

    public Side leg2Side() {
        return decoder.leg2Side();
    }

    public long leg2LimitPriceScaled() {
        return decoder.leg2LimitPriceScaled();
    }

    public long parentTimeoutMicros() {
        return decoder.parentTimeoutMicros();
    }
}
