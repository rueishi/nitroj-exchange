/* Generated SBE (Simple Binary Encoding) message codec. */
package ig.rueishi.nitroj.exchange.messages;

import org.agrona.MutableDirectBuffer;


/**
 * Position snapshot for metrics/audit
 */
@SuppressWarnings("all")
public final class PositionEventEncoder
{
    public static final int BLOCK_LENGTH = 56;
    public static final int TEMPLATE_ID = 21;
    public static final int SCHEMA_ID = 1;
    public static final int SCHEMA_VERSION = 3;
    public static final String SEMANTIC_VERSION = "5.3";
    public static final java.nio.ByteOrder BYTE_ORDER = java.nio.ByteOrder.LITTLE_ENDIAN;

    private final PositionEventEncoder parentMessage = this;
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

    public PositionEventEncoder wrap(final MutableDirectBuffer buffer, final int offset)
    {
        if (buffer != this.buffer)
        {
            this.buffer = buffer;
        }
        this.offset = offset;
        limit(offset + BLOCK_LENGTH);

        return this;
    }

    public PositionEventEncoder wrapAndApplyHeader(
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

    public static int venueIdId()
    {
        return 1;
    }

    public static int venueIdSinceVersion()
    {
        return 0;
    }

    public static int venueIdEncodingOffset()
    {
        return 0;
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

    public PositionEventEncoder venueId(final int value)
    {
        buffer.putInt(offset + 0, value, BYTE_ORDER);
        return this;
    }


    public static int instrumentIdId()
    {
        return 2;
    }

    public static int instrumentIdSinceVersion()
    {
        return 0;
    }

    public static int instrumentIdEncodingOffset()
    {
        return 4;
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

    public PositionEventEncoder instrumentId(final int value)
    {
        buffer.putInt(offset + 4, value, BYTE_ORDER);
        return this;
    }


    public static int netQtyScaledId()
    {
        return 3;
    }

    public static int netQtyScaledSinceVersion()
    {
        return 0;
    }

    public static int netQtyScaledEncodingOffset()
    {
        return 8;
    }

    public static int netQtyScaledEncodingLength()
    {
        return 8;
    }

    public static String netQtyScaledMetaAttribute(final MetaAttribute metaAttribute)
    {
        if (MetaAttribute.PRESENCE == metaAttribute)
        {
            return "required";
        }

        return "";
    }

    public static long netQtyScaledNullValue()
    {
        return -9223372036854775808L;
    }

    public static long netQtyScaledMinValue()
    {
        return -9223372036854775807L;
    }

    public static long netQtyScaledMaxValue()
    {
        return 9223372036854775807L;
    }

    public PositionEventEncoder netQtyScaled(final long value)
    {
        buffer.putLong(offset + 8, value, BYTE_ORDER);
        return this;
    }


    public static int avgEntryPriceScaledId()
    {
        return 4;
    }

    public static int avgEntryPriceScaledSinceVersion()
    {
        return 0;
    }

    public static int avgEntryPriceScaledEncodingOffset()
    {
        return 16;
    }

    public static int avgEntryPriceScaledEncodingLength()
    {
        return 8;
    }

    public static String avgEntryPriceScaledMetaAttribute(final MetaAttribute metaAttribute)
    {
        if (MetaAttribute.PRESENCE == metaAttribute)
        {
            return "required";
        }

        return "";
    }

    public static long avgEntryPriceScaledNullValue()
    {
        return -9223372036854775808L;
    }

    public static long avgEntryPriceScaledMinValue()
    {
        return -9223372036854775807L;
    }

    public static long avgEntryPriceScaledMaxValue()
    {
        return 9223372036854775807L;
    }

    public PositionEventEncoder avgEntryPriceScaled(final long value)
    {
        buffer.putLong(offset + 16, value, BYTE_ORDER);
        return this;
    }


    public static int realizedPnlScaledId()
    {
        return 5;
    }

    public static int realizedPnlScaledSinceVersion()
    {
        return 0;
    }

    public static int realizedPnlScaledEncodingOffset()
    {
        return 24;
    }

    public static int realizedPnlScaledEncodingLength()
    {
        return 8;
    }

    public static String realizedPnlScaledMetaAttribute(final MetaAttribute metaAttribute)
    {
        if (MetaAttribute.PRESENCE == metaAttribute)
        {
            return "required";
        }

        return "";
    }

    public static long realizedPnlScaledNullValue()
    {
        return -9223372036854775808L;
    }

    public static long realizedPnlScaledMinValue()
    {
        return -9223372036854775807L;
    }

    public static long realizedPnlScaledMaxValue()
    {
        return 9223372036854775807L;
    }

    public PositionEventEncoder realizedPnlScaled(final long value)
    {
        buffer.putLong(offset + 24, value, BYTE_ORDER);
        return this;
    }


    public static int unrealizedPnlScaledId()
    {
        return 6;
    }

    public static int unrealizedPnlScaledSinceVersion()
    {
        return 0;
    }

    public static int unrealizedPnlScaledEncodingOffset()
    {
        return 32;
    }

    public static int unrealizedPnlScaledEncodingLength()
    {
        return 8;
    }

    public static String unrealizedPnlScaledMetaAttribute(final MetaAttribute metaAttribute)
    {
        if (MetaAttribute.PRESENCE == metaAttribute)
        {
            return "required";
        }

        return "";
    }

    public static long unrealizedPnlScaledNullValue()
    {
        return -9223372036854775808L;
    }

    public static long unrealizedPnlScaledMinValue()
    {
        return -9223372036854775807L;
    }

    public static long unrealizedPnlScaledMaxValue()
    {
        return 9223372036854775807L;
    }

    public PositionEventEncoder unrealizedPnlScaled(final long value)
    {
        buffer.putLong(offset + 32, value, BYTE_ORDER);
        return this;
    }


    public static int snapshotClusterTimeId()
    {
        return 7;
    }

    public static int snapshotClusterTimeSinceVersion()
    {
        return 0;
    }

    public static int snapshotClusterTimeEncodingOffset()
    {
        return 40;
    }

    public static int snapshotClusterTimeEncodingLength()
    {
        return 8;
    }

    public static String snapshotClusterTimeMetaAttribute(final MetaAttribute metaAttribute)
    {
        if (MetaAttribute.PRESENCE == metaAttribute)
        {
            return "required";
        }

        return "";
    }

    public static long snapshotClusterTimeNullValue()
    {
        return -9223372036854775808L;
    }

    public static long snapshotClusterTimeMinValue()
    {
        return -9223372036854775807L;
    }

    public static long snapshotClusterTimeMaxValue()
    {
        return 9223372036854775807L;
    }

    public PositionEventEncoder snapshotClusterTime(final long value)
    {
        buffer.putLong(offset + 40, value, BYTE_ORDER);
        return this;
    }


    public static int triggeringClOrdIdId()
    {
        return 8;
    }

    public static int triggeringClOrdIdSinceVersion()
    {
        return 0;
    }

    public static int triggeringClOrdIdEncodingOffset()
    {
        return 48;
    }

    public static int triggeringClOrdIdEncodingLength()
    {
        return 8;
    }

    public static String triggeringClOrdIdMetaAttribute(final MetaAttribute metaAttribute)
    {
        if (MetaAttribute.PRESENCE == metaAttribute)
        {
            return "required";
        }

        return "";
    }

    public static long triggeringClOrdIdNullValue()
    {
        return -9223372036854775808L;
    }

    public static long triggeringClOrdIdMinValue()
    {
        return -9223372036854775807L;
    }

    public static long triggeringClOrdIdMaxValue()
    {
        return 9223372036854775807L;
    }

    public PositionEventEncoder triggeringClOrdId(final long value)
    {
        buffer.putLong(offset + 48, value, BYTE_ORDER);
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

        final PositionEventDecoder decoder = new PositionEventDecoder();
        decoder.wrap(buffer, offset, BLOCK_LENGTH, SCHEMA_VERSION);

        return decoder.appendTo(builder);
    }
}
