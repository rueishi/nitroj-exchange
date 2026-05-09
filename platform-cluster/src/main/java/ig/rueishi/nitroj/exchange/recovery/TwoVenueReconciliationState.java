package ig.rueishi.nitroj.exchange.recovery;

import ig.rueishi.nitroj.exchange.common.Ids;
import ig.rueishi.nitroj.exchange.execution.ParentOrderState;
import java.util.Arrays;

/**
 * Deterministic V14 two-venue reconciliation surface.
 *
 * <p>The clustered recovery coordinator still owns gateway reconnect state and
 * RiskEngine recovery locks. This surface records the two-venue facts needed by
 * V14 tooling and task-owned recovery checks: which venue is disconnected, which
 * active parents have children there, whether venue balance/position/working
 * order snapshots reconcile, and which parent terminal reason is consistent for
 * a partial venue outage. It stores only primitive arrays so replayed recovery
 * decisions are deterministic and allocation-free after construction.</p>
 *
 * <p>Non-goals: this class does not contact venues, replace the existing
 * {@code RecoveryCoordinatorImpl}, or clear real-venue QA/UAT. Real Coinbase and
 * Binance UAT remains blocked until local gates and release sign-off pass.</p>
 */
public final class TwoVenueReconciliationState {
    public static final String UNRECONCILED_VENUE_STATE = "unreconciled_venue_state";
    private static final int DEFAULT_PARENT_CAPACITY = 256;

    private final boolean[] connected = new boolean[Ids.MAX_VENUES + 1];
    private final boolean[] balanceReconciled = new boolean[Ids.MAX_VENUES + 1];
    private final boolean[] positionReconciled = new boolean[Ids.MAX_VENUES + 1];
    private final boolean[] workingOrdersReconciled = new boolean[Ids.MAX_VENUES + 1];
    private final long[] venueBalances = new long[Ids.MAX_VENUES + 1];
    private final long[] internalBalances = new long[Ids.MAX_VENUES + 1];
    private final long[] venuePositions = new long[Ids.MAX_VENUES + 1];
    private final long[] internalPositions = new long[Ids.MAX_VENUES + 1];
    private final int[] venueWorkingOrders = new int[Ids.MAX_VENUES + 1];
    private final int[] internalWorkingOrders = new int[Ids.MAX_VENUES + 1];
    private final long[] parentIds;
    private final int[] parentStrategyIds;
    private final int[] parentVenueIds;
    private final int[] parentChildCounts;
    private final byte[] parentTerminalReasons;

    private int activeParents;
    private long disconnects;
    private long reconnects;
    private long unreconciledVenueStates;
    private String killSwitchReason;

    public TwoVenueReconciliationState() {
        this(DEFAULT_PARENT_CAPACITY);
    }

    public TwoVenueReconciliationState(final int parentCapacity) {
        if (parentCapacity <= 0) {
            throw new IllegalArgumentException("parentCapacity must be positive");
        }
        parentIds = new long[parentCapacity];
        parentStrategyIds = new int[parentCapacity];
        parentVenueIds = new int[parentCapacity];
        parentChildCounts = new int[parentCapacity];
        parentTerminalReasons = new byte[parentCapacity];
        Arrays.fill(connected, true);
        Arrays.fill(balanceReconciled, true);
        Arrays.fill(positionReconciled, true);
        Arrays.fill(workingOrdersReconciled, true);
    }

    public void registerActiveParent(
        final long parentOrderId,
        final int strategyId,
        final int venueId,
        final int childCount) {
        validateVenue(venueId);
        if (parentOrderId <= 0L || childCount <= 0) {
            throw new IllegalArgumentException("parentOrderId and childCount must be positive");
        }
        final int existing = findParent(parentOrderId);
        final int slot = existing >= 0 ? existing : allocateParent(parentOrderId);
        parentStrategyIds[slot] = strategyId;
        parentVenueIds[slot] = venueId;
        parentChildCounts[slot] = childCount;
        parentTerminalReasons[slot] = ParentOrderState.REASON_NONE;
    }

    public void onVenueDisconnected(final int venueId) {
        validateVenue(venueId);
        connected[venueId] = false;
        balanceReconciled[venueId] = false;
        positionReconciled[venueId] = false;
        workingOrdersReconciled[venueId] = false;
        disconnects++;
        for (int i = 0; i < parentIds.length; i++) {
            if (parentIds[i] != 0L && parentVenueIds[i] == venueId) {
                parentTerminalReasons[i] = ParentOrderState.REASON_EXECUTION_ABORTED;
            }
        }
    }

    public void onVenueReconnected(final int venueId) {
        validateVenue(venueId);
        connected[venueId] = true;
        reconnects++;
    }

