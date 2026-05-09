/* Generated Fix Gateway message codec */
package ig.rueishi.nitroj.exchange.fix.fix42.builder;

import uk.co.real_logic.artio.builder.AbstractLogonEncoder;
import uk.co.real_logic.artio.dictionary.Generated;
import org.agrona.MutableDirectBuffer;
import org.agrona.AsciiSequenceView;
import static uk.co.real_logic.artio.dictionary.generation.CodecUtil.*;
import static uk.co.real_logic.artio.dictionary.SessionConstants.*;
import uk.co.real_logic.artio.builder.Encoder;
import ig.rueishi.nitroj.exchange.fix.fix42.builder.HeaderEncoder;
import ig.rueishi.nitroj.exchange.fix.fix42.builder.TrailerEncoder;
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
import org.agrona.DirectBuffer;
import org.agrona.MutableDirectBuffer;
import org.agrona.concurrent.UnsafeBuffer;
import org.agrona.AsciiSequenceView;
import uk.co.real_logic.artio.builder.FieldBagEncoder;
import static java.nio.charset.StandardCharsets.US_ASCII;
import static uk.co.real_logic.artio.builder.Validation.CODEC_VALIDATION_ENABLED;
import static uk.co.real_logic.artio.builder.RejectUnknownField.CODEC_REJECT_UNKNOWN_FIELD_ENABLED;
import static uk.co.real_logic.artio.builder.RejectUnknownEnumValue.CODEC_REJECT_UNKNOWN_ENUM_VALUE_ENABLED;
import ig.rueishi.nitroj.exchange.fix.fix42.*;

@Generated("uk.co.real_logic.artio")
public class LogonEncoder implements AbstractLogonEncoder
{
    public long messageType()
    {
        return 65L;
    }

    public LogonEncoder()
    {
        header.msgType("A");
    }

    private final TrailerEncoder trailer = new TrailerEncoder();

    public TrailerEncoder trailer()
    {
        return trailer;
    }

    private final HeaderEncoder header = new HeaderEncoder();

    public HeaderEncoder header()
    {
        return header;
    }

    private static final int encryptMethodHeaderLength = 3;
    private static final byte[] encryptMethodHeader = new byte[] {57, 56, (byte) '='};

    private static final int heartBtIntHeaderLength = 4;
    private static final byte[] heartBtIntHeader = new byte[] {49, 48, 56, (byte) '='};

    private static final int rawDataLengthHeaderLength = 3;
    private static final byte[] rawDataLengthHeader = new byte[] {57, 53, (byte) '='};

    private static final int rawDataHeaderLength = 3;
    private static final byte[] rawDataHeader = new byte[] {57, 54, (byte) '='};

    private static final int resetSeqNumFlagHeaderLength = 4;
    private static final byte[] resetSeqNumFlagHeader = new byte[] {49, 52, 49, (byte) '='};

    private static final int usernameHeaderLength = 4;
    private static final byte[] usernameHeader = new byte[] {53, 53, 51, (byte) '='};

    private static final int passwordHeaderLength = 4;
    private static final byte[] passwordHeader = new byte[] {53, 53, 52, (byte) '='};

    private static final int messageHandlingHeaderLength = 6;
    private static final byte[] messageHandlingHeader = new byte[] {50, 53, 48, 51, 53, (byte) '='};

    private static final int cancelOrdersOnDisconnectHeaderLength = 5;
    private static final byte[] cancelOrdersOnDisconnectHeader = new byte[] {56, 48, 49, 51, (byte) '='};

    private int encryptMethod;

    private boolean hasEncryptMethod;

    public boolean hasEncryptMethod()
    {
        return hasEncryptMethod;
    }

    /* EncryptMethod = 98 */
    public LogonEncoder encryptMethod(int value)
    {
        encryptMethod = value;
        hasEncryptMethod = true;
        return this;
    }

    /* EncryptMethod = 98 */
    public int encryptMethod()
    {
        return encryptMethod;
    }

    private int heartBtInt;

