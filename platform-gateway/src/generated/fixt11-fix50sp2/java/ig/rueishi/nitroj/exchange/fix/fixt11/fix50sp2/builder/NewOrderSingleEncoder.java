/* Generated Fix Gateway message codec */
package ig.rueishi.nitroj.exchange.fix.fixt11.fix50sp2.builder;

import uk.co.real_logic.artio.dictionary.Generated;
import org.agrona.MutableDirectBuffer;
import org.agrona.AsciiSequenceView;
import static uk.co.real_logic.artio.dictionary.generation.CodecUtil.*;
import static uk.co.real_logic.artio.dictionary.SessionConstants.*;
import uk.co.real_logic.artio.builder.Encoder;
import ig.rueishi.nitroj.exchange.fix.fixt11.fix50sp2.builder.HeaderEncoder;
import ig.rueishi.nitroj.exchange.fix.fixt11.fix50sp2.builder.TrailerEncoder;
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
import ig.rueishi.nitroj.exchange.fix.fixt11.fix50sp2.*;

@Generated("uk.co.real_logic.artio")
public class NewOrderSingleEncoder implements Encoder
{
    public long messageType()
    {
        return 68L;
    }

    public NewOrderSingleEncoder()
    {
        header.msgType("D");
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

    private static final int accountHeaderLength = 2;
    private static final byte[] accountHeader = new byte[] {49, (byte) '='};

    private static final int clOrdIDHeaderLength = 3;
    private static final byte[] clOrdIDHeader = new byte[] {49, 49, (byte) '='};

    private static final int handlInstHeaderLength = 3;
    private static final byte[] handlInstHeader = new byte[] {50, 49, (byte) '='};

    private static final int symbolHeaderLength = 3;
    private static final byte[] symbolHeader = new byte[] {53, 53, (byte) '='};

    private static final int sideHeaderLength = 3;
    private static final byte[] sideHeader = new byte[] {53, 52, (byte) '='};

    private static final int transactTimeHeaderLength = 3;
    private static final byte[] transactTimeHeader = new byte[] {54, 48, (byte) '='};

    private static final int orderQtyHeaderLength = 3;
    private static final byte[] orderQtyHeader = new byte[] {51, 56, (byte) '='};

    private static final int ordTypeHeaderLength = 3;
    private static final byte[] ordTypeHeader = new byte[] {52, 48, (byte) '='};

    private static final int priceHeaderLength = 3;
    private static final byte[] priceHeader = new byte[] {52, 52, (byte) '='};

    private static final int timeInForceHeaderLength = 3;
    private static final byte[] timeInForceHeader = new byte[] {53, 57, (byte) '='};

    private static final int selfTradeTypeHeaderLength = 5;
    private static final byte[] selfTradeTypeHeader = new byte[] {55, 57, 50, 56, (byte) '='};

    private static final int selfTradePreventionModeHeaderLength = 6;
    private static final byte[] selfTradePreventionModeHeader = new byte[] {50, 53, 48, 48, 49, (byte) '='};

    private final MutableDirectBuffer account = new UnsafeBuffer();
    private byte[] accountInternalBuffer = account.byteArray();
    private int accountOffset = 0;
    private int accountLength = 0;

    /* Account = 1 */
    public NewOrderSingleEncoder account(final DirectBuffer value, final int offset, final int length)
    {
        account.wrap(value);
        accountOffset = offset;
        accountLength = length;
        return this;
    }

    /* Account = 1 */
    public NewOrderSingleEncoder account(final DirectBuffer value, final int length)
    {
        return account(value, 0, length);
    }

    /* Account = 1 */
    public NewOrderSingleEncoder account(final DirectBuffer value)
    {
        return account(value, 0, value.capacity());
    }

    /* Account = 1 */
    public NewOrderSingleEncoder account(final byte[] value, final int offset, final int length)
    {
        account.wrap(value);
        accountOffset = offset;
        accountLength = length;
        return this;
    }

    /* Account = 1 */
    public NewOrderSingleEncoder accountAsCopy(final byte[] value, final int offset, final int length)
    {
        if (copyInto(account, value, offset, length))
        {
            accountInternalBuffer = account.byteArray();
        }
        accountOffset = 0;
        accountLength = length;
        return this;
    }

    /* Account = 1 */
    public NewOrderSingleEncoder account(final byte[] value, final int length)
    {
        return account(value, 0, length);
    }

    /* Account = 1 */
    public NewOrderSingleEncoder account(final byte[] value)
    {
        return account(value, 0, value.length);
    }

    /* Account = 1 */
    public boolean hasAccount()
    {
        return accountLength > 0;
    }

    /* Account = 1 */
    public MutableDirectBuffer account()
    {
        return account;
    }

    /* Account = 1 */
    public String accountAsString()
    {
        return account.getStringWithoutLengthAscii(accountOffset, accountLength);
    }

    /* Account = 1 */
    public NewOrderSingleEncoder account(final CharSequence value)
    {
        if (toBytes(value, account))
        {
            accountInternalBuffer = account.byteArray();
        }
        accountOffset = 0;
        accountLength = value.length();
        return this;
    }

    /* Account = 1 */
    public NewOrderSingleEncoder account(final AsciiSequenceView value)
    {
        final DirectBuffer buffer = value.buffer();
        if (buffer != null)
        {
            account.wrap(buffer);
            accountOffset = value.offset();
            accountLength = value.length();
        }
        return this;
    }

    /* Account = 1 */
    public NewOrderSingleEncoder account(final char[] value)
    {
        return account(value, 0, value.length);
    }

    /* Account = 1 */
    public NewOrderSingleEncoder account(final char[] value, final int length)
    {
        return account(value, 0, length);
    }

    /* Account = 1 */
    public NewOrderSingleEncoder account(final char[] value, final int offset, final int length)
    {
        if (toBytes(value, account, offset, length))
        {
            accountInternalBuffer = account.byteArray();
        }
        accountOffset = 0;
        accountLength = length;
        return this;
    }

    private final MutableDirectBuffer clOrdID = new UnsafeBuffer();
    private byte[] clOrdIDInternalBuffer = clOrdID.byteArray();
    private int clOrdIDOffset = 0;
    private int clOrdIDLength = 0;

    /* ClOrdID = 11 */
    public NewOrderSingleEncoder clOrdID(final DirectBuffer value, final int offset, final int length)
    {
        clOrdID.wrap(value);
        clOrdIDOffset = offset;
        clOrdIDLength = length;
        return this;
    }

    /* ClOrdID = 11 */
    public NewOrderSingleEncoder clOrdID(final DirectBuffer value, final int length)
    {
        return clOrdID(value, 0, length);
    }

    /* ClOrdID = 11 */
    public NewOrderSingleEncoder clOrdID(final DirectBuffer value)
    {
        return clOrdID(value, 0, value.capacity());
    }

    /* ClOrdID = 11 */
    public NewOrderSingleEncoder clOrdID(final byte[] value, final int offset, final int length)
    {
        clOrdID.wrap(value);
        clOrdIDOffset = offset;
        clOrdIDLength = length;
        return this;
    }

    /* ClOrdID = 11 */
    public NewOrderSingleEncoder clOrdIDAsCopy(final byte[] value, final int offset, final int length)
    {
        if (copyInto(clOrdID, value, offset, length))
        {
            clOrdIDInternalBuffer = clOrdID.byteArray();
        }
        clOrdIDOffset = 0;
        clOrdIDLength = length;
        return this;
    }

    /* ClOrdID = 11 */
    public NewOrderSingleEncoder clOrdID(final byte[] value, final int length)
    {
        return clOrdID(value, 0, length);
    }

    /* ClOrdID = 11 */
    public NewOrderSingleEncoder clOrdID(final byte[] value)
    {
        return clOrdID(value, 0, value.length);
    }

    /* ClOrdID = 11 */
    public boolean hasClOrdID()
    {
        return clOrdIDLength > 0;
    }

    /* ClOrdID = 11 */
    public MutableDirectBuffer clOrdID()
    {
        return clOrdID;
    }

    /* ClOrdID = 11 */
    public String clOrdIDAsString()
    {
        return clOrdID.getStringWithoutLengthAscii(clOrdIDOffset, clOrdIDLength);
    }

    /* ClOrdID = 11 */
    public NewOrderSingleEncoder clOrdID(final CharSequence value)
    {
        if (toBytes(value, clOrdID))
        {
            clOrdIDInternalBuffer = clOrdID.byteArray();
        }
        clOrdIDOffset = 0;
        clOrdIDLength = value.length();
        return this;
    }

    /* ClOrdID = 11 */
    public NewOrderSingleEncoder clOrdID(final AsciiSequenceView value)
    {
        final DirectBuffer buffer = value.buffer();
        if (buffer != null)
        {
            clOrdID.wrap(buffer);
            clOrdIDOffset = value.offset();
            clOrdIDLength = value.length();
        }
        return this;
    }

    /* ClOrdID = 11 */
    public NewOrderSingleEncoder clOrdID(final char[] value)
    {
        return clOrdID(value, 0, value.length);
    }

    /* ClOrdID = 11 */
    public NewOrderSingleEncoder clOrdID(final char[] value, final int length)
    {
        return clOrdID(value, 0, length);
    }

    /* ClOrdID = 11 */
    public NewOrderSingleEncoder clOrdID(final char[] value, final int offset, final int length)
    {
        if (toBytes(value, clOrdID, offset, length))
        {
            clOrdIDInternalBuffer = clOrdID.byteArray();
        }
        clOrdIDOffset = 0;
        clOrdIDLength = length;
        return this;
    }

    private char handlInst;

    private boolean hasHandlInst;

    public boolean hasHandlInst()
    {
        return hasHandlInst;
    }

    /* HandlInst = 21 */
    public NewOrderSingleEncoder handlInst(char value)
    {
        handlInst = value;
        hasHandlInst = true;
        return this;
    }

    /* HandlInst = 21 */
    public char handlInst()
    {
        return handlInst;
    }

    private final MutableDirectBuffer symbol = new UnsafeBuffer();
    private byte[] symbolInternalBuffer = symbol.byteArray();
    private int symbolOffset = 0;
    private int symbolLength = 0;

    /* Symbol = 55 */
    public NewOrderSingleEncoder symbol(final DirectBuffer value, final int offset, final int length)
    {
        symbol.wrap(value);
        symbolOffset = offset;
        symbolLength = length;
        return this;
    }

    /* Symbol = 55 */
    public NewOrderSingleEncoder symbol(final DirectBuffer value, final int length)
    {
        return symbol(value, 0, length);
    }

    /* Symbol = 55 */
    public NewOrderSingleEncoder symbol(final DirectBuffer value)
    {
        return symbol(value, 0, value.capacity());
    }

    /* Symbol = 55 */
    public NewOrderSingleEncoder symbol(final byte[] value, final int offset, final int length)
    {
        symbol.wrap(value);
        symbolOffset = offset;
        symbolLength = length;
        return this;
    }

    /* Symbol = 55 */
    public NewOrderSingleEncoder symbolAsCopy(final byte[] value, final int offset, final int length)
    {
        if (copyInto(symbol, value, offset, length))
        {
            symbolInternalBuffer = symbol.byteArray();
        }
        symbolOffset = 0;
        symbolLength = length;
        return this;
    }

    /* Symbol = 55 */
    public NewOrderSingleEncoder symbol(final byte[] value, final int length)
    {
        return symbol(value, 0, length);
    }

    /* Symbol = 55 */
    public NewOrderSingleEncoder symbol(final byte[] value)
    {
        return symbol(value, 0, value.length);
    }

    /* Symbol = 55 */
    public boolean hasSymbol()
    {
        return symbolLength > 0;
    }

    /* Symbol = 55 */
    public MutableDirectBuffer symbol()
    {
        return symbol;
    }

    /* Symbol = 55 */
    public String symbolAsString()
    {
        return symbol.getStringWithoutLengthAscii(symbolOffset, symbolLength);
    }

    /* Symbol = 55 */
    public NewOrderSingleEncoder symbol(final CharSequence value)
    {
        if (toBytes(value, symbol))
        {
            symbolInternalBuffer = symbol.byteArray();
        }
        symbolOffset = 0;
        symbolLength = value.length();
        return this;
    }

    /* Symbol = 55 */
    public NewOrderSingleEncoder symbol(final AsciiSequenceView value)
    {
        final DirectBuffer buffer = value.buffer();
        if (buffer != null)
        {
            symbol.wrap(buffer);
            symbolOffset = value.offset();
            symbolLength = value.length();
        }
        return this;
    }

    /* Symbol = 55 */
    public NewOrderSingleEncoder symbol(final char[] value)
    {
        return symbol(value, 0, value.length);
    }

    /* Symbol = 55 */
    public NewOrderSingleEncoder symbol(final char[] value, final int length)
    {
        return symbol(value, 0, length);
    }

    /* Symbol = 55 */
    public NewOrderSingleEncoder symbol(final char[] value, final int offset, final int length)
    {
        if (toBytes(value, symbol, offset, length))
        {
            symbolInternalBuffer = symbol.byteArray();
        }
        symbolOffset = 0;
        symbolLength = length;
        return this;
    }

    private char side;

    private boolean hasSide;

    public boolean hasSide()
    {
        return hasSide;
    }

    /* Side = 54 */
    public NewOrderSingleEncoder side(char value)
    {
        side = value;
        hasSide = true;
        return this;
    }

    /* Side = 54 */
    public char side()
    {
        return side;
    }

    private final MutableDirectBuffer transactTime = new UnsafeBuffer();
    private byte[] transactTimeInternalBuffer = transactTime.byteArray();
    private int transactTimeOffset = 0;
    private int transactTimeLength = 0;

    /* TransactTime = 60 */
    public NewOrderSingleEncoder transactTime(final DirectBuffer value, final int offset, final int length)
    {
        transactTime.wrap(value);
        transactTimeOffset = offset;
        transactTimeLength = length;
        return this;
    }

    /* TransactTime = 60 */
    public NewOrderSingleEncoder transactTime(final DirectBuffer value, final int length)
    {
        return transactTime(value, 0, length);
    }

    /* TransactTime = 60 */
    public NewOrderSingleEncoder transactTime(final DirectBuffer value)
    {
        return transactTime(value, 0, value.capacity());
    }

    /* TransactTime = 60 */
    public NewOrderSingleEncoder transactTime(final byte[] value, final int offset, final int length)
    {
        transactTime.wrap(value);
        transactTimeOffset = offset;
        transactTimeLength = length;
        return this;
    }

    /* TransactTime = 60 */
    public NewOrderSingleEncoder transactTimeAsCopy(final byte[] value, final int offset, final int length)
    {
        if (copyInto(transactTime, value, offset, length))
        {
            transactTimeInternalBuffer = transactTime.byteArray();
        }
        transactTimeOffset = 0;
        transactTimeLength = length;
        return this;
    }

    /* TransactTime = 60 */
    public NewOrderSingleEncoder transactTime(final byte[] value, final int length)
    {
        return transactTime(value, 0, length);
    }

    /* TransactTime = 60 */
    public NewOrderSingleEncoder transactTime(final byte[] value)
    {
        return transactTime(value, 0, value.length);
    }

    /* TransactTime = 60 */
    public boolean hasTransactTime()
    {
        return transactTimeLength > 0;
    }

    /* TransactTime = 60 */
    public MutableDirectBuffer transactTime()
    {
        return transactTime;
    }

    /* TransactTime = 60 */
    public String transactTimeAsString()
    {
        return transactTime.getStringWithoutLengthAscii(transactTimeOffset, transactTimeLength);
    }

    private final DecimalFloat orderQty = new DecimalFloat();

    private boolean hasOrderQty;

    public boolean hasOrderQty()
    {
        return hasOrderQty;
    }

    /* OrderQty = 38 */
    public NewOrderSingleEncoder orderQty(ReadOnlyDecimalFloat value)
    {
        orderQty.set(value);
        hasOrderQty = true;
        return this;
    }

    /* OrderQty = 38 */
    public NewOrderSingleEncoder orderQty(long value, int scale)
    {
        orderQty.set(value, scale);
        hasOrderQty = true;
        return this;
    }

    /* OrderQty = 38 */
    public DecimalFloat orderQty()
    {
        return orderQty;
    }

    private char ordType;

    private boolean hasOrdType;

    public boolean hasOrdType()
    {
        return hasOrdType;
    }

    /* OrdType = 40 */
    public NewOrderSingleEncoder ordType(char value)
    {
        ordType = value;
        hasOrdType = true;
        return this;
    }

    /* OrdType = 40 */
    public char ordType()
    {
        return ordType;
    }

    private final DecimalFloat price = new DecimalFloat();

    private boolean hasPrice;

    public boolean hasPrice()
    {
        return hasPrice;
    }

    /* Price = 44 */
    public NewOrderSingleEncoder price(ReadOnlyDecimalFloat value)
    {
        price.set(value);
        hasPrice = true;
        return this;
    }

    /* Price = 44 */
    public NewOrderSingleEncoder price(long value, int scale)
    {
        price.set(value, scale);
        hasPrice = true;
        return this;
    }

    /* Price = 44 */
    public DecimalFloat price()
    {
        return price;
    }

    private char timeInForce;

    private boolean hasTimeInForce;

    public boolean hasTimeInForce()
    {
        return hasTimeInForce;
    }

    /* TimeInForce = 59 */
    public NewOrderSingleEncoder timeInForce(char value)
    {
        timeInForce = value;
        hasTimeInForce = true;
        return this;
    }

    /* TimeInForce = 59 */
    public char timeInForce()
    {
        return timeInForce;
    }

    private char selfTradeType;

    private boolean hasSelfTradeType;

    public boolean hasSelfTradeType()
    {
        return hasSelfTradeType;
    }

    /* SelfTradeType = 7928 */
    public NewOrderSingleEncoder selfTradeType(char value)
    {
        selfTradeType = value;
        hasSelfTradeType = true;
        return this;
    }

    /* SelfTradeType = 7928 */
    public char selfTradeType()
    {
        return selfTradeType;
    }

    private char selfTradePreventionMode;

    private boolean hasSelfTradePreventionMode;

    public boolean hasSelfTradePreventionMode()
    {
        return hasSelfTradePreventionMode;
    }

    /* SelfTradePreventionMode = 25001 */
    public NewOrderSingleEncoder selfTradePreventionMode(char value)
    {
        selfTradePreventionMode = value;
        hasSelfTradePreventionMode = true;
        return this;
    }

    /* SelfTradePreventionMode = 25001 */
    public char selfTradePreventionMode()
    {
        return selfTradePreventionMode;
    }

    public long encode(final MutableAsciiBuffer buffer, final int offset)
    {
        final long startMessageResult = header.startMessage(buffer, offset);
        final int bodyStart = Encoder.offset(startMessageResult);
        int position = bodyStart + Encoder.length(startMessageResult);

        if (accountLength > 0)
        {
            buffer.putBytes(position, accountHeader, 0, accountHeaderLength);
            position += accountHeaderLength;
            buffer.putBytes(position, account, accountOffset, accountLength);
            position += accountLength;
            buffer.putSeparator(position);
            position++;
        }

        if (clOrdIDLength > 0)
        {
            buffer.putBytes(position, clOrdIDHeader, 0, clOrdIDHeaderLength);
            position += clOrdIDHeaderLength;
            buffer.putBytes(position, clOrdID, clOrdIDOffset, clOrdIDLength);
            position += clOrdIDLength;
            buffer.putSeparator(position);
            position++;
        }
        else if (CODEC_VALIDATION_ENABLED)
        {
            throw new EncodingException("Missing Field: ClOrdID");
        }

        if (hasHandlInst)
        {
            buffer.putBytes(position, handlInstHeader, 0, handlInstHeaderLength);
            position += handlInstHeaderLength;
            position += buffer.putCharAscii(position, handlInst);
            buffer.putSeparator(position);
            position++;
        }
        else if (CODEC_VALIDATION_ENABLED)
        {
            throw new EncodingException("Missing Field: HandlInst");
        }

        if (symbolLength > 0)
        {
            buffer.putBytes(position, symbolHeader, 0, symbolHeaderLength);
            position += symbolHeaderLength;
            buffer.putBytes(position, symbol, symbolOffset, symbolLength);
            position += symbolLength;
            buffer.putSeparator(position);
            position++;
        }
        else if (CODEC_VALIDATION_ENABLED)
        {
            throw new EncodingException("Missing Field: Symbol");
        }

        if (hasSide)
        {
            buffer.putBytes(position, sideHeader, 0, sideHeaderLength);
            position += sideHeaderLength;
            position += buffer.putCharAscii(position, side);
            buffer.putSeparator(position);
            position++;
        }
        else if (CODEC_VALIDATION_ENABLED)
        {
            throw new EncodingException("Missing Field: Side");
        }

        if (transactTimeLength > 0)
        {
            buffer.putBytes(position, transactTimeHeader, 0, transactTimeHeaderLength);
            position += transactTimeHeaderLength;
            buffer.putBytes(position, transactTime, transactTimeOffset, transactTimeLength);
            position += transactTimeLength;
            buffer.putSeparator(position);
            position++;
        }
        else if (CODEC_VALIDATION_ENABLED)
        {
            throw new EncodingException("Missing Field: TransactTime");
        }

        if (hasOrderQty)
        {
            buffer.putBytes(position, orderQtyHeader, 0, orderQtyHeaderLength);
            position += orderQtyHeaderLength;
            position += buffer.putFloatAscii(position, orderQty);
            buffer.putSeparator(position);
            position++;
        }
        else if (CODEC_VALIDATION_ENABLED)
        {
            throw new EncodingException("Missing Field: OrderQty");
        }

        if (hasOrdType)
        {
            buffer.putBytes(position, ordTypeHeader, 0, ordTypeHeaderLength);
            position += ordTypeHeaderLength;
            position += buffer.putCharAscii(position, ordType);
            buffer.putSeparator(position);
            position++;
        }
        else if (CODEC_VALIDATION_ENABLED)
        {
            throw new EncodingException("Missing Field: OrdType");
        }

        if (hasPrice)
        {
            buffer.putBytes(position, priceHeader, 0, priceHeaderLength);
            position += priceHeaderLength;
            position += buffer.putFloatAscii(position, price);
            buffer.putSeparator(position);
            position++;
        }

        if (hasTimeInForce)
        {
            buffer.putBytes(position, timeInForceHeader, 0, timeInForceHeaderLength);
            position += timeInForceHeaderLength;
            position += buffer.putCharAscii(position, timeInForce);
            buffer.putSeparator(position);
            position++;
        }

        if (hasSelfTradeType)
        {
            buffer.putBytes(position, selfTradeTypeHeader, 0, selfTradeTypeHeaderLength);
            position += selfTradeTypeHeaderLength;
            position += buffer.putCharAscii(position, selfTradeType);
            buffer.putSeparator(position);
            position++;
        }

        if (hasSelfTradePreventionMode)
        {
            buffer.putBytes(position, selfTradePreventionModeHeader, 0, selfTradePreventionModeHeaderLength);
            position += selfTradePreventionModeHeaderLength;
            position += buffer.putCharAscii(position, selfTradePreventionMode);
            buffer.putSeparator(position);
            position++;
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
        this.resetAccount();
        this.resetClOrdID();
        this.resetHandlInst();
        this.resetSymbol();
        this.resetSide();
        this.resetTransactTime();
        this.resetOrderQty();
        this.resetOrdType();
        this.resetPrice();
        this.resetTimeInForce();
        this.resetSelfTradeType();
        this.resetSelfTradePreventionMode();
    }

    public void resetAccount()
    {
        accountLength = 0;
        account.wrap(accountInternalBuffer);
    }

    public void resetClOrdID()
    {
        clOrdIDLength = 0;
        clOrdID.wrap(clOrdIDInternalBuffer);
    }

    public void resetHandlInst()
    {
        handlInst = MISSING_CHAR;
    }

    public void resetSymbol()
    {
        symbolLength = 0;
        symbol.wrap(symbolInternalBuffer);
    }

    public void resetSide()
    {
        side = MISSING_CHAR;
    }

    public void resetTransactTime()
    {
        transactTimeLength = 0;
        transactTime.wrap(transactTimeInternalBuffer);
    }

    public void resetOrderQty()
    {
        hasOrderQty = false;
    }

    public void resetOrdType()
    {
        ordType = MISSING_CHAR;
    }

    public void resetPrice()
    {
        hasPrice = false;
    }

    public void resetTimeInForce()
    {
        hasTimeInForce = false;
    }

    public void resetSelfTradeType()
    {
        hasSelfTradeType = false;
    }

    public void resetSelfTradePreventionMode()
    {
        hasSelfTradePreventionMode = false;
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
        builder.append("\"MessageName\": \"NewOrderSingle\",\n");
        builder.append("  \"header\": ");
        header.appendTo(builder, level + 1);
        builder.append("\n");
        if (hasAccount())
        {
            indent(builder, level);
            builder.append("\"Account\": \"");
            appendBuffer(builder, account, accountOffset, accountLength);
            builder.append("\",\n");
        }

        if (hasClOrdID())
        {
            indent(builder, level);
            builder.append("\"ClOrdID\": \"");
            appendBuffer(builder, clOrdID, clOrdIDOffset, clOrdIDLength);
            builder.append("\",\n");
        }

        if (hasHandlInst())
        {
            indent(builder, level);
            builder.append("\"HandlInst\": \"");
            builder.append(handlInst);
            builder.append("\",\n");
        }

        if (hasSymbol())
        {
            indent(builder, level);
            builder.append("\"Symbol\": \"");
            appendBuffer(builder, symbol, symbolOffset, symbolLength);
            builder.append("\",\n");
        }

        if (hasSide())
        {
            indent(builder, level);
            builder.append("\"Side\": \"");
            builder.append(side);
            builder.append("\",\n");
        }

        if (hasTransactTime())
        {
            indent(builder, level);
            builder.append("\"TransactTime\": \"");
            appendBuffer(builder, transactTime, transactTimeOffset, transactTimeLength);
            builder.append("\",\n");
        }

        if (hasOrderQty())
        {
            indent(builder, level);
            builder.append("\"OrderQty\": \"");
            orderQty.appendTo(builder);
            builder.append("\",\n");
        }

        if (hasOrdType())
        {
            indent(builder, level);
            builder.append("\"OrdType\": \"");
            builder.append(ordType);
            builder.append("\",\n");
        }

        if (hasPrice())
        {
            indent(builder, level);
            builder.append("\"Price\": \"");
            price.appendTo(builder);
            builder.append("\",\n");
        }

        if (hasTimeInForce())
        {
            indent(builder, level);
            builder.append("\"TimeInForce\": \"");
            builder.append(timeInForce);
            builder.append("\",\n");
        }

        if (hasSelfTradeType())
        {
            indent(builder, level);
            builder.append("\"SelfTradeType\": \"");
            builder.append(selfTradeType);
            builder.append("\",\n");
        }

        if (hasSelfTradePreventionMode())
        {
            indent(builder, level);
            builder.append("\"SelfTradePreventionMode\": \"");
            builder.append(selfTradePreventionMode);
            builder.append("\",\n");
        }
        indent(builder, level - 1);
        builder.append("}");
        return builder;
    }

    public NewOrderSingleEncoder copyTo(final Encoder encoder)
    {
        return copyTo((NewOrderSingleEncoder)encoder);
    }

    public NewOrderSingleEncoder copyTo(final NewOrderSingleEncoder encoder)
    {
        encoder.reset();
        if (hasAccount())
        {
            encoder.accountAsCopy(account.byteArray(), 0, accountLength);
        }

        if (hasClOrdID())
        {
            encoder.clOrdIDAsCopy(clOrdID.byteArray(), 0, clOrdIDLength);
        }

        if (hasHandlInst())
        {
            encoder.handlInst(this.handlInst());
        }

        if (hasSymbol())
        {
            encoder.symbolAsCopy(symbol.byteArray(), 0, symbolLength);
        }

        if (hasSide())
        {
            encoder.side(this.side());
        }

        if (hasTransactTime())
        {
            encoder.transactTimeAsCopy(transactTime.byteArray(), 0, transactTimeLength);
        }

        if (hasOrderQty())
        {
            encoder.orderQty(this.orderQty());
        }

        if (hasOrdType())
        {
            encoder.ordType(this.ordType());
        }

        if (hasPrice())
        {
            encoder.price(this.price());
        }

        if (hasTimeInForce())
        {
            encoder.timeInForce(this.timeInForce());
        }

        if (hasSelfTradeType())
        {
            encoder.selfTradeType(this.selfTradeType());
        }

        if (hasSelfTradePreventionMode())
        {
            encoder.selfTradePreventionMode(this.selfTradePreventionMode());
        }
        return encoder;
    }

}
