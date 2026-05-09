/* Generated SBE (Simple Binary Encoding) message codec. */
package ig.rueishi.nitroj.exchange.messages;

import org.agrona.MutableDirectBuffer;
import org.agrona.DirectBuffer;


/**
 * Cancel request to gateway
 */
@SuppressWarnings("all")
public final class CancelOrderCommandDecoder
{
    public static final int BLOCK_LENGTH = 33;
    public static final int TEMPLATE_ID = 11;
    public static final int SCHEMA_ID = 1;
    public static final int SCHEMA_VERSION = 3;
    public static final String SEMANTIC_VERSION = "5.3";
    public static final java.nio.ByteOrder BYTE_ORDER = java.nio.ByteOrder.LITTLE_ENDIAN;

    private final CancelOrderCommandDecoder parentMessage = this;
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

    public CancelOrderCommandDecoder wrap(
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

    public CancelOrderCommandDecoder wrapAndApplyHeader(
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

    public CancelOrderCommandDecoder sbeRewind()
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

    public static int cancelClOrdIdId()
    {
        return 1;
    }

    public static int cancelClOrdIdSinceVersion()
    {
        return 0;
    }

    public static int cancelClOrdIdEncodingOffset()
    {
        return 0;
    }

    public static int cancelClOrdIdEncodingLength()
    {
        return 8;
    }

    public static String cancelClOrdIdMetaAttribute(final MetaAttribute metaAttribute)
    {
        if (MetaAttribute.PRESENCE == metaAttribute)
        {
            return "required";
        }

        return "";
    }

    public static long cancelClOrdIdNullValue()
    {
        return -9223372036854775808L;
    }

    public static long cancelClOrdIdMinValue()
    {
        return -9223372036854775807L;
    }

    public static long cancelClOrdIdMaxValue()
    {
        return 9223372036854775807L;
    }

    public long cancelClOrdId()
    {
        return buffer.getLong(offset + 0, BYTE_ORDER);
    }


    public static int origClOrdIdId()
    {
        return 2;
    }

    public static int origClOrdIdSinceVersion()
    {
        return 0;
    }

    public static int origClOrdIdEncodingOffset()
    {
        return 8;
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

    public long origClOrdId()
    {
        return buffer.getLong(offset + 8, BYTE_ORDER);
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

    public int venueId()
    {
        return buffer.getInt(offset + 16, BYTE_ORDER);
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

    public int instrumentId()
    {
        return buffer.getInt(offset + 20, BYTE_ORDER);
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

    public byte sideRaw()
    {
        return buffer.getByte(offset + 24);
    }

    public Side side()
    {
        return Side.get(buffer.getByte(offset + 24));
    }


    public static int originalQtyScaledId()
    {
        return 6;
    }

    public static int originalQtyScaledSinceVersion()
    {
        return 0;
    }

    public static int originalQtyScaledEncodingOffset()
    {
        return 25;
    }

    public static int originalQtyScaledEncodingLength()
    {
        return 8;
    }

    public static String originalQtyScaledMetaAttribute(final MetaAttribute metaAttribute)
    {
        if (MetaAttribute.PRESENCE == metaAttribute)
        {
            return "required";
        }

        return "";
    }

    public static long originalQtyScaledNullValue()
    {
        return -9223372036854775808L;
    }

    public static long originalQtyScaledMinValue()
    {
        return -9223372036854775807L;
    }

    public static long originalQtyScaledMaxValue()
    {
        return 9223372036854775807L;
    }

    public long originalQtyScaled()
    {
        return buffer.getLong(offset + 25, BYTE_ORDER);
    }


    public static int venueOrderIdId()
    {
        return 7;
    }

    public static int venueOrderIdSinceVersion()
    {
        return 0;
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

    public int venueOrderIdLength()
    {
        final int limit = parentMessage.limit();
        return (buffer.getShort(limit, BYTE_ORDER) & 0xFFFF);
    }

    public int skipVenueOrderId()
    {
        final int headerLength = 2;
        final int limit = parentMessage.limit();
        final int dataLength = (buffer.getShort(limit, BYTE_ORDER) & 0xFFFF);
        final int dataOffset = limit + headerLength;
        parentMessage.limit(dataOffset + dataLength);

        return dataLength;
    }

    public int getVenueOrderId(final MutableDirectBuffer dst, final int dstOffset, final int length)
    {
        final int headerLength = 2;
        final int limit = parentMessage.limit();
        final int dataLength = (buffer.getShort(limit, BYTE_ORDER) & 0xFFFF);
        final int bytesCopied = Math.min(length, dataLength);
        parentMessage.limit(limit + headerLength + dataLength);
        buffer.getBytes(limit + headerLength, dst, dstOffset, bytesCopied);

        return bytesCopied;
    }

    public int getVenueOrderId(final byte[] dst, final int dstOffset, final int length)
    {
        final int headerLength = 2;
        final int limit = parentMessage.limit();
        final int dataLength = (buffer.getShort(limit, BYTE_ORDER) & 0xFFFF);
        final int bytesCopied = Math.min(length, dataLength);
        parentMessage.limit(limit + headerLength + dataLength);
        buffer.getBytes(limit + headerLength, dst, dstOffset, bytesCopied);

        return bytesCopied;
    }

    public void wrapVenueOrderId(final DirectBuffer wrapBuffer)
    {
        final int headerLength = 2;
        final int limit = parentMessage.limit();
        final int dataLength = (buffer.getShort(limit, BYTE_ORDER) & 0xFFFF);
        parentMessage.limit(limit + headerLength + dataLength);
        wrapBuffer.wrap(buffer, limit + headerLength, dataLength);
    }

    public String toString()
    {
        if (null == buffer)
        {
            return "";
        }

        final CancelOrderCommandDecoder decoder = new CancelOrderCommandDecoder();
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
        builder.append("[CancelOrderCommand](sbeTemplateId=");
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
        builder.append("cancelClOrdId=");
        builder.append(this.cancelClOrdId());
        builder.append('|');
        builder.append("origClOrdId=");
        builder.append(this.origClOrdId());
        builder.append('|');
        builder.append("venueId=");
        builder.append(this.venueId());
        builder.append('|');
        builder.append("instrumentId=");
        builder.append(this.instrumentId());
        builder.append('|');
        builder.append("side=");
        builder.append(this.side());
        builder.append('|');
        builder.append("originalQtyScaled=");
        builder.append(this.originalQtyScaled());
        builder.append('|');
        builder.append("venueOrderId=");
        builder.append(skipVenueOrderId()).append(" bytes of raw data");

        limit(originalLimit);

        return builder;
    }
    
    public CancelOrderCommandDecoder sbeSkip()
    {
        sbeRewind();
        skipVenueOrderId();

        return this;
    }
}
