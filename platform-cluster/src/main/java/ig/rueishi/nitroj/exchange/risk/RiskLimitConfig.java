package ig.rueishi.nitroj.exchange.risk;

import ig.rueishi.nitroj.exchange.cluster.RiskDecision;
import ig.rueishi.nitroj.exchange.common.Ids;
import ig.rueishi.nitroj.exchange.common.ScaledMath;
import ig.rueishi.nitroj.exchange.messages.Side;
import java.util.Arrays;
import java.util.Objects;

/**
 * V14 per-venue and aggregate limit configuration evaluated before child submit.
 *
 * <p>This class is intentionally separate from {@code RiskEngineImpl}. V14 adds
 * explicit configuration for venue-scoped and aggregate exposure envelopes, but
 * does not change the V13 {@code RiskEngine} decision order, snapshots, or hot
 * path semantics. Execution components that need the V14 envelope can evaluate
 * this immutable configuration before delegating to the existing risk engine.
 * The stateful position snapshot held here is primitive and deterministic; it
 * is not a synchronization mechanism and must be owned by the cluster thread.</p>
 *
 * <p>Aggregate membership is explicit. BTC-USD and BTCUSDT are not auto-netted
 * because USD and USDT basis trading is outside V14 scope; an aggregate limit
 * includes only the instrument IDs listed in its {@link AggregateLimit}.</p>
 */
public final class RiskLimitConfig {
    private final VenueLimit[] venueLimits;
    private final AggregateLimit[] aggregateLimits;
    private final long[][] netPositionByVenueInstrument;

    public RiskLimitConfig(final VenueLimit[] venueLimits, final AggregateLimit[] aggregateLimits) {
        Objects.requireNonNull(venueLimits, "venueLimits");
        Objects.requireNonNull(aggregateLimits, "aggregateLimits");
        this.venueLimits = venueLimits.clone();
        this.aggregateLimits = aggregateLimits.clone();
        for (VenueLimit limit : this.venueLimits) {
            Objects.requireNonNull(limit, "venueLimit");
        }
        for (AggregateLimit limit : this.aggregateLimits) {
            Objects.requireNonNull(limit, "aggregateLimit");
        }
        netPositionByVenueInstrument = new long[Ids.MAX_VENUES + 1][Ids.MAX_INSTRUMENTS + 1];
    }

    /**
     * Updates the deterministic position snapshot used by aggregate checks.
     */
    public void updatePositionSnapshot(final int venueId, final int instrumentId, final long netQtyScaled) {
        validateVenueInstrument(venueId, instrumentId);
        netPositionByVenueInstrument[venueId][instrumentId] = netQtyScaled;
    }

    /**
     * Evaluates V14 venue and aggregate envelopes before child order submission.
     *
     * <p>Returned {@link RiskDecision} singletons deliberately reuse the V13
     * reason-code surface so downstream rejection propagation remains unchanged.
     * Missing venue limits reject as max-notional because no configured notional
     * envelope exists for the child order.</p>
     */
    public RiskDecision preChildSubmitCheck(
        final int venueId,
        final int instrumentId,
        final byte side,
        final long priceScaled,
        final long qtyScaled) {

        validateVenueInstrument(venueId, instrumentId);
        if (priceScaled <= 0L || qtyScaled <= 0L) {
            return RiskDecision.REJECT_MAX_NOTIONAL;
        }

        final VenueLimit venueLimit = venueLimit(venueId, instrumentId);
        if (venueLimit == null) {
            return RiskDecision.REJECT_MAX_NOTIONAL;
        }

        final long signedQty = side == Side.BUY.value() ? qtyScaled : -qtyScaled;
        final long projectedVenuePosition = netPositionByVenueInstrument[venueId][instrumentId] + signedQty;
        final RiskDecision venuePositionDecision = positionDecision(
            projectedVenuePosition,
            venueLimit.maxLongPositionScaled(),
            venueLimit.maxShortPositionScaled());
        if (!venuePositionDecision.approved()) {
            return venuePositionDecision;
        }

        final long childNotional = ScaledMath.safeMulDiv(qtyScaled, priceScaled, Ids.SCALE);
        if (childNotional > venueLimit.maxNotionalScaled()) {
            return RiskDecision.REJECT_MAX_NOTIONAL;
        }

        for (AggregateLimit aggregateLimit : aggregateLimits) {
            if (!aggregateLimit.includes(instrumentId)) {
                continue;
            }
            final long projectedAggregatePosition = aggregatePosition(aggregateLimit) + signedQty;
            final RiskDecision aggregatePositionDecision = positionDecision(
                projectedAggregatePosition,
                aggregateLimit.maxLongPositionScaled(),
                aggregateLimit.maxShortPositionScaled());
            if (!aggregatePositionDecision.approved()) {
                return aggregatePositionDecision;
            }
            if (childNotional > aggregateLimit.maxNotionalScaled()) {
                return RiskDecision.REJECT_MAX_NOTIONAL;
            }
        }

        return RiskDecision.APPROVED;
    }

