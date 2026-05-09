package ig.rueishi.nitroj.exchange.strategy;

import ig.rueishi.nitroj.exchange.cluster.ExternalLiquidityView;
import ig.rueishi.nitroj.exchange.cluster.InternalMarketView;
import ig.rueishi.nitroj.exchange.cluster.RiskDecision;
import ig.rueishi.nitroj.exchange.cluster.RiskEngine;
import ig.rueishi.nitroj.exchange.common.ArbStrategyConfig;
import ig.rueishi.nitroj.exchange.common.ExecutionStrategyIds;
import ig.rueishi.nitroj.exchange.common.Ids;
import ig.rueishi.nitroj.exchange.common.ScaledMath;
import ig.rueishi.nitroj.exchange.execution.ExecutionStrategyEngine;
import ig.rueishi.nitroj.exchange.messages.BooleanType;
import ig.rueishi.nitroj.exchange.messages.CancelOrderCommandEncoder;
import ig.rueishi.nitroj.exchange.messages.EntryType;
import ig.rueishi.nitroj.exchange.messages.ExecutionEventDecoder;
import ig.rueishi.nitroj.exchange.messages.MarketDataEventDecoder;
import ig.rueishi.nitroj.exchange.messages.MessageHeaderDecoder;
import ig.rueishi.nitroj.exchange.messages.MessageHeaderEncoder;
import ig.rueishi.nitroj.exchange.messages.NewOrderCommandEncoder;
import ig.rueishi.nitroj.exchange.messages.OrdType;
import ig.rueishi.nitroj.exchange.messages.ParentIntentType;
import ig.rueishi.nitroj.exchange.messages.ParentOrderIntentDecoder;
import ig.rueishi.nitroj.exchange.messages.ParentOrderIntentEncoder;
import ig.rueishi.nitroj.exchange.messages.ParentOrderTerminalDecoder;
import ig.rueishi.nitroj.exchange.messages.ParentTerminalReason;
import ig.rueishi.nitroj.exchange.messages.PriceMode;
import ig.rueishi.nitroj.exchange.messages.Side;
import ig.rueishi.nitroj.exchange.messages.TimeInForce;
import ig.rueishi.nitroj.exchange.order.OrderManager;
import io.aeron.cluster.service.Cluster;
import java.util.Objects;
import org.agrona.concurrent.UnsafeBuffer;

/**
 * Cross-venue arbitrage strategy with deterministic two-leg submission.
 *
 * <p>The strategy scans configured venue pairs for a buy-ask/sell-bid edge after
 * taker fees and linear slippage. When an opportunity is actionable it encodes
 * both IOC legs into one SBE buffer and performs a single cluster offer, reducing
 * leg gap while preserving Raft-log determinism. Active attempt state is stored
 * in primitive fields and is intentionally not snapshotted; recovery reconciles
 * any live orders after restart.</p>
 */
public final class ArbStrategy implements Strategy {
    static final long ARB_TIMEOUT_BASE = 4_000L;
    private static final byte LEG_PENDING = 0;
    private static final byte LEG_DONE = 1;
    private static final int COMMAND_BUFFER_BYTES = 1024;
    private static final byte[] EMPTY_VENUE_ORDER_ID = new byte[0];

