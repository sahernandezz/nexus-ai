import { apiClient } from './axiosInstance'
import type { LoginRequest, LoginResponse } from '@/types/Auth'
import type { HealthResponse } from '@/types/Api'

export const authApi = {
  login: (data: LoginRequest) =>
    apiClient.post<LoginResponse>('/auth/login', data).then((r) => r.data),

  refresh: (refreshToken: string) =>
    apiClient
      .post<LoginResponse>('/auth/refresh', { refreshToken })
      .then((r) => r.data),

  me: () =>
    apiClient.get<{ username: string }>('/auth/me').then((r) => r.data),
}

export const healthApi = {
  check: () =>
    apiClient.get<HealthResponse>('/api/health').then((r) => r.data),
}

