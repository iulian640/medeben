#!/usr/bin/env bash
# Preparación ÚNICA del servidor de MeDeben (Oracle Cloud Free, Ubuntu 24.04 ARM).
# Uso, ya conectado por SSH como el usuario `ubuntu`:
#   bash instala-servidor.sh
# Es idempotente: se puede re-ejecutar sin romper nada.
set -euo pipefail

echo "== 1/5 Firewall del SISTEMA (Oracle bloquea 80/443 por iptables, no solo en la VCN) =="
# Las imágenes de Ubuntu de Oracle traen iptables restrictivo de fábrica:
# abrir el puerto en la Security List de la VCN NO basta, hay que abrirlo
# también aquí. La regla se inserta justo ANTES del primer REJECT/DROP de la
# cadena (posición calculada en runtime, no hardcodeada: el layout de la
# imagen puede cambiar y una posición fija dejaría la regla DESPUÉS del
# REJECT — puerto cerrado en silencio).
abre_puerto() {
    local cmd="$1" puerto="$2"
    if sudo "$cmd" -C INPUT -m state --state NEW -p tcp --dport "$puerto" -j ACCEPT 2>/dev/null; then
        echo "   [$cmd] puerto $puerto ya estaba abierto"
        return
    fi
    local linea
    linea=$(sudo "$cmd" -L INPUT --line-numbers -n | awk '$2 ~ /REJECT|DROP/{print $1; exit}')
    sudo "$cmd" -I INPUT "${linea:-1}" -m state --state NEW -p tcp --dport "$puerto" -j ACCEPT
    echo "   [$cmd] puerto $puerto abierto (antes de la regla ${linea:-1})"
}
for puerto in 80 443; do
    abre_puerto iptables "$puerto"
    # También IPv6: la imagen trae el mismo REJECT en ip6tables. Aunque hoy
    # medeben.net solo publique registro A, así un AAAA futuro no nace roto.
    abre_puerto ip6tables "$puerto"
done
sudo netfilter-persistent save

echo "== 2/5 Docker =="
if ! command -v docker >/dev/null; then
    curl -fsSL https://get.docker.com | sudo sh
    sudo usermod -aG docker "$USER"
    echo "   Docker instalado. OJO: cierra sesión y vuelve a entrar para usar docker sin sudo."
else
    echo "   Docker ya instalado: $(docker --version)"
fi

echo "== 3/5 Caddy (terminador TLS) =="
if ! command -v caddy >/dev/null; then
    sudo apt-get update -qq
    sudo apt-get install -y -qq debian-keyring debian-archive-keyring apt-transport-https curl
    curl -1sLf 'https://dl.cloudsmith.io/public/caddy/stable/gpg.key' \
        | sudo gpg --dearmor -o /usr/share/keyrings/caddy-stable-archive-keyring.gpg
    curl -1sLf 'https://dl.cloudsmith.io/public/caddy/stable/debian.deb.txt' \
        | sudo tee /etc/apt/sources.list.d/caddy-stable.list >/dev/null
    sudo apt-get update -qq
    sudo apt-get install -y -qq caddy
else
    echo "   Caddy ya instalado: $(caddy version)"
fi

echo "== 4/5 Código =="
if [ ! -d "$HOME/medeben" ]; then
    git clone https://github.com/iulian640/medeben.git "$HOME/medeben"
else
    echo "   repo ya clonado en ~/medeben"
fi

echo "== 5/5 Backup diario (obligatorio antes de usuarios reales) =="
# El directorio se crea AQUÍ y no solo en backup-db.sh: la redirección del
# cron (>> ~/backups/backup.log) la abre la shell ANTES de ejecutar el script,
# así que sin esto la primera ejecución moriría sin dejar rastro.
mkdir -p "$HOME/backups"
LINEA_CRON='0 4 * * * bash $HOME/medeben/deploy/backup-db.sh >> $HOME/backups/backup.log 2>&1'
if ! crontab -l 2>/dev/null | grep -qF 'backup-db.sh'; then
    (crontab -l 2>/dev/null; echo "$LINEA_CRON") | crontab -
    echo "   cron instalado: backup diario a las 04:00"
else
    echo "   cron del backup ya instalado"
fi

cat <<'FIN'

Servidor preparado. Siguientes pasos (ver deploy/oracle-cloud.md):
  1. RECONECTA por SSH (salir y volver a entrar): el grupo docker no aplica
     a esta sesión y despliega.sh lo necesita.
  2. Copiar los secretos DESDE TU PC (no están en el repo):
       scp deploy/.env ubuntu@IP:~/medeben/deploy/.env
       scp frontend/.env.local ubuntu@IP:~/medeben/frontend/.env.local
  3. Apuntar el DNS de medeben.net a la IP de este servidor ANTES de
     desplegar (Caddy necesita resolverlo para sacar el certificado).
  4. Desplegar:  cd ~/medeben && bash deploy/despliega.sh
FIN
