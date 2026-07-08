import { beforeEach, describe, expect, it } from 'vitest'
import { createPinia, setActivePinia } from 'pinia'
import type { RouteLocationNormalized } from 'vue-router'
import { useAuthStore } from '../stores/auth'
import { guardiaSesion } from './guardia'

function ruta(parcial: Partial<RouteLocationNormalized>): RouteLocationNormalized {
  return { meta: {}, fullPath: '/', name: undefined, ...parcial } as RouteLocationNormalized
}

beforeEach(() => {
  setActivePinia(createPinia())
})

describe('guardiaSesion', () => {
  it('deja pasar a rutas públicas sin sesión', () => {
    expect(guardiaSesion(ruta({ fullPath: '/perfil' }))).toBe(true)
  })

  it('manda a login (con redirect de vuelta) si la ruta exige sesión y no la hay', () => {
    const resultado = guardiaSesion(ruta({ meta: { requiereSesion: true }, fullPath: '/cuenta' }))

    expect(resultado).toEqual({ name: 'login', query: { redirect: '/cuenta' } })
  })

  it('deja pasar a rutas protegidas con sesión iniciada', () => {
    const auth = useAuthStore()
    auth.token = 'jwt-123'

    expect(guardiaSesion(ruta({ meta: { requiereSesion: true }, fullPath: '/cuenta' }))).toBe(true)
  })

  it('con sesión iniciada, login y registro redirigen a la cuenta', () => {
    const auth = useAuthStore()
    auth.token = 'jwt-123'

    expect(guardiaSesion(ruta({ name: 'login', fullPath: '/login' }))).toEqual({ name: 'cuenta' })
    expect(guardiaSesion(ruta({ name: 'registro', fullPath: '/registro' }))).toEqual({
      name: 'cuenta',
    })
  })
})
