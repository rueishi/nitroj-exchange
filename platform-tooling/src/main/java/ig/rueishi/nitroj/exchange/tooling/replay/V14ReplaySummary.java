package ig.rueishi.nitroj.exchange.tooling.replay;

/**
 * Deterministic replay evidence labels for V14 venue-indifferent scenarios.
 *
 * <p>The tooling layer uses these stable labels when archiving replay summaries.
 * They deliberately describe decoded behavior rather than raw binary payloads so
 * replay evidence can compare equivalent outbound FIX command sequences across
 * Coinbase and Binance without depending on venue-specific wire tags.</p>
 */
public final class V14ReplaySummary {
    public static final String HEDGE_PARALLEL = "hedge.parallel";
    public static final String HEDGE_SOR_RESLICE = "hedge.sor.reslice";
    public static final String CROSS_VENUE_MIXED_PRECISION = "crossVenue.mixedPrecision";
    public static final String PARALLEL_MARKET_MAKING = "marketMaking.parallelVenues";
    public static final String SNAPSHOT_PARENT_STATE = "snapshot.parentState";
    public static final String CAPACITY_COUNTERS = "capacity.counters";
    public static final String OUTBOUND_FIX_EQUIVALENT = "outboundFix.equivalentDecodedCommands";

    private V14ReplaySummary() {
    }
}
