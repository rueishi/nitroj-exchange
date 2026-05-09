package ig.rueishi.nitroj.exchange.strategy;

import ig.rueishi.nitroj.exchange.cluster.RiskDecision;
import ig.rueishi.nitroj.exchange.common.ArbStrategyConfig;
import ig.rueishi.nitroj.exchange.common.Ids;
import ig.rueishi.nitroj.exchange.messages.BooleanType;
import ig.rueishi.nitroj.exchange.messages.EntryType;
import ig.rueishi.nitroj.exchange.messages.ParentTerminalReason;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * TASK-410 configuration/integration coverage for cross-venue Arb activation.
 *
 * <p>This task intentionally changes no ArbStrategy or MultiLegContingent code.
 * The applicable coverage is configuration-shaped activation, mixed-precision
 * self-cross checks, leg submission/fill/reject behavior, hedge and cooldown
 * flows, and V13 equivalence. Allocation/latency categories are non-applicable:
 * no runtime hot path changes are made by TASK-410.</p>
 */
final class CrossVenueArbActivationTest {
    @Test
    void edgeDetectionBetweenCoinbaseAndBinance_submitsLegsToBothVenues() {
        final ArbStrategyTest.Harness harness = harness();
        seedCoinbaseBinanceOpportunity(harness);

        triggerBinanceBid(harness);

        assertThat(harness.order().orders).hasSize(2);
        assertThat(harness.order().orders).extracting(order -> order.venueId)
            .containsExactly(Ids.VENUE_COINBASE, Ids.VENUE_BINANCE);
    }

    @Test
    void selfCrossWithMixedPrecision_suppressesFalseOpportunity() {
        final ArbStrategyTest.Harness harness = harness();
        seedCoinbaseBinanceOpportunity(harness);
        harness.marketView().ownOrderOverlay().upsert(
            40L, "CB-OWN-ASK", Ids.VENUE_COINBASE, Ids.INSTRUMENT_BTC_USD,
            EntryType.ASK, 100_000L * Ids.SCALE, 10L * Ids.SCALE, true);

        triggerBinanceBid(harness);

        assertThat(harness.order().orders).isEmpty();
    }

    @Test
    void imbalancedLegFill_submitsHedgeToOppositeVenue() {
        final ArbStrategyTest.Harness harness = executedHarness();
        harness.cluster().offerLengths.clear();

        harness.strategy().onFill(ArbStrategyTest.execution(harness.strategy().leg1ClOrdId(),
            3L * Ids.SCALE, BooleanType.TRUE));
        harness.strategy().onFill(ArbStrategyTest.execution(harness.strategy().leg2ClOrdId(),
            Ids.SCALE, BooleanType.TRUE));

        assertThat(harness.order().orders).extracting(order -> order.strategyId).contains(Ids.STRATEGY_ARB_HEDGE);
        assertThat(harness.order().orders.get(harness.order().orders.size() - 1).venueId)
            .isEqualTo(Ids.VENUE_BINANCE);
    }

    @Test
    void hedgeRejectEscalatesKillSwitchAcrossVenuesAndCooldownSuppressesNextArb() {
        final ArbStrategyTest.Harness harness = executedHarness();
        harness.risk().decision = RiskDecision.REJECT_KILL_SWITCH;

        harness.strategy().onFill(ArbStrategyTest.execution(harness.strategy().leg1ClOrdId(),
            3L * Ids.SCALE, BooleanType.TRUE));
        harness.strategy().onFill(ArbStrategyTest.execution(harness.strategy().leg2ClOrdId(),
            Ids.SCALE, BooleanType.TRUE));
        harness.order().orders.clear();
        triggerBinanceBid(harness);

        assertThat(harness.risk().killSwitchReason).isEqualTo("hedge_failure");
        assertThat(harness.strategy().cooldownUntilMicros()).isGreaterThan(harness.cluster().time);
        assertThat(harness.order().orders).isEmpty();
    }

