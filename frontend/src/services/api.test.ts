import { afterEach, describe, expect, it, vi } from 'vitest'
import { api, ApiError, getHealth } from './api'

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
