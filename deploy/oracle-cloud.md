# Deploy de MeDeben en Oracle Cloud Free

> Decidido el 2026-07-15: se lanza en el **Always Free tier de Oracle Cloud**
> (0 €/mes) en vez del Hetzner CX22 del plan original. Servidores en la UE
> (región de Madrid). La política de privacidad ya nombra a Oracle; el
> RAT/EIPD internos se actualizan en paralelo.

## Qué da el tier gratuito (verificado 2026-07-15)

- **VM ARM Ampere A1**: hasta **2 OCPU y 12 GB de RAM** en total, para
  siempre. (OJO: Oracle lo recortó el 15-jun-2026; las guías antiguas dicen
  4 OCPU/24 GB y ya no es verdad.) Para Spring + Postgres + nginx + Caddy
  con nuestro tráfico de lanzamiento sobra.
- **~200 GB de almacenamiento** en bloque (el boot volume mínimo de 47 GB vale).
- 1 **IP pública reservada** (estática), gratis mientras esté asignada a la
  instancia.
- Letras pequeñas (todas verificadas):
  - La **capacidad ARM gratuita escasea**: crear la VM puede dar
    "Out of capacity". Se reintenta a otras horas o cambiando el availability
    domain; con paciencia sale sin pagar. (Pasarse a Pay As You Go mejora la
    prioridad y lo Always Free sigue siendo gratis, pero no hace falta de
    entrada.)
  - Oracle puede **parar instancias Always Free ociosas** (avisa por email
    antes). La nuestra sirve tráfico real: no aplica, pero vigila el email de
    la cuenta.
  - El alta pide tarjeta con una retención temporal de ~1 € que no se cobra.

## Parte de Iulian (cuenta e infraestructura, ~30 min)

1. **Cuenta**: <https://signup.oraclecloud.com> → email + tarjeta.
   **Home region: `Spain Central (Madrid)`** — no se puede cambiar después y
   los recursos Always Free viven ahí. (Si Madrid diera "out of capacity"
   persistente durante días, la alternativa UE es Frankfurt — pero entonces
   la política de privacidad debe decir Alemania en vez de España: avísame.)
   RGPD: el DPA art. 28 de Oracle viene **incorporado a las condiciones del
   servicio** que aceptas en el alta — no se firma nada aparte.
2. **VM**: Compute → Instances → Create instance:
   - Image: **Ubuntu 24.04** (aarch64/ARM).
   - Shape: **VM.Standard.A1.Flex**, 2 OCPU / 12 GB (el máximo Always Free).
   - SSH keys → *Paste public key* → pegar tu clave pública (en PowerShell:
     `Get-Content ~\.ssh\id_ed25519.pub | Set-Clipboard`).
   - Networking: la VCN nueva que propone, con IP pública asignada.
3. **IP estática**: Instance details → attached VNIC → IPv4 Addresses →
   editar la IP pública → cambiar de *Ephemeral* a **Reserved** (que no
   cambie al reiniciar).
4. **Abrir 80/443 en la VCN**: Networking → Virtual Cloud Networks → la VCN
   → la subnet → su **Security List** → Add Ingress Rules:
   - Source `0.0.0.0/0`, TCP, destination port `80`
   - Source `0.0.0.0/0`, TCP, destination port `443`
   (El 22/SSH ya viene abierto. El firewall DENTRO de la VM lo abre
   `instala-servidor.sh`; hacen falta los dos.)
5. **DNS de `medeben.net`** (Cloudflare): registro `A` de `@` y de `www` →
   la IP reservada. **Modo "DNS only" (nube gris), NO proxied**: el proxy
   naranja de Cloudflare rompería el reto ACME de Caddy y metería un tercero
   en el flujo de datos que la política de privacidad no menciona. TTL corto
   (300 s) por si hay que cambiar la IP.

## Despliegue (con la IP ya en mano)

Desde PowerShell en el PC:

```powershell
# 1. Entrar (primera vez: aceptar el fingerprint)
ssh ubuntu@IP

# 2. En el servidor: firewall + Docker + Caddy + repo + cron de backup
curl -fsSL https://raw.githubusercontent.com/iulian640/medeben/main/deploy/instala-servidor.sh | bash
exit
# ... y RECONECTAR (el grupo docker solo aplica a sesiones nuevas)

# 3. Desde el PC: copiar los DOS ficheros de secretos (no están en el repo)
scp C:\Users\iulia\Documents\tedeben\deploy\.env ubuntu@IP:~/medeben/deploy/.env
scp C:\Users\iulia\Documents\tedeben\frontend\.env.local ubuntu@IP:~/medeben/frontend/.env.local

# 4. Con el DNS YA apuntando (paso 5 de arriba): desplegar
ssh ubuntu@IP "cd ~/medeben && bash deploy/despliega.sh"
```

## Verificación post-deploy

- `https://medeben.net` carga con candado (Caddy pide el certificado solo;
  necesita el DNS propagado y el 80 abierto para el reto ACME).
- `https://medeben.net/api/v1/health` responde.
- `https://www.medeben.net` redirige al apex.
- `/aviso-legal` muestra NIF y domicilio (LSSI) — si salen en blanco, faltó
  `frontend/.env.local` en el build.
- Cabeceras: <https://securityheaders.com> sobre medeben.net.
- Registro + login de una cuenta de prueba; borrarla después desde la app.
- El primer backup: `ssh ubuntu@IP "bash ~/medeben/deploy/backup-db.sh"` y
  comprobar que aparece en `~/backups/`.

## Actualizaciones posteriores

```bash
ssh ubuntu@IP "cd ~/medeben && bash deploy/despliega.sh"
```

(`git pull` + rebuild + recarga; los datos viven en el volumen
`medeben-datos` y sobreviven a los redeploys. El clon del servidor es de
solo lectura: no editar ficheros ahí, que el `--ff-only` aborta.)
