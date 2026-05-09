package ig.rueishi.nitroj.exchange.strategy;

import ig.rueishi.nitroj.exchange.cluster.InternalMarketView;
import ig.rueishi.nitroj.exchange.cluster.PortfolioEngine;
import ig.rueishi.nitroj.exchange.cluster.RecoveryCoordinator;
import ig.rueishi.nitroj.exchange.cluster.RiskEngine;
import ig.rueishi.nitroj.exchange.common.ExecutionStrategyIds;
import ig.rueishi.nitroj.exchange.common.Ids;
import ig.rueishi.nitroj.exchange.common.MarketMakingConfig;
import ig.rueishi.nitroj.exchange.common.ScaledMath;
import ig.rueishi.nitroj.exchange.messages.EntryType;
import ig.rueishi.nitroj.exchange.messages.ExecutionEventDecoder;
import ig.rueishi.nitroj.exchange.messages.MarketDataEventDecoder;
import ig.rueishi.nitroj.exchange.messages.MessageHeaderDecoder;
import ig.rueishi.nitroj.exchange.messages.MessageHeaderEncoder;
import ig.rueishi.nitroj.exchange.messages.ParentIntentType;
import ig.rueishi.nitroj.exchange.messages.ParentOrderIntentDecoder;
import ig.rueishi.nitroj.exchange.messages.ParentOrderIntentEncoder;
import ig.rueishi.nitroj.exchange.messages.ParentOrderTerminalDecoder;
import ig.rueishi.nitroj.exchange.messages.PriceMode;
import ig.rueishi.nitroj.exchange.messages.Side;
import ig.rueishi.nitroj.exchange.messages.TimeInForce;
import ig.rueishi.nitroj.exchange.execution.ExecutionStrategyEngine;
import ig.rueishi.nitroj.exchange.execution.ParentOrderState;
import io.aeron.cluster.service.Cluster;
import java.util.Objects;
import org.agrona.concurrent.UnsafeBuffer;

/**
 * Deterministic two-sided market-making strategy.
 *
 * <p>The strategy reads the cluster-local L2 book, applies inventory skew,
 * rounds prices and sizes to configured tick/lot increments, cancels stale live
 * quotes, and publishes replacement orders through the Aeron cluster facade.
 * It is initialized by StrategyEngine with a {@link StrategyContext}; all
 * mutable state is primitive and instance-local so replay and leader failover
 * remain deterministic.</p>
 *
 * <p>The implementation follows the TASK-030 fixed-point algorithm. It never
 * uses floating point in the quote path, uses cluster log position as the base
 * for client order ids, and suppresses quoting when risk, recovery, stale book,
 * wide-spread, rejection-cooldown, or kill-switch conditions are active.</p>
 */
public final class MarketMakingStrategy implements Strategy {
    private static final long WIDE_SPREAD_COOLDOWN_MICROS = 5_000_000L;
    private static final long REJECTION_COOLDOWN_MICROS = 5_000_000L;
    private static final int COMMAND_BUFFER_BYTES = 512;

    private final MarketMakingConfig config;
    private final int[] subscribedInstrumentIds;
    private final int[] activeVenueIds;
    private InternalMarketView marketView;
    private RiskEngine riskEngine;
    private RecoveryCoordinator recoveryCoordinator;
    private PortfolioEngine portfolioEngine;
    private ExecutionStrategyEngine executionEngine;
    private Cluster cluster;
    private UnsafeBuffer egressBuffer;
    private MessageHeaderEncoder headerEncoder;
    private final ParentOrderIntentEncoder parentIntentEncoder = new ParentOrderIntentEncoder();
    private final ParentOrderIntentDecoder parentIntentDecoder = new ParentOrderIntentDecoder();
    private final MessageHeaderDecoder headerDecoder = new MessageHeaderDecoder();
    private long lastQuotedMid;
    private long lastBidPrice;
    private long lastAskPrice;
    private long lastBidSize;
    private long lastAskSize;
    private long lastQuoteTimeCluster;
    private long liveBidClOrdId;
    private long liveAskClOrdId;
    private long lastKnownPosition;
    private long suppressedUntilMicros;
    private int consecutiveRejections;
    private long lastRejectionTimeMicros;
    private long lastTradePriceScaled;
    private long nextQuoteClOrdId;
    private long parentSubmitRejects;

