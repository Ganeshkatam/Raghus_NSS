#!/usr/bin/env bash
set -euo pipefail

ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
cd "$ROOT_DIR"

echo "=== NSS Management Platform: Building All Components ==="

# 1. Frontend Build
if [ -d "frontend" ] && command -v npm >/dev/null 2>&1; then
    echo "[INFO] Building frontend..."
    (cd frontend && npm run build)
else
    echo "[WARN] Skipping frontend build (npm or frontend dir unavailable)."
fi

# 2. Backend Build
echo "[INFO] Building backend..."
if command -v docker >/dev/null 2>&1; then
    docker build -t nss-backend:latest ./backend
else
    echo "[ERROR] Docker is required to build the backend image." >&2
    exit 1
fi

# 3. Analytics Validation
PYTHON_CMD=""
for cmd in python python3 py; do
    if command -v "$cmd" >/dev/null 2>&1 && "$cmd" --version >/dev/null 2>&1; then
        PYTHON_CMD="$cmd"
        break
    fi
done

if [ -n "$PYTHON_CMD" ] && [ -d "analytics" ]; then
    echo "[INFO] Validating analytics module ($PYTHON_CMD)..."
    $PYTHON_CMD -m py_compile analytics/main.py
fi

echo "[INFO] All components built successfully."