    @Test
    void legRejectTerminalDrivesCooldown() {
        final ArbStrategyTest.Harness harness = executedHarness();

        deliverChildRejectedParentTerminal(harness);

        assertThat(harness.strategy().cooldownUntilMicros()).isGreaterThan(harness.cluster().time);
    }

    @Test
    void v13SingleVenueEquivalenceFixtureStillProducesTwoLegs() {
        final ArbStrategyTest.Harness harness = ArbStrategyTest.executedHarness();

        assertThat(harness.order().orders).hasSize(2);
        assertThat(harness.order().orders).extracting(order -> order.venueId)
            .containsExactly(Ids.VENUE_COINBASE, Ids.VENUE_COINBASE_SANDBOX);
    }

    private static ArbStrategyTest.Harness executedHarness() {
        final ArbStrategyTest.Harness harness = harness();
        seedCoinbaseBinanceOpportunity(harness);
        triggerBinanceBid(harness);
        return harness;
    }

    private static ArbStrategyTest.Harness harness() {
        return ArbStrategyTest.harness(config());
    }

    private static ArbStrategyConfig config() {
        final long[] fees = new long[Ids.MAX_VENUES + 1];
        final long[] baseSlip = new long[Ids.MAX_VENUES + 1];
        final long[] slope = new long[Ids.MAX_VENUES + 1];
        baseSlip[Ids.VENUE_COINBASE] = 5;
        baseSlip[Ids.VENUE_BINANCE] = 5;
        return new ArbStrategyConfig(
            Ids.INSTRUMENT_BTC_USD,
            new int[]{Ids.VENUE_COINBASE, Ids.VENUE_BINANCE},
            1,
            fees,
            baseSlip,
            slope,
            Ids.SCALE,
            10L * Ids.SCALE,
            100,
            5_000_000,
            10_000_000);
    }

    private static void seedCoinbaseBinanceOpportunity(final ArbStrategyTest.Harness harness) {
        ArbStrategyTest.apply(harness, Ids.VENUE_COINBASE, EntryType.BID, 99_900L * Ids.SCALE);
        ArbStrategyTest.apply(harness, Ids.VENUE_COINBASE, EntryType.ASK, 100_000L * Ids.SCALE);
        ArbStrategyTest.apply(harness, Ids.VENUE_BINANCE, EntryType.BID, 100_500L * Ids.SCALE);
        ArbStrategyTest.apply(harness, Ids.VENUE_BINANCE, EntryType.ASK, 100_600L * Ids.SCALE);
    }

    private static void triggerBinanceBid(final ArbStrategyTest.Harness harness) {
        harness.strategy().onMarketData(
            ArbStrategyTest.decoder(Ids.VENUE_BINANCE, EntryType.BID, 100_500L * Ids.SCALE));
    }

    private static void deliverChildRejectedParentTerminal(final ArbStrategyTest.Harness harness) {
        final var buffer = new org.agrona.concurrent.UnsafeBuffer(new byte[128]);
        new ig.rueishi.nitroj.exchange.messages.ParentOrderTerminalEncoder()
            .wrapAndApplyHeader(buffer, 0, new ig.rueishi.nitroj.exchange.messages.MessageHeaderEncoder())
            .parentOrderId(harness.strategy().arbAttemptId())
            .strategyId((short)Ids.STRATEGY_ARB)
            .executionStrategyId(ig.rueishi.nitroj.exchange.common.ExecutionStrategyIds.MULTI_LEG_CONTINGENT)
            .finalCumFillQtyScaled(0L)
            .terminalReason(ParentTerminalReason.CHILD_REJECTED);
        final var decoder = new ig.rueishi.nitroj.exchange.messages.ParentOrderTerminalDecoder();
        decoder.wrapAndApplyHeader(buffer, 0, new ig.rueishi.nitroj.exchange.messages.MessageHeaderDecoder());
        harness.strategy().onParentTerminal(decoder);
    }
}
