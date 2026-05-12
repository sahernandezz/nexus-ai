import axios, { type AxiosError } from 'axios'

interface BackendErrorBody {
  error?: string
  message?: string
  msg?: string
}

/**
 * Turns an unknown error from a network call into a human-readable Spanish
 * message suitable for showing to the user.
 *
 * Priority:
 *   1. Backend body's {error|message|msg} field (we control these on the server)
 *   2. Status-code mapping (401, 403, 404, 5xx, etc.)
 *   3. Axios transport codes (network down, aborted, timeout)
 *   4. The error's own .message
 *   5. Generic fallback
 */
export function describeError(err: unknown): string {
  if (axios.isAxiosError(err)) {
    return describeAxios(err as AxiosError<BackendErrorBody>)
  }
  if (err instanceof Error) return err.message || 'Error inesperado.'
  if (typeof err === 'string') return err
  return 'Error inesperado.'
}

function describeAxios(err: AxiosError<BackendErrorBody>): string {
  // 1. Server-provided message (handles validation errors with field-specific text)
  const data = err.response?.data
  if (data) {
    if (typeof data === 'string') return data
    if (typeof data === 'object') {
      const serverMsg = data.error ?? data.message ?? data.msg
      if (serverMsg && typeof serverMsg === 'string') return translateKnownMessage(serverMsg)
    }
  }

  // 2. Transport-level errors (no HTTP response at all)
  if (err.code === 'ECONNABORTED' || err.message?.includes('timeout')) {
    return 'La solicitud tardo demasiado. Intentalo de nuevo.'
  }
  if (err.code === 'ERR_NETWORK') {
    return 'No se puede conectar con el servidor. Comprueba tu conexion.'
  }
  if (err.code === 'ERR_CANCELED') {
    return 'Operacion cancelada.'
  }

  // 3. Status-code defaults
  switch (err.response?.status) {
    case 400: return 'Datos invalidos. Revisa lo que has enviado.'
    case 401: return 'Usuario o contrasena incorrectos.'
    case 403: return 'No tienes permisos para realizar esta accion.'
    case 404: return 'Recurso no encontrado.'
    case 408: return 'Tiempo de espera agotado.'
    case 409: return 'Conflicto con el estado actual.'
    case 413: return 'El archivo es demasiado grande.'
    case 415: return 'Formato de archivo no soportado.'
    case 422: return 'Datos no procesables.'
    case 429: return 'Demasiadas solicitudes. Espera un momento.'
    case 500: return 'Error interno del servidor.'
    case 502:
    case 503:
    case 504: return 'El servidor no esta disponible. Intenta de nuevo en unos momentos.'
  }

  return err.message || 'Error inesperado.'
}

/**
 * Translate the most common English server messages into Spanish.
 * Falls back to the raw message if not recognized.
 */
function translateKnownMessage(msg: string): string {
  const map: Record<string, string> = {
    'Invalid username or password': 'Usuario o contrasena incorrectos.',
    'username is required':         'El usuario es obligatorio.',
    'password is required':         'La contrasena es obligatoria.',
    'password must be at least 6 characters':
                                    'La contrasena debe tener al menos 6 caracteres.',
    'message must not be blank':    'El mensaje no puede estar vacio.',
    'message exceeds maximum length':
                                    'El mensaje supera la longitud maxima.',
    'Invalid or expired refresh token':
                                    'Tu sesion expiro. Vuelve a iniciar sesion.',
    'refreshToken is required':     'Falta el token de refresco.',
    'invalid id':                   'Identificador invalido.',
    "Expected a multipart field named 'file'":
                                    'Falta el archivo en la solicitud.',
    "Expected multipart field 'file'":
                                    'Falta el archivo en la solicitud.',
  }
  return map[msg] ?? msg
}
