/* Generated SBE (Simple Binary Encoding) message codec. */
package ig.rueishi.nitroj.exchange.messages;

import org.agrona.MutableDirectBuffer;
import org.agrona.DirectBuffer;


/**
 * Normalized FIX L3 order-level market data entry
 */
@SuppressWarnings("all")
public final class MarketByOrderEventEncoder
{
    public static final int BLOCK_LENGTH = 46;
    public static final int TEMPLATE_ID = 5;
    public static final int SCHEMA_ID = 1;
    public static final int SCHEMA_VERSION = 3;
    public static final String SEMANTIC_VERSION = "5.3";
    public static final java.nio.ByteOrder BYTE_ORDER = java.nio.ByteOrder.LITTLE_ENDIAN;

    private final MarketByOrderEventEncoder parentMessage = this;
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

    public MarketByOrderEventEncoder wrap(final MutableDirectBuffer buffer, final int offset)
    {
        if (buffer != this.buffer)
        {
            this.buffer = buffer;
        }
        this.offset = offset;
        limit(offset + BLOCK_LENGTH);

        return this;
    }

    public MarketByOrderEventEncoder wrapAndApplyHeader(
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

    public MarketByOrderEventEncoder venueId(final int value)
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

    public MarketByOrderEventEncoder instrumentId(final int value)
    {
        buffer.putInt(offset + 4, value, BYTE_ORDER);
        return this;
    }


    public static int sideId()
    {
        return 3;
    }

    public static int sideSinceVersion()
    {
        return 0;
    }

    public static int sideEncodingOffset()
    {
        return 8;
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

    public MarketByOrderEventEncoder side(final Side value)
    {
        buffer.putByte(offset + 8, value.value());
        return this;
    }

    public static int updateActionId()
    {
        return 4;
    }

    public static int updateActionSinceVersion()
    {
        return 0;
    }

    public static int updateActionEncodingOffset()
    {
        return 9;
    }

    public static int updateActionEncodingLength()
    {
        return 1;
    }

    public static String updateActionMetaAttribute(final MetaAttribute metaAttribute)
    {
        if (MetaAttribute.PRESENCE == metaAttribute)
        {
            return "required";
        }

        return "";
    }

    public MarketByOrderEventEncoder updateAction(final UpdateAction value)
    {
        buffer.putByte(offset + 9, value.value());
        return this;
    }

    public static int priceScaledId()
    {
        return 5;
    }

    public static int priceScaledSinceVersion()
    {
        return 0;
    }

    public static int priceScaledEncodingOffset()
    {
        return 10;
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

    public MarketByOrderEventEncoder priceScaled(final long value)
    {
        buffer.putLong(offset + 10, value, BYTE_ORDER);
        return this;
    }


    public static int sizeScaledId()
    {
        return 6;
    }

    public static int sizeScaledSinceVersion()
    {
        return 0;
    }

    public static int sizeScaledEncodingOffset()
    {
        return 18;
    }

    public static int sizeScaledEncodingLength()
    {
        return 8;
    }

    public static String sizeScaledMetaAttribute(final MetaAttribute metaAttribute)
    {
        if (MetaAttribute.PRESENCE == metaAttribute)
        {
            return "required";
        }

        return "";
    }

    public static long sizeScaledNullValue()
    {
        return -9223372036854775808L;
    }

    public static long sizeScaledMinValue()
    {
        return -9223372036854775807L;
    }

    public static long sizeScaledMaxValue()
    {
        return 9223372036854775807L;
    }

    public MarketByOrderEventEncoder sizeScaled(final long value)
    {
        buffer.putLong(offset + 18, value, BYTE_ORDER);
        return this;
    }


    public static int ingressTimestampNanosId()
    {
        return 7;
    }

    public static int ingressTimestampNanosSinceVersion()
    {
        return 0;
    }

    public static int ingressTimestampNanosEncodingOffset()
    {
        return 26;
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

    public MarketByOrderEventEncoder ingressTimestampNanos(final long value)
    {
        buffer.putLong(offset + 26, value, BYTE_ORDER);
        return this;
    }


    public static int exchangeTimestampNanosId()
    {
        return 8;
    }

    public static int exchangeTimestampNanosSinceVersion()
    {
        return 0;
    }

    public static int exchangeTimestampNanosEncodingOffset()
    {
        return 34;
    }

    public static int exchangeTimestampNanosEncodingLength()
    {
        return 8;
    }

    public static String exchangeTimestampNanosMetaAttribute(final MetaAttribute metaAttribute)
    {
        if (MetaAttribute.PRESENCE == metaAttribute)
        {
            return "required";
        }

        return "";
    }

    public static long exchangeTimestampNanosNullValue()
    {
        return -9223372036854775808L;
    }

    public static long exchangeTimestampNanosMinValue()
    {
        return -9223372036854775807L;
    }

    public static long exchangeTimestampNanosMaxValue()
    {
        return 9223372036854775807L;
    }

    public MarketByOrderEventEncoder exchangeTimestampNanos(final long value)
    {
        buffer.putLong(offset + 34, value, BYTE_ORDER);
        return this;
    }


    public static int fixSeqNumId()
    {
        return 9;
    }

    public static int fixSeqNumSinceVersion()
    {
        return 0;
    }

    public static int fixSeqNumEncodingOffset()
    {
        return 42;
    }

    public static int fixSeqNumEncodingLength()
    {
        return 4;
    }

    public static String fixSeqNumMetaAttribute(final MetaAttribute metaAttribute)
    {
        if (MetaAttribute.PRESENCE == metaAttribute)
        {
            return "required";
        }

        return "";
    }

    public static int fixSeqNumNullValue()
    {
        return -2147483648;
    }

    public static int fixSeqNumMinValue()
    {
        return -2147483647;
    }

    public static int fixSeqNumMaxValue()
    {
        return 2147483647;
    }

    public MarketByOrderEventEncoder fixSeqNum(final int value)
    {
        buffer.putInt(offset + 42, value, BYTE_ORDER);
        return this;
    }


    public static int venueOrderIdId()
    {
        return 10;
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

    public MarketByOrderEventEncoder putVenueOrderId(final DirectBuffer src, final int srcOffset, final int length)
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

    public MarketByOrderEventEncoder putVenueOrderId(final byte[] src, final int srcOffset, final int length)
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

        final MarketByOrderEventDecoder decoder = new MarketByOrderEventDecoder();
        decoder.wrap(buffer, offset, BLOCK_LENGTH, SCHEMA_VERSION);

        return decoder.appendTo(builder);
    }
}
