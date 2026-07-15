#!/usr/bin/env bash
# Despliegue (y re-despliegue) de MeDeben en el servidor.
# Uso, desde la raíz del repo en el servidor:
#   bash deploy/despliega.sh
# Requisitos previos: instala-servidor.sh ejecutado, deploy/.env y
# frontend/.env.local copiados desde el PC (ver oracle-cloud.md).
set -euo pipefail
cd "$(dirname "$0")/.."

echo "== Comprobaciones previas =="
# El grupo docker solo aplica en sesiones nuevas: tras instala-servidor.sh
# hay que reconectar por SSH, o todo lo de abajo muere con "permission denied".
docker info >/dev/null 2>&1 \
    || { echo "Sin acceso a Docker: reconecta por SSH (o ejecuta 'newgrp docker') y reintenta."; exit 1; }
# Sin estos ficheros el deploy sale ILEGAL o roto; mejor parar aquí.
[ -f deploy/.env ] || { echo "FALTA deploy/.env (secretos de BD y JWT). Cópialo desde tu PC."; exit 1; }
# El aviso legal sin la identificación LSSI (art. 10) no puede publicarse
# con las donaciones activas: el .env.local inyecta NIF y domicilio al build.
[ -f frontend/.env.local ] || { echo "FALTA frontend/.env.local (identificación LSSI). Cópialo desde tu PC."; exit 1; }
grep -q "VITE_RESPONSABLE_NIF=." frontend/.env.local \
    || { echo "frontend/.env.local está sin rellenar (VITE_RESPONSABLE_NIF vacío)."; exit 1; }
# Contienen la password de la BD y el secreto JWT: que no queden legibles
# para cualquiera (scp los deja en 0644).
chmod 600 deploy/.env frontend/.env.local

echo "== Código al día =="
git pull --ff-only

echo "== Build del frontend (Node en contenedor, nada que instalar en el host) =="
# A un directorio de staging y swap al final: dist/ lo sirve nginx EN VIVO
# (bind mount) y Vite lo vacía al empezar — construir en el sitio dejaría la
# web en 404 durante el build, o rota si el build falla a medias.
# --user: que los artefactos no queden a nombre de root en el host.
rm -rf frontend/dist.new
docker run --rm --user "$(id -u):$(id -g)" -e HOME=/tmp \
    -v "$PWD/frontend":/app -w /app node:22-alpine \
    sh -c "npm ci --no-audit --no-fund && npm run build -- --outDir dist.new"
rm -rf frontend/dist
mv frontend/dist.new frontend/dist

echo "== Backend + BD + nginx =="
docker compose -f deploy/docker-compose.prod.yml up -d --build

echo "== Caddy (TLS) =="
if ! cmp -s deploy/Caddyfile /etc/caddy/Caddyfile; then
    sudo cp deploy/Caddyfile /etc/caddy/Caddyfile
    sudo systemctl reload caddy
    echo "   Caddyfile actualizado y recargado"
else
    echo "   Caddyfile sin cambios"
fi

echo "== Salud =="
# El backend aplica las migraciones Flyway al arrancar: darle margen.
# Al 8080 (el nginx del compose); el 80 del host es de Caddy.
for i in $(seq 1 30); do
    if curl -fsS http://127.0.0.1:8080/api/v1/health >/dev/null 2>&1; then
        echo "   backend sano (intento $i)"
        echo
        echo "Desplegado. Prueba desde fuera: https://medeben.net"
        exit 0
    fi
    sleep 5
done
echo "El health check no respondió tras 150s. Mira los logs:"
echo "  docker compose -f deploy/docker-compose.prod.yml logs backend --tail 50"
exit 1
