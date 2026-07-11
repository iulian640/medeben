import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'
import { DOMWrapper, mount, flushPromises, type VueWrapper } from '@vue/test-utils'
import { createPinia, setActivePinia } from 'pinia'
import { createMemoryHistory, createRouter, type Router } from 'vue-router'
import { ApiError } from '../services/api'
import type { ApunteGuardado, EstadoDiaGuardado } from '../services/fichajes'
import { CLAVE_ONBOARDING_LIBRETA } from '../lib/libreta'
import { hoyIso } from '../lib/formato'
import LibretaView from './LibretaView.vue'

vi.mock('../services/fichajes', () => ({
  getEstadoDia: vi.fn(),
  postApunte: vi.fn(),
}))
vi.mock('../services/horario', () => ({
  getHorarioSemana: vi.fn(),
}))

import { getEstadoDia, postApunte } from '../services/fichajes'

const apunteEntrada: ApunteGuardado = {
  fecha: '2026-07-08',
  tipo: 'ENTRADA',
  hora: '14:05',
  motivo: null,
  origen: 'CONFIRMADO',
  registradoEn: new Date(2026, 6, 8, 14, 6).toISOString(),
}

const diaServidor: EstadoDiaGuardado = {
  fecha: '2026-07-08',
  estado: 'EN_CURSO',
  sellado: false,
  selladoDesde: '2026-07-23',
  minutosTrabajados: null,
  tramos: [],
  entradaAbierta: '14:05',
  apuntes: [apunteEntrada],
}

const Stub = { template: '<div />' }

function crearRouter(): Router {
  return createRouter({
    history: createMemoryHistory(),
    routes: [
      { path: '/', name: 'home', component: Stub },
      { path: '/libreta', name: 'libreta', component: LibretaView },
      { path: '/libreta/semana', name: 'libreta-semana', component: Stub },
      { path: '/libreta/dia/:fecha', name: 'libreta-dia', component: LibretaView },
      { path: '/resumen', name: 'resumen', component: Stub },
      { path: '/horario', name: 'horario', component: Stub },
    ],
  })
}

async function montarConRouter(
  ruta: string,
): Promise<{ wrapper: VueWrapper; router: Router }> {
  const pinia = createPinia()
  setActivePinia(pinia)
  const router = crearRouter()
  await router.push(ruta)
  const wrapper = mount(LibretaView, { global: { plugins: [pinia, router] } })
  await flushPromises()
  return { wrapper, router }
}

async function montar(ruta = '/libreta'): Promise<VueWrapper> {
  return (await montarConRouter(ruta)).wrapper
}

function boton(wrapper: VueWrapper, texto: string) {
  const encontrado = wrapper.findAll('button').find((b) => b.text() === texto)
  if (!encontrado) {
    throw new Error(`No hay botón "${texto}"`)
  }
  return encontrado
}

/**
 * Los paneles de hora manual y ausencia viven siempre en el DOM (PanelPlegable
 * los despliega animado en vez de montarlos con v-if), así que ahora hay dos
 * <form> a la vez: localizar por el campo que contienen evita la ambigüedad
 * de "el primer form", que además dejaría de ser correcto si cambia el orden.
 */
function formularioDe(wrapper: VueWrapper, selectorCampo: string) {
  const campo = wrapper.get(selectorCampo).element
  const form = campo.closest('form')
  if (!form) {
    throw new Error(`El campo "${selectorCampo}" no está dentro de un <form>`)
  }
  return new DOMWrapper(form)
}

/** Este jsdom no trae localStorage: un doble mínimo en memoria basta. */
function localStorageFalso(): Pick<Storage, 'getItem' | 'setItem' | 'removeItem'> {
  const datos = new Map<string, string>()
  return {
    getItem: (clave: string) => datos.get(clave) ?? null,
    setItem: (clave: string, valor: string) => void datos.set(clave, valor),
    removeItem: (clave: string) => void datos.delete(clave),
  }
}