    private boolean hasHeartBtInt;

    public boolean hasHeartBtInt()
    {
        return hasHeartBtInt;
    }

    /* HeartBtInt = 108 */
    public LogonEncoder heartBtInt(int value)
    {
        heartBtInt = value;
        hasHeartBtInt = true;
        return this;
    }

    /* HeartBtInt = 108 */
    public int heartBtInt()
    {
        return heartBtInt;
    }

    private int rawDataLength;

    private boolean hasRawDataLength;

    public boolean hasRawDataLength()
    {
        return hasRawDataLength;
    }

    /* RawDataLength = 95 */
    public LogonEncoder rawDataLength(int value)
    {
        rawDataLength = value;
        hasRawDataLength = true;
        return this;
    }

    /* RawDataLength = 95 */
    public int rawDataLength()
    {
        return rawDataLength;
    }

    private byte[] rawData;

    private boolean hasRawData;

    public boolean hasRawData()
    {
        return hasRawData;
    }

    /* RawData = 96 */
    public LogonEncoder rawData(byte[] value)
    {
        rawData = value;
        hasRawData = true;
        return this;
    }

    /* RawData = 96 */
    public byte[] rawData()
    {
        return rawData;
    }

    /* RawData = 96 */
    public LogonEncoder rawDataAsCopy(final byte[] value, final int offset, final int length)
    {
        rawData = copyInto(rawData, value, offset, length);
        hasRawData = true;
        return this;
    }

    private boolean resetSeqNumFlag;

    private boolean hasResetSeqNumFlag;

    public boolean hasResetSeqNumFlag()
    {
        return hasResetSeqNumFlag;
    }

    /* ResetSeqNumFlag = 141 */
    public LogonEncoder resetSeqNumFlag(boolean value)
    {
        resetSeqNumFlag = value;
        hasResetSeqNumFlag = true;
        return this;
    }

    /* ResetSeqNumFlag = 141 */
    public boolean resetSeqNumFlag()
    {
        return resetSeqNumFlag;
    }

    private final MutableDirectBuffer username = new UnsafeBuffer();
    private byte[] usernameInternalBuffer = username.byteArray();
    private int usernameOffset = 0;
    private int usernameLength = 0;

    /* Username = 553 */
    public LogonEncoder username(final DirectBuffer value, final int offset, final int length)
    {
        username.wrap(value);
        usernameOffset = offset;
        usernameLength = length;
        return this;
    }

    /* Username = 553 */
    public LogonEncoder username(final DirectBuffer value, final int length)
    {
        return username(value, 0, length);
    }

    /* Username = 553 */
    public LogonEncoder username(final DirectBuffer value)
    {
        return username(value, 0, value.capacity());
    }

    /* Username = 553 */
    public LogonEncoder username(final byte[] value, final int offset, final int length)
    {
        username.wrap(value);
        usernameOffset = offset;
        usernameLength = length;
        return this;
    }

    /* Username = 553 */
    public LogonEncoder usernameAsCopy(final byte[] value, final int offset, final int length)
    {
        if (copyInto(username, value, offset, length))
        {
            usernameInternalBuffer = username.byteArray();
        }
        usernameOffset = 0;
        usernameLength = length;
        return this;
    }

    /* Username = 553 */
    public LogonEncoder username(final byte[] value, final int length)
    {
        return username(value, 0, length);
    }

    /* Username = 553 */
    public LogonEncoder username(final byte[] value)
    {
        return username(value, 0, value.length);
    }

    /* Username = 553 */
    public boolean hasUsername()
    {
        return usernameLength > 0;
    }

    /* Username = 553 */
    public MutableDirectBuffer username()
    {
        return username;
    }

    /* Username = 553 */
    public String usernameAsString()
    {
        return username.getStringWithoutLengthAscii(usernameOffset, usernameLength);
    }

    /* Username = 553 */
    public LogonEncoder username(final CharSequence value)
    {
        if (toBytes(value, username))
        {
            usernameInternalBuffer = username.byteArray();
        }
        usernameOffset = 0;
        usernameLength = value.length();
        return this;
    }

