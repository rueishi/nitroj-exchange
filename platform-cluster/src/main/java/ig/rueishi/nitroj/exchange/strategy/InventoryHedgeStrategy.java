package ig.rueishi.nitroj.exchange.strategy;

import ig.rueishi.nitroj.exchange.cluster.InternalMarketView;
import ig.rueishi.nitroj.exchange.cluster.PortfolioEngine;
import ig.rueishi.nitroj.exchange.common.ConfigValidationException;
import ig.rueishi.nitroj.exchange.common.ExecutionStrategyIds;
import ig.rueishi.nitroj.exchange.common.Ids;
import ig.rueishi.nitroj.exchange.execution.ExecutionStrategyEngine;
import ig.rueishi.nitroj.exchange.messages.BooleanType;
import ig.rueishi.nitroj.exchange.messages.EntryType;
import ig.rueishi.nitroj.exchange.messages.ExecutionEventDecoder;
import ig.rueishi.nitroj.exchange.messages.MarketDataEventDecoder;
import ig.rueishi.nitroj.exchange.messages.MessageHeaderDecoder;
import ig.rueishi.nitroj.exchange.messages.MessageHeaderEncoder;
import ig.rueishi.nitroj.exchange.messages.ParentIntentType;
import ig.rueishi.nitroj.exchange.messages.ParentOrderIntentDecoder;
import ig.rueishi.nitroj.exchange.messages.ParentOrderIntentEncoder;
import ig.rueishi.nitroj.exchange.messages.ParentOrderTerminalDecoder;
import ig.rueishi.nitroj.exchange.messages.ParentTerminalReason;
import ig.rueishi.nitroj.exchange.messages.PriceMode;
import ig.rueishi.nitroj.exchange.messages.Side;
import ig.rueishi.nitroj.exchange.messages.TimeInForce;
import ig.rueishi.nitroj.exchange.order.OrderManager;
import ig.rueishi.nitroj.exchange.order.OrderState;
import io.aeron.cluster.service.Cluster;
import java.util.Arrays;
import java.util.Objects;
import org.agrona.concurrent.UnsafeBuffer;

/**
 * Venue-indifferent inventory hedge producer.
 *
 * <p>The strategy watches deterministic portfolio and working-order state for
 * one instrument. When configured exposure exceeds the threshold, it emits one
 * parent hedge intent carrying a venue-set identifier and leaves venue selection
 * to the configured V14 execution strategy. The implementation keeps all
 * mutable hot-path state in primitive fields or fixed arrays so replay and JMH
 * allocation checks remain stable.</p>
 */
public final class InventoryHedgeStrategy implements Strategy {
    private static final int COMMAND_BUFFER_BYTES = 512;
    private static final byte SELF_TRADE_REJECT = 1;
    private static final byte URGENCY_HIGH = 3;

    private final Config config;
    private final int[] subscribedInstrumentIds;
    private final int[] activeVenueIds;
    private final long[] positionByVenue = new long[Ids.MAX_VENUES + 1];
    private final WorkingExposureConsumer workingExposureConsumer = new WorkingExposureConsumer();
    private final ParentOrderIntentEncoder parentIntentEncoder = new ParentOrderIntentEncoder();
    private final ParentOrderIntentDecoder parentIntentDecoder = new ParentOrderIntentDecoder();
    private final MessageHeaderDecoder headerDecoder = new MessageHeaderDecoder();
    private InternalMarketView marketView;
    private PortfolioEngine portfolioEngine;
    private OrderManager orderManager;
    private ExecutionStrategyEngine executionEngine;
    private Cluster cluster;
    private UnsafeBuffer egressBuffer;
    private MessageHeaderEncoder headerEncoder;
    private long activeParentOrderId;
    private long cooldownUntilMicros;
    private long lastExposureQtyScaled;
    private long lastThresholdMetricScaled;
    private long emittedParentCount;
    private long suppressedActiveParentCount;
    private long suppressedCooldownCount;
    private long failedSubmitCount;
    private long fallbackParentOrderId = 1L;