    public long aggregatePosition(final int assetId) {
        for (AggregateLimit aggregateLimit : aggregateLimits) {
            if (aggregateLimit.assetId() == assetId) {
                return aggregatePosition(aggregateLimit);
            }
        }
        return 0L;
    }

    public VenueLimit[] venueLimits() {
        return venueLimits.clone();
    }

    public AggregateLimit[] aggregateLimits() {
        return aggregateLimits.clone();
    }

    private long aggregatePosition(final AggregateLimit aggregateLimit) {
        long position = 0L;
        for (int venueId = 1; venueId < netPositionByVenueInstrument.length; venueId++) {
            for (int instrumentId : aggregateLimit.instrumentIds()) {
                position += netPositionByVenueInstrument[venueId][instrumentId];
            }
        }
        return position;
    }

    private VenueLimit venueLimit(final int venueId, final int instrumentId) {
        for (VenueLimit limit : venueLimits) {
            if (limit.venueId() == venueId && limit.instrumentId() == instrumentId) {
                return limit;
            }
        }
        return null;
    }

    private static RiskDecision positionDecision(
        final long projectedPosition,
        final long maxLongPositionScaled,
        final long maxShortPositionScaled) {
        if (projectedPosition > maxLongPositionScaled) {
            return RiskDecision.REJECT_MAX_LONG;
        }
        if (projectedPosition < -maxShortPositionScaled) {
            return RiskDecision.REJECT_MAX_SHORT;
        }
        return RiskDecision.APPROVED;
    }

    private static void validateVenueInstrument(final int venueId, final int instrumentId) {
        if (venueId <= 0 || venueId > Ids.MAX_VENUES) {
            throw new IllegalArgumentException("venueId out of range: " + venueId);
        }
        if (instrumentId <= 0 || instrumentId > Ids.MAX_INSTRUMENTS) {
            throw new IllegalArgumentException("instrumentId out of range: " + instrumentId);
        }
    }

    public record VenueLimit(
        int venueId,
        int instrumentId,
        long maxLongPositionScaled,
        long maxShortPositionScaled,
        long maxNotionalScaled) {
        public VenueLimit {
            validateVenueInstrument(venueId, instrumentId);
            if (maxLongPositionScaled <= 0L || maxShortPositionScaled <= 0L || maxNotionalScaled <= 0L) {
                throw new IllegalArgumentException("venue limits must be positive");
            }
        }
    }

    public record AggregateLimit(
        int assetId,
        int[] instrumentIds,
        long maxLongPositionScaled,
        long maxShortPositionScaled,
        long maxNotionalScaled) {
        public AggregateLimit {
            if (assetId <= 0) {
                throw new IllegalArgumentException("assetId must be positive");
            }
            Objects.requireNonNull(instrumentIds, "instrumentIds");
            if (instrumentIds.length == 0) {
                throw new IllegalArgumentException("aggregate instrumentIds must not be empty");
            }
            instrumentIds = instrumentIds.clone();
            for (int instrumentId : instrumentIds) {
                if (instrumentId <= 0 || instrumentId > Ids.MAX_INSTRUMENTS) {
                    throw new IllegalArgumentException("instrumentId out of range: " + instrumentId);
                }
            }
            Arrays.sort(instrumentIds);
            if (maxLongPositionScaled <= 0L || maxShortPositionScaled <= 0L || maxNotionalScaled <= 0L) {
                throw new IllegalArgumentException("aggregate limits must be positive");
            }
        }

        public int[] instrumentIds() {
            return instrumentIds.clone();
        }

        boolean includes(final int instrumentId) {
            return Arrays.binarySearch(instrumentIds, instrumentId) >= 0;
        }
    }
}
