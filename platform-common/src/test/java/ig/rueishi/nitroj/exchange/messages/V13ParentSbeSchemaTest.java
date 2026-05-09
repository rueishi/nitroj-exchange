package ig.rueishi.nitroj.exchange.messages;

import org.agrona.concurrent.UnsafeBuffer;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Locks down the V13 parent-order SBE wire contract.
 *
 * <p>Parent IDs are internal cluster attribution fields. A value of {@code 0}
 * is the no-parent sentinel so V12 child commands and snapshots can still be
 * decoded by V13 code without accidental parent ownership.</p>
 */
final class V13ParentSbeSchemaTest {
    @Test
    void parentOrderIntent_roundTripsAllFixedFields() {
        final UnsafeBuffer buffer = new UnsafeBuffer(new byte[256]);
        final ParentOrderIntentEncoder encoder = new ParentOrderIntentEncoder();
        encoder.wrapAndApplyHeader(buffer, 0, new MessageHeaderEncoder())
            .parentOrderId(9001L)
            .strategyId((short)7)
            .executionStrategyId(11)
            .intentType(ParentIntentType.MULTI_LEG)
            .side(Side.BUY)
            .instrumentId(101)
            .primaryVenueId(1)
            .secondaryVenueId(2)
            .quantityScaled(50_000_000L)
            .priceMode(PriceMode.LIMIT)
            .limitPriceScaled(6_500_000_000_000L)
            .referencePriceScaled(6_499_000_000_000L)
            .timeInForcePreference(TimeInForce.IOC)
            .urgencyHint((byte)3)
            .postOnlyPreference(BooleanType.FALSE)
            .selfTradePolicy((byte)4)
            .correlationId(77L)
            .legCount((byte)2)
            .leg2Side(Side.SELL)
            .leg2LimitPriceScaled(6_510_000_000_000L)
            .parentTimeoutMicros(123_456L)
            .venueSetId(42);

        final ParentOrderIntentDecoder decoder = new ParentOrderIntentDecoder();
        decoder.wrapAndApplyHeader(buffer, 0, new MessageHeaderDecoder());

        assertThat(decoder.parentOrderId()).isEqualTo(9001L);
        assertThat(decoder.strategyId()).isEqualTo((short)7);
        assertThat(decoder.executionStrategyId()).isEqualTo(11);
        assertThat(decoder.intentType()).isEqualTo(ParentIntentType.MULTI_LEG);
        assertThat(decoder.side()).isEqualTo(Side.BUY);
        assertThat(decoder.instrumentId()).isEqualTo(101);
        assertThat(decoder.primaryVenueId()).isEqualTo(1);
        assertThat(decoder.secondaryVenueId()).isEqualTo(2);
        assertThat(decoder.quantityScaled()).isEqualTo(50_000_000L);
        assertThat(decoder.priceMode()).isEqualTo(PriceMode.LIMIT);
        assertThat(decoder.limitPriceScaled()).isEqualTo(6_500_000_000_000L);
        assertThat(decoder.referencePriceScaled()).isEqualTo(6_499_000_000_000L);
        assertThat(decoder.timeInForcePreference()).isEqualTo(TimeInForce.IOC);
        assertThat(decoder.urgencyHint()).isEqualTo((byte)3);
        assertThat(decoder.postOnlyPreference()).isEqualTo(BooleanType.FALSE);
        assertThat(decoder.selfTradePolicy()).isEqualTo((byte)4);
        assertThat(decoder.correlationId()).isEqualTo(77L);
        assertThat(decoder.legCount()).isEqualTo((byte)2);
        assertThat(decoder.leg2Side()).isEqualTo(Side.SELL);
        assertThat(decoder.leg2LimitPriceScaled()).isEqualTo(6_510_000_000_000L);
        assertThat(decoder.parentTimeoutMicros()).isEqualTo(123_456L);
        assertThat(decoder.venueSetId()).isEqualTo(42);
    }