    private final ArbStrategyConfig config;
    private final int[] venueIds;
    private final int[] subscribedInstrumentIds;
    private final long[] takerFeeScaled;
    private final long[] baseSlippageBps;
    private final long[] slippageSlopeBps;
    private InternalMarketView marketView;
    private ExternalLiquidityView externalLiquidityView;
    private RiskEngine riskEngine;
    private OrderManager orderManager;
    private ExecutionStrategyEngine executionEngine;
    private Cluster cluster;
    private UnsafeBuffer egressBuffer;
    private MessageHeaderEncoder headerEncoder;
    private final MessageHeaderDecoder headerDecoder = new MessageHeaderDecoder();
    private NewOrderCommandEncoder newOrderEncoder;
    private CancelOrderCommandEncoder cancelOrderEncoder;
    private final ParentOrderIntentEncoder parentIntentEncoder = new ParentOrderIntentEncoder();
    private final ParentOrderIntentDecoder parentIntentDecoder = new ParentOrderIntentDecoder();
    private long arbAttemptId;
    private int leg1VenueId;
    private int leg2VenueId;
    private long leg1ClOrdId;
    private long leg2ClOrdId;
    private byte leg1Status = LEG_PENDING;
    private byte leg2Status = LEG_PENDING;
    private long leg1FillQtyScaled;
    private long leg2FillQtyScaled;
    private long arbCreatedCluster;
    private boolean arbActive;
    private long activeArbTimeoutCorrelId;
    private long cooldownUntilMicros;

    public ArbStrategy(final ArbStrategyConfig config) {
        this.config = Objects.requireNonNull(config, "config");
        this.venueIds = config.venueIds();
        this.takerFeeScaled = config.takerFeeScaled();
        this.baseSlippageBps = config.baseSlippageBps();
        this.slippageSlopeBps = config.slippageSlopeBps();
        subscribedInstrumentIds = new int[] {config.instrumentId()};
    }

    @Override
    public void init(final StrategyContext ctx) {
        marketView = ctx.marketView();
        externalLiquidityView = ctx.externalLiquidityView();
        riskEngine = ctx.riskEngine();
        orderManager = ctx.orderManager();
        executionEngine = ctx.executionEngine();
        cluster = ctx.cluster();
        egressBuffer = ctx.egressBuffer() == null ? new UnsafeBuffer(new byte[COMMAND_BUFFER_BYTES]) : ctx.egressBuffer();
        headerEncoder = ctx.headerEncoder() == null ? new MessageHeaderEncoder() : ctx.headerEncoder();
        newOrderEncoder = ctx.newOrderEncoder() == null ? new NewOrderCommandEncoder() : ctx.newOrderEncoder();
        cancelOrderEncoder = ctx.cancelOrderEncoder() == null ? new CancelOrderCommandEncoder() : ctx.cancelOrderEncoder();
    }

    @Override
    public void onMarketData(final MarketDataEventDecoder decoder) {
        if (arbActive || decoder.instrumentId() != config.instrumentId() || nowMicros() < cooldownUntilMicros) {
            return;
        }
        final long size = Math.min(config.referenceSize(), config.maxArbPositionScaled());
        for (int buyVenueId : venueIds) {
                final long buyAsk = externalLiquidityView.externalBestAsk(buyVenueId, config.instrumentId());
                if (buyAsk == Ids.INVALID_PRICE) {
                    continue;
                }
                for (int sellVenueId : venueIds) {
                    if (buyVenueId == sellVenueId) {
                        continue;
                    }
                    final long sellBid = externalLiquidityView.externalBestBid(sellVenueId, config.instrumentId());
                    if (sellBid == Ids.INVALID_PRICE) {
                        continue;
                    }
                    final long buyExternalSize = externalLiquidityView.externalSizeAt(
                        buyVenueId, config.instrumentId(), EntryType.ASK, buyAsk);
                    final long sellExternalSize = externalLiquidityView.externalSizeAt(
                        sellVenueId, config.instrumentId(), EntryType.BID, sellBid);
                    final long executableSize = Math.min(size, Math.min(buyExternalSize, sellExternalSize));
                    if (executableSize <= 0L || wouldSelfCross(buyVenueId, Side.BUY, buyAsk, buyExternalSize)
                        || wouldSelfCross(sellVenueId, Side.SELL, sellBid, sellExternalSize)) {
                        continue;
                    }
                    final long grossProfit = sellBid - buyAsk;
                    final long buyFee = ScaledMath.safeMulDiv(buyAsk, takerFeeScaled[buyVenueId], Ids.SCALE);
                    final long sellFee = ScaledMath.safeMulDiv(sellBid, takerFeeScaled[sellVenueId], Ids.SCALE);
                    final long netProfit = grossProfit - buyFee - sellFee
                        - slippageScaled(buyVenueId, executableSize)
                        - slippageScaled(sellVenueId, executableSize);
                    final long netProfitBps = netProfit * 10_000L / buyAsk;
                    if (netProfitBps >= config.minNetProfitBps()) {
                        executeArb(buyVenueId, sellVenueId, executableSize, buyAsk, sellBid);
                        return;
                    }
                }
        }
    }

