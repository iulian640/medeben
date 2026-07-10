import { beforeEach, describe, expect, it, vi } from 'vitest'
import { createPinia, setActivePinia } from 'pinia'
import { ApiError } from '../services/api'
import type { ConvenioResumen, OcupacionResuelta, SalarioBase } from '../services/convenios'
import { usePerfilStore } from './perfil'

vi.mock('../services/convenios', () => ({
  getProvincias: vi.fn(),
  getConvenioParaTrabajador: vi.fn(),
  getPuestos: vi.fn(),
  getOcupacion: vi.fn(),
  postSalarioBase: vi.fn(),
  postHorasExtra: vi.fn(),
}))

import {
  getConvenioParaTrabajador,
  getOcupacion,
  getProvincias,
  getPuestos,
  postSalarioBase,
} from '../services/convenios'

const convenioMadrid: ConvenioResumen = {
  id: 'madrid-hosteleria',
  nombre: 'Convenio de Hostelería de Madrid',
  subsector: 'hosteleria',
  ambitoTipo: 'provincial',
  provincias: ['Madrid'],
  vigenciaDesde: '2024-01-01',
  vigenciaHasta: '2025-12-31',
  fuenteUrl: 'https://www.bocm.es/ejemplo.pdf',
  estado: 'vigente',
}

const ocupacionConPendiente: OcupacionResuelta = {
  dimensiones: { nivel: 'III' },
  pendientes: [{ dimension: 'claseEmpresa', valores: ['A', 'B', 'C'] }],
  articulo: 'Art. 12',
}

const salario: SalarioBase = {
  importe: 1425.5,
  unidad: 'EUR/mes',
  bajoSmi: false,
  smiMensual: 1221,
  minimoLegal: null,
  comparativaSmi: null,
  citas: [{ texto: 'Salario base...', url: 'https://boe.es/x' }],
}

beforeEach(() => {
  setActivePinia(createPinia())
  vi.clearAllMocks()
})