    /* Username = 553 */
    public LogonEncoder username(final AsciiSequenceView value)
    {
        final DirectBuffer buffer = value.buffer();
        if (buffer != null)
        {
            username.wrap(buffer);
            usernameOffset = value.offset();
            usernameLength = value.length();
        }
        return this;
    }

    /* Username = 553 */
    public LogonEncoder username(final char[] value)
    {
        return username(value, 0, value.length);
    }

    /* Username = 553 */
    public LogonEncoder username(final char[] value, final int length)
    {
        return username(value, 0, length);
    }

    /* Username = 553 */
    public LogonEncoder username(final char[] value, final int offset, final int length)
    {
        if (toBytes(value, username, offset, length))
        {
            usernameInternalBuffer = username.byteArray();
        }
        usernameOffset = 0;
        usernameLength = length;
        return this;
    }

    private final MutableDirectBuffer password = new UnsafeBuffer();
    private byte[] passwordInternalBuffer = password.byteArray();
    private int passwordOffset = 0;
    private int passwordLength = 0;

    /* Password = 554 */
    public LogonEncoder password(final DirectBuffer value, final int offset, final int length)
    {
        password.wrap(value);
        passwordOffset = offset;
        passwordLength = length;
        return this;
    }

    /* Password = 554 */
    public LogonEncoder password(final DirectBuffer value, final int length)
    {
        return password(value, 0, length);
    }

    /* Password = 554 */
    public LogonEncoder password(final DirectBuffer value)
    {
        return password(value, 0, value.capacity());
    }

    /* Password = 554 */
    public LogonEncoder password(final byte[] value, final int offset, final int length)
    {
        password.wrap(value);
        passwordOffset = offset;
        passwordLength = length;
        return this;
    }

    /* Password = 554 */
    public LogonEncoder passwordAsCopy(final byte[] value, final int offset, final int length)
    {
        if (copyInto(password, value, offset, length))
        {
            passwordInternalBuffer = password.byteArray();
        }
        passwordOffset = 0;
        passwordLength = length;
        return this;
    }

    /* Password = 554 */
    public LogonEncoder password(final byte[] value, final int length)
    {
        return password(value, 0, length);
    }

    /* Password = 554 */
    public LogonEncoder password(final byte[] value)
    {
        return password(value, 0, value.length);
    }

    /* Password = 554 */
    public boolean hasPassword()
    {
        return passwordLength > 0;
    }

    /* Password = 554 */
    public MutableDirectBuffer password()
    {
        return password;
    }

    /* Password = 554 */
    public String passwordAsString()
    {
        return password.getStringWithoutLengthAscii(passwordOffset, passwordLength);
    }

    /* Password = 554 */
    public LogonEncoder password(final CharSequence value)
    {
        if (toBytes(value, password))
        {
            passwordInternalBuffer = password.byteArray();
        }
        passwordOffset = 0;
        passwordLength = value.length();
        return this;
    }

    /* Password = 554 */
    public LogonEncoder password(final AsciiSequenceView value)
    {
        final DirectBuffer buffer = value.buffer();
        if (buffer != null)
        {
            password.wrap(buffer);
            passwordOffset = value.offset();
            passwordLength = value.length();
        }
        return this;
    }

    /* Password = 554 */
    public LogonEncoder password(final char[] value)
    {
        return password(value, 0, value.length);
    }

    /* Password = 554 */
    public LogonEncoder password(final char[] value, final int length)
    {
        return password(value, 0, length);
    }

    /* Password = 554 */
    public LogonEncoder password(final char[] value, final int offset, final int length)
    {
        if (toBytes(value, password, offset, length))
        {
            passwordInternalBuffer = password.byteArray();
        }
        passwordOffset = 0;
        passwordLength = length;
        return this;
    }

    private int messageHandling;

    private boolean hasMessageHandling;