    @Override
    public void onFill(final ExecutionEventDecoder decoder) {
        if (!arbActive) {
            return;
        }
        final long clOrdId = decoder.clOrdId();
        if (clOrdId == leg1ClOrdId) {
            leg1FillQtyScaled += decoder.fillQtyScaled();
            if (decoder.isFinal() != BooleanType.FALSE) {
                leg1Status = LEG_DONE;
            }
        } else if (clOrdId == leg2ClOrdId) {
            leg2FillQtyScaled += decoder.fillQtyScaled();
            if (decoder.isFinal() != BooleanType.FALSE) {
                leg2Status = LEG_DONE;
            }
        }
        if (leg1Status == LEG_DONE && leg2Status == LEG_DONE) {
            hedgeImbalanceIfNeeded();
            resetArbState();
        }
    }

    @Override
    public void onTimer(final long correlationId) {
        if (correlationId != activeArbTimeoutCorrelId || !arbActive) {
            return;
        }
        if (leg1Status == LEG_PENDING) {
            submitCancel(leg1ClOrdId, leg1VenueId, Side.BUY);
        }
        if (leg2Status == LEG_PENDING) {
            submitCancel(leg2ClOrdId, leg2VenueId, Side.SELL);
        }
        hedgeImbalanceIfNeeded();
        resetArbState();
    }

    @Override public void onOrderRejected(final long clOrdId, final byte rejectCode, final int venueId) { }
    @Override public void onKillSwitch() { resetArbState(); }
    @Override public void onKillSwitchCleared() { cooldownUntilMicros = 0L; }
    @Override public void onVenueRecovered(final int venueId) { }
    @Override public void onPositionUpdate(final int venueId, final int instrumentId, final long netQtyScaled, final long avgEntryScaled) { }
    @Override public int[] subscribedInstrumentIds() { return subscribedInstrumentIds; }
    @Override public int[] activeVenueIds() { return venueIds; }
    @Override public void shutdown() { resetArbState(); }
    @Override public int strategyId() { return Ids.STRATEGY_ARB; }

    @Override
    public void onParentTerminal(final ParentOrderTerminalDecoder decoder) {
        if (decoder.parentOrderId() != arbAttemptId) {
            return;
        }
        if (decoder.terminalReason() == ParentTerminalReason.EXPIRED
            || decoder.terminalReason() == ParentTerminalReason.RISK_REJECTED
            || decoder.terminalReason() == ParentTerminalReason.HEDGE_FAILED
            || decoder.terminalReason() == ParentTerminalReason.KILL_SWITCH
            || decoder.terminalReason() == ParentTerminalReason.EXECUTION_ABORTED
            || decoder.terminalReason() == ParentTerminalReason.CHILD_REJECTED) {
            cooldownUntilMicros = nowMicros() + config.cooldownAfterFailureMicros();
        }
        resetArbState();
    }