beforeEach(() => {
  vi.clearAllMocks()
  vi.stubGlobal('localStorage', localStorageFalso())
  // Por defecto el onboarding ya está visto; los tests de onboarding lo quitan.
  localStorage.setItem(CLAVE_ONBOARDING_LIBRETA, '1')
  vi.mocked(getEstadoDia).mockResolvedValue(diaServidor)
})

afterEach(() => {
  vi.useRealTimers()
  vi.unstubAllGlobals()
})

describe('LibretaView — el día', () => {
  it('muestra el estado del día y sus apuntes, sin el contador de sellado (retirado por ruido)', async () => {
    const wrapper = await montar()

    expect(wrapper.text()).toContain('miércoles 08/07/2026')
    expect(wrapper.text()).toContain('En curso: falta la salida')
    expect(wrapper.text()).toContain('Entrada')
    expect(wrapper.text()).toContain('a las 14:05')
    expect(wrapper.text()).toContain('fichado al momento')
    // El contador diario de sellado se retiró: era ruido. La protección se
    // explica en el onboarding y el panel de rectificación aparece cuando toca.
    expect(wrapper.text()).not.toMatch(/se sella|queda protegido como prueba/)
  })

  it('desde la libreta se llega al resumen y al editor de horario', async () => {
    const wrapper = await montar()

    const enlaces = wrapper.findAll('a.enlace-resumen')
    expect(enlaces.map((e) => e.attributes('href'))).toEqual(['/resumen', '/horario'])
    expect(enlaces[1].text()).toContain('Tu horario')
  })

  it('la tarjeta enseña la jornada derivada y pliega el diario en bruto', async () => {
    vi.mocked(getEstadoDia).mockResolvedValue({
      ...diaServidor,
      estado: 'COMPLETO',
      minutosTrabajados: 242,
      tramos: [
        { entrada: '10:00', salida: '14:02' },
        { entrada: '14:30', salida: '18:00' },
      ],
      entradaAbierta: null,
      apuntes: [
        apunteEntrada,
        { ...apunteEntrada, tipo: 'SALIDA', hora: '14:02', registradoEn: '2026-07-08T14:02:30+02:00' },
      ],
    })

    const wrapper = await montar()

    // La lectura: los tramos con las correcciones ya aplicadas, cada uno con
    // su franja horaria (no solo la etiqueta).
    expect(wrapper.text()).toContain('Turno 1')
    expect(wrapper.text()).toContain('10:00 → 14:02')
    expect(wrapper.text()).toContain('Turno 2')
    expect(wrapper.text()).toContain('14:30 → 18:00')
    // El diario en bruto (la prueba) queda plegado hasta que se pide.
    const plegableDe = () => wrapper.get('.apuntes').element.closest('.plegable')
    expect(plegableDe()?.classList.contains('abierto')).toBe(false)
    await boton(wrapper, 'Ver el diario (2 apuntes)').trigger('click')
    expect(plegableDe()?.classList.contains('abierto')).toBe(true)
    await boton(wrapper, 'Ocultar el diario').trigger('click')
    expect(plegableDe()?.classList.contains('abierto')).toBe(false)
  })

  it('con la jornada abierta, la tarjeta dice desde cuándo', async () => {
    const wrapper = await montar()

    // diaServidor está EN_CURSO con la entrada de las 14:05 sin salida.
    expect(wrapper.text()).toContain('En curso')
    expect(wrapper.text()).toContain('desde las 14:05')
  })

  it('un total sin calcular se explica, no se esconde (techo de cordura)', async () => {
    vi.mocked(getEstadoDia).mockResolvedValue({
      ...diaServidor,
      estado: 'COMPLETO',
      minutosTrabajados: null,
      tramos: [{ entrada: '10:00', salida: '09:00' }],
      entradaAbierta: null,
    })

    const wrapper = await montar()

    expect(wrapper.text()).toContain('10:00 → 09:00')
    expect(wrapper.text()).toContain('Sin total: hay un tramo que no cuadra')
    expect(wrapper.text()).not.toContain('Llevas apuntado')
  })

  it('un día que no cuadra lo dice alto: aviso con explicación, sin lectura falsa y con el diario en bruto accesible (issue #230)', async () => {
    // El caso (a) de la issue: turno partido reconstruido en desorden. El
    // backend deriva NO_CUADRA sin tramos ni total; la vista debe avisar en
    // vez de enseñar una jornada plausible pero falsa o un cero mudo.
    vi.mocked(getEstadoDia).mockResolvedValue({
      ...diaServidor,
      estado: 'NO_CUADRA',
      minutosTrabajados: null,
      tramos: [],
      entradaAbierta: null,
      apuntes: [
        { ...apunteEntrada, tipo: 'SALIDA', hora: '14:00', registradoEn: '2026-07-08T21:00:00+02:00' },
        { ...apunteEntrada, hora: '10:00', registradoEn: '2026-07-08T21:01:00+02:00' },
        { ...apunteEntrada, tipo: 'SALIDA', hora: '21:00', registradoEn: '2026-07-08T21:02:00+02:00' },
        { ...apunteEntrada, hora: '17:00', registradoEn: '2026-07-08T21:03:00+02:00' },
      ],
    })

    const wrapper = await montar()

    // El estado en cristiano y el aviso que explica qué pasa y qué hacer.
    expect(wrapper.text()).toContain('No cuadra: revísalo')
    const aviso = wrapper.find('.aviso-no-cuadra')
    expect(aviso.attributes('role')).toBe('alert')
    expect(aviso.text()).toContain('se contradicen')
    expect(aviso.text()).toContain('no suma en el resumen del mes')
    // Ni jornada inventada ni total: no hay lectura que enseñar.
    expect(wrapper.text()).not.toContain('Tu jornada')
    expect(wrapper.text()).not.toContain('Llevas apuntado')
    expect(wrapper.text()).not.toContain('Sin total')
    // La prueba sigue ahí: el diario en bruto, plegado, con sus 4 apuntes.
    await boton(wrapper, 'Ver el diario (4 apuntes)').trigger('click')
    expect(wrapper.text()).toContain('a las 14:00')
    expect(wrapper.text()).toContain('a las 17:00')
  })

  it('si la carga falla, enseña el error y deja reintentar', async () => {
    vi.mocked(getEstadoDia).mockRejectedValueOnce(
      new ApiError(500, 'API 500', { status: 500, detail: 'Error interno' }),
    )

    const wrapper = await montar()

    expect(wrapper.find('[role="alert"]').text()).toContain('Error interno')

    vi.mocked(getEstadoDia).mockResolvedValue(diaServidor)
    await boton(wrapper, 'Reintentar').trigger('click')
    await flushPromises()

    expect(wrapper.text()).toContain('En curso: falta la salida')
  })

  it('el motivo de una ausencia se interpola como texto, nunca como HTML', async () => {
    vi.mocked(getEstadoDia).mockResolvedValue({
      ...diaServidor,
      estado: 'AUSENCIA',
      apuntes: [
        {
          ...apunteEntrada,
          tipo: 'AUSENCIA',
          hora: null,
          motivo: '<img src="x" onerror="alert(1)">',
        },
      ],
    })

    const wrapper = await montar()

    expect(wrapper.find('.apuntes img').exists()).toBe(false)
    expect(wrapper.text()).toContain('<img src="x" onerror="alert(1)">')
    // El fixture arrastra un entradaAbierta rezagado: en un día AUSENCIA la
    // tarjeta NO puede pintar "jornada en curso" (acoplado al estado, review).
    expect(wrapper.text()).not.toContain('desde las 14:05')
  })
})

