/* Generated SBE (Simple Binary Encoding) message codec. */
package ig.rueishi.nitroj.exchange.messages;

import org.agrona.MutableDirectBuffer;
import org.agrona.DirectBuffer;


/**
 * Normalized FIX ExecutionReport
 */
@SuppressWarnings("all")
public final class ExecutionEventEncoder
{
    public static final int BLOCK_LENGTH = 75;
    public static final int TEMPLATE_ID = 2;
    public static final int SCHEMA_ID = 1;
    public static final int SCHEMA_VERSION = 3;
    public static final String SEMANTIC_VERSION = "5.3";
    public static final java.nio.ByteOrder BYTE_ORDER = java.nio.ByteOrder.LITTLE_ENDIAN;

    private final ExecutionEventEncoder parentMessage = this;
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

    public ExecutionEventEncoder wrap(final MutableDirectBuffer buffer, final int offset)
    {
        if (buffer != this.buffer)
        {
            this.buffer = buffer;
        }
        this.offset = offset;
        limit(offset + BLOCK_LENGTH);

        return this;
    }

    public ExecutionEventEncoder wrapAndApplyHeader(
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

    public ExecutionEventEncoder clOrdId(final long value)
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

    public ExecutionEventEncoder venueId(final int value)
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

    public ExecutionEventEncoder instrumentId(final int value)
    {
        buffer.putInt(offset + 12, value, BYTE_ORDER);
        return this;
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

    public ExecutionEventEncoder execType(final ExecType value)
    {
        buffer.putByte(offset + 16, value.value());
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

    public ExecutionEventEncoder side(final Side value)
    {
        buffer.putByte(offset + 17, value.value());
        return this;
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

    public ExecutionEventEncoder fillPriceScaled(final long value)
    {
        buffer.putLong(offset + 18, value, BYTE_ORDER);
        return this;
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

    public ExecutionEventEncoder fillQtyScaled(final long value)
    {
        buffer.putLong(offset + 26, value, BYTE_ORDER);
        return this;
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

    public ExecutionEventEncoder cumQtyScaled(final long value)
    {
        buffer.putLong(offset + 34, value, BYTE_ORDER);
        return this;
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

    public ExecutionEventEncoder leavesQtyScaled(final long value)
    {
        buffer.putLong(offset + 42, value, BYTE_ORDER);
        return this;
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

    public ExecutionEventEncoder rejectCode(final int value)
    {
        buffer.putInt(offset + 50, value, BYTE_ORDER);
        return this;
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

    public ExecutionEventEncoder ingressTimestampNanos(final long value)
    {
        buffer.putLong(offset + 54, value, BYTE_ORDER);
        return this;
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

    public ExecutionEventEncoder exchangeTimestampNanos(final long value)
    {
        buffer.putLong(offset + 62, value, BYTE_ORDER);
        return this;
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

    public ExecutionEventEncoder fixSeqNum(final int value)
    {
        buffer.putInt(offset + 70, value, BYTE_ORDER);
        return this;
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

    public ExecutionEventEncoder isFinal(final BooleanType value)
    {
        buffer.putByte(offset + 74, value.value());
        return this;
    }

    public static int venueOrderIdId()
    {
        return 15;
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

    public ExecutionEventEncoder putVenueOrderId(final DirectBuffer src, final int srcOffset, final int length)
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

    public ExecutionEventEncoder putVenueOrderId(final byte[] src, final int srcOffset, final int length)
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

    public static int execIdId()
    {
        return 16;
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

    public ExecutionEventEncoder putExecId(final DirectBuffer src, final int srcOffset, final int length)
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

    public ExecutionEventEncoder putExecId(final byte[] src, final int srcOffset, final int length)
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

        final ExecutionEventDecoder decoder = new ExecutionEventDecoder();
        decoder.wrap(buffer, offset, BLOCK_LENGTH, SCHEMA_VERSION);

        return decoder.appendTo(builder);
    }
}
