/* Generated Fix Gateway message codec */
package ig.rueishi.nitroj.exchange.fix.fix44.decoder;

import uk.co.real_logic.artio.decoder.AbstractLogonDecoder;
import org.agrona.AsciiNumberFormatException;
import uk.co.real_logic.artio.dictionary.Generated;
import org.agrona.MutableDirectBuffer;
import org.agrona.AsciiSequenceView;
import static uk.co.real_logic.artio.dictionary.generation.CodecUtil.*;
import static uk.co.real_logic.artio.dictionary.SessionConstants.*;
import uk.co.real_logic.artio.builder.Decoder;
import ig.rueishi.nitroj.exchange.fix.fix44.decoder.HeaderDecoder;
import ig.rueishi.nitroj.exchange.fix.fix44.decoder.TrailerDecoder;
import uk.co.real_logic.artio.fields.ReadOnlyDecimalFloat;
import uk.co.real_logic.artio.fields.DecimalFloat;
import uk.co.real_logic.artio.util.MutableAsciiBuffer;
import uk.co.real_logic.artio.util.AsciiBuffer;
import uk.co.real_logic.artio.fields.LocalMktDateEncoder;
import uk.co.real_logic.artio.fields.UtcTimestampEncoder;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import uk.co.real_logic.artio.dictionary.CharArraySet;
import org.agrona.collections.IntHashSet;
import org.agrona.collections.IntHashSet.IntIterator;
import uk.co.real_logic.artio.EncodingException;
import uk.co.real_logic.artio.dictionary.CharArrayWrapper;
import uk.co.real_logic.artio.builder.Encoder;
import uk.co.real_logic.artio.builder.CommonDecoderImpl;
import static java.nio.charset.StandardCharsets.US_ASCII;
import static uk.co.real_logic.artio.builder.Validation.CODEC_VALIDATION_ENABLED;
import static uk.co.real_logic.artio.builder.RejectUnknownField.CODEC_REJECT_UNKNOWN_FIELD_ENABLED;
import static uk.co.real_logic.artio.builder.RejectUnknownEnumValue.CODEC_REJECT_UNKNOWN_ENUM_VALUE_ENABLED;
import ig.rueishi.nitroj.exchange.fix.fix44.*;
import ig.rueishi.nitroj.exchange.fix.fix44.builder.LogonEncoder;

@Generated("uk.co.real_logic.artio")
public class LogonDecoder extends CommonDecoderImpl implements MessageDecoder, AbstractLogonDecoder
{
    public final IntHashSet REQUIRED_FIELDS = new IntHashSet(6);

    {
        if (CODEC_VALIDATION_ENABLED)
        {
            REQUIRED_FIELDS.add(Constants.ENCRYPT_METHOD);
            REQUIRED_FIELDS.add(Constants.HEART_BT_INT);
            REQUIRED_FIELDS.add(Constants.CANCEL_ORDERS_ON_DISCONNECT);
        }
    }

    private final IntHashSet alreadyVisitedFields = new IntHashSet(18);

    private final IntHashSet unknownFields = new IntHashSet(10);

    private final IntHashSet missingRequiredFields = new IntHashSet(6);

    public boolean validate()
    {
        if (rejectReason != Decoder.NO_ERROR)
        {
            return false;
        }
        final IntIterator missingFieldsIterator = missingRequiredFields.iterator();
        final IntIterator unknownFieldsIterator = unknownFields.iterator();
        if (CODEC_REJECT_UNKNOWN_FIELD_ENABLED && unknownFieldsIterator.hasNext())
        {
            invalidTagId = unknownFieldsIterator.nextValue();
            rejectReason = Constants.ALL_FIELDS.contains(invalidTagId) ? 2 : 0;
            return false;
        }
        if (!header.validate())
        {
            invalidTagId = header.invalidTagId();
            rejectReason = header.rejectReason();
            return false;
        }
        else if (!trailer.validate())
        {
            invalidTagId = trailer.invalidTagId();
            rejectReason = trailer.rejectReason();
            return false;
        }
        if (missingFieldsIterator.hasNext())
        {
            invalidTagId = missingFieldsIterator.nextValue();
            rejectReason = 1;
            return false;
        }
        if (hasResetSeqNumFlag)
        {
        if (CODEC_VALIDATION_ENABLED && resetSeqNumFlagLength > 1)
        {
            invalidTagId = 141;
            rejectReason = 5;
            return false;
        }
        }

        if (CODEC_VALIDATION_ENABLED && cancelOrdersOnDisconnectLength > 1)
        {
            invalidTagId = 8013;
            rejectReason = 5;
            return false;
        }
        return true;
    }

