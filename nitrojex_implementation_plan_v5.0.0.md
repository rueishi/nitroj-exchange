# NitroJEx - V14 Binance, Hedger, and Multi-Venue Routing Implementation Plan

## Version

v5.0.0

## Based On

`nitrojex_implementation_plan_v4.0.0.md` (frozen V13 execution strategy layer baseline)

## Scope

Implements `NitroJEx_Master_Spec_V14.0.md`.

V14's major feature is **Smart Order Routing**. The plan adds Binance and
InventoryHedge because SOR needs a real second venue and a venue-indifferent
parent-intent producer, but the central design goal is to progress the V13
execution engine naturally: strategies submit intent, while execution strategy
plugins own fee-aware venue selection, child-order routing, re-slice ordering,
and parent/child terminal state.

## Source Spec

`NitroJEx_Master_Spec_V14.0.md`

## Rules

- Do not modify frozen V10, V11, V12, or V13 baseline artifacts.
- Do not reuse TASK-001 through TASK-318.
- V14 task cards start at TASK-401.
- Preserve V13 deterministic replay, parent registry semantics, snapshot/load mechanics, hot-path allocation policy, benchmark gates, simulator live-wire gates, and production preflight.
- Preserve V12 deterministic replay, hot-path allocation policy, benchmark gates, simulator live-wire gates, REST-boundary policy, and production preflight.
- Preserve V11 venue plugin and FIX protocol plugin separation.
- Add the second venue (Binance) without modifying Coinbase code, V13 execution strategies, V13 trading strategies, or shared FIX 4.4 protocol plugin code.
- Add new venue-indifferent producer (`InventoryHedgeStrategy`) and consumers (`ParallelVenueExecution`, `SmartOrderRoutingExecution`) as peer plugins, not as modifications to existing plugins.
- Treat `SmartOrderRoutingExecution` as the headline V14 execution feature and
  the proof point for the V13 execution engine design: SOR must keep routing,
  re-slice, lifecycle, timer, and terminal behavior inside the execution layer,
  not inside trading strategies.
- Treat V14 as superseding V13 only for the venue, trading strategy, and execution strategy surfaces; V13 remains the frozen execution strategy layer baseline.
- Every child order must still pass `RiskEngine` before submission.
- Every production-code task must include task-owned tests at the same or stronger coverage level as V13.
- No V14 production-code task is complete until expected behavior is fully verified across applicable positive, negative, edge, malformed, capacity, replay, integration, allocation, latency, and documentation categories.

---

# Section 1 - V14 Acceptance Criteria

## Venue Integration

AC-V14-VENUE-001 Binance Spot is registered as venue ID 2 in `venues.toml` with `fixPlugin = "FIX_44"`, `venuePlugin = "BINANCE"`, `marketDataModel = "L2"`, and immutable venue ID assignment.

AC-V14-VENUE-002 The Binance gateway runs as a separate process per the one-gateway-per-venue rule, configured via `gateway-2.toml` and started via `scripts/gateway-binance-start.sh`.

AC-V14-VENUE-003 Binance Ed25519 logon, heartbeat, sequence reset policy, market-data recovery, and disconnect/reconnect flows are implemented in `BinanceLogonCustomization` and the credential resolver, reusing the existing FIX 4.4 protocol plugin without modification.

AC-V14-VENUE-004 Binance proprietary tags, STP modes, order-type enrichment, and execution-report normalization are confined to `BinanceOrderEntryPolicy` and `BinanceExecutionReportPolicy`. No Binance-specific code lives in shared FIX 4.4 plugin classes.

AC-V14-VENUE-005 `BinanceL2MarketDataNormalizer` produces SBE events from `MarketDataIncrementalRefresh<X>` and `MarketDataSnapshotFullRefresh<W>` with allocation-free hot-path behavior after warmup.

AC-V14-VENUE-006 `BinanceExchangeSimulator` provides local TCP FIX endpoint, scenario controls, deterministic event publishing, and order-entry response simulation parity with `CoinbaseExchangeSimulator`.

AC-V14-VENUE-007 `BinanceFixL2LiveWireE2ETest` proves end-to-end Binance simulator FIX → gateway → cluster → strategy observation and order entry without live Binance access.

AC-V14-VENUE-008 Real Binance QA/UAT remains blocked until V12, V13, and V14 evidence bundles are complete.

AC-V14-VENUE-009 Coinbase `BTC-USD` and Binance `BTCUSDT` receive distinct internal `instrumentId` values. No `ConsolidatedL2Book` consolidation across them. No `ArbStrategy` arbitrage between them.

## Cross-Venue Activation (V13 Code, V14 Configuration)

AC-V14-XVENUE-001 `ArbStrategy` operates cross-venue between Coinbase and Binance using `MultiLegContingentExecution` from V13 with no code changes to either component. Behavior is config-driven activation only.

AC-V14-XVENUE-002 Independent `MarketMakingStrategy` instances run on Coinbase and Binance with `PostOnlyQuoteExecution` per V13. Quote refresh, staleness expiry, post-only retry, and cancel/replace are independent per venue.

AC-V14-XVENUE-003 `ConsolidatedL2Book`, `ExternalLiquidityView`, and `OwnOrderOverlay` aggregate Coinbase L3 (with derived L2) and Binance L2 uniformly. Strategies that read these abstractions do not branch on venue precision.

AC-V14-XVENUE-004 V13 cross-venue arbitrage controls (executable external liquidity, self-cross checks, cooldowns, leg timeouts, venue-native STP) operate correctly in mixed-precision mode.

AC-V14-XVENUE-005 Shared execution strategy plugins support multiple active parent lifecycles, or explicitly enforce and test a deterministic single-flight rejection contract. `PostOnlyQuoteExecution` must support bounded concurrent quote parents before parallel multi-venue market making is activated.

## Mixed-Precision Asymmetric Venues

AC-V14-MIXED-001 `OwnOrderOverlay` returns precise own-order quantities for Coinbase positions (using L3 venue order IDs) and conservative own-liquidity subtraction for Binance positions (using L2 price-level data) in the same test scenario.

AC-V14-MIXED-002 `ExternalLiquidityView` exposes mixed-precision results uniformly through its read interface; consumers do not require venue-precision branching.

AC-V14-MIXED-003 `ConsolidatedL2Book` aggregation is uniform across native L2 (Binance) and derived L2 (Coinbase) sources.

AC-V14-MIXED-004 Mixed-precision behavior is exercised by automated tests for `ArbStrategy`, `MarketMakingStrategy`, `InventoryHedgeStrategy`, `ParallelVenueExecution`, and `SmartOrderRoutingExecution`.

## Inventory Hedge Strategy

