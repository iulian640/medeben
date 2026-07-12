export const meta = {
  name: 'auditoria-lanzamiento',
  description: 'Auditoría integral de MeDeben antes del lanzamiento: seguridad, dinero, RGPD, bugs básicos, deploy',
  whenToUse: 'Antes de sacar MeDeben a producción. Fase 1 del HANDOFF-lanzamiento.md',
  phases: [
    { title: 'Auditar', detail: '12 auditores en paralelo (5 Fable / 4 Opus / 3 Sonnet)' },
    { title: 'Verificar', detail: '3 escépticos independientes por hallazgo; sobrevive lo que 2 de 3 confirman' },
    { title: 'Sintetizar', detail: 'Triaje final: qué bloquea el lanzamiento', model: 'fable' },
  ],
}

const REPO = 'C:\\Users\\iulia\\Documents\\tedeben'

const CONTEXTO = `
Proyecto: MeDeben (repo en ${REPO}). App para trabajadores de hostelería que registra
su jornada y calcula, de forma orientativa, las horas extra que se les deben según el
convenio colectivo aplicable. Backend Java 21 / Spring Boot (${REPO}\\backend), frontend
Vue 3 + Capacitor (${REPO}\\frontend), Postgres + Flyway, despliegue en ${REPO}\\deploy.

SE LANZA A PRODUCCIÓN MAÑANA con usuarios reales. Trata datos personales, incluido un
POSIBLE DATO DE SALUD (el campo 'motivo' de una ausencia, categoría especial art. 9 RGPD),
y produce CIFRAS DE DINERO que la gente usará para reclamar a su empresa. Un cálculo mal
hecho o una fuga de datos entre usuarios son fallos graves de verdad.

La suite de tests está VERDE hoy. La revisión legal documental ya existe en
docs/cumplimiento-lanzamiento.md — NO la repitas; tú auditas el CÓDIGO.

Explora el código de verdad (lee ficheros, no supongas). Ancla cada hallazgo a un
fichero y una línea reales, con la cita del código.
`

const REGLAS = `
REGLAS DE INFORME (críticas):
- Reporta SOLO defectos reales y demostrables, con un escenario de fallo concreto:
  entradas/estado -> comportamiento incorrecto. Nada de "sería recomendable" genérico.
- Prefiero 3 hallazgos ciertos a 15 especulativos. Si dudas, NO lo reportes.
- Nada de nits de estilo. Nada de "falta un comentario".
- 'bloquea_lanzamiento': true SOLO si soltar esto mañana con usuarios reales causaría
  fuga de datos, cálculo de dinero erróneo, pérdida de datos, o la app rota para el usuario.
`

const HALLAZGOS_SCHEMA = {
  type: 'object',
  required: ['hallazgos'],
  properties: {
    hallazgos: {
      type: 'array',
      items: {
        type: 'object',
        required: ['titulo', 'fichero', 'linea', 'severidad', 'escenario_fallo', 'evidencia', 'arreglo', 'bloquea_lanzamiento'],
        properties: {
          titulo: { type: 'string', description: 'Una frase: cuál es el defecto' },
          fichero: { type: 'string', description: 'Ruta relativa al repo' },
          linea: { type: 'integer' },
          severidad: { type: 'string', enum: ['CRITICO', 'ALTO', 'MEDIO', 'BAJO'] },
          escenario_fallo: { type: 'string', description: 'Entradas/estado concretos -> resultado incorrecto' },
          evidencia: { type: 'string', description: 'Cita literal del código que lo demuestra' },
          arreglo: { type: 'string', description: 'Arreglo propuesto, concreto' },
          bloquea_lanzamiento: { type: 'boolean' },
        },
      },
    },
  },
}

const VEREDICTO_SCHEMA = {
  type: 'object',
  required: ['es_real', 'razon'],
  properties: {
    es_real: { type: 'boolean' },
    razon: { type: 'string' },
    severidad_corregida: { type: 'string', enum: ['CRITICO', 'ALTO', 'MEDIO', 'BAJO', 'NO_ES_UN_FALLO'] },
    bloquea_lanzamiento: { type: 'boolean' },
  },
}

