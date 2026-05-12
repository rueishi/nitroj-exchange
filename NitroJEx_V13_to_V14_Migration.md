# NitroJEx V13 to V14 Migration

## Status

Active

## Source

`NitroJEx_Master_Spec_V13.0.md` (frozen) → `NitroJEx_Master_Spec_V14.0.md` (active)

## Implementation Plan

`nitrojex_implementation_plan_v5.0.0.md`

---

# 1. Summary of Changes

V14 is a Smart Order Routing release. The migration keeps the V13 execution
strategy layer intact, then uses it for the first adaptive venue-indifferent
router: a trading strategy emits parent hedge intent, and
`SmartOrderRoutingExecution` owns fee-aware venue ranking, child-order
lifecycle, deterministic re-slicing, and parent terminal outcomes. Binance Spot
and `InventoryHedgeStrategy` are the enabling surfaces that let this execution
engine progression operate across real venue boundaries.

V14 makes three additions on top of V13:

    Add Binance Spot as venue ID 2 via FIX 4.4 L2.
    Add InventoryHedgeStrategy as the first venue-indifferent trading strategy.
    Add ParallelVenueExecution and SmartOrderRoutingExecution as
      venue-indifferent execution strategies.

V14 also activates capabilities that exist in V13 but had no second venue to operate against:

    Cross-venue arbitrage (ArbStrategy + MultiLegContingentExecution).
    Parallel multi-venue market making (independent MarketMakingStrategy
      instances per venue).
    Mixed-precision OwnOrderOverlay, ExternalLiquidityView, and
      ConsolidatedL2Book.

V14 makes no changes to V13 SBE schema, V13 trading-strategy behavior, V13 RiskEngine semantics, V13 deterministic replay principles, V13 base snapshot/load mechanics, or V13 hot-path allocation policy. V14 tightens the execution-strategy restart contract: execution-strategy engine state, execution-strategy plugin state, timer-owner state, and relevant execution-strategy stats must be present in Aeron Cluster snapshot/load and validated through replay/restart/rebuild.

Relevant execution-strategy stats are cluster state when they can affect
operator diagnosis, parent recovery, or future SOR policy/model inputs. They are
not disposable wall-clock telemetry. V14 SOR still routes from deterministic
fees, executable liquidity, side, quantity, and cluster-time re-slice rules, but
it must preserve observational venue stats such as child submissions, risk
rejects, execution-report counts, filled quantity, ack/fill latency windows, and
last submit/report cluster time so restart/rebuild begins from the same
policy-input state.

---

# 2. Compatibility Surface

## 2.1 V13 Code That Is Unchanged in V14

The following V13 components must not be modified by V14 work:

    ImmediateLimitExecution
    PostOnlyQuoteExecution
    MultiLegContingentExecution
    MarketMakingStrategy
    ArbStrategy
    StrategyContext (interface and ExecutionStrategyEngine accessor)
    ExecutionStrategy plugin contract
    ExecutionStrategyContext
    ExecutionStrategyEngine (registration, dispatch, timer routing)
    ParentOrderRegistry
    ParentOrderState lifecycle
    OrderManager
    OrderState (including parentOrderId field)
    RiskEngine semantics
    SBE schema (ParentOrderIntent, ParentOrderUpdate, ParentOrderTerminal)

If a V14 task touches one of these, the change is out of scope and must be
justified in a separate spec amendment, not embedded in a V14 task card.

## 2.2 V13 External Behavior Preservation

V14 must produce V13-equivalent results for these scenarios when run in
single-venue (Coinbase only) mode:

    MarketMaking → PostOnlyQuote quote refresh, post-only retry, staleness
      expiry.
    Arb → MultiLegContingent two-leg execution, leg timer, imbalance hedge,
      hedge rejection escalation.
    ImmediateLimit one-shot intent.
    Snapshot/load round trips for all V13 parent shapes.
    Deterministic replay for V13 parent shapes with no V14 strategy or
      execution plugin active.