    public void recordVenueSnapshot(
        final int venueId,
        final long venueBalanceScaled,
        final long internalBalanceScaled,
        final long venuePositionScaled,
        final long internalPositionScaled,
        final int venueWorkingOrderCount,
        final int internalWorkingOrderCount) {
        validateVenue(venueId);
        if (venueWorkingOrderCount < 0 || internalWorkingOrderCount < 0) {
            throw new IllegalArgumentException("working order counts must be non-negative");
        }
        venueBalances[venueId] = venueBalanceScaled;
        internalBalances[venueId] = internalBalanceScaled;
        venuePositions[venueId] = venuePositionScaled;
        internalPositions[venueId] = internalPositionScaled;
        venueWorkingOrders[venueId] = venueWorkingOrderCount;
        internalWorkingOrders[venueId] = internalWorkingOrderCount;
        balanceReconciled[venueId] = venueBalanceScaled == internalBalanceScaled;
        positionReconciled[venueId] = venuePositionScaled == internalPositionScaled;
        workingOrdersReconciled[venueId] = venueWorkingOrderCount == internalWorkingOrderCount;
        if (!isVenueReconciled(venueId)) {
            unreconciledVenueStates++;
            killSwitchReason = UNRECONCILED_VENUE_STATE;
        }
    }

    public void completeRecovery(final int venueId) {
        validateVenue(venueId);
        if (!connected[venueId] || !isVenueReconciled(venueId)) {
            return;
        }
        for (int i = 0; i < parentIds.length; i++) {
            if (parentIds[i] != 0L && parentVenueIds[i] == venueId) {
                parentTerminalReasons[i] = ParentOrderState.REASON_NONE;
            }
        }
        if (allTrackedVenuesReconciled()) {
            killSwitchReason = null;
        }
    }

    public boolean isVenueConnected(final int venueId) {
        validateVenue(venueId);
        return connected[venueId];
    }

    public boolean isVenueReconciled(final int venueId) {
        validateVenue(venueId);
        return balanceReconciled[venueId] && positionReconciled[venueId] && workingOrdersReconciled[venueId];
    }

    public int activeParentCount(final int venueId) {
        validateVenue(venueId);
        int count = 0;
        for (int i = 0; i < parentIds.length; i++) {
            if (parentIds[i] != 0L && parentVenueIds[i] == venueId) {
                count++;
            }
        }
        return count;
    }

    public int activeChildCount(final int venueId) {
        validateVenue(venueId);
        int count = 0;
        for (int i = 0; i < parentIds.length; i++) {
            if (parentIds[i] != 0L && parentVenueIds[i] == venueId) {
                count += parentChildCounts[i];
            }
        }
        return count;
    }

    public byte parentTerminalReason(final long parentOrderId) {
        final int slot = findParent(parentOrderId);
        return slot < 0 ? ParentOrderState.REASON_NONE : parentTerminalReasons[slot];
    }

    public String killSwitchReason() {
        return killSwitchReason;
    }

    public long disconnects() {
        return disconnects;
    }

    public long reconnects() {
        return reconnects;
    }

    public long unreconciledVenueStates() {
        return unreconciledVenueStates;
    }

    public long venueBalance(final int venueId) {
        validateVenue(venueId);
        return venueBalances[venueId];
    }

    public long internalBalance(final int venueId) {
        validateVenue(venueId);
        return internalBalances[venueId];
    }

    public long venuePosition(final int venueId) {
        validateVenue(venueId);
        return venuePositions[venueId];
    }

    public long internalPosition(final int venueId) {
        validateVenue(venueId);
        return internalPositions[venueId];
    }

    public int venueWorkingOrders(final int venueId) {
        validateVenue(venueId);
        return venueWorkingOrders[venueId];
    }

    public int internalWorkingOrders(final int venueId) {
        validateVenue(venueId);
        return internalWorkingOrders[venueId];
    }

    private boolean allTrackedVenuesReconciled() {
        for (int venueId = 1; venueId < connected.length; venueId++) {
            if (connected[venueId] && !isVenueReconciled(venueId)) {
                return false;
            }
        }
        return true;
    }

    private int allocateParent(final long parentOrderId) {
        for (int i = 0; i < parentIds.length; i++) {
            if (parentIds[i] == 0L) {
                parentIds[i] = parentOrderId;
                activeParents++;
                return i;
            }
        }
        throw new IllegalStateException("active parent capacity full: " + activeParents);
    }

    private int findParent(final long parentOrderId) {
        for (int i = 0; i < parentIds.length; i++) {
            if (parentIds[i] == parentOrderId) {
                return i;
            }
        }
        return -1;
    }

    private static void validateVenue(final int venueId) {
        if (venueId <= 0 || venueId > Ids.MAX_VENUES) {
            throw new IllegalArgumentException("venueId out of range: " + venueId);
        }
    }
}
