import { ApiError } from '../services/api'

const FORMATO_IMPORTE = new Intl.NumberFormat('es-ES', {
  minimumFractionDigits: 2,
  maximumFractionDigits: 2,
  // El CLDR español no agrupa los miles hasta 5 cifras; un salario de
  // "1.425,50" se lee mejor con el punto de miles siempre. El cast es porque
  // los tipos de la lib de TS aún no conocen el valor 'always' (ES2023).
  useGrouping: 'always' as unknown as boolean,
})

/** 1425.5 → "1.425,50" (estilo español). */
export function formatearImporte(importe: number): string {
  return FORMATO_IMPORTE.format(importe)
}

const FORMATO_HORAS = new Intl.NumberFormat('es-ES', {
  maximumFractionDigits: 2,
})

/** Horas fraccionarias en estilo español: 3.5 → "3,5"; 72 → "72". */
export function formatearHoras(horas: number): string {
  return FORMATO_HORAS.format(horas)
}

/**
 * Unidad de la API en lenguaje llano. Las desconocidas se muestran tal cual:
 * nunca inventamos periodicidades (Cuenca publica en EUR/año, no EUR/mes).
 */
const UNIDADES: Record<string, string> = {
  'EUR/mes': '€ al mes',
  'EUR/año': '€ al año',
  'EUR/hora': '€ la hora',
}

export function etiquetaUnidad(unidad: string): string {
  return UNIDADES[unidad] ?? unidad
}

/**
 * Mensaje legible de un error: el backend habla RFC 7807 ({status, detail}),
 * así que el detail va primero. Siempre texto plano, nunca HTML.
 */
export function mensajeDeError(error: unknown): string {
  if (error instanceof ApiError && error.body && typeof error.body === 'object') {
    const detail = (error.body as Record<string, unknown>).detail
    if (typeof detail === 'string' && detail.length > 0) {
      return detail
    }
  }
  if (error instanceof Error) {
    return error.message
  }
  return 'Algo ha fallado. Inténtalo de nuevo.'
}

/** Nombres en cristiano de las dimensiones que devuelve la API. */
const ETIQUETAS_DIMENSION: Record<string, string> = {
  claseEmpresa: 'Clase de empresa',
  nivel: 'Nivel',
  nivelRetributivo: 'Nivel',
  nivelSalarial: 'Nivel',
  grupo: 'Grupo',
  grupoProfesional: 'Grupo profesional',
  grupoActividad: 'Grupo de actividad',
  categoria: 'Categoría',
  categoriaEstablecimiento: 'Categoría del local',
  establecimiento: 'Tipo de local',
  grupoEstablecimiento: 'Tipo de local',
  tipoEstablecimiento: 'Tipo de local',
  clasificacion: 'Clasificación del local',
  clasificacionEstablecimiento: 'Clasificación del local',
  seccion: 'Tipo de negocio',
  zona: 'Zona',
  tabla: 'Tabla salarial',
  tramo: 'Tramo',
  area: 'Área',
  areaFuncional: 'Área',
  departamento: 'Departamento',
  provincia: 'Provincia',
}

export function etiquetaDimension(dimension: string): string {
  const conocida = ETIQUETAS_DIMENSION[dimension]
  if (conocida) {
    return conocida
  }
  // camelCase → "Camel case": mejor que enseñar la clave técnica tal cual.
  const conEspacios = dimension.replace(/([a-z])([A-Z])/g, '$1 $2').toLowerCase()
  return conEspacios.charAt(0).toUpperCase() + conEspacios.slice(1)
}

/** Sustantivo con el que se lee un valor limpio (romano/número/letra) de cada dimensión. */
const SUSTANTIVO_VALOR: Record<string, string> = {
  grupo: 'Grupo',
  grupoProfesional: 'Grupo',
  grupoActividad: 'Grupo',
  grupoEstablecimiento: 'Grupo',
  nivel: 'Nivel',
  nivelRetributivo: 'Nivel',
  nivelSalarial: 'Nivel',
  clasificacion: 'Clasificación',
  clasificacionEstablecimiento: 'Clasificación',
  claseEmpresa: 'Clase',
  categoria: 'Categoría',
  categoriaEstablecimiento: 'Categoría',
  seccion: 'Sección',
  tramo: 'Tramo',
  area: 'Área',
  areaFuncional: 'Área',
}

