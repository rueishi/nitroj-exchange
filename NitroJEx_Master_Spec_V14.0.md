# NitroJEx Master Specification V14.0

## Status

Active Development - Supersedes V13.0 for the venue, trading strategy, and execution strategy surfaces only

## Based On

`NitroJEx_Master_Spec_V13.0.md` (frozen execution strategy layer baseline)

## Implementation Plan

`nitrojex_implementation_plan_v5.0.0.md`

## Key Enhancements

- Makes **Smart Order Routing** the primary V14 feature. V14 deliberately
  progresses the V13 execution engine design: trading strategies emit parent
  intent, and execution strategy plugins own deterministic child-order routing,
  state, timers, and callbacks. `SmartOrderRoutingExecution` is the first
  fee-aware, adaptive, venue-indifferent router built on that contract.
- Adds Binance Spot as the second supported venue using the existing FIX 4.4 protocol plugin and the existing venue plugin model from V11.
- Activates cross-venue arbitrage by configuration: `ArbStrategy` and `MultiLegContingentExecution` from V13 operate across Coinbase L3 and Binance L2 with no code changes to the trading strategy or execution strategy.
- Activates parallel multi-venue market making by configuration: independent `MarketMakingStrategy` instances on each venue, each paired with `PostOnlyQuoteExecution`.
- Introduces `InventoryHedgeStrategy` as the first venue-indifferent trading strategy. It reads `PortfolioEngine` position state, triggers on configured threshold breach, and emits parent intents with `venueSetId` populated after the V14 schema plumbing task adds that field.
- Introduces `ParallelVenueExecution` as the first venue-indifferent execution strategy: depth-proportional slicing across the venue set, parallel IOC children, leg-timer residual cancel.
- Introduces `SmartOrderRoutingExecution` as the second venue-indifferent execution strategy: depth + fee + own-liquidity-netted scoring with re-slicing on market-data tick.
- Activates the venue-indifferent dispatch path end-to-end for the first time. V14 adds the missing `venueSetId` field to `ParentOrderIntent`; V14 is the first release where a producer populates it and a consumer routes on it.
- Exercises mixed-precision asymmetric venues (Coinbase L3 + Binance L2) through `OwnOrderOverlay`, `ExternalLiquidityView`, and `ConsolidatedL2Book` with task-owned tests.
- Preserves V13 deterministic replay, hot-path allocation policy, parent registry semantics, base snapshot/load mechanics, and risk gating. V14 makes execution-strategy engine state, execution-strategy plugin state, and relevant execution-strategy stats explicit members of the Aeron Cluster snapshot/replay/restart contract. V13 external behavior of `MarketMakingStrategy`, `ArbStrategy`, `MultiLegContingentExecution`, `PostOnlyQuoteExecution`, and `ImmediateLimitExecution` is unchanged.

---

# 1. Versioning and Baseline Rules

## 1.1 Frozen Baselines

The following files are immutable historical artifacts:

    NitroJEx_Master_Spec_V10.0.md
    nitrojex_implementation_plan_v1.4.0.md
    NitroJEx_Master_Spec_V11.0.md
    nitrojex_implementation_plan_v2.0.0.md
    NitroJEx_Master_Spec_V12.0.md
    nitrojex_implementation_plan_v3.0.0.md
    NitroJEx_Master_Spec_V13.0.md
    nitrojex_implementation_plan_v4.0.0.md
    NitroJEx_V10_to_V11_Migration.md
    NitroJEx_V11_to_V12_Migration.md
    NitroJEx_V12_to_V13_Migration.md

They must not be edited for V14 work except for explicit archival corrections approved separately. V13 is the frozen execution strategy layer baseline. V12 remains the frozen low-latency, deterministic replay, benchmark evidence, simulator live-wire, REST-boundary, and production-preflight baseline. V11 remains the frozen multi-venue architecture baseline.

## 1.2 V14 Scope

V14 makes three distinct additions on top of the V13 baseline:

    Venue addition:        Binance Spot via FIX 4.4 L2.
    Trading strategy:      InventoryHedgeStrategy (venue-indifferent producer).
    Execution strategies:  ParallelVenueExecution and SmartOrderRoutingExecution
                           (venue-indifferent consumers).

The release headline is Smart Order Routing. Binance and the inventory hedge
producer are enabling surfaces; the central design step is that the V13
`ExecutionStrategyEngine` can now route one parent intent across a venue set
using a plugin that owns fee-aware slice planning, re-slice ordering,
parent/child linkage, and deterministic terminal callbacks.

V14 also activates capabilities that already exist in V13 but had no second venue to operate against:

    Cross-venue arbitrage via ArbStrategy + MultiLegContingentExecution.
    Parallel multi-venue market making via independent MarketMakingStrategy instances.
    Mixed-precision OwnOrderOverlay, ExternalLiquidityView, and ConsolidatedL2Book.

V14 does not modify V13 trading-strategy behavior, V13 SBE schema, V13 risk semantics, V13 deterministic replay principles, V13 base snapshot/load mechanics, or V13 hot-path allocation policy. V14 does tighten the snapshot/restart contract for execution strategies: all execution-strategy state and stats that affect replay equivalence, recovery, audit, timers, parent/child routing, or future execution behavior must be included in Aeron Cluster snapshot/load and validated across replay/restart/rebuild.

## 1.3 Task Numbering

V14 implementation tasks must not reuse V10, V11, V12, or V13 task IDs. V14 task cards start at:

    TASK-401

---

# 2. Professional Claim

Before V14 evidence is complete, the allowed claim remains:

    NitroJEx has a completed V13 execution strategy layer baseline and is adding
    Binance Spot as a second venue with venue-indifferent trading and execution
    strategies for hedge-style multi-venue routing. The major V14 feature is
    Smart Order Routing, implemented as a natural extension of the V13
    execution engine rather than as strategy-owned venue logic.

