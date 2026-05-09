/* Generated SBE (Simple Binary Encoding) message codec. */
package ig.rueishi.nitroj.exchange.messages;

import org.agrona.MutableDirectBuffer;


/**
 * Order submission command to gateway
 */
@SuppressWarnings("all")
public final class NewOrderCommandEncoder
{
    public static final int BLOCK_LENGTH = 45;
    public static final int TEMPLATE_ID = 10;
    public static final int SCHEMA_ID = 1;
    public static final int SCHEMA_VERSION = 3;
    public static final String SEMANTIC_VERSION = "5.3";
    public static final java.nio.ByteOrder BYTE_ORDER = java.nio.ByteOrder.LITTLE_ENDIAN;

    private final NewOrderCommandEncoder parentMessage = this;
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

    public NewOrderCommandEncoder wrap(final MutableDirectBuffer buffer, final int offset)
    {
        if (buffer != this.buffer)
        {
            this.buffer = buffer;
        }
        this.offset = offset;
        limit(offset + BLOCK_LENGTH);

        return this;
    }

    public NewOrderCommandEncoder wrapAndApplyHeader(
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

    public NewOrderCommandEncoder clOrdId(final long value)
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

    public NewOrderCommandEncoder venueId(final int value)
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

    public NewOrderCommandEncoder instrumentId(final int value)
    {
        buffer.putInt(offset + 12, value, BYTE_ORDER);
        return this;
    }


    public static int sideId()
    {
        return 4;
    }

    public static int sideSinceVersion()
    {
        return 0;
    }

    public static int sideEncodingOffset()
    {
        return 16;
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

    public NewOrderCommandEncoder side(final Side value)
    {
        buffer.putByte(offset + 16, value.value());
        return this;
    }

    public static int ordTypeId()
    {
        return 5;
    }

    public static int ordTypeSinceVersion()
    {
        return 0;
    }

    public static int ordTypeEncodingOffset()
    {
        return 17;
    }

    public static int ordTypeEncodingLength()
    {
        return 1;
    }

    public static String ordTypeMetaAttribute(final MetaAttribute metaAttribute)
    {
        if (MetaAttribute.PRESENCE == metaAttribute)
        {
            return "required";
        }

        return "";
    }

    public NewOrderCommandEncoder ordType(final OrdType value)
    {
        buffer.putByte(offset + 17, value.value());
        return this;
    }

    public static int timeInForceId()
    {
        return 6;
    }

    public static int timeInForceSinceVersion()
    {
        return 0;
    }

    public static int timeInForceEncodingOffset()
    {
        return 18;
    }

    public static int timeInForceEncodingLength()
    {
        return 1;
    }

    public static String timeInForceMetaAttribute(final MetaAttribute metaAttribute)
    {
        if (MetaAttribute.PRESENCE == metaAttribute)
        {
            return "required";
        }

        return "";
    }

    public NewOrderCommandEncoder timeInForce(final TimeInForce value)
    {
        buffer.putByte(offset + 18, value.value());
        return this;
    }

    public static int priceScaledId()
    {
        return 7;
    }

    public static int priceScaledSinceVersion()
    {
        return 0;
    }

    public static int priceScaledEncodingOffset()
    {
        return 19;
    }

    public static int priceScaledEncodingLength()
    {
        return 8;
    }

    public static String priceScaledMetaAttribute(final MetaAttribute metaAttribute)
    {
        if (MetaAttribute.PRESENCE == metaAttribute)
        {
            return "required";
        }

        return "";
    }

    public static long priceScaledNullValue()
    {
        return -9223372036854775808L;
    }

    public static long priceScaledMinValue()
    {
        return -9223372036854775807L;
    }

    public static long priceScaledMaxValue()
    {
        return 9223372036854775807L;
    }

    public NewOrderCommandEncoder priceScaled(final long value)
    {
        buffer.putLong(offset + 19, value, BYTE_ORDER);
        return this;
    }


    public static int qtyScaledId()
    {
        return 8;
    }

    public static int qtyScaledSinceVersion()
    {
        return 0;
    }

    public static int qtyScaledEncodingOffset()
    {
        return 27;
    }

    public static int qtyScaledEncodingLength()
    {
        return 8;
    }

    public static String qtyScaledMetaAttribute(final MetaAttribute metaAttribute)
    {
        if (MetaAttribute.PRESENCE == metaAttribute)
        {
            return "required";
        }

        return "";
    }

    public static long qtyScaledNullValue()
    {
        return -9223372036854775808L;
    }

    public static long qtyScaledMinValue()
    {
        return -9223372036854775807L;
    }

    public static long qtyScaledMaxValue()
    {
        return 9223372036854775807L;
    }

    public NewOrderCommandEncoder qtyScaled(final long value)
    {
        buffer.putLong(offset + 27, value, BYTE_ORDER);
        return this;
    }


    public static int strategyIdId()
    {
        return 9;
    }

    public static int strategyIdSinceVersion()
    {
        return 0;
    }

    public static int strategyIdEncodingOffset()
    {
        return 35;
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

    public NewOrderCommandEncoder strategyId(final short value)
    {
        buffer.putShort(offset + 35, value, BYTE_ORDER);
        return this;
    }


    public static int parentOrderIdId()
    {
        return 10;
    }

    public static int parentOrderIdSinceVersion()
    {
        return 2;
    }

    public static int parentOrderIdEncodingOffset()
    {
        return 37;
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

    public NewOrderCommandEncoder parentOrderId(final long value)
    {
        buffer.putLong(offset + 37, value, BYTE_ORDER);
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

        final NewOrderCommandDecoder decoder = new NewOrderCommandDecoder();
        decoder.wrap(buffer, offset, BLOCK_LENGTH, SCHEMA_VERSION);

        return decoder.appendTo(builder);
    }
}