    public MarketMakingStrategy(final MarketMakingConfig config) {
        this.config = Objects.requireNonNull(config, "config");
        subscribedInstrumentIds = new int[] {config.instrumentId()};
        activeVenueIds = new int[] {config.venueId()};
    }

    @Override
    public void init(final StrategyContext ctx) {
        marketView = ctx.marketView();
        riskEngine = ctx.riskEngine();
        recoveryCoordinator = ctx.recoveryCoordinator();
        portfolioEngine = ctx.portfolioEngine();
        executionEngine = ctx.executionEngine();
        cluster = ctx.cluster();
        egressBuffer = ctx.egressBuffer() == null ? new UnsafeBuffer(new byte[COMMAND_BUFFER_BYTES]) : ctx.egressBuffer();
        headerEncoder = ctx.headerEncoder() == null ? new MessageHeaderEncoder() : ctx.headerEncoder();
    }

    /**
     * Computes and publishes refreshed quotes for actionable market-data updates.
     *
     * @param decoder decoded normalized market-data event
     */
    @Override
    public void onMarketData(final MarketDataEventDecoder decoder) {
        if (decoder.entryType() == EntryType.TRADE) {
            lastTradePriceScaled = decoder.priceScaled();
            return;
        }
        if (decoder.venueId() != config.venueId() || decoder.instrumentId() != config.instrumentId()) {
            return;
        }
        if (isSuppressed()) {
            return;
        }

        final long bestBid = marketView.getBestBid(config.venueId(), config.instrumentId());
        final long bestAsk = marketView.getBestAsk(config.venueId(), config.instrumentId());
        if (bestBid == Ids.INVALID_PRICE || bestAsk == Ids.INVALID_PRICE) {
            return;
        }

        final long spread = bestAsk - bestBid;
        final long midPrice = (bestBid + bestAsk) / 2L;
        final long fairValue;
        if (spread * 10_000L / midPrice > config.wideSpreadThresholdBps()) {
            if (lastTradePriceScaled > 0L) {
                fairValue = lastTradePriceScaled;
            } else {
                suppress(WIDE_SPREAD_COOLDOWN_MICROS);
                return;
            }
        } else {
            fairValue = midPrice;
        }
        if (spread * 10_000L / midPrice > config.maxTolerableSpreadBps()) {
            suppress(WIDE_SPREAD_COOLDOWN_MICROS);
            return;
        }

        final long maxLimit = lastKnownPosition >= 0L ? config.maxPositionLongScaled() : config.maxPositionShortScaled();
        final long inventoryRatioBps = maxLimit == 0L ? 0L : lastKnownPosition * 10_000L / maxLimit;
        final long skewBps = config.inventorySkewFactorBps() * inventoryRatioBps / 10_000L;
        final long adjustedFairValue = fairValue - (skewBps * fairValue / 10_000L);

        final long halfSpread = config.targetSpreadBps() * adjustedFairValue / 20_000L;
        final long ourBid = ScaledMath.floorToTick(adjustedFairValue - halfSpread, config.tickSizeScaled());
        long ourAsk = ScaledMath.ceilToTick(adjustedFairValue + halfSpread, config.tickSizeScaled());
        if (ourBid >= ourAsk) {
            ourAsk = ourBid + config.tickSizeScaled();
        }

        final long minFloor = config.minQuoteSizeFractionBps();
        long bidSize;
        long askSize;
        if (inventoryRatioBps > 0L) {
            bidSize = config.baseQuoteSizeScaled() * Math.max(minFloor, 10_000L - inventoryRatioBps) / 10_000L;
            askSize = config.baseQuoteSizeScaled() * (10_000L + inventoryRatioBps) / 10_000L;
        } else {
            bidSize = config.baseQuoteSizeScaled() * (10_000L - inventoryRatioBps) / 10_000L;
            askSize = config.baseQuoteSizeScaled() * Math.max(minFloor, 10_000L + inventoryRatioBps) / 10_000L;
        }
        if (inventoryRatioBps >= 10_000L) {
            bidSize = 0L;
        } else if (inventoryRatioBps <= -10_000L) {
            askSize = 0L;
        }
        bidSize = Math.min(ScaledMath.floorToLot(bidSize, config.lotSizeScaled()), config.maxQuoteSizeScaled());
        askSize = Math.min(ScaledMath.floorToLot(askSize, config.lotSizeScaled()), config.maxQuoteSizeScaled());

        if (!requiresRefresh(ourBid, ourAsk, bidSize, askSize)) {
            return;
        }

        nextQuoteClOrdId = cluster == null ? 0L : cluster.logPosition();
        cancelLiveQuotes();
        if (bidSize > 0L) {
            submitQuote(Side.BUY, ourBid, bidSize);
        }
        if (askSize > 0L) {
            submitQuote(Side.SELL, ourAsk, askSize);
        }
        lastQuotedMid = midPrice;
        lastBidPrice = ourBid;
        lastAskPrice = ourAsk;
        lastBidSize = bidSize;
        lastAskSize = askSize;
        lastQuoteTimeCluster = nowMicros();
    }