    public boolean hasMessageHandling()
    {
        return hasMessageHandling;
    }

    /* MessageHandling = 25035 */
    public LogonEncoder messageHandling(int value)
    {
        messageHandling = value;
        hasMessageHandling = true;
        return this;
    }

    /* MessageHandling = 25035 */
    public int messageHandling()
    {
        return messageHandling;
    }

    private char cancelOrdersOnDisconnect;

    private boolean hasCancelOrdersOnDisconnect;

    public boolean hasCancelOrdersOnDisconnect()
    {
        return hasCancelOrdersOnDisconnect;
    }

    /* CancelOrdersOnDisconnect = 8013 */
    public LogonEncoder cancelOrdersOnDisconnect(char value)
    {
        cancelOrdersOnDisconnect = value;
        hasCancelOrdersOnDisconnect = true;
        return this;
    }

    /* CancelOrdersOnDisconnect = 8013 */
    public char cancelOrdersOnDisconnect()
    {
        return cancelOrdersOnDisconnect;
    }

    public LogonEncoder cancelOnDisconnectType(final int value)
    {
        throw new UnsupportedOperationException();
    }
    public LogonEncoder cODTimeoutWindow(final int value)
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

    public long encode(final MutableAsciiBuffer buffer, final int offset)
    {
        final long startMessageResult = header.startMessage(buffer, offset);
        final int bodyStart = Encoder.offset(startMessageResult);
        int position = bodyStart + Encoder.length(startMessageResult);

        if (hasEncryptMethod)
        {
            buffer.putBytes(position, encryptMethodHeader, 0, encryptMethodHeaderLength);
            position += encryptMethodHeaderLength;
            position += buffer.putIntAscii(position, encryptMethod);
            buffer.putSeparator(position);
            position++;
        }
        else if (CODEC_VALIDATION_ENABLED)
        {
            throw new EncodingException("Missing Field: EncryptMethod");
        }

        if (hasHeartBtInt)
        {
            buffer.putBytes(position, heartBtIntHeader, 0, heartBtIntHeaderLength);
            position += heartBtIntHeaderLength;
            position += buffer.putIntAscii(position, heartBtInt);
            buffer.putSeparator(position);
            position++;
        }
        else if (CODEC_VALIDATION_ENABLED)
        {
            throw new EncodingException("Missing Field: HeartBtInt");
        }

        if (hasRawDataLength)
        {
            buffer.putBytes(position, rawDataLengthHeader, 0, rawDataLengthHeaderLength);
            position += rawDataLengthHeaderLength;
            position += buffer.putIntAscii(position, rawDataLength);
            buffer.putSeparator(position);
            position++;
        }

        if (hasRawData)
        {
            buffer.putBytes(position, rawDataHeader, 0, rawDataHeaderLength);
            position += rawDataHeaderLength;
            buffer.putBytes(position, rawData, 0, rawDataLength);
            position += rawDataLength;
            buffer.putSeparator(position);
            position++;
        }

        if (hasResetSeqNumFlag)
        {
            buffer.putBytes(position, resetSeqNumFlagHeader, 0, resetSeqNumFlagHeaderLength);
            position += resetSeqNumFlagHeaderLength;
            position += buffer.putBooleanAscii(position, resetSeqNumFlag);
            buffer.putSeparator(position);
            position++;
        }

        if (usernameLength > 0)
        {
            buffer.putBytes(position, usernameHeader, 0, usernameHeaderLength);
            position += usernameHeaderLength;
            buffer.putBytes(position, username, usernameOffset, usernameLength);
            position += usernameLength;
            buffer.putSeparator(position);
            position++;
        }

        if (passwordLength > 0)
        {
            buffer.putBytes(position, passwordHeader, 0, passwordHeaderLength);
            position += passwordHeaderLength;
            buffer.putBytes(position, password, passwordOffset, passwordLength);
            position += passwordLength;
            buffer.putSeparator(position);
            position++;
        }

        if (hasMessageHandling)
        {
            buffer.putBytes(position, messageHandlingHeader, 0, messageHandlingHeaderLength);
            position += messageHandlingHeaderLength;
            position += buffer.putIntAscii(position, messageHandling);
            buffer.putSeparator(position);
            position++;
        }

        if (hasCancelOrdersOnDisconnect)
        {
            buffer.putBytes(position, cancelOrdersOnDisconnectHeader, 0, cancelOrdersOnDisconnectHeaderLength);
            position += cancelOrdersOnDisconnectHeaderLength;
            position += buffer.putCharAscii(position, cancelOrdersOnDisconnect);
            buffer.putSeparator(position);
            position++;
        }
        else if (CODEC_VALIDATION_ENABLED)
        {
            throw new EncodingException("Missing Field: CancelOrdersOnDisconnect");
        }
        position += trailer.startTrailer(buffer, position);

        final int messageStart = header.finishHeader(buffer, bodyStart, position - bodyStart);
        return trailer.finishMessage(buffer, messageStart, position);
    }

