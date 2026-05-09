/* Generated SBE (Simple Binary Encoding) message codec. */
package ig.rueishi.nitroj.exchange.messages;

import org.agrona.DirectBuffer;


/**
 * Deterministic terminal parent-order update
 */
@SuppressWarnings("all")
public final class ParentOrderTerminalDecoder
{
    public static final int BLOCK_LENGTH = 47;
    public static final int TEMPLATE_ID = 42;
    public static final int SCHEMA_ID = 1;
    public static final int SCHEMA_VERSION = 3;
    public static final String SEMANTIC_VERSION = "5.3";
    public static final java.nio.ByteOrder BYTE_ORDER = java.nio.ByteOrder.LITTLE_ENDIAN;

    private final ParentOrderTerminalDecoder parentMessage = this;
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

    public ParentOrderTerminalDecoder wrap(
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

    public ParentOrderTerminalDecoder wrapAndApplyHeader(
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

    public ParentOrderTerminalDecoder sbeRewind()
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

    public long parentOrderId()
    {
        return buffer.getLong(offset + 0, BYTE_ORDER);
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

    public short strategyId()
    {
        return buffer.getShort(offset + 8, BYTE_ORDER);
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

    public int executionStrategyId()
    {
        return buffer.getInt(offset + 10, BYTE_ORDER);
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

    public long finalCumFillQtyScaled()
    {
        return buffer.getLong(offset + 14, BYTE_ORDER);
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

    public long avgFillPriceScaled()
    {
        return buffer.getLong(offset + 22, BYTE_ORDER);
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

    public byte terminalReasonRaw()
    {
        return buffer.getByte(offset + 30);
    }

    public ParentTerminalReason terminalReason()
    {
        return ParentTerminalReason.get(buffer.getByte(offset + 30));
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

    public long lastChildClOrdId()
    {
        return buffer.getLong(offset + 31, BYTE_ORDER);
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

    public long eventClusterTime()
    {
        return buffer.getLong(offset + 39, BYTE_ORDER);
    }


    public String toString()
    {
        if (null == buffer)
        {
            return "";
        }

        final ParentOrderTerminalDecoder decoder = new ParentOrderTerminalDecoder();
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
        builder.append("[ParentOrderTerminal](sbeTemplateId=");
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
        builder.append("parentOrderId=");
        builder.append(this.parentOrderId());
        builder.append('|');
        builder.append("strategyId=");
        builder.append(this.strategyId());
        builder.append('|');
        builder.append("executionStrategyId=");
        builder.append(this.executionStrategyId());
        builder.append('|');
        builder.append("finalCumFillQtyScaled=");
        builder.append(this.finalCumFillQtyScaled());
        builder.append('|');
        builder.append("avgFillPriceScaled=");
        builder.append(this.avgFillPriceScaled());
        builder.append('|');
        builder.append("terminalReason=");
        builder.append(this.terminalReason());
        builder.append('|');
        builder.append("lastChildClOrdId=");
        builder.append(this.lastChildClOrdId());
        builder.append('|');
        builder.append("eventClusterTime=");
        builder.append(this.eventClusterTime());

        limit(originalLimit);

        return builder;
    }
    
    public ParentOrderTerminalDecoder sbeSkip()
    {
        sbeRewind();

        return this;
    }
}