    @Override
    public void onFill(final ExecutionEventDecoder decoder) {
        if (decoder.instrumentId() == config.instrumentId() && decoder.venueId() == config.venueId()) {
            lastKnownPosition = portfolioEngine == null ?
                lastKnownPosition + (decoder.side() == Side.BUY ? decoder.fillQtyScaled() : -decoder.fillQtyScaled()) :
                portfolioEngine.getNetQtyScaled(config.venueId(), config.instrumentId());
            consecutiveRejections = 0;
        }
    }

    @Override
    public void onOrderRejected(final long clOrdId, final byte rejectCode, final int venueId) {
        if (venueId != config.venueId()) {
            return;
        }
        consecutiveRejections++;
        lastRejectionTimeMicros = nowMicros();
        if (consecutiveRejections >= 3) {
            suppressedUntilMicros = nowMicros() + REJECTION_COOLDOWN_MICROS;
            consecutiveRejections = 0;
        }
    }

    @Override
    public void onKillSwitch() {
        cancelLiveQuotes();
        suppressedUntilMicros = Long.MAX_VALUE;
    }

    @Override
    public void onKillSwitchCleared() {
        suppressedUntilMicros = 0L;
    }

    @Override public void onVenueRecovered(final int venueId) { }
    @Override public void onPositionUpdate(final int venueId, final int instrumentId, final long netQtyScaled, final long avgEntryScaled) {
        if (venueId == config.venueId() && instrumentId == config.instrumentId()) {
            lastKnownPosition = netQtyScaled;
        }
    }
    @Override public int[] subscribedInstrumentIds() { return subscribedInstrumentIds; }
    @Override public int[] activeVenueIds() { return activeVenueIds; }
    @Override public void shutdown() { cancelLiveQuotes(); }
    @Override public int strategyId() { return Ids.STRATEGY_MARKET_MAKING; }

    @Override
    public void onParentTerminal(final ParentOrderTerminalDecoder decoder) {
        if (decoder.parentOrderId() == liveBidClOrdId) {
            liveBidClOrdId = 0L;
        }
        if (decoder.parentOrderId() == liveAskClOrdId) {
            liveAskClOrdId = 0L;
        }
    }

