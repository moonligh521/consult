#!/bin/sh
# Daily backup for the single-host observability stack. Not a HA substitute.
# Restore is documented in docs/观测/capacity-and-backup.md.

set -eu

ROOT=$(CDPATH= cd -- "$(dirname "$0")" && pwd)
STAMP=$(date +%Y%m%d-%H%M%S)
DEST=${BACKUP_DIR:-$ROOT/backups}/$STAMP
COMPOSE="$ROOT/observability-compose.yml"
ENV_FILE="$ROOT/.env"

mkdir -p "$DEST"

docker compose -f "$COMPOSE" --env-file "$ENV_FILE" exec -T langfuse-postgres \
  pg_dump -U "${POSTGRES_USER:-langfuse}" "${POSTGRES_DB:-langfuse}" > "$DEST/postgres.sql"

docker compose -f "$COMPOSE" --env-file "$ENV_FILE" exec -T clickhouse \
  clickhouse-client --query "BACKUP DATABASE default TO Disk('backups', '$STAMP')" || \
  docker compose -f "$COMPOSE" --env-file "$ENV_FILE" exec -T clickhouse \
    clickhouse-client --query "SELECT 1" > "$DEST/clickhouse-alive.txt"

docker run --rm \
  --network ai-net \
  -v consult-observability_langfuse_minio_data:/data \
  -v "$DEST":/backup \
  docker.io/minio/mc:RELEASE.2025-04-16T18-13-26Z \
  mirror /data /backup/minio || true

find "${BACKUP_DIR:-$ROOT/backups}" -mindepth 1 -maxdepth 1 -mtime +7 -exec rm -rf {} +
echo "backup_ok=$DEST"
