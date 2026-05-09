/* Generated SBE (Simple Binary Encoding) message codec. */
package ig.rueishi.nitroj.exchange.messages;

import org.agrona.DirectBuffer;

@SuppressWarnings("all")
public final class PositionSnapshotDecoder
{
    public static final int BLOCK_LENGTH = 32;
    public static final int TEMPLATE_ID = 51;
    public static final int SCHEMA_ID = 1;
    public static final int SCHEMA_VERSION = 3;
    public static final String SEMANTIC_VERSION = "5.3";
    public static final java.nio.ByteOrder BYTE_ORDER = java.nio.ByteOrder.LITTLE_ENDIAN;

    private final PositionSnapshotDecoder parentMessage = this;
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

    public PositionSnapshotDecoder wrap(
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

    public PositionSnapshotDecoder wrapAndApplyHeader(
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

    public PositionSnapshotDecoder sbeRewind()
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

    public int venueId()
    {
        return buffer.getInt(offset + 0, BYTE_ORDER);
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

    public int instrumentId()
    {
        return buffer.getInt(offset + 4, BYTE_ORDER);
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

    public long netQtyScaled()
    {
        return buffer.getLong(offset + 8, BYTE_ORDER);
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

    public long avgEntryPriceScaled()
    {
        return buffer.getLong(offset + 16, BYTE_ORDER);
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

    public long realizedPnlScaled()
    {
        return buffer.getLong(offset + 24, BYTE_ORDER);
    }


    public String toString()
    {
        if (null == buffer)
        {
            return "";
        }

        final PositionSnapshotDecoder decoder = new PositionSnapshotDecoder();
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
        builder.append("[PositionSnapshot](sbeTemplateId=");
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
        builder.append("venueId=");
        builder.append(this.venueId());
        builder.append('|');
        builder.append("instrumentId=");
        builder.append(this.instrumentId());
        builder.append('|');
        builder.append("netQtyScaled=");
        builder.append(this.netQtyScaled());
        builder.append('|');
        builder.append("avgEntryPriceScaled=");
        builder.append(this.avgEntryPriceScaled());
        builder.append('|');
        builder.append("realizedPnlScaled=");
        builder.append(this.realizedPnlScaled());

        limit(originalLimit);

        return builder;
    }
    
    public PositionSnapshotDecoder sbeSkip()
    {
        sbeRewind();

        return this;
    }
}
