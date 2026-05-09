/* Generated SBE (Simple Binary Encoding) message codec. */
package ig.rueishi.nitroj.exchange.messages;

import org.agrona.MutableDirectBuffer;


/**
 * Declarative parent-order intent from trading strategy to execution strategy
 */
@SuppressWarnings("all")
public final class ParentOrderIntentEncoder
{
    public static final int BLOCK_LENGTH = 87;
    public static final int TEMPLATE_ID = 40;
    public static final int SCHEMA_ID = 1;
    public static final int SCHEMA_VERSION = 3;
    public static final String SEMANTIC_VERSION = "5.3";
    public static final java.nio.ByteOrder BYTE_ORDER = java.nio.ByteOrder.LITTLE_ENDIAN;

    private final ParentOrderIntentEncoder parentMessage = this;
    private MutableDirectBuffer buffer;
    private int offset;
    private int limit;

    public int sbeBlockLength()
    {
        return BLOCK_LENGTH;
    }

    public int sbeTemplateId()
    {
        return TEMPLATE_ID;
    }

    public int sbeSchemaId()
    {
        return SCHEMA_ID;
    }

    public int sbeSchemaVersion()
    {
        return SCHEMA_VERSION;
    }

    public String sbeSemanticType()
    {
        return "";
    }

    public MutableDirectBuffer buffer()
    {
        return buffer;
    }

    public int offset()
    {
        return offset;
    }

    public ParentOrderIntentEncoder wrap(final MutableDirectBuffer buffer, final int offset)
    {
        if (buffer != this.buffer)
        {
            this.buffer = buffer;
        }
        this.offset = offset;
        limit(offset + BLOCK_LENGTH);

        return this;
    }

    public ParentOrderIntentEncoder wrapAndApplyHeader(
        final MutableDirectBuffer buffer, final int offset, final MessageHeaderEncoder headerEncoder)
    {
        headerEncoder
            .wrap(buffer, offset)
            .blockLength(BLOCK_LENGTH)
            .templateId(TEMPLATE_ID)
            .schemaId(SCHEMA_ID)
            .version(SCHEMA_VERSION);

        return wrap(buffer, offset + MessageHeaderEncoder.ENCODED_LENGTH);
    }

    public int encodedLength()
    {
        return limit - offset;
    }

    public int limit()
    {
        return limit;
    }

    public void limit(final int limit)
    {
        this.limit = limit;
    }

    public static int parentOrderIdId()
    {
        return 1;
    }

    public static int parentOrderIdSinceVersion()
    {
        return 0;
    }

    public static int parentOrderIdEncodingOffset()
    {
        return 0;
    }

    public static int parentOrderIdEncodingLength()
    {
        return 8;
    }

    public static String parentOrderIdMetaAttribute(final MetaAttribute metaAttribute)
    {
        if (MetaAttribute.PRESENCE == metaAttribute)
        {
            return "optional";
        }

        return "";
    }

    public static long parentOrderIdNullValue()
    {
        return 0L;
    }

    public static long parentOrderIdMinValue()
    {
        return -9223372036854775807L;
    }

    public static long parentOrderIdMaxValue()
    {
        return 9223372036854775807L;
    }

    public ParentOrderIntentEncoder parentOrderId(final long value)
    {
        buffer.putLong(offset + 0, value, BYTE_ORDER);
        return this;
    }


    public static int strategyIdId()
    {
        return 2;
    }

    public static int strategyIdSinceVersion()
    {
        return 0;
    }

    public static int strategyIdEncodingOffset()
    {
        return 8;
    }

    public static int strategyIdEncodingLength()
    {
        return 2;
    }

    public static String strategyIdMetaAttribute(final MetaAttribute metaAttribute)
    {
        if (MetaAttribute.PRESENCE == metaAttribute)
        {
            return "required";
        }

        return "";
    }

    public static short strategyIdNullValue()
    {
        return (short)-32768;
    }

    public static short strategyIdMinValue()
    {
        return (short)-32767;
    }

    public static short strategyIdMaxValue()
    {
        return (short)32767;
    }

    public ParentOrderIntentEncoder strategyId(final short value)
    {
        buffer.putShort(offset + 8, value, BYTE_ORDER);
        return this;
    }


    public static int executionStrategyIdId()
    {
        return 3;
    }

    public static int executionStrategyIdSinceVersion()
    {
        return 0;
    }