AC-V14-HEDGE-001 `InventoryHedgeStrategy` reads `PortfolioEngine` for net position state and detects threshold breaches per configured `thresholdMode` (`base_quantity` or `notional`) and `exposureMode` (`filled_only` or `filled_plus_working`).

AC-V14-HEDGE-002 `InventoryHedgeStrategy` emits `ParentOrderIntent` with `intentType = HEDGE`, populated `venueSetId`, configured `executionStrategyId`, and target quantity to return to the safe band.

AC-V14-HEDGE-003 `InventoryHedgeStrategy` does not emit a second hedge parent for the same instrument while a hedge parent is in non-terminal state.

AC-V14-HEDGE-004 `InventoryHedgeStrategy` enforces a configured cooldown after parent terminal in deterministic cluster time, with cooldown extension on hedge failure terminal reasons.

AC-V14-HEDGE-005 `InventoryHedgeStrategy` is replay-deterministic: identical PortfolioEngine and OrderManager state under replay produces identical hedge trigger decisions and identical emitted parent intents.

AC-V14-HEDGE-006 `InventoryHedgeStrategy` snapshot/load preserves cooldown state, last-checked thresholds, and active-parent tracking.

## Parallel Venue Execution

AC-V14-PARALLEL-001 `ParallelVenueExecution` reads `ExternalLiquidityView` and computes a depth-proportional slice plan across `venueSetId` with a minimum-slice floor.

AC-V14-PARALLEL-002 `ParallelVenueExecution` submits one IOC limit child per qualifying venue in parallel through `OrderManager`. Each child passes `RiskEngine` before submission.

AC-V14-PARALLEL-003 `ParallelVenueExecution` schedules a parent leg-completion timer with registered owner correlation per V13 timer rules.

AC-V14-PARALLEL-004 `ParallelVenueExecution` aggregates child fills onto parent state and terminates parents with primitive reason codes: `LEG_TIMER_RESIDUAL_CANCELED`, `ALL_CHILDREN_REJECTED`, `DONE`, `CANCELED`.

AC-V14-PARALLEL-005 `ParallelVenueExecution` does not re-slice on market-data tick. Re-slicing logic, fee weighting, fill-quality feedback, and latency weighting are explicit non-features.

AC-V14-PARALLEL-006 `ParallelVenueExecution` parent and child lifecycle is replay-deterministic and supports snapshot/load.

## Smart Order Routing Execution

Smart Order Routing is the primary V14 feature. It is implemented as an
execution strategy because V13 deliberately separated "what should be traded"
from "how child orders are worked." `InventoryHedgeStrategy` emits the hedge
parent intent; `SmartOrderRoutingExecution` owns fee-aware ranking, slice
planning, re-slice cancel/resubmit ordering, parent/child lifecycle, and terminal
reasoning.

AC-V14-SOR-001 `SmartOrderRoutingExecution` reads `ExternalLiquidityView` and a configured per-venue fee schedule and computes an executable-price-after-fees ranking.

AC-V14-SOR-002 `SmartOrderRoutingExecution` greedy-fills from cheapest venue down to executable depth at acceptable price, with a minimum-slice floor.

AC-V14-SOR-003 `SmartOrderRoutingExecution` re-slices on market-data tick when remaining quantity exists and the new plan differs materially from the original. Re-slice cancel-and-resubmit occurs as ordered cluster events.

AC-V14-SOR-004 `SmartOrderRoutingExecution` enforces a configured minimum cluster-time interval between re-slice attempts.

AC-V14-SOR-005 `SmartOrderRoutingExecution` terminates parents with primitive reason codes including the V14-specific `RESLICE_FAILED` in addition to the `ParallelVenueExecution` reason set.

AC-V14-SOR-006 `SmartOrderRoutingExecution` does not implement venue latency weighting, fill-quality feedback, or fill-probability modeling. The fee schedule is loaded once at startup; it does not update from REST during normal operation.

AC-V14-SOR-007 `SmartOrderRoutingExecution` parent and child lifecycle is replay-deterministic and supports snapshot/load.

## Compatibility Matrix

AC-V14-COMPAT-001 The V14 default compatibility matrix is `MarketMaking → PostOnlyQuote`, `Arb → MultiLegContingent`, `InventoryHedge → ParallelVenue`, generic one-shot → `ImmediateLimit`. Permitted override: `InventoryHedge → SOR`.

AC-V14-COMPAT-002 Unsupported pairings (`MarketMaking → ParallelVenue/SOR/MultiLegContingent/ImmediateLimit`, `Arb → ParallelVenue/SOR/PostOnlyQuote/ImmediateLimit`, `InventoryHedge → PostOnlyQuote/MultiLegContingent`) fail startup validation with clear errors. Compatibility validation does not require String comparison on the hot path.

AC-V14-SCHEMA-001 `ParentOrderIntent` exposes `venueSetId` in SBE schema, generated encoder/decoder, `ParentOrderIntentView`, and producer/consumer tests. Existing V13 `primaryVenueId` and `secondaryVenueId` semantics remain unchanged when `venueSetId` is zero.

AC-V14-SUBMIT-001 Trading strategies that submit parent intents through `ExecutionStrategyEngine.submit(...)` must handle synchronous rejection before a `ParentOrderState` exists. A rejected submit must not leave stale active parent IDs, live quote IDs, cooldown state, or retry gating inconsistent with execution state. Strategies must also continue to handle `ParentOrderTerminal` callbacks for parents that were accepted and later terminally failed. Tests must cover both rejection surfaces.

## Determinism

AC-V14-DET-001 Replay across both Coinbase and Binance simulator scenarios produces identical hedge triggers, parent intents, slice plans, re-slice sequences, child commands, child state, parent state, counters, snapshot/load, and outbound FIX bytes (or equivalent decoded summaries).

AC-V14-DET-002 V14 timer rules inherit V13 §9 (owner registration before scheduling, duplicate active correlation rejected, scheduling failure rolls back owner registration, required-timer failure terminates parent deterministically with no live children or active child links).

AC-V14-DET-003 Re-slice cancel-and-resubmit ordering is deterministic. Cancel commands precede new submissions in cluster ordering. Re-slice failure leaves no orphaned working child.

AC-V14-DET-004 Wall-clock callbacks are forbidden in `InventoryHedgeStrategy`, `ParallelVenueExecution`, and `SmartOrderRoutingExecution`. All time queries use `ctx.clock()`.

## Allocation and Latency Evidence

AC-V14-ALLOC-001 The V14 hot-path surfaces in master spec §11.2 are allocation-free after warmup, proven by JMH with `-prof gc`.

AC-V14-ALLOC-002 V14 hot paths avoid `String`, boxed keys, general-purpose mutable collections, exception construction for expected outcomes, and formatted logging on expected outcomes.

