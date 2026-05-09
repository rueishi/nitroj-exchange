/* Generated SBE (Simple Binary Encoding) message codec. */
package ig.rueishi.nitroj.exchange.messages;

import org.agrona.MutableDirectBuffer;
import org.agrona.DirectBuffer;

@SuppressWarnings("all")
public final class OrderStateSnapshotEncoder
{
    public static final int BLOCK_LENGTH = 78;
    public static final int TEMPLATE_ID = 50;
    public static final int SCHEMA_ID = 1;
    public static final int SCHEMA_VERSION = 3;
    public static final String SEMANTIC_VERSION = "5.3";
    public static final java.nio.ByteOrder BYTE_ORDER = java.nio.ByteOrder.LITTLE_ENDIAN;

    private final OrderStateSnapshotEncoder parentMessage = this;
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

    public OrderStateSnapshotEncoder wrap(final MutableDirectBuffer buffer, final int offset)
    {
        if (buffer != this.buffer)
        {
            this.buffer = buffer;
        }
        this.offset = offset;
        limit(offset + BLOCK_LENGTH);

        return this;
    }

    public OrderStateSnapshotEncoder wrapAndApplyHeader(
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

    public OrderStateSnapshotEncoder clOrdId(final long value)
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

    public OrderStateSnapshotEncoder venueId(final int value)
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

    public OrderStateSnapshotEncoder instrumentId(final int value)
    {
        buffer.putInt(offset + 12, value, BYTE_ORDER);
        return this;
    }


    public static int strategyIdId()
    {
        return 4;
    }

    public static int strategyIdSinceVersion()
    {
        return 0;
    }

    public static int strategyIdEncodingOffset()
    {
        return 16;
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

    public OrderStateSnapshotEncoder strategyId(final short value)
    {
        buffer.putShort(offset + 16, value, BYTE_ORDER);
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
        return 18;
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

    public OrderStateSnapshotEncoder side(final Side value)
    {
        buffer.putByte(offset + 18, value.value());
        return this;
    }

    public static int ordTypeId()
    {
        return 6;
    }

    public static int ordTypeSinceVersion()
    {
        return 0;
    }

    public static int ordTypeEncodingOffset()
    {
        return 19;
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

    public OrderStateSnapshotEncoder ordType(final OrdType value)
    {
        buffer.putByte(offset + 19, value.value());
        return this;
    }

    public static int timeInForceId()
    {
        return 7;
    }

    public static int timeInForceSinceVersion()
    {
        return 0;
    }

    public static int timeInForceEncodingOffset()
    {
        return 20;
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

    public OrderStateSnapshotEncoder timeInForce(final TimeInForce value)
    {
        buffer.putByte(offset + 20, value.value());
        return this;
    }

    public static int statusId()
    {
        return 8;
    }

    public static int statusSinceVersion()
    {
        return 0;
    }

    public static int statusEncodingOffset()
    {
        return 21;
    }

    public static int statusEncodingLength()
    {
        return 1;
    }

    public static String statusMetaAttribute(final MetaAttribute metaAttribute)
    {
        if (MetaAttribute.PRESENCE == metaAttribute)
        {
            return "required";
        }

        return "";
    }

    public static byte statusNullValue()
    {
        return (byte)-128;
    }

    public static byte statusMinValue()
    {
        return (byte)-127;
    }

    public static byte statusMaxValue()
    {
        return (byte)127;
    }

    public OrderStateSnapshotEncoder status(final byte value)
    {
        buffer.putByte(offset + 21, value);
        return this;
    }


    public static int priceScaledId()
    {
        return 9;
    }

    public static int priceScaledSinceVersion()
    {
        return 0;
    }

    public static int priceScaledEncodingOffset()
    {
        return 22;
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

    public OrderStateSnapshotEncoder priceScaled(final long value)
    {
        buffer.putLong(offset + 22, value, BYTE_ORDER);
        return this;
    }


    public static int qtyScaledId()
    {
        return 10;
    }

    public static int qtyScaledSinceVersion()
    {
        return 0;
    }

    public static int qtyScaledEncodingOffset()
    {
        return 30;
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

    public OrderStateSnapshotEncoder qtyScaled(final long value)
    {
        buffer.putLong(offset + 30, value, BYTE_ORDER);
        return this;
    }


    public static int cumFillQtyScaledId()
    {
        return 11;
    }

    public static int cumFillQtyScaledSinceVersion()
    {
        return 0;
    }

    public static int cumFillQtyScaledEncodingOffset()
    {
        return 38;
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

    public OrderStateSnapshotEncoder cumFillQtyScaled(final long value)
    {
        buffer.putLong(offset + 38, value, BYTE_ORDER);
        return this;
    }


    public static int leavesQtyScaledId()
    {
        return 12;
    }

    public static int leavesQtyScaledSinceVersion()
    {
        return 0;
    }

    public static int leavesQtyScaledEncodingOffset()
    {
        return 46;
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

    public OrderStateSnapshotEncoder leavesQtyScaled(final long value)
    {
        buffer.putLong(offset + 46, value, BYTE_ORDER);
        return this;
    }


    public static int avgFillPriceScaledId()
    {
        return 13;
    }

    public static int avgFillPriceScaledSinceVersion()
    {
        return 0;
    }

    public static int avgFillPriceScaledEncodingOffset()
    {
        return 54;
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

    public OrderStateSnapshotEncoder avgFillPriceScaled(final long value)
    {
        buffer.putLong(offset + 54, value, BYTE_ORDER);
        return this;
    }


    public static int createdClusterTimeId()
    {
        return 14;
    }

    public static int createdClusterTimeSinceVersion()
    {
        return 0;
    }

    public static int createdClusterTimeEncodingOffset()
    {
        return 62;
    }

    public static int createdClusterTimeEncodingLength()
    {
        return 8;
    }

    public static String createdClusterTimeMetaAttribute(final MetaAttribute metaAttribute)
    {
        if (MetaAttribute.PRESENCE == metaAttribute)
        {
            return "required";
        }

        return "";
    }

    public static long createdClusterTimeNullValue()
    {
        return -9223372036854775808L;
    }

    public static long createdClusterTimeMinValue()
    {
        return -9223372036854775807L;
    }

    public static long createdClusterTimeMaxValue()
    {
        return 9223372036854775807L;
    }

    public OrderStateSnapshotEncoder createdClusterTime(final long value)
    {
        buffer.putLong(offset + 62, value, BYTE_ORDER);
        return this;
    }


    public static int parentOrderIdId()
    {
        return 15;
    }

    public static int parentOrderIdSinceVersion()
    {
        return 2;
    }

    public static int parentOrderIdEncodingOffset()
    {
        return 70;
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

    public OrderStateSnapshotEncoder parentOrderId(final long value)
    {
        buffer.putLong(offset + 70, value, BYTE_ORDER);
        return this;
    }


    public static int venueOrderIdId()
    {
        return 16;
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

    public OrderStateSnapshotEncoder putVenueOrderId(final DirectBuffer src, final int srcOffset, final int length)
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

    public OrderStateSnapshotEncoder putVenueOrderId(final byte[] src, final int srcOffset, final int length)
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

        final OrderStateSnapshotDecoder decoder = new OrderStateSnapshotDecoder();
        decoder.wrap(buffer, offset, BLOCK_LENGTH, SCHEMA_VERSION);

        return decoder.appendTo(builder);
    }
}
