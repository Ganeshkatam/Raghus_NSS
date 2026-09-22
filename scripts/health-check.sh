#!/usr/bin/env bash
set -euo pipefail

ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
cd "$ROOT_DIR"

RETRIES=${1:-15}
DELAY=${2:-2}

echo "[INFO] Running health checks (Max retries: $RETRIES, Delay: ${DELAY}s)..."

# 1. PostgreSQL check
echo -n "[CHECK] PostgreSQL: "
pg_ok=0
for ((i=1; i<=RETRIES; i++)); do
    if docker exec nss-postgres pg_isready -U nss -d nss >/dev/null 2>&1; then
        pg_ok=1
        break
    fi
    sleep "$DELAY"
done

if [ "$pg_ok" -eq 1 ]; then
    echo "HEALTHY"
else
    echo "FAILED"
fi

# 2. Redis check
echo -n "[CHECK] Redis: "
redis_ok=0
for ((i=1; i<=RETRIES; i++)); do
    if [ "$(docker exec nss-redis redis-cli ping 2>/dev/null | tr -d '\r')" = "PONG" ]; then
        redis_ok=1
        break
    fi
    sleep "$DELAY"
done

if [ "$redis_ok" -eq 1 ]; then
    echo "HEALTHY"
else
    echo "FAILED"
fi

# 3. Backend check
echo -n "[CHECK] Backend API (/api/v1): "
backend_ok=0
for ((i=1; i<=RETRIES; i++)); do
    if curl -fsS http://localhost:8080/api/v1 >/dev/null 2>&1; then
        backend_ok=1
        break
    fi
    sleep "$DELAY"
done

if [ "$backend_ok" -eq 1 ]; then
    echo "HEALTHY"
else
    echo "WAITING/NOT RUNNING (optional if backend container is starting up)"
fi

if [ "$pg_ok" -eq 1 ] && [ "$redis_ok" -eq 1 ]; then
    echo "[INFO] Core infrastructure services are healthy."
    exit 0
else
    echo "[ERROR] Core infrastructure health check failed." >&2
    exit 1
fi
