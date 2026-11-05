#!/usr/bin/env sh
set -eu
: "${SISCOM_MYSQL_CONFIG:?Defina la ruta de un archivo MySQL protegido con client user password host}"
mkdir -p respaldos
siscom_destino="respaldos/siscom-$(date +%Y%m%d-%H%M%S).sql"
mysqldump --defaults-extra-file="$SISCOM_MYSQL_CONFIG" --single-transaction --routines --triggers --set-gtid-purged=OFF siscom_db > "$siscom_destino"
test -s "$siscom_destino"
sha256sum "$siscom_destino" > "$siscom_destino.sha256"
printf '%s\n' "$siscom_destino"