describe('perfil store', () => {
  it('carga provincias una vez', async () => {
    vi.mocked(getProvincias).mockResolvedValue(['Madrid', 'Cuenca'])
    const store = usePerfilStore()

    await store.cargarProvincias()
    await store.cargarProvincias()

    expect(store.provincias).toEqual(['Madrid', 'Cuenca'])
    expect(getProvincias).toHaveBeenCalledTimes(1)
  })

  it('limpiar() borra los datos personales (dispositivo compartido, review de seguridad)', async () => {
    vi.mocked(getConvenioParaTrabajador).mockResolvedValue(convenioMadrid)
    vi.mocked(getPuestos).mockResolvedValue([{ id: 'cocinero', etiqueta: 'Cocinero/a' }])
    const store = usePerfilStore()
    store.provincia = 'Madrid'
    await store.elegirSubsector('hosteleria')
    expect(store.convenio).not.toBeNull()

    store.limpiar()

    expect(store.provincia).toBeNull()
    expect(store.subsector).toBeNull()
    expect(store.convenio).toBeNull()
    expect(store.puestos).toEqual([])
    expect(store.puestoId).toBeNull()
    expect(store.salario).toBeNull()
  })

  it('busca el convenio cuando hay provincia y subsector', async () => {
    vi.mocked(getConvenioParaTrabajador).mockResolvedValue(convenioMadrid)
    vi.mocked(getPuestos).mockResolvedValue([{ id: 'cocinero', etiqueta: 'Cocinero/a' }])
    const store = usePerfilStore()

    store.provincia = 'Madrid'
    await store.elegirSubsector('hosteleria')

    expect(getConvenioParaTrabajador).toHaveBeenCalledWith('Madrid', 'hosteleria')
    expect(store.convenio).toEqual(convenioMadrid)
    expect(store.puestos).toHaveLength(1)
  })

  it('guarda el detail RFC 7807 como error legible si no hay convenio', async () => {
    vi.mocked(getConvenioParaTrabajador).mockRejectedValue(
      new ApiError(404, 'API 404', { status: 404, detail: 'No hay convenio para esa provincia' }),
    )
    const store = usePerfilStore()

    store.provincia = 'Narnia'
    await store.elegirSubsector('hosteleria')

    expect(store.convenio).toBeNull()
    expect(store.error).toBe('No hay convenio para esa provincia')
  })

  it('al elegir puesto con pregunta pendiente NO calcula el salario todavía', async () => {
    vi.mocked(getOcupacion).mockResolvedValue(ocupacionConPendiente)
    const store = usePerfilStore()
    store.convenio = convenioMadrid

    await store.elegirPuesto('cocinero')

    expect(store.ocupacion).toEqual(ocupacionConPendiente)
    expect(store.pendientesSinResponder).toHaveLength(1)
    expect(postSalarioBase).not.toHaveBeenCalled()
  })

  it('al responder la pendiente re-resuelve y calcula el salario con las dimensiones ya resueltas', async () => {
    // 1ª resolución: pendiente la clase de empresa. Al responderla, el backend la
    // pliega en las dimensiones (re-resolución) y ya no queda pregunta.
    vi.mocked(getOcupacion)
      .mockResolvedValueOnce(ocupacionConPendiente)
      .mockResolvedValueOnce({
        dimensiones: { nivel: 'III', claseEmpresa: 'B' },
        pendientes: [],
        articulo: null,
      })
    vi.mocked(postSalarioBase).mockResolvedValue(salario)
    const store = usePerfilStore()
    store.convenio = convenioMadrid

    await store.elegirPuesto('cocinero')
    await store.responderPendiente('claseEmpresa', 'B')

    // Re-resuelto con la respuesta acumulada y calculado con las dimensiones del backend.
    expect(getOcupacion).toHaveBeenLastCalledWith('madrid-hosteleria', 'cocinero', {
      claseEmpresa: 'B',
    })
    expect(postSalarioBase).toHaveBeenCalledWith(
      'madrid-hosteleria',
      { nivel: 'III', claseEmpresa: 'B' },
      expect.stringMatching(/^\d{4}-\d{2}-\d{2}$/),
    )
    expect(store.salario).toEqual(salario)
  })

  it('si la re-resolución falla, NO confirma la respuesta ni oculta la pregunta (reintentable)', async () => {
    vi.mocked(getOcupacion)
      .mockResolvedValueOnce(ocupacionConPendiente)
      .mockRejectedValueOnce(new ApiError(500, 'boom'))
    const store = usePerfilStore()
    store.convenio = convenioMadrid

    await store.elegirPuesto('cocinero')
    await store.responderPendiente('claseEmpresa', 'B')

    expect(store.error).not.toBeNull()
    // La respuesta no se confirmó y la pregunta sigue en pantalla para reintentar.
    expect(store.respuestas).toEqual({})
    expect(store.pendientesSinResponder).toHaveLength(1)
    expect(postSalarioBase).not.toHaveBeenCalled()
  })

  it('sin pendientes calcula el salario directamente al elegir puesto', async () => {
    vi.mocked(getOcupacion).mockResolvedValue({
      dimensiones: { nivel: 'II' },
      pendientes: [],
      articulo: 'Art. 9',
    })
    vi.mocked(postSalarioBase).mockResolvedValue(salario)
    const store = usePerfilStore()
    store.convenio = convenioMadrid

    await store.elegirPuesto('camarero')

    expect(postSalarioBase).toHaveBeenCalledTimes(1)
    expect(store.salario).toEqual(salario)
  })

  it('marca puestoNoMapeado en un 404 al resolver el puesto', async () => {
    vi.mocked(getOcupacion).mockRejectedValue(new ApiError(404, 'API 404', { status: 404 }))
    const store = usePerfilStore()
    store.convenio = convenioMadrid

    await store.elegirPuesto('camarera-pisos')

    expect(store.puestoNoMapeado).toBe(true)
    expect(store.error).toBeNull()
  })

  it('prellena el salario mensual para horas extra solo si la unidad es EUR/mes', async () => {
    const store = usePerfilStore()

    store.salario = salario
    expect(store.salarioMensualPrefill).toBe(1425.5)

    store.salario = { ...salario, unidad: 'EUR/año' }
    expect(store.salarioMensualPrefill).toBeNull()
  })

  it('con la tabla bajo el SMI, el prellenado usa el suelo legal, no la tabla superada', async () => {
    const store = usePerfilStore()

    store.salario = { ...salario, importe: 1066.61, bajoSmi: true, minimoLegal: 1221 }

    // El valor hora de la calculadora debe partir del mínimo que la ley
    // garantiza; con la tabla vieja saldría infravalorado.
    expect(store.salarioMensualPrefill).toBe(1221)
  })

  it('bajoSmi sin minimoLegal (backend viejo): mejor no prellenar que infravalorar', async () => {
    const store = usePerfilStore()

    store.salario = { ...salario, importe: 1066.61, bajoSmi: true, minimoLegal: null }

    expect(store.salarioMensualPrefill).toBeNull()
  })

  it('cambiar de provincia resetea convenio, puesto y salario', async () => {
    vi.mocked(getConvenioParaTrabajador).mockResolvedValue(convenioMadrid)
    vi.mocked(getPuestos).mockResolvedValue([])
    vi.mocked(getOcupacion).mockResolvedValue(ocupacionConPendiente)
    const store = usePerfilStore()

    store.provincia = 'Madrid'
    await store.elegirSubsector('hosteleria')
    await store.elegirPuesto('cocinero')

    await store.elegirProvincia('Cuenca')

    expect(store.puestoId).toBeNull()
    expect(store.ocupacion).toBeNull()
    expect(store.salario).toBeNull()
    expect(store.subsector).toBeNull()
  })

  it('ignora la respuesta vieja si el usuario cambia de puesto antes de que llegue', async () => {
    // Carrera: la petición del primer puesto resuelve DESPUÉS que la del segundo.
    let resolverVieja!: (v: OcupacionResuelta) => void
    const respuestaVieja = new Promise<OcupacionResuelta>((r) => {
      resolverVieja = r
    })
    vi.mocked(getOcupacion)
      .mockReturnValueOnce(respuestaVieja)
      .mockResolvedValueOnce({ dimensiones: { nivel: 'II' }, pendientes: [], articulo: 'Art. 9' })
    vi.mocked(postSalarioBase).mockResolvedValue(salario)
    const store = usePerfilStore()
    store.convenio = convenioMadrid

    const primera = store.elegirPuesto('cocinero')
    const segunda = store.elegirPuesto('camarero')
    await segunda
    resolverVieja(ocupacionConPendiente)
    await primera

    // Gana la selección más nueva: nada del cocinero pisa al camarero.
    expect(store.puestoId).toBe('camarero')
    expect(store.ocupacion?.dimensiones).toEqual({ nivel: 'II' })
    expect(store.pendientesSinResponder).toHaveLength(0)
    expect(store.salario).toEqual(salario)
    expect(store.cargando).toBe(false)
  })

  it('ignora el convenio viejo si el usuario cambia de subsector antes de que llegue', async () => {
    let resolverVieja!: (v: ConvenioResumen) => void
    const respuestaVieja = new Promise<ConvenioResumen>((r) => {
      resolverVieja = r
    })
    const convenioHospedaje = { ...convenioMadrid, id: 'madrid-hospedaje', subsector: 'hospedaje' }
    vi.mocked(getConvenioParaTrabajador)
      .mockReturnValueOnce(respuestaVieja)
      .mockResolvedValueOnce(convenioHospedaje)
    vi.mocked(getPuestos).mockResolvedValue([])
    const store = usePerfilStore()
    store.provincia = 'Madrid'

    const primera = store.elegirSubsector('hosteleria')
    const segunda = store.elegirSubsector('hospedaje')
    await segunda
    resolverVieja(convenioMadrid)
    await primera

    expect(store.subsector).toBe('hospedaje')
    expect(store.convenio).toEqual(convenioHospedaje)
    expect(store.cargando).toBe(false)
  })
})