    @Test
    void parentOrderIntent_venueSetIdDefaultsToZeroForOlderBlockLength() {
        final UnsafeBuffer buffer = new UnsafeBuffer(new byte[256]);
        new ParentOrderIntentEncoder()
            .wrapAndApplyHeader(buffer, 0, new MessageHeaderEncoder())
            .parentOrderId(9001L)
            .strategyId((short)7)
            .executionStrategyId(11)
            .intentType(ParentIntentType.IMMEDIATE_LIMIT)
            .side(Side.BUY)
            .instrumentId(101)
            .primaryVenueId(1)
            .secondaryVenueId(0)
            .quantityScaled(50_000_000L)
            .priceMode(PriceMode.LIMIT)
            .limitPriceScaled(6_500_000_000_000L)
            .referencePriceScaled(0L)
            .timeInForcePreference(TimeInForce.IOC)
            .urgencyHint((byte)3)
            .postOnlyPreference(BooleanType.FALSE)
            .selfTradePolicy((byte)4)
            .correlationId(77L)
            .legCount((byte)1)
            .leg2Side(Side.SELL)
            .leg2LimitPriceScaled(0L)
            .parentTimeoutMicros(0L)
            .venueSetId(99);

        final ParentOrderIntentDecoder decoder = new ParentOrderIntentDecoder();
        decoder.wrap(buffer, MessageHeaderEncoder.ENCODED_LENGTH,
            ParentOrderIntentEncoder.BLOCK_LENGTH - ParentOrderIntentEncoder.venueSetIdEncodingLength(), 2);

        assertThat(decoder.primaryVenueId()).isEqualTo(1);
        assertThat(decoder.secondaryVenueId()).isZero();
        assertThat(decoder.venueSetId()).isZero();
    }

    @Test
    void parentOrderUpdateAndTerminal_roundTripReasonCodes() {
        final UnsafeBuffer updateBuffer = new UnsafeBuffer(new byte[128]);
        new ParentOrderUpdateEncoder()
            .wrapAndApplyHeader(updateBuffer, 0, new MessageHeaderEncoder())
            .parentOrderId(9002L)
            .strategyId((short)8)
            .executionStrategyId(12)
            .cumFillQtyScaled(10L)
            .avgFillPriceScaled(20L)
            .leavesQtyScaled(30L)
            .workingChildCount(2)
            .lastChildClOrdId(1001L)
            .updateReason(ParentUpdateReason.CHILD_PARTIAL_FILL)
            .eventClusterTime(123L);

        final ParentOrderUpdateDecoder update = new ParentOrderUpdateDecoder();
        update.wrapAndApplyHeader(updateBuffer, 0, new MessageHeaderDecoder());
        assertThat(update.parentOrderId()).isEqualTo(9002L);
        assertThat(update.updateReason()).isEqualTo(ParentUpdateReason.CHILD_PARTIAL_FILL);
        assertThat(update.workingChildCount()).isEqualTo(2);

        final UnsafeBuffer terminalBuffer = new UnsafeBuffer(new byte[128]);
        new ParentOrderTerminalEncoder()
            .wrapAndApplyHeader(terminalBuffer, 0, new MessageHeaderEncoder())
            .parentOrderId(9003L)
            .strategyId((short)9)
            .executionStrategyId(13)
            .finalCumFillQtyScaled(40L)
            .avgFillPriceScaled(50L)
            .terminalReason(ParentTerminalReason.HEDGE_FAILED)
            .lastChildClOrdId(1002L)
            .eventClusterTime(456L);

        final ParentOrderTerminalDecoder terminal = new ParentOrderTerminalDecoder();
        terminal.wrapAndApplyHeader(terminalBuffer, 0, new MessageHeaderDecoder());
        assertThat(terminal.parentOrderId()).isEqualTo(9003L);
        assertThat(terminal.terminalReason()).isEqualTo(ParentTerminalReason.HEDGE_FAILED);
        assertThat(terminal.finalCumFillQtyScaled()).isEqualTo(40L);
    }