    public InventoryHedgeStrategy(final Config config) {
        this.config = Objects.requireNonNull(config, "config");
        subscribedInstrumentIds = new int[] { config.instrumentId };
        activeVenueIds = Arrays.copyOf(config.venueIds, config.venueIds.length);
    }

    @Override
    public void init(final StrategyContext ctx) {
        marketView = ctx.marketView();
        portfolioEngine = ctx.portfolioEngine();
        orderManager = ctx.orderManager();
        executionEngine = ctx.executionEngine();
        cluster = ctx.cluster();
        egressBuffer = ctx.egressBuffer() == null ? new UnsafeBuffer(new byte[COMMAND_BUFFER_BYTES]) : ctx.egressBuffer();
        headerEncoder = ctx.headerEncoder() == null ? new MessageHeaderEncoder() : ctx.headerEncoder();
    }

    @Override
    public void onMarketData(final MarketDataEventDecoder decoder) {
        if (decoder == null || decoder.instrumentId() == config.instrumentId) {
            evaluate();
        }
    }

    @Override
    public void onFill(final ExecutionEventDecoder decoder) {
        if (decoder.instrumentId() == config.instrumentId) {
            evaluate();
        }
    }

    @Override public void onOrderRejected(final long clOrdId, final byte rejectCode, final int venueId) { }
    @Override public void onKillSwitch() { }
    @Override public void onKillSwitchCleared() { }
    @Override public void onVenueRecovered(final int venueId) { }

    @Override
    public void onPositionUpdate(
        final int venueId,
        final int instrumentId,
        final long netQtyScaled,
        final long avgEntryScaled
    ) {
        if (instrumentId == config.instrumentId && venueId > 0 && venueId < positionByVenue.length) {
            positionByVenue[venueId] = netQtyScaled;
            evaluate();
        }
    }

    @Override public int[] subscribedInstrumentIds() { return subscribedInstrumentIds; }
    @Override public int[] activeVenueIds() { return activeVenueIds; }
    @Override public void shutdown() { activeParentOrderId = 0L; }
    @Override public int strategyId() { return Ids.STRATEGY_INVENTORY_HEDGE; }

    @Override
    public void onParentTerminal(final ParentOrderTerminalDecoder decoder) {
        if (decoder.strategyId() != Ids.STRATEGY_INVENTORY_HEDGE || decoder.parentOrderId() != activeParentOrderId) {
            return;
        }
        activeParentOrderId = 0L;
        final long cooldown = isFailure(decoder.terminalReason())
            ? config.failureCooldownMicros
            : config.cooldownMicros;
        cooldownUntilMicros = nowMicros() + cooldown;
    }

    public void snapshotInto(final Snapshot snapshot) {
        snapshot.activeParentOrderId = activeParentOrderId;
        snapshot.cooldownUntilMicros = cooldownUntilMicros;
        snapshot.lastExposureQtyScaled = lastExposureQtyScaled;
        snapshot.lastThresholdMetricScaled = lastThresholdMetricScaled;
        snapshot.emittedParentCount = emittedParentCount;
        snapshot.suppressedActiveParentCount = suppressedActiveParentCount;
        snapshot.suppressedCooldownCount = suppressedCooldownCount;
        snapshot.failedSubmitCount = failedSubmitCount;
        snapshot.fallbackParentOrderId = fallbackParentOrderId;
        System.arraycopy(positionByVenue, 0, snapshot.positionByVenue, 0, positionByVenue.length);
    }

