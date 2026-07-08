# Revisiones pendientes — calendario de mantenimiento del corpus

> Ordena **por urgencia** qué convenios habrá que revisar o actualizar antes, con
> el disparador de la revisión y el boletín oficial que hay que vigilar.
> Estado a fecha **2026-07-08**. 55 convenios en el corpus.
>
> **Regla de oro (D32):** no se inventan fechas ni tablas. Todo lo de aquí sale de
> los ficheros del repo (`convenios/*.json` y `docs/dudas-resueltas/*.md`). Si algo
> no consta en fuente oficial, se dice explícitamente.

---

## 🔴 ACCIONES NIVEL 1 — vigilar YA y de forma periódica

Firmado o acordado pero **aún sin publicación oficial**. Hay que comprobar el
boletín cada pocas semanas hasta que salga; en cuanto se publique, transcribir e
integrar.

### 1. La Rioja hospedaje — convenio 2026-2028 ya FIRMADO, sin BOR
- **Fichero:** `convenios/larioja-hospedaje.json` (campo `convenioPosteriorFirmadoNoPublicado`).
- **Situación:** el convenio 2026-2028 se firmó el **09-03-2026** (patronal FER +
  UGT/CCOO/USO). El JSON sigue con las tablas 2023-2025 en ultraactividad porque el
  nuevo **no está publicado en el BOR** a jul-2026.
- **Cambios ya anunciados a cargar cuando salga:** subidas +5% (2026) / +4,5% (2027)
  / +4,5% (2028); jornada 1.768 h → **1.752 h**; nocturnidad **70 €/mes**; nuevo plus
  festivo **10 €/festivo**; seguro accidentes 30.000 € → **50.000 €**; permiso
  fallecimiento 1er grado 2 → **3 días**; cláusula IPC en 2029.
- **Fuente a vigilar:** **BOR** (Boletín Oficial de La Rioja).
- **Cadencia:** mensual.
- *Fuente del dato:* `docs/dudas-resueltas/grupo-b.md` (⚠️ ACTUALIZAR CONVENIO nº 1).

### 2. ALEH estatal — modificación 2026 del VI ALEH, sin BOE
- **Fichero:** `convenios/aleh-estatal.json` (campo `modificacion2026Pendiente`).
- **Situación:** modificación del VI ALEH **firmada el 13-04-2026** (FeSMC-UGT,
  CCOO Servicios, Hostelería de España, CEHAT), con efectos desde la firma, pero
  **pendiente de publicación en el BOE**. Verificado el 2026-07-08 en boe.es: la
  última publicación sigue siendo el VI ALEH (BOE-A-2023-6344).
- **Cambios anunciados:** audiencia previa al despido disciplinario (alineado con STS
  18-11-2024), protocolo de olas de calor, cláusula LGTBI.
- **Qué tocará al publicarse:** `regimenDisciplinario.procedimiento` del JSON.
- **Fuente a vigilar:** **BOE**.
- **Cadencia:** mensual.
- *Fuente del dato:* `docs/dudas-resueltas/grupo-d.md`.

### 3. Permiso por fallecimiento ampliado a 10 días — ley general, sin BOE
- **Alcance:** **ley general (art. 37.3 ET)** → afecta a **los 55 convenios**, no a uno.
- **Situación:** la ampliación del permiso por fallecimiento a **10 días** está
  **acordada (dic-2025)** pero **aún no publicada en el BOE**. Mientras tanto sigue
  vigente lo anterior (2 días, ampliables a 4 con desplazamiento).
- **Fuente a vigilar:** **BOE**.
- **Cadencia:** mensual.
- *Fuente del dato:* `docs/dudas-resueltas/grupo-c.md` y `docs/dudas-resueltas/00-transversales.md` (A.2).

---

## Resumen por niveles

