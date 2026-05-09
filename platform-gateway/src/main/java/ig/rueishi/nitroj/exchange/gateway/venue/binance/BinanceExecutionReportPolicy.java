package ig.rueishi.nitroj.exchange.gateway.venue.binance;

import ig.rueishi.nitroj.exchange.messages.ExecType;

/**
 * Binance Spot execution-report normalization policy.
 */
public final class BinanceExecutionReportPolicy {
    public static final int REJECT_NONE = 0;
    public static final int REJECT_UNKNOWN_SYMBOL = 1;
    public static final int REJECT_EXCHANGE_CLOSED = 2;
    public static final int REJECT_ORDER_EXCEEDS_LIMIT = 3;
    public static final int REJECT_UNKNOWN_ORDER = 5;
    public static final int REJECT_DUPLICATE_ORDER = 6;
    public static final int REJECT_OTHER = 99;

    public ExecType mapExecType(final char fixExecType) {
        return switch (fixExecType) {
            case '0' -> ExecType.NEW;
            case '1' -> ExecType.PARTIAL_FILL;
            case '4' -> ExecType.CANCELED;
            case '5' -> ExecType.REPLACED;
            case '8' -> ExecType.REJECTED;
            case 'C' -> ExecType.EXPIRED;
            case 'F' -> ExecType.FILL;
            case 'I' -> ExecType.ORDER_STATUS;
            default -> ExecType.NULL_VAL;
        };
    }

    public boolean isTerminal(final ExecType execType, final long leavesQtyScaled) {
        return execType == ExecType.CANCELED
            || execType == ExecType.REPLACED
            || execType == ExecType.REJECTED
            || execType == ExecType.EXPIRED
            || (execType == ExecType.FILL && leavesQtyScaled == 0L);
    }

    public int mapRejectReason(final int ordRejReason) {
        return switch (ordRejReason) {
            case 0 -> REJECT_UNKNOWN_SYMBOL;
            case 2 -> REJECT_EXCHANGE_CLOSED;
            case 3 -> REJECT_ORDER_EXCEEDS_LIMIT;
            case 5 -> REJECT_UNKNOWN_ORDER;
            case 6 -> REJECT_DUPLICATE_ORDER;
            case 99 -> REJECT_OTHER;
            default -> ordRejReason < 0 ? REJECT_OTHER : ordRejReason;
        };
    }

    public boolean malformedExecutionReport(
        final boolean hasClOrdId,
        final boolean hasSymbol,
        final boolean hasOrderId,
        final boolean hasExecId,
        final ExecType execType) {

        return !hasClOrdId
            || !hasSymbol
            || !hasOrderId
            || !hasExecId
            || execType == ExecType.NULL_VAL;
    }
}
