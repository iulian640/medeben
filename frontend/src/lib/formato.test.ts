import { describe, expect, it } from 'vitest'
import { ApiError } from '../services/api'
import {
  describeVigencia,
  esUrlSegura,
  etiquetaDimension,
  etiquetaDimensionValor,
  etiquetaUnidad,
  etiquetaValor,
  explicacionDimension,
  formatearFecha,
  formatearImporte,
  mensajeDeError,
} from './formato'

describe('formatearImporte', () => {
  it('formatea con separador de miles y 2 decimales al estilo español', () => {
    expect(formatearImporte(1425.5)).toBe('1.425,50')
  })

  it('formatea importes pequeños (precio hora)', () => {
    expect(formatearImporte(11.4)).toBe('11,40')
  })
})

describe('etiquetaUnidad', () => {
  it('traduce EUR/mes a lenguaje llano', () => {
    expect(etiquetaUnidad('EUR/mes')).toBe('€ al mes')
  })

  it('traduce EUR/año (Cuenca publica en anual)', () => {
    expect(etiquetaUnidad('EUR/año')).toBe('€ al año')
  })

  it('deja tal cual una unidad desconocida antes que inventar', () => {
    expect(etiquetaUnidad('€/día trabajado')).toBe('€/día trabajado')
  })
})

describe('mensajeDeError', () => {
  it('usa el detail de un error RFC 7807 del backend', () => {
    const error = new ApiError(404, 'API 404: Not Found', {
      status: 404,
      detail: "No hay convenio para la provincia 'Narnia'",
    })
    expect(mensajeDeError(error)).toBe("No hay convenio para la provincia 'Narnia'")
  })

  it('usa el message del backend si no hay detail (variante RFC 7807 con message)', () => {
    const error = new ApiError(400, 'API 400: Bad Request', {
      message: 'El NIF no es válido',
    })
    expect(mensajeDeError(error)).toBe('El NIF no es válido')
  })

  it('un 403 sin cuerpo JSON útil da un mensaje en castellano, nunca el texto en inglés', () => {
    const error = new ApiError(403, 'API 403: Forbidden', null)
    const mensaje = mensajeDeError(error)
    expect(mensaje).not.toMatch(/Forbidden/i)
    expect(mensaje).not.toMatch(/API 403/)
    expect(mensaje).toBe('No tienes permiso para hacer esto.')
  })

  it('un 502 de proxy/CDN sin JSON (body null) da un mensaje en castellano', () => {
    const error = new ApiError(502, 'API 502: Bad Gateway', null)
    const mensaje = mensajeDeError(error)
    expect(mensaje).not.toMatch(/Bad Gateway/i)
    expect(mensaje).not.toMatch(/API 502/)
    expect(mensaje).toBe(
      'El servidor no está disponible ahora mismo. Inténtalo de nuevo en un momento.',
    )
  })

  it('un 429 sin cuerpo JSON útil pide esperar, en castellano', () => {
    const error = new ApiError(429, 'API 429: Too Many Requests', null)
    expect(mensajeDeError(error)).toBe(
      'Estás yendo muy rápido. Espera un momento e inténtalo de nuevo.',
    )
  })

  it('un 4xx sin detail/message ni mapeo específico da un genérico en castellano', () => {
    const error = new ApiError(418, "API 418: I'm a teapot", null)
    const mensaje = mensajeDeError(error)
    expect(mensaje).not.toMatch(/teapot/i)
    expect(mensaje).toBe('No hemos podido completar la acción. Inténtalo de nuevo.')
  })

  it('da un mensaje genérico para errores que no son Error', () => {
    expect(mensajeDeError('boom')).toBe('Algo ha fallado. Inténtalo de nuevo.')
  })

  it('traduce el TypeError de red del fetch (offline, DNS, CORS...) a un mensaje sin tecnicismos', () => {
    expect(mensajeDeError(new TypeError('Failed to fetch'))).toBe(
      'No hay conexión. Comprueba tu red e inténtalo de nuevo.',
    )
  })

  it('traduce también el TypeError de red con el texto de Firefox/Safari, no solo el de Chrome', () => {
    expect(mensajeDeError(new TypeError('NetworkError when attempting to fetch resource.'))).toBe(
      'No hay conexión. Comprueba tu red e inténtalo de nuevo.',
    )
  })
})

describe('etiquetas de dimensiones', () => {
  it('conoce claseEmpresa', () => {
    expect(etiquetaDimension('claseEmpresa')).toBe('Clase de empresa')
  })

  it('humaniza una dimensión camelCase desconocida', () => {
    expect(etiquetaDimension('nombreRaroDesconocido')).toBe('Nombre raro desconocido')
  })

  it('da nombre en cristiano a las dimensiones de tipo de local', () => {
    expect(etiquetaDimension('grupoEstablecimiento')).toBe('Tipo de local')
    expect(etiquetaDimension('seccion')).toBe('Tipo de negocio')
    expect(etiquetaDimension('grupoActividad')).toBe('Grupo de actividad')
  })

  it('tiene explicación corta para claseEmpresa y para el tipo de local', () => {
    expect(explicacionDimension('claseEmpresa')).not.toBe('')
    expect(explicacionDimension('grupoEstablecimiento')).not.toBe('')
    expect(explicacionDimension('inventada')).toBe('')
  })

  it('conoce la provincia (pregunta de la restauración colectiva) y la explica', () => {
    expect(etiquetaDimension('provincia')).toBe('Provincia')
    expect(explicacionDimension('provincia')).not.toBe('')
  })
})