/**
 * Códigos crudos que el convenio guarda de forma poco legible, en cristiano.
 * Solo el TEXTO que se muestra cambia; el valor que se envía a la API es el crudo.
 */
const VALORES_CURADOS: Record<string, string> = {
  AF1_recepcion_conserjeria_rrpp_admon_gestion: 'Recepción, conserjería y administración',
  AF2_cocina_economato: 'Cocina y economato',
  AF3_restaurante_sala_bar_colectividades_catering: 'Restaurante, sala, bar y catering',
  AF4_pisos_limpieza: 'Pisos y limpieza',
  AF5_mantenimiento_servicios_auxiliares: 'Mantenimiento y servicios auxiliares',
  AF6_servicios_complementarios: 'Servicios complementarios',
  // Tipos de establecimiento de los convenios con mapeo condicional (Asturias,
  // Jaén, Cataluña, Málaga…). Los códigos crudos del convenio, en cristiano.
  cafes_bares_cervecerias_especial: 'Cafés, bares y cervecerías (categoría especial)',
  cafes_bares_cervecerias_otros: 'Cafés, bares y cervecerías (resto)',
  cafeterias: 'Cafeterías',
  casinosBingos: 'Casinos y bingos',
  catering: 'Catering',
  colectividades: 'Colectividades (comedores, hospitales, catering…)',
  hoteles: 'Hoteles',
  hoteles_1_estrella: 'Hoteles de 1 estrella',
  hoteles_2_estrellas: 'Hoteles de 2 estrellas',
  hoteles_3_estrellas: 'Hoteles de 3 estrellas',
  hoteles_5o4_estrellas: 'Hoteles de 4 o 5 estrellas',
  otrosEstablecimientos: 'Otros establecimientos',
  pensiones1y2estrellas: 'Pensiones de 1 y 2 estrellas',
  restaurantes: 'Restaurantes',
  restaurantes_1_tenedor: 'Restaurantes de 1 tenedor',
  restaurantes_2_tenedores: 'Restaurantes de 2 tenedores',
  restaurantes_3_tenedores: 'Restaurantes de 3 tenedores',
  restaurantes_4_tenedores: 'Restaurantes de 4 tenedores',
  restaurantes_5_tenedores: 'Restaurantes de 5 tenedores',
  salasFiestaDiscotecas: 'Salas de fiesta y discotecas',
  salas_fiesta_discotecas: 'Salas de fiesta y discotecas',
  // Grupos profesionales del ALEH (Melilla y otros que lo remiten), por nivel de
  // responsabilidad: el trabajador se reconoce en uno de los tres.
  grupoPrimero: '1º · Mando o jefe/a',
  grupoSegundo: '2º · Técnico/a o especialista',
  grupoTercero: '3º · Asistente',
  // Provincias que la capa normalizada guarda en ASCII (restauración colectiva).
  'A Coruna': 'A Coruña',
  Almeria: 'Almería',
  Avila: 'Ávila',
  Caceres: 'Cáceres',
  Cadiz: 'Cádiz',
  Cordoba: 'Córdoba',
  Guipuzcoa: 'Guipúzcoa',
  Jaen: 'Jaén',
  Leon: 'León',
  Malaga: 'Málaga',
}

const RE_VALOR_LIMPIO = /^([IVXLCDM]+|\d{1,2}[ºªAB]?|[A-H])$/i
const RE_ES_CODIGO = /_|[a-zñáéíóú][A-ZÑ]|^(AF\d|NS_|e\d)/
const RE_ROMANO = /^[IVXLCDM]+$/i
// Conjunciones y artículos que no se capitalizan en medio de una frase.
const CONECTORES = new Set(['o', 'y', 'e', 'u', 'de', 'del', 'la', 'el'])

function capitaliza(palabra: string): string {
  return palabra.charAt(0).toUpperCase() + palabra.slice(1)
}

/** Casa una palabra suelta de un código: romanos y letras en mayúscula, resto en minúscula. */
function palabraLegible(palabra: string, primera: boolean): string {
  if (RE_ROMANO.test(palabra)) {
    return palabra.toUpperCase()
  }
  const min = palabra.toLowerCase()
  if (CONECTORES.has(min)) {
    return primera ? capitaliza(min) : min
  }
  if (palabra.length === 1) {
    return palabra.toUpperCase() // letra de grado suelta (A, B...)
  }
  return primera ? capitaliza(min) : min
}

