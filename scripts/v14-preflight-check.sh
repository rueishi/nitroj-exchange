#!/usr/bin/env bash
set -euo pipefail

# V14 QA/UAT gate runner.
#
# V14 extends the V12 gateway/book/risk evidence gate and the V13
# parent/execution evidence gate with Binance, mixed-precision, inventory hedge,
# parallel venue execution, SOR, and two-venue live-wire proof. This script is a
# local release gate only. It does not connect to production venues, load real
# credentials, or replace the manual QA/UAT evidence listed below.

export JAVA_HOME="${JAVA_HOME:-/usr/lib/jvm/java-21-openjdk-amd64}"

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
ROOT_DIR="$(cd "${SCRIPT_DIR}/.." && pwd)"

"${SCRIPT_DIR}/v12-preflight-check.sh"
"${SCRIPT_DIR}/v13-preflight-check.sh"

"${ROOT_DIR}/gradlew" clean
"${ROOT_DIR}/gradlew" check
"${ROOT_DIR}/gradlew" e2eTest
"${ROOT_DIR}/gradlew" :platform-benchmarks:jmh
"${ROOT_DIR}/gradlew" :platform-benchmarks:jmhLatencyReport
"${ROOT_DIR}/gradlew" :platform-benchmarks:verifyJmhReports

cat <<'EOT'
Manual V14 evidence required before real two-venue QA/UAT:
- review and archive config/v14-production-preflight.toml
- V12 and V13 preflight evidence remains attached and valid
- Binance Ed25519 credential handling, credential rotation, and FIX session signoff
- Binance L2 snapshot/incremental/gap/malformed evidence and operational alert routing
- venue-indifferent InventoryHedgeStrategy, ParallelVenueExecution, and SmartOrderRoutingExecution live-wire evidence
- SOR fee schedule, re-slice, cancel/resubmit ordering, re-slice failure, and minimum interval evidence
- mixed-precision Coinbase BTC-USD and Binance BTCUSDT identity evidence with no implicit USD/USDT basis arb
- two-venue partial outage, reconciliation, deployment, failover, disaster recovery, and rollback signoff
- JMH allocation and latency reports for every V14 hot path in spec section 11.2
EOT