AC-V14-ALLOC-003 Selected V14 dispatch paths publish p50, p90, p99, and p99.9 latency evidence: parent-intent dispatch into `ParallelVenueExecution` and `SmartOrderRoutingExecution`, slice-plan computation for both, and the SOR re-slice path.

AC-V14-ALLOC-004 Shared support classes in `platform-cluster/.../execution/support` introduce no per-call allocation. Bounded scratch state is owned by the calling execution strategy.

AC-V14-ALLOC-005 Any non-zero V14 hot-path allocation has owner, reason, path classification, and remediation before zero-allocation claims are allowed.

## Risk and Capacity

AC-V14-RISK-001 Per-venue and aggregate risk limits are configured and enforced before child submission. Aggregate limit semantics are configuration-only; `RiskEngine` semantics are unchanged from V13.

AC-V14-RISK-002 `InventoryHedgeStrategy` reads aggregate exposure when computing `notional` mode thresholds.

AC-V14-CAP-001 Parent capacity, child-link capacity, and per-strategy capacity counters extend cleanly to V14 strategies and execution plugins. Capacity exhaustion produces deterministic reject reasons.

## Test Coverage and QA/UAT

AC-V14-TEST-001 Every new or modified production class has task-owned tests covering constructors, public methods, state transitions, parser branches, capacity boundaries, exception/counter paths, and failure side effects.

AC-V14-TEST-002 Unit tests cover positive, negative, edge, malformed, capacity, cancellation race, timer race, risk reject, child reject, parent terminal, re-slice race, and replay cases for each changed behavior.

AC-V14-TEST-003 Integration tests validate strategy-to-execution dispatch, parent registry, order manager, SBE messages, gateway handoff, FIX order entry, simulator execution reports, and parent callbacks across both venues.

AC-V14-TEST-004 Live-wire E2E tests prove `ArbStrategy` cross-venue, `InventoryHedgeStrategy` × `ParallelVenueExecution`, `InventoryHedgeStrategy` × `SmartOrderRoutingExecution`, and parallel `MarketMakingStrategy` flows through both simulators concurrently.

AC-V14-TEST-005 V13-to-V14 behavior-equivalence tests prove unchanged strategies and execution plugins produce equivalent results in single-venue scenarios.

AC-V14-TEST-006 Real Coinbase and Binance QA/UAT are blocked until all V12, V13, and V14 gates pass.

AC-V14-TEST-007 V14 does not use real-venue QA/UAT to replace automated local coverage.

---

# Section 2 - Mandatory Task-Owned Test Coverage Contract

Every V14 task card inherits the V13 Section 2 mandatory task-owned coverage contract. Task-specific test bullets are additions, not replacements.

For every new or modified production behavior, the task must add or update automated coverage for:

    positive behavior
    negative behavior
    edge values and boundary values
    malformed or invalid input where parsing/validation is involved
    capacity limits and full-capacity behavior where bounded state is involved
    safe-drop, reject, status-code, terminal-reason, and counter behavior where
      expected failure is possible
    snapshot/load/recovery where persistent or replayed state is involved
    deterministic replay where parent state, child state, strategy output,
      counters, or ordering changes
    integration where behavior crosses strategy, execution engine, parent
      registry, order manager, SBE, gateway, Aeron, FIX, or simulator
      boundaries
    allocation benchmark coverage where a declared hot path changes
    latency/percentile evidence where a latency-sensitive dispatch path changes
    V13-to-V14 behavior equivalence where existing strategy or execution
      behavior is exercised in a new configuration
    documentation of non-applicable categories with reason

V14-specific required case categories:

    mixed-precision OwnOrderOverlay query
    mixed-precision ExternalLiquidityView read
    cross-venue ArbStrategy edge detection
    cross-venue MultiLegContingentExecution leg fill, hedge, timer
    parallel multi-venue MarketMakingStrategy
    InventoryHedge threshold trigger and non-trigger
    InventoryHedge re-trigger gating during active parent
    InventoryHedge cooldown enforcement and cooldown extension
    InventoryHedge replay determinism
    InventoryHedge snapshot/load with active parent
    ParallelVenue slice plan
    ParallelVenue parallel child submission
    ParallelVenue timer-driven residual cancel
    ParallelVenue all-children-rejected
    ParallelVenue parent cancel mid-flight
    SOR slice plan with fee scoring
    SOR re-slice on tick
    SOR re-slice cancel-and-resubmit ordering
    SOR re-slice failure
    SOR fee schedule edge cases
    SOR minimum re-slice interval enforcement
    Binance Ed25519 logon and disconnect/reconnect
    Binance L2 normalizer snapshot, incremental, gap recovery, malformed
    Two-venue partial outage with active parent
    Two-venue reconciliation
    Compatibility matrix unsupported pairings
    USD/USDT instrument identity (no auto-arb between BTC-USD and BTCUSDT)

No task may claim complete coverage by testing only happy paths. If automation is not practical, the task must document the limitation, owner, exact manual verification command or evidence artifact, and why automation is not practical.

Every task card from TASK-401 through TASK-425 must treat the following as required acceptance criteria, even when the task-specific bullets below add more detail:

    1. Satisfy the Section 2 mandatory task-owned coverage contract.
    2. Add or update automated tests for every applicable production behavior
       changed by the task.
    3. Cover positive, negative, edge/boundary, malformed/invalid input,
       capacity, failure/counter, replay, integration, allocation, and latency
       categories where applicable.
    4. Prove expected behavior with assertions, not only compilation or smoke
       coverage.
    5. Run the narrowest relevant module tests plus broader Gradle checks
       required by the task.
    6. Document every non-applicable coverage category with exact reason and
       owner.
    7. Do not defer task-owned coverage to a later task unless the plan
       explicitly names the later task and the current task documents the
       deferral.

---

# Section 3 - Task Cards

## TASK-401 - Create V14 Documentation Baseline

### Objective

Create the V14 master spec, implementation plan, migration guide, and README references. Verify Binance FIX endpoint, port, FIX version, schema version, STP modes, and Ed25519 authentication requirements against current Binance documentation.

### Files to Create

    NitroJEx_Master_Spec_V14.0.md
    nitrojex_implementation_plan_v5.0.0.md
    NitroJEx_V13_to_V14_Migration.md

### Files to Update

    README.md

### Acceptance Criteria

- Satisfies the mandatory task-owned coverage contract in Section 2, including documented justification for non-applicable categories.
- Active development line points at V14.
- V13 artifacts remain frozen references.
- V14 task IDs start at TASK-401.
- V14 scope explicitly excludes new FIX plugins, WebSocket transport, derivatives, RiskEngine semantic changes, V13 plugin modifications, execution composition, USD/USDT basis trading, and all catalog items not explicitly enumerated as V14 built-ins.
- Binance integration parameters (FIX host, FIX port, schema version, STP modes, Ed25519 logon flow) verified against current Binance documentation and recorded in the spec.