V13-to-V14 behavior-equivalence tests are a required acceptance criterion for any task that exercises these surfaces in V14 (TASK-410, TASK-411, TASK-412, TASK-418, TASK-425).

## 2.3 V13 SBE Schema

V14 adds no new SBE messages and no new SBE fields. The V13 schema is sufficient because it already includes:

    ParentOrderIntent.venueSetId         (populated for the first time in V14)
    ParentOrderIntent.executionStrategyId (gains canonical IDs ParallelVenue
                                          and SOR)
    OrderState.parentOrderId             (unchanged)

V14 does not require SBE regeneration unless the schema file is unchanged but the build determines otherwise.

This is the important migration boundary for SOR: V14 does not add venue
routing behavior into existing trading strategies. The existing
`ExecutionStrategyEngine` remains the dispatch point, and the SOR plugin is the
new execution-side owner for multi-venue routing decisions and lifecycle state.

---

# 3. Configuration Migration

## 3.1 venues.toml

Add a new venue entry without modifying the existing Coinbase entry:

    [[venue]]
    id          = 2
    name        = "BINANCE"
    fixHost     = "fix-oe.binance.com"          # verify in TASK-401
    fixPort     = 9000                          # verify in TASK-401
    sandbox     = false
    fixPlugin   = "FIX_44"
    venuePlugin = "BINANCE"
    marketDataModel = "L2"
    orderEntryEnabled       = true
    marketDataEnabled       = true
    nativeReplaceSupported  = false

Operational rule: venue ID 2 must never be reused. All persisted state and replay
data references the numeric ID.

TASK-401 verified the current Binance Spot FIX production endpoints from the
official Binance documentation: Order Entry is
`tcp+tls://fix-oe.binance.com:9000`, Drop Copy is
`tcp+tls://fix-dc.binance.com:9000`, and Market Data is
`tcp+tls://fix-md.binance.com:9000`. Binance FIX uses FIX 4.4 over TLS with SNI
and Ed25519-only authentication. Logon signs `35`, `49`, `56`, `34`, and `52`
with SOH separators; the API key is tag `553`, the base64 signature is tag `96`,
and `ResetSeqNumFlag(141)=Y` is required.

## 3.2 instruments.toml

Add Binance instrument entries with distinct internal `instrumentId` from
Coinbase entries. The USD vs USDT decision is enforced here:

    [[instrument]]
    id            = 1
    symbol        = "BTC-USD"
    venue         = "COINBASE"
    venueSymbol   = "BTC-USD"
    base          = "BTC"
    quote         = "USD"
    pricePrecision = 2
    quantityPrecision = 8

    [[instrument]]
    id            = 3
    symbol        = "BTCUSDT"
    venue         = "BINANCE"
    venueSymbol   = "BTCUSDT"
    base          = "BTC"
    quote         = "USDT"
    pricePrecision = 2
    quantityPrecision = 8

`BTC-USD-COINBASE` (Coinbase, instrument ID 1) and `BTC-USDT-BINANCE`
(Binance, instrument ID 3) are distinct instruments. They do not auto-arb.
They are not consolidated onto a single instrument view because USD and USDT
have different settlement, risk, and accounting semantics. A future release
that wants consolidation must add an explicit product-alias/conversion layer,
define USD/USDT risk conversion rules, prove replay determinism for converted
prices and quantities, and update strategy eligibility rules before mapping
both venue symbols to one instrument ID.

## 3.3 strategies.toml

V14 adds three categories of strategy configuration. None require modification
of existing V13 entries.

Cross-venue arb (uses V13 code, no migration of code):

    [[strategy]]
    id                = "arb-btc-coinbase-binance"
    type              = "Arb"
    instrumentSet     = ["BTC-USD", "BTC-USDT"]    # see note below
    venueSet          = ["COINBASE", "BINANCE"]
    edgeThreshold     = 0.0005
    cooldownMicros    = 5000000
    executionStrategy = "MultiLegContingent"