describe('LibretaView — fichar', () => {
  it('"Entro ahora" manda la fecha del backend y la hora del momento, y enseña el sello', async () => {
    // El navegador ya va por la madrugada del día 9, pero el estado cargado
    // dice que el día en pantalla es el 8: manda la fecha del backend.
    vi.useFakeTimers({ toFake: ['Date'], now: new Date(2026, 6, 9, 1, 30) })
    vi.mocked(postApunte).mockResolvedValue(apunteEntrada)
    const wrapper = await montar()

    await boton(wrapper, 'Entro ahora').trigger('click')
    await flushPromises()

    expect(postApunte).toHaveBeenCalledWith({
      fecha: '2026-07-08',
      tipo: 'ENTRADA',
      hora: '01:30',
      motivo: null,
      rectificacionTardiaConfirmada: false,
    })
    expect(wrapper.text()).toContain('✓ apuntado a las 14:06')
  })

  it('"Salgo ahora" manda una SALIDA', async () => {
    vi.mocked(postApunte).mockResolvedValue({ ...apunteEntrada, tipo: 'SALIDA' })
    const wrapper = await montar()

    await boton(wrapper, 'Salgo ahora').trigger('click')
    await flushPromises()

    expect(postApunte).toHaveBeenCalledWith(expect.objectContaining({ tipo: 'SALIDA' }))
  })

  it('con hora manual manda la hora elegida', async () => {
    vi.mocked(postApunte).mockResolvedValue({ ...apunteEntrada, tipo: 'SALIDA', hora: '23:45' })
    const wrapper = await montar()

    await boton(wrapper, 'Registrar el turno manualmente').trigger('click')
    await wrapper.find('#hora-manual').setValue('23:45')
    await boton(wrapper, 'Salida a esa hora').trigger('click')
    await flushPromises()

    expect(postApunte).toHaveBeenCalledWith(
      expect.objectContaining({ tipo: 'SALIDA', hora: '23:45' }),
    )
  })

  it('los toggles de excepción anuncian su estado y se pliegan tras un apunte con éxito', async () => {
    vi.mocked(postApunte).mockResolvedValue({ ...apunteEntrada, hora: '09:00' })
    const wrapper = await montar()

    const toggle = () => boton(wrapper, 'Registrar el turno manualmente')
    expect(toggle().attributes('aria-expanded')).toBe('false')
    expect(toggle().attributes('aria-controls')).toBe('panel-hora-manual')

    await toggle().trigger('click')
    expect(toggle().attributes('aria-expanded')).toBe('true')

    // Tras fichar con éxito el panel se recoge, y el botón lo cuenta.
    await wrapper.find('#hora-manual').setValue('09:00')
    await formularioDe(wrapper, '#hora-manual').trigger('submit')
    await flushPromises()

    expect(toggle().attributes('aria-expanded')).toBe('false')
    // El aria-controls del otro toggle también apunta a su panel.
    expect(boton(wrapper, 'No he ido').attributes('aria-controls')).toBe('panel-ausencia')
  })

  it('los enlaces del pie viven en un nav con nombre accesible', async () => {
    const wrapper = await montar()

    expect(wrapper.find('nav.pie-enlaces').attributes('aria-label')).toBe('Ir a otras pantallas')
  })

  it('en el panel de hora manual, Intro (submit del form) ficha una ENTRADA', async () => {
    vi.mocked(postApunte).mockResolvedValue({ ...apunteEntrada, hora: '09:00' })
    const wrapper = await montar()

    await boton(wrapper, 'Registrar el turno manualmente').trigger('click')
    await wrapper.find('#hora-manual').setValue('09:00')
    await formularioDe(wrapper, '#hora-manual').trigger('submit')
    await flushPromises()

    expect(postApunte).toHaveBeenCalledWith(
      expect.objectContaining({ tipo: 'ENTRADA', hora: '09:00' }),
    )
  })

  it('doble submit: con el POST en vuelo los botones quedan deshabilitados', async () => {
    let resolverPost!: (a: ApunteGuardado) => void
    vi.mocked(postApunte).mockReturnValue(
      new Promise((resolve) => {
        resolverPost = resolve
      }),
    )
    const wrapper = await montar()

    await boton(wrapper, 'Entro ahora').trigger('click')

    expect(boton(wrapper, 'Entro ahora').attributes('disabled')).toBeDefined()
    expect(boton(wrapper, 'Salgo ahora').attributes('disabled')).toBeDefined()

    await boton(wrapper, 'Salgo ahora').trigger('click')
    expect(postApunte).toHaveBeenCalledTimes(1)

    resolverPost(apunteEntrada)
    await flushPromises()
    expect(boton(wrapper, 'Entro ahora').attributes('disabled')).toBeUndefined()
  })
})