    public static final long MESSAGE_TYPE = 65L;

    public static final String MESSAGE_TYPE_AS_STRING = "A";

    public static final char[] MESSAGE_TYPE_CHARS = MESSAGE_TYPE_AS_STRING.toCharArray();

    public static final byte[] MESSAGE_TYPE_BYTES = MESSAGE_TYPE_AS_STRING.getBytes(US_ASCII);

    public final IntHashSet messageFields = new IntHashSet(50);

    {
        messageFields.add(Constants.BEGIN_STRING);
        messageFields.add(Constants.BODY_LENGTH);
        messageFields.add(Constants.MSG_TYPE);
        messageFields.add(Constants.SENDER_COMP_ID);
        messageFields.add(Constants.TARGET_COMP_ID);
        messageFields.add(Constants.MSG_SEQ_NUM);
        messageFields.add(Constants.SENDER_SUB_ID);
        messageFields.add(Constants.SENDER_LOCATION_ID);
        messageFields.add(Constants.TARGET_SUB_ID);
        messageFields.add(Constants.TARGET_LOCATION_ID);
        messageFields.add(Constants.POSS_DUP_FLAG);
        messageFields.add(Constants.POSS_RESEND);
        messageFields.add(Constants.SENDING_TIME);
        messageFields.add(Constants.ORIG_SENDING_TIME);
        messageFields.add(Constants.LAST_MSG_SEQ_NUM_PROCESSED);
        messageFields.add(Constants.ENCRYPT_METHOD);
        messageFields.add(Constants.HEART_BT_INT);
        messageFields.add(Constants.RAW_DATA_LENGTH);
        messageFields.add(Constants.RAW_DATA);
        messageFields.add(Constants.RESET_SEQ_NUM_FLAG);
        messageFields.add(Constants.USERNAME);
        messageFields.add(Constants.PASSWORD);
        messageFields.add(Constants.MESSAGE_HANDLING);
        messageFields.add(Constants.CANCEL_ORDERS_ON_DISCONNECT);
        messageFields.add(Constants.CHECK_SUM);
    }

    private final TrailerDecoder trailer = new TrailerDecoder();

    public TrailerDecoder trailer()
    {
        return trailer;
    }

    private final HeaderDecoder header = new HeaderDecoder(trailer);

    public HeaderDecoder header()
    {
        return header;
    }

    private int encryptMethod = MISSING_INT;

    /* EncryptMethod = 98 */
    public int encryptMethod()
    {
        return encryptMethod;
    }



    private int heartBtInt = MISSING_INT;

    /* HeartBtInt = 108 */
    public int heartBtInt()
    {
        return heartBtInt;
    }



    private int rawDataLength = MISSING_INT;

    private boolean hasRawDataLength;

    /* RawDataLength = 95 */
    public int rawDataLength()
    {
        if (!hasRawDataLength)
        {
            throw new IllegalArgumentException("No value for optional field: RawDataLength");
        }

        return rawDataLength;
    }

    public boolean hasRawDataLength()
    {
        return hasRawDataLength;
    }



    private byte[] rawData = new byte[1];

    private boolean hasRawData;

    /* RawData = 96 */
    public byte[] rawData()
    {
        if (!hasRawData)
        {
            throw new IllegalArgumentException("No value for optional field: RawData");
        }

        return rawData;
    }

    public boolean hasRawData()
    {
        return hasRawData;
    }



    private boolean resetSeqNumFlag;

    private int resetSeqNumFlagLength = 0;
    public int resetSeqNumFlagLength()    {
       return resetSeqNumFlagLength;
    }
    private boolean hasResetSeqNumFlag;

    /* ResetSeqNumFlag = 141 */
    public boolean resetSeqNumFlag()
    {
        if (!hasResetSeqNumFlag)
        {
            throw new IllegalArgumentException("No value for optional field: ResetSeqNumFlag");
        }

        return resetSeqNumFlag;
    }

