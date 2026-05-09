/* Generated SBE (Simple Binary Encoding) message codec. */
package ig.rueishi.nitroj.exchange.messages;

import org.agrona.MutableDirectBuffer;
import org.agrona.DirectBuffer;


/**
 * Normalized FIX ExecutionReport
 */
@SuppressWarnings("all")
public final class ExecutionEventDecoder
{
    public static final int BLOCK_LENGTH = 75;
    public static final int TEMPLATE_ID = 2;
    public static final int SCHEMA_ID = 1;
    public static final int SCHEMA_VERSION = 3;
    public static final String SEMANTIC_VERSION = "5.3";
    public static final java.nio.ByteOrder BYTE_ORDER = java.nio.ByteOrder.LITTLE_ENDIAN;

    private final ExecutionEventDecoder parentMessage = this;
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

    public ExecutionEventDecoder wrap(
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

    public ExecutionEventDecoder wrapAndApplyHeader(
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

    public ExecutionEventDecoder sbeRewind()
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


    public static int execTypeId()
    {
        return 4;
    }

    public static int execTypeSinceVersion()
    {
        return 0;
    }

    public static int execTypeEncodingOffset()
    {
        return 16;
    }

    public static int execTypeEncodingLength()
    {
        return 1;
    }

    public static String execTypeMetaAttribute(final MetaAttribute metaAttribute)
    {
        if (MetaAttribute.PRESENCE == metaAttribute)
        {
            return "required";
        }

        return "";
    }

    public byte execTypeRaw()
    {
        return buffer.getByte(offset + 16);
    }

    public ExecType execType()
    {
        return ExecType.get(buffer.getByte(offset + 16));
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
        return 17;
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
        return buffer.getByte(offset + 17);
    }

    public Side side()
    {
        return Side.get(buffer.getByte(offset + 17));
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
        return 18;
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
        return buffer.getLong(offset + 18, BYTE_ORDER);
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
        return 26;
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
        return buffer.getLong(offset + 26, BYTE_ORDER);
    }


    public static int cumQtyScaledId()
    {
        return 8;
    }

    public static int cumQtyScaledSinceVersion()
    {
        return 0;
    }

    public static int cumQtyScaledEncodingOffset()
    {
        return 34;
    }

    public static int cumQtyScaledEncodingLength()
    {
        return 8;
    }

    public static String cumQtyScaledMetaAttribute(final MetaAttribute metaAttribute)
    {
        if (MetaAttribute.PRESENCE == metaAttribute)
        {
            return "required";
        }

        return "";
    }

    public static long cumQtyScaledNullValue()
    {
        return -9223372036854775808L;
    }

    public static long cumQtyScaledMinValue()
    {
        return -9223372036854775807L;
    }

    public static long cumQtyScaledMaxValue()
    {
        return 9223372036854775807L;
    }

    public long cumQtyScaled()
    {
        return buffer.getLong(offset + 34, BYTE_ORDER);
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
        return 42;
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
        return buffer.getLong(offset + 42, BYTE_ORDER);
    }


    public static int rejectCodeId()
    {
        return 10;
    }

    public static int rejectCodeSinceVersion()
    {
        return 0;
    }

    public static int rejectCodeEncodingOffset()
    {
        return 50;
    }

    public static int rejectCodeEncodingLength()
    {
        return 4;
    }

    public static String rejectCodeMetaAttribute(final MetaAttribute metaAttribute)
    {
        if (MetaAttribute.PRESENCE == metaAttribute)
        {
            return "required";
        }

        return "";
    }

    public static int rejectCodeNullValue()
    {
        return -2147483648;
    }

    public static int rejectCodeMinValue()
    {
        return -2147483647;
    }

    public static int rejectCodeMaxValue()
    {
        return 2147483647;
    }

    public int rejectCode()
    {
        return buffer.getInt(offset + 50, BYTE_ORDER);
    }


    public static int ingressTimestampNanosId()
    {
        return 11;
    }

    public static int ingressTimestampNanosSinceVersion()
    {
        return 0;
    }

    public static int ingressTimestampNanosEncodingOffset()
    {
        return 54;
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
        return buffer.getLong(offset + 54, BYTE_ORDER);
    }


    public static int exchangeTimestampNanosId()
    {
        return 12;
    }

    public static int exchangeTimestampNanosSinceVersion()
    {
        return 0;
    }

    public static int exchangeTimestampNanosEncodingOffset()
    {
        return 62;
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
        return buffer.getLong(offset + 62, BYTE_ORDER);
    }


    public static int fixSeqNumId()
    {
        return 13;
    }

    public static int fixSeqNumSinceVersion()
    {
        return 0;
    }

    public static int fixSeqNumEncodingOffset()
    {
        return 70;
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
        return buffer.getInt(offset + 70, BYTE_ORDER);
    }


    public static int isFinalId()
    {
        return 14;
    }

    public static int isFinalSinceVersion()
    {
        return 0;
    }

    public static int isFinalEncodingOffset()
    {
        return 74;
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
        return buffer.getByte(offset + 74);
    }

    public BooleanType isFinal()
    {
        return BooleanType.get(buffer.getByte(offset + 74));
    }


    public static int venueOrderIdId()
    {
        return 15;
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

    public static int execIdId()
    {
        return 16;
    }

    public static int execIdSinceVersion()
    {
        return 0;
    }

    public static String execIdMetaAttribute(final MetaAttribute metaAttribute)
    {
        if (MetaAttribute.PRESENCE == metaAttribute)
        {
            return "required";
        }

        return "";
    }

    public static int execIdHeaderLength()
    {
        return 2;
    }

    public int execIdLength()
    {
        final int limit = parentMessage.limit();
        return (buffer.getShort(limit, BYTE_ORDER) & 0xFFFF);
    }

    public int skipExecId()
    {
        final int headerLength = 2;
        final int limit = parentMessage.limit();
        final int dataLength = (buffer.getShort(limit, BYTE_ORDER) & 0xFFFF);
        final int dataOffset = limit + headerLength;
        parentMessage.limit(dataOffset + dataLength);

        return dataLength;
    }

    public int getExecId(final MutableDirectBuffer dst, final int dstOffset, final int length)
    {
        final int headerLength = 2;
        final int limit = parentMessage.limit();
        final int dataLength = (buffer.getShort(limit, BYTE_ORDER) & 0xFFFF);
        final int bytesCopied = Math.min(length, dataLength);
        parentMessage.limit(limit + headerLength + dataLength);
        buffer.getBytes(limit + headerLength, dst, dstOffset, bytesCopied);

        return bytesCopied;
    }

    public int getExecId(final byte[] dst, final int dstOffset, final int length)
    {
        final int headerLength = 2;
        final int limit = parentMessage.limit();
        final int dataLength = (buffer.getShort(limit, BYTE_ORDER) & 0xFFFF);
        final int bytesCopied = Math.min(length, dataLength);
        parentMessage.limit(limit + headerLength + dataLength);
        buffer.getBytes(limit + headerLength, dst, dstOffset, bytesCopied);

        return bytesCopied;
    }

    public void wrapExecId(final DirectBuffer wrapBuffer)
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

        final ExecutionEventDecoder decoder = new ExecutionEventDecoder();
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
        builder.append("[ExecutionEvent](sbeTemplateId=");
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
        builder.append("execType=");
        builder.append(this.execType());
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
        builder.append("cumQtyScaled=");
        builder.append(this.cumQtyScaled());
        builder.append('|');
        builder.append("leavesQtyScaled=");
        builder.append(this.leavesQtyScaled());
        builder.append('|');
        builder.append("rejectCode=");
        builder.append(this.rejectCode());
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
        builder.append("isFinal=");
        builder.append(this.isFinal());
        builder.append('|');
        builder.append("venueOrderId=");
        builder.append(skipVenueOrderId()).append(" bytes of raw data");
        builder.append('|');
        builder.append("execId=");
        builder.append(skipExecId()).append(" bytes of raw data");

        limit(originalLimit);

        return builder;
    }
    
    public ExecutionEventDecoder sbeSkip()
    {
        sbeRewind();
        skipVenueOrderId();
        skipExecId();

        return this;
    }
}