    public void loadFrom(final Snapshot snapshot) {
        activeParentOrderId = snapshot.activeParentOrderId;
        cooldownUntilMicros = snapshot.cooldownUntilMicros;
        lastExposureQtyScaled = snapshot.lastExposureQtyScaled;
        lastThresholdMetricScaled = snapshot.lastThresholdMetricScaled;
        emittedParentCount = snapshot.emittedParentCount;
        suppressedActiveParentCount = snapshot.suppressedActiveParentCount;
        suppressedCooldownCount = snapshot.suppressedCooldownCount;
        failedSubmitCount = snapshot.failedSubmitCount;
        fallbackParentOrderId = snapshot.fallbackParentOrderId <= 0L ? 1L : snapshot.fallbackParentOrderId;
        System.arraycopy(snapshot.positionByVenue, 0, positionByVenue, 0, positionByVenue.length);
    }

    public long activeParentOrderId() { return activeParentOrderId; }
    public long cooldownUntilMicros() { return cooldownUntilMicros; }
    public long lastExposureQtyScaled() { return lastExposureQtyScaled; }
    public long lastThresholdMetricScaled() { return lastThresholdMetricScaled; }
    public long emittedParentCount() { return emittedParentCount; }
    public long suppressedActiveParentCount() { return suppressedActiveParentCount; }
    public long suppressedCooldownCount() { return suppressedCooldownCount; }
    public long failedSubmitCount() { return failedSubmitCount; }

    private void evaluate() {
        if (activeParentOrderId != 0L) {
            suppressedActiveParentCount++;
            return;
        }
        if (nowMicros() < cooldownUntilMicros) {
            suppressedCooldownCount++;
            return;
        }

        final long exposureQty = exposureQtyScaled();
        final long mid = consolidatedMid();
        final long absExposureQty = Math.abs(exposureQty);
        final long thresholdMetric = config.thresholdMode == ThresholdMode.BASE_QUANTITY
            ? absExposureQty
            : notional(absExposureQty, mid);
        lastExposureQtyScaled = exposureQty;
        lastThresholdMetricScaled = thresholdMetric;
        if (thresholdMetric <= config.thresholdValueScaled || thresholdMetric == 0L) {
            return;
        }

        final long safeQty = config.thresholdMode == ThresholdMode.BASE_QUANTITY
            ? config.safeBandValueScaled
            : quantityFromNotional(config.safeBandValueScaled, mid);
        final long hedgeQty = absExposureQty - safeQty;
        if (hedgeQty <= 0L) {
            return;
        }
        submitHedge(exposureQty > 0L ? Side.SELL : Side.BUY, hedgeQty, mid);
    }

    private long exposureQtyScaled() {
        long exposure = 0L;
        for (int i = 0; i < config.venueIds.length; i++) {
            final int venueId = config.venueIds[i];
            final long netQty = portfolioEngine == null
                ? positionByVenue[venueId]
                : portfolioEngine.getNetQtyScaled(venueId, config.instrumentId);
            positionByVenue[venueId] = netQty;
            exposure += netQty;
        }
        if (config.exposureMode == ExposureMode.FILLED_PLUS_WORKING && orderManager != null) {
            workingExposureConsumer.reset(config.instrumentId);
            for (int i = 0; i < config.venueIds.length; i++) {
                orderManager.forEachLiveOrder(config.venueIds[i], workingExposureConsumer);
            }
            exposure += workingExposureConsumer.signedWorkingQtyScaled;
        }
        return exposure;
    }

    private void submitHedge(final Side side, final long hedgeQty, final long mid) {
        final long parentOrderId = nextParentOrderId();
        parentIntentEncoder.wrapAndApplyHeader(egressBuffer, 0, headerEncoder)
            .parentOrderId(parentOrderId)
            .strategyId((short) Ids.STRATEGY_INVENTORY_HEDGE)
            .executionStrategyId(config.executionStrategyId)
            .intentType(ParentIntentType.HEDGE)
            .side(side)
            .instrumentId(config.instrumentId)
            .primaryVenueId(config.venueIds[0])
            .secondaryVenueId(0)
            .quantityScaled(hedgeQty)
            .priceMode(PriceMode.REFERENCE)
            .limitPriceScaled(mid)
            .referencePriceScaled(mid)
            .timeInForcePreference(TimeInForce.IOC)
            .urgencyHint(URGENCY_HIGH)
            .postOnlyPreference(BooleanType.FALSE)
            .selfTradePolicy(SELF_TRADE_REJECT)
            .correlationId(parentOrderId)
            .legCount((byte) 1)
            .leg2Side(Side.NULL_VAL)
            .leg2LimitPriceScaled(0L)
            .parentTimeoutMicros(config.parentTimeoutMicros)
            .venueSetId(config.venueSetId);
        parentIntentDecoder.wrapAndApplyHeader(egressBuffer, 0, headerDecoder);
        if (executionEngine == null || !executionEngine.submit(parentIntentDecoder)) {
            failedSubmitCount++;
            cooldownUntilMicros = nowMicros() + config.failureCooldownMicros;
            return;
        }
        activeParentOrderId = parentOrderId;
        emittedParentCount++;
    }

