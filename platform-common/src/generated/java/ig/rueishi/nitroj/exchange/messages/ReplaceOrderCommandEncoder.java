/* Generated SBE (Simple Binary Encoding) message codec. */
package ig.rueishi.nitroj.exchange.messages;

import org.agrona.MutableDirectBuffer;
import org.agrona.DirectBuffer;


/**
 * Replace via cancel+new; gateway synthesizes the two FIX messages
 */
@SuppressWarnings("all")
public final class ReplaceOrderCommandEncoder
{
    public static final int BLOCK_LENGTH = 49;
    public static final int TEMPLATE_ID = 34;
    public static final int SCHEMA_ID = 1;
    public static final int SCHEMA_VERSION = 3;
    public static final String SEMANTIC_VERSION = "5.3";
    public static final java.nio.ByteOrder BYTE_ORDER = java.nio.ByteOrder.LITTLE_ENDIAN;

    private final ReplaceOrderCommandEncoder parentMessage = this;
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

    public ReplaceOrderCommandEncoder wrap(final MutableDirectBuffer buffer, final int offset)
    {
        if (buffer != this.buffer)
        {
            this.buffer = buffer;
        }
        this.offset = offset;
        limit(offset + BLOCK_LENGTH);

        return this;
    }

    public ReplaceOrderCommandEncoder wrapAndApplyHeader(
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

    public static int origClOrdIdId()
    {
        return 1;
    }

    public static int origClOrdIdSinceVersion()
    {
        return 0;
    }

    public static int origClOrdIdEncodingOffset()
    {
        return 0;
    }

    public static int origClOrdIdEncodingLength()
    {
        return 8;
    }

    public static String origClOrdIdMetaAttribute(final MetaAttribute metaAttribute)
    {
        if (MetaAttribute.PRESENCE == metaAttribute)
        {
            return "required";
        }

        return "";
    }

    public static long origClOrdIdNullValue()
    {
        return -9223372036854775808L;
    }

    public static long origClOrdIdMinValue()
    {
        return -9223372036854775807L;
    }

    public static long origClOrdIdMaxValue()
    {
        return 9223372036854775807L;
    }

    public ReplaceOrderCommandEncoder origClOrdId(final long value)
    {
        buffer.putLong(offset + 0, value, BYTE_ORDER);
        return this;
    }


    public static int newClOrdIdId()
    {
        return 2;
    }

    public static int newClOrdIdSinceVersion()
    {
        return 0;
    }

    public static int newClOrdIdEncodingOffset()
    {
        return 8;
    }

    public static int newClOrdIdEncodingLength()
    {
        return 8;
    }

    public static String newClOrdIdMetaAttribute(final MetaAttribute metaAttribute)
    {
        if (MetaAttribute.PRESENCE == metaAttribute)
        {
            return "required";
        }

        return "";
    }

    public static long newClOrdIdNullValue()
    {
        return -9223372036854775808L;
    }

    public static long newClOrdIdMinValue()
    {
        return -9223372036854775807L;
    }

    public static long newClOrdIdMaxValue()
    {
        return 9223372036854775807L;
    }

    public ReplaceOrderCommandEncoder newClOrdId(final long value)
    {
        buffer.putLong(offset + 8, value, BYTE_ORDER);
        return this;
    }


    public static int venueIdId()
    {
        return 3;
    }

    public static int venueIdSinceVersion()
    {
        return 0;
    }

    public static int venueIdEncodingOffset()
    {
        return 16;
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

    public ReplaceOrderCommandEncoder venueId(final int value)
    {
        buffer.putInt(offset + 16, value, BYTE_ORDER);
        return this;
    }


    public static int instrumentIdId()
    {
        return 4;
    }

    public static int instrumentIdSinceVersion()
    {
        return 0;
    }

    public static int instrumentIdEncodingOffset()
    {
        return 20;
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

    public ReplaceOrderCommandEncoder instrumentId(final int value)
    {
        buffer.putInt(offset + 20, value, BYTE_ORDER);
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
        return 24;
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

    public ReplaceOrderCommandEncoder side(final Side value)
    {
        buffer.putByte(offset + 24, value.value());
        return this;
    }

    public static int newPriceScaledId()
    {
        return 6;
    }

    public static int newPriceScaledSinceVersion()
    {
        return 0;
    }

    public static int newPriceScaledEncodingOffset()
    {
        return 25;
    }

    public static int newPriceScaledEncodingLength()
    {
        return 8;
    }

    public static String newPriceScaledMetaAttribute(final MetaAttribute metaAttribute)
    {
        if (MetaAttribute.PRESENCE == metaAttribute)
        {
            return "required";
        }

        return "";
    }

    public static long newPriceScaledNullValue()
    {
        return -9223372036854775808L;
    }

    public static long newPriceScaledMinValue()
    {
        return -9223372036854775807L;
    }

    public static long newPriceScaledMaxValue()
    {
        return 9223372036854775807L;
    }

    public ReplaceOrderCommandEncoder newPriceScaled(final long value)
    {
        buffer.putLong(offset + 25, value, BYTE_ORDER);
        return this;
    }


    public static int newQtyScaledId()
    {
        return 7;
    }

    public static int newQtyScaledSinceVersion()
    {
        return 0;
    }

    public static int newQtyScaledEncodingOffset()
    {
        return 33;
    }

    public static int newQtyScaledEncodingLength()
    {
        return 8;
    }

    public static String newQtyScaledMetaAttribute(final MetaAttribute metaAttribute)
    {
        if (MetaAttribute.PRESENCE == metaAttribute)
        {
            return "required";
        }

        return "";
    }

    public static long newQtyScaledNullValue()
    {
        return -9223372036854775808L;
    }

    public static long newQtyScaledMinValue()
    {
        return -9223372036854775807L;
    }

    public static long newQtyScaledMaxValue()
    {
        return 9223372036854775807L;
    }

    public ReplaceOrderCommandEncoder newQtyScaled(final long value)
    {
        buffer.putLong(offset + 33, value, BYTE_ORDER);
        return this;
    }


    public static int origQtyScaledId()
    {
        return 8;
    }

    public static int origQtyScaledSinceVersion()
    {
        return 0;
    }

    public static int origQtyScaledEncodingOffset()
    {
        return 41;
    }

    public static int origQtyScaledEncodingLength()
    {
        return 8;
    }

    public static String origQtyScaledMetaAttribute(final MetaAttribute metaAttribute)
    {
        if (MetaAttribute.PRESENCE == metaAttribute)
        {
            return "required";
        }

        return "";
    }

    public static long origQtyScaledNullValue()
    {
        return -9223372036854775808L;
    }

    public static long origQtyScaledMinValue()
    {
        return -9223372036854775807L;
    }

    public static long origQtyScaledMaxValue()
    {
        return 9223372036854775807L;
    }

    public ReplaceOrderCommandEncoder origQtyScaled(final long value)
    {
        buffer.putLong(offset + 41, value, BYTE_ORDER);
        return this;
    }


    public static int venueOrderIdId()
    {
        return 9;
    }

    public static String venueOrderIdMetaAttribute(final MetaAttribute metaAttribute)
    {
        if (MetaAttribute.PRESENCE == metaAttribute)
        {
            return "required";
        }

        return "";
    }

    public static int venueOrderIdHeaderLength()
    {
        return 2;
    }

    public ReplaceOrderCommandEncoder putVenueOrderId(final DirectBuffer src, final int srcOffset, final int length)
    {
        if (length > 65534)
        {
            throw new IllegalStateException("length > maxValue for type: " + length);
        }

        final int headerLength = 2;
        final int limit = parentMessage.limit();
        parentMessage.limit(limit + headerLength + length);
        buffer.putShort(limit, (short)length, BYTE_ORDER);
        buffer.putBytes(limit + headerLength, src, srcOffset, length);

        return this;
    }

    public ReplaceOrderCommandEncoder putVenueOrderId(final byte[] src, final int srcOffset, final int length)
    {
        if (length > 65534)
        {
            throw new IllegalStateException("length > maxValue for type: " + length);
        }

        final int headerLength = 2;
        final int limit = parentMessage.limit();
        parentMessage.limit(limit + headerLength + length);
        buffer.putShort(limit, (short)length, BYTE_ORDER);
        buffer.putBytes(limit + headerLength, src, srcOffset, length);

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

        final ReplaceOrderCommandDecoder decoder = new ReplaceOrderCommandDecoder();
        decoder.wrap(buffer, offset, BLOCK_LENGTH, SCHEMA_VERSION);

        return decoder.appendTo(builder);
    }
}