## TASK-402 - Binance Venue Plugin Skeleton

### Objective

Create the Binance venue plugin package with `BinanceVenuePlugin`, capability declarations, and registration with the venue plugin registry. No order entry, market data, or simulator behavior yet.

### Files to Create

    platform-gateway/src/main/java/ig/rueishi/nitroj/exchange/gateway/venue/binance/BinanceVenuePlugin.java
    platform-gateway/src/main/java/ig/rueishi/nitroj/exchange/gateway/venue/binance/BinanceVenueCapabilities.java

### Files to Update

    platform-gateway/src/main/java/ig/rueishi/nitroj/exchange/gateway/venue/VenuePluginRegistry.java
    config/venues.toml
    config/gateway-2.toml
    scripts/gateway-binance-start.sh
    platform-gateway/src/test/java/*

### Acceptance Criteria

- Satisfies the mandatory task-owned coverage contract in Section 2.
- AC-V14-VENUE-001
- AC-V14-VENUE-002
- Tests cover plugin registration, capability declaration, venue ID immutability, gateway config loading, gateway script venue-name validation, and rejection of mismatched venue ID across config files.
- Binance plugin compiles and registers but is not yet wired for order entry or market data.

## TASK-403 - Binance Ed25519 Logon and Authentication

### Objective

Implement `BinanceLogonCustomization` and the credential resolution surface for Ed25519 signing. Confirm the credential resolver supports both HMAC (Coinbase) and Ed25519 (Binance) without leaking authentication primitive into shared FIX plugin code.

### Files to Create

    platform-gateway/src/main/java/ig/rueishi/nitroj/exchange/gateway/venue/binance/BinanceLogonCustomization.java
    platform-gateway/src/main/java/ig/rueishi/nitroj/exchange/gateway/venue/binance/BinanceCredentialResolver.java

### Files to Update

    platform-common/src/main/java/ig/rueishi/nitroj/exchange/common/credentials/CredentialResolver.java
    platform-gateway/src/test/java/*

### Acceptance Criteria

- Satisfies the mandatory task-owned coverage contract in Section 2.
- AC-V14-VENUE-003
- Tests cover Ed25519 signature payload construction, signature verification round trip, malformed key rejection, missing credential rejection, vault path resolution, primitive-tag mismatch (HMAC credential with Binance plugin) rejection, and logon message field assembly.
- Coinbase HMAC logon path is unchanged and verified by behavior-equivalence tests.

## TASK-404 - Binance L2 Market Data Normalizer

### Objective

Implement `BinanceL2MarketDataNormalizer` consuming Binance FIX `MarketDataIncrementalRefresh<X>` and `MarketDataSnapshotFullRefresh<W>` messages and producing internal SBE market-data events.

### Files to Create

    platform-gateway/src/main/java/ig/rueishi/nitroj/exchange/gateway/venue/binance/BinanceL2MarketDataNormalizer.java

### Files to Update

    platform-gateway/src/test/java/*

### Acceptance Criteria

- Satisfies the mandatory task-owned coverage contract in Section 2.
- AC-V14-VENUE-005
- AC-V14-ALLOC-001
- Tests cover snapshot, incremental refresh add/change/delete, depth boundary edge cases, sequence gap detection and recovery, malformed message safe-drop, depth size 2 and 5000 boundary, and SBE event production correctness.
- JMH proves normalizer hot-path allocation behavior at `0 B/op` after warmup.

## TASK-405 - Binance Order Entry Policy and Execution Report Policy

### Objective

Implement `BinanceOrderEntryPolicy` (proprietary tag enrichment, STP mode handling, order-type mapping) and `BinanceExecutionReportPolicy` (execution report normalization, reason code mapping).

### Files to Create

    platform-gateway/src/main/java/ig/rueishi/nitroj/exchange/gateway/venue/binance/BinanceOrderEntryPolicy.java
    platform-gateway/src/main/java/ig/rueishi/nitroj/exchange/gateway/venue/binance/BinanceExecutionReportPolicy.java

### Files to Update

    platform-gateway/src/test/java/*

### Acceptance Criteria

- Satisfies the mandatory task-owned coverage contract in Section 2.
- AC-V14-VENUE-004
- Tests cover order-entry enrichment for limit/IOC/FOK/post-only/GTC, STP mode mapping (`STP_NONE`, `EXPIRE_TAKER`, `EXPIRE_MAKER`, `EXPIRE_BOTH`, `DECREMENT`, `TRANSFER` if available), execution report normalization for new/partial-fill/full-fill/cancel/reject/expired, Binance reason code mapping to NitroJEx primitive reason codes, malformed execution report safe-drop, and counter increments on rejected orders.

## TASK-406 - Binance Exchange Simulator

### Objective

Implement `BinanceExchangeSimulator` providing a local TCP FIX endpoint, scenario controls, deterministic event publishing, and order-entry response simulation parity with `CoinbaseExchangeSimulator`.

### Files to Create

    platform-tooling/src/main/java/ig/rueishi/nitroj/exchange/simulator/BinanceExchangeSimulator.java
    platform-tooling/src/main/java/ig/rueishi/nitroj/exchange/simulator/BinanceSimulatorScenarios.java

### Files to Update

    platform-tooling/src/test/java/*

### Acceptance Criteria

- Satisfies the mandatory task-owned coverage contract in Section 2.
- AC-V14-VENUE-006
- Tests cover Ed25519 logon acceptance, heartbeat negotiation, market-data subscription, snapshot generation, incremental update generation, depth scenarios, order acceptance/reject scenarios, partial fill scenarios, sequence reset scenarios, disconnect/reconnect scenarios, and simulator deterministic time control.

## TASK-407 - Instrument Registry Mapping

### Objective

Add Binance instrument entries to `instruments.toml` with distinct internal `instrumentId` from Coinbase entries. Document and enforce the USD vs USDT instrument identity decision.

### Files to Update

    config/instruments.toml
    platform-common/src/main/java/ig/rueishi/nitroj/exchange/common/instruments/InstrumentRegistry.java
    platform-common/src/test/java/*

### Acceptance Criteria

- Satisfies the mandatory task-owned coverage contract in Section 2.
- AC-V14-VENUE-009
- Tests cover instrument loading for both venues, distinct internal `instrumentId` for `BTC-USD-COINBASE` versus `BTC-USDT-BINANCE`, rejection of duplicate `instrumentId`, rejection of missing venue mapping, and rejection of mappings to undeclared venues.
- Documentation in `instruments.toml` and migration guide explains the USD vs USDT decision and what would need to change if a future release wanted to consolidate them.

## TASK-408 - Binance FIX L2 Live-Wire E2E

### Objective

Implement live-wire E2E test proving the full Binance simulator → gateway → cluster → strategy → gateway → simulator flow without live Binance access.

### Files to Create

    platform-tooling/src/e2eTest/java/ig/rueishi/nitroj/exchange/e2e/BinanceFixL2LiveWireE2ETest.java

### Acceptance Criteria

- Satisfies the mandatory task-owned coverage contract in Section 2.
- AC-V14-VENUE-007
- Tests prove logon, market-data subscription, snapshot processing, incremental updates flowing into `L2OrderBook`, order submission, execution report processing, and parent state updates for an `ImmediateLimitExecution` parent on Binance.
- Tests cover positive flow, market-data gap recovery, order reject, disconnect/reconnect with order recovery, and simulator deterministic-time scenarios.

## TASK-409 - Mixed-Precision Overlay and Liquidity View Validation

### Objective

Validate that `OwnOrderOverlay`, `ExternalLiquidityView`, and `ConsolidatedL2Book` operate correctly in mixed-precision mode (Coinbase L3 + Binance L2 concurrently). Add task-owned tests for the mixed case.

### Files to Update

    platform-cluster/src/test/java/ig/rueishi/nitroj/exchange/marketdata/*
    platform-cluster/src/test/java/ig/rueishi/nitroj/exchange/cluster/*

### Acceptance Criteria

- Satisfies the mandatory task-owned coverage contract in Section 2.
- AC-V14-MIXED-001 through AC-V14-MIXED-004
- AC-V14-XVENUE-003
- Tests cover precise own-order matching on Coinbase L3 in the same scenario as conservative subtraction on Binance L2, uniform `ExternalLiquidityView` reads, uniform `ConsolidatedL2Book` aggregation, and precision-aware regression tests confirming Coinbase precision is unchanged from V13.

## TASK-410 - Cross-Venue ArbStrategy Activation

### Objective

Activate cross-venue `ArbStrategy` operation between Coinbase and Binance using `MultiLegContingentExecution` from V13. No code changes to either component; configuration and integration tests only.

### Files to Update

    config/strategies.toml
    platform-cluster/src/test/java/*
    platform-tooling/src/e2eTest/java/ig/rueishi/nitroj/exchange/e2e/CrossVenueArbLiveWireE2ETest.java

### Acceptance Criteria

- Satisfies the mandatory task-owned coverage contract in Section 2.
- AC-V14-XVENUE-001
- AC-V14-XVENUE-004
- Tests cover edge detection between Coinbase L3 and Binance L2, self-cross check with mixed precision, leg submission to both venues, leg fill scenarios, leg reject scenarios, imbalance hedge with hedge submission going to one of the two venues, hedge rejection kill-switch escalation across venues, and cooldown enforcement.
- Live-wire E2E proves the full cross-venue arb flow against both simulators concurrently.
- V13-to-V14 behavior-equivalence tests confirm `ArbStrategy` and `MultiLegContingentExecution` behavior on a single-venue scenario is unchanged.

## TASK-410A - Shared Execution Strategy Multi-Parent Lifecycle Hardening

### Objective

Harden the shared execution-strategy plugin lifecycle model before activating parallel market making. Execution strategy instances are registered once by `executionStrategyId` and are called synchronously many times by the deterministic cluster thread. Parent/child venue lifecycle callbacks arrive later as separate ordered events, so plugin-owned lifecycle state must be bounded per parent/child rather than singleton global active-parent fields.

`PostOnlyQuoteExecution` is the immediate blocker for TASK-411 because one market-making strategy can have bid and ask quote parents live at the same time, and V14 adds independent Coinbase and Binance market-making instances. Preserve V13 external behavior while allowing multiple active quote parents across venues, instruments, and sides.

### Files to Update

    platform-cluster/src/main/java/ig/rueishi/nitroj/exchange/execution/PostOnlyQuoteExecution.java
    platform-cluster/src/test/java/ig/rueishi/nitroj/exchange/execution/PostOnlyQuoteExecutionTest.java
    platform-cluster/src/test/java/ig/rueishi/nitroj/exchange/strategy/V12ToV13BehaviorEquivalenceTest.java
    platform-benchmarks/src/jmh/java/ig/rueishi/nitroj/exchange/execution/PostOnlyQuoteExecutionBenchmark.java

### Acceptance Criteria

- Satisfies the mandatory task-owned coverage contract in Section 2.
- AC-V14-XVENUE-005
- `PostOnlyQuoteExecution` tracks bounded per-parent/per-child state and no longer uses singleton active-parent fields for lifecycle ownership.
- Tests cover two simultaneous quote parents from one market-making instance, four simultaneous quote parents across Coinbase and Binance market-making instances, venue-scoped market-data refresh, parent cancel scoped to one parent, post-only reject retry scoped to one parent, final fill scoped to one parent, missing parent/child callback safe-drop or counter behavior, capacity full, deterministic replay, and snapshot/load behavior through the existing parent/order registries.
- V13-to-V14 behavior-equivalence tests confirm single-venue `MarketMakingStrategy` plus `PostOnlyQuoteExecution` behavior is unchanged.
- JMH proves `PostOnlyQuoteExecution` dispatch and callback hot-path allocation behavior remains at `0 B/op` after warmup.
- Documentation in code comments identifies the cluster-thread ownership assumption and explains that the per-parent table is for multi-lifecycle correctness, not thread synchronization.

## TASK-411 - Parallel Multi-Venue Market Making Activation

### Objective

Activate parallel `MarketMakingStrategy` instances on Coinbase and Binance with `PostOnlyQuoteExecution` per V13. Configuration and integration tests only; no code changes. This task is blocked until TASK-410A is complete.

### Files to Update

    config/strategies.toml
    platform-cluster/src/test/java/*
    platform-tooling/src/e2eTest/java/ig/rueishi/nitroj/exchange/e2e/ParallelMarketMakingLiveWireE2ETest.java

### Acceptance Criteria

- Satisfies the mandatory task-owned coverage contract in Section 2.
- AC-V14-XVENUE-002
- AC-V14-XVENUE-005 evidence from TASK-410A is present before activation.
- Tests cover independent quote computation per venue, independent staleness expiry, independent post-only retry, no cross-venue interference in inventory tracking when configured for per-venue inventory accounting, and per-venue STP behavior.
- Live-wire E2E proves both MM instances run concurrently against both simulators with independent quote refresh.
- V13-to-V14 behavior-equivalence tests confirm single-venue MM behavior is unchanged.

## TASK-412 - Per-Venue and Aggregate Risk Limit Configuration

### Objective

Add per-venue and aggregate risk limit configuration. Aggregate limits are configuration-only on top of the existing `RiskEngine`; no `RiskEngine` semantic changes.

### Files to Update

    config/risk.toml
    platform-cluster/src/main/java/ig/rueishi/nitroj/exchange/risk/RiskLimitConfig.java
    platform-cluster/src/test/java/ig/rueishi/nitroj/exchange/risk/*

### Acceptance Criteria

- Satisfies the mandatory task-owned coverage contract in Section 2.
- AC-V14-RISK-001
- Tests cover per-venue limit enforcement before child submission, aggregate limit enforcement before child submission, aggregate limit computation across venues (excluding USD/USDT auto-net), missing-limit rejection, malformed-limit rejection, and limit breach reason code propagation.
- `RiskEngine` semantics are unchanged from V13; behavior-equivalence tests verify single-venue scenarios match V13.

## TASK-413 - Two-Venue Reconciliation and Disconnect/Reconnect

### Objective

Add reconciliation surface and disconnect/reconnect handling for the two-venue case. Cover scenarios where one venue is disconnected while parents have working children on both.

### Files to Update

    platform-cluster/src/main/java/ig/rueishi/nitroj/exchange/recovery/*
    platform-cluster/src/test/java/*
    platform-tooling/src/main/java/ig/rueishi/nitroj/exchange/tooling/reconciliation/*

### Acceptance Criteria

- Satisfies the mandatory task-owned coverage contract in Section 2.
- Tests cover one-venue-disconnected with active arb parent, one-venue-disconnected with active parallel-MM parents, reconciliation of balances/positions/working-orders across both venues, kill-switch on unreconciled venue state, recovery after reconnect, and parent terminal reason consistency across venue partial outage.

## TASK-413A - VenueSetId Schema and Strategy Registry Plumbing

### Objective

Add the missing `ParentOrderIntent.venueSetId` schema plumbing required by venue-indifferent V14 producers and consumers. The V14 spec requires `venueSetId`, but the current repository schema and generated codecs only expose `primaryVenueId` and `secondaryVenueId`. This task must complete before `InventoryHedgeStrategy`, `ParallelVenueExecution`, or `SmartOrderRoutingExecution` are implemented.

Also create or wire the strategy registry surface referenced by TASK-414 if it is still absent, so InventoryHedge registration and compatibility can be implemented against an explicit local owner instead of implicit ad hoc wiring.

### Files to Update

    platform-common/src/main/resources/messages.xml
    platform-common/src/generated/java/ig/rueishi/nitroj/exchange/messages/*
    platform-common/src/test/java/ig/rueishi/nitroj/exchange/messages/*
    platform-cluster/src/main/java/ig/rueishi/nitroj/exchange/execution/ParentOrderIntentView.java
    platform-cluster/src/main/java/ig/rueishi/nitroj/exchange/strategy/StrategyRegistry.java
    platform-cluster/src/test/java/*
    platform-benchmarks/src/jmh/java/*

### Acceptance Criteria

- Satisfies the mandatory task-owned coverage contract in Section 2.
- AC-V14-SCHEMA-001
- `ParentOrderIntent` SBE schema includes `venueSetId` with generated encoder and decoder accessors.
- `ParentOrderIntentView` exposes `venueSetId()` and all existing `primaryVenueId()` and `secondaryVenueId()` behavior remains unchanged.
- Tests cover SBE encode/decode round trip with `venueSetId`, zero/default `venueSetId` compatibility for V13 MM and Arb parent intents, malformed or out-of-range venue-set IDs if validation exists, and deterministic replay of an intent carrying `venueSetId`.
- Existing V13-to-V14 behavior-equivalence tests confirm single-venue MarketMaking and Arb behavior is unchanged.
- Strategy registry plumbing exists for TASK-414 and can register or identify `InventoryHedgeStrategy` without breaking current StrategyEngine behavior.
- JMH or existing benchmark evidence confirms parent-intent dispatch/view hot-path allocation behavior remains unchanged after the schema field addition.

## TASK-414 - InventoryHedgeStrategy Implementation

### Objective

Implement `InventoryHedgeStrategy` as a venue-indifferent trading strategy. This task is blocked until TASK-413A is complete.

### Files to Create

    platform-cluster/src/main/java/ig/rueishi/nitroj/exchange/strategy/InventoryHedgeStrategy.java

### Files to Update

    platform-cluster/src/main/java/ig/rueishi/nitroj/exchange/strategy/StrategyRegistry.java
    config/strategies.toml
    platform-cluster/src/test/java/*

### Acceptance Criteria

- Satisfies the mandatory task-owned coverage contract in Section 2.
- AC-V14-HEDGE-001 through AC-V14-HEDGE-006
- AC-V14-RISK-002
- Tests cover threshold trigger in `base_quantity` mode, threshold trigger in `notional` mode, exposure mode `filled_only` and `filled_plus_working`, threshold non-trigger inside safe band, re-trigger gating during active parent, cooldown enforcement, cooldown extension on hedge failure, replay determinism, snapshot/load with active parent, configuration validation (positive thresholds, valid mode strings, valid venueSet), and intent emission with correct `venueSetId` and `executionStrategyId`.
- JMH proves hedge strategy hot-path allocation behavior at `0 B/op` after warmup.

## TASK-415 - ParallelVenueExecution Implementation

### Objective

Implement `ParallelVenueExecution` as the conservative reference venue-indifferent execution strategy.

### Files to Create

    platform-cluster/src/main/java/ig/rueishi/nitroj/exchange/execution/ParallelVenueExecution.java

### Files to Update

    platform-cluster/src/main/java/ig/rueishi/nitroj/exchange/execution/ExecutionStrategyRegistry.java
    platform-cluster/src/test/java/*

### Acceptance Criteria

- Satisfies the mandatory task-owned coverage contract in Section 2.
- AC-V14-PARALLEL-001 through AC-V14-PARALLEL-006
- AC-V14-DET-001
- AC-V14-DET-002
- AC-V14-DET-004
- Tests cover slice plan with two-venue full depth, slice plan with one-venue thin depth, minimum-slice floor enforcement, single-venue venueSet edge case, empty external liquidity edge case, parallel child submission ordering, leg-timer schedule, residual cancel on timer, all-children-rejected terminal, partial fills with timer cancel, parent cancel mid-flight, child reject during pending cancel, capacity full, replay determinism, and snapshot/load with active parent.
- JMH proves slice-plan and dispatch hot-path allocation behavior at `0 B/op` after warmup.
- Latency percentiles published for parent-intent dispatch and slice-plan computation.

## TASK-415A - Trading Strategy Submit-Rejection Handling

### Objective

Close the V14 parent-intent rejection feedback gap found after TASK-415. Trading strategies must handle both rejection paths: synchronous `ExecutionStrategyEngine.submit(...) == false` before parent state exists, and later `ParentOrderTerminal` callbacks for accepted parents that reach terminal failure. The known current gap is `MarketMakingStrategy.submitQuote(...)`, which can set `liveBidClOrdId` or `liveAskClOrdId` even when the execution engine rejects the parent intent synchronously.

### Files to Update

    platform-cluster/src/main/java/ig/rueishi/nitroj/exchange/strategy/MarketMakingStrategy.java
    platform-cluster/src/main/java/ig/rueishi/nitroj/exchange/strategy/ArbStrategy.java
    platform-cluster/src/main/java/ig/rueishi/nitroj/exchange/strategy/InventoryHedgeStrategy.java
    platform-cluster/src/test/java/*

### Acceptance Criteria

- Satisfies the mandatory task-owned coverage contract in Section 2.
- AC-V14-SUBMIT-001
- `MarketMakingStrategy` checks the boolean result of `executionEngine.submit(...)` before recording a quote parent as live.
- On synchronous quote-parent rejection, `MarketMakingStrategy` leaves the rejected side's live parent ID at zero and applies deterministic rejection handling such as cooldown/suppression/counter behavior.
- `ArbStrategy` and `InventoryHedgeStrategy` have explicit tests proving their existing synchronous submit-rejection handling clears or avoids active state and applies configured failure cooldown where applicable.
- Tests cover accepted-parent terminal callbacks separately from synchronous submit rejection for MarketMaking, Arb, and InventoryHedge.
- Replay determinism tests prove rejected synchronous parent-intent submission produces identical strategy state and counters.

## TASK-416 - SmartOrderRoutingExecution Implementation

### Objective

Implement `SmartOrderRoutingExecution` as the adaptive venue-indifferent execution strategy with re-slicing on market-data tick. This task is blocked until TASK-415A is complete so venue-indifferent execution is not activated on top of stale trading-strategy parent state.

### Files to Create

    platform-cluster/src/main/java/ig/rueishi/nitroj/exchange/execution/SmartOrderRoutingExecution.java

### Files to Update

    platform-cluster/src/main/java/ig/rueishi/nitroj/exchange/execution/ExecutionStrategyRegistry.java
    config/fees.toml
    platform-cluster/src/main/java/ig/rueishi/nitroj/exchange/execution/FeeSchedule.java
    platform-cluster/src/test/java/*

### Acceptance Criteria

- Satisfies the mandatory task-owned coverage contract in Section 2.
- AC-V14-SOR-001 through AC-V14-SOR-007
- AC-V14-DET-001
- AC-V14-DET-002
- AC-V14-DET-003
- AC-V14-DET-004
- Tests cover slice plan with fee scoring (cheaper venue chosen first), tied-price tiebreaker by depth, fee schedule loaded at startup, fee schedule edge cases (zero fee, very high fee, asymmetric maker-taker), greedy fill ordering, minimum-slice floor, re-slice on market-data tick when residual remains, re-slice cancel-and-resubmit ordering (cancels precede new submissions), re-slice failure terminal, minimum re-slice interval enforcement, no re-slice when in `CANCEL_PENDING`, re-slice during pending child execution report, parent cancel during re-slice, capacity full, replay determinism (including identical re-slice sequences), and snapshot/load with active parent and active re-slice timer.
- JMH proves slice-plan, re-slice, and dispatch hot-path allocation behavior at `0 B/op` after warmup.
- Latency percentiles published for parent-intent dispatch, slice-plan computation, and re-slice path.

## TASK-417 - Compatibility Matrix and Configuration Validation

### Objective

Extend the V13 compatibility matrix and `strategies.toml` validation for the V14 trading strategy and execution strategies.

### Files to Update

    platform-cluster/src/main/java/ig/rueishi/nitroj/exchange/strategy/StrategyContextImpl.java
    platform-cluster/src/main/java/ig/rueishi/nitroj/exchange/execution/ExecutionStrategyEngine.java
    platform-common/src/main/java/ig/rueishi/nitroj/exchange/common/config/StrategyConfigValidator.java
    platform-common/src/test/java/*

### Acceptance Criteria

- Satisfies the mandatory task-owned coverage contract in Section 2.
- AC-V14-COMPAT-001
- AC-V14-COMPAT-002
- Tests cover each unsupported pairing failing startup with a clear error, each supported pairing succeeding, default mapping resolution when `executionStrategy` is omitted, override resolution when present, override against an undeclared instrument or venue, missing required hedge config (threshold, safe band, cooldown), and validation evidence for each canonical execution strategy ID (`ImmediateLimit`, `PostOnlyQuote`, `MultiLegContingent`, `ParallelVenue`, `SOR`).

## TASK-418 - Venue-Indifferent Deterministic Replay Expansion

### Objective

Expand deterministic replay to include hedge triggers, venue-indifferent parent intents, slice planning, re-slice sequences, mixed-precision liquidity views, and cross-venue scenarios.

### Files to Update

    platform-cluster/src/test/java/ig/rueishi/nitroj/exchange/cluster/DeterministicReplayTest.java
    platform-tooling/src/main/java/ig/rueishi/nitroj/exchange/tooling/replay/*

### Acceptance Criteria

- Satisfies the mandatory task-owned coverage contract in Section 2.
- AC-V14-DET-001 through AC-V14-DET-004
- Tests cover hedge → ParallelVenue full replay, hedge → SOR full replay including re-slice sequences, cross-venue arb replay with mixed-precision inputs, parallel MM replay across both venues, parent state replay across snapshot/load boundary, capacity-counter determinism, and identical outbound FIX command sequences for both venues under replay.

## TASK-419 - Venue-Indifferent Snapshot/Recovery Integration

### Objective

Integrate venue-indifferent parents (hedge with `ParallelVenue` or `SOR`) with snapshot/load, recovery coordinator, and reconciliation evidence.

### Files to Update

    platform-cluster/src/main/java/ig/rueishi/nitroj/exchange/cluster/*
    platform-cluster/src/test/java/*

### Acceptance Criteria

- Satisfies the mandatory task-owned coverage contract in Section 2.
- AC-V14-DET-001
- AC-V14-HEDGE-006
- AC-V14-PARALLEL-006
- AC-V14-SOR-007
- Tests cover hedge parent snapshot round trip, ParallelVenue parent recovery with active children on both venues, SOR parent recovery with pending re-slice timer, SOR parent recovery during cancel-and-resubmit, reconciliation mismatch with hedge parent active, kill-switch on unreconciled hedge parent risk, and operational counters for hedge/ParallelVenue/SOR.

## TASK-420 - Cross-Venue Arb Live-Wire E2E

### Objective

Live-wire E2E proving cross-venue arbitrage end-to-end with both simulators running concurrently.

### Files to Create

    platform-tooling/src/e2eTest/java/ig/rueishi/nitroj/exchange/e2e/CrossVenueArbLiveWireE2ETest.java

### Acceptance Criteria

- Satisfies the mandatory task-owned coverage contract in Section 2.
- AC-V14-XVENUE-001
- AC-V14-XVENUE-004
- AC-V14-TEST-004
- Tests prove `ArbStrategy → MultiLegIntent → MultiLegContingentExecution → OrderManager → Coinbase + Binance gateways → both simulators → execution reports → parent callback`. Cover full edge detection, leg fill scenarios, leg reject scenarios, imbalance hedge, hedge rejection, parent terminal reasons, and cooldown.

## TASK-421 - Hedge × Parallel Venue Live-Wire E2E

### Objective

Live-wire E2E proving the venue-indifferent dispatch path through `ParallelVenueExecution` with both simulators running concurrently.

### Files to Create

    platform-tooling/src/e2eTest/java/ig/rueishi/nitroj/exchange/e2e/InventoryHedgeParallelVenueLiveWireE2ETest.java

### Acceptance Criteria

- Satisfies the mandatory task-owned coverage contract in Section 2.
- AC-V14-HEDGE-001 through AC-V14-HEDGE-006
- AC-V14-PARALLEL-001 through AC-V14-PARALLEL-006
- AC-V14-TEST-004
- Tests prove `InventoryHedgeStrategy → HedgeIntent (venueSetId populated) → ParallelVenueExecution → OrderManager → Coinbase + Binance gateways → both simulators → execution reports → parent callback`. Cover threshold trigger, parallel child submission, both-venue partial fills, timer-driven residual cancel, all-children-rejected scenario, parent cancel mid-flight, and cooldown after parent terminal.

## TASK-422 - Hedge × SOR Live-Wire E2E

### Objective

Live-wire E2E proving the venue-indifferent dispatch path through `SmartOrderRoutingExecution` with both simulators running concurrently, including a re-slice scenario triggered by simulator-driven depth shift.

### Files to Create

    platform-tooling/src/e2eTest/java/ig/rueishi/nitroj/exchange/e2e/InventoryHedgeSorLiveWireE2ETest.java

### Acceptance Criteria

- Satisfies the mandatory task-owned coverage contract in Section 2.
- AC-V14-SOR-001 through AC-V14-SOR-007
- AC-V14-TEST-004
- Tests prove `InventoryHedgeStrategy → HedgeIntent (venueSetId populated) → SmartOrderRoutingExecution → OrderManager → Coinbase + Binance gateways → both simulators → execution reports → parent callback`. Cover initial slice plan with fee scoring, simulator-driven depth shift triggering re-slice, re-slice cancel-and-resubmit ordering, re-slice failure terminal scenario, and minimum re-slice interval enforcement.

## TASK-423 - Parent/Execution Benchmark Surface Updates

### Objective

Add V14 hot paths to the allocation policy declaration, JMH benchmark map, latency report task, and verification gates.

### Files to Update

    platform-common/src/main/java/ig/rueishi/nitroj/exchange/common/AllocationPolicy.java
    platform-benchmarks/src/jmh/java/*
    platform-benchmarks/src/test/java/*
    platform-benchmarks/README.md
    scripts/v14-preflight-check.sh

### Acceptance Criteria

- Satisfies the mandatory task-owned coverage contract in Section 2.
- AC-V14-ALLOC-001 through AC-V14-ALLOC-005
- Tests prove every V14 hot-path surface from spec §11.2 has a benchmark owner.
- JMH publishes `-prof gc` allocation evidence and latency percentile evidence for hedge strategy dispatch, parallel venue execution, SOR execution including re-slice, mixed-precision overlay query, and Binance L2 normalizer event production.
- `scripts/v14-preflight-check.sh` runs the V12, V13, and V14 evidence gates.

## TASK-424 - Documentation and Runbook Update

### Objective

Update README, operational runbooks, and migration documentation for V14 behavior.

### Files to Update

    README.md
    config/*
    scripts/*
    NitroJEx_V13_to_V14_Migration.md

### Acceptance Criteria

- Satisfies the mandatory task-owned coverage contract in Section 2.
- Docs explain the second venue, mixed-precision asymmetric venues, the venue-indifferent dispatch path, `InventoryHedgeStrategy`, `ParallelVenueExecution`, `SmartOrderRoutingExecution`, configuration, non-goals, test gates, benchmark gates, rollback, USD/USDT instrument identity, two-venue reconciliation, and QA/UAT blockers for both venues.
- New runbooks cover hedge stuck, hedge cooldown stuck, parallel-venue partial-outage, SOR re-slice loop, fee schedule misconfiguration, and rollback to V13.

## TASK-425 - Final V14 Release Gate

### Objective

Run and document the full V14 verification gate.

### Files to Update

    README.md
    NitroJEx_V13_to_V14_Migration.md
    release evidence documentation or scripts

### Acceptance Criteria

- Satisfies the mandatory task-owned coverage contract in Section 2.
- Full `clean`, `check`, `e2eTest`, `:platform-benchmarks:jmh`, `:platform-benchmarks:jmhLatencyReport`, and `scripts/v14-preflight-check.sh` pass.
- All V14 task-owned tests pass.
- Mixed-precision evidence is archived.
- Cross-venue arb evidence is archived.
- Hedge × ParallelVenue and Hedge × SOR live-wire evidence is archived.
- Parent/execution JMH reports for V14 surfaces are archived.
- Deterministic replay evidence including mixed-precision and re-slice scenarios is archived.
- V13-to-V14 behavior-equivalence evidence is archived.
- Real Coinbase and Binance QA/UAT remain blocked until V12, V13, and V14 release evidence is complete.

---

# Section 4 - Required Verification Commands

Before V14 QA/UAT:

    JAVA_HOME=/usr/lib/jvm/java-21-openjdk-amd64 ./gradlew clean
    JAVA_HOME=/usr/lib/jvm/java-21-openjdk-amd64 ./gradlew check
    JAVA_HOME=/usr/lib/jvm/java-21-openjdk-amd64 ./gradlew e2eTest
    JAVA_HOME=/usr/lib/jvm/java-21-openjdk-amd64 ./gradlew :platform-benchmarks:jmh
    JAVA_HOME=/usr/lib/jvm/java-21-openjdk-amd64 ./gradlew :platform-benchmarks:jmhLatencyReport
    JAVA_HOME=/usr/lib/jvm/java-21-openjdk-amd64 scripts/v14-preflight-check.sh

The benchmark and latency output must be archived with the release evidence. Any non-zero V14 hot-path allocation must be fixed or explicitly reclassified before zero-GC claims are allowed. The `v14-preflight-check.sh` script extends the V12 and V13 preflight scripts; every gate from V12 and V13 remains required.
