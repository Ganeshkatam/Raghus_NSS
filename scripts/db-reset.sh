#!/usr/bin/env bash
set -euo pipefail

ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
cd "$ROOT_DIR"

echo "=== NSS Management Platform: Reset Development Database ==="

# Check container running
if ! docker ps --format '{{.Names}}' | grep -w "nss-postgres" >/dev/null 2>&1; then
    echo "[INFO] Starting postgres container..."
    docker compose up -d postgres
    sleep 3
fi

echo "[WARN] Dropping and recreating database 'nss'..."
docker exec -i nss-postgres psql -U nss -d postgres <<'EOF'
SELECT pg_terminate_backend(pid) FROM pg_stat_activity WHERE datname = 'nss' AND pid <> pg_backend_pid();
DROP DATABASE IF EXISTS nss;
CREATE DATABASE nss;
GRANT ALL PRIVILEGES ON DATABASE nss TO nss;
EOF

echo "[INFO] Running fresh migrations..."
bash "$ROOT_DIR/scripts/db-migrate.sh"

echo "[INFO] Database reset complete."
