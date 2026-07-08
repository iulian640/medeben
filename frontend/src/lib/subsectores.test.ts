import { describe, expect, it } from 'vitest'
import { SUBSECTORES, etiquetaSubsector } from './subsectores'

describe('subsectores', () => {
  it('ofrece las 3 opciones en lenguaje llano con su clave de API', () => {
    expect(SUBSECTORES).toEqual([
      { clave: 'hosteleria', etiqueta: 'Bar, restaurante o cafetería' },
      { clave: 'hospedaje', etiqueta: 'Hotel u hospedaje' },
      { clave: 'restauracion-colectiva', etiqueta: 'Comedor de colegio, hospital, empresa...' },
    ])
  })

  it('traduce una clave a su etiqueta', () => {
    expect(etiquetaSubsector('hospedaje')).toBe('Hotel u hospedaje')
  })

  it('devuelve la clave tal cual si no la conoce', () => {
    expect(etiquetaSubsector('otro')).toBe('otro')
  })
})