// 5 Fable (lo muy importante) + 4 Opus (importante) + 3 Sonnet (básico) = 12 auditores.
const AUDITORES = [
  {
    key: 'authz-idor', model: 'fable', effort: 'xhigh',
    prompt: `Auditoría de AUTORIZACIÓN. Recorre TODOS los controllers de
${REPO}\\backend\\src\\main\\java\\es\\medeben\\controller\\ y, para cada endpoint, comprueba si
los datos que devuelve o modifica están REALMENTE acotados al usuario autenticado.

Busca fugas entre usuarios (IDOR): un id de recurso que venga del path/body y se use para
buscar en el repositorio SIN filtrar además por el usuario dueño. Ej.: ¿puede el usuario A
leer/borrar/modificar el apunte, el cuadrante, el perfil o el informe del usuario B?
Verifica también los métodos de los repositorios (¿findById a secas o findByIdAndUsuario?)
y qué endpoints deja pasar SecurityConfig sin autenticar (permitAll).
Comprueba el PDF: ¿puede alguien descargar el informe de otro?`,
  },
  {
    key: 'auth-sesiones', model: 'fable', effort: 'xhigh',
    prompt: `Auditoría de AUTENTICACIÓN Y SESIONES. Lee AuthService, SecurityConfig, JwtConfig,
Sesion, SesionRepository, SesionesPurga, el filtro de JWT, RateLimitFilter y la config CORS.

Busca: firma/validación del JWT floja o con algoritmo confundible; caducidad no comprobada;
rotación del refresh token mal hecha (reutilización no detectada, carrera entre dos refresh
simultáneos); logout que no revoca de verdad; secretos por defecto o débiles en
application*.yml que puedan llegar a producción; timing attack en el login; enumeración de
usuarios (¿responde distinto si el email existe?); rate limiting evitable (¿se puede
falsificar la IP con X-Forwarded-For?); CORS demasiado abierto (¿origen reflejado?,
¿credentials con *?).`,
  },
  {
    key: 'dinero-valor-hora', model: 'fable', effort: 'xhigh',
    prompt: `Auditoría del MOTOR DE DINERO (parte 1: valor de la hora y horas extra).
Lee CalculoConvenioService, ValorHoraCalculado, HorasExtraCalculadas, TablaSalarialService,
SalarioBaseResuelto, DesgloseValorHora, y sus tests.

Este cálculo va a una reclamación laboral real: si da de menos, el trabajador pierde dinero;
si da de más, hace el ridículo ante un juez. Busca errores REALES de cálculo: redondeo mal
hecho (¿double en vez de BigDecimal? ¿RoundingMode correcto?), divisiones por cero, el
divisor anual/jornada equivocado, pagas extra prorrateadas mal (o dos veces), recargos que
no se aplican o se aplican dos veces, mezcla de valores de años distintos (vigencia del
convenio), y qué pasa en los bordes: 0 horas, jornada partida, cambio de año, mes incompleto.`,
  },
  {
    key: 'dinero-topes-informe', model: 'fable', effort: 'xhigh',
    prompt: `Auditoría del MOTOR DE DINERO (parte 2: agregación, topes e informe).
Lee ResumenMensualService, InformeMensualService, InformeAnualService, TopeHorasExtra,
TopeAnualResumen, SmiService, ImporteEstimadoMensual, PdfInforme, y sus tests.

Busca: sumas de horas mal agregadas entre días/semanas/meses; el tope legal de 80 horas
extra anuales aplicado mal (o al periodo equivocado); comparación con el SMI incorrecta;
descuadres entre lo que muestra el resumen y lo que sale impreso en el PDF (¿misma fuente
de verdad?); zonas horarias / cambio de hora que desplacen un fichaje de día; meses con
datos incompletos que se den por completos; el estado NO_CUADRA de un día perdiéndose en
la agregación.`,
  },
  {
    key: 'rgpd-codigo', model: 'fable', effort: 'xhigh',
    prompt: `Auditoría RGPD A NIVEL DE CÓDIGO (no documental: el análisis legal ya existe).

1. El campo 'motivo' de una ausencia puede contener un DATO DE SALUD (art. 9). Rastréalo
   por TODO el código y comprueba que no se filtra: ¿aparece en algún log, en un mensaje de
   excepción, en un toString(), en una respuesta de error, en la traza de un stacktrace?
2. Borrado de cuenta (art. 17): ¿borra DE VERDAD todo? Cruza las migraciones Flyway V1-V7
   contra CuentaService: ¿alguna tabla o fichero (el PDF cacheado, las sesiones) se queda
   huérfano tras el borrado?
3. ¿Se loguean PII (email, contraseña, token) en algún sitio? Revisa niveles de log en
   application-prod.yml.
4. ¿Filtra el manejo de errores en producción algún dato interno (stacktrace, SQL, ruta)?
   Mira GlobalExceptionHandler y ProblemDetailEntryPoint.`,
  },
  {
    key: 'bugs-basicos', model: 'opus', effort: 'high',
    prompt: `Auditoría de FALLOS BÁSICOS DE FUNCIONAMIENTO. Foco: FichajeService, HorarioService,
PerfilService, PerfilOcupacionService, Cuadrante, Apunte, EstadoDia.

Iulian ha dicho literalmente: "no puede tener errores básicos de funcionamiento". Busca lo
que le explotaría a un usuario normal el primer día: NullPointerException en un camino
plausible, Optional.get() sin comprobar, errores tragados en silencio (catch vacío o que
devuelve null/lista vacía disimulando el fallo), estados imposibles que la máquina de
estados deja pasar (¿fichar salida sin entrada? ¿dos entradas seguidas? ¿fichar en un día
sellado?), validación que falta en el borde de la API, y off-by-one en fechas.`,
  },
  {
    key: 'datos-integridad', model: 'opus', effort: 'high',
    prompt: `Auditoría de INTEGRIDAD DE DATOS. Revisa las migraciones Flyway V1-V7 en
${REPO}\\backend\\src\\main\\resources\\db\\migration\\ contra las entidades JPA
(Usuario, Perfil, Sesion, Apunte, Cuadrante).

Busca: entidades y esquema desalineados (columna nullable en BD pero @NotNull en Java, o al
revés); falta de constraints únicas donde hace falta (¿dos usuarios con el mismo email?);
ON DELETE mal puesto; @Transactional ausente donde hay varias escrituras que deben ir
juntas; lecturas/escrituras con carrera (dos peticiones a la vez sobre el mismo día);
N+1 queries en los listados; índices que faltan en las columnas por las que se filtra
siempre. Comprueba también que las migraciones V6/V7 aplican limpias sobre una BD que ya
tenía datos de V1-V5.`,
  },
  {
    key: 'frontend-vue', model: 'opus', effort: 'high',
    prompt: `Auditoría del FRONTEND (${REPO}\\frontend, Vue 3 + Pinia + Capacitor + PWA).

Busca: manejo del token (¿el access token se escapa a localStorage? el refresh, ¿se maneja
bien?), XSS (v-html, innerHTML, contenido del usuario sin escapar), fallos de reactividad
que dejen la UI mostrando datos viejos o de otro usuario tras cambiar de sesión, estados de
carga/error no manejados (una llamada al API que falla y deja la pantalla en blanco o con
un spinner eterno), race conditions al refrescar el token con varias peticiones a la vez,
y la sesión entre pestañas (el issue #229 ya lo tocó: comprueba que no se rompió).
Comprueba también que el service worker de la PWA no cachee respuestas del API con datos
de un usuario y se las sirva a otro tras cerrar sesión.`,
  },
  {
    key: 'deploy-prod', model: 'opus', effort: 'high',
    prompt: `Auditoría del DESPLIEGUE A PRODUCCIÓN. Lee ${REPO}\\deploy\\ (Caddyfile, nginx.conf),
${REPO}\\docker-compose.yml, ${REPO}\\backend\\Dockerfile,
${REPO}\\backend\\src\\main\\resources\\application-prod.yml y application.yml, y docs\\despliegue.md.

Busca lo que rompería o expondría el sitio al desplegarlo mañana: secretos con valor por
defecto que funcionen en producción (JWT, contraseña de la BD) — el arranque DEBE fallar si
faltan, no tirar de un default; puertos expuestos que no deberían (¿Postgres publicado al
exterior? ¿el backend fuera de 127.0.0.1?); cabeceras de seguridad mal puestas (CSP, HSTS);
la IP real perdida tras el proxy (rompe el rate limiting — ya pasó una vez); ddl-auto en algo
distinto de 'validate'; el actuator/health filtrando información; el contenedor corriendo
como root; los backups (¿el cron existe de verdad? ¿se ha probado restaurar?).`,
  },
  {
    key: 'deps-cve', model: 'sonnet', effort: 'medium',
    prompt: `Revisa las DEPENDENCIAS. Lee ${REPO}\\backend\\pom.xml y ${REPO}\\frontend\\package.json.
Lista las que tengan CVE conocido o estén notablemente desactualizadas frente a la última
versión estable de su rama. Reporta SOLO las que tengan impacto real en esta app (una
vulnerabilidad en una dependencia que solo se usa en tests no bloquea nada). Si puedes,
ejecuta 'npm audit' en el frontend y mira si el proyecto tiene alertas de Dependabot.
Di explícitamente cuáles NO se pueden actualizar sin romper algo.`,
  },
  {
    key: 'tests-huecos', model: 'sonnet', effort: 'medium',
    prompt: `Revisa los HUECOS DE TEST en lo crítico. La suite está verde, así que no busques
tests rotos: busca comportamiento importante que NADIE prueba. Céntrate en: el motor de
cálculo del dinero (casos límite), la autorización (¿hay algún test que compruebe que el
usuario A no puede leer los datos del B?), el borrado de cuenta, y la rotación del refresh
token. Reporta cada hueco como un test concreto que falta, diciendo qué fallo real dejaría
pasar. Ignora la cobertura por porcentaje; me importa el riesgo.`,
  },
  {
    key: 'ux-errores', model: 'sonnet', effort: 'medium',
    prompt: `Revisa la EXPERIENCIA DE ERROR de cara al usuario. La app la usan camareros y
cocineros, no informáticos. Busca: mensajes de error que le lleguen al usuario en inglés,
en jerga técnica, o con un código HTTP pelado; errores del backend que no digan qué hacer
para arreglarlo; formularios que no expliquen por qué han fallado; pantallas vacías sin
explicación cuando aún no hay datos; y textos en castellano con voseo o argentinismos
("vos", "tenés") que se hayan colado. Céntrate en los flujos principales: registro, login,
crear perfil, fichar, ver lo que te deben.`,
  },
]