    private void executeArb(final int buyVenueId, final int sellVenueId, final long size, final long buyPrice, final long sellPrice) {
        final long base = cluster.logPosition();
        arbAttemptId = base;
        leg1ClOrdId = base + 1L;
        leg2ClOrdId = base + 2L;
        leg1VenueId = buyVenueId;
        leg2VenueId = sellVenueId;
        leg1Status = LEG_PENDING;
        leg2Status = LEG_PENDING;
        arbCreatedCluster = nowMicros();
        arbActive = true;
        if (executionEngine != null) {
            if (!submitMultiLegIntent(buyVenueId, sellVenueId, size, buyPrice, sellPrice, base)) {
                cooldownUntilMicros = nowMicros() + config.cooldownAfterFailureMicros();
                resetArbState();
            }
            return;
        }
        if (!riskApproved(buyVenueId, Side.BUY, buyPrice, size)
            || !riskApproved(sellVenueId, Side.SELL, sellPrice, size)) {
            cooldownUntilMicros = nowMicros() + config.cooldownAfterFailureMicros();
            resetArbState();
            return;
        }
        int offset = 0;
        offset += encodeLeg(offset, buyVenueId, Side.BUY, buyPrice, size, leg1ClOrdId, Ids.STRATEGY_ARB);
        offset += encodeLeg(offset, sellVenueId, Side.SELL, sellPrice, size, leg2ClOrdId, Ids.STRATEGY_ARB);
        cluster.offer(egressBuffer, 0, offset);
        activeArbTimeoutCorrelId = ARB_TIMEOUT_BASE + (arbAttemptId & 0xFFFFL);
        cluster.scheduleTimer(activeArbTimeoutCorrelId, nowMicros() + config.legTimeoutClusterMicros());
    }

    private boolean submitMultiLegIntent(
        final int buyVenueId,
        final int sellVenueId,
        final long size,
        final long buyPrice,
        final long sellPrice,
        final long parentOrderId
    ) {
        parentIntentEncoder.wrapAndApplyHeader(egressBuffer, 0, headerEncoder)
            .parentOrderId(parentOrderId)
            .strategyId((short) Ids.STRATEGY_ARB)
            .executionStrategyId(config.executionStrategy().executionStrategyId())
            .intentType(ParentIntentType.MULTI_LEG)
            .side(Side.BUY)
            .instrumentId(config.instrumentId())
            .primaryVenueId(buyVenueId)
            .secondaryVenueId(sellVenueId)
            .quantityScaled(size)
            .priceMode(PriceMode.LIMIT)
            .limitPriceScaled(buyPrice)
            .referencePriceScaled(0L)
            .timeInForcePreference(TimeInForce.IOC)
            .urgencyHint((byte) 1)
            .postOnlyPreference(BooleanType.FALSE)
            .selfTradePolicy((byte) 0)
            .correlationId(parentOrderId)
            .legCount((byte) 2)
            .leg2Side(Side.SELL)
            .leg2LimitPriceScaled(sellPrice)
            .parentTimeoutMicros(config.legTimeoutClusterMicros())
            .venueSetId(0);
        parentIntentDecoder.wrapAndApplyHeader(egressBuffer, 0, headerDecoder);
        return executionEngine.submit(parentIntentDecoder);
    }

    private boolean wouldSelfCross(final int venueId, final Side side, final long price, final long externalSizeAtPrice) {
        return marketView.ownOrderOverlay().wouldSelfCrossBeyondExternal(
            venueId,
            config.instrumentId(),
            side == Side.BUY ? EntryType.BID : EntryType.ASK,
            price,
            externalSizeAtPrice);
    }

    private boolean riskApproved(final int venueId, final Side side, final long price, final long size) {
        final RiskDecision decision = riskEngine.preTradeCheck(
            venueId,
            config.instrumentId(),
            side.value(),
            price,
            size,
            Ids.STRATEGY_ARB);
        if (!decision.approved() && decision == RiskDecision.REJECT_KILL_SWITCH) {
            riskEngine.activateKillSwitch("arb_pretrade_reject");
        }
        return decision.approved();
    }

    private int encodeLeg(
        final int offset,
        final int venueId,
        final Side side,
        final long price,
        final long size,
        final long clOrdId,
        final int strategyId
    ) {
        orderManager.createPendingOrder(clOrdId, venueId, config.instrumentId(), side.value(), OrdType.LIMIT.value(), TimeInForce.IOC.value(), price, size, strategyId);
        newOrderEncoder.wrapAndApplyHeader(egressBuffer, offset, headerEncoder)
            .clOrdId(clOrdId)
            .venueId(venueId)
            .instrumentId(config.instrumentId())
            .side(side)
            .ordType(OrdType.LIMIT)
            .timeInForce(TimeInForce.IOC)
            .priceScaled(price)
            .qtyScaled(size)
            .strategyId((short)strategyId);
        return MessageHeaderEncoder.ENCODED_LENGTH + newOrderEncoder.encodedLength();
    }

