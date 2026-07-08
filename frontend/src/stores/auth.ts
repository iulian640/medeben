import { computed, ref } from 'vue'
import { defineStore } from 'pinia'
import { setAuthToken } from '../services/api'
import { postLogin, postRegistro } from '../services/auth'
import { mensajeDeError } from '../lib/formato'

/**
 * Sesión del usuario. REQUISITO DE SEGURIDAD: el JWT vive SOLO aquí, en
 * memoria — nunca localStorage, sessionStorage ni cookies legibles por JS
 * (un XSS no debe encontrar una credencial persistida). Al recargar la
 * página la sesión se pierde y se vuelve a pedir login; aceptado para v1.
 */
export const useAuthStore = defineStore('auth', () => {
  const token = ref<string | null>(null)
  const email = ref<string | null>(null)
  const expiraEn = ref<string | null>(null)
  const cargando = ref(false)
  const error = ref<string | null>(null)
  /** Mensaje informativo (p. ej. "tu sesión ha caducado") para la pantalla de login. */
  const aviso = ref<string | null>(null)

  const autenticado = computed(() => token.value !== null)

  async function iniciarSesion(emailForm: string, password: string): Promise<boolean> {
    if (cargando.value) {
      return false
    }
    cargando.value = true
    error.value = null
    aviso.value = null
    try {
      const emitido = await postLogin(emailForm, password)
      token.value = emitido.token
      expiraEn.value = emitido.expiraEn
      email.value = emailForm
      setAuthToken(emitido.token)
      return true
    } catch (e) {
      limpiarSesion()
      error.value = mensajeDeError(e)
      return false
    } finally {
      cargando.value = false
    }
  }

  async function registrarse(emailForm: string, password: string): Promise<boolean> {
    if (cargando.value) {
      return false
    }
    cargando.value = true
    error.value = null
    try {
      await postRegistro(emailForm, password)
    } catch (e) {
      error.value = mensajeDeError(e)
      return false
    } finally {
      cargando.value = false
    }
    // Cuenta creada: entramos directamente con las mismas credenciales.
    return iniciarSesion(emailForm, password)
  }

  function limpiarSesion() {
    token.value = null
    email.value = null
    expiraEn.value = null
    setAuthToken(null)
  }

  /** Logout voluntario. */
  function cerrarSesion() {
    limpiarSesion()
    error.value = null
    aviso.value = null
  }

  /** 401 con token: la sesión ya no vale. Se limpia y se avisa en el login. */
  function sesionCaducada() {
    limpiarSesion()
    aviso.value = 'Tu sesión ha caducado. Entra de nuevo, por favor.'
  }

  return {
    // Solo lectura hacia fuera: nadie puede tocar el token sin pasar por las
    // acciones del store (que mantienen el cliente API sincronizado). El JWT
    // que viaja lo gestiona services/api.ts.
    token: computed(() => token.value),
    email,
    expiraEn,
    cargando,
    error,
    aviso,
    autenticado,
    iniciarSesion,
    registrarse,
    cerrarSesion,
    sesionCaducada,
  }
})