    public void reset()
    {
        header.reset();
        trailer.reset();
        resetMessage();
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
        hasEncryptMethod = false;
    }

    public void resetHeartBtInt()
    {
        hasHeartBtInt = false;
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
        usernameLength = 0;
        username.wrap(usernameInternalBuffer);
    }

    public void resetPassword()
    {
        passwordLength = 0;
        password.wrap(passwordInternalBuffer);
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
        if (hasEncryptMethod())
        {
            indent(builder, level);
            builder.append("\"EncryptMethod\": \"");
            builder.append(encryptMethod);
            builder.append("\",\n");
        }

        if (hasHeartBtInt())
        {
            indent(builder, level);
            builder.append("\"HeartBtInt\": \"");
            builder.append(heartBtInt);
            builder.append("\",\n");
        }

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
            appendBuffer(builder, username, usernameOffset, usernameLength);
            builder.append("\",\n");
        }

        if (hasPassword())
        {
            indent(builder, level);
            builder.append("\"Password\": \"");
            appendBuffer(builder, password, passwordOffset, passwordLength);
            builder.append("\",\n");
        }

        if (hasMessageHandling())
        {
            indent(builder, level);
            builder.append("\"MessageHandling\": \"");
            builder.append(messageHandling);
            builder.append("\",\n");
        }

        if (hasCancelOrdersOnDisconnect())
        {
            indent(builder, level);
            builder.append("\"CancelOrdersOnDisconnect\": \"");
            builder.append(cancelOrdersOnDisconnect);
            builder.append("\",\n");
        }
        indent(builder, level - 1);
        builder.append("}");
        return builder;
    }

    public LogonEncoder copyTo(final Encoder encoder)
    {
        return copyTo((LogonEncoder)encoder);
    }

    public LogonEncoder copyTo(final LogonEncoder encoder)
    {
        encoder.reset();
        if (hasEncryptMethod())
        {
            encoder.encryptMethod(this.encryptMethod());
        }

        if (hasHeartBtInt())
        {
            encoder.heartBtInt(this.heartBtInt());
        }

        if (hasRawDataLength())
        {
            encoder.rawDataLength(this.rawDataLength());
        }

        if (hasRawData())
        {
            encoder.rawDataAsCopy(this.rawData(), 0, rawDataLength());
            encoder.rawDataLength(rawDataLength());
        }

        if (hasResetSeqNumFlag())
        {
            encoder.resetSeqNumFlag(this.resetSeqNumFlag());
        }

        if (hasUsername())
        {
            encoder.usernameAsCopy(username.byteArray(), 0, usernameLength);
        }

        if (hasPassword())
        {
            encoder.passwordAsCopy(password.byteArray(), 0, passwordLength);
        }

        if (hasMessageHandling())
        {
            encoder.messageHandling(this.messageHandling());
        }

        if (hasCancelOrdersOnDisconnect())
        {
            encoder.cancelOrdersOnDisconnect(this.cancelOrdersOnDisconnect());
        }
        return encoder;
    }

}