After V14 evidence is complete, the allowed claim becomes:

    NitroJEx supports two-venue cryptocurrency spot trading across Coinbase L3
    and Binance L2 with deterministic cross-venue arbitrage, parallel multi-venue
    market making, and venue-indifferent inventory hedging. Hedge parents are
    routed across venues by either depth-proportional parallel execution or
    adaptive Smart Order Routing. SOR uses the execution strategy layer to keep
    fee-aware venue ranking, re-slice decisions, child-order lifecycle, and
    parent terminal semantics inside the execution engine. Replay proves
    identical parent state, child command sequences, and outbound FIX behavior
    under documented capacity limits in mixed-precision venue configurations.

No V14 claim may state that TWAP, VWAP, POV, peg, iceberg, sequential-venue, dark-pool, or RFQ execution algorithms are implemented unless a later release adds them with their own evidence. No V14 claim may state that WebSocket transport is supported. No V14 claim may state cross-venue inventory netting in `MarketMakingStrategy` is supported.

---

# 3. Venue-Indifferent Dispatch Path

## 3.1 The Producer-Consumer Gap V14 Closes

The V14 venue-indifferent path requires `ParentOrderIntent` to carry both the
single-venue field used by V13 (`primaryVenueId`) and a new `venueSetId` field
used by V14 hedge execution. During TASK-414 planning, the repository schema
was found to contain `primaryVenueId` and `secondaryVenueId`, but no generated
`venueSetId` encoder, decoder, or `ParentOrderIntentView` accessor. V14 must
therefore add and verify the schema plumbing before implementing
`InventoryHedgeStrategy`, `ParallelVenueExecution`, or `SmartOrderRoutingExecution`.

Once the schema blocker is complete, V14 fills both halves of the venue-indifferent path:

    Producer side:  InventoryHedgeStrategy populates venueSetId for the first
                    time when emitting hedge parent intents.
    Consumer side:  ParallelVenueExecution and SmartOrderRoutingExecution read
                    venueSetId and route children across the set.

V13 venue-aware producers (`MarketMakingStrategy`, `ArbStrategy`) remain semantically unchanged. V13 venue-aware consumers (`ImmediateLimitExecution`, `PostOnlyQuoteExecution`, `MultiLegContingentExecution`) keep their external execution semantics, but every shared execution-strategy plugin must be able to own more than one active parent lifecycle at a time. This is required because execution strategies are registered once by `executionStrategyId` and are called repeatedly by the deterministic cluster thread for many parent intents. A single trading strategy can emit multiple active parents, such as bid and ask quote parents, and V14 also runs multiple strategy instances across venues.

## 3.2 Venue-Aware vs Venue-Indifferent Intent Semantics

Two intent shapes coexist after V14:

    Venue-aware intent:
      primaryVenueId is populated.
      venueSetId is unused or set to a singleton containing primaryVenueId.
      Consumed by ImmediateLimit, PostOnlyQuote, or MultiLegContingent.

    Venue-indifferent intent:
      venueSetId is populated with two or more venues.
      primaryVenueId is unused or set to a hint value the consumer may ignore.
      Consumed by ParallelVenue or SOR.

The execution engine selects the consumer plugin from `executionStrategyId` on the parent intent, validated at startup against the compatibility matrix.

## 3.3 Shared Execution Strategy Lifecycle State

`ExecutionStrategyRegistry` installs one plugin instance per execution-strategy ID. That instance is a shared deterministic component, not a per-parent object. Therefore an execution strategy must not store lifecycle state as a single global "active parent" or "active child" unless it also enforces and tests a single-flight rejection policy.

The required V14 invariant is:

    One execution strategy plugin instance may receive many parent intents.
    Parent-intent dispatch is synchronous on the cluster thread.
    Venue acknowledgement, fill, cancel, reject, refresh, and timer callbacks
      arrive later as separate ordered cluster events.
    Plugin-owned mutable lifecycle state must therefore be bounded per parent,
      per child, or recovered from bounded registries by parent/child identity.

`PostOnlyQuoteExecution` is the first V14 blocker because parallel market making requires at least four concurrent quote parents in the common case:

    Coinbase bid quote parent
    Coinbase ask quote parent
    Binance bid quote parent
    Binance ask quote parent

The fix must preserve the V13 external behavior of `PostOnlyQuoteExecution` while replacing singleton active-parent fields with bounded multi-parent state, or an equivalent deterministic per-parent state mechanism. The same invariant applies to `ImmediateLimitExecution`, `MultiLegContingentExecution`, `ParallelVenueExecution`, and `SmartOrderRoutingExecution`; each task that touches an execution plugin must prove either bounded multi-parent support or an explicit deterministic single-flight rejection contract.

## 3.4 No SBE Schema Change

V14 introduces no new SBE messages and no new SBE fields. The schema additions in V13 (`ParentOrderIntent`, `ParentOrderUpdate`, `ParentOrderTerminal`, child `parentOrderId`, snapshot `parentOrderId`) are sufficient. V14 only changes which fields are populated by which producers and read by which consumers.

---

# 4. Binance Venue Integration

## 4.1 Protocol and Authentication

Binance Spot uses **FIX 4.4** for Order Entry, Drop Copy, and Market Data sessions. NitroJEx already provides a FIX 4.4 protocol plugin from V11. V14 reuses that plugin without modification.

Binance FIX authentication uses **Ed25519** signatures over the Logon message. Coinbase uses HMAC. The credential resolution surface must support both signing primitives. Implementations may extend the existing Coinbase HMAC path with a parallel Ed25519 path, or generalize the credential resolver to a primitive-tagged signing interface. Either approach is acceptable provided startup validation rejects mismatched credential type and venue plugin combinations.

## 4.2 Binance Venue Plugin Surface

Binance-specific production classes live under:

    platform-gateway/src/main/java/ig/rueishi/nitroj/exchange/gateway/venue/binance

Required classes:

    BinanceVenuePlugin
    BinanceLogonCustomization
    BinanceOrderEntryPolicy
    BinanceExecutionReportPolicy
    BinanceL2MarketDataNormalizer

