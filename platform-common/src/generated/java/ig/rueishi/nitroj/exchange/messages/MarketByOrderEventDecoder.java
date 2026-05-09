/* Generated SBE (Simple Binary Encoding) message codec. */
package ig.rueishi.nitroj.exchange.messages;

import org.agrona.MutableDirectBuffer;
import org.agrona.DirectBuffer;


/**
 * Normalized FIX L3 order-level market data entry
 */
@SuppressWarnings("all")
public final class MarketByOrderEventDecoder
{
    public static final int BLOCK_LENGTH = 46;
    public static final int TEMPLATE_ID = 5;
    public static final int SCHEMA_ID = 1;
    public static final int SCHEMA_VERSION = 3;
    public static final String SEMANTIC_VERSION = "5.3";
    public static final java.nio.ByteOrder BYTE_ORDER = java.nio.ByteOrder.LITTLE_ENDIAN;

    private final MarketByOrderEventDecoder parentMessage = this;
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

    public MarketByOrderEventDecoder wrap(
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

    public MarketByOrderEventDecoder wrapAndApplyHeader(
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

    public MarketByOrderEventDecoder sbeRewind()
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

    public byte sideRaw()
    {
        return buffer.getByte(offset + 8);
    }

    public Side side()
    {
        return Side.get(buffer.getByte(offset + 8));
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

    public byte updateActionRaw()
    {
        return buffer.getByte(offset + 9);
    }

    public UpdateAction updateAction()
    {
        return UpdateAction.get(buffer.getByte(offset + 9));
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

    public long priceScaled()
    {
        return buffer.getLong(offset + 10, BYTE_ORDER);
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

    public long sizeScaled()
    {
        return buffer.getLong(offset + 18, BYTE_ORDER);
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

    public long ingressTimestampNanos()
    {
        return buffer.getLong(offset + 26, BYTE_ORDER);
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

    public long exchangeTimestampNanos()
    {
        return buffer.getLong(offset + 34, BYTE_ORDER);
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

    public int fixSeqNum()
    {
        return buffer.getInt(offset + 42, BYTE_ORDER);
    }


    public static int venueOrderIdId()
    {
        return 10;
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

        final MarketByOrderEventDecoder decoder = new MarketByOrderEventDecoder();
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
        builder.append("[MarketByOrderEvent](sbeTemplateId=");
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
        builder.append("side=");
        builder.append(this.side());
        builder.append('|');
        builder.append("updateAction=");
        builder.append(this.updateAction());
        builder.append('|');
        builder.append("priceScaled=");
        builder.append(this.priceScaled());
        builder.append('|');
        builder.append("sizeScaled=");
        builder.append(this.sizeScaled());
        builder.append('|');
        builder.append("ingressTimestampNanos=");
        builder.append(this.ingressTimestampNanos());
        builder.append('|');
        builder.append("exchangeTimestampNanos=");
        builder.append(this.exchangeTimestampNanos());
        builder.append('|');
        builder.append("fixSeqNum=");
        builder.append(this.fixSeqNum());
        builder.append('|');
        builder.append("venueOrderId=");
        builder.append(skipVenueOrderId()).append(" bytes of raw data");

        limit(originalLimit);

        return builder;
    }
    
    public MarketByOrderEventDecoder sbeSkip()
    {
        sbeRewind();
        skipVenueOrderId();

        return this;
    }
}
