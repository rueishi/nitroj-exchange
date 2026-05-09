package ig.rueishi.nitroj.exchange.tooling.reconciliation;

import ig.rueishi.nitroj.exchange.recovery.TwoVenueReconciliationState;

/**
 * Lightweight operator-facing formatter for V14 two-venue reconciliation state.
 *
 * <p>The report is deliberately read-only: tooling can render the local cluster
 * state for runbooks without mutating recovery decisions, replacing automated
 * tests, or implying real Coinbase/Binance UAT approval.</p>
 */
public final class TwoVenueReconciliationReport {
    public String venueLine(final TwoVenueReconciliationState state, final int venueId) {
        return "venue=" + venueId
            + " connected=" + state.isVenueConnected(venueId)
            + " reconciled=" + state.isVenueReconciled(venueId)
            + " activeParents=" + state.activeParentCount(venueId)
            + " activeChildren=" + state.activeChildCount(venueId)
            + " venueBalance=" + state.venueBalance(venueId)
            + " internalBalance=" + state.internalBalance(venueId)
            + " venuePosition=" + state.venuePosition(venueId)
            + " internalPosition=" + state.internalPosition(venueId)
            + " venueWorkingOrders=" + state.venueWorkingOrders(venueId)
            + " internalWorkingOrders=" + state.internalWorkingOrders(venueId);
    }
}
