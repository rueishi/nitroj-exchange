/* Generated SBE (Simple Binary Encoding) message codec. */
package ig.rueishi.nitroj.exchange.messages;

import org.agrona.MutableDirectBuffer;


/**
 * Deterministic non-terminal parent-order update
 */
@SuppressWarnings("all")
public final class ParentOrderUpdateEncoder
{
    public static final int BLOCK_LENGTH = 59;
    public static final int TEMPLATE_ID = 41;
    public static final int SCHEMA_ID = 1;
    public static final int SCHEMA_VERSION = 3;
    public static final String SEMANTIC_VERSION = "5.3";
    public static final java.nio.ByteOrder BYTE_ORDER = java.nio.ByteOrder.LITTLE_ENDIAN;

    private final ParentOrderUpdateEncoder parentMessage = this;
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

    public ParentOrderUpdateEncoder wrap(final MutableDirectBuffer buffer, final int offset)
    {
        if (buffer != this.buffer)
        {
            this.buffer = buffer;
        }
        this.offset = offset;
        limit(offset + BLOCK_LENGTH);

        return this;
    }

    public ParentOrderUpdateEncoder wrapAndApplyHeader(
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

    public ParentOrderUpdateEncoder parentOrderId(final long value)
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

    public ParentOrderUpdateEncoder strategyId(final short value)
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

    public ParentOrderUpdateEncoder executionStrategyId(final int value)
    {
        buffer.putInt(offset + 10, value, BYTE_ORDER);
        return this;
    }


    public static int cumFillQtyScaledId()
    {
        return 4;
    }

    public static int cumFillQtyScaledSinceVersion()
    {
        return 0;
    }

    public static int cumFillQtyScaledEncodingOffset()
    {
        return 14;
    }

    public static int cumFillQtyScaledEncodingLength()
    {
        return 8;
    }

    public static String cumFillQtyScaledMetaAttribute(final MetaAttribute metaAttribute)
    {
        if (MetaAttribute.PRESENCE == metaAttribute)
        {
            return "required";
        }

        return "";
    }

    public static long cumFillQtyScaledNullValue()
    {
        return -9223372036854775808L;
    }

    public static long cumFillQtyScaledMinValue()
    {
        return -9223372036854775807L;
    }

    public static long cumFillQtyScaledMaxValue()
    {
        return 9223372036854775807L;
    }

    public ParentOrderUpdateEncoder cumFillQtyScaled(final long value)
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

    public ParentOrderUpdateEncoder avgFillPriceScaled(final long value)
    {
        buffer.putLong(offset + 22, value, BYTE_ORDER);
        return this;
    }


    public static int leavesQtyScaledId()
    {
        return 6;
    }

    public static int leavesQtyScaledSinceVersion()
    {
        return 0;
    }

    public static int leavesQtyScaledEncodingOffset()
    {
        return 30;
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

    public ParentOrderUpdateEncoder leavesQtyScaled(final long value)
    {
        buffer.putLong(offset + 30, value, BYTE_ORDER);
        return this;
    }


    public static int workingChildCountId()
    {
        return 7;
    }

    public static int workingChildCountSinceVersion()
    {
        return 0;
    }

    public static int workingChildCountEncodingOffset()
    {
        return 38;
    }

    public static int workingChildCountEncodingLength()
    {
        return 4;
    }

    public static String workingChildCountMetaAttribute(final MetaAttribute metaAttribute)
    {
        if (MetaAttribute.PRESENCE == metaAttribute)
        {
            return "required";
        }

        return "";
    }

    public static int workingChildCountNullValue()
    {
        return -2147483648;
    }

    public static int workingChildCountMinValue()
    {
        return -2147483647;
    }

    public static int workingChildCountMaxValue()
    {
        return 2147483647;
    }

    public ParentOrderUpdateEncoder workingChildCount(final int value)
    {
        buffer.putInt(offset + 38, value, BYTE_ORDER);
        return this;
    }


    public static int lastChildClOrdIdId()
    {
        return 8;
    }

    public static int lastChildClOrdIdSinceVersion()
    {
        return 0;
    }

    public static int lastChildClOrdIdEncodingOffset()
    {
        return 42;
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

    public ParentOrderUpdateEncoder lastChildClOrdId(final long value)
    {
        buffer.putLong(offset + 42, value, BYTE_ORDER);
        return this;
    }


    public static int updateReasonId()
    {
        return 9;
    }

    public static int updateReasonSinceVersion()
    {
        return 0;
    }

    public static int updateReasonEncodingOffset()
    {
        return 50;
    }

    public static int updateReasonEncodingLength()
    {
        return 1;
    }

    public static String updateReasonMetaAttribute(final MetaAttribute metaAttribute)
    {
        if (MetaAttribute.PRESENCE == metaAttribute)
        {
            return "required";
        }

        return "";
    }

    public ParentOrderUpdateEncoder updateReason(final ParentUpdateReason value)
    {
        buffer.putByte(offset + 50, value.value());
        return this;
    }

    public static int eventClusterTimeId()
    {
        return 10;
    }

    public static int eventClusterTimeSinceVersion()
    {
        return 0;
    }

    public static int eventClusterTimeEncodingOffset()
    {
        return 51;
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

    public ParentOrderUpdateEncoder eventClusterTime(final long value)
    {
        buffer.putLong(offset + 51, value, BYTE_ORDER);
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

        final ParentOrderUpdateDecoder decoder = new ParentOrderUpdateDecoder();
        decoder.wrap(buffer, offset, BLOCK_LENGTH, SCHEMA_VERSION);

        return decoder.appendTo(builder);
    }
}
