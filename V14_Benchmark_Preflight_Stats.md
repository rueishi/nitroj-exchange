# V14 Benchmark, Preflight, and Allocation Stats

Generated: 2026-05-09  
Source archive: `release-evidence/v14`  
JMH source files:

- `release-evidence/v14/jmh-allocation-results.json`
- `release-evidence/v14/jmh-latency-results.json`

## Summary

The latest V14 benchmark results are not obviously slow for a WSL2
laptop/developer environment. The key Smart Order Routing paths are measured in
microseconds rather than milliseconds:

- SOR parent intent dispatch mean: about `1.8 us`.
- SOR slice plan mean: about `0.63 us`.
- SOR re-slice path mean: about `4.5 us`.
- Order manager parent ack/fill/release cycle mean: about `0.115 us`.
- Binance L2 normalizer event path mean: about `0.54 us`.

The results are good enough for local release evidence, regression tracking, and
hot-path allocation proof in this development setup. They should not be used as
production latency claims yet. The main caution is tail latency: some p99.9
values are much larger, such as SOR re-slice around `188 us`. In WSL2, those
tails can be affected by VM scheduling, laptop power management, OS
interruptions, profiler noise, and background processes.

The benchmark environment is intentionally modest rather than tuned for best
possible numbers. This helps show that NitroJEx can preserve low-latency,
deterministic behavior and zero-allocation hot-path evidence even under a
constrained developer setup. Practical interpretation: the mean, p50, and p90
numbers are promising; the p99/p99.9 numbers need to be remeasured on pinned,
production-like hardware before making absolute latency, throughput, or capacity
claims.

## Environment Caveat

These numbers were generated in a developer WSL2 environment. They are valid as
release evidence that the V14 gates were run, that benchmark coverage exists,
and that hot-path allocation behavior was measured under a repeatable local
setup. This medium/constrained environment is useful because it demonstrates
low-latency behavior without relying on specialized benchmark hardware. It
should not be marketed as production latency numbers or exchange
colocation/server-class performance claims.

Observed local environment:

| Field | Value |
|---|---|
| OS / kernel | Linux `6.6.87.2-microsoft-standard-WSL2` |
| Environment | WSL2 / Microsoft hypervisor |
| CPU | AMD Ryzen 5 7535HS with Radeon Graphics |
| Visible CPUs | 12 logical CPUs, 6 cores, 2 threads/core |
| Visible memory | 7.4 GiB |
| Swap | 2.0 GiB |
| JDK used by JMH | OpenJDK 21.0.10 |

Interpretation:

- Use these results for regression comparison inside this development setup.
- Use `measuredThreadAllocatedBytes = 0.0` as the local zero-allocation evidence
  for the declared hot paths.
- Re-run the full V14 gate on pinned production-like hardware before making
  absolute latency, throughput, or capacity claims.
- Archive CPU model, core count, memory, OS/kernel, JVM version, CPU governor,
  container/VM status, and benchmark command line with any future production
  benchmark report.

## Release Gate Summary

The V14 evidence archive manifest records these upstream gates as required and
completed before archive creation:

| Gate | Status |
|---|---:|
| `./gradlew clean` | Passed |
| `./gradlew check` | Passed |
| `./gradlew e2eTest` | Passed |
| `./gradlew :platform-benchmarks:jmh` | Passed |
| `./gradlew :platform-benchmarks:jmhLatencyReport` | Passed |
| `scripts/v14-preflight-check.sh` | Passed |
| `scripts/archive-v14-release-evidence.sh` | Passed |

Archived automated evidence:

| Evidence | Count / Status |
|---|---:|
| Archived test XML files | 95 |
| Total archived test cases | 828 |
| Failures | 0 |
| Errors | 0 |
| Skipped | 0 |
| Allocation benchmark entries | 49 |
| Latency benchmark entries | 44 |
| JMH version | 1.37 |
| JDK | OpenJDK 21.0.10 |

Manual evidence is still required before real Coinbase or Binance QA/UAT:

- Binance credential rotation and live FIX session signoff.
- Two-venue partial-outage and reconciliation rehearsal signoff.
- Deployment, monitoring, alert routing, failover, disaster recovery, and rollback signoff.

## Zero-Allocation Evidence

For the V14 execution hot paths that use the thread-allocation profiler,
`measuredThreadAllocatedBytes` reported `0.0`, which is the release evidence used
for zero-allocation hot-path claims.

