#!/usr/bin/env bash
set -euo pipefail

ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
cd "$ROOT_DIR"

echo "=== NSS Management Platform: Running CI Pipeline Locally ==="

echo "--- Step 1: Quality and Lint Checks ---"
bash "$ROOT_DIR/scripts/lint.sh"

echo "--- Step 2: Build Verification ---"
bash "$ROOT_DIR/scripts/build.sh"

echo "--- Step 3: Automated Test Suite ---"
bash "$ROOT_DIR/scripts/test.sh"

echo "=== NSS CI Pipeline Passed Successfully ==="