    private long consolidatedMid() {
        if (marketView == null) {
            return 0L;
        }
        final long bid = marketView.consolidatedBestBid(config.instrumentId);
        final long ask = marketView.consolidatedBestAsk(config.instrumentId);
        if (bid == Ids.INVALID_PRICE || ask == Ids.INVALID_PRICE) {
            return 0L;
        }
        return (bid + ask) / 2L;
    }

    private static long notional(final long qtyScaled, final long priceScaled) {
        return priceScaled <= 0L ? 0L : (long)(((double)qtyScaled * (double)priceScaled) / (double)Ids.SCALE);
    }

    private static long quantityFromNotional(final long notionalScaled, final long priceScaled) {
        return priceScaled <= 0L ? Long.MAX_VALUE : (long)(((double)notionalScaled * (double)Ids.SCALE) / (double)priceScaled);
    }

    private long nextParentOrderId() {
        if (cluster != null) {
            return cluster.logPosition();
        }
        return fallbackParentOrderId++;
    }

    private long nowMicros() {
        return cluster == null ? 0L : cluster.time();
    }

    private static boolean isFailure(final ParentTerminalReason reason) {
        return reason == ParentTerminalReason.RISK_REJECTED
            || reason == ParentTerminalReason.CHILD_REJECTED
            || reason == ParentTerminalReason.HEDGE_FAILED
            || reason == ParentTerminalReason.KILL_SWITCH
            || reason == ParentTerminalReason.EXECUTION_ABORTED
            || reason == ParentTerminalReason.CAPACITY_REJECTED;
    }

    public enum ThresholdMode {
        BASE_QUANTITY,
        NOTIONAL
    }

    public enum ExposureMode {
        FILLED_ONLY,
        FILLED_PLUS_WORKING
    }

