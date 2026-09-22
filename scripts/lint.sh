#!/usr/bin/env bash
set -euo pipefail

ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
cd "$ROOT_DIR"

echo "=== NSS Management Platform: Running Code Quality Checks ==="

STATUS=0

# 1. Check for committed secrets / accidental tracking of .env
echo "[LINT] Checking for tracked environment secrets..."
if git ls-files | grep -E '(^|\/)\.env($|\..*)' | grep -v '\.env\.example$' >/dev/null 2>&1; then
    echo "  [FAIL] Tracked .env file detected! Please untrack immediately." >&2
    STATUS=1
else
    echo "  [PASS] No tracked .env files found."
fi

# 2. Frontend type and code consistency checks
if [ -d "frontend" ] && command -v npm >/dev/null 2>&1; then
    echo "[LINT] Checking frontend TypeScript compilation..."
    if (cd frontend && npx tsc --noEmit); then
        echo "  [PASS] Frontend TypeScript check passed."
    else
        echo "  [FAIL] Frontend TypeScript errors found." >&2
        STATUS=1
    fi
fi

# 3. Analytics Python compilation
PYTHON_CMD=""
for cmd in python python3 py; do
    if command -v "$cmd" >/dev/null 2>&1 && "$cmd" --version >/dev/null 2>&1; then
        PYTHON_CMD="$cmd"
        break
    fi
done

if [ -n "$PYTHON_CMD" ] && [ -d "analytics" ]; then
    echo "[LINT] Checking Python syntax..."
    if $PYTHON_CMD -m py_compile analytics/main.py; then
        echo "  [PASS] Python syntax check passed."
    else
        echo "  [FAIL] Python syntax error detected." >&2
        STATUS=1
    fi
fi

if [ "$STATUS" -eq 0 ]; then
    echo "[INFO] All lint and quality checks passed."
else
    echo "[ERROR] Lint or quality checks failed." >&2
fi

exit "$STATUS"
