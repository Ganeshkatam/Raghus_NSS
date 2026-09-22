#!/usr/bin/env bash
set -euo pipefail

ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
cd "$ROOT_DIR"

echo "=== NSS Management Platform: Project Setup ==="

# 1. Environment file setup
if [ ! -f ".env" ]; then
    if [ -f ".env.example" ]; then
        echo "[INFO] Creating .env from .env.example..."
        cp .env.example .env
    else
        echo "[WARN] .env.example not found, skipping .env generation."
    fi
else
    echo "[INFO] Existing .env file found."
fi

# 2. Prerequisites inspection
echo "[INFO] Checking development tools..."
for tool in git docker node npm; do
    if command -v "$tool" >/dev/null 2>&1; then
        echo "  [OK] $tool is available: $(command -v "$tool")"
    else
        echo "  [WARN] $tool is not installed or not in PATH."
    fi
done

# 3. Frontend dependency installation
if [ -d "frontend" ] && command -v npm >/dev/null 2>&1; then
    echo "[INFO] Installing frontend dependencies..."
    (cd frontend && npm install)
else
    echo "[WARN] Skipping frontend dependency installation."
fi

# 4. Analytics environment setup
PYTHON_CMD=""
for cmd in python python3 py; do
    if command -v "$cmd" >/dev/null 2>&1 && "$cmd" --version >/dev/null 2>&1; then
        PYTHON_CMD="$cmd"
        break
    fi
done

if [ -n "$PYTHON_CMD" ] && [ -d "analytics" ]; then
    echo "[INFO] Setting up Python virtual environment for analytics using $PYTHON_CMD..."
    if [ ! -d "analytics/.venv" ]; then
        $PYTHON_CMD -m venv analytics/.venv || true
    fi
    if [ -f "analytics/requirements.txt" ]; then
        if [ -f "analytics/.venv/bin/pip" ]; then
            analytics/.venv/bin/pip install -r analytics/requirements.txt || true
        elif [ -f "analytics/.venv/Scripts/pip.exe" ]; then
            analytics/.venv/Scripts/pip.exe install -r analytics/requirements.txt || true
        fi
    fi
fi

echo "[INFO] Setup completed successfully."