describe('etiquetaValor', () => {
  it('prefija los valores limpios con el nombre de la dimensión', () => {
    expect(etiquetaValor('grupo', 'II')).toBe('Grupo II')
    expect(etiquetaValor('grupoActividad', 'III')).toBe('Grupo III')
    expect(etiquetaValor('nivel', '3')).toBe('Nivel 3')
    expect(etiquetaValor('claseEmpresa', 'A')).toBe('Clase A')
    expect(etiquetaValor('clasificacionEstablecimiento', '5')).toBe('Clasificación 5')
  })

  it('traduce los códigos de área funcional del ALEH', () => {
    expect(etiquetaValor('areaFuncional', 'AF2_cocina_economato')).toBe('Cocina y economato')
    expect(etiquetaValor('areaFuncional', 'AF4_pisos_limpieza')).toBe('Pisos y limpieza')
  })

  it('traduce los grupos profesionales del ALEH a algo autoexplicativo', () => {
    expect(etiquetaValor('grupoProfesional', 'grupoPrimero')).toBe('1º · Mando o jefe/a')
    expect(etiquetaValor('grupoProfesional', 'grupoSegundo')).toBe('2º · Técnico/a o especialista')
    expect(etiquetaValor('grupoProfesional', 'grupoTercero')).toBe('3º · Asistente')
  })

  it('traduce los tipos de establecimiento de los convenios condicionales', () => {
    expect(etiquetaValor('establecimiento', 'hoteles_5o4_estrellas')).toBe('Hoteles de 4 o 5 estrellas')
    expect(etiquetaValor('establecimiento', 'cafes_bares_cervecerias_especial')).toBe(
      'Cafés, bares y cervecerías (categoría especial)',
    )
    expect(etiquetaValor('establecimiento', 'restaurantes_5_tenedores')).toBe(
      'Restaurantes de 5 tenedores',
    )
    expect(etiquetaValor('establecimiento', 'colectividades')).toBe(
      'Colectividades (comedores, hospitales, catering…)',
    )
  })

  it('pone la tilde a las provincias que la capa normalizada guarda en ASCII', () => {
    expect(etiquetaValor('provincia', 'Caceres')).toBe('Cáceres')
    expect(etiquetaValor('provincia', 'A Coruna')).toBe('A Coruña')
    expect(etiquetaValor('provincia', 'Malaga')).toBe('Málaga')
    // Las que ya van bien se quedan como están.
    expect(etiquetaValor('provincia', 'Madrid')).toBe('Madrid')
    expect(etiquetaValor('provincia', 'Santa Cruz de Tenerife')).toBe('Santa Cruz de Tenerife')
  })

  it('humaniza códigos snake_case y camelCase desconocidos', () => {
    expect(etiquetaValor('seccion', '1y2_hotelesHostales')).toBe('1 y 2 hoteles hostales')
    expect(etiquetaValor('tipoEstablecimiento', 'cafeterias_bares')).toBe('Cafeterias bares')
  })

  it('conserva los romanos embebidos en un código (no los pasa a minúscula)', () => {
    expect(etiquetaValor('grupo', 'grupoII_tecnicos')).toBe('Grupo II tecnicos')
    expect(etiquetaValor('grupo', 'grupoI_mandos')).toBe('Grupo I mandos')
    expect(etiquetaValor('grupo', 'grupoIII_asistentes')).toBe('Grupo III asistentes')
  })

  it('separa la conjunción pegada en camelCase ("...OCatering")', () => {
    expect(etiquetaValor('cargo', 'auxiliarCocinaOCatering')).toBe('Auxiliar cocina o catering')
  })

  it('no pierde la letra de nivel de un código con sufijo ("NS_V_A")', () => {
    expect(etiquetaValor('nivelSalarial', 'NS_V_A')).toBe('V A')
  })

  it('un "tipoA" del árbol de decisión se lee "Tipo A", no "Tipo a"', () => {
    expect(etiquetaValor('establecimiento', 'tipoA')).toBe('Tipo A')
  })

  it('el área funcional con valor limpio lleva su sustantivo', () => {
    expect(etiquetaValor('areaFuncional', 'A')).toBe('Área A')
  })

  it('capitaliza los valores ya legibles pero sueltos ("cuarto" → "Cuarto")', () => {
    expect(etiquetaValor('grupoProfesional', 'cuarto')).toBe('Cuarto')
  })

  it('deja tal cual los valores que ya son legibles', () => {
    expect(etiquetaValor('categoriaEstablecimiento', '1 y 2 Estrellas')).toBe('1 y 2 Estrellas')
    expect(etiquetaValor('categoriaEstablecimiento', '3 Tenedores')).toBe('3 Tenedores')
  })

  it('sin sustantivo conocido, un valor limpio se muestra tal cual', () => {
    expect(etiquetaValor('apartadoBOP', 'VII')).toBe('VII')
  })
})