| Nivel | Qué es | Cuándo revisar | Nº convenios |
|-------|--------|----------------|--------------|
| 🔴 1 | Firmado/acordado sin publicar oficialmente | **YA**, mensual | 2 convenios + 1 ley general |
| 🟠 2 | Tablas 2026 **provisionales** a la espera de definitivas | ~ene-2027 (al cerrar IPC 2026) | 4 |
| 🟡 3 | Convenio **vencido** en ultraactividad | Continuo (vigilar convenio nuevo) | 26 |
| 🟢 4 | Revisión **IPC condicional** por confirmar | Cross-cutting (ver overlay) | 10 (solapan con 2/3/5) |
| ⚪ 5 | Multi-año **vigentes** (informativo) | En su año de vencimiento | 23 |

> Cada convenio tiene un nivel **primario** (2 + 4 + 26 + 23 = 55). El Nivel 4 es un
> **overlay** de cláusulas IPC que hay que vigilar aparte; sus convenios ya aparecen
> en su nivel primario.

---

## 🟠 NIVEL 2 — Tablas 2026 provisionales a la espera de definitivas

Tienen tabla 2026 publicada pero marcada **provisional / pago a cuenta**; la
definitiva depende de cerrar el IPC 2026. **Revisar ~enero-2027.**

| Fichero | Vigencia | Disparador | Boletín a vigilar | Cadencia |
|---------|----------|------------|-------------------|----------|
| `badajoz-hosteleria` | 2024→2026-12-31 | Tabla 2026 provisional; definitiva depende del IPC 2026 | **DOE** | Revisar en 2027 |
| `guadalajara-hosteleria` | 2025→2026-12-31 | Tabla 2026 provisional (art. 27): si IPC 2026 > 2% se actualiza y pasa a definitiva | **BOP Guadalajara** | Revisar en 2027 |
| `malaga-hosteleria` | 2025→2028-12-31 | Tabla 2026 provisional (BOP nº70/2026 trae solo tablas) | **BOP Málaga** | Revisar en 2027 |
| `salamanca-hosteleria` | 2020→2025 (ultraactividad) | Tabla 2026 provisional, pago a cuenta +2,9%; a la espera de acuerdo definitivo 2026 y/o convenio nuevo | **BOP Salamanca** | Revisar en 2027 |

**Correcciones sobre la lista tentativa del encargo (verificadas en JSON):**
- **Albacete** → **no** es tabla 2026 provisional: su provisional es la de **2025**
  (revisable IPC dic-2025, art. 60) y el convenio es anual vencido. Va a **Nivel 3**
  (con overlay Nivel 4).
- **Castellón** → **no** es provisional: el acta de 23-01-2026 confirmó las tablas
  2026 (solo había errata de rótulo en el BOP 32). Va a **Nivel 5** (con overlay
  Nivel 4 por las revisiones IPC 2027-2028).

---

## 🟡 NIVEL 3 — Convenio vencido en ultraactividad (vigilar convenio nuevo)

`vigencia.hasta` ya pasó y siguen rigiendo por ultraactividad (art. 86.4 ET). El
disparador es la **publicación de un convenio nuevo** (o de tablas nuevas) en el
boletín provincial. Ordenados de más antiguo (más urgente) a más reciente.