Note: per §3.2, BTC-USD and BTC-USDT are distinct instruments. ArbStrategy must
operate on a configured per-venue instrument pair where the venue declares both
sides explicitly. The `instrumentSet` shape in `strategies.toml` must accept this
without auto-netting USD and USDT.

Parallel multi-venue MM (uses V13 code, no migration of code):

    [[strategy]]
    id                = "mm-btc-coinbase"
    type              = "MarketMaking"
    instrument        = "BTC-USD"
    venue             = "COINBASE"
    spreadBps         = 5
    quoteSizeBase     = 0.01
    inventoryTarget   = 0.0
    executionStrategy = "PostOnlyQuote"

    [[strategy]]
    id                = "mm-btc-binance"
    type              = "MarketMaking"
    instrument        = "BTC-USDT"
    venue             = "BINANCE"
    spreadBps         = 5
    quoteSizeBase     = 0.01
    inventoryTarget   = 0.0
    executionStrategy = "PostOnlyQuote"

InventoryHedge (V14 new):

    [[strategy]]
    id                = "hedge-btc-coinbase"
    type              = "InventoryHedge"
    instrument        = "BTC-USD"
    venueSet          = ["COINBASE"]
    thresholdMode     = "base_quantity"
    thresholdValue    = 1.0
    exposureMode      = "filled_plus_working"
    safeBandValue     = 0.5
    cooldownMicros    = 30000000
    executionStrategy = "ParallelVenue"

    [[strategy]]
    id                = "hedge-btc-binance"
    type              = "InventoryHedge"
    instrument        = "BTC-USDT"
    venueSet          = ["BINANCE"]
    thresholdMode     = "notional"
    thresholdValue    = 50000.0
    exposureMode      = "filled_plus_working"
    safeBandValue     = 25000.0
    cooldownMicros    = 30000000
    executionStrategy = "SOR"

Note that `venueSet` for hedge strategies is per-instrument and currently
single-venue per strategy instance. Multi-venue hedge for one instrument
requires the instrument to actually trade on multiple venues, which BTC-USD
and BTC-USDT do not (they are different instruments per §3.2). Future asset
configurations where the same instrument trades on multiple venues will have
populated multi-venue `venueSet` values.

## 3.4 fees.toml

V14 adds the per-venue fee schedule that `SmartOrderRoutingExecution` reads at
startup:

    [venue.1]
    makerFeeBps = 0
    takerFeeBps = 6

    [venue.2]
    makerFeeBps = 0
    takerFeeBps = 10

Fee schedule is loaded once at startup. Changes require restart. SOR does not
poll for fee updates during normal operation. Routing uses taker fees because
SOR emits IOC children; maker fees are retained in the table for cold-path
configuration visibility and future non-IOC routing, but they are not part of
the TASK-416 hot path.

## 3.5 risk.toml (or equivalent)

V14 adds per-venue and aggregate risk limits:

    [[risk.limit]]
    instrument   = "BTC-USD"
    venue        = "COINBASE"
    maxPosition  = 5.0
    maxNotional  = 250000.0

    [[risk.limit]]
    instrument   = "BTC-USDT"
    venue        = "BINANCE"
    maxPosition  = 5.0
    maxNotional  = 250000.0

    [[risk.limit.aggregate]]
    asset        = "BTC"
    maxPosition  = 8.0
    maxNotional  = 400000.0

Aggregate limits apply to `InventoryHedgeStrategy` notional-mode threshold
computation and to pre-trade risk checks. RiskEngine semantics are unchanged
from V13.

## 3.6 gateway-2.toml

New file:

    [process]
    venueId  = 2
    nodeRole = "gateway"

    [credentials]
    vaultPath = "secret/trading/binance/venue-2"

