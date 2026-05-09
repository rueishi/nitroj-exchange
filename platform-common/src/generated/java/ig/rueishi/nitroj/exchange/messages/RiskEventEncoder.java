/* Generated SBE (Simple Binary Encoding) message codec. */
package ig.rueishi.nitroj.exchange.messages;

import org.agrona.MutableDirectBuffer;


/**
 * Risk state change
 */
@SuppressWarnings("all")
public final class RiskEventEncoder
{
    public static final int BLOCK_LENGTH = 41;
    public static final int TEMPLATE_ID = 22;
    public static final int SCHEMA_ID = 1;
    public static final int SCHEMA_VERSION = 3;
    public static final String SEMANTIC_VERSION = "5.3";
    public static final java.nio.ByteOrder BYTE_ORDER = java.nio.ByteOrder.LITTLE_ENDIAN;

    private final RiskEventEncoder parentMessage = this;
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

    public RiskEventEncoder wrap(final MutableDirectBuffer buffer, final int offset)
    {
        if (buffer != this.buffer)
        {
            this.buffer = buffer;
        }
        this.offset = offset;
        limit(offset + BLOCK_LENGTH);

        return this;
    }

    public RiskEventEncoder wrapAndApplyHeader(
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

    public RiskEventEncoder riskEventType(final RiskEventType value)
    {
        buffer.putByte(offset + 0, value.value());
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

    public RiskEventEncoder venueId(final int value)
    {
        buffer.putInt(offset + 1, value, BYTE_ORDER);
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

    public RiskEventEncoder instrumentId(final int value)
    {
        buffer.putInt(offset + 5, value, BYTE_ORDER);
        return this;
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

    public RiskEventEncoder affectedClOrdId(final long value)
    {
        buffer.putLong(offset + 9, value, BYTE_ORDER);
        return this;
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

    public RiskEventEncoder limitValueScaled(final long value)
    {
        buffer.putLong(offset + 17, value, BYTE_ORDER);
        return this;
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

    public RiskEventEncoder currentValueScaled(final long value)
    {
        buffer.putLong(offset + 25, value, BYTE_ORDER);
        return this;
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

    public RiskEventEncoder eventClusterTime(final long value)
    {
        buffer.putLong(offset + 33, value, BYTE_ORDER);
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

        final RiskEventDecoder decoder = new RiskEventDecoder();
        decoder.wrap(buffer, offset, BLOCK_LENGTH, SCHEMA_VERSION);

        return decoder.appendTo(builder);
    }
}
