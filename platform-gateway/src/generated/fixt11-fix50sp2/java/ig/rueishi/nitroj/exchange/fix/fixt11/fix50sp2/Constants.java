/* Generated Fix Gateway message codec */
package ig.rueishi.nitroj.exchange.fix.fixt11.fix50sp2;

import org.agrona.collections.IntHashSet;
import uk.co.real_logic.artio.dictionary.CharArraySet;
import uk.co.real_logic.artio.dictionary.Generated;

@Generated("uk.co.real_logic.artio")
public class Constants
{

    public static String VERSION = "FIX.5.0";
    public static char[] VERSION_CHARS = VERSION.toCharArray();

    public static final String LOGON_MESSAGE_AS_STR = "A";
    public static final long LOGON_MESSAGE = 65L;

    public static final String NEW_ORDER_SINGLE_MESSAGE_AS_STR = "D";
    public static final long NEW_ORDER_SINGLE_MESSAGE = 68L;

    public static final String ORDER_CANCEL_REQUEST_MESSAGE_AS_STR = "F";
    public static final long ORDER_CANCEL_REQUEST_MESSAGE = 70L;

    public static final String ORDER_CANCEL_REPLACE_REQUEST_MESSAGE_AS_STR = "G";
    public static final long ORDER_CANCEL_REPLACE_REQUEST_MESSAGE = 71L;

    public static final String ORDER_STATUS_REQUEST_MESSAGE_AS_STR = "H";
    public static final long ORDER_STATUS_REQUEST_MESSAGE = 72L;

    public static final int BEGIN_STRING = 8;

    public static final int ACCOUNT = 1;

    public static final int SENDER_COMP_ID = 49;

    public static final int ORIG_SENDING_TIME = 122;

    public static final int POSS_DUP_FLAG = 43;

    public static final int ORDER_QTY = 38;

    public static final int SYMBOL = 55;

    public static final int TARGET_COMP_ID = 56;

    public static final int TARGET_LOCATION_ID = 143;

    public static final int ORDER_ID = 37;

    public static final int TRANSACT_TIME = 60;

    public static final int DELIVER_TO_COMP_ID = 128;

    public static final int SIDE = 54;

    public static final int SELF_TRADE_TYPE = 7928;

    public static final int SENDING_TIME = 52;

    public static final int SELF_TRADE_PREVENTION_MODE = 25001;

    public static final int TIME_IN_FORCE = 59;

    public static final int HANDL_INST = 21;

    public static final int BODY_LENGTH = 9;

    public static final int PASSWORD = 554;

    public static final int RAW_DATA = 96;

    public static final int ENCRYPT_METHOD = 98;

    public static final int SENDER_SUB_ID = 50;

    public static final int ORD_TYPE = 40;

    public static final int SECURE_DATA_LENGTH = 91;

    public static final int RAW_DATA_LENGTH = 95;

    public static final int CL_ORD_ID = 11;

    public static final int TARGET_SUB_ID = 57;

    public static final int MSG_TYPE = 35;

    public static final int MSG_SEQ_NUM = 34;

    public static final int DELIVER_TO_SUB_ID = 129;

    public static final int SECURE_DATA = 92;

    public static final int GAP_FILL_FLAG = 123;

    public static final int PRICE = 44;

    public static final int USERNAME = 553;

    public static final int CHECK_SUM = 10;

    public static final int SENDER_LOCATION_ID = 142;

    public static final int LAST_MSG_SEQ_NUM_PROCESSED = 369;

    public static final int MESSAGE_HANDLING = 25035;

    public static final int RESET_SEQ_NUM_FLAG = 141;

    public static final int CANCEL_ORDERS_ON_DISCONNECT = 8013;

    public static final int HEART_BT_INT = 108;

    public static final int ORIG_CL_ORD_ID = 41;

    public static final int POSS_RESEND = 97;

    public static final IntHashSet ALL_FIELDS = new IntHashSet(88);
    static
    {
        ALL_FIELDS.add(Constants.BEGIN_STRING);
        ALL_FIELDS.add(Constants.ACCOUNT);
        ALL_FIELDS.add(Constants.SENDER_COMP_ID);
        ALL_FIELDS.add(Constants.ORIG_SENDING_TIME);
        ALL_FIELDS.add(Constants.POSS_DUP_FLAG);
        ALL_FIELDS.add(Constants.ORDER_QTY);
        ALL_FIELDS.add(Constants.SYMBOL);
        ALL_FIELDS.add(Constants.TARGET_COMP_ID);
        ALL_FIELDS.add(Constants.TARGET_LOCATION_ID);
        ALL_FIELDS.add(Constants.ORDER_ID);
        ALL_FIELDS.add(Constants.TRANSACT_TIME);
        ALL_FIELDS.add(Constants.DELIVER_TO_COMP_ID);
        ALL_FIELDS.add(Constants.SIDE);
        ALL_FIELDS.add(Constants.SELF_TRADE_TYPE);
        ALL_FIELDS.add(Constants.SENDING_TIME);
        ALL_FIELDS.add(Constants.SELF_TRADE_PREVENTION_MODE);
        ALL_FIELDS.add(Constants.TIME_IN_FORCE);
        ALL_FIELDS.add(Constants.HANDL_INST);
        ALL_FIELDS.add(Constants.BODY_LENGTH);
        ALL_FIELDS.add(Constants.PASSWORD);
        ALL_FIELDS.add(Constants.RAW_DATA);
        ALL_FIELDS.add(Constants.ENCRYPT_METHOD);
        ALL_FIELDS.add(Constants.SENDER_SUB_ID);
        ALL_FIELDS.add(Constants.ORD_TYPE);
        ALL_FIELDS.add(Constants.SECURE_DATA_LENGTH);
        ALL_FIELDS.add(Constants.RAW_DATA_LENGTH);
        ALL_FIELDS.add(Constants.CL_ORD_ID);
        ALL_FIELDS.add(Constants.TARGET_SUB_ID);
        ALL_FIELDS.add(Constants.MSG_TYPE);
        ALL_FIELDS.add(Constants.MSG_SEQ_NUM);
        ALL_FIELDS.add(Constants.DELIVER_TO_SUB_ID);
        ALL_FIELDS.add(Constants.SECURE_DATA);
        ALL_FIELDS.add(Constants.GAP_FILL_FLAG);
        ALL_FIELDS.add(Constants.PRICE);
        ALL_FIELDS.add(Constants.USERNAME);
        ALL_FIELDS.add(Constants.CHECK_SUM);
        ALL_FIELDS.add(Constants.SENDER_LOCATION_ID);
        ALL_FIELDS.add(Constants.LAST_MSG_SEQ_NUM_PROCESSED);
        ALL_FIELDS.add(Constants.MESSAGE_HANDLING);
        ALL_FIELDS.add(Constants.RESET_SEQ_NUM_FLAG);
        ALL_FIELDS.add(Constants.CANCEL_ORDERS_ON_DISCONNECT);
        ALL_FIELDS.add(Constants.HEART_BT_INT);
        ALL_FIELDS.add(Constants.ORIG_CL_ORD_ID);
        ALL_FIELDS.add(Constants.POSS_RESEND);
    }

}
