/* Generated SBE (Simple Binary Encoding) message codec. */
package ig.rueishi.nitroj.exchange.messages;

@SuppressWarnings("all")
public enum ParentIntentType
{
    IMMEDIATE_LIMIT((byte)1),

    QUOTE((byte)2),

    MULTI_LEG((byte)3),

    CANCEL_PARENT((byte)4),

    HEDGE((byte)5),

    /**
     * To be used to represent not present or null.
     */
    NULL_VAL((byte)-128);

    private final byte value;

    ParentIntentType(final byte value)
    {
        this.value = value;
    }

    /**
     * The raw encoded value in the Java type representation.
     *
     * @return the raw value encoded.
     */
    public byte value()
    {
        return value;
    }

    /**
     * Lookup the enum value representing the value.
     *
     * @param value encoded to be looked up.
     * @return the enum value representing the value.
     */
    public static ParentIntentType get(final byte value)
    {
        switch (value)
        {
            case 1: return IMMEDIATE_LIMIT;
            case 2: return QUOTE;
            case 3: return MULTI_LEG;
            case 4: return CANCEL_PARENT;
            case 5: return HEDGE;
            case -128: return NULL_VAL;
        }

        throw new IllegalArgumentException("Unknown value: " + value);
    }
}
