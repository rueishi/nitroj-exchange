/* Generated SBE (Simple Binary Encoding) message codec. */
package ig.rueishi.nitroj.exchange.messages;

import org.agrona.MutableDirectBuffer;

@SuppressWarnings("all")
public final class RiskStateSnapshotEncoder
{
    public static final int BLOCK_LENGTH = 25;
    public static final int TEMPLATE_ID = 52;
    public static final int SCHEMA_ID = 1;
    public static final int SCHEMA_VERSION = 3;
    public static final String SEMANTIC_VERSION = "5.3";
    public static final java.nio.ByteOrder BYTE_ORDER = java.nio.ByteOrder.LITTLE_ENDIAN;

    private final RiskStateSnapshotEncoder parentMessage = this;
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

    public RiskStateSnapshotEncoder wrap(final MutableDirectBuffer buffer, final int offset)
    {
        if (buffer != this.buffer)
        {
            this.buffer = buffer;
        }
        this.offset = offset;
        limit(offset + BLOCK_LENGTH);

        return this;
    }

    public RiskStateSnapshotEncoder wrapAndApplyHeader(
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

    public static int killSwitchActiveId()
    {
        return 1;
    }

    public static int killSwitchActiveSinceVersion()
    {
        return 0;
    }

    public static int killSwitchActiveEncodingOffset()
    {
        return 0;
    }

    public static int killSwitchActiveEncodingLength()
    {
        return 1;
    }

    public static String killSwitchActiveMetaAttribute(final MetaAttribute metaAttribute)
    {
        if (MetaAttribute.PRESENCE == metaAttribute)
        {
            return "required";
        }

        return "";
    }

    public RiskStateSnapshotEncoder killSwitchActive(final BooleanType value)
    {
        buffer.putByte(offset + 0, value.value());
        return this;
    }

    public static int dailyRealizedPnlScaledId()
    {
        return 2;
    }

    public static int dailyRealizedPnlScaledSinceVersion()
    {
        return 0;
    }

    public static int dailyRealizedPnlScaledEncodingOffset()
    {
        return 1;
    }

    public static int dailyRealizedPnlScaledEncodingLength()
    {
        return 8;
    }

    public static String dailyRealizedPnlScaledMetaAttribute(final MetaAttribute metaAttribute)
    {
        if (MetaAttribute.PRESENCE == metaAttribute)
        {
            return "required";
        }

        return "";
    }

    public static long dailyRealizedPnlScaledNullValue()
    {
        return -9223372036854775808L;
    }

    public static long dailyRealizedPnlScaledMinValue()
    {
        return -9223372036854775807L;
    }

    public static long dailyRealizedPnlScaledMaxValue()
    {
        return 9223372036854775807L;
    }

    public RiskStateSnapshotEncoder dailyRealizedPnlScaled(final long value)
    {
        buffer.putLong(offset + 1, value, BYTE_ORDER);
        return this;
    }


    public static int dailyVolumeScaledId()
    {
        return 3;
    }

    public static int dailyVolumeScaledSinceVersion()
    {
        return 0;
    }

    public static int dailyVolumeScaledEncodingOffset()
    {
        return 9;
    }

    public static int dailyVolumeScaledEncodingLength()
    {
        return 8;
    }

    public static String dailyVolumeScaledMetaAttribute(final MetaAttribute metaAttribute)
    {
        if (MetaAttribute.PRESENCE == metaAttribute)
        {
            return "required";
        }

        return "";
    }

    public static long dailyVolumeScaledNullValue()
    {
        return -9223372036854775808L;
    }

    public static long dailyVolumeScaledMinValue()
    {
        return -9223372036854775807L;
    }

    public static long dailyVolumeScaledMaxValue()
    {
        return 9223372036854775807L;
    }

    public RiskStateSnapshotEncoder dailyVolumeScaled(final long value)
    {
        buffer.putLong(offset + 9, value, BYTE_ORDER);
        return this;
    }


    public static int snapshotClusterTimeId()
    {
        return 4;
    }

    public static int snapshotClusterTimeSinceVersion()
    {
        return 0;
    }

    public static int snapshotClusterTimeEncodingOffset()
    {
        return 17;
    }

    public static int snapshotClusterTimeEncodingLength()
    {
        return 8;
    }

    public static String snapshotClusterTimeMetaAttribute(final MetaAttribute metaAttribute)
    {
        if (MetaAttribute.PRESENCE == metaAttribute)
        {
            return "required";
        }

        return "";
    }

    public static long snapshotClusterTimeNullValue()
    {
        return -9223372036854775808L;
    }

    public static long snapshotClusterTimeMinValue()
    {
        return -9223372036854775807L;
    }

    public static long snapshotClusterTimeMaxValue()
    {
        return 9223372036854775807L;
    }

    public RiskStateSnapshotEncoder snapshotClusterTime(final long value)
    {
        buffer.putLong(offset + 17, value, BYTE_ORDER);
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

        final RiskStateSnapshotDecoder decoder = new RiskStateSnapshotDecoder();
        decoder.wrap(buffer, offset, BLOCK_LENGTH, SCHEMA_VERSION);

        return decoder.appendTo(builder);
    }
}
