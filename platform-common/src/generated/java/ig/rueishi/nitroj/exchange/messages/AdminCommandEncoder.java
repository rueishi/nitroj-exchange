/* Generated SBE (Simple Binary Encoding) message codec. */
package ig.rueishi.nitroj.exchange.messages;

import org.agrona.MutableDirectBuffer;


/**
 * Operator command; routed through gateway admin Aeron publication
 */
@SuppressWarnings("all")
public final class AdminCommandEncoder
{
    public static final int BLOCK_LENGTH = 29;
    public static final int TEMPLATE_ID = 33;
    public static final int SCHEMA_ID = 1;
    public static final int SCHEMA_VERSION = 3;
    public static final String SEMANTIC_VERSION = "5.3";
    public static final java.nio.ByteOrder BYTE_ORDER = java.nio.ByteOrder.LITTLE_ENDIAN;

    private final AdminCommandEncoder parentMessage = this;
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

    public AdminCommandEncoder wrap(final MutableDirectBuffer buffer, final int offset)
    {
        if (buffer != this.buffer)
        {
            this.buffer = buffer;
        }
        this.offset = offset;
        limit(offset + BLOCK_LENGTH);

        return this;
    }

    public AdminCommandEncoder wrapAndApplyHeader(
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

    public static int commandTypeId()
    {
        return 1;
    }

    public static int commandTypeSinceVersion()
    {
        return 0;
    }

    public static int commandTypeEncodingOffset()
    {
        return 0;
    }

    public static int commandTypeEncodingLength()
    {
        return 1;
    }

    public static String commandTypeMetaAttribute(final MetaAttribute metaAttribute)
    {
        if (MetaAttribute.PRESENCE == metaAttribute)
        {
            return "required";
        }

        return "";
    }

    public AdminCommandEncoder commandType(final AdminCommandType value)
    {
        buffer.putByte(offset + 0, value.value());
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
        return 1;
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

    public AdminCommandEncoder venueId(final int value)
    {
        buffer.putInt(offset + 1, value, BYTE_ORDER);
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
        return 5;
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

    public AdminCommandEncoder instrumentId(final int value)
    {
        buffer.putInt(offset + 5, value, BYTE_ORDER);
        return this;
    }


    public static int operatorIdId()
    {
        return 4;
    }

    public static int operatorIdSinceVersion()
    {
        return 0;
    }

    public static int operatorIdEncodingOffset()
    {
        return 9;
    }

    public static int operatorIdEncodingLength()
    {
        return 4;
    }

    public static String operatorIdMetaAttribute(final MetaAttribute metaAttribute)
    {
        if (MetaAttribute.PRESENCE == metaAttribute)
        {
            return "required";
        }

        return "";
    }

    public static int operatorIdNullValue()
    {
        return -2147483648;
    }

    public static int operatorIdMinValue()
    {
        return -2147483647;
    }

    public static int operatorIdMaxValue()
    {
        return 2147483647;
    }

    public AdminCommandEncoder operatorId(final int value)
    {
        buffer.putInt(offset + 9, value, BYTE_ORDER);
        return this;
    }


    public static int hmacSignatureId()
    {
        return 5;
    }

    public static int hmacSignatureSinceVersion()
    {
        return 0;
    }

    public static int hmacSignatureEncodingOffset()
    {
        return 13;
    }

    public static int hmacSignatureEncodingLength()
    {
        return 8;
    }

    public static String hmacSignatureMetaAttribute(final MetaAttribute metaAttribute)
    {
        if (MetaAttribute.PRESENCE == metaAttribute)
        {
            return "required";
        }

        return "";
    }

    public static long hmacSignatureNullValue()
    {
        return -9223372036854775808L;
    }

    public static long hmacSignatureMinValue()
    {
        return -9223372036854775807L;
    }

    public static long hmacSignatureMaxValue()
    {
        return 9223372036854775807L;
    }

    public AdminCommandEncoder hmacSignature(final long value)
    {
        buffer.putLong(offset + 13, value, BYTE_ORDER);
        return this;
    }


    public static int nonceId()
    {
        return 6;
    }

    public static int nonceSinceVersion()
    {
        return 0;
    }

    public static int nonceEncodingOffset()
    {
        return 21;
    }

    public static int nonceEncodingLength()
    {
        return 8;
    }

    public static String nonceMetaAttribute(final MetaAttribute metaAttribute)
    {
        if (MetaAttribute.PRESENCE == metaAttribute)
        {
            return "required";
        }

        return "";
    }

    public static long nonceNullValue()
    {
        return -9223372036854775808L;
    }

    public static long nonceMinValue()
    {
        return -9223372036854775807L;
    }

    public static long nonceMaxValue()
    {
        return 9223372036854775807L;
    }

    public AdminCommandEncoder nonce(final long value)
    {
        buffer.putLong(offset + 21, value, BYTE_ORDER);
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

        final AdminCommandDecoder decoder = new AdminCommandDecoder();
        decoder.wrap(buffer, offset, BLOCK_LENGTH, SCHEMA_VERSION);

        return decoder.appendTo(builder);
    }
}