    public boolean hasResetSeqNumFlag()
    {
        return hasResetSeqNumFlag;
    }



    private char[] username = new char[1];

    private boolean hasUsername;

    /* Username = 553 */
    public char[] username()
    {
        if (!hasUsername)
        {
            throw new IllegalArgumentException("No value for optional field: Username");
        }

        return username;
    }

    public boolean hasUsername()
    {
        return hasUsername;
    }


    private int usernameOffset;

    private int usernameLength;

    /* Username = 553 */
    public int usernameLength()
    {
        if (!hasUsername)
        {
            throw new IllegalArgumentException("No value for optional field: Username");
        }

        return usernameLength;
    }

    /* Username = 553 */
    public String usernameAsString()
    {
        return hasUsername ? new String(username, 0, usernameLength) : null;
    }

    /* Username = 553 */
    public AsciiSequenceView username(final AsciiSequenceView view)
    {
        if (!hasUsername)
        {
            throw new IllegalArgumentException("No value for optional field: Username");
        }

        return view.wrap(buffer, usernameOffset, usernameLength);
    }


    private final CharArrayWrapper usernameWrapper = new CharArrayWrapper();
    private char[] password = new char[1];

    private boolean hasPassword;

    /* Password = 554 */
    public char[] password()
    {
        if (!hasPassword)
        {
            throw new IllegalArgumentException("No value for optional field: Password");
        }

        return password;
    }

    public boolean hasPassword()
    {
        return hasPassword;
    }


    private int passwordOffset;

    private int passwordLength;

    /* Password = 554 */
    public int passwordLength()
    {
        if (!hasPassword)
        {
            throw new IllegalArgumentException("No value for optional field: Password");
        }

        return passwordLength;
    }

    /* Password = 554 */
    public String passwordAsString()
    {
        return hasPassword ? new String(password, 0, passwordLength) : null;
    }

    /* Password = 554 */
    public AsciiSequenceView password(final AsciiSequenceView view)
    {
        if (!hasPassword)
        {
            throw new IllegalArgumentException("No value for optional field: Password");
        }

        return view.wrap(buffer, passwordOffset, passwordLength);
    }


    private final CharArrayWrapper passwordWrapper = new CharArrayWrapper();
    private int messageHandling = MISSING_INT;

    private boolean hasMessageHandling;

    /* MessageHandling = 25035 */
    public int messageHandling()
    {
        if (!hasMessageHandling)
        {
            throw new IllegalArgumentException("No value for optional field: MessageHandling");
        }

        return messageHandling;
    }

    public boolean hasMessageHandling()
    {
        return hasMessageHandling;
    }



    private char cancelOrdersOnDisconnect = MISSING_CHAR;

    private int cancelOrdersOnDisconnectLength = 0;
    public int cancelOrdersOnDisconnectLength()    {
       return cancelOrdersOnDisconnectLength;
    }
    /* CancelOrdersOnDisconnect = 8013 */
    public char cancelOrdersOnDisconnect()
    {
        return cancelOrdersOnDisconnect;
    }



    public int cancelOnDisconnectType()
    {
        throw new UnsupportedOperationException();
    }

    public boolean hasCancelOnDisconnectType()
    {
        throw new UnsupportedOperationException();
    }

    public int cODTimeoutWindow()
    {
        throw new UnsupportedOperationException();
    }

    public boolean hasCODTimeoutWindow()
    {
        throw new UnsupportedOperationException();
    }

    public boolean supportsUsername()
    {
        return true;
    }

    public boolean supportsPassword()
    {
        return true;
    }

    public boolean supportsCancelOnDisconnectType()
    {
        return false;
    }

    public boolean supportsCODTimeoutWindow()
    {
        return false;
    }

