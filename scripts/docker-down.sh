#!/usr/bin/env bash
set -euo pipefail

ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
cd "$ROOT_DIR"

command -v docker >/dev/null 2>&1 || { echo "[ERROR] Docker is not installed or not in PATH." >&2; exit 1; }

echo "[INFO] Stopping Docker services..."
docker compose down "$@"
echo "[INFO] Docker services stopped."
