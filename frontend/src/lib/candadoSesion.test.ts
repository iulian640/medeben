import { afterEach, describe, expect, it, vi } from 'vitest'
import { ESPERA_MAX_CANDADO_MS, NOMBRE_CANDADO_SESION, conCandadoExclusivo } from './candadoSesion'
import { TIMEOUT_REFRESH_MS } from '../services/auth'

/**
 * LockManager de mentira que concede los candados EN SERIE: cada callback
 * espera a que termine el anterior, como hace el navigator.locks real con el
 * mismo nombre de candado. Suficiente para probar la exclusión mutua.
 */
function locksSecuenciales() {
  let ultimo: Promise<unknown> = Promise.resolve()
  return {
    request: (_nombre: string, _opts: unknown, callback: () => Promise<unknown>) => {
      const turno = ultimo.then(() => callback())
      ultimo = turno.catch(() => {
        // Un fallo en un turno no puede atascar la cola de los siguientes.
      })
      return turno
    },
  }
}

/**
 * LockManager que NUNCA concede el candado (otra pestaña colgada lo retiene):
 * solo responde al abort de la señal, como el real. Para probar el timeout.
 */
function locksQueNuncaConceden() {
  return {
    request: (_nombre: string, opts: { signal?: AbortSignal }) =>
      new Promise((_resolve, reject) => {
        opts.signal?.addEventListener('abort', () =>
          reject(new DOMException('abortado', 'AbortError')),
        )
      }),
  }
}

afterEach(() => {
  vi.unstubAllGlobals()
  vi.useRealTimers()
  vi.restoreAllMocks()
})

describe('conCandadoExclusivo', () => {
  it('INVARIANTE: el candado espera MÁS que el POST de refresh que protege', () => {
    // Si el candado se rindiera antes de que el refresh más lento termine, la
    // pestaña degradada leería el marcador enVuelo de un refresh VIVO y lo
    // quemaría como huérfano (review #229, HIGH). Quien cambie una constante
    // sin la otra, rompe este test.
    expect(ESPERA_MAX_CANDADO_MS).toBeGreaterThan(TIMEOUT_REFRESH_MS)
  })

  it('sin navigator.locks ejecuta la función directamente y devuelve su resultado', async () => {
    // jsdom no trae Web Locks: este es el camino de fallback (best-effort).
    expect(await conCandadoExclusivo(async () => 'resultado')).toBe('resultado')
  })

  it('sin navigator.locks avisa UNA vez por consola del modo degradado (no spamea)', async () => {
    // El aviso importa (candado no-op = origen sin secure context en prod),
    // pero repetirlo en cada refresh sería ruido. Módulo fresco para no
    // heredar el flag de otros tests.
    vi.resetModules()
    const { conCandadoExclusivo: conCandado } = await import('./candadoSesion')
    const warn = vi.spyOn(console, 'warn').mockImplementation(() => {})

    await conCandado(async () => 1)
    await conCandado(async () => 2)

    expect(warn).toHaveBeenCalledTimes(1)
  })

  it('sin navigator.locks los errores de la función se propagan', async () => {
    await expect(
      conCandadoExclusivo(async () => {
        throw new Error('revienta')
      }),
    ).rejects.toThrow('revienta')
  })

  it('con locks serializa: dos secciones concurrentes no se entrelazan', async () => {
    vi.stubGlobal('navigator', { locks: locksSecuenciales() })
    const orden: string[] = []

    const a = conCandadoExclusivo(async () => {
      orden.push('a-entra')
      // Cede el event loop: sin candado, b se colaría aquí en medio.
      await Promise.resolve()
      orden.push('a-sale')
    })
    const b = conCandadoExclusivo(async () => {
      orden.push('b-entra')
      orden.push('b-sale')
    })
    await Promise.all([a, b])

    expect(orden).toEqual(['a-entra', 'a-sale', 'b-entra', 'b-sale'])
  })

  it('con locks pide SIEMPRE el mismo nombre de candado (todas las pestañas compiten por él)', async () => {
    const request = vi.fn((_n: string, _o: unknown, cb: () => Promise<unknown>) => cb())
    vi.stubGlobal('navigator', { locks: { request } })

    await conCandadoExclusivo(async () => 'x')

    expect(request).toHaveBeenCalledWith(NOMBRE_CANDADO_SESION, expect.anything(), expect.any(Function))
  })

  it('con locks los errores de la función se propagan', async () => {
    vi.stubGlobal('navigator', { locks: locksSecuenciales() })

    await expect(
      conCandadoExclusivo(async () => {
        throw new Error('revienta-con-candado')
      }),
    ).rejects.toThrow('revienta-con-candado')
  })

  it('si el candado no llega a tiempo, degrada a best-effort y ejecuta igual', async () => {
    // Una pestaña colgada reteniendo el candado no puede dejar al usuario sin
    // renovar sesión para siempre: pasado el plazo se ejecuta sin exclusión.
    vi.useFakeTimers()
    vi.stubGlobal('navigator', { locks: locksQueNuncaConceden() })
    vi.spyOn(console, 'warn').mockImplementation(() => {})

    const pendiente = conCandadoExclusivo(async () => 'sin-candado')
    await vi.advanceTimersByTimeAsync(ESPERA_MAX_CANDADO_MS)

    await expect(pendiente).resolves.toBe('sin-candado')
  })
})
