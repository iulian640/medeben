# Índice maestro de convenios (toda España) — CORPUS COMPLETO

Mapa de los convenios de hostelería que cubre la app, por CCAA y provincia.
**Estado: COMPLETO a 2026-07-08.** Cobertura territorial 100%. Regla D32: cada uno en su **última** publicación con tablas.

**Leyenda:** ✅ transcrito de la imagen oficial celda a celda **+ spot-check independiente OK (0 discrepancias)**. Cada dato con su artículo. Lo no publicado → `pendiente` (nunca inventado).

## Subsectores (ADR, 3 subsectores)
1. **Hospedaje** (hoteles) — solo 3 territorios lo tienen como convenio propio separado: Madrid, Gipuzkoa, La Rioja. En el resto va DENTRO del convenio provincial de hostelería.
2. **Hostelería** (bares, restaurantes, cafeterías) — un convenio por provincia.
3. **Restauración colectiva** (comedores escolares, hospitales) → **1 convenio ESTATAL** ✅ (BOE-A-2025-12598) con **los 49 anexos provinciales completos**.
4. **ALEH VI** (marco estatal, BOE-A-2023-6344) ✅ — al que remiten ~37 provinciales para **periodo de prueba** y **régimen disciplinario**. (Ojo: modificación firmada abr-2026 aún sin publicar en BOE → integrar cuando salga.)

---

## Estado por provincia — todas ✅

### Andalucía (8/8)
- Almería ✅ · Cádiz ✅ · Córdoba ✅ · Granada ✅ · Huelva ✅ · Jaén ✅ · Málaga ✅ · Sevilla ✅ (unificado hoteles+restauración)

### Aragón (3/3)
- Huesca ✅ · Teruel ✅ · Zaragoza ✅

### Asturias ✅ · Illes Balears ✅ · Cantabria ✅ · Navarra ✅ · La Rioja ✅ (×2: hostelería + hospedaje) · Región de Murcia ✅

### Canarias (2/2)
- Las Palmas ✅ · Santa Cruz de Tenerife ✅ (4 clasificaciones completas, incl. restauración y ocio)

### Castilla-La Mancha (5/5)
- Albacete ✅ · Ciudad Real ✅ · Cuenca ✅ · Guadalajara ✅ · Toledo ✅

### Castilla y León (9/9)
- Ávila ✅ · Burgos ✅ · León ✅ · Palencia ✅ · Salamanca ✅ · Segovia ✅ · Soria ✅ · Valladolid ✅ · Zamora ✅

### Cataluña (4/4)
- Barcelona / Girona / Tarragona ✅ (interprovincial) · Lleida ✅ (convenio provincial propio)

### Comunidad Valenciana (3/3)
- Alicante ✅ (tablas 2026 + condiciones completas) · Castellón ✅ · Valencia ✅

### Extremadura (2/2)
- Badajoz ✅ · Cáceres ✅ (Extremadura publica en el DOE, no en BOP)

### Galicia (4/4)
- A Coruña ✅ · Lugo ✅ · Ourense ✅ · Pontevedra ✅

### Madrid
- Hospedaje ✅ · Hostelería ✅

### País Vasco (3/3)
- Álava ✅ · Gipuzkoa ✅ (×2: restauración + alojamientos) · Bizkaia ✅

### Ceuta ✅ · Melilla ✅

---

## Recuento final
- **55 ficheros JSON** en `origin/main`: 50 provincias + Ceuta + Melilla (Cataluña interprovincial cubre BCN/Girona/Tarragona; Lleida aparte) + Madrid×2 + Gipuzkoa hospedaje + La Rioja hospedaje + ALEH estatal + estatal colectiva (con 49 anexos).
- **Todos con spot-check independiente: 0 discrepancias** en miles de celdas.
- Único fallo de dato cazado y corregido: nocturnidad del estatal de colectiva (estaba sobregeneralizada al 25%, corregida a regla por provincia).

## Dudas UGT recurrentes (anotadas en cada JSON, NO bloquean la app)
- Retribución de vacaciones: base vs promedio con complementos (la mayoría de convenios no lo especifica).
- Años provisionales aún sin tabla definitiva en algunas provincias (marcados `pendiente`).
- Permisos de convenios pre-RD-ley 5/2023: prevalece el ET por más favorable (marcado donde aplica).

## Mantenimiento
Cuando salga la modificación 2026 del ALEH en el BOE, integrarla en `aleh-estatal.json`. Revisar anualmente las provincias con tablas en ultraactividad por si publican revisión. PRs de la comunidad (AGPL) para nuevas publicaciones.