    public static int executionStrategyIdEncodingOffset()
    {
        return 10;
    }

    public static int executionStrategyIdEncodingLength()
    {
        return 4;
    }

    public static String executionStrategyIdMetaAttribute(final MetaAttribute metaAttribute)
    {
        if (MetaAttribute.PRESENCE == metaAttribute)
        {
            return "required";
        }

        return "";
    }

    public static int executionStrategyIdNullValue()
    {
        return -2147483648;
    }

    public static int executionStrategyIdMinValue()
    {
        return -2147483647;
    }

    public static int executionStrategyIdMaxValue()
    {
        return 2147483647;
    }

    public ParentOrderIntentEncoder executionStrategyId(final int value)
    {
        buffer.putInt(offset + 10, value, BYTE_ORDER);
        return this;
    }


    public static int intentTypeId()
    {
        return 4;
    }

    public static int intentTypeSinceVersion()
    {
        return 0;
    }

    public static int intentTypeEncodingOffset()
    {
        return 14;
    }

    public static int intentTypeEncodingLength()
    {
        return 1;
    }

    public static String intentTypeMetaAttribute(final MetaAttribute metaAttribute)
    {
        if (MetaAttribute.PRESENCE == metaAttribute)
        {
            return "required";
        }

        return "";
    }

    public ParentOrderIntentEncoder intentType(final ParentIntentType value)
    {
        buffer.putByte(offset + 14, value.value());
        return this;
    }

    public static int sideId()
    {
        return 5;
    }

    public static int sideSinceVersion()
    {
        return 0;
    }

    public static int sideEncodingOffset()
    {
        return 15;
    }

    public static int sideEncodingLength()
    {
        return 1;
    }

    public static String sideMetaAttribute(final MetaAttribute metaAttribute)
    {
        if (MetaAttribute.PRESENCE == metaAttribute)
        {
            return "required";
        }

        return "";
    }

    public ParentOrderIntentEncoder side(final Side value)
    {
        buffer.putByte(offset + 15, value.value());
        return this;
    }

    public static int instrumentIdId()
    {
        return 6;
    }

    public static int instrumentIdSinceVersion()
    {
        return 0;
    }

    public static int instrumentIdEncodingOffset()
    {
        return 16;
    }

    public static int instrumentIdEncodingLength()
    {
        return 4;
    }

    public static String instrumentIdMetaAttribute(final MetaAttribute metaAttribute)
    {
        if (MetaAttribute.PRESENCE == metaAttribute)
        {
            return "required";
        }

        return "";
    }

    public static int instrumentIdNullValue()
    {
        return -2147483648;
    }

    public static int instrumentIdMinValue()
    {
        return -2147483647;
    }

    public static int instrumentIdMaxValue()
    {
        return 2147483647;
    }

    public ParentOrderIntentEncoder instrumentId(final int value)
    {
        buffer.putInt(offset + 16, value, BYTE_ORDER);
        return this;
    }


    public static int primaryVenueIdId()
    {
        return 7;
    }

    public static int primaryVenueIdSinceVersion()
    {
        return 0;
    }

    public static int primaryVenueIdEncodingOffset()
    {
        return 20;
    }

    public static int primaryVenueIdEncodingLength()
    {
        return 4;
    }

    public static String primaryVenueIdMetaAttribute(final MetaAttribute metaAttribute)
    {
        if (MetaAttribute.PRESENCE == metaAttribute)
        {
            return "required";
        }

        return "";
    }

    public static int primaryVenueIdNullValue()
    {
        return -2147483648;
    }

    public static int primaryVenueIdMinValue()
    {
        return -2147483647;
    }

    public static int primaryVenueIdMaxValue()
    {
        return 2147483647;
    }

    public ParentOrderIntentEncoder primaryVenueId(final int value)
    {
        buffer.putInt(offset + 20, value, BYTE_ORDER);
        return this;
    }


    public static int secondaryVenueIdId()
    {
        return 8;
    }

    public static int secondaryVenueIdSinceVersion()
    {
        return 0;
    }

    public static int secondaryVenueIdEncodingOffset()
    {
        return 24;
    }

    public static int secondaryVenueIdEncodingLength()
    {
        return 4;
    }

    public static String secondaryVenueIdMetaAttribute(final MetaAttribute metaAttribute)
    {
        if (MetaAttribute.PRESENCE == metaAttribute)
        {
            return "required";
        }

        return "";
    }