    @Test
    void newOrderCommand_parentOrderIdSupportsNullMaxAndV12Compatibility() {
        final UnsafeBuffer buffer = new UnsafeBuffer(new byte[128]);
        new NewOrderCommandEncoder()
            .wrapAndApplyHeader(buffer, 0, new MessageHeaderEncoder())
            .clOrdId(1001L)
            .venueId(1)
            .instrumentId(11)
            .side(Side.BUY)
            .ordType(OrdType.LIMIT)
            .timeInForce(TimeInForce.GTC)
            .priceScaled(10L)
            .qtyScaled(20L)
            .strategyId((short)3)
            .parentOrderId(Long.MAX_VALUE);

        final NewOrderCommandDecoder decoder = new NewOrderCommandDecoder();
        decoder.wrapAndApplyHeader(buffer, 0, new MessageHeaderDecoder());
        assertThat(decoder.parentOrderId()).isEqualTo(Long.MAX_VALUE);

        decoder.wrap(buffer, MessageHeaderEncoder.ENCODED_LENGTH, 37, 1);
        assertThat(decoder.parentOrderId()).isZero();

        new NewOrderCommandEncoder()
            .wrapAndApplyHeader(buffer, 0, new MessageHeaderEncoder())
            .clOrdId(1002L)
            .venueId(1)
            .instrumentId(11)
            .side(Side.SELL)
            .ordType(OrdType.MARKET)
            .timeInForce(TimeInForce.IOC)
            .priceScaled(0L)
            .qtyScaled(20L)
            .strategyId((short)3)
            .parentOrderId(NewOrderCommandEncoder.parentOrderIdNullValue());

        decoder.wrapAndApplyHeader(buffer, 0, new MessageHeaderDecoder());
        assertThat(decoder.parentOrderId()).isEqualTo(NewOrderCommandEncoder.parentOrderIdNullValue());
    }

    @Test
    void orderStateSnapshot_parentOrderIdSupportsRoundTripAndV12Compatibility() {
        final UnsafeBuffer buffer = new UnsafeBuffer(new byte[256]);
        final OrderStateSnapshotEncoder encoder = new OrderStateSnapshotEncoder();
        encoder.wrapAndApplyHeader(buffer, 0, new MessageHeaderEncoder())
            .clOrdId(1001L)
            .venueId(1)
            .instrumentId(11)
            .strategyId((short)3)
            .side(Side.BUY)
            .ordType(OrdType.LIMIT)
            .timeInForce(TimeInForce.GTC)
            .status((byte)1)
            .priceScaled(10L)
            .qtyScaled(20L)
            .cumFillQtyScaled(5L)
            .leavesQtyScaled(15L)
            .avgFillPriceScaled(10L)
            .createdClusterTime(123L)
            .parentOrderId(9004L)
            .putVenueOrderId(new byte[] {'v', '1'}, 0, 2);

        final OrderStateSnapshotDecoder decoder = new OrderStateSnapshotDecoder();
        decoder.wrapAndApplyHeader(buffer, 0, new MessageHeaderDecoder());
        assertThat(decoder.parentOrderId()).isEqualTo(9004L);

        decoder.wrap(buffer, MessageHeaderEncoder.ENCODED_LENGTH, 70, 1);
        assertThat(decoder.parentOrderId()).isZero();
    }

    @Test
    void parentDecoder_rejectsUnknownTemplateId() {
        final UnsafeBuffer buffer = new UnsafeBuffer(new byte[64]);
        new MessageHeaderEncoder()
            .wrap(buffer, 0)
            .blockLength(0)
            .templateId(999)
            .schemaId(1)
            .version(2);

        assertThatThrownBy(() -> new ParentOrderIntentDecoder()
            .wrapAndApplyHeader(buffer, 0, new MessageHeaderDecoder()))
            .isInstanceOf(IllegalStateException.class)
            .hasMessageContaining("Invalid TEMPLATE_ID");
    }
}