/**
 * Un valor de dimensión, dicho para una persona. Un "grupoII" se lee "Grupo II";
 * un "AF2_cocina_economato", "Cocina y economato"; un "grupoII_tecnicos",
 * "Grupo II tecnicos". Nunca cambia el valor que se manda a la API: solo lo que
 * ve el usuario. Los valores que ya son legibles ("1 y 2 Estrellas") se respetan.
 */
export function etiquetaValor(dimension: string, valor: string): string {
  if (!valor) {
    return valor
  }
  const curado = VALORES_CURADOS[valor]
  if (curado) {
    return curado
  }
  const sustantivo = SUSTANTIVO_VALOR[dimension]
  if (RE_VALOR_LIMPIO.test(valor)) {
    // Romanos en mayúscula; el resto se respeta ("3A", "1a" no se tocan).
    const limpio = RE_ROMANO.test(valor) ? valor.toUpperCase() : valor
    return sustantivo ? `${sustantivo} ${limpio}` : limpio
  }
  if (!RE_ES_CODIGO.test(valor)) {
    return capitaliza(valor) // ya legible ("cuarto" → "Cuarto", "3 Tenedores" intacto)
  }
  const palabras = valor
    .replace(/^(AF\d+|NS|area[A-Za-zÁÉÍÓÚñ]+|seccion\d*)_/i, '')
    .replace(/^(\d+)y(\d+)_/i, '$1 y $2 ')
    .replace(/_/g, ' ')
    .replace(/([a-zñáéíóú0-9])([A-ZÑ])/g, '$1 $2') // camelCase / dígito→mayúscula
    .replace(/([A-ZÑ])([A-ZÑ][a-zñáéíóú])/g, '$1 $2') // "OCatering" → "O Catering"
    .replace(/\s+/g, ' ')
    .trim()
    .split(' ')
  return palabras.map((p, i) => palabraLegible(p, i === 0)).join(' ')
}

/**
 * Un valor de dimensión con su etiqueta antepuesta, para frases donde el valor
 * suelto no se entiende por sí solo (D219: "tu puesto es barcelona" en vez de
 * "zona Barcelona" — "Barcelona" sin la palabra "zona" delante lee como si
 * fuera el puesto). Si etiquetaValor ya incorpora la etiqueta de la dimensión
 * (p. ej. "Nivel III" ya lleva "Nivel", vía SUSTANTIVO_VALOR), no se repite.
 *
 * etiquetaValor puede haber antepuesto el sinónimo CORTO de SUSTANTIVO_VALOR
 * ("Grupo", "Clase"...) en vez de la etiqueta LARGA de esta dimensión ("Grupo
 * profesional", "Clase de empresa"...). Cuando no coinciden textualmente, el
 * startsWith de arriba nunca casa y anteponer la etiqueta larga a pelo duplica
 * ("Clase de empresa Clase A") o mezcla conceptos ("Tipo de local Grupo A", para
 * grupoEstablecimiento). En ese caso sustituimos el sinónimo corto por la
 * etiqueta larga en vez de anteponerla entera.
 */
export function etiquetaDimensionValor(dimension: string, valor: string): string {
  const legible = etiquetaValor(dimension, valor)
  const etiqueta = etiquetaDimension(dimension)
  if (legible.toLowerCase().startsWith(etiqueta.toLowerCase())) {
    return legible
  }
  const sustantivo = SUSTANTIVO_VALOR[dimension]
  if (sustantivo && legible.toLowerCase().startsWith(sustantivo.toLowerCase())) {
    const resto = legible.slice(sustantivo.length).trim()
    return resto ? `${etiqueta} ${resto}` : etiqueta
  }
  return `${etiqueta} ${legible}`
}

