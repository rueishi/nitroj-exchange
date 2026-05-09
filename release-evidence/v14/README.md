# NitroJEx V14 Release Evidence

This directory is the archive target for TASK-425. It is populated by:

```bash
JAVA_HOME=/usr/lib/jvm/java-21-openjdk-amd64 scripts/archive-v14-release-evidence.sh
```

Run the archive script only after the full V14 verification gate passes:

```bash
JAVA_HOME=/usr/lib/jvm/java-21-openjdk-amd64 ./gradlew clean
JAVA_HOME=/usr/lib/jvm/java-21-openjdk-amd64 ./gradlew check
JAVA_HOME=/usr/lib/jvm/java-21-openjdk-amd64 ./gradlew e2eTest
JAVA_HOME=/usr/lib/jvm/java-21-openjdk-amd64 ./gradlew :platform-benchmarks:jmh
JAVA_HOME=/usr/lib/jvm/java-21-openjdk-amd64 ./gradlew :platform-benchmarks:jmhLatencyReport
JAVA_HOME=/usr/lib/jvm/java-21-openjdk-amd64 scripts/v14-preflight-check.sh
```

Archived evidence must include:

- mixed-precision Coinbase `BTC-USD` and Binance `BTCUSDT` identity tests
- cross-venue arb simulator/live-wire tests
- Hedge x ParallelVenue and Hedge x SOR live-wire tests
- V14 parent/execution unit tests for `ParallelVenueExecution`,
  `SmartOrderRoutingExecution`, and `InventoryHedgeStrategy`
- deterministic replay and V13-to-V14 behavior-equivalence tests
- JMH allocation and latency reports for the V14 hot-path owners
- reviewed V14 config files and the `config/v14-production-preflight.toml`
  release checklist

Real Coinbase and Binance QA/UAT are still blocked after this archive is
created unless the manual evidence in `MANIFEST.txt` and
`config/v14-production-preflight.toml` is complete and signed off.