Production secrets remain outside repository configs. Credential resolution
must produce the Binance Ed25519 key pair from the approved secret source.

## 3.7 admin.toml

No new admin command surfaces are required. Existing admin commands gain
support for hedge-strategy pause/resume by passing the hedge strategy ID as the
target.

---

# 4. Operational Migration

## 4.1 Pre-Deployment Checklist

Before V14 deployment to a UAT or production-shadow environment:

    Confirm Binance FIX endpoint, port, schema version, FIX dictionary, and
      onboarding requirements from current Binance documentation.
    Confirm Binance API key has FIX_API trading permissions.
    Wire real Binance Ed25519 credential resolution from Vault or the
      approved secret source.
    Run V14 unit, integration, simulator, and live-wire E2E.
    Run V14 deterministic replay including mixed-precision scenarios.
    Run execution-strategy snapshot/load and restart/rebuild checks for
      engine state, plugin state, timer-owner state, active parent/child
      mappings, terminal reasons, venue stats, and SOR future-policy inputs.
    Run V14 JMH allocation and latency reports.
    Archive V14 evidence bundle per spec §14.
    Confirm USD/USDT instrument identity decision is reflected in operational
      runbooks (no auto-arb between BTC-USD and BTC-USDT).
    Confirm aggregate risk limits are sized correctly for two-venue exposure.
    Confirm hedge strategy thresholds are sized correctly for the expected
      operational profile.
    Confirm fee schedule reflects the actual fee tier the API key qualifies
      for.
    Run two-venue partial outage rehearsal: simulate Binance disconnect with
      active arb parents and active hedge parents on both venues.
    Run two-venue reconciliation rehearsal: prove balance, position, working
      order, and parent state reconciliation across both venues.

## 4.2 Deployment Order

V14 deployment is a two-stage process:

    Stage 1: Deploy V14 binaries with Binance disabled in venues.toml
             (orderEntryEnabled = false, marketDataEnabled = false).
             Confirm V13-equivalent single-venue operation. Run
             behavior-equivalence regression tests.

    Stage 2: Enable Binance in venues.toml. Start Binance gateway.
             Confirm two-venue operation. Activate cross-venue strategies in
             order: arb first (lowest-risk activation), parallel MM second,
             hedge with ParallelVenue third, hedge with SOR fourth.

Two-stage deployment isolates V14 binary changes from V14 venue activation,
making rollback decisions cleaner.

## 4.3 Rollback Plan

Signal: V14 venue, parent, execution, replay, snapshot, reconciliation, or
benchmark evidence fails after deployment rehearsal or production shadow.

Stage 2 rollback (binary deployed, Binance disabled):

    Set orderEntryEnabled = false and marketDataEnabled = false in
      venues.toml for venue ID 2.
    Restart Binance gateway (which then idles).
    Verify V13-equivalent single-venue operation continues.
    Use V13 strategies and execution plugins; do not configure V14 strategies
      until binary issue is resolved.

Stage 1 rollback (binary rollback to V13):

    Stop V14 cluster and gateway processes.
    Restore frozen V13 binaries and V13 strategy configuration.
    Use V13 migration and preflight documentation as the active release record.
    Reconcile all live venue orders and balances on Coinbase before resuming.
    Real Binance QA/UAT remains blocked.

V14 must not use real-venue QA/UAT to compensate for missing local evidence.

---

## 4.4 Snapshot, Replay, Restart, and Rebuild Contract

V14 keeps the V13 snapshot/load mechanics but expands the required contents for
the execution-strategy layer. A valid V14 snapshot/restart path must restore:

    ExecutionStrategyEngine dispatch state and deterministic timer ownership.
    Execution plugin state for ImmediateLimitExecution, PostOnlyQuoteExecution,
      MultiLegContingentExecution, ParallelVenueExecution, and
      SmartOrderRoutingExecution.
    Parent registry state, active parent/child links, fill aggregation, terminal
      reasons, and cancel/reject/recovery state.
    Relevant per-strategy and per-venue stats that are derived from ordered
      cluster events and useful for recovery, diagnosis, or future policy.
    SOR future-policy/model inputs: per-venue child submissions, risk rejects,
      ack/fill/reject/cancel/expire report counts, filled quantity, ack/fill
      latency windows, and last submit/report cluster time.