Binance proprietary tag handling, Self-Trade Prevention modes (`STP_NONE`, `EXPIRE_TAKER`, `EXPIRE_MAKER`, `EXPIRE_BOTH`, `DECREMENT`, `TRANSFER`), and order-type enrichment must be confined to `BinanceOrderEntryPolicy` and `BinanceExecutionReportPolicy`. Shared FIX 4.4 protocol mechanics must remain in the existing FIX plugin.

## 4.3 Market Data Model

Binance declares:

    marketDataModel = "L2"

Binance FIX Market Data publishes `MarketDataIncrementalRefresh<X>` and `MarketDataSnapshotFullRefresh<W>` messages from the `DEPTH` stream into per-venue `L2OrderBook`. Binance does not publish public L3 order-level data. `VenueL3Book` is not instantiated for Binance.

`ConsolidatedL2Book` aggregates Coinbase derived-L2 (from `VenueL3Book`) and Binance native L2 uniformly. The consolidation logic does not branch on venue precision.

## 4.4 Venue Configuration

`config/venues.toml` gains:

    [[venue]]
    id          = 2
    name        = "BINANCE"
    fixHost     = "fix-oe.binance.com"
    fixPort     = 9000
    sandbox     = false
    fixPlugin   = "FIX_44"
    venuePlugin = "BINANCE"
    marketDataModel = "L2"
    orderEntryEnabled       = true
    marketDataEnabled       = true
    nativeReplaceSupported  = false

Binance market data and order entry use distinct FIX session endpoints in production. TASK-401 verified the current official Binance Spot FIX endpoints as:

    Order Entry: tcp+tls://fix-oe.binance.com:9000
    Drop Copy:   tcp+tls://fix-dc.binance.com:9000
    Market Data: tcp+tls://fix-md.binance.com:9000

Binance requires TLS with SNI and certificate validation. FIX sessions use FIX 4.4 and only support Ed25519 API keys. Order Entry requires the `FIX_API` permission; Market Data accepts `FIX_API` or `FIX_API_READ_ONLY`. The Logon `<A>` Ed25519 payload is the SOH-joined sequence `MsgType(35)`, `SenderCompID(49)`, `TargetCompID(56)`, `MsgSeqNum(34)`, and `SendingTime(52)`, with the base64 signature in RawData tag `96`, RawDataLength tag `95`, API key in Username tag `553`, `ResetSeqNumFlag(141)=Y`, and `MessageHandling(25035)` set by configuration or defaulted to sequential mode `2`. TASK-401 verification source: `https://github.com/binance/binance-spot-api-docs/blob/master/fix-api.md`. The gateway process must support multiple concurrent FIX sessions to one venue when the venue exposes them.

## 4.5 Instrument Identity Decision

Coinbase `BTC-USD` and Binance `BTCUSDT` are distinct products. USD is fiat; USDT is a stablecoin with its own basis dynamics, settlement mechanics, and liquidity profile.

V14 treats these as **different instruments** in `instruments.toml`. They receive distinct internal `instrumentId` values. `ArbStrategy` does not arb between them. `ConsolidatedL2Book` does not consolidate them onto a single instrument view.

USD/USDT basis trading is not a V14 capability. Any future strategy that wants to trade the basis must be specified separately with its own market-data interpretation, risk model, and QA/UAT evidence.

## 4.6 Gateway Process

NitroJEx remains one gateway process per venue:

    config/gateway-2.toml      Binance gateway config with process.venueId = 2
    scripts/gateway-binance-start.sh
                               Wrapper delegating to gateway-start.sh BINANCE

The Binance gateway loads `gateway-2.toml`, `venues.toml`, and `instruments.toml` like the Coinbase gateway loads `gateway-1.toml`.

## 4.7 Simulator and Live-Wire E2E

`BinanceExchangeSimulator` mirrors `CoinbaseExchangeSimulator`: local TCP FIX endpoint, scenario controls, deterministic event publishing, simulator market-data publishing, and order-entry response simulation. Live-wire E2E must prove:

    Binance simulator FIX session
      -> gateway FIX session
      -> gateway disruptor
      -> Aeron Cluster ingress
      -> cluster market state / books / strategy observation
      -> cluster egress order command
      -> gateway OrderCommandHandler / ExecutionRouter
      -> Binance FIX order entry into simulator
      -> simulator ExecutionReport
      -> gateway ExecutionHandler
      -> cluster OrderManager / PortfolioEngine / RiskEngine / StrategyEngine

Required live-wire E2E classes:

    platform-tooling/src/e2eTest/java/ig/rueishi/nitroj/exchange/e2e/BinanceFixL2LiveWireE2ETest.java

Real Binance QA/UAT is blocked until the simulator live-wire E2E gates pass.

## 4.8 REST Boundary

Binance does not require REST polling for production-path data the way Coinbase REST polling exists. If V14 introduces any Binance REST usage (for example, for historical depth snapshots or symbol metadata), it must be confined to cold/control-plane paths under `gateway/venue/binance` REST-owned code, must not allocate into the trading hot path, and must not appear in cluster books, risk, order management, strategy APIs, or normal FIX market-data handling. The V12 REST boundary rule applies unchanged.

---

# 5. New Trading Strategy: InventoryHedgeStrategy

## 5.1 Purpose

`InventoryHedgeStrategy` is a venue-indifferent risk-management trading strategy. Its purpose is to flatten net inventory exposure when configured thresholds are breached. It is the first V14 producer of venue-indifferent parent intents.

## 5.2 Trigger Logic

The strategy reads from `PortfolioEngine` to compute net position per instrument. Net position is the sum of filled positions across all venues for the same internal `instrumentId`.

The strategy supports two threshold modes, configured per strategy instance:

    base_quantity:    threshold expressed as absolute base quantity (for example, BTC).
    notional:         threshold expressed as quote-currency notional using last consolidated mid.

