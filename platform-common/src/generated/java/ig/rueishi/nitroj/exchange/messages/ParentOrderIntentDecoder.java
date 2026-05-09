/* Generated SBE (Simple Binary Encoding) message codec. */
package ig.rueishi.nitroj.exchange.messages;

import org.agrona.DirectBuffer;


/**
 * Declarative parent-order intent from trading strategy to execution strategy
 */
@SuppressWarnings("all")
public final class ParentOrderIntentDecoder
{
    public static final int BLOCK_LENGTH = 87;
    public static final int TEMPLATE_ID = 40;
    public static final int SCHEMA_ID = 1;
    public static final int SCHEMA_VERSION = 3;
    public static final String SEMANTIC_VERSION = "5.3";
    public static final java.nio.ByteOrder BYTE_ORDER = java.nio.ByteOrder.LITTLE_ENDIAN;

    private final ParentOrderIntentDecoder parentMessage = this;
    private DirectBuffer buffer;
    private int offset;
    private int limit;
    int actingBlockLength;
    int actingVersion;

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

    public DirectBuffer buffer()
    {
        return buffer;
    }

    public int offset()
    {
        return offset;
    }

    public ParentOrderIntentDecoder wrap(
        final DirectBuffer buffer,
        final int offset,
        final int actingBlockLength,
        final int actingVersion)
    {
        if (buffer != this.buffer)
        {
            this.buffer = buffer;
        }
        this.offset = offset;
        this.actingBlockLength = actingBlockLength;
        this.actingVersion = actingVersion;
        limit(offset + actingBlockLength);

        return this;
    }

    public ParentOrderIntentDecoder wrapAndApplyHeader(
        final DirectBuffer buffer,
        final int offset,
        final MessageHeaderDecoder headerDecoder)
    {
        headerDecoder.wrap(buffer, offset);

        final int templateId = headerDecoder.templateId();
        if (TEMPLATE_ID != templateId)
        {
            throw new IllegalStateException("Invalid TEMPLATE_ID: " + templateId);
        }

        return wrap(
            buffer,
            offset + MessageHeaderDecoder.ENCODED_LENGTH,
            headerDecoder.blockLength(),
            headerDecoder.version());
    }

    public ParentOrderIntentDecoder sbeRewind()
    {
        return wrap(buffer, offset, actingBlockLength, actingVersion);
    }

    public int sbeDecodedLength()
    {
        final int currentLimit = limit();
        sbeSkip();
        final int decodedLength = encodedLength();
        limit(currentLimit);

        return decodedLength;
    }