describe('LibretaView — ausencia', () => {
  it('el panel avisa de la privacidad del motivo y sin texto manda motivo null', async () => {
    vi.mocked(postApunte).mockResolvedValue({
      ...apunteEntrada,
      tipo: 'AUSENCIA',
      hora: null,
    })
    const wrapper = await montar()

    await boton(wrapper, 'No he ido').trigger('click')

    expect(wrapper.text()).toContain('El motivo es opcional; si lo escribes, queda en tu libreta.')

    // El panel es un <form>: registrar (botón submit o Intro) dispara el submit.
    await formularioDe(wrapper, '#motivo').trigger('submit')
    await flushPromises()

    expect(postApunte).toHaveBeenCalledWith({
      fecha: '2026-07-08',
      tipo: 'AUSENCIA',
      hora: null,
      motivo: null,
      rectificacionTardiaConfirmada: false,
    })
  })

  it('con motivo escrito lo manda tal cual (recortado)', async () => {
    vi.mocked(postApunte).mockResolvedValue({
      ...apunteEntrada,
      tipo: 'AUSENCIA',
      hora: null,
      motivo: 'médico',
    })
    const wrapper = await montar()

    await boton(wrapper, 'No he ido').trigger('click')
    await wrapper.find('#motivo').setValue('  médico  ')
    await formularioDe(wrapper, '#motivo').trigger('submit')
    await flushPromises()

    expect(postApunte).toHaveBeenCalledWith(expect.objectContaining({ motivo: 'médico' }))
  })
})

