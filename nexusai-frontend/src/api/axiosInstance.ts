import axios, {
  type AxiosInstance,
  type AxiosRequestConfig,
  type InternalAxiosRequestConfig,
} from 'axios'

// ─── Token helpers ────────────────────────────────────────────────────────────
const TOKEN_KEY   = 'nexusai_access_token'
const REFRESH_KEY = 'nexusai_refresh_token'

export const tokenStorage = {
  getAccess:      ()       => localStorage.getItem(TOKEN_KEY),
  setAccess:       (t: string) => localStorage.setItem(TOKEN_KEY, t),
  getRefresh:      ()       => localStorage.getItem(REFRESH_KEY),
  setRefresh:      (t: string) => localStorage.setItem(REFRESH_KEY, t),
  clear:           ()       => {
    localStorage.removeItem(TOKEN_KEY)
    localStorage.removeItem(REFRESH_KEY)
  },
}

// ─── Axios instance ───────────────────────────────────────────────────────────
const apiClient: AxiosInstance = axios.create({
  baseURL: import.meta.env.VITE_API_URL ?? '',
  timeout: 30_000,
  headers: {
    'Content-Type': 'application/json',
    Accept: 'application/json',
  },
})

// ─── Request interceptor — attach Bearer token ────────────────────────────────
apiClient.interceptors.request.use(
  (config: InternalAxiosRequestConfig) => {
    const token = tokenStorage.getAccess()
    if (token) {
      config.headers.Authorization = `Bearer ${token}`
    }
    return config
  },
  (error) => Promise.reject(error),
)

// ─── Response interceptor — transparent token refresh ────────────────────────
let isRefreshing = false
let failedQueue: Array<{
  resolve: (value: string) => void
  reject: (reason?: unknown) => void
}> = []

const processQueue = (error: unknown | null, token: string | null = null) => {
  failedQueue.forEach(({ resolve, reject }) => {
    if (error) {
      reject(error)
    } else {
      resolve(token!)
    }
  })
  failedQueue = []
}

apiClient.interceptors.response.use(
  (response) => response,
  async (error) => {
    const originalRequest = error.config as AxiosRequestConfig & { _retry?: boolean }

    if (error.response?.status === 401 && !originalRequest._retry) {
      const refreshToken = tokenStorage.getRefresh()

      if (!refreshToken) {
        // No refresh token — redirect to login
        tokenStorage.clear()
        window.location.href = '/login'
        return Promise.reject(error)
      }

      if (isRefreshing) {
        return new Promise<string>((resolve, reject) => {
          failedQueue.push({ resolve, reject })
        }).then((token) => {
          if (originalRequest.headers) {
            (originalRequest.headers as Record<string, string>).Authorization = `Bearer ${token}`
          }
          return apiClient(originalRequest)
        })
      }

      originalRequest._retry = true
      isRefreshing = true

      try {
        const { data } = await axios.post<{ accessToken: string; refreshToken: string }>(
          `${import.meta.env.VITE_API_URL ?? ''}/auth/refresh`,
          { refreshToken },
        )

        tokenStorage.setAccess(data.accessToken)
        tokenStorage.setRefresh(data.refreshToken)
        processQueue(null, data.accessToken)

        if (originalRequest.headers) {
          (originalRequest.headers as Record<string, string>).Authorization = `Bearer ${data.accessToken}`
        }
        return apiClient(originalRequest)
      } catch (refreshError) {
        processQueue(refreshError, null)
        tokenStorage.clear()
        window.location.href = '/login'
        return Promise.reject(refreshError)
      } finally {
        isRefreshing = false
      }
    }

    return Promise.reject(error)
  },
)

export { apiClient }