    public int actingVersion()
    {
        return actingVersion;
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

    public long parentOrderId()
    {
        return buffer.getLong(offset + 0, BYTE_ORDER);
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

    public short strategyId()
    {
        return buffer.getShort(offset + 8, BYTE_ORDER);
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

    public int executionStrategyId()
    {
        return buffer.getInt(offset + 10, BYTE_ORDER);
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

    public byte intentTypeRaw()
    {
        return buffer.getByte(offset + 14);
    }

    public ParentIntentType intentType()
    {
        return ParentIntentType.get(buffer.getByte(offset + 14));
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

    public byte sideRaw()
    {
        return buffer.getByte(offset + 15);
    }

    public Side side()
    {
        return Side.get(buffer.getByte(offset + 15));
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

    public int instrumentId()
    {
        return buffer.getInt(offset + 16, BYTE_ORDER);
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

    public int primaryVenueId()
    {
        return buffer.getInt(offset + 20, BYTE_ORDER);
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

    public int secondaryVenueId()
    {
        return buffer.getInt(offset + 24, BYTE_ORDER);
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

    public long quantityScaled()
    {
        return buffer.getLong(offset + 28, BYTE_ORDER);
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

    public byte priceModeRaw()
    {
        return buffer.getByte(offset + 36);
    }

    public PriceMode priceMode()
    {
        return PriceMode.get(buffer.getByte(offset + 36));
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

    public long limitPriceScaled()
    {
        return buffer.getLong(offset + 37, BYTE_ORDER);
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

    public long referencePriceScaled()
    {
        return buffer.getLong(offset + 45, BYTE_ORDER);
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

    public byte timeInForcePreferenceRaw()
    {
        return buffer.getByte(offset + 53);
    }

    public TimeInForce timeInForcePreference()
    {
        return TimeInForce.get(buffer.getByte(offset + 53));
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

    public byte urgencyHint()
    {
        return buffer.getByte(offset + 54);
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

    public byte postOnlyPreferenceRaw()
    {
        return buffer.getByte(offset + 55);
    }

    public BooleanType postOnlyPreference()
    {
        return BooleanType.get(buffer.getByte(offset + 55));
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

    public byte selfTradePolicy()
    {
        return buffer.getByte(offset + 56);
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

    public long correlationId()
    {
        return buffer.getLong(offset + 57, BYTE_ORDER);
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

    public byte legCount()
    {
        return buffer.getByte(offset + 65);
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

    public byte leg2SideRaw()
    {
        return buffer.getByte(offset + 66);
    }

    public Side leg2Side()
    {
        return Side.get(buffer.getByte(offset + 66));
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

    public long leg2LimitPriceScaled()
    {
        return buffer.getLong(offset + 67, BYTE_ORDER);
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

    public long parentTimeoutMicros()
    {
        return buffer.getLong(offset + 75, BYTE_ORDER);
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

    public int venueSetId()
    {
        if (parentMessage.actingVersion < 3)
        {
            return 0;
        }

        return buffer.getInt(offset + 83, BYTE_ORDER);
    }


    public String toString()
    {
        if (null == buffer)
        {
            return "";
        }

        final ParentOrderIntentDecoder decoder = new ParentOrderIntentDecoder();
        decoder.wrap(buffer, offset, actingBlockLength, actingVersion);

        return decoder.appendTo(new StringBuilder()).toString();
    }

    public StringBuilder appendTo(final StringBuilder builder)
    {
        if (null == buffer)
        {
            return builder;
        }

        final int originalLimit = limit();
        limit(offset + actingBlockLength);
        builder.append("[ParentOrderIntent](sbeTemplateId=");
        builder.append(TEMPLATE_ID);
        builder.append("|sbeSchemaId=");
        builder.append(SCHEMA_ID);
        builder.append("|sbeSchemaVersion=");
        if (parentMessage.actingVersion != SCHEMA_VERSION)
        {
            builder.append(parentMessage.actingVersion);
            builder.append('/');
        }
        builder.append(SCHEMA_VERSION);
        builder.append("|sbeBlockLength=");
        if (actingBlockLength != BLOCK_LENGTH)
        {
            builder.append(actingBlockLength);
            builder.append('/');
        }
        builder.append(BLOCK_LENGTH);
        builder.append("):");
        builder.append("parentOrderId=");
        builder.append(this.parentOrderId());
        builder.append('|');
        builder.append("strategyId=");
        builder.append(this.strategyId());
        builder.append('|');
        builder.append("executionStrategyId=");
        builder.append(this.executionStrategyId());
        builder.append('|');
        builder.append("intentType=");
        builder.append(this.intentType());
        builder.append('|');
        builder.append("side=");
        builder.append(this.side());
        builder.append('|');
        builder.append("instrumentId=");
        builder.append(this.instrumentId());
        builder.append('|');
        builder.append("primaryVenueId=");
        builder.append(this.primaryVenueId());
        builder.append('|');
        builder.append("secondaryVenueId=");
        builder.append(this.secondaryVenueId());
        builder.append('|');
        builder.append("quantityScaled=");
        builder.append(this.quantityScaled());
        builder.append('|');
        builder.append("priceMode=");
        builder.append(this.priceMode());
        builder.append('|');
        builder.append("limitPriceScaled=");
        builder.append(this.limitPriceScaled());
        builder.append('|');
        builder.append("referencePriceScaled=");
        builder.append(this.referencePriceScaled());
        builder.append('|');
        builder.append("timeInForcePreference=");
        builder.append(this.timeInForcePreference());
        builder.append('|');
        builder.append("urgencyHint=");
        builder.append(this.urgencyHint());
        builder.append('|');
        builder.append("postOnlyPreference=");
        builder.append(this.postOnlyPreference());
        builder.append('|');
        builder.append("selfTradePolicy=");
        builder.append(this.selfTradePolicy());
        builder.append('|');
        builder.append("correlationId=");
        builder.append(this.correlationId());
        builder.append('|');
        builder.append("legCount=");
        builder.append(this.legCount());
        builder.append('|');
        builder.append("leg2Side=");
        builder.append(this.leg2Side());
        builder.append('|');
        builder.append("leg2LimitPriceScaled=");
        builder.append(this.leg2LimitPriceScaled());
        builder.append('|');
        builder.append("parentTimeoutMicros=");
        builder.append(this.parentTimeoutMicros());
        builder.append('|');
        builder.append("venueSetId=");
        builder.append(this.venueSetId());

        limit(originalLimit);

        return builder;
    }
    
    public ParentOrderIntentDecoder sbeSkip()
    {
        sbeRewind();

        return this;
    }
}