    public int decode(final AsciiBuffer buffer, final int offset, final int length)
    {
        // Decode Logon
        int seenFieldCount = 0;
        if (CODEC_VALIDATION_ENABLED)
        {
            missingRequiredFields.copy(REQUIRED_FIELDS);
            alreadyVisitedFields.clear();
        }
        this.buffer = buffer;
        final int end = offset + length;
        int position = offset;
        int positionIter = position;
        position += header.decode(buffer, position, length);
        int tag;

        while (position < end)
        {
            final int equalsPosition = buffer.scan(position, end, '=');
            if (equalsPosition == AsciiBuffer.UNKNOWN_INDEX)
            {
               return position;
            }
            tag = buffer.getInt(position, equalsPosition);
            final int valueOffset = equalsPosition + 1;
            int endOfField = buffer.scan(valueOffset, end, START_OF_HEADER);
            if (endOfField == AsciiBuffer.UNKNOWN_INDEX)
            {
                rejectReason = 5;
                break;
            }
            final int valueLength = endOfField - valueOffset;
            if (CODEC_VALIDATION_ENABLED)
            {
                if (tag <= 0)
                {
                    invalidTagId = tag;
                    rejectReason = 0;
                }
                else if (valueLength == 0)
                {
                    invalidTagId = tag;
                    rejectReason = 4;
                }
                if (!alreadyVisitedFields.add(tag))
                {
                    invalidTagId = tag;
                    rejectReason = 13;
                }
                missingRequiredFields.remove(tag);
                seenFieldCount++;
            }

            switch (tag)
            {
            case Constants.ENCRYPT_METHOD:
                encryptMethod = getInt(buffer, valueOffset, endOfField, 98, CODEC_VALIDATION_ENABLED);
                break;

            case Constants.HEART_BT_INT:
                heartBtInt = getInt(buffer, valueOffset, endOfField, 108, CODEC_VALIDATION_ENABLED);
                break;

            case Constants.RAW_DATA_LENGTH:
                hasRawDataLength = true;
                rawDataLength = getInt(buffer, valueOffset, endOfField, 95, CODEC_VALIDATION_ENABLED);
                break;

            case Constants.RAW_DATA:
                hasRawData = true;
                rawData = buffer.getBytes(rawData, valueOffset, rawDataLength);
                endOfField = valueOffset + rawDataLength;
                break;

            case Constants.RESET_SEQ_NUM_FLAG:
                hasResetSeqNumFlag = true;
                resetSeqNumFlag = buffer.getBoolean(valueOffset);
                resetSeqNumFlagLength = valueLength;
                break;

            case Constants.USERNAME:
                hasUsername = true;
                username = buffer.getChars(username, valueOffset, valueLength);
                usernameOffset = valueOffset;
                usernameLength = valueLength;
                break;

            case Constants.PASSWORD:
                hasPassword = true;
                password = buffer.getChars(password, valueOffset, valueLength);
                passwordOffset = valueOffset;
                passwordLength = valueLength;
                break;

            case Constants.MESSAGE_HANDLING:
                hasMessageHandling = true;
                messageHandling = getInt(buffer, valueOffset, endOfField, 25035, CODEC_VALIDATION_ENABLED);
                break;

            case Constants.CANCEL_ORDERS_ON_DISCONNECT:
                cancelOrdersOnDisconnect = buffer.getChar(valueOffset);
                cancelOrdersOnDisconnectLength = valueLength;
                break;

            default:
                if (!CODEC_REJECT_UNKNOWN_FIELD_ENABLED)
                {
                    alreadyVisitedFields.remove(tag);
                }
                else
                {
                    if (!(trailer.REQUIRED_FIELDS.contains(tag)))
                    {
                        unknownFields.add(tag);
                    }
                }
                if (CODEC_REJECT_UNKNOWN_FIELD_ENABLED || (trailer.REQUIRED_FIELDS.contains(tag)))
                {
                    position += trailer.decode(buffer, position, end - position);
                    return position - offset;
                }

            }

            if (position < (endOfField + 1))
            {
                position = endOfField + 1;
            }
        }
        position += trailer.decode(buffer, position, end - position);
        return position - offset;
    }

    public void reset()
    {
        header.reset();
        trailer.reset();
        resetMessage();
        buffer = null;
        if (CODEC_VALIDATION_ENABLED)
        {
            invalidTagId = Decoder.NO_ERROR;
            rejectReason = Decoder.NO_ERROR;
            missingRequiredFields.clear();
            unknownFields.clear();
            alreadyVisitedFields.clear();
        }
    }

    public void resetMessage()
    {
        this.resetEncryptMethod();
        this.resetHeartBtInt();
        this.resetRawDataLength();
        this.resetRawData();
        this.resetResetSeqNumFlag();
        this.resetUsername();
        this.resetPassword();
        this.resetMessageHandling();
        this.resetCancelOrdersOnDisconnect();
    }

