#!/usr/bin/env bash
set -euo pipefail
cd "$(dirname "$0")"
if [[ -x ./gradlew ]]; then exec ./gradlew run; else exec gradle run; fi