    public static int secondaryVenueIdNullValue()
    {
        return -2147483648;
    }

    public static int secondaryVenueIdMinValue()
    {
        return -2147483647;
    }

    public static int secondaryVenueIdMaxValue()
    {
        return 2147483647;
    }

    public ParentOrderIntentEncoder secondaryVenueId(final int value)
    {
        buffer.putInt(offset + 24, value, BYTE_ORDER);
        return this;
    }


    public static int quantityScaledId()
    {
        return 9;
    }

    public static int quantityScaledSinceVersion()
    {
        return 0;
    }

    public static int quantityScaledEncodingOffset()
    {
        return 28;
    }

    public static int quantityScaledEncodingLength()
    {
        return 8;
    }

    public static String quantityScaledMetaAttribute(final MetaAttribute metaAttribute)
    {
        if (MetaAttribute.PRESENCE == metaAttribute)
        {
            return "required";
        }

        return "";
    }

    public static long quantityScaledNullValue()
    {
        return -9223372036854775808L;
    }

    public static long quantityScaledMinValue()
    {
        return -9223372036854775807L;
    }

    public static long quantityScaledMaxValue()
    {
        return 9223372036854775807L;
    }

    public ParentOrderIntentEncoder quantityScaled(final long value)
    {
        buffer.putLong(offset + 28, value, BYTE_ORDER);
        return this;
    }


    public static int priceModeId()
    {
        return 10;
    }

    public static int priceModeSinceVersion()
    {
        return 0;
    }

    public static int priceModeEncodingOffset()
    {
        return 36;
    }

    public static int priceModeEncodingLength()
    {
        return 1;
    }

    public static String priceModeMetaAttribute(final MetaAttribute metaAttribute)
    {
        if (MetaAttribute.PRESENCE == metaAttribute)
        {
            return "required";
        }

        return "";
    }

    public ParentOrderIntentEncoder priceMode(final PriceMode value)
    {
        buffer.putByte(offset + 36, value.value());
        return this;
    }

    public static int limitPriceScaledId()
    {
        return 11;
    }

    public static int limitPriceScaledSinceVersion()
    {
        return 0;
    }

    public static int limitPriceScaledEncodingOffset()
    {
        return 37;
    }

    public static int limitPriceScaledEncodingLength()
    {
        return 8;
    }

    public static String limitPriceScaledMetaAttribute(final MetaAttribute metaAttribute)
    {
        if (MetaAttribute.PRESENCE == metaAttribute)
        {
            return "required";
        }

        return "";
    }

    public static long limitPriceScaledNullValue()
    {
        return -9223372036854775808L;
    }

    public static long limitPriceScaledMinValue()
    {
        return -9223372036854775807L;
    }

    public static long limitPriceScaledMaxValue()
    {
        return 9223372036854775807L;
    }

    public ParentOrderIntentEncoder limitPriceScaled(final long value)
    {
        buffer.putLong(offset + 37, value, BYTE_ORDER);
        return this;
    }


    public static int referencePriceScaledId()
    {
        return 12;
    }

    public static int referencePriceScaledSinceVersion()
    {
        return 0;
    }

    public static int referencePriceScaledEncodingOffset()
    {
        return 45;
    }

    public static int referencePriceScaledEncodingLength()
    {
        return 8;
    }

    public static String referencePriceScaledMetaAttribute(final MetaAttribute metaAttribute)
    {
        if (MetaAttribute.PRESENCE == metaAttribute)
        {
            return "required";
        }

        return "";
    }

    public static long referencePriceScaledNullValue()
    {
        return -9223372036854775808L;
    }

    public static long referencePriceScaledMinValue()
    {
        return -9223372036854775807L;
    }

    public static long referencePriceScaledMaxValue()
    {
        return 9223372036854775807L;
    }

    public ParentOrderIntentEncoder referencePriceScaled(final long value)
    {
        buffer.putLong(offset + 45, value, BYTE_ORDER);
        return this;
    }


    public static int timeInForcePreferenceId()
    {
        return 13;
    }

    public static int timeInForcePreferenceSinceVersion()
    {
        return 0;
    }

    public static int timeInForcePreferenceEncodingOffset()
    {
        return 53;
    }

    public static int timeInForcePreferenceEncodingLength()
    {
        return 1;
    }

