import { describe, expect, it } from 'vitest'
import { AxiosError, AxiosHeaders } from 'axios'
import { describeError } from './errorMessage'

/**
 * Helper to build a fake axios error with a body + status, matching the
 * shape `axios.isAxiosError()` checks for.
 */
function axiosErr(opts: {
  status?: number
  data?: unknown
  code?: string
  message?: string
}): AxiosError {
  const headers = new AxiosHeaders()
  const err = new AxiosError(
    opts.message ?? `Request failed with status code ${opts.status ?? 0}`,
    opts.code,
    { headers } as never,
    null,
    opts.status
      ? {
          status: opts.status,
          statusText: '',
          data: opts.data ?? {},
          headers,
          config: { headers } as never,
        }
      : undefined,
  )
  return err
}

describe('describeError', () => {
  // ── Backend body wins over status ───────────────────────────────────────────

  it('uses the body.error field when present', () => {
    const err = axiosErr({ status: 400, data: { error: 'la cosa fallo' } })
    expect(describeError(err)).toBe('la cosa fallo')
  })

  it('translates the validation message for short password', () => {
    const err = axiosErr({
      status: 400,
      data: { error: 'password must be at least 6 characters' },
    })
    expect(describeError(err)).toBe(
      'La contrasena debe tener al menos 6 caracteres.',
    )
  })

  it('translates "Invalid username or password"', () => {
    const err = axiosErr({
      status: 401,
      data: { error: 'Invalid username or password' },
    })
    expect(describeError(err)).toBe('Usuario o contrasena incorrectos.')
  })

  it('falls back to body.message when body.error missing', () => {
    const err = axiosErr({ status: 500, data: { message: 'oops' } })
    expect(describeError(err)).toBe('oops')
  })

  it('handles a plain-string body', () => {
    const err = axiosErr({ status: 500, data: 'boom' })
    expect(describeError(err)).toBe('boom')
  })

  // ── Status-code mapping ───────────────────────────────────────────────────

  it.each<[number, string]>([
    [400, 'Datos invalidos. Revisa lo que has enviado.'],
    [401, 'Usuario o contrasena incorrectos.'],
    [403, 'No tienes permisos para realizar esta accion.'],
    [404, 'Recurso no encontrado.'],
    [413, 'El archivo es demasiado grande.'],
    [429, 'Demasiadas solicitudes. Espera un momento.'],
    [500, 'Error interno del servidor.'],
    [503, 'El servidor no esta disponible. Intenta de nuevo en unos momentos.'],
  ])('maps status %i to friendly message', (status, expected) => {
    const err = axiosErr({ status, data: {} })
    expect(describeError(err)).toBe(expected)
  })

  // ── Transport errors (no HTTP response) ───────────────────────────────────

  it('returns a connectivity hint for ERR_NETWORK', () => {
    const err = axiosErr({ code: 'ERR_NETWORK', message: 'network down' })
    expect(describeError(err)).toContain('No se puede conectar')
  })

  it('returns a timeout hint for ECONNABORTED', () => {
    const err = axiosErr({ code: 'ECONNABORTED', message: 'timeout of 30000ms exceeded' })
    expect(describeError(err)).toContain('tardo demasiado')
  })

  it('handles cancellation', () => {
    const err = axiosErr({ code: 'ERR_CANCELED', message: 'canceled' })
    expect(describeError(err)).toBe('Operacion cancelada.')
  })

  // ── Non-axios inputs ──────────────────────────────────────────────────────

  it('uses Error.message for non-axios errors', () => {
    expect(describeError(new Error('vanilla error'))).toBe('vanilla error')
  })

  it('handles plain strings', () => {
    expect(describeError('something broke')).toBe('something broke')
  })

  it('falls back for unknown types', () => {
    expect(describeError({ weird: 'shape' })).toBe('Error inesperado.')
    expect(describeError(null)).toBe('Error inesperado.')
    expect(describeError(undefined)).toBe('Error inesperado.')
  })

  it('returns the Error.message even when empty falls back to generic', () => {
    expect(describeError(new Error(''))).toBe('Error inesperado.')
  })
})
