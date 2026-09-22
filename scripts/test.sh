#!/usr/bin/env bash
set -euo pipefail

ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
cd "$ROOT_DIR"

echo "=== NSS Management Platform: Running Automated Tests ==="

STATUS=0

# 1. Frontend Checks
if [ -d "frontend" ] && command -v npm >/dev/null 2>&1; then
    echo "[TEST] Running frontend type-check..."
    if (cd frontend && npx tsc --noEmit); then
        echo "  [PASS] Frontend type checking passed."
    else
        echo "  [FAIL] Frontend type checking failed." >&2
        STATUS=1
    fi
fi

# 2. Analytics Validation & Unit Tests
PYTHON_CMD=""
for cmd in python python3 py; do
    if command -v "$cmd" >/dev/null 2>&1 && "$cmd" --version >/dev/null 2>&1; then
        PYTHON_CMD="$cmd"
        break
    fi
done

if [ -n "$PYTHON_CMD" ] && [ -d "analytics" ]; then
    echo "[TEST] Testing analytics service..."
    if $PYTHON_CMD -c "
from analytics.main import summarize_service_hours
res = summarize_service_hours([{'volunteer_id': 1, 'hours': 5}, {'volunteer_id': 1, 'hours': 3}])
assert res.loc[0, 'hours'] == 8, 'Calculation mismatch'
print('  [PASS] Analytics service hours summary test passed.')
"; then
        :
    else
        echo "  [FAIL] Analytics test failed." >&2
        STATUS=1
    fi
fi

# 3. Backend Verification
if command -v docker >/dev/null 2>&1; then
    echo "[TEST] Verifying backend compilation and tests via Docker build..."
    if docker build -t nss-backend-test:latest ./backend; then
        echo "  [PASS] Backend compilation and build test passed."
    else
        echo "  [FAIL] Backend build/test failed." >&2
        STATUS=1
    fi
fi

if [ "$STATUS" -eq 0 ]; then
    echo "[INFO] All tests completed successfully."
else
    echo "[ERROR] One or more test suites failed." >&2
fi

exit "$STATUS"
