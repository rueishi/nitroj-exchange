#!/usr/bin/env bash

# Starts the dedicated Binance gateway process. The generic wrapper validates
# that config/gateway-2.toml points to venue ID 2 and that venues.toml maps that
# ID to BINANCE before launching the gateway jar.

set -euo pipefail

SCRIPT_DIR="$(cd -- "$(dirname -- "${BASH_SOURCE[0]}")" && pwd)"
exec "${SCRIPT_DIR}/gateway-start.sh" BINANCE config/gateway-2.toml