Latency windows matter for SOR because they can become routing policy inputs in
later releases. V14 does not use those windows to rank venues, so preserving
them must not change current V14 routing decisions. The acceptance requirement
is stronger: after snapshot/load, deterministic replay, restart, or rebuild, the
stats and policy-input state must match the original ordered event stream while
the V14 SOR route choice remains governed only by configured fees, executable
liquidity, side, quantity, and cluster-time re-slice eligibility.

TASK-418 and TASK-419 evidence must cover this contract. If a stat is derived
only from non-deterministic wall-clock observation and cannot affect recovery,
diagnosis, or future policy, it may remain telemetry. If it is derived from
cluster-ordered events and could be used by future SOR modelling or policy, it
must be included in snapshot/load and replay/restart/rebuild validation.

---

# 5. Behavior Changes Operations Should Know

## 5.1 What Looks Different in V14

Operators should expect these visible behavior changes once V14 is fully
activated:

    Two gateway processes instead of one.
    Two FIX session lifecycles to monitor (Coinbase HMAC logon, Binance
      Ed25519 logon).
    Cross-venue arb parents emit MultiLegIntent with one leg per venue.
    Hedge parents emit ParentOrderIntent with venueSetId populated for the
      first time.
    ParallelVenueExecution emits multiple parallel children per parent.
    SmartOrderRoutingExecution emits IOC children to the cheapest
      fee-adjusted executable venues first, re-slices residual quantity on
      deterministic market-data ticks, enforces a minimum re-slice interval,
      and cancels old children before replacement submissions.
    OwnOrderOverlay returns precise numbers for Coinbase positions and
      conservative numbers for Binance positions.
    Reconciliation surface covers two venues.
    Execution-strategy snapshot/restart evidence now includes strategy-owned
      venue stats and SOR future-policy inputs, even though those inputs do not
      alter V14 SOR route ranking.
    Per-venue and aggregate risk limits replace the implicit
      single-venue-equals-aggregate model.

## 5.2 What Is Unchanged in V14

The following V13 behaviors remain identical when V14 single-venue scenarios
are run:

    MarketMakingStrategy quote computation, post-only retry, staleness expiry
      on the Coinbase side.
    ArbStrategy edge detection logic on a single instrument.
    MultiLegContingentExecution leg coordination, hedge submission,
      kill-switch escalation.
    ImmediateLimitExecution, PostOnlyQuoteExecution single-venue lifecycle.
    OrderManager, RiskEngine, ParentOrderRegistry, PortfolioEngine APIs and
      semantics.
    SBE message wire format.
    Snapshot/load file format for V13 parent shapes.

V13-to-V14 behavior-equivalence tests are required for the unchanged surfaces.
Any divergence is a V14 bug, not a planned change.

## 5.3 New Operational Runbooks

V14 introduces new operational scenarios. The README contains the operator
runbooks for each of these release-blocking cases:

    Hedge parent stuck in non-terminal state.
    Hedge cooldown not advancing under deterministic cluster time.
    ParallelVenue partial-outage with one venue disconnected.
    SOR re-slice loop (re-slicing more frequently than minimum interval
      should allow — implies clock or counter bug).
    Fee schedule misconfiguration (SOR routing decisions look wrong).
    Two-venue reconciliation mismatch.
    Binance Ed25519 credential rotation.
    V14 → V13 rollback mid-trade with active hedge or arb parents.