The strategy supports two exposure modes:

    filled_only:               trigger on filled position only.
    filled_plus_working:       trigger on filled position plus signed quantity of
                               own working orders (own working buys reduce excess
                               long exposure trigger; own working sells reduce
                               excess short exposure trigger).

Default mode is `filled_plus_working` to avoid over-hedging when flatten orders are already in flight.

## 5.3 Intent Emission

When the strategy detects a threshold breach and no hedge parent for the instrument is currently active, it emits a `ParentOrderIntent` with:

    intentType            = HEDGE
    side                  = SELL if net long over threshold, BUY if net short under threshold
    instrumentId          = the breached instrument
    venueSetId            = configured venue set (typically all venues that trade the instrument)
    primaryVenueId        = optional hint, may be unused by consumer
    quantityScaled        = quantity required to return to the safe band
    limitPriceScaled      = configured limit relative to last mid, or absent for IOC
    timeInForcePreference = IOC or DAY-with-cancel-on-timer
    urgencyHint           = HIGH
    selfTradePolicy       = REJECT
    executionStrategyId   = configured per strategy instance: ParallelVenue or SOR
    correlationId         = strategy-issued correlation

The strategy does not encode child orders. It does not pick venues. Both decisions belong to the execution strategy.

## 5.4 Re-Trigger and Cooldown

The strategy must not emit a second hedge parent for the same instrument while a hedge parent is still in non-terminal state (`PENDING`, `WORKING`, `PARTIALLY_FILLED`, `HEDGING`, `CANCEL_PENDING`).

After a hedge parent reaches terminal state, the strategy applies a configured cooldown before evaluating the threshold again. Cooldown duration is measured in deterministic cluster time, not wall-clock.

Cooldown is reset on successful flatten back into the safe band. Cooldown is extended on hedge failure terminal reasons.

## 5.5 Replay and Determinism

`InventoryHedgeStrategy` reads only from cluster-deterministic state: `PortfolioEngine`, `OrderManager`, `ParentOrderRegistry`, `OwnOrderOverlay`, `ConsolidatedL2Book`, and the deterministic cluster clock. It must not read wall-clock, REST, or external sources on the hot path.

Replay must produce identical hedge trigger decisions, identical emitted parent intents, identical cooldown state, and identical re-trigger ordering given the same ordered SBE input plus the same initial state.

## 5.6 Configuration

`config/strategies.toml` gains:

    [[strategy]]
    id                = "hedge-btc-multi-venue"
    type              = "InventoryHedge"
    instrument        = "BTC-USD-COINBASE"
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
    instrument        = "BTC-USDT-BINANCE"
    venueSet          = ["BINANCE"]
    thresholdMode     = "notional"
    thresholdValue    = 50000.0
    exposureMode      = "filled_plus_working"
    safeBandValue     = 25000.0
    cooldownMicros    = 30000000
    executionStrategy = "SOR"

Per-instrument configuration is required because each instrument has its own threshold band, venue set, and routing preference. There is no global hedge configuration.

Note: per the instrument identity decision in §4.5, BTC-USD on Coinbase and BTC-USDT on Binance are distinct instruments. A multi-venue hedge that spans both is not a V14 supported configuration. The `venueSet` for an instrument is the set of venues that trade that exact instrument.

---

# 6. New Execution Strategies

## 6.1 ParallelVenueExecution

Canonical execution strategy ID: `ParallelVenue`.

### Behavior

Default conservative reference for venue-indifferent execution. On `onParentIntent`:

    Read ExternalLiquidityView for each venue in venueSetId.
    Compute per-venue executable depth net of own working liquidity at or
      better than limitPriceScaled (if a limit is set).
    Compute slice plan: per-venue child quantity proportional to per-venue
      executable depth, capped by total parentQuantity, with a minimum-slice
      floor below which a venue is omitted.
    Submit one IOC limit child per venue in parallel through OrderManager
      (each child still passes RiskEngine).
    Link each child to the parent in ParentOrderRegistry.
    Schedule one parent leg-completion timer through ctx.timerScheduler() with
      registered owner correlation.
    Aggregate child fills onto parent state.

### Termination

    All children fully fill within timer    -> parent DONE
    Children partially fill within timer    -> on timer fire, cancel all
                                                working children, parent
                                                terminates as PARTIALLY_FILLED
                                                with primitive reason
                                                LEG_TIMER_RESIDUAL_CANCELED.
    All children reject                      -> parent FAILED with primitive
                                                reason ALL_CHILDREN_REJECTED.
    Parent cancel arrives                    -> cancel all working children,
                                                parent terminates CANCELED.

### Constraints

`ParallelVenueExecution` does not re-slice on market-data tick. It does not adjust venue weights based on observed fill quality. It does not weight by fees. It is the conservative reference path. Any future enrichment must be a separate execution strategy plugin, not a behavior change to `ParallelVenueExecution`.

## 6.2 SmartOrderRoutingExecution

Canonical execution strategy ID: `SOR`.

### Behavior

Adaptive venue-indifferent execution. On `onParentIntent`:

    Read ExternalLiquidityView and per-venue fee schedule (configured at
      startup, not hot-path RPC).
    Compute per-venue executable price after fees.
    Rank venues by executable-price-after-fees, breaking ties by depth.
    Compute slice plan: greedy fill from cheapest venue down to per-venue
      executable depth at acceptable price, with a minimum-slice floor.
    Submit per-venue IOC limit children through OrderManager.
    Link children to parent in ParentOrderRegistry.
    Schedule parent leg-completion timer with registered owner correlation.

On `onMarketDataTick`:

    If parent has remaining quantity and is not in CANCEL_PENDING or terminal
      state, recompute the slice plan against current ExternalLiquidityView.
    If new plan differs materially from the original (configurable threshold
      on price improvement or depth shift), cancel-and-resubmit residual
      children to the new venue allocation. The cancel-and-resubmit must
      occur as ordered cluster events, not in a single multi-step tick.

### Termination

Same as `ParallelVenueExecution` plus:

    Re-slice cancel-and-resubmit fails       -> parent FAILED with primitive
                                                reason RESLICE_FAILED.