describe('LibretaView — día sellado (409)', () => {
  it('explica el sellado y solo reenvía como rectificación tardía tras confirmar', async () => {
    vi.mocked(postApunte).mockRejectedValueOnce(
      new ApiError(409, 'API 409', {
        status: 409,
        detail: 'El día 2026-07-08 ya quedó protegido (pasados 14 días): solo cabe una rectificación tardía',
      }),
    )
    vi.mocked(postApunte).mockResolvedValueOnce({
      ...apunteEntrada,
      origen: 'RECTIFICACION_TARDIA',
    })
    const wrapper = await montar()

    await boton(wrapper, 'Entro ahora').trigger('click')
    await flushPromises()

    // La explicación en cristiano, con la fricción del checkbox.
    expect(wrapper.text()).toContain('Este día ya quedó protegido')
    expect(wrapper.text()).toContain('rectificación tardía')
    expect(wrapper.text()).toContain('lo ya protegido no se toca')
    const confirmar = boton(wrapper, 'Registrar la rectificación')
    expect(confirmar.attributes('disabled')).toBeDefined()

    await wrapper.find('input[type="checkbox"]').setValue(true)
    expect(boton(wrapper, 'Registrar la rectificación').attributes('disabled')).toBeUndefined()

    await boton(wrapper, 'Registrar la rectificación').trigger('click')
    await flushPromises()

    expect(postApunte).toHaveBeenLastCalledWith(
      expect.objectContaining({ tipo: 'ENTRADA', rectificacionTardiaConfirmada: true }),
    )
    // Tras el éxito, el panel de rectificación desaparece.
    expect(wrapper.text()).not.toContain('Este día ya quedó protegido')
  })
})

