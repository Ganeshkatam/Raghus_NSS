#!/usr/bin/env bash
set -euo pipefail

ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
cd "$ROOT_DIR"

echo "=== NSS Management Platform: Running Database Migrations ==="

# Check container running
if ! docker ps --format '{{.Names}}' | grep -w "nss-postgres" >/dev/null 2>&1; then
    echo "[INFO] PostgreSQL container not running. Starting it now..."
    docker compose up -d postgres
    sleep 3
fi

# Ensure schema_migrations table exists
docker exec -i nss-postgres psql -U nss -d nss <<'EOF'
CREATE TABLE IF NOT EXISTS schema_migrations (
    version VARCHAR(100) PRIMARY KEY,
    applied_at TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP
);
EOF

# Find all migration files in database/init or backend/src/main/resources/db/migration
MIGRATION_DIRS=("database/init" "backend/src/main/resources/db/migration")

for dir in "${MIGRATION_DIRS[@]}"; do
    if [ -d "$dir" ]; then
        echo "[INFO] Checking migrations in $dir..."
        for file in "$dir"/*.sql; do
            [ -f "$file" ] || continue
            filename=$(basename "$file")
            
            # Check if already applied
            applied=$(docker exec -i nss-postgres psql -U nss -d nss -tAc "SELECT COUNT(*) FROM schema_migrations WHERE version = '$filename';")
            
            if [ "$applied" -eq 0 ]; then
                echo "  [MIGRATE] Applying $filename..."
                docker exec -i nss-postgres psql -U nss -d nss < "$file"
                docker exec -i nss-postgres psql -U nss -d nss -c "INSERT INTO schema_migrations (version) VALUES ('$filename');" >/dev/null
                echo "  [SUCCESS] Applied $filename"
            else
                echo "  [SKIP] $filename already applied."
            fi
        done
    fi
done

echo "[INFO] Database migrations complete."