### Constraints

`SmartOrderRoutingExecution` reads only from cluster-deterministic state. The fee schedule is loaded at startup from configuration; it does not update from REST endpoints during normal operation. Re-slicing decisions are bounded: a minimum cluster-time interval between re-slice attempts must be enforced to prevent thrash. The minimum interval is a configuration value.

`SmartOrderRoutingExecution` does not use venue latency weighting, fill-quality feedback, or fill-probability modeling for V14 routing decisions. V14 does, however, record bounded per-venue future-policy inputs from ordered cluster events so a later SOR model can be introduced without losing restart continuity. These recorded inputs include child submissions, risk rejects, acknowledgement reports, fill reports, reject reports, cancel/expire reports, filled quantity, submit-to-ack latency totals/maxima, submit-to-fill latency totals/maxima, and last submit/report cluster times. These stats must remain observational in V14 and must not alter the executable-price-after-fees ranking.

## 6.3 Default Compatibility Matrix

V14 extends the V13 compatibility matrix:

    MarketMaking      -> PostOnlyQuote        (V13 default, unchanged)
    Arb               -> MultiLegContingent   (V13 default, unchanged)
    InventoryHedge    -> ParallelVenue        (V14 default)
    Generic one-shot  -> ImmediateLimit       (V13 default, unchanged)

Permitted overrides:

    InventoryHedge    -> SOR                  (configurable)

Unsupported pairings that must fail startup validation:

    MarketMaking      -> ParallelVenue, SOR, MultiLegContingent, ImmediateLimit
    Arb               -> ParallelVenue, SOR, PostOnlyQuote, ImmediateLimit
    InventoryHedge    -> PostOnlyQuote, MultiLegContingent

Compatibility validation occurs once at startup and must not require String comparison on the hot path.

## 6.4 Parent Intent Submit-Rejection Feedback

Trading strategies submit parent intents synchronously through `ExecutionStrategyEngine.submit(...)`. V14 requires every trading strategy to handle two distinct rejection surfaces:

    Rejected before parent state exists:
      ExecutionStrategyEngine.submit(...) returns false, for example because the
      execution strategy ID is unknown or the trading/execution pairing is not
      compatible. No ParentOrderState exists, so no ParentOrderTerminal callback
      is guaranteed. The trading strategy must not record the parent as active,
      must not leave live quote/hedge/arb IDs pointing at the rejected parent,
      and must apply deterministic retry or cooldown behavior.

    Accepted then terminally failed:
      The execution strategy claimed a ParentOrderState and later transitioned
      it to a terminal failure such as RISK_REJECTED, CHILD_REJECTED, or
      CAPACITY_REJECTED. ExecutionStrategyEngine emits one ParentOrderTerminal
      callback via emitUnreportedTerminalCallbacks(). The trading strategy must
      clear active state and apply its terminal-reason-specific cooldown or retry
      policy from that callback.

This distinction is required because a pre-claim rejection has no parent object
from which a terminal callback can be emitted. V14 task coverage must prove both
paths for MarketMakingStrategy, ArbStrategy, and InventoryHedgeStrategy. In
particular, MarketMakingStrategy must only set `liveBidClOrdId` or
`liveAskClOrdId` after the execution engine accepts the quote parent.

## 6.5 No Composition

V14 plugins are peer plugins. There is no execution strategy composition. Routing logic and lifecycle logic are unified within each plugin.

If shared lifecycle logic emerges across `ParallelVenueExecution`, `SmartOrderRoutingExecution`, and `MultiLegContingentExecution` (for example, leg timer helpers, hedge submission helpers, kill-switch escalators), it must be factored into shared support classes in `platform-cluster/src/main/java/ig/rueishi/nitroj/exchange/execution/support`, called from within each plugin. Support classes must respect V13 hot-path allocation policy.

---

# 7. Mixed-Precision Asymmetric Venues

## 7.1 First Operational Exercise of V11 Abstractions

V14 is the first release where two venues with different `marketDataModel` values operate concurrently. V11 spec'd the L2/L3 abstractions to support this case; V14 exercises them in production.

The relevant V11 abstractions:

    OwnOrderOverlay
      - Coinbase L3: exact own-order matching using reliable venue order IDs.
      - Binance  L2: conservative own-liquidity subtraction at price levels.

    ExternalLiquidityView
      - Reads per-venue OwnOrderOverlay output uniformly.
      - L3 venues contribute precise external-liquidity numbers.
      - L2 venues contribute conservative external-liquidity numbers.

    ConsolidatedL2Book
      - Aggregates per-venue L2 (native L2 from Binance, derived L2 from
        Coinbase VenueL3Book).
      - Does not branch on venue precision.

## 7.2 Strategy Behavior in Mixed-Precision Mode

Strategies that read these abstractions do not branch on venue precision:

    ArbStrategy: reads ExternalLiquidityView, treats L3-precise and
                 L2-conservative numbers uniformly. Self-cross check using
                 OwnOrderOverlay degrades gracefully on the L2 side.
    MarketMakingStrategy: reads venue-specific market data and OwnOrderOverlay
                 per its instance configuration. L3 instances get precise own
                 overlay; L2 instances get conservative own overlay.
    InventoryHedgeStrategy: reads PortfolioEngine and emits parent intents.
                 No precision branching needed.
    ParallelVenueExecution / SmartOrderRoutingExecution: read
                 ExternalLiquidityView for slice planning. L2-conservative
                 depth produces slightly conservative routing toward L2 venues,
                 which is acceptable because under-routing is safer than
                 over-routing into own quotes.

## 7.3 L3-Only Strategy Capabilities Are Out of Scope

Capabilities that depend on L3-specific signals (queue position estimation, exact iceberg detection from order-level refresh patterns, fine-grained order-flow inference) are not introduced in V14. Any future strategy that requires L3 signals must be configured to run only on L3 venues.

## 7.4 Required Mixed-Precision Tests