The `gc.alloc.rate.norm` profiler sometimes reports tiny non-zero `B/op` values
or larger sampling-profiler noise. Treat `measuredThreadAllocatedBytes = 0.0` as
the primary zero-allocation evidence for the declared V14 hot paths below.

| Hot Path | Allocation Report Mean | GC `B/op` | Thread Allocated Bytes |
|---|---:|---:|---:|
| `BinanceL2NormalizerBenchmark.binanceIncrementalPublishesSbeEvent` | 527.109 ns/op | 0.0036 | 0.0 |
| `ExecutionStrategyEngineBenchmark.parentIntentDispatch` | 68.886 ns/op | 0.0049 | 0.0 |
| `ParallelVenueExecutionBenchmark.parentIntentDispatch` | 535.592 ns/op | 1.1581 | 0.0 |
| `ParallelVenueExecutionBenchmark.slicePlan` | 568.192 ns/op | 1.1843 | 0.0 |
| `SmartOrderRoutingExecutionBenchmark.parentIntentDispatch` | 1760.580 ns/op | 1.2445 | 0.0 |
| `SmartOrderRoutingExecutionBenchmark.slicePlan` | 536.196 ns/op | 1.1714 | 0.0 |
| `SmartOrderRoutingExecutionBenchmark.reSlicePath` | 3014.500 ns/op | 2.7946 | 0.0 |
| `InventoryHedgeStrategyBenchmark.baseQuantityTrigger` | 66822.800 ns/op | 11.6396 | 0.0 |
| `PostOnlyQuoteExecutionBenchmark.childFillCallbacks` | 891.888 ns/op | 104.7191 | 0.0 |
| `PostOnlyQuoteExecutionBenchmark.marketDataRefreshCallbacks` | 820.386 ns/op | 104.7255 | 0.0 |

## Latency Percentiles

All values are from `jmh-latency-results.json`.

| Benchmark | Mean | p50 | p90 | p99 | p99.9 |
|---|---:|---:|---:|---:|---:|
| `BinanceL2NormalizerBenchmark.binanceIncrementalPublishesSbeEvent` | 540.500 ns/op | 546.000 | 572.500 | 574.000 | 574.000 |
| `ExecutionStrategyEngineBenchmark.parentIntentDispatch` | 193.773 ns/op | 93.000 | 166.000 | 377.900 | 24184.576 |
| `ParallelVenueExecutionBenchmark.parentIntentDispatch` | 497.697 ns/op | 352.000 | 620.000 | 1914.100 | 20244.672 |
| `ParallelVenueExecutionBenchmark.slicePlan` | 699.191 ns/op | 383.000 | 735.000 | 2931.200 | 84971.008 |
| `SmartOrderRoutingExecutionBenchmark.parentIntentDispatch` | 1813.859 ns/op | 1056.000 | 2880.000 | 6797.440 | 85265.664 |
| `SmartOrderRoutingExecutionBenchmark.slicePlan` | 626.470 ns/op | 373.000 | 798.000 | 2594.800 | 30502.720 |
| `SmartOrderRoutingExecutionBenchmark.reSlicePath` | 4505.145 ns/op | 2684.000 | 5863.200 | 32544.960 | 188648.192 |
| `InventoryHedgeStrategyBenchmark.baseQuantityTrigger` | 74192.797 ns/op | 56448.000 | 136448.000 | 173529.600 | 329128.960 |
| `PostOnlyQuoteExecutionBenchmark.childFillCallbacks` | 897.629 ns/op | 743.000 | 1254.000 | 1744.000 | 4285.144 |
| `PostOnlyQuoteExecutionBenchmark.marketDataRefreshCallbacks` | 757.599 ns/op | 618.000 | 1052.000 | 1536.000 | 2754.760 |
| `OrderManagerBenchmark.createWithParentAckFillReleaseCycle` | 115.113 ns/op | 94.000 | 150.000 | 211.000 | 378.268 |

## Smart Order Routing Focus

V14's headline feature is Smart Order Routing. The latest archived evidence
covers the SOR execution-engine paths directly:

| SOR Surface | Mean | p99 | Thread Allocated Bytes |
|---|---:|---:|---:|
| Parent intent dispatch | 1813.859 ns/op | 6797.440 ns/op | 0.0 |
| Slice plan | 626.470 ns/op | 2594.800 ns/op | 0.0 |
| Re-slice path | 4505.145 ns/op | 32544.960 ns/op | 0.0 |

These numbers show SOR being measured as an execution-engine feature: the
strategy emits a parent hedge intent, while the execution strategy owns
fee-aware venue ranking, child order routing, deterministic re-slicing, and
parent/child lifecycle state.
