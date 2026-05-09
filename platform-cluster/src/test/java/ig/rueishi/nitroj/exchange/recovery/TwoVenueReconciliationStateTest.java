package ig.rueishi.nitroj.exchange.recovery;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import ig.rueishi.nitroj.exchange.common.Ids;
import ig.rueishi.nitroj.exchange.execution.ParentOrderState;
import org.junit.jupiter.api.Test;

/**
 * TASK-413 coverage for the V14 two-venue disconnect/reconnect surface.
 *
 * <p>The existing {@code RecoveryCoordinatorImpl} tests own SBE query, balance,
 * timer, parent snapshot/load, and single-venue state-machine behavior. These
 * task-owned tests add the two-venue matrix requested by TASK-413. Parser and
 * allocation benchmark categories are non-applicable because this task adds no
 * parser and declares no latency-sensitive hot path.</p>
 */
final class TwoVenueReconciliationStateTest {
    private static final long ARB_PARENT = 101L;
    private static final long MM_COINBASE_PARENT = 201L;
    private static final long MM_BINANCE_PARENT = 202L;

    @Test
    void oneVenueDisconnectedWithActiveArbParentKeepsOtherVenueConnected() {
        final TwoVenueReconciliationState state = new TwoVenueReconciliationState();
        state.registerActiveParent(ARB_PARENT, Ids.STRATEGY_ARB, Ids.VENUE_BINANCE, 1);

        state.onVenueDisconnected(Ids.VENUE_BINANCE);

        assertThat(state.isVenueConnected(Ids.VENUE_BINANCE)).isFalse();
        assertThat(state.isVenueConnected(Ids.VENUE_COINBASE)).isTrue();
        assertThat(state.activeParentCount(Ids.VENUE_BINANCE)).isEqualTo(1);
        assertThat(state.activeChildCount(Ids.VENUE_BINANCE)).isEqualTo(1);
        assertThat(state.parentTerminalReason(ARB_PARENT)).isEqualTo(ParentOrderState.REASON_EXECUTION_ABORTED);
    }

    @Test
    void oneVenueDisconnectedWithParallelMarketMakingParentsIsVenueScoped() {
        final TwoVenueReconciliationState state = new TwoVenueReconciliationState();
        state.registerActiveParent(MM_COINBASE_PARENT, Ids.STRATEGY_MARKET_MAKING, Ids.VENUE_COINBASE, 2);
        state.registerActiveParent(MM_BINANCE_PARENT, Ids.STRATEGY_MARKET_MAKING, Ids.VENUE_BINANCE, 2);

        state.onVenueDisconnected(Ids.VENUE_COINBASE);

        assertThat(state.activeParentCount(Ids.VENUE_COINBASE)).isEqualTo(1);
        assertThat(state.activeParentCount(Ids.VENUE_BINANCE)).isEqualTo(1);
        assertThat(state.activeChildCount(Ids.VENUE_COINBASE)).isEqualTo(2);
        assertThat(state.activeChildCount(Ids.VENUE_BINANCE)).isEqualTo(2);
        assertThat(state.parentTerminalReason(MM_COINBASE_PARENT)).isEqualTo(ParentOrderState.REASON_EXECUTION_ABORTED);
        assertThat(state.parentTerminalReason(MM_BINANCE_PARENT)).isEqualTo(ParentOrderState.REASON_NONE);
    }

    @Test
    void reconcilesBalancesPositionsAndWorkingOrdersAcrossBothVenues() {
        final TwoVenueReconciliationState state = new TwoVenueReconciliationState();

        state.recordVenueSnapshot(Ids.VENUE_COINBASE, 10L, 10L, 5L, 5L, 2, 2);
        state.recordVenueSnapshot(Ids.VENUE_BINANCE, 20L, 20L, -3L, -3L, 1, 1);

        assertThat(state.isVenueReconciled(Ids.VENUE_COINBASE)).isTrue();
        assertThat(state.isVenueReconciled(Ids.VENUE_BINANCE)).isTrue();
        assertThat(state.killSwitchReason()).isNull();
    }