| Fichero | Venció | Nota | Boletín a vigilar |
|---------|--------|------|-------------------|
| `teruel-hosteleria` | 2023-12-31 | **Negociación suspendida** — el más rezagado | BOP Teruel |
| `acoruna-hosteleria` | 2024-12-31 | Tablas 2024 son la última publicación | BOP A Coruña |
| `ciudadreal-hosteleria` | 2024-12-31 | Ultraactividad | BOP Ciudad Real |
| `cuenca-hosteleria` | 2024-12-31 | Tablas 2025 provisionales (+1,9%, art. 53) vigentes | BOP Cuenca |
| `huesca-hosteleria` | 2024-12-31 | Ultraactividad; cláusula IPC (ver Nivel 4) | BOP Huesca |
| `lugo-hosteleria` | 2024-12-31 | Vigilar tabla nueva | BOP Lugo |
| `lleida-hosteleria` | 2026-06-30 | Recién vencido | BOP Lleida / DOGC |
| `almeria-hosteleria` | 2025-12-31 | Convenio anual | BOP Almería |
| `albacete-hosteleria` | 2025-12-31 | Anual; provisional 2025 + IPC art. 60 (ver Nivel 4) | BOP Albacete |
| `avila-hosteleria` | 2025-12-31 | Ultraactividad | BOP Ávila |
| `caceres-hosteleria` | 2025-12-31 | Ultraactividad | DOE |
| `cantabria-hosteleria` | 2025-12-31 | Tablas 2026 / convenio 2025-2028 **sin publicar en BOC** | BOC |
| `granada-hosteleria` | 2025-12-31 | Ultraactividad | BOP Granada |
| `jaen-hosteleria` | 2025-12-31 | Ultraactividad | BOP Jaén |
| `larioja-hosteleria` | 2025-12-31 | Restauración (distinto del hospedaje del Nivel 1); IPC (ver Nivel 4) | BOR |
| `laspalmas-hosteleria` | 2025-12-31 | Vigilar tabla nueva | BOP Las Palmas |
| `leon-hosteleria` | 2025-12-31 | Incremento/revisión salarial pendiente (ver Nivel 4) | BOP León |
| `madrid-hosteleria` | 2025-12-31 | Ultraactividad | BOCM |
| `murcia-hosteleria` | 2025-12-31 | Sin BORM de hostelería nuevo (ojo: "convenio del Tomate Fresco" es falso positivo) | BORM |
| `navarra-hosteleria` | 2025-12-31 | Revisión IPC por confirmar (ver Nivel 4) | BON |
| `pontevedra-hosteleria` | 2025-12-31 | Vigilar tabla revisada | BOPPO |
| `soria-hosteleria` | 2025-12-31 | Ultraactividad | BOP Soria |
| `toledo-hosteleria` | 2025-12-31 | Ultraactividad | BOP Toledo |
| `valencia-hosteleria` | 2025-12-31 | Ultraactividad | BOP Valencia |
| `zaragoza-hosteleria` | 2025-12-31 | Revisión salarial pendiente (ver Nivel 4) | BOPZ |
| `estatal-restauracion-colectiva` | 2025-12-31 | Convenio marco **anual** + 49 provincias; se renueva cada año | BOE |

**Corrección sobre la lista tentativa:** **Ourense** **no** está vencido —
`vigencia` 2025→2027-12-31. Va a **Nivel 5** (con overlay Nivel 4 por su cláusula IPC).

**Cadencia sugerida Nivel 3:** trimestral para los vencidos ≤ 2024; semestral para
los que vencieron a fin de 2025 (la negociación del sucesor aún es reciente).

---

## 🟢 NIVEL 4 — Overlay de revisiones IPC condicionales por confirmar

Cláusula de revisión al IPC **no confirmada / sin resolución localizada**. No forman
un bucket aparte: cada uno ya está en su nivel primario, pero conviene vigilarlos por
la cláusula además de por el convenio.

| Fichero | Nivel primario | Cláusula IPC a confirmar | Boletín |
|---------|----------------|--------------------------|---------|
| `albacete-hosteleria` | 3 | Revisión definitiva art. 60 (+0,5% IPC dic-2025); sin resolución 2026 localizada | BOP Albacete |
| `huesca-hosteleria` | 3 | Cláusula de actualización IPC | BOP Huesca |
| `larioja-hosteleria` | 3 | Revisión salarial IPC (restauración) | BOR |
| `navarra-hosteleria` | 3 | Revisión IPC (¿se activó? publicación de 2023 es falso positivo) | BON |
| `leon-hosteleria` | 3 | Incremento y revisión salarial | BOP León |
| `zaragoza-hosteleria` | 3 | Revisión salarial | BOPZ |
| `guadalajara-hosteleria` | 2 | Art. 27: IPC 2026 > 2% → tabla definitiva | BOP Guadalajara |
| `ourense-hosteleria` | 5 | `clausulaRevisionIPC` | BOP Ourense |
| `castellon-hosteleria` | 5 | 2027 y 2028: IPC año anterior +0,5% (gr. III-IV) / +1% (niv. I-II); tabla 2026 pendiente de IPC | BOP Castellón |
| `palencia-hosteleria` | 5 | Revisión salarial | BOP Palencia |