describe('LibretaView — un día concreto (desde "Tu semana")', () => {
  it('carga la fecha de la URL, esconde el "ahora" y abre la hora manual con su aviso', async () => {
    vi.mocked(getEstadoDia).mockResolvedValue({
      ...diaServidor,
      fecha: '2026-07-01',
      estado: 'HUECO',
      apuntes: [],
    })

    const wrapper = await montar('/libreta/dia/2026-07-01')

    expect(getEstadoDia).toHaveBeenCalledWith('2026-07-01')
    // En un día pasado no existe el "ahora": solo hora manual y ausencia.
    expect(wrapper.findAll('button').some((b) => b.text() === 'Entro ahora')).toBe(false)
    expect(wrapper.text()).toContain('queda marcado como reconstruido')
    // El panel de hora manual llega abierto: apuntar la hora es el único camino.
    const plegable = wrapper.get('#hora-manual').element.closest('.plegable')
    expect(plegable?.classList.contains('abierto')).toBe(true)
    // Y hay vuelta a hoy.
    const hrefs = wrapper.findAll('a').map((a) => a.attributes('href'))
    expect(hrefs).toContain('/libreta')
  })

  it('una fecha inválida en la URL cae a hoy sin romper nada', async () => {
    await montar('/libreta/dia/patata')

    expect(getEstadoDia).toHaveBeenCalledWith(hoyIso())
  })

  it('el 30 de febrero pasa la regex pero no existe: cae a hoy', async () => {
    await montar('/libreta/dia/2026-02-30')

    expect(getEstadoDia).toHaveBeenCalledWith(hoyIso())
  })

  it('un día futuro no se ficha: mensaje honesto y sin paneles', async () => {
    vi.mocked(getEstadoDia).mockResolvedValue({
      ...diaServidor,
      fecha: '2999-01-01',
      estado: 'PENDIENTE',
      apuntes: [],
    })

    const wrapper = await montar('/libreta/dia/2999-01-01')

    expect(wrapper.text()).toContain('Este día aún no ha llegado')
    expect(wrapper.findAll('button').some((b) => b.text() === 'Entro ahora')).toBe(false)
    expect(wrapper.find('#hora-manual').exists()).toBe(false)
    expect(wrapper.findAll('button').some((b) => b.text() === 'No he ido')).toBe(false)
  })

  it('al volver a hoy, el panel de hora manual se recoge y vuelven los botones de ahora', async () => {
    const { wrapper, router } = await montarConRouter('/libreta/dia/2026-07-01')

    // En el día pasado el panel llegó abierto.
    let plegable = wrapper.get('#hora-manual').element.closest('.plegable')
    expect(plegable?.classList.contains('abierto')).toBe(true)

    await router.push('/libreta')
    await flushPromises()

    // De vuelta a hoy: los "ahora" reaparecen y el panel está recogido (el
    // router reutiliza el componente, los refs sobreviven a la navegación).
    expect(wrapper.findAll('button').some((b) => b.text() === 'Entro ahora')).toBe(true)
    plegable = wrapper.get('#hora-manual').element.closest('.plegable')
    expect(plegable?.classList.contains('abierto')).toBe(false)
  })
})

describe('LibretaView — onboarding', () => {
  it('se muestra solo la primera vez y al cerrarlo guarda el flag', async () => {
    localStorage.removeItem(CLAVE_ONBOARDING_LIBRETA)
    const wrapper = await montar()

    expect(wrapper.text()).toContain('Fichar al momento vale más')

    await boton(wrapper, 'Siguiente').trigger('click')
    await boton(wrapper, 'Siguiente').trigger('click')
    await boton(wrapper, 'Empezar a fichar').trigger('click')

    expect(localStorage.getItem(CLAVE_ONBOARDING_LIBRETA)).toBe('1')
    expect(wrapper.text()).toContain('Entro ahora')
  })

  it('con el flag ya guardado no se muestra, pero se puede volver a ver', async () => {
    const wrapper = await montar()

    expect(wrapper.text()).not.toContain('Fichar al momento vale más')

    await boton(wrapper, '¿Cómo funciona la libreta?').trigger('click')

    expect(wrapper.text()).toContain('Fichar al momento vale más')
  })
})