phase('Auditar')
log(`Auditando MeDeben: ${AUDITORES.length} auditores (5 Fable / 4 Opus / 3 Sonnet)`)

// Pipeline: cada dimensión pasa a verificación en cuanto termina, sin esperar a las demás.
const porDimension = await pipeline(
  AUDITORES,
  (a) =>
    agent(`${CONTEXTO}\n\n${a.prompt}\n\n${REGLAS}`, {
      label: `auditar:${a.key}`,
      phase: 'Auditar',
      model: a.model,
      effort: a.effort,
      schema: HALLAZGOS_SCHEMA,
    }),

  // Verificación adversarial: 3 lentes distintas por hallazgo. Sobrevive con 2 de 3.
  (res, auditor) => {
    if (!res || !res.hallazgos?.length) return []
    log(`${auditor.key}: ${res.hallazgos.length} hallazgo(s) -> a verificar`)

    const LENTES = [
      {
        n: 'refutar',
        // Lo crítico y lo alto lo refuta Opus; lo menor, Sonnet.
        m: (h) => (h.severidad === 'CRITICO' || h.severidad === 'ALTO' ? 'opus' : 'sonnet'),
        q: (h) => `Eres un escéptico. INTENTA REFUTAR este hallazgo leyendo el código real.
¿Existe una guarda, una anotación, una validación o un test que el auditor no vio y que hace
que el fallo NO ocurra? Por defecto, si no consigues demostrar que el fallo es real, márcalo
como es_real=false.`,
      },
      {
        n: 'reproducir',
        m: () => 'opus',
        q: (h) => `Intenta CONSTRUIR EL CASO CONCRETO que dispara este fallo: peticiones exactas,
datos exactos, estado exacto de la BD. Si no consigues escribir una reproducción creíble
siguiendo el código de verdad, es_real=false.`,
      },
      {
        n: 'impacto',
        m: () => 'sonnet',
        q: (h) => `Asumiendo que el fallo es real, juzga SOLO su impacto para un lanzamiento mañana
con usuarios reales. ¿Bloquea de verdad (fuga de datos entre usuarios, dinero mal calculado,
pérdida de datos, app rota) o se puede arreglar la semana que viene? Corrige la severidad si
el auditor la infló.`,
      },
    ]

    return parallel(
      res.hallazgos.map((h) => () =>
        parallel(
          LENTES.map((l) => () =>
            agent(
              `${CONTEXTO}\n\nHALLAZGO A EXAMEN (lo reportó el auditor '${auditor.key}'):\n` +
                `- Título: ${h.titulo}\n- Fichero: ${h.fichero}:${h.linea}\n` +
                `- Severidad que le puso: ${h.severidad}\n- Escenario de fallo: ${h.escenario_fallo}\n` +
                `- Evidencia que citó: ${h.evidencia}\n\n${l.q(h)}`,
              {
                label: `verificar:${l.n}:${h.fichero.split(/[\\/]/).pop()}`,
                phase: 'Verificar',
                model: l.m(h),
                effort: 'high',
                schema: VEREDICTO_SCHEMA,
              },
            ),
          ),
        ).then((votos) => {
          const v = votos.filter(Boolean)
          const aFavor = v.filter((x) => x.es_real).length
          const sobrevive = aFavor >= 2
          const bloquea = v.filter((x) => x.bloquea_lanzamiento).length >= 2
          return { ...h, dimension: auditor.key, sobrevive, votos_a_favor: aFavor, votos_totales: v.length, bloquea_confirmado: bloquea, veredictos: v }
        }),
      ),
    )
  },
)