V14 must include task-owned tests that exercise the mixed-precision case explicitly:

    OwnOrderOverlay returns precise numbers for Coinbase positions and
      conservative numbers for Binance positions in the same test scenario.
    ExternalLiquidityView returns mixed-precision results uniformly through
      its read interface.
    ConsolidatedL2Book aggregates Coinbase derived-L2 and Binance native L2
      with no precision-aware branching.
    ArbStrategy detects and submits cross-venue arb between Coinbase L3 and
      Binance L2 using mixed-precision liquidity views.
    ParallelVenueExecution computes slice plans against mixed-precision depth.
    SmartOrderRoutingExecution computes slice plans and re-slice decisions
      against mixed-precision depth.

---

# 8. Configuration

## 8.1 venues.toml

Adds Binance entry per §4.4. Coinbase entry unchanged.

## 8.2 instruments.toml

Adds Binance instrument entries per §4.5. Coinbase instrument entries unchanged. The internal `instrumentId` for `BTC-USDT-BINANCE` must differ from the internal `instrumentId` for `BTC-USD-COINBASE`.

## 8.3 strategies.toml

Adds:

    InventoryHedge strategy entries per §5.6.
    ArbStrategy instances configured with both Coinbase and Binance in scope.
    MarketMakingStrategy instances per venue (one for Coinbase, one for Binance).

## 8.4 gateway-2.toml

New file mirroring `gateway-1.toml`:

    [process]
    venueId  = 2
    nodeRole = "gateway"

    [credentials]
    vaultPath = "secret/trading/binance/venue-2"

Production secrets remain outside repository configs. Credential resolution must produce the Binance Ed25519 key pair and any required passphrase from the approved secret source.

## 8.5 admin.toml

Admin commands gain support for hedge-strategy pause/resume and per-venue kill-switch where applicable. No new admin command surfaces are required beyond the V13 admin command model; extension is by configuration and command parameters only.

## 8.6 Per-Venue and Aggregate Risk Limits

V14 introduces explicit per-venue and aggregate risk limit configuration:

    [[risk.limit]]
    instrument   = "BTC-USD-COINBASE"
    venue        = "COINBASE"
    maxPosition  = 5.0
    maxNotional  = 250000.0

    [[risk.limit]]
    instrument   = "BTC-USDT-BINANCE"
    venue        = "BINANCE"
    maxPosition  = 5.0
    maxNotional  = 250000.0

    [[risk.limit.aggregate]]
    asset        = "BTC"
    maxPosition  = 8.0
    maxNotional  = 400000.0

Per-venue limits are enforced before child submission. Aggregate limits are enforced before child submission and when computing hedge trigger thresholds in `InventoryHedgeStrategy`. Aggregate limit semantics are configuration; `RiskEngine` semantics are unchanged from V13.

---

# 9. SBE Schema

## 9.1 Required Schema Plumbing

V14 introduces no new SBE messages, but it does require one schema-field correction:
`ParentOrderIntent.venueSetId` must exist as a generated encoder/decoder field.
The current repository schema has `primaryVenueId` and `secondaryVenueId`, so the
venue-set field must be added before venue-indifferent producer/consumer tasks.

## 9.2 New Field Population

`ParentOrderIntent.venueSetId` is populated for the first time by `InventoryHedgeStrategy` and read for the first time by `ParallelVenueExecution` and `SmartOrderRoutingExecution`. V14 schema tests must verify encoder/decoder round trip, `ParentOrderIntentView` access, existing `primaryVenueId`/`secondaryVenueId` compatibility, and unchanged V13 parent-intent behavior when `venueSetId` is zero.

## 9.3 SBE Generation

The venue-set schema task must regenerate SBE and commit the generated codec changes. The generation task remains:

    JAVA_HOME=/usr/lib/jvm/java-21-openjdk-amd64 ./gradlew :platform-common:generateSBE

---

# 10. Determinism Preservation

## 10.1 Carryforward From V13

All V13 §9 determinism rules apply unchanged. Parent timers enter the cluster as ordered events; owner registration occurs before scheduling; duplicate active correlations are rejected; scheduling failure rolls back owner registration; required-timer scheduling failure terminates the parent deterministically.

## 10.2 Venue-Indifferent Determinism

V14 introduces these determinism requirements specific to the venue-indifferent path:

    The slice plan computed by ParallelVenueExecution must be a pure function of
      ParentOrderIntent fields, ExternalLiquidityView state at the dispatch
      cluster time, and OwnOrderOverlay state at the dispatch cluster time.
    The slice plan computed by SmartOrderRoutingExecution must additionally be
      a pure function of the configured fee schedule.
    Re-slice decisions in SmartOrderRoutingExecution must be triggered only by
      ordered market-data ticks, never by wall-clock callbacks.
    Re-slice cancel-and-resubmit must be expressed as ordered cluster events,
      with deterministic ordering between cancel commands and new submissions.
    InventoryHedgeStrategy hedge trigger decisions must be a pure function of
      PortfolioEngine state and OrderManager working-order state at the
      cluster tick.

## 10.3 Required Replay Tests

Replay tests must prove that the same ordered SBE stream plus the same initial state produces identical:

    Hedge trigger decisions per InventoryHedgeStrategy instance.
    Emitted parent intents (correlation IDs, venueSetId, fields).
    Slice plans per ParallelVenueExecution dispatch.
    Slice plans and re-slice sequences per SmartOrderRoutingExecution dispatch.
    Cross-venue ArbStrategy edge detection and MultiLegIntent emission with
      mixed-precision inputs.
    Outbound FIX command bytes (or equivalent decoded command summaries) per
      gateway across both venues.
    Snapshot/load round trips with active hedge parents and active
      ParallelVenue or SOR parents.

## 10.4 Snapshot, Restart, and Rebuild Contract

V14 restart correctness is defined as:

    latest Aeron Cluster snapshot
      + ordered cluster log replay after that snapshot
      + venue/order/position reconciliation
      + rebuild of explicitly ephemeral telemetry

