import { afterEach, describe, expect, it, vi } from 'vitest'
import { api, ApiError, getHealth, setAuthToken, setOnUnauthorized } from './api'

function mockFetch(response: Partial<Response> & { jsonValue?: unknown }) {
  const fetchMock = vi.fn().mockResolvedValue({
    ok: response.ok ?? true,
    status: response.status ?? 200,
    statusText: response.statusText ?? 'OK',
    headers: response.headers ?? new Headers(),
    json: async () => response.jsonValue ?? {},
  } as Response)
  vi.stubGlobal('fetch', fetchMock)
  return fetchMock
}

afterEach(() => {
  vi.unstubAllGlobals()
  vi.restoreAllMocks()
  setAuthToken(null)
  setOnUnauthorized(null)
})

describe('api client', () => {
  it('sends the default Content-Type on a plain GET', async () => {
    const fetchMock = mockFetch({ jsonValue: { status: 'ok' } })

    await getHealth()

    const [, init] = fetchMock.mock.calls[0]
    expect((init.headers as Record<string, string>)['Content-Type']).toBe('application/json')
  })

  it('keeps the default Content-Type when the caller adds their own header', async () => {
    // Regression: spreading options after headers used to clobber Content-Type,
    // silently breaking every authenticated (Bearer) request once JWT lands.
    const fetchMock = mockFetch({ jsonValue: {} })

    await api.get('/whatever', { headers: { Authorization: 'Bearer token' } })

    const [, init] = fetchMock.mock.calls[0]
    const headers = init.headers as Record<string, string>
    expect(headers['Content-Type']).toBe('application/json')
    expect(headers['Authorization']).toBe('Bearer token')
  })

  it('does not clobber method/body when merging headers', async () => {
    const fetchMock = mockFetch({ jsonValue: {} })

    await api.post('/x', { a: 1 })

    const [, init] = fetchMock.mock.calls[0]
    expect(init.method).toBe('POST')
    expect(init.body).toBe(JSON.stringify({ a: 1 }))
  })

  it('throws ApiError with the backend message on a non-ok response', async () => {
    mockFetch({
      ok: false,
      status: 422,
      statusText: 'Unprocessable Entity',
      jsonValue: { message: 'convenio no encontrado' },
    })

    await expect(getHealth()).rejects.toMatchObject({
      name: 'ApiError',
      status: 422,
      message: 'convenio no encontrado',
    })
    await expect(getHealth()).rejects.toBeInstanceOf(ApiError)
  })

  it('returns undefined on a 204 No Content instead of parsing an empty body', async () => {
    mockFetch({ status: 204, headers: new Headers({ 'Content-Length': '0' }) })

    await expect(api.delete('/x')).resolves.toBeUndefined()
  })
})

describe('api client auth token', () => {
  it('attaches Authorization: Bearer when a token is set', async () => {
    const fetchMock = mockFetch({ jsonValue: {} })
    setAuthToken('mi-jwt')

    await api.get('/perfil')

    const [, init] = fetchMock.mock.calls[0]
    expect((init.headers as Record<string, string>)['Authorization']).toBe('Bearer mi-jwt')
  })

  it('sends no Authorization header when there is no token', async () => {
    const fetchMock = mockFetch({ jsonValue: {} })

    await api.get('/provincias')

    const [, init] = fetchMock.mock.calls[0]
    expect((init.headers as Record<string, string>)['Authorization']).toBeUndefined()
  })

  it('stops attaching the token after clearing it', async () => {
    const fetchMock = mockFetch({ jsonValue: {} })
    setAuthToken('mi-jwt')
    setAuthToken(null)

    await api.get('/provincias')

    const [, init] = fetchMock.mock.calls[0]
    expect((init.headers as Record<string, string>)['Authorization']).toBeUndefined()
  })

  it('notifies the unauthorized handler on a 401 from an authenticated request', async () => {
    mockFetch({ ok: false, status: 401, statusText: 'Unauthorized', jsonValue: null })
    const onUnauthorized = vi.fn()
    setAuthToken('jwt-caducado')
    setOnUnauthorized(onUnauthorized)

    await expect(api.get('/perfil')).rejects.toBeInstanceOf(ApiError)
    expect(onUnauthorized).toHaveBeenCalledTimes(1)
  })

  it('does NOT notify the handler on a 401 without token (failed login is not an expired session)', async () => {
    mockFetch({ ok: false, status: 401, statusText: 'Unauthorized', jsonValue: null })
    const onUnauthorized = vi.fn()
    setOnUnauthorized(onUnauthorized)

    await expect(api.post('/auth/login', { email: 'a@b.c', password: 'x' })).rejects.toBeInstanceOf(
      ApiError,
    )
    expect(onUnauthorized).not.toHaveBeenCalled()
  })

  it('does NOT notify the handler on non-401 errors', async () => {
    mockFetch({ ok: false, status: 404, statusText: 'Not Found', jsonValue: null })
    const onUnauthorized = vi.fn()
    setAuthToken('mi-jwt')
    setOnUnauthorized(onUnauthorized)

    await expect(api.get('/perfil')).rejects.toBeInstanceOf(ApiError)
    expect(onUnauthorized).not.toHaveBeenCalled()
  })
})
