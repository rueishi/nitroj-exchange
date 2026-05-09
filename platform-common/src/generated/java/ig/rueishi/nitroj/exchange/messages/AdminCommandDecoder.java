/* Generated SBE (Simple Binary Encoding) message codec. */
package ig.rueishi.nitroj.exchange.messages;

import org.agrona.DirectBuffer;


/**
 * Operator command; routed through gateway admin Aeron publication
 */
@SuppressWarnings("all")
public final class AdminCommandDecoder
{
    public static final int BLOCK_LENGTH = 29;
    public static final int TEMPLATE_ID = 33;
    public static final int SCHEMA_ID = 1;
    public static final int SCHEMA_VERSION = 3;
    public static final String SEMANTIC_VERSION = "5.3";
    public static final java.nio.ByteOrder BYTE_ORDER = java.nio.ByteOrder.LITTLE_ENDIAN;

    private final AdminCommandDecoder parentMessage = this;
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

    public AdminCommandDecoder wrap(
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

    public AdminCommandDecoder wrapAndApplyHeader(
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

    public AdminCommandDecoder sbeRewind()
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

    public byte commandTypeRaw()
    {
        return buffer.getByte(offset + 0);
    }

    public AdminCommandType commandType()
    {
        return AdminCommandType.get(buffer.getByte(offset + 0));
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

    public int venueId()
    {
        return buffer.getInt(offset + 1, BYTE_ORDER);
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

    public int instrumentId()
    {
        return buffer.getInt(offset + 5, BYTE_ORDER);
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

    public int operatorId()
    {
        return buffer.getInt(offset + 9, BYTE_ORDER);
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

    public long hmacSignature()
    {
        return buffer.getLong(offset + 13, BYTE_ORDER);
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

    public long nonce()
    {
        return buffer.getLong(offset + 21, BYTE_ORDER);
    }


    public String toString()
    {
        if (null == buffer)
        {
            return "";
        }

        final AdminCommandDecoder decoder = new AdminCommandDecoder();
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
        builder.append("[AdminCommand](sbeTemplateId=");
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
        builder.append("commandType=");
        builder.append(this.commandType());
        builder.append('|');
        builder.append("venueId=");
        builder.append(this.venueId());
        builder.append('|');
        builder.append("instrumentId=");
        builder.append(this.instrumentId());
        builder.append('|');
        builder.append("operatorId=");
        builder.append(this.operatorId());
        builder.append('|');
        builder.append("hmacSignature=");
        builder.append(this.hmacSignature());
        builder.append('|');
        builder.append("nonce=");
        builder.append(this.nonce());

        limit(originalLimit);

        return builder;
    }
    
    public AdminCommandDecoder sbeSkip()
    {
        sbeRewind();

        return this;
    }
}
