/* Generated SBE (Simple Binary Encoding) message codec. */
package ig.rueishi.nitroj.exchange.messages;

import org.agrona.MutableDirectBuffer;


/**
 * Deterministic terminal parent-order update
 */
@SuppressWarnings("all")
public final class ParentOrderTerminalEncoder
{
    public static final int BLOCK_LENGTH = 47;
    public static final int TEMPLATE_ID = 42;
    public static final int SCHEMA_ID = 1;
    public static final int SCHEMA_VERSION = 3;
    public static final String SEMANTIC_VERSION = "5.3";
    public static final java.nio.ByteOrder BYTE_ORDER = java.nio.ByteOrder.LITTLE_ENDIAN;

    private final ParentOrderTerminalEncoder parentMessage = this;
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

    public ParentOrderTerminalEncoder wrap(final MutableDirectBuffer buffer, final int offset)
    {
        if (buffer != this.buffer)
        {
            this.buffer = buffer;
        }
        this.offset = offset;
        limit(offset + BLOCK_LENGTH);

        return this;
    }

    public ParentOrderTerminalEncoder wrapAndApplyHeader(
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

    public static int parentOrderIdId()
    {
        return 1;
    }

    public static int parentOrderIdSinceVersion()
    {
        return 0;
    }

    public static int parentOrderIdEncodingOffset()
    {
        return 0;
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

    public ParentOrderTerminalEncoder parentOrderId(final long value)
    {
        buffer.putLong(offset + 0, value, BYTE_ORDER);
        return this;
    }


    public static int strategyIdId()
    {
        return 2;
    }

    public static int strategyIdSinceVersion()
    {
        return 0;
    }

    public static int strategyIdEncodingOffset()
    {
        return 8;
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

    public ParentOrderTerminalEncoder strategyId(final short value)
    {
        buffer.putShort(offset + 8, value, BYTE_ORDER);
        return this;
    }


    public static int executionStrategyIdId()
    {
        return 3;
    }

    public static int executionStrategyIdSinceVersion()
    {
        return 0;
    }

    public static int executionStrategyIdEncodingOffset()
    {
        return 10;
    }

    public static int executionStrategyIdEncodingLength()
    {
        return 4;
    }

    public static String executionStrategyIdMetaAttribute(final MetaAttribute metaAttribute)
    {
        if (MetaAttribute.PRESENCE == metaAttribute)
        {
            return "required";
        }

        return "";
    }

    public static int executionStrategyIdNullValue()
    {
        return -2147483648;
    }

    public static int executionStrategyIdMinValue()
    {
        return -2147483647;
    }

    public static int executionStrategyIdMaxValue()
    {
        return 2147483647;
    }

    public ParentOrderTerminalEncoder executionStrategyId(final int value)
    {
        buffer.putInt(offset + 10, value, BYTE_ORDER);
        return this;
    }


    public static int finalCumFillQtyScaledId()
    {
        return 4;
    }

    public static int finalCumFillQtyScaledSinceVersion()
    {
        return 0;
    }

    public static int finalCumFillQtyScaledEncodingOffset()
    {
        return 14;
    }

    public static int finalCumFillQtyScaledEncodingLength()
    {
        return 8;
    }

    public static String finalCumFillQtyScaledMetaAttribute(final MetaAttribute metaAttribute)
    {
        if (MetaAttribute.PRESENCE == metaAttribute)
        {
            return "required";
        }

        return "";
    }

    public static long finalCumFillQtyScaledNullValue()
    {
        return -9223372036854775808L;
    }

    public static long finalCumFillQtyScaledMinValue()
    {
        return -9223372036854775807L;
    }

    public static long finalCumFillQtyScaledMaxValue()
    {
        return 9223372036854775807L;
    }

    public ParentOrderTerminalEncoder finalCumFillQtyScaled(final long value)
    {
        buffer.putLong(offset + 14, value, BYTE_ORDER);
        return this;
    }


    public static int avgFillPriceScaledId()
    {
        return 5;
    }

    public static int avgFillPriceScaledSinceVersion()
    {
        return 0;
    }

    public static int avgFillPriceScaledEncodingOffset()
    {
        return 22;
    }

    public static int avgFillPriceScaledEncodingLength()
    {
        return 8;
    }

    public static String avgFillPriceScaledMetaAttribute(final MetaAttribute metaAttribute)
    {
        if (MetaAttribute.PRESENCE == metaAttribute)
        {
            return "required";
        }

        return "";
    }

    public static long avgFillPriceScaledNullValue()
    {
        return -9223372036854775808L;
    }

    public static long avgFillPriceScaledMinValue()
    {
        return -9223372036854775807L;
    }

    public static long avgFillPriceScaledMaxValue()
    {
        return 9223372036854775807L;
    }

    public ParentOrderTerminalEncoder avgFillPriceScaled(final long value)
    {
        buffer.putLong(offset + 22, value, BYTE_ORDER);
        return this;
    }


    public static int terminalReasonId()
    {
        return 6;
    }

    public static int terminalReasonSinceVersion()
    {
        return 0;
    }

    public static int terminalReasonEncodingOffset()
    {
        return 30;
    }

    public static int terminalReasonEncodingLength()
    {
        return 1;
    }

    public static String terminalReasonMetaAttribute(final MetaAttribute metaAttribute)
    {
        if (MetaAttribute.PRESENCE == metaAttribute)
        {
            return "required";
        }

        return "";
    }

    public ParentOrderTerminalEncoder terminalReason(final ParentTerminalReason value)
    {
        buffer.putByte(offset + 30, value.value());
        return this;
    }

    public static int lastChildClOrdIdId()
    {
        return 7;
    }

    public static int lastChildClOrdIdSinceVersion()
    {
        return 0;
    }

    public static int lastChildClOrdIdEncodingOffset()
    {
        return 31;
    }

    public static int lastChildClOrdIdEncodingLength()
    {
        return 8;
    }

    public static String lastChildClOrdIdMetaAttribute(final MetaAttribute metaAttribute)
    {
        if (MetaAttribute.PRESENCE == metaAttribute)
        {
            return "required";
        }

        return "";
    }

    public static long lastChildClOrdIdNullValue()
    {
        return -9223372036854775808L;
    }

    public static long lastChildClOrdIdMinValue()
    {
        return -9223372036854775807L;
    }

    public static long lastChildClOrdIdMaxValue()
    {
        return 9223372036854775807L;
    }

    public ParentOrderTerminalEncoder lastChildClOrdId(final long value)
    {
        buffer.putLong(offset + 31, value, BYTE_ORDER);
        return this;
    }


    public static int eventClusterTimeId()
    {
        return 8;
    }

    public static int eventClusterTimeSinceVersion()
    {
        return 0;
    }

    public static int eventClusterTimeEncodingOffset()
    {
        return 39;
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

    public ParentOrderTerminalEncoder eventClusterTime(final long value)
    {
        buffer.putLong(offset + 39, value, BYTE_ORDER);
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

        final ParentOrderTerminalDecoder decoder = new ParentOrderTerminalDecoder();
        decoder.wrap(buffer, offset, BLOCK_LENGTH, SCHEMA_VERSION);

        return decoder.appendTo(builder);
    }
}
