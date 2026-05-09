/* Generated SBE (Simple Binary Encoding) message codec. */
package ig.rueishi.nitroj.exchange.messages;

import org.agrona.DirectBuffer;


/**
 * Risk state change
 */
@SuppressWarnings("all")
public final class RiskEventDecoder
{
    public static final int BLOCK_LENGTH = 41;
    public static final int TEMPLATE_ID = 22;
    public static final int SCHEMA_ID = 1;
    public static final int SCHEMA_VERSION = 3;
    public static final String SEMANTIC_VERSION = "5.3";
    public static final java.nio.ByteOrder BYTE_ORDER = java.nio.ByteOrder.LITTLE_ENDIAN;

    private final RiskEventDecoder parentMessage = this;
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

    public RiskEventDecoder wrap(
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

    public RiskEventDecoder wrapAndApplyHeader(
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

    public RiskEventDecoder sbeRewind()
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

    public static int riskEventTypeId()
    {
        return 1;
    }

    public static int riskEventTypeSinceVersion()
    {
        return 0;
    }

    public static int riskEventTypeEncodingOffset()
    {
        return 0;
    }

    public static int riskEventTypeEncodingLength()
    {
        return 1;
    }

    public static String riskEventTypeMetaAttribute(final MetaAttribute metaAttribute)
    {
        if (MetaAttribute.PRESENCE == metaAttribute)
        {
            return "required";
        }

        return "";
    }

    public byte riskEventTypeRaw()
    {
        return buffer.getByte(offset + 0);
    }

    public RiskEventType riskEventType()
    {
        return RiskEventType.get(buffer.getByte(offset + 0));
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
        return 1;
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
        return buffer.getInt(offset + 1, BYTE_ORDER);
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
        return 5;
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
        return buffer.getInt(offset + 5, BYTE_ORDER);
    }


    public static int affectedClOrdIdId()
    {
        return 4;
    }

    public static int affectedClOrdIdSinceVersion()
    {
        return 0;
    }

    public static int affectedClOrdIdEncodingOffset()
    {
        return 9;
    }

    public static int affectedClOrdIdEncodingLength()
    {
        return 8;
    }

    public static String affectedClOrdIdMetaAttribute(final MetaAttribute metaAttribute)
    {
        if (MetaAttribute.PRESENCE == metaAttribute)
        {
            return "required";
        }

        return "";
    }

    public static long affectedClOrdIdNullValue()
    {
        return -9223372036854775808L;
    }

    public static long affectedClOrdIdMinValue()
    {
        return -9223372036854775807L;
    }

    public static long affectedClOrdIdMaxValue()
    {
        return 9223372036854775807L;
    }

    public long affectedClOrdId()
    {
        return buffer.getLong(offset + 9, BYTE_ORDER);
    }


    public static int limitValueScaledId()
    {
        return 5;
    }

    public static int limitValueScaledSinceVersion()
    {
        return 0;
    }

    public static int limitValueScaledEncodingOffset()
    {
        return 17;
    }

    public static int limitValueScaledEncodingLength()
    {
        return 8;
    }

    public static String limitValueScaledMetaAttribute(final MetaAttribute metaAttribute)
    {
        if (MetaAttribute.PRESENCE == metaAttribute)
        {
            return "required";
        }

        return "";
    }

    public static long limitValueScaledNullValue()
    {
        return -9223372036854775808L;
    }

    public static long limitValueScaledMinValue()
    {
        return -9223372036854775807L;
    }

    public static long limitValueScaledMaxValue()
    {
        return 9223372036854775807L;
    }

    public long limitValueScaled()
    {
        return buffer.getLong(offset + 17, BYTE_ORDER);
    }


    public static int currentValueScaledId()
    {
        return 6;
    }

    public static int currentValueScaledSinceVersion()
    {
        return 0;
    }

    public static int currentValueScaledEncodingOffset()
    {
        return 25;
    }

    public static int currentValueScaledEncodingLength()
    {
        return 8;
    }

    public static String currentValueScaledMetaAttribute(final MetaAttribute metaAttribute)
    {
        if (MetaAttribute.PRESENCE == metaAttribute)
        {
            return "required";
        }

        return "";
    }

    public static long currentValueScaledNullValue()
    {
        return -9223372036854775808L;
    }

    public static long currentValueScaledMinValue()
    {
        return -9223372036854775807L;
    }

    public static long currentValueScaledMaxValue()
    {
        return 9223372036854775807L;
    }

    public long currentValueScaled()
    {
        return buffer.getLong(offset + 25, BYTE_ORDER);
    }


    public static int eventClusterTimeId()
    {
        return 7;
    }

    public static int eventClusterTimeSinceVersion()
    {
        return 0;
    }

    public static int eventClusterTimeEncodingOffset()
    {
        return 33;
    }

    public static int eventClusterTimeEncodingLength()
    {
        return 8;
    }

    public static String eventClusterTimeMetaAttribute(final MetaAttribute metaAttribute)
    {
        if (MetaAttribute.PRESENCE == metaAttribute)
        {
            return "required";
        }

        return "";
    }

    public static long eventClusterTimeNullValue()
    {
        return -9223372036854775808L;
    }

    public static long eventClusterTimeMinValue()
    {
        return -9223372036854775807L;
    }

    public static long eventClusterTimeMaxValue()
    {
        return 9223372036854775807L;
    }

    public long eventClusterTime()
    {
        return buffer.getLong(offset + 33, BYTE_ORDER);
    }


    public String toString()
    {
        if (null == buffer)
        {
            return "";
        }

        final RiskEventDecoder decoder = new RiskEventDecoder();
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
        builder.append("[RiskEvent](sbeTemplateId=");
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
        builder.append("riskEventType=");
        builder.append(this.riskEventType());
        builder.append('|');
        builder.append("venueId=");
        builder.append(this.venueId());
        builder.append('|');
        builder.append("instrumentId=");
        builder.append(this.instrumentId());
        builder.append('|');
        builder.append("affectedClOrdId=");
        builder.append(this.affectedClOrdId());
        builder.append('|');
        builder.append("limitValueScaled=");
        builder.append(this.limitValueScaled());
        builder.append('|');
        builder.append("currentValueScaled=");
        builder.append(this.currentValueScaled());
        builder.append('|');
        builder.append("eventClusterTime=");
        builder.append(this.eventClusterTime());

        limit(originalLimit);

        return builder;
    }
    
    public RiskEventDecoder sbeSkip()
    {
        sbeRewind();

        return this;
    }
}