The Aeron Cluster snapshot must include every execution-strategy datum that can affect future deterministic behavior, replay equivalence, audit, recovery, timer dispatch, parent/child routing, or operator-visible execution counters. This includes:

    ExecutionStrategyEngine counters:
      parent-intent dispatches, child-execution dispatches, timer dispatches,
      cancel dispatches, market-data dispatches, unknown execution-strategy
      rejects, incompatible strategy rejects, missing-parent rejects,
      timer-capacity rejects, and unknown-timer rejects.
    ExecutionStrategyEngine timer-owner state:
      active timer correlation IDs and owning execution-strategy IDs.
    ParentOrderRegistry state:
      active parent slots, requested/filled/remaining quantity, average fill
      price, status, terminal reason, terminal-reported state, last transition
      cluster time, primary timer correlation ID, active child links, and
      registry capacity/reject counters.
    Execution strategy plugin state:
      active parent slots, active child counts or child IDs, cancel-pending
      flags, retry counts, re-slice generations, filled/rejected quantities,
      timer correlation IDs, routing/slice state, last re-slice cluster time,
      and parent terminal bookkeeping.
    Execution strategy plugin stats:
      relevant counters such as parent intents, child submissions, risk rejects,
      capacity rejects, malformed rejects, retry submissions, retry exhaustions,
      timer schedules/firings, residual cancels, all-children-rejected,
      reslice attempts/successes/failures, reslice interval skips, parent
      cancels, child-fill-during-cancel, missing callback drops, and bounded
      future-policy inputs that may affect later SOR models.
    Future SOR policy inputs:
      per-venue child submissions, risk rejects, acknowledgement reports, fill
      reports, reject reports, cancel/expire reports, filled quantity,
      submit-to-ack latency totals/maxima, submit-to-fill latency totals/maxima,
      and last submit/report cluster times. V14 records and snapshots these
      fields but does not use them for routing decisions.

Only explicitly ephemeral state may be omitted from the cluster snapshot:

    transient scratch buffers and reusable SBE encoders/decoders
    live heartbeat windows
    rolling telemetry that does not affect decisions, audit, future SOR policy,
      or replay evidence
    derived market-data observations that are rebuilt from current books or
      restarted cold before strategies are enabled

Startup must load snapshots before strategy activation, replay the ordered log
after the snapshot, reconcile venue truth for open orders/fills/positions, and
rebuild omitted telemetry before execution strategies are allowed to emit new
child orders. Tests must prove that an active execution strategy restored from
snapshot plus replay reaches the same parent state, child command sequence,
timer-owner table, plugin counters, and engine counters as uninterrupted replay.

---

# 11. Hot-Path Policy

## 11.1 Carryforward From V12 and V13

All V12 hot-path allocation rules and V13 §10 forbidden hot-path operations apply unchanged.

## 11.2 V14 Hot Paths

The following V14 surfaces are declared hot paths and must be benchmark-proven allocation-free after warmup:

    InventoryHedgeStrategy.onMarketDataTick
    InventoryHedgeStrategy.onPortfolioUpdate
    InventoryHedgeStrategy parent-intent emission
    ParallelVenueExecution.onParentIntent
    ParallelVenueExecution slice-plan computation
    ParallelVenueExecution.onChildExecution
    ParallelVenueExecution.onTimer
    SmartOrderRoutingExecution.onParentIntent
    SmartOrderRoutingExecution slice-plan computation
    SmartOrderRoutingExecution.onMarketDataTick (re-slice path)
    SmartOrderRoutingExecution.onChildExecution
    SmartOrderRoutingExecution.onTimer
    Mixed-precision OwnOrderOverlay query path for L2 venues
    BinanceL2MarketDataNormalizer SBE event production
    BinanceVenuePlugin order-entry policy enrichment

## 11.3 Required JMH Coverage

V14 must add JMH benchmarks with `-prof gc` for every surface in §11.2. Latency percentile evidence (p50, p90, p99, p99.9) is required for the parent-intent dispatch path, the slice-plan computation, and the re-slice path.

## 11.4 No Composition Allocation

The shared support classes in `platform-cluster/.../execution/support` must not introduce per-call allocation. If support classes hold preallocated bounded scratch state, that state must be owned by the calling execution strategy, not by the support class itself, to prevent cross-plugin state leakage.

---

# 12. Test Coverage and QA/UAT Gate

## 12.1 Carryforward From V13

The V13 mandatory task-owned test coverage contract carries forward in full. Every V14 production task includes task-owned tests covering positive, negative, edge, malformed, capacity, safe-drop/reject/counter, snapshot/load, replay, integration, allocation, latency percentile, and behavior-equivalence categories where applicable. Non-applicable categories are documented with reason.

## 12.2 V14-Specific Required Cases

Every V14 task card must add automated coverage for, where applicable:

    Mixed-precision OwnOrderOverlay queries: precise on Coinbase, conservative
      on Binance, in the same test scenario.
    Mixed-precision ExternalLiquidityView reads.
    Mixed-precision ConsolidatedL2Book aggregation.
    Cross-venue ArbStrategy edge detection.
    Cross-venue MultiLegContingentExecution leg-fill, hedge, and timer
      scenarios spanning Coinbase L3 and Binance L2.
    Parallel multi-venue MarketMakingStrategy: independent quote refresh,
      independent staleness expiry, no cross-venue interference.
    InventoryHedgeStrategy: threshold trigger, threshold non-trigger inside
      safe band, re-trigger gating during active parent, cooldown enforcement,
      cooldown extension on hedge failure, replay determinism, snapshot/load
      with active hedge parent.
    ParallelVenueExecution: slice planning, parallel child submission, full
      fill, partial fill, timer-driven residual cancel, all-children-rejected,
      parent cancel mid-flight, capacity full, replay, snapshot/load including
      plugin state, timer-owner state, and relevant execution stats.
    SmartOrderRoutingExecution: slice planning with fee scoring, re-slice on
      tick, re-slice cancel-and-resubmit ordering, re-slice failure, fee
      schedule edge cases, capacity full, replay, snapshot/load including
      plugin state, timer-owner state, reslice state, and relevant execution
      stats.
    Binance Ed25519 logon, heartbeat, sequence reset, and disconnect/reconnect.
    Binance L2 normalizer: snapshot, incremental refresh, snapshot replay,
      malformed message safe-drop, message sequence gap recovery.
    Two-venue partial outage: one venue disconnected while a parent has
      working children on both venues.
    Two-venue reconciliation: balance, position, working order, and parent
      state across both venues.
    Compatibility matrix: each unsupported pairing fails startup validation
      with a clear error.

