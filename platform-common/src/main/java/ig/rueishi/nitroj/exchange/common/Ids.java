package ig.rueishi.nitroj.exchange.common;

/**
 * Shared numeric identifiers and scalar sentinels used across the platform.
 */
public final class Ids {
    public static final int VENUE_COINBASE = 1;
    public static final int VENUE_BINANCE = 2;
    public static final int VENUE_COINBASE_SANDBOX = 3;
    public static final int INSTRUMENT_BTC_USD = 1;
    public static final int INSTRUMENT_ETH_USD = 2;
    public static final int INSTRUMENT_BTC_USDT = 3;
    public static final int STRATEGY_MARKET_MAKING = 1;
    public static final int STRATEGY_ARB = 2;
    public static final int STRATEGY_ARB_HEDGE = 3;
    public static final int STRATEGY_INVENTORY_HEDGE = 4;
    public static final int MAX_VENUES = 16;
    public static final int MAX_INSTRUMENTS = 64;
    public static final int MAX_ORDERS_PER_WINDOW = 1000;
    public static final long INVALID_PRICE = Long.MIN_VALUE;
    public static final long INVALID_QTY = Long.MIN_VALUE;
    public static final long SCALE = 100_000_000L;

    private Ids() {
    }
}