    private void submitCancel(final long origClOrdId, final int venueId, final Side side) {
        cancelOrderEncoder.wrapAndApplyHeader(egressBuffer, 0, headerEncoder)
            .cancelClOrdId(cluster.logPosition())
            .origClOrdId(origClOrdId)
            .venueId(venueId)
            .instrumentId(config.instrumentId())
            .side(side)
            .originalQtyScaled(config.referenceSize())
            .putVenueOrderId(EMPTY_VENUE_ORDER_ID, 0, 0);
        cluster.offer(egressBuffer, 0, MessageHeaderEncoder.ENCODED_LENGTH + cancelOrderEncoder.encodedLength());
    }

    private void hedgeImbalanceIfNeeded() {
        final long imbalance = leg1FillQtyScaled - leg2FillQtyScaled;
        if (Math.abs(imbalance) > config.referenceSize() && Math.abs(imbalance) > 0L) {
            submitHedge(imbalance > 0L ? Side.SELL : Side.BUY, Math.abs(imbalance), imbalance > 0L ? leg2VenueId : leg1VenueId);
        }
    }

    private void submitHedge(final Side side, final long size, final int venueId) {
        final long price = side == Side.BUY ? marketView.getBestAsk(venueId, config.instrumentId()) : marketView.getBestBid(venueId, config.instrumentId());
        final RiskDecision decision = riskEngine.preTradeCheck(venueId, config.instrumentId(), side.value(), price, size, Ids.STRATEGY_ARB_HEDGE);
        if (decision != RiskDecision.APPROVED) {
            riskEngine.activateKillSwitch("hedge_failure");
            cooldownUntilMicros = nowMicros() + config.cooldownAfterFailureMicros();
            resetArbState();
            return;
        }
        encodeLeg(0, venueId, side, price, size, cluster.logPosition(), Ids.STRATEGY_ARB_HEDGE);
        cluster.offer(egressBuffer, 0, MessageHeaderEncoder.ENCODED_LENGTH + newOrderEncoder.encodedLength());
    }

    long slippageScaled(final int venueId, final long sizeScaled) {
        final long slippageBps = baseSlippageBps[venueId] + slippageSlopeBps[venueId] * sizeScaled / config.referenceSize();
        final long midPrice = (marketView.getBestBid(venueId, config.instrumentId()) + marketView.getBestAsk(venueId, config.instrumentId())) / 2L;
        return midPrice * slippageBps / 10_000L;
    }

    private void resetArbState() {
        arbAttemptId = 0L;
        leg1VenueId = 0;
        leg2VenueId = 0;
        leg1ClOrdId = 0L;
        leg2ClOrdId = 0L;
        leg1Status = LEG_PENDING;
        leg2Status = LEG_PENDING;
        leg1FillQtyScaled = 0L;
        leg2FillQtyScaled = 0L;
        arbCreatedCluster = 0L;
        arbActive = false;
        activeArbTimeoutCorrelId = 0L;
    }

    private long nowMicros() {
        return cluster == null ? 0L : cluster.time();
    }

    boolean arbActive() { return arbActive; }
    long arbAttemptId() { return arbAttemptId; }
    long leg1ClOrdId() { return leg1ClOrdId; }
    long leg2ClOrdId() { return leg2ClOrdId; }
    int leg1VenueId() { return leg1VenueId; }
    int leg2VenueId() { return leg2VenueId; }
    long activeArbTimeoutCorrelId() { return activeArbTimeoutCorrelId; }
    long cooldownUntilMicros() { return cooldownUntilMicros; }
}