/** Explicación corta (una frase) para las preguntas pendientes conocidas. */
const EXPLICACIONES_DIMENSION: Record<string, string> = {
  claseEmpresa:
    'Es la categoría del local según el convenio (por tamaño o tipo). Suele venir en tu nómina o en el cartel del convenio; si dudas, elige la que creas y compara el resultado.',
  grupoEstablecimiento:
    'El tipo de sitio donde trabajas: hotel, bar, restaurante, cafetería… Elige el que más se parezca. Si te equivocas no pasa nada: pruebas otro y comparas.',
  tipoEstablecimiento:
    'El tipo de sitio donde trabajas: hotel, bar, restaurante, cafetería… Elige el que más se parezca. Si te equivocas no pasa nada: pruebas otro y comparas.',
  categoria:
    'La categoría del local (estrellas, tenedores, tazas…). Suele estar en la entrada o en la web del sitio. Si no la sabes, elige la que creas y compara.',
  categoriaEstablecimiento:
    'La categoría del local (estrellas, tenedores, tazas…). Suele estar en la entrada o en la web del sitio. Si no la sabes, elige la que creas y compara.',
  establecimiento:
    'El tipo de sitio donde trabajas: hotel, bar, restaurante, cafetería… Elige el que más se parezca. Si te equivocas no pasa nada: pruebas otro y comparas.',
  clasificacion:
    'Cómo clasifica el convenio a tu local. Si no lo tienes claro, elige la opción que más te suene y mira si el sueldo cuadra.',
  clasificacionEstablecimiento:
    'Cómo clasifica el convenio a tu local. Si no lo tienes claro, elige la opción que más te suene y mira si el sueldo cuadra.',
  seccion:
    'El tipo de negocio: hotel, restaurante, cafetería, bar, discoteca… Elige el tuyo. Si dudas entre dos, pruébalos y compara.',
  grupoActividad:
    'Es cómo agrupa el convenio los puestos por actividad. No hace falta que te lo sepas: prueba y compara el resultado.',
  grupoProfesional:
    'Tu grupo según el convenio, por responsabilidad (definición del ALEH): 1º = mandos y jefes (organizas y diriges); 2º = técnicos y especialistas (dominas el oficio, trabajas con autonomía); 3º = asistentes (trabajas siguiendo instrucciones y supervisión). Elige el tuyo.',
  zona: 'La parte de la provincia donde está tu local. Elige la tuya; si tu pueblo no sale, mira a qué zona pertenece.',
  provincia:
    'La provincia donde está tu centro de trabajo (el comedor, el colegio, el hospital, la empresa…). Las tablas de este convenio van por provincia. Si la tuya no sale, el convenio no clasifica tu puesto ahí: puedes elegir tu categoría a mano.',
}

export function explicacionDimension(dimension: string): string {
  return EXPLICACIONES_DIMENSION[dimension] ?? ''
}

/**
 * Solo enlazamos URLs http(s): cualquier otro esquema (javascript:, data:...)
 * no se renderiza como enlace. Defensa en profundidad sobre datos del backend.
 */
export function esUrlSegura(url: string | null): url is string {
  return url !== null && /^https?:\/\//i.test(url)
}

/** "2025-12-31" → "31/12/2025". Si no es ISO, se devuelve tal cual. */
export function formatearFecha(valor: string): string {
  const match = /^(\d{4})-(\d{2})-(\d{2})$/.exec(valor)
  if (!match) {
    return valor
  }
  return `${match[3]}/${match[2]}/${match[1]}`
}

/** Fecha de hoy en ISO (yyyy-mm-dd) en hora local, para /calculo/salario-base. */
export function hoyIso(): string {
  const hoy = new Date()
  const mes = String(hoy.getMonth() + 1).padStart(2, '0')
  const dia = String(hoy.getDate()).padStart(2, '0')
  return `${hoy.getFullYear()}-${mes}-${dia}`
}

const RE_FECHA_ISO = /^\d{4}-\d{2}-\d{2}$/

/**
 * La vigencia de un convenio, dicha en cristiano. Clave: un convenio "vencido"
 * NO está caducado — sigue aplicando por ultraactividad hasta que se publique
 * el nuevo. Enseñar fechas pasadas a pelo parece un dato erróneo (feedback de
 * Iulian); aquí se explica sin tecnicismos.
 */
export function describeVigencia(desde: string, hasta: string, hoyIsoStr: string): string {
  if (!RE_FECHA_ISO.test(hasta) || !RE_FECHA_ISO.test(desde)) {
    return 'Vigencia según su publicación oficial'
  }
  if (hasta >= hoyIsoStr) {
    return `En vigor hasta el ${formatearFecha(hasta)}`
  }
  const anio = hasta.slice(0, 4)
  return `Sigue en vigor: mientras no se publique el nuevo convenio, se aplican las últimas tablas (de ${anio})`
}