    public void resetEncryptMethod()
    {
        encryptMethod = MISSING_INT;
    }

    public void resetHeartBtInt()
    {
        heartBtInt = MISSING_INT;
    }

    public void resetRawDataLength()
    {
        hasRawDataLength = false;
    }

    public void resetRawData()
    {
        hasRawData = false;
    }

    public void resetResetSeqNumFlag()
    {
        hasResetSeqNumFlag = false;
    }

    public void resetUsername()
    {
        hasUsername = false;
    }

    public void resetPassword()
    {
        hasPassword = false;
    }

    public void resetMessageHandling()
    {
        hasMessageHandling = false;
    }

    public void resetCancelOrdersOnDisconnect()
    {
        cancelOrdersOnDisconnect = MISSING_CHAR;
    }

    public String toString()
    {
        return appendTo(new StringBuilder()).toString();
    }

    public StringBuilder appendTo(final StringBuilder builder)
    {
        return appendTo(builder, 1);
    }

    public StringBuilder appendTo(final StringBuilder builder, final int level)
    {
        builder.append("{\n");        indent(builder, level);
        builder.append("\"MessageName\": \"Logon\",\n");
        builder.append("  \"header\": ");
        header.appendTo(builder, level + 1);
        builder.append("\n");
        indent(builder, level);
        builder.append("\"EncryptMethod\": \"");
        builder.append(encryptMethod);
        builder.append("\",\n");

        indent(builder, level);
        builder.append("\"HeartBtInt\": \"");
        builder.append(heartBtInt);
        builder.append("\",\n");

        if (hasRawDataLength())
        {
            indent(builder, level);
            builder.append("\"RawDataLength\": \"");
            builder.append(rawDataLength);
            builder.append("\",\n");
        }

        if (hasRawData())
        {
            indent(builder, level);
            builder.append("\"RawData\": \"");
            appendData(builder, rawData, rawDataLength);
            builder.append("\",\n");
        }

        if (hasResetSeqNumFlag())
        {
            indent(builder, level);
            builder.append("\"ResetSeqNumFlag\": \"");
            builder.append(resetSeqNumFlag);
            builder.append("\",\n");
        }

        if (hasUsername())
        {
            indent(builder, level);
            builder.append("\"Username\": \"");
            builder.append(this.username(), 0, usernameLength());
            builder.append("\",\n");
        }

        if (hasPassword())
        {
            indent(builder, level);
            builder.append("\"Password\": \"");
            builder.append(this.password(), 0, passwordLength());
            builder.append("\",\n");
        }

        if (hasMessageHandling())
        {
            indent(builder, level);
            builder.append("\"MessageHandling\": \"");
            builder.append(messageHandling);
            builder.append("\",\n");
        }

        indent(builder, level);
        builder.append("\"CancelOrdersOnDisconnect\": \"");
        builder.append(cancelOrdersOnDisconnect);
        builder.append("\",\n");
        indent(builder, level - 1);
        builder.append("}");
        return builder;
    }

    public LogonEncoder toEncoder(final Encoder encoder)
    {
        return toEncoder((LogonEncoder)encoder);
    }

    public LogonEncoder toEncoder(final LogonEncoder encoder)
    {
        encoder.reset();
        encoder.encryptMethod(this.encryptMethod());
        encoder.heartBtInt(this.heartBtInt());
        if (hasRawDataLength())
        {
            encoder.rawDataLength(this.rawDataLength());
        }

        if (hasRawData())
        {
            encoder.rawDataAsCopy(this.rawData(), 0, rawDataLength());
            encoder.rawDataLength(this.rawDataLength());
        }

        if (hasResetSeqNumFlag())
        {
            encoder.resetSeqNumFlag(this.resetSeqNumFlag());
        }

        if (hasUsername())
        {
            encoder.username(this.username(), 0, usernameLength());
        }

        if (hasPassword())
        {
            encoder.password(this.password(), 0, passwordLength());
        }

        if (hasMessageHandling())
        {
            encoder.messageHandling(this.messageHandling());
        }

        encoder.cancelOrdersOnDisconnect(this.cancelOrdersOnDisconnect());        return encoder;
    }

}
