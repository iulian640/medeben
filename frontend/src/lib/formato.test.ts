import { describe, expect, it } from 'vitest'
import { ApiError } from '../services/api'
import {
  describeVigencia,
  esUrlSegura,
  etiquetaDimension,
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

  it('cae al message del error si no hay detail', () => {
    expect(mensajeDeError(new ApiError(500, 'API 500: Internal Server Error'))).toBe(
      'API 500: Internal Server Error',
    )
  })

  it('da un mensaje genérico para errores que no son Error', () => {
    expect(mensajeDeError('boom')).toBe('Algo ha fallado. Inténtalo de nuevo.')
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

  it('humaniza códigos snake_case y camelCase desconocidos', () => {
    expect(etiquetaValor('seccion', '1y2_hotelesHostales')).toBe('1 y 2 hoteles hostales')
    expect(etiquetaValor('tipoEstablecimiento', 'cafeterias_bares')).toBe('Cafeterias bares')
  })

  it('deja tal cual los valores que ya son legibles', () => {
    expect(etiquetaValor('categoriaEstablecimiento', '1 y 2 Estrellas')).toBe('1 y 2 Estrellas')
    expect(etiquetaValor('categoriaEstablecimiento', '3 Tenedores')).toBe('3 Tenedores')
  })

  it('sin sustantivo conocido, un valor limpio se muestra tal cual', () => {
    expect(etiquetaValor('apartadoBOP', 'VII')).toBe('VII')
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