**Cadencia sugerida Nivel 4:** revisar en **enero-febrero de cada año** (cuando el INE
publica el IPC de diciembre), que es cuando estas cláusulas se activan.

---

## ⚪ NIVEL 5 — Multi-año vigentes (informativo, sin acción hasta su año)

Ya cubren hasta 2026/2027/2028/2029. Solo revisar al acercarse su vencimiento.

**Vencen a fin de 2026 — empezar a vigilar el boletín en otoño-2026:**

| Fichero | Vigencia hasta | Boletín |
|---------|----------------|---------|
| `alicante-hosteleria` | 2026-12-31 | BOP Alicante |
| `burgos-hosteleria` | 2026-12-31 | BOP Burgos |
| `cordoba-hosteleria` | 2026-12-31 | BOP Córdoba |
| `gipuzkoa-hosteleria` | 2026-12-31 | BOG |
| `palencia-hosteleria` | 2026-12-31 | BOP Palencia |
| `segovia-hosteleria` | 2026-12-31 | BOP Segovia |
| `valladolid-hosteleria` | 2026-12-31 | BOP Valladolid |
| `zamora-hosteleria` | 2026-12-31 | BOP Zamora |

**Vencen en 2027 o después — sin acción cercana:**

| Fichero | Vigencia hasta | Boletín |
|---------|----------------|---------|
| `asturias-hosteleria` | 2027-12-31 | BOPA |
| `huelva-hosteleria` | 2027-12-31 | BOP Huelva |
| `ourense-hosteleria` | 2027-12-31 | BOP Ourense |
| `vizcaya-hosteleria` | 2027-12-31 | BOB |
| `alava-hosteleria` | 2028-12-31 | BOTHA |
| `baleares-hosteleria` | 2028-03-31 | BOIB |
| `cadiz-hosteleria` | 2028-12-31 | BOP Cádiz |
| `castellon-hosteleria` | 2028-12-31 | BOP Castellón |
| `cataluna-hosteleria` | 2028-12-31 | DOGC |
| `gipuzkoa-hospedaje` | 2028-12-31 | BOG |
| `es-madrid-hospedaje` | 2028-12-31 | BOCM |
| `melilla-hosteleria` | 2028-12-31 | BOME |
| `sevilla-hosteleria` | 2028-12-31 | BOP Sevilla |
| `tenerife-hosteleria` | 2028-06-30 | BOP Santa Cruz de Tenerife |
| `ceuta-hosteleria` | 2029-12-31 | BOCCE |

---

## Metodología

- **Fuentes:** los 55 `convenios/*.json` (campos `vigencia`, `estado`,
  `convenioPosteriorFirmadoNoPublicado`, `modificacion2026Pendiente`, notas de
  provisional/ultraactividad/IPC) y los 5 docs `docs/dudas-resueltas/` (marcas
  ⚠️ ACTUALIZAR CONVENIO y watch items).
- **No se inventa nada:** las fechas y estados salen del repo. Donde una revisión
  depende de un IPC o de un acuerdo aún no publicado, se marca como "por confirmar"
  y se indica el boletín donde aparecerá.
- **Boletines por tipo:** BOE (estatal), DOE/DOGC/BOC/BON/BOR/BOIB/BOTHA/BOPA
  (autonómicos), BOP/BOG/BOB/BOPZ/BOPPO/BOME/BOCCE (provinciales/ciudades autónomas).
