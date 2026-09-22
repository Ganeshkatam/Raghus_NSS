#!/usr/bin/env bash
set -euo pipefail

ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
cd "$ROOT_DIR"

echo "=== NSS Management Platform: Loading Seed Data ==="

# Check container running
if ! docker ps --format '{{.Names}}' | grep -w "nss-postgres" >/dev/null 2>&1; then
    echo "[INFO] Starting postgres container..."
    docker compose up -d postgres
    sleep 3
fi

SEED_DIR="database/seed"

if [ -d "$SEED_DIR" ]; then
    for file in "$SEED_DIR"/*.sql; do
        [ -f "$file" ] || continue
        echo "  [SEED] Executing $(basename "$file")..."
        docker exec -i nss-postgres psql -U nss -d nss < "$file"
    done
else
    echo "[INFO] No database/seed directory found. Inserting baseline metadata seed..."
    docker exec -i nss-postgres psql -U nss -d nss <<'EOF'
INSERT INTO app_metadata(key, value)
VALUES ('environment', 'development'), ('seeded_at', CURRENT_TIMESTAMP::text)
ON CONFLICT (key) DO UPDATE SET value = EXCLUDED.value;
EOF
fi

echo "[INFO] Seed data loaded successfully."
