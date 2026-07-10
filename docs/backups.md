# Copias de seguridad de la base de datos

La BD de producción guarda la **evidencia** de los usuarios: el diario sellado
de fichajes y el historial de cuadrantes. Perderla es perder aquello para lo
que existe la app. Reglas de la casa:

1. **Backup diario automático** (pg_dump verificado antes de guardarse).
2. **Copia fuera de la máquina** (nube o disco externo): un backup en el mismo
   disco que la BD no cubre robo, ransomware ni disco muerto.
3. **Ensayo de restauración periódico**: un backup solo existe si se ha
   restaurado alguna vez.

## Hacer un backup a mano

```powershell
cd C:\Users\iulia\Documents\tedeben\deploy
.\backup-db.ps1 -CopiaExterna "<carpeta de OneDrive o disco externo>"
```

Qué hace: `pg_dump` (formato custom, comprimido) dentro del servicio `db` del
compose de producción → verifica el archivo con `pg_restore --list` (un dump
corrupto NO se guarda) → lo copia a `Documents\medeben-backups\` → segunda
copia en `-CopiaExterna` si se indica → purga los backups locales de más de
30 días (`-DiasRetencion` para cambiarlo).

El dump nunca atraviesa un pipe de PowerShell: se escribe a fichero dentro del
contenedor y sale con `docker cp` (PowerShell 5.1 corrompe binarios por stdout).

## Programarlo (diario, 03:30)

Una vez, en una PowerShell **como administrador** (ajusta `-CopiaExterna`):

```powershell
$accion = New-ScheduledTaskAction -Execute 'powershell.exe' `
  -Argument '-NoProfile -ExecutionPolicy Bypass -File C:\Users\iulia\Documents\tedeben\deploy\backup-db.ps1 -CopiaExterna "C:\Users\iulia\OneDrive\medeben-backups"'
$disparo = New-ScheduledTaskTrigger -Daily -At 03:30
Register-ScheduledTask -TaskName 'MeDeben backup diario' -Action $accion -Trigger $disparo -Description 'pg_dump diario de la BD de MeDeben con copia externa'
```

Comprobar que corrió: `Get-ScheduledTaskInfo -TaskName 'MeDeben backup diario'`
y mirar que aparece el fichero del día en `Documents\medeben-backups\`.

> El compose de producción debe estar levantado a esa hora; si no, el script
> falla ruidosamente (no deja un backup vacío).

## Ensayar la restauración (hazlo de vez en cuando)

Inofensivo: restaura en un Postgres efímero aparte, enseña el recuento de
filas por tabla y lo destruye. Producción no se toca.

```powershell
cd C:\Users\iulia\Documents\tedeben\deploy
.\restaura-db.ps1 -Fichero C:\Users\iulia\Documents\medeben-backups\medeben-<fecha>.dump
```

Si el ensayo falla, ese backup no vale — investiga antes de necesitarlo.

## Restaurar producción de verdad (desastre)

**Destructivo**: machaca la BD actual con el contenido del dump.

```powershell
docker compose -f deploy/docker-compose.prod.yml stop backend
.\restaura-db.ps1 -Fichero <dump> -SobreLaBaseDeProduccion   # pide escribir RESTAURAR
docker compose -f deploy/docker-compose.prod.yml start backend
```

Después: comprobar `/api/v1/health` y un login real.

## Cuando haya hosting Linux

Estos scripts son la solución del prod local en Windows. Con el VPS, lo mismo
en cron (`pg_dump` + `pg_restore --list` + copia a otro sitio + ensayo), o el
servicio de backups gestionado del proveedor si lo hay — pero el ensayo de
restauración sigue siendo obligatorio, gestione quien gestione el backup.