    public static String timeInForcePreferenceMetaAttribute(final MetaAttribute metaAttribute)
    {
        if (MetaAttribute.PRESENCE == metaAttribute)
        {
            return "required";
        }

        return "";
    }

    public ParentOrderIntentEncoder timeInForcePreference(final TimeInForce value)
    {
        buffer.putByte(offset + 53, value.value());
        return this;
    }

    public static int urgencyHintId()
    {
        return 14;
    }

    public static int urgencyHintSinceVersion()
    {
        return 0;
    }

    public static int urgencyHintEncodingOffset()
    {
        return 54;
    }

    public static int urgencyHintEncodingLength()
    {
        return 1;
    }

    public static String urgencyHintMetaAttribute(final MetaAttribute metaAttribute)
    {
        if (MetaAttribute.PRESENCE == metaAttribute)
        {
            return "required";
        }

        return "";
    }

    public static byte urgencyHintNullValue()
    {
        return (byte)-128;
    }

    public static byte urgencyHintMinValue()
    {
        return (byte)-127;
    }

    public static byte urgencyHintMaxValue()
    {
        return (byte)127;
    }

    public ParentOrderIntentEncoder urgencyHint(final byte value)
    {
        buffer.putByte(offset + 54, value);
        return this;
    }


    public static int postOnlyPreferenceId()
    {
        return 15;
    }

    public static int postOnlyPreferenceSinceVersion()
    {
        return 0;
    }

    public static int postOnlyPreferenceEncodingOffset()
    {
        return 55;
    }

    public static int postOnlyPreferenceEncodingLength()
    {
        return 1;
    }

    public static String postOnlyPreferenceMetaAttribute(final MetaAttribute metaAttribute)
    {
        if (MetaAttribute.PRESENCE == metaAttribute)
        {
            return "required";
        }

        return "";
    }

    public ParentOrderIntentEncoder postOnlyPreference(final BooleanType value)
    {
        buffer.putByte(offset + 55, value.value());
        return this;
    }

    public static int selfTradePolicyId()
    {
        return 16;
    }

    public static int selfTradePolicySinceVersion()
    {
        return 0;
    }

    public static int selfTradePolicyEncodingOffset()
    {
        return 56;
    }

    public static int selfTradePolicyEncodingLength()
    {
        return 1;
    }

    public static String selfTradePolicyMetaAttribute(final MetaAttribute metaAttribute)
    {
        if (MetaAttribute.PRESENCE == metaAttribute)
        {
            return "required";
        }

        return "";
    }

    public static byte selfTradePolicyNullValue()
    {
        return (byte)-128;
    }

    public static byte selfTradePolicyMinValue()
    {
        return (byte)-127;
    }

    public static byte selfTradePolicyMaxValue()
    {
        return (byte)127;
    }

    public ParentOrderIntentEncoder selfTradePolicy(final byte value)
    {
        buffer.putByte(offset + 56, value);
        return this;
    }


    public static int correlationIdId()
    {
        return 17;
    }

    public static int correlationIdSinceVersion()
    {
        return 0;
    }

    public static int correlationIdEncodingOffset()
    {
        return 57;
    }

    public static int correlationIdEncodingLength()
    {
        return 8;
    }

    public static String correlationIdMetaAttribute(final MetaAttribute metaAttribute)
    {
        if (MetaAttribute.PRESENCE == metaAttribute)
        {
            return "required";
        }

        return "";
    }

    public static long correlationIdNullValue()
    {
        return -9223372036854775808L;
    }

    public static long correlationIdMinValue()
    {
        return -9223372036854775807L;
    }

    public static long correlationIdMaxValue()
    {
        return 9223372036854775807L;
    }

    public ParentOrderIntentEncoder correlationId(final long value)
    {
        buffer.putLong(offset + 57, value, BYTE_ORDER);
        return this;
    }


    public static int legCountId()
    {
        return 18;
    }

    public static int legCountSinceVersion()
    {
        return 0;
    }

    public static int legCountEncodingOffset()
    {
        return 65;
    }

    public static int legCountEncodingLength()
    {
        return 1;
    }

    public static String legCountMetaAttribute(final MetaAttribute metaAttribute)
    {
        if (MetaAttribute.PRESENCE == metaAttribute)
        {
            return "required";
        }

        return "";
    }

    public static byte legCountNullValue()
    {
        return (byte)-128;
    }

    public static byte legCountMinValue()
    {
        return (byte)-127;
    }