    @Test
    void unreconciledVenueStateRequestsKillSwitch() {
        final TwoVenueReconciliationState state = new TwoVenueReconciliationState();

        state.recordVenueSnapshot(Ids.VENUE_COINBASE, 10L, 9L, 5L, 5L, 2, 2);

        assertThat(state.isVenueReconciled(Ids.VENUE_COINBASE)).isFalse();
        assertThat(state.killSwitchReason()).isEqualTo(TwoVenueReconciliationState.UNRECONCILED_VENUE_STATE);
        assertThat(state.unreconciledVenueStates()).isEqualTo(1L);
    }

    @Test
    void recoveryAfterReconnectClearsOutageReasonWhenVenueStateMatches() {
        final TwoVenueReconciliationState state = new TwoVenueReconciliationState();
        state.registerActiveParent(MM_BINANCE_PARENT, Ids.STRATEGY_MARKET_MAKING, Ids.VENUE_BINANCE, 2);
        state.onVenueDisconnected(Ids.VENUE_BINANCE);
        state.recordVenueSnapshot(Ids.VENUE_BINANCE, 10L, 9L, 5L, 5L, 2, 2);

        state.onVenueReconnected(Ids.VENUE_BINANCE);
        state.recordVenueSnapshot(Ids.VENUE_BINANCE, 10L, 10L, 5L, 5L, 2, 2);
        state.completeRecovery(Ids.VENUE_BINANCE);

        assertThat(state.isVenueConnected(Ids.VENUE_BINANCE)).isTrue();
        assertThat(state.isVenueReconciled(Ids.VENUE_BINANCE)).isTrue();
        assertThat(state.killSwitchReason()).isNull();
        assertThat(state.parentTerminalReason(MM_BINANCE_PARENT)).isEqualTo(ParentOrderState.REASON_NONE);
        assertThat(state.disconnects()).isEqualTo(1L);
        assertThat(state.reconnects()).isEqualTo(1L);
    }

    @Test
    void parentTerminalReasonConsistencyAcrossVenuePartialOutage() {
        final TwoVenueReconciliationState state = new TwoVenueReconciliationState();
        state.registerActiveParent(MM_COINBASE_PARENT, Ids.STRATEGY_MARKET_MAKING, Ids.VENUE_COINBASE, 1);
        state.registerActiveParent(MM_BINANCE_PARENT, Ids.STRATEGY_MARKET_MAKING, Ids.VENUE_BINANCE, 1);

        state.onVenueDisconnected(Ids.VENUE_BINANCE);

        assertThat(state.parentTerminalReason(MM_BINANCE_PARENT)).isEqualTo(ParentOrderState.REASON_EXECUTION_ABORTED);
        assertThat(state.parentTerminalReason(MM_COINBASE_PARENT)).isEqualTo(ParentOrderState.REASON_NONE);
    }

    @Test
    void malformedInputsRejectedDeterministically() {
        final TwoVenueReconciliationState state = new TwoVenueReconciliationState();

        assertThatThrownBy(() -> new TwoVenueReconciliationState(0))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("parentCapacity");
        assertThatThrownBy(() -> state.onVenueDisconnected(0))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("venueId");
        assertThatThrownBy(() -> state.registerActiveParent(0L, Ids.STRATEGY_ARB, Ids.VENUE_COINBASE, 1))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("parentOrderId");
        assertThatThrownBy(() -> state.recordVenueSnapshot(Ids.VENUE_COINBASE, 0L, 0L, 0L, 0L, -1, 0))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("working order");
    }

    @Test
    void capacityFullRejectedDeterministically() {
        final TwoVenueReconciliationState state = new TwoVenueReconciliationState(1);
        state.registerActiveParent(1L, Ids.STRATEGY_ARB, Ids.VENUE_COINBASE, 1);

        assertThatThrownBy(() -> state.registerActiveParent(2L, Ids.STRATEGY_ARB, Ids.VENUE_BINANCE, 1))
            .isInstanceOf(IllegalStateException.class)
            .hasMessageContaining("capacity");
    }
}
