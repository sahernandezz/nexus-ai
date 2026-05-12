// ─── Auth Types ───────────────────────────────────────────────────────────────

export interface LoginRequest {
  username: string
  password: string
}

export interface LoginResponse {
  accessToken: string
  refreshToken: string
  tokenType: string
  expiresIn: number
  username: string
  roles: string[]
}

export interface RefreshRequest {
  refreshToken: string
}

export type UserRole = 'ADMIN' | 'USER' | 'AGENT'

export interface AuthUser {
  username: string
  roles: UserRole[]
}

