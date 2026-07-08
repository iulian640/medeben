import { describe, expect, it } from 'vitest'
import { ApiError } from '../services/api'
import {
  esUrlSegura,
  etiquetaDimension,
  etiquetaUnidad,
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
    expect(etiquetaDimension('tipoEstablecimiento')).toBe('Tipo establecimiento')
  })

  it('tiene explicación corta para claseEmpresa y vacía para el resto', () => {
    expect(explicacionDimension('claseEmpresa')).not.toBe('')
    expect(explicacionDimension('nivel')).toBe('')
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
