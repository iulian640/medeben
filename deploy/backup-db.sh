#!/usr/bin/env bash
# Backup diario de la BD de MeDeben en el servidor (equivalente Linux de
# backup-db.ps1, que es para el PC de desarrollo).
#
# La instalación del cron (y la creación de ~/backups, que el propio cron
# necesita para su log ANTES de que este script corra) la hace
# instala-servidor.sh — aquí no hay que tocar nada.
#
# Restauración (ensayada en local con restaura-db.ps1; en el servidor):
#   gunzip -c ~/backups/medeben-FECHA.sql.gz | docker compose -f deploy/docker-compose.prod.yml exec -T db psql -U medeben medeben
set -euo pipefail
cd "$(dirname "$0")/.."

DIR_BACKUPS="$HOME/backups"
DIAS_RETENCION=14
mkdir -p "$DIR_BACKUPS"

FICHERO="$DIR_BACKUPS/medeben-$(date +%F-%H%M).sql.gz"
docker compose -f deploy/docker-compose.prod.yml exec -T db \
    pg_dump -U medeben --clean --if-exists medeben | gzip > "$FICHERO"
# pipefail cubre el exit de pg_dump, pero además se valida el contenido: un
# gzip de entrada vacía son ~20 bytes ([ -s ] no lo detectaría) y un dump
# parcial pequeño tampoco debe darse por bueno. El esquema real con datos
# no baja de unos pocos KB.
MIN_BYTES=1000
[ "$(stat -c%s "$FICHERO")" -ge "$MIN_BYTES" ] \
    || { echo "ERROR: backup sospechosamente pequeño ($(stat -c%s "$FICHERO") bytes) en $FICHERO"; exit 1; }
gunzip -t "$FICHERO" || { echo "ERROR: gzip corrupto en $FICHERO"; exit 1; }

find "$DIR_BACKUPS" -name 'medeben-*.sql.gz' -mtime +"$DIAS_RETENCION" -delete
echo "OK $(date -Is) → $FICHERO ($(du -h "$FICHERO" | cut -f1))"