describe('etiquetaDimensionValor', () => {
  it('antepone la etiqueta de la dimensión cuando el valor no la incluye (D219: zona "Barcelona")', () => {
    expect(etiquetaDimensionValor('zona', 'Barcelona')).toBe('Zona Barcelona')
  })

  it('no repite la etiqueta cuando el valor ya la incorpora (nivel/categoría vía sustantivo)', () => {
    expect(etiquetaDimensionValor('nivel', 'III')).toBe('Nivel III')
    expect(etiquetaDimensionValor('categoria', 'C')).toBe('Categoría C')
  })

  it('no duplica la palabra cuando el sinónimo corto de etiquetaValor no coincide con la etiqueta larga (regresión hallazgo revisor PR#223)', () => {
    // etiquetaValor antepone el sinónimo corto de SUSTANTIVO_VALOR ("Clase", "Grupo",
    // "Clasificación"...) que no siempre coincide con la etiqueta larga de
    // ETIQUETAS_DIMENSION ("Clase de empresa", "Grupo de actividad"...). Sin este
    // fix, etiquetaDimensionValor antepone la etiqueta larga IGUAL, duplicando la
    // palabra: "Clase de empresa Clase A".
    expect(etiquetaDimensionValor('claseEmpresa', 'A')).toBe('Clase de empresa A')
    expect(etiquetaDimensionValor('grupoActividad', 'III')).toBe('Grupo de actividad III')
    expect(etiquetaDimensionValor('clasificacionEstablecimiento', '5')).toBe(
      'Clasificación del local 5',
    )
  })

  it('no mezcla conceptos cuando el sinónimo corto es de otra dimensión (datos reales: Badajoz/Cádiz/Baleares/Alicante)', () => {
    // grupoProfesional: badajoz-hosteleria.json y cadiz-hosteleria.json guardan
    // "I"/"1" sin pasar por VALORES_CURADOS (que solo mapea grupoPrimero/Segundo/Tercero).
    expect(etiquetaDimensionValor('grupoProfesional', 'I')).toBe('Grupo profesional I')
    // categoriaEstablecimiento: cadiz-hosteleria.json y baleares-hosteleria.json.
    expect(etiquetaDimensionValor('categoriaEstablecimiento', 'A')).toBe('Categoría del local A')
    // grupoEstablecimiento: alicante-hosteleria.json. SUSTANTIVO_VALOR dice "Grupo"
    // pero la dimensión es "Tipo de local" — sin el fix da "Tipo de local Grupo A",
    // una frase que mezcla dos conceptos distintos como si fueran el mismo.
    expect(etiquetaDimensionValor('grupoEstablecimiento', 'A')).toBe('Tipo de local A')
  })
})

describe('esUrlSegura', () => {
  it('acepta http y https', () => {
    expect(esUrlSegura('https://www.boe.es/x')).toBe(true)
    expect(esUrlSegura('http://boe.es')).toBe(true)
    expect(esUrlSegura('HTTPS://BOE.ES')).toBe(true)
  })

  it('rechaza esquemas peligrosos, vacío y null', () => {
    expect(esUrlSegura('javascript:alert(1)')).toBe(false)
    expect(esUrlSegura('data:text/html,x')).toBe(false)
    expect(esUrlSegura('')).toBe(false)
    expect(esUrlSegura(null)).toBe(false)
  })
})

describe('formatearFecha', () => {
  it('pasa una fecha ISO a dd/mm/aaaa', () => {
    expect(formatearFecha('2025-12-31')).toBe('31/12/2025')
  })

  it('devuelve el valor tal cual si no es ISO', () => {
    expect(formatearFecha('en vigor')).toBe('en vigor')
  })
})

describe('describeVigencia', () => {
  const hoy = '2026-07-08'

  it('convenio en vigor por fechas → "En vigor hasta..."', () => {
    expect(describeVigencia('2025-01-01', '2029-12-31', hoy)).toBe('En vigor hasta el 31/12/2029')
  })

  it('convenio vencido → lo explica en cristiano (ultraactividad), sin parecer un dato roto', () => {
    expect(describeVigencia('2023-01-01', '2025-12-31', hoy)).toBe(
      'Sigue en vigor: mientras no se publique el nuevo convenio, se aplican las últimas tablas (de 2025)',
    )
  })

  it('fechas no ISO ("pendiente") → texto neutro', () => {
    expect(describeVigencia('2023', 'pendiente', hoy)).toBe('Vigencia según su publicación oficial')
  })
})
