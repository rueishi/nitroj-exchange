/* Generated SBE (Simple Binary Encoding) message codec. */
package ig.rueishi.nitroj.exchange.messages;

import org.agrona.MutableDirectBuffer;


/**
 * Fill notification for metrics/audit
 */
@SuppressWarnings("all")
public final class FillEventEncoder
{
    public static final int BLOCK_LENGTH = 69;
    public static final int TEMPLATE_ID = 20;
    public static final int SCHEMA_ID = 1;
    public static final int SCHEMA_VERSION = 3;
    public static final String SEMANTIC_VERSION = "5.3";
    public static final java.nio.ByteOrder BYTE_ORDER = java.nio.ByteOrder.LITTLE_ENDIAN;

    private final FillEventEncoder parentMessage = this;
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

    public FillEventEncoder wrap(final MutableDirectBuffer buffer, final int offset)
    {
        if (buffer != this.buffer)
        {
            this.buffer = buffer;
        }
        this.offset = offset;
        limit(offset + BLOCK_LENGTH);

        return this;
    }

    public FillEventEncoder wrapAndApplyHeader(
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

    public static int clOrdIdId()
    {
        return 1;
    }

    public static int clOrdIdSinceVersion()
    {
        return 0;
    }

    public static int clOrdIdEncodingOffset()
    {
        return 0;
    }

    public static int clOrdIdEncodingLength()
    {
        return 8;
    }

    public static String clOrdIdMetaAttribute(final MetaAttribute metaAttribute)
    {
        if (MetaAttribute.PRESENCE == metaAttribute)
        {
            return "required";
        }

        return "";
    }

    public static long clOrdIdNullValue()
    {
        return -9223372036854775808L;
    }

    public static long clOrdIdMinValue()
    {
        return -9223372036854775807L;
    }

    public static long clOrdIdMaxValue()
    {
        return 9223372036854775807L;
    }

    public FillEventEncoder clOrdId(final long value)
    {
        buffer.putLong(offset + 0, value, BYTE_ORDER);
        return this;
    }


    public static int venueIdId()
    {
        return 2;
    }

    public static int venueIdSinceVersion()
    {
        return 0;
    }

    public static int venueIdEncodingOffset()
    {
        return 8;
    }

    public static int venueIdEncodingLength()
    {
        return 4;
    }

    public static String venueIdMetaAttribute(final MetaAttribute metaAttribute)
    {
        if (MetaAttribute.PRESENCE == metaAttribute)
        {
            return "required";
        }

        return "";
    }

    public static int venueIdNullValue()
    {
        return -2147483648;
    }

    public static int venueIdMinValue()
    {
        return -2147483647;
    }

    public static int venueIdMaxValue()
    {
        return 2147483647;
    }

    public FillEventEncoder venueId(final int value)
    {
        buffer.putInt(offset + 8, value, BYTE_ORDER);
        return this;
    }


    public static int instrumentIdId()
    {
        return 3;
    }

    public static int instrumentIdSinceVersion()
    {
        return 0;
    }

    public static int instrumentIdEncodingOffset()
    {
        return 12;
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

    public FillEventEncoder instrumentId(final int value)
    {
        buffer.putInt(offset + 12, value, BYTE_ORDER);
        return this;
    }


    public static int strategyIdId()
    {
        return 4;
    }

    public static int strategyIdSinceVersion()
    {
        return 0;
    }

    public static int strategyIdEncodingOffset()
    {
        return 16;
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

    public FillEventEncoder strategyId(final short value)
    {
        buffer.putShort(offset + 16, value, BYTE_ORDER);
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
        return 18;
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

    public FillEventEncoder side(final Side value)
    {
        buffer.putByte(offset + 18, value.value());
        return this;
    }

    public static int fillPriceScaledId()
    {
        return 6;
    }

    public static int fillPriceScaledSinceVersion()
    {
        return 0;
    }

    public static int fillPriceScaledEncodingOffset()
    {
        return 19;
    }

    public static int fillPriceScaledEncodingLength()
    {
        return 8;
    }

    public static String fillPriceScaledMetaAttribute(final MetaAttribute metaAttribute)
    {
        if (MetaAttribute.PRESENCE == metaAttribute)
        {
            return "required";
        }

        return "";
    }

    public static long fillPriceScaledNullValue()
    {
        return -9223372036854775808L;
    }

    public static long fillPriceScaledMinValue()
    {
        return -9223372036854775807L;
    }

    public static long fillPriceScaledMaxValue()
    {
        return 9223372036854775807L;
    }

    public FillEventEncoder fillPriceScaled(final long value)
    {
        buffer.putLong(offset + 19, value, BYTE_ORDER);
        return this;
    }


    public static int fillQtyScaledId()
    {
        return 7;
    }

    public static int fillQtyScaledSinceVersion()
    {
        return 0;
    }

    public static int fillQtyScaledEncodingOffset()
    {
        return 27;
    }

    public static int fillQtyScaledEncodingLength()
    {
        return 8;
    }

    public static String fillQtyScaledMetaAttribute(final MetaAttribute metaAttribute)
    {
        if (MetaAttribute.PRESENCE == metaAttribute)
        {
            return "required";
        }

        return "";
    }

    public static long fillQtyScaledNullValue()
    {
        return -9223372036854775808L;
    }

    public static long fillQtyScaledMinValue()
    {
        return -9223372036854775807L;
    }

    public static long fillQtyScaledMaxValue()
    {
        return 9223372036854775807L;
    }

    public FillEventEncoder fillQtyScaled(final long value)
    {
        buffer.putLong(offset + 27, value, BYTE_ORDER);
        return this;
    }


    public static int cumFillQtyScaledId()
    {
        return 8;
    }

    public static int cumFillQtyScaledSinceVersion()
    {
        return 0;
    }

    public static int cumFillQtyScaledEncodingOffset()
    {
        return 35;
    }

    public static int cumFillQtyScaledEncodingLength()
    {
        return 8;
    }

    public static String cumFillQtyScaledMetaAttribute(final MetaAttribute metaAttribute)
    {
        if (MetaAttribute.PRESENCE == metaAttribute)
        {
            return "required";
        }

        return "";
    }

    public static long cumFillQtyScaledNullValue()
    {
        return -9223372036854775808L;
    }

    public static long cumFillQtyScaledMinValue()
    {
        return -9223372036854775807L;
    }

    public static long cumFillQtyScaledMaxValue()
    {
        return 9223372036854775807L;
    }

    public FillEventEncoder cumFillQtyScaled(final long value)
    {
        buffer.putLong(offset + 35, value, BYTE_ORDER);
        return this;
    }


    public static int leavesQtyScaledId()
    {
        return 9;
    }

    public static int leavesQtyScaledSinceVersion()
    {
        return 0;
    }

    public static int leavesQtyScaledEncodingOffset()
    {
        return 43;
    }

    public static int leavesQtyScaledEncodingLength()
    {
        return 8;
    }

    public static String leavesQtyScaledMetaAttribute(final MetaAttribute metaAttribute)
    {
        if (MetaAttribute.PRESENCE == metaAttribute)
        {
            return "required";
        }

        return "";
    }

    public static long leavesQtyScaledNullValue()
    {
        return -9223372036854775808L;
    }

    public static long leavesQtyScaledMinValue()
    {
        return -9223372036854775807L;
    }

    public static long leavesQtyScaledMaxValue()
    {
        return 9223372036854775807L;
    }

    public FillEventEncoder leavesQtyScaled(final long value)
    {
        buffer.putLong(offset + 43, value, BYTE_ORDER);
        return this;
    }


    public static int isFinalId()
    {
        return 10;
    }

    public static int isFinalSinceVersion()
    {
        return 0;
    }

    public static int isFinalEncodingOffset()
    {
        return 51;
    }

    public static int isFinalEncodingLength()
    {
        return 1;
    }

    public static String isFinalMetaAttribute(final MetaAttribute metaAttribute)
    {
        if (MetaAttribute.PRESENCE == metaAttribute)
        {
            return "required";
        }

        return "";
    }

    public FillEventEncoder isFinal(final BooleanType value)
    {
        buffer.putByte(offset + 51, value.value());
        return this;
    }

    public static int isSyntheticId()
    {
        return 11;
    }

    public static int isSyntheticSinceVersion()
    {
        return 0;
    }

    public static int isSyntheticEncodingOffset()
    {
        return 52;
    }

    public static int isSyntheticEncodingLength()
    {
        return 1;
    }

    public static String isSyntheticMetaAttribute(final MetaAttribute metaAttribute)
    {
        if (MetaAttribute.PRESENCE == metaAttribute)
        {
            return "required";
        }

        return "";
    }

    public FillEventEncoder isSynthetic(final BooleanType value)
    {
        buffer.putByte(offset + 52, value.value());
        return this;
    }

    public static int fillTimestampNanosId()
    {
        return 12;
    }

    public static int fillTimestampNanosSinceVersion()
    {
        return 0;
    }

    public static int fillTimestampNanosEncodingOffset()
    {
        return 53;
    }

    public static int fillTimestampNanosEncodingLength()
    {
        return 8;
    }

    public static String fillTimestampNanosMetaAttribute(final MetaAttribute metaAttribute)
    {
        if (MetaAttribute.PRESENCE == metaAttribute)
        {
            return "required";
        }

        return "";
    }

    public static long fillTimestampNanosNullValue()
    {
        return -9223372036854775808L;
    }

    public static long fillTimestampNanosMinValue()
    {
        return -9223372036854775807L;
    }

    public static long fillTimestampNanosMaxValue()
    {
        return 9223372036854775807L;
    }

    public FillEventEncoder fillTimestampNanos(final long value)
    {
        buffer.putLong(offset + 53, value, BYTE_ORDER);
        return this;
    }


    public static int ingressTimestampNanosId()
    {
        return 13;
    }

    public static int ingressTimestampNanosSinceVersion()
    {
        return 0;
    }

    public static int ingressTimestampNanosEncodingOffset()
    {
        return 61;
    }

    public static int ingressTimestampNanosEncodingLength()
    {
        return 8;
    }

    public static String ingressTimestampNanosMetaAttribute(final MetaAttribute metaAttribute)
    {
        if (MetaAttribute.PRESENCE == metaAttribute)
        {
            return "required";
        }

        return "";
    }

    public static long ingressTimestampNanosNullValue()
    {
        return -9223372036854775808L;
    }

    public static long ingressTimestampNanosMinValue()
    {
        return -9223372036854775807L;
    }

    public static long ingressTimestampNanosMaxValue()
    {
        return 9223372036854775807L;
    }

    public FillEventEncoder ingressTimestampNanos(final long value)
    {
        buffer.putLong(offset + 61, value, BYTE_ORDER);
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

        final FillEventDecoder decoder = new FillEventDecoder();
        decoder.wrap(buffer, offset, BLOCK_LENGTH, SCHEMA_VERSION);

        return decoder.appendTo(builder);
    }
}