    public static byte legCountMaxValue()
    {
        return (byte)127;
    }

    public ParentOrderIntentEncoder legCount(final byte value)
    {
        buffer.putByte(offset + 65, value);
        return this;
    }


    public static int leg2SideId()
    {
        return 19;
    }

    public static int leg2SideSinceVersion()
    {
        return 0;
    }

    public static int leg2SideEncodingOffset()
    {
        return 66;
    }

    public static int leg2SideEncodingLength()
    {
        return 1;
    }

    public static String leg2SideMetaAttribute(final MetaAttribute metaAttribute)
    {
        if (MetaAttribute.PRESENCE == metaAttribute)
        {
            return "required";
        }

        return "";
    }

    public ParentOrderIntentEncoder leg2Side(final Side value)
    {
        buffer.putByte(offset + 66, value.value());
        return this;
    }

    public static int leg2LimitPriceScaledId()
    {
        return 20;
    }

    public static int leg2LimitPriceScaledSinceVersion()
    {
        return 0;
    }

    public static int leg2LimitPriceScaledEncodingOffset()
    {
        return 67;
    }

    public static int leg2LimitPriceScaledEncodingLength()
    {
        return 8;
    }

    public static String leg2LimitPriceScaledMetaAttribute(final MetaAttribute metaAttribute)
    {
        if (MetaAttribute.PRESENCE == metaAttribute)
        {
            return "required";
        }

        return "";
    }

    public static long leg2LimitPriceScaledNullValue()
    {
        return -9223372036854775808L;
    }

    public static long leg2LimitPriceScaledMinValue()
    {
        return -9223372036854775807L;
    }

    public static long leg2LimitPriceScaledMaxValue()
    {
        return 9223372036854775807L;
    }

    public ParentOrderIntentEncoder leg2LimitPriceScaled(final long value)
    {
        buffer.putLong(offset + 67, value, BYTE_ORDER);
        return this;
    }


    public static int parentTimeoutMicrosId()
    {
        return 21;
    }

    public static int parentTimeoutMicrosSinceVersion()
    {
        return 0;
    }

    public static int parentTimeoutMicrosEncodingOffset()
    {
        return 75;
    }

    public static int parentTimeoutMicrosEncodingLength()
    {
        return 8;
    }

    public static String parentTimeoutMicrosMetaAttribute(final MetaAttribute metaAttribute)
    {
        if (MetaAttribute.PRESENCE == metaAttribute)
        {
            return "required";
        }

        return "";
    }

    public static long parentTimeoutMicrosNullValue()
    {
        return -9223372036854775808L;
    }

    public static long parentTimeoutMicrosMinValue()
    {
        return -9223372036854775807L;
    }

    public static long parentTimeoutMicrosMaxValue()
    {
        return 9223372036854775807L;
    }

    public ParentOrderIntentEncoder parentTimeoutMicros(final long value)
    {
        buffer.putLong(offset + 75, value, BYTE_ORDER);
        return this;
    }


    public static int venueSetIdId()
    {
        return 22;
    }

    public static int venueSetIdSinceVersion()
    {
        return 3;
    }

    public static int venueSetIdEncodingOffset()
    {
        return 83;
    }

    public static int venueSetIdEncodingLength()
    {
        return 4;
    }

    public static String venueSetIdMetaAttribute(final MetaAttribute metaAttribute)
    {
        if (MetaAttribute.PRESENCE == metaAttribute)
        {
            return "optional";
        }

        return "";
    }

    public static int venueSetIdNullValue()
    {
        return 0;
    }

    public static int venueSetIdMinValue()
    {
        return -2147483647;
    }

    public static int venueSetIdMaxValue()
    {
        return 2147483647;
    }

    public ParentOrderIntentEncoder venueSetId(final int value)
    {
        buffer.putInt(offset + 83, value, BYTE_ORDER);
        return this;
    }


    public String toString()
    {
        if (null == buffer)
        {
            return "";
        }

        return appendTo(new StringBuilder()).toString();
    }

    public StringBuilder appendTo(final StringBuilder builder)
    {
        if (null == buffer)
        {
            return builder;
        }

        final ParentOrderIntentDecoder decoder = new ParentOrderIntentDecoder();
        decoder.wrap(buffer, offset, BLOCK_LENGTH, SCHEMA_VERSION);

        return decoder.appendTo(builder);
    }
}
