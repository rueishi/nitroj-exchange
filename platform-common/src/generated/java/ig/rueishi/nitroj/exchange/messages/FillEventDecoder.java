/* Generated SBE (Simple Binary Encoding) message codec. */
package ig.rueishi.nitroj.exchange.messages;

import org.agrona.DirectBuffer;


/**
 * Fill notification for metrics/audit
 */
@SuppressWarnings("all")
public final class FillEventDecoder
{
    public static final int BLOCK_LENGTH = 69;
    public static final int TEMPLATE_ID = 20;
    public static final int SCHEMA_ID = 1;
    public static final int SCHEMA_VERSION = 3;
    public static final String SEMANTIC_VERSION = "5.3";
    public static final java.nio.ByteOrder BYTE_ORDER = java.nio.ByteOrder.LITTLE_ENDIAN;

    private final FillEventDecoder parentMessage = this;
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

    public FillEventDecoder wrap(
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

    public FillEventDecoder wrapAndApplyHeader(
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

    public FillEventDecoder sbeRewind()
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

    public long clOrdId()
    {
        return buffer.getLong(offset + 0, BYTE_ORDER);
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

    public int venueId()
    {
        return buffer.getInt(offset + 8, BYTE_ORDER);
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

    public int instrumentId()
    {
        return buffer.getInt(offset + 12, BYTE_ORDER);
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

    public short strategyId()
    {
        return buffer.getShort(offset + 16, BYTE_ORDER);
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

    public byte sideRaw()
    {
        return buffer.getByte(offset + 18);
    }

    public Side side()
    {
        return Side.get(buffer.getByte(offset + 18));
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

    public long fillPriceScaled()
    {
        return buffer.getLong(offset + 19, BYTE_ORDER);
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

    public long fillQtyScaled()
    {
        return buffer.getLong(offset + 27, BYTE_ORDER);
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

    public long cumFillQtyScaled()
    {
        return buffer.getLong(offset + 35, BYTE_ORDER);
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

    public long leavesQtyScaled()
    {
        return buffer.getLong(offset + 43, BYTE_ORDER);
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

    public byte isFinalRaw()
    {
        return buffer.getByte(offset + 51);
    }

    public BooleanType isFinal()
    {
        return BooleanType.get(buffer.getByte(offset + 51));
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

    public byte isSyntheticRaw()
    {
        return buffer.getByte(offset + 52);
    }

    public BooleanType isSynthetic()
    {
        return BooleanType.get(buffer.getByte(offset + 52));
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

    public long fillTimestampNanos()
    {
        return buffer.getLong(offset + 53, BYTE_ORDER);
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

    public long ingressTimestampNanos()
    {
        return buffer.getLong(offset + 61, BYTE_ORDER);
    }


    public String toString()
    {
        if (null == buffer)
        {
            return "";
        }

        final FillEventDecoder decoder = new FillEventDecoder();
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
        builder.append("[FillEvent](sbeTemplateId=");
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
        builder.append("clOrdId=");
        builder.append(this.clOrdId());
        builder.append('|');
        builder.append("venueId=");
        builder.append(this.venueId());
        builder.append('|');
        builder.append("instrumentId=");
        builder.append(this.instrumentId());
        builder.append('|');
        builder.append("strategyId=");
        builder.append(this.strategyId());
        builder.append('|');
        builder.append("side=");
        builder.append(this.side());
        builder.append('|');
        builder.append("fillPriceScaled=");
        builder.append(this.fillPriceScaled());
        builder.append('|');
        builder.append("fillQtyScaled=");
        builder.append(this.fillQtyScaled());
        builder.append('|');
        builder.append("cumFillQtyScaled=");
        builder.append(this.cumFillQtyScaled());
        builder.append('|');
        builder.append("leavesQtyScaled=");
        builder.append(this.leavesQtyScaled());
        builder.append('|');
        builder.append("isFinal=");
        builder.append(this.isFinal());
        builder.append('|');
        builder.append("isSynthetic=");
        builder.append(this.isSynthetic());
        builder.append('|');
        builder.append("fillTimestampNanos=");
        builder.append(this.fillTimestampNanos());
        builder.append('|');
        builder.append("ingressTimestampNanos=");
        builder.append(this.ingressTimestampNanos());

        limit(originalLimit);

        return builder;
    }
    
    public FillEventDecoder sbeSkip()
    {
        sbeRewind();

        return this;
    }
}