    boolean requiresRefresh(final long newBid, final long newAsk, final long newBidSize, final long newAskSize) {
        if (liveBidClOrdId == 0L || liveAskClOrdId == 0L) {
            return true;
        }
        if (nowMicros() - lastQuoteTimeCluster > config.maxQuoteAgeMicros()) {
            return true;
        }
        final long priceDelta = Math.max(Math.abs(newBid - lastBidPrice), Math.abs(newAsk - lastAskPrice));
        final long refreshThresh = lastQuotedMid * config.refreshThresholdBps() / 10_000L;
        if (priceDelta > refreshThresh) {
            return true;
        }
        final long sizeDelta = Math.max(Math.abs(newBidSize - lastBidSize), Math.abs(newAskSize - lastAskSize));
        return sizeDelta > config.baseQuoteSizeScaled() / 10L;
    }

    private boolean isSuppressed() {
        return riskEngine.isKillSwitchActive()
            || recoveryCoordinator.isInRecovery(config.venueId())
            || marketView.isStale(config.venueId(), config.instrumentId(), config.marketDataStalenessThresholdMicros(), nowMicros())
            || suppressedUntilMicros > 0L && nowMicros() < suppressedUntilMicros;
    }

    private void submitQuote(final Side side, final long price, final long size) {
        final long parentOrderId = nextQuoteClOrdId++;
        parentIntentEncoder.wrapAndApplyHeader(egressBuffer, 0, headerEncoder)
            .parentOrderId(parentOrderId)
            .strategyId((short) Ids.STRATEGY_MARKET_MAKING)
            .executionStrategyId(config.executionStrategy().executionStrategyId())
            .intentType(ParentIntentType.QUOTE)
            .side(side)
            .instrumentId(config.instrumentId())
            .primaryVenueId(config.venueId())
            .secondaryVenueId(0)
            .quantityScaled(size)
            .priceMode(PriceMode.LIMIT)
            .limitPriceScaled(price)
            .referencePriceScaled(0L)
            .timeInForcePreference(TimeInForce.GTC)
            .urgencyHint((byte) 1)
            .postOnlyPreference(ig.rueishi.nitroj.exchange.messages.BooleanType.TRUE)
            .selfTradePolicy((byte) 0)
            .correlationId(parentOrderId)
            .legCount((byte) 1)
            .leg2Side(Side.SELL)
            .leg2LimitPriceScaled(0L)
            .parentTimeoutMicros(0L)
            .venueSetId(0);
        parentIntentDecoder.wrapAndApplyHeader(egressBuffer, 0, headerDecoder);
        if (executionEngine == null || !executionEngine.submit(parentIntentDecoder)) {
            parentSubmitRejects++;
            suppress(REJECTION_COOLDOWN_MICROS);
            return;
        }
        if (side == Side.BUY) {
            liveBidClOrdId = parentOrderId;
        } else {
            liveAskClOrdId = parentOrderId;
        }
    }

    private void cancelLiveQuotes() {
        if (liveBidClOrdId != 0L) {
            if (executionEngine != null) {
                executionEngine.cancelParent(liveBidClOrdId, ParentOrderState.REASON_CANCELED_BY_PARENT);
            }
            liveBidClOrdId = 0L;
        }
        if (liveAskClOrdId != 0L) {
            if (executionEngine != null) {
                executionEngine.cancelParent(liveAskClOrdId, ParentOrderState.REASON_CANCELED_BY_PARENT);
            }
            liveAskClOrdId = 0L;
        }
    }

    private void suppress(final long durationMicros) {
        suppressedUntilMicros = nowMicros() + durationMicros;
    }

    private long nowMicros() {
        return cluster == null ? 0L : cluster.time();
    }

    long lastBidPrice() { return lastBidPrice; }
    long lastAskPrice() { return lastAskPrice; }
    long liveBidClOrdId() { return liveBidClOrdId; }
    long liveAskClOrdId() { return liveAskClOrdId; }
    long suppressedUntilMicros() { return suppressedUntilMicros; }
    int consecutiveRejections() { return consecutiveRejections; }
    long lastRejectionTimeMicros() { return lastRejectionTimeMicros; }
    long lastTradePriceScaled() { return lastTradePriceScaled; }
    long parentSubmitRejects() { return parentSubmitRejects; }
}
