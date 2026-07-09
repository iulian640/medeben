import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'
import {
  CLAVE_HORA_RECORDATORIO,
  DIAS_PROGRAMADOS,
  ID_BASE_RECORDATORIO,
  guardaHoraRecordatorio,
  horaRecordatorio,
  idsRecordatorio,
  planRecordatorios,
} from './recordatorio'

// Mismo idioma que libreta.test.ts: localStorage no existe en el entorno de
// test, se stubea con un Map.
const datos = new Map<string, string>()

beforeEach(() => {
  datos.clear()
  vi.stubGlobal('localStorage', {
    getItem: (clave: string) => datos.get(clave) ?? null,
    setItem: (clave: string, valor: string) => void datos.set(clave, valor),
    removeItem: (clave: string) => void datos.delete(clave),
  })
})

afterEach(() => {
  vi.unstubAllGlobals()
})

describe('preferencia de hora del recordatorio', () => {
  it('guarda y lee la hora; null la borra', () => {
    guardaHoraRecordatorio('21:30')
    expect(horaRecordatorio()).toBe('21:30')
    expect(datos.get(CLAVE_HORA_RECORDATORIO)).toBe('21:30')

    guardaHoraRecordatorio(null)
    expect(horaRecordatorio()).toBeNull()
    expect(datos.has(CLAVE_HORA_RECORDATORIO)).toBe(false)
  })

  it('sin localStorage (modo privado estricto) no explota: simplemente no hay preferencia', () => {
    vi.stubGlobal('localStorage', undefined)

    expect(horaRecordatorio()).toBeNull()
    expect(() => guardaHoraRecordatorio('21:30')).not.toThrow()
  })
})

describe('planRecordatorios', () => {
  // Arrange común: "ahora" fijo, miércoles 8 de julio de 2026 a las 12:00.
  const ahora = new Date(2026, 6, 8, 12, 0)

  it('programa una quincena con ids estables del bloque reservado', () => {
    const plan = planRecordatorios('21:30', ahora)

    expect(plan).toHaveLength(DIAS_PROGRAMADOS)
    expect(plan[0].id).toBe(ID_BASE_RECORDATORIO)
    expect(plan[13].id).toBe(ID_BASE_RECORDATORIO + 13)
    expect(idsRecordatorio()).toEqual(plan.map((n) => n.id))
  })

  it('si la hora de hoy aún no ha pasado, la primera es hoy', () => {
    const plan = planRecordatorios('21:30', ahora)

    expect(plan[0].fecha.getDate()).toBe(8)
    expect(plan[0].fecha.getHours()).toBe(21)
    expect(plan[0].fecha.getMinutes()).toBe(30)
  })

  it('si la hora de hoy ya pasó, empieza mañana', () => {
    const plan = planRecordatorios('09:00', ahora) // son las 12:00

    expect(plan[0].fecha.getDate()).toBe(9)
  })

  it('los días son consecutivos y cruzan el fin de mes sin sustos', () => {
    const finDeMes = new Date(2026, 6, 30, 12, 0) // 30 de julio
    const plan = planRecordatorios('21:30', finDeMes)

    expect(plan[0].fecha.getDate()).toBe(30)
    expect(plan[2].fecha.getMonth()).toBe(7) // 1 de agosto
    expect(plan[2].fecha.getDate()).toBe(1)
  })

  it('el texto habla en cristiano y no lleva datos personales', () => {
    const plan = planRecordatorios('21:30', ahora)

    expect(plan[0].titulo).toBe('Tu libreta')
    expect(plan[0].cuerpo).toContain('¿Has apuntado lo de hoy?')
  })
})