const todos = porDimension.flat().filter(Boolean)
const confirmados = todos.filter((h) => h.sobrevive)
const descartados = todos.filter((h) => !h.sobrevive)
const bloqueantes = confirmados.filter((h) => h.bloquea_confirmado || h.severidad === 'CRITICO')

log(`Verificación: ${confirmados.length} confirmados de ${todos.length} (descartados ${descartados.length}). Bloqueantes: ${bloqueantes.length}`)

if (!confirmados.length) {
  return { bloqueantes: [], confirmados: [], descartados: descartados.length, resumen: 'Ningún hallazgo sobrevivió a la verificación adversarial.' }
}

phase('Sintetizar')
const informe = await agent(
  `${CONTEXTO}

Eres el responsable de decidir si MeDeben SE LANZA MAÑANA. Doce auditores han peinado el
código y cada hallazgo ha pasado por tres escépticos independientes; abajo tienes solo los
que sobrevivieron (2 de 3 votos).

HALLAZGOS CONFIRMADOS (JSON):
${JSON.stringify(confirmados.map(({ veredictos, ...h }) => ({ ...h, veredictos: veredictos.map((v) => ({ es_real: v.es_real, razon: v.razon })) })), null, 2)}

Escribe el informe de decisión, EN CASTELLANO DE ESPAÑA (tuteo, sin voseo ni argentinismos),
directo y sin relleno:

1. **Veredicto**: ¿se puede lanzar mañana? Sí / Sí, arreglando X primero / No.
2. **Bloqueantes**, ordenados por gravedad. Por cada uno: qué es, dónde (fichero:línea), qué
   le pasa al usuario si se lanza así, y el arreglo concreto. Estima cuánto cuesta arreglarlo.
3. **Importante pero no bloqueante** (se arregla la semana que viene).
4. **Ruido descartado**: menciona en una línea qué tipo de cosas se cayeron en la verificación.
5. **Orden de ataque** para hoy: la lista de arreglos en el orden en que hay que hacerlos.

Agrupa los hallazgos que en realidad son la misma causa raíz. Si dos auditores encontraron
lo mismo por caminos distintos, dilo (es señal de que es real).`,
  { label: 'sintesis:decision-lanzamiento', phase: 'Sintetizar', model: 'fable', effort: 'xhigh' },
)

return {
  veredicto: informe,
  bloqueantes: bloqueantes.map(({ veredictos, ...h }) => h),
  confirmados: confirmados.map(({ veredictos, ...h }) => h),
  descartados: descartados.length,
}
