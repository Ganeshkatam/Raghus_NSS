#!/usr/bin/env bash
set -euo pipefail

ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
cd "$ROOT_DIR"

echo "=== NSS Management Platform: Starting Dev Environment ==="

if [ -f ".env" ]; then
    set -a
    # shellcheck disable=SC1091
    source .env
    set +a
fi

# Ensure docker is available
command -v docker >/dev/null 2>&1 || { echo "[ERROR] Docker is required to run the development stack." >&2; exit 1; }

# Start postgres and redis (or all services if requested)
TARGET=${1:-"all"}

if [ "$TARGET" = "infra" ]; then
    echo "[INFO] Starting database and cache infrastructure..."
    docker compose up -d postgres redis
elif [ "$TARGET" = "all" ]; then
    echo "[INFO] Starting complete Docker Compose development stack..."
    docker compose up -d --build
else
    echo "[INFO] Starting requested service(s): $*..."
    docker compose up -d "$@"
fi

# Run health check
bash "$ROOT_DIR/scripts/health-check.sh" 10 2 || true

echo "[INFO] Environment ready."
echo "  - PostgreSQL: localhost:5432 (DB: nss)"
echo "  - Redis:      localhost:6379"
echo "  - Backend:    http://localhost:8080/api/v1"