## 12.3 Live-Wire E2E

Live-wire E2E must prove, with both Coinbase and Binance simulators running concurrently:

    ArbStrategy -> MultiLegIntent -> MultiLegContingentExecution -> OrderManager
      -> both gateways -> both simulators -> execution reports -> parent
      callback.

    InventoryHedgeStrategy -> HedgeIntent -> ParallelVenueExecution ->
      OrderManager -> both gateways -> both simulators -> execution reports
      -> parent callback.

    InventoryHedgeStrategy -> HedgeIntent -> SmartOrderRoutingExecution ->
      OrderManager -> both gateways -> both simulators -> execution reports,
      including a re-slice scenario triggered by simulator-driven depth shift
      -> parent callback.

    Parallel MarketMakingStrategy on both venues with independent quote
      refresh and execution reports.

Required live-wire E2E classes:

    platform-tooling/src/e2eTest/java/ig/rueishi/nitroj/exchange/e2e/
        BinanceFixL2LiveWireE2ETest.java
        CrossVenueArbLiveWireE2ETest.java
        InventoryHedgeParallelVenueLiveWireE2ETest.java
        InventoryHedgeSorLiveWireE2ETest.java
        ParallelMarketMakingLiveWireE2ETest.java

## 12.4 QA/UAT Gate

Real Coinbase QA/UAT remains blocked by V13 evidence requirements. Real Binance QA/UAT is additionally blocked by V14 evidence requirements:

    All V14 unit, integration, simulator, and live-wire E2E gates pass.
    All V14 deterministic replay evidence is archived.
    All V14 JMH allocation and latency reports are archived.
    Mixed-precision evidence is archived.
    Real Binance credential resolution is wired from the approved secret source.

V14 does not use real Binance QA/UAT to replace missing local evidence.

---

# 13. Explicit Non-Goals

V14 does not introduce:

    New FIX protocol plugins (Binance reuses FIX 4.4)
    WebSocket transport in the gateway
    Binance derivatives, futures, options, perpetuals, or non-spot products
    Binance WebSocket Market Data, WebSocket SBE Market Data, or WebSocket API
      for order entry
    Coinbase L3 or Coinbase venue plugin changes
    SBE schema changes
    RiskEngine semantic changes
    Modifications to V13 ImmediateLimitExecution, PostOnlyQuoteExecution, or
      MultiLegContingentExecution
    Modifications to V13 MarketMakingStrategy or ArbStrategy
    Execution strategy composition (routing + lifecycle layering)
    Multi-leg + smart-routing fusion
    Cross-venue inventory netting in MarketMakingStrategy
    Cross-venue position transfer or rebalancing automation
    USD/USDT basis trading or strategies
    TWAP, VWAP, POV, peg, iceberg, sequential-venue, dark-pool, RFQ,
      last-look-aware, stream-hitter, auction, or any execution algorithm
      not explicitly listed as a V14 built-in
    Queue-position-aware strategies
    L3-only signal strategies on L2 venues
    Latency-weighted routing
    Fill-quality-feedback routing
    Fill-probability modeling

Risk still gates every child order before submission. Non-V14 catalog items remain roadmap candidates for future releases with their own specs, plans, task cards, tests, and evidence.

---

# 14. Release Gate

Before V14 QA/UAT or production connectivity claims:

    JAVA_HOME=/usr/lib/jvm/java-21-openjdk-amd64 ./gradlew clean
    JAVA_HOME=/usr/lib/jvm/java-21-openjdk-amd64 ./gradlew check
    JAVA_HOME=/usr/lib/jvm/java-21-openjdk-amd64 ./gradlew e2eTest
    JAVA_HOME=/usr/lib/jvm/java-21-openjdk-amd64 ./gradlew :platform-benchmarks:jmh
    JAVA_HOME=/usr/lib/jvm/java-21-openjdk-amd64 ./gradlew :platform-benchmarks:jmhLatencyReport
    JAVA_HOME=/usr/lib/jvm/java-21-openjdk-amd64 scripts/v14-preflight-check.sh

The V14 evidence bundle must archive:

    Unit and integration reports for all new V14 production classes
    Mixed-precision OwnOrderOverlay, ExternalLiquidityView, and
      ConsolidatedL2Book reports
    Cross-venue ArbStrategy reports
    Parallel multi-venue MarketMakingStrategy reports
    InventoryHedgeStrategy unit and replay reports
    ParallelVenueExecution and SmartOrderRoutingExecution unit, replay, and
      snapshot/load reports
    BinanceFixL2LiveWireE2E reports
    CrossVenueArbLiveWireE2E reports
    InventoryHedgeParallelVenueLiveWireE2E and InventoryHedgeSorLiveWireE2E
      reports
    ParallelMarketMakingLiveWireE2E reports
    Deterministic replay reports including mixed-precision scenarios
    JMH allocation reports for all V14 hot-path surfaces
    Latency percentile reports for V14 dispatch paths
    Per-venue and aggregate risk limit configuration evidence
    Two-venue reconciliation and disconnect/reconnect evidence
    Security/operations preflight evidence inherited from V12 and V13,
      extended for the Binance credential surface
    V13-to-V14 behavior-equivalence reports for unchanged strategies and
      execution plugins

The V14 preflight gate extends the V13 preflight gate. Every gate from V12 and V13 remains required.