Runbook ownership:

    Hedge stuck and hedge cooldown stuck are owned by the InventoryHedgeStrategy
      release owner. Automated unit/replay coverage proves state transitions;
      the runbook covers operator diagnosis when live state and deterministic
      evidence must be reconciled.
    ParallelVenue partial-outage is owned by the execution strategy release
      owner. Automated live-wire coverage proves local simulator behavior; the
      runbook covers venue-session isolation and manual reconciliation.
    SOR re-slice loop and fee schedule misconfiguration are owned by the SOR
      release owner. Automated tests cover minimum interval, ordering, and fee
      ranking; the runbook covers production config review and pause/rollback.
    V14 → V13 rollback is owned by the release manager. It requires V13
      preflight evidence and reconciliation of all V14 parent/child state before
      re-enabling traffic.

TASK-424 changes documentation and configuration evidence only. It does not
change production Java behavior, parser behavior, bounded state, replay state,
or hot-path code. Therefore the Section 2 positive, negative, edge, malformed,
capacity, failure/counter, replay, integration, allocation, latency, and
V13-to-V14 behavior-equivalence automation categories are non-applicable to
TASK-424 itself; their behavior owners are the task cards that implemented the
referenced surfaces. TASK-424 validation is documentation/config review plus the
standard Gradle gates to prove no repository regression.

---

# 6. What V14 Explicitly Does Not Migrate

V14 is not a migration of:

    Coinbase venue plugin (unchanged).
    FIX 4.4 protocol plugin (reused).
    FIXT.1.1/FIX 5.0 SP2 protocol plugin (unchanged, still used by Coinbase).
    SBE schema.
    V13 trading strategies or execution strategies.
    RiskEngine semantics.
    Deterministic replay rules from V12 and V13.
    Base snapshot/load mechanics or V13 parent snapshot format; V14 only
      expands the execution-strategy contents that must be captured.
    Hot-path allocation policy.

These are baseline-frozen and must not change as part of V14 work. Any V14 task
that proposes touching them is out of scope.

---

# 7. Reference

Active V14 spec:        `NitroJEx_Master_Spec_V14.0.md`
Active V14 plan:        `nitrojex_implementation_plan_v5.0.0.md`
Frozen V13 spec:        `NitroJEx_Master_Spec_V13.0.md`
Frozen V13 plan:        `nitrojex_implementation_plan_v4.0.0.md`
V12 → V13 migration:    `NitroJEx_V12_to_V13_Migration.md`
V11 → V12 migration:    `NitroJEx_V11_to_V12_Migration.md`
V10 → V11 migration:    `NitroJEx_V10_to_V11_Migration.md`

Standard automated check command:

    JAVA_HOME=/usr/lib/jvm/java-21-openjdk-amd64 ./gradlew check

V14 preflight gate:

    JAVA_HOME=/usr/lib/jvm/java-21-openjdk-amd64 scripts/v14-preflight-check.sh

V14 evidence archive command after the full TASK-425 gate succeeds:

    JAVA_HOME=/usr/lib/jvm/java-21-openjdk-amd64 scripts/archive-v14-release-evidence.sh

The V14 preflight extends V12 and V13 preflights. Every gate from prior
releases remains required. The V14 gate must be archived with:

    Full `clean`, `check`, and `e2eTest` output.
    `:platform-benchmarks:jmh` allocation report with `-prof gc`.
    `:platform-benchmarks:jmhLatencyReport` percentile report.
    Mixed-precision Coinbase `BTC-USD` / Binance `BTCUSDT` identity evidence.
    Cross-venue arb evidence.
    Hedge × ParallelVenue and Hedge × SOR live-wire evidence.
    Deterministic replay evidence for mixed precision and SOR re-slice.
    Execution-strategy snapshot/load and restart/rebuild evidence covering
      engine/plugin state, timer-owner state, venue stats, and SOR
      future-policy inputs.
    Two-venue reconciliation evidence.

Real Coinbase and Binance QA/UAT remain blocked until the V12, V13, and V14
automated gates and manual operational evidence are complete.