    public record Config(
        int instrumentId,
        int venueSetId,
        int[] venueIds,
        ThresholdMode thresholdMode,
        long thresholdValueScaled,
        ExposureMode exposureMode,
        long safeBandValueScaled,
        long cooldownMicros,
        long failureCooldownMicros,
        int executionStrategyId,
        long parentTimeoutMicros
    ) {
        public Config {
            if (instrumentId <= 0 || instrumentId > Ids.MAX_INSTRUMENTS) {
                throw new ConfigValidationException("strategy.inventoryHedge.instrumentId", "instrumentId out of range");
            }
            if (venueSetId <= 0) {
                throw new ConfigValidationException("strategy.inventoryHedge.venueSetId", "venueSetId must be positive");
            }
            if (venueIds == null || venueIds.length == 0) {
                throw new ConfigValidationException("strategy.inventoryHedge.venueSet", "venueSet must not be empty");
            }
            venueIds = Arrays.copyOf(venueIds, venueIds.length);
            for (int i = 0; i < venueIds.length; i++) {
                if (venueIds[i] <= 0 || venueIds[i] > Ids.MAX_VENUES) {
                    throw new ConfigValidationException("strategy.inventoryHedge.venueSet", "venue ID out of range");
                }
            }
            Objects.requireNonNull(thresholdMode, "thresholdMode");
            Objects.requireNonNull(exposureMode, "exposureMode");
            if (thresholdValueScaled <= 0L) {
                throw new ConfigValidationException("strategy.inventoryHedge.thresholdValue", "thresholdValue must be positive");
            }
            if (safeBandValueScaled < 0L || safeBandValueScaled >= thresholdValueScaled) {
                throw new ConfigValidationException("strategy.inventoryHedge.safeBandValue", "safeBandValue must be non-negative and below thresholdValue");
            }
            if (cooldownMicros < 0L || failureCooldownMicros < cooldownMicros) {
                throw new ConfigValidationException("strategy.inventoryHedge.cooldownMicros", "failure cooldown must be at least normal cooldown");
            }
            if (!ExecutionStrategyIds.isCompatible(Ids.STRATEGY_INVENTORY_HEDGE, executionStrategyId)) {
                throw new ConfigValidationException("strategy.inventoryHedge.executionStrategy", "unsupported InventoryHedge execution strategy");
            }
        }

        public static Config of(
            final int instrumentId,
            final int venueSetId,
            final int[] venueIds,
            final String thresholdMode,
            final long thresholdValueScaled,
            final String exposureMode,
            final long safeBandValueScaled,
            final long cooldownMicros,
            final long failureCooldownMicros,
            final String executionStrategy,
            final long parentTimeoutMicros
        ) {
            return new Config(
                instrumentId,
                venueSetId,
                venueIds,
                parseThresholdMode(thresholdMode),
                thresholdValueScaled,
                parseExposureMode(exposureMode),
                safeBandValueScaled,
                cooldownMicros,
                failureCooldownMicros,
                ExecutionStrategyIds.parseCanonical(executionStrategy, "strategy.inventoryHedge.executionStrategy"),
                parentTimeoutMicros);
        }

        @Override
        public int[] venueIds() {
            return Arrays.copyOf(venueIds, venueIds.length);
        }
    }

    public static final class Snapshot {
        private final long[] positionByVenue = new long[Ids.MAX_VENUES + 1];
        long activeParentOrderId;
        long cooldownUntilMicros;
        long lastExposureQtyScaled;
        long lastThresholdMetricScaled;
        long emittedParentCount;
        long suppressedActiveParentCount;
        long suppressedCooldownCount;
        long failedSubmitCount;
        long fallbackParentOrderId;

        public long activeParentOrderId() { return activeParentOrderId; }
        public long cooldownUntilMicros() { return cooldownUntilMicros; }
        public long lastExposureQtyScaled() { return lastExposureQtyScaled; }
    }

    private static ThresholdMode parseThresholdMode(final String value) {
        if ("base_quantity".equals(value)) {
            return ThresholdMode.BASE_QUANTITY;
        }
        if ("notional".equals(value)) {
            return ThresholdMode.NOTIONAL;
        }
        throw new ConfigValidationException("strategy.inventoryHedge.thresholdMode", "thresholdMode must be base_quantity or notional");
    }

    private static ExposureMode parseExposureMode(final String value) {
        if ("filled_only".equals(value)) {
            return ExposureMode.FILLED_ONLY;
        }
        if ("filled_plus_working".equals(value)) {
            return ExposureMode.FILLED_PLUS_WORKING;
        }
        throw new ConfigValidationException("strategy.inventoryHedge.exposureMode", "exposureMode must be filled_only or filled_plus_working");
    }

    private static final class WorkingExposureConsumer implements OrderManager.LiveOrderConsumer {
        private int instrumentId;
        private long signedWorkingQtyScaled;

        void reset(final int instrumentId) {
            this.instrumentId = instrumentId;
            signedWorkingQtyScaled = 0L;
        }

        @Override
        public void onLiveOrder(final OrderState order) {
            if (order.instrumentId() != instrumentId || !order.isWorkingVisible()) {
                return;
            }
            signedWorkingQtyScaled += order.side() == Side.BUY.value()
                ? order.leavesQtyScaled()
                : -order.leavesQtyScaled();
        }
    }
}
