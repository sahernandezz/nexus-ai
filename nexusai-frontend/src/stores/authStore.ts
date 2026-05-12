import { create } from 'zustand'
import { persist, createJSONStorage } from 'zustand/middleware'
import { tokenStorage } from '@/api/axiosInstance'
import { authApi } from '@/api/authApi'
import { describeError } from '@/api/errorMessage'
import { useChatStore } from '@/stores/chatStore'
import type { AuthUser, LoginRequest, UserRole } from '@/types/Auth'

interface AuthState {
  user: AuthUser | null
  isAuthenticated: boolean
  isLoading: boolean
  error: string | null

  // Actions
  login: (data: LoginRequest) => Promise<void>
  logout: () => void
  clearError: () => void
  hasRole: (role: UserRole) => boolean
}

/**
 * Wipes any chat data left over from a previous user session — both the
 * Zustand in-memory state AND the persisted snapshot in localStorage.
 * Called on logout AND right before login, so any same-tab user-switch
 * starts from an empty store. ProtectedRoute then re-hydrates from the
 * backend for the new user (`hydrated === false` triggers it).
 */
function wipeChatStateForUserSwitch() {
  try {
    useChatStore.getState().reset()       // in-memory: conversations=[], hydrated=false
    localStorage.removeItem('nexusai-chat-store')
  } catch { /* noop */ }
}

export const useAuthStore = create<AuthState>()(
  persist(
    (set, get) => ({
      user: null,
      isAuthenticated: false,
      isLoading: false,
      error: null,

      login: async (data: LoginRequest) => {
        set({ isLoading: true, error: null })
        try {
          const response = await authApi.login(data)

          // Only AFTER credentials check succeeds: if this is a different
          // user than the one already cached in this tab, wipe everything
          // so we don't leak the prior user's chat history. We compare
          // against the persisted username (could be from a previous
          // session in this tab or another).
          const previousUser = get().user?.username
          if (previousUser !== response.username) {
            wipeChatStateForUserSwitch()
          }

          tokenStorage.setAccess(response.accessToken)
          tokenStorage.setRefresh(response.refreshToken)

          set({
            user: {
              username: response.username,
              roles: response.roles as UserRole[],
            },
            isAuthenticated: true,
            isLoading: false,
            error: null,
          })
        } catch (err: unknown) {
          set({ isLoading: false, error: describeError(err), isAuthenticated: false })
          throw err
        }
      },

      logout: () => {
        tokenStorage.clear()
        set({ user: null, isAuthenticated: false, error: null })
        wipeChatStateForUserSwitch()
      },

      clearError: () => set({ error: null }),

      hasRole: (role: UserRole) => {
        const { user } = get()
        return user?.roles.includes(role) ?? false
      },
    }),
    {
      name: 'nexusai-auth',
      storage: createJSONStorage(() => localStorage),
      partialize: (state) => ({
        user: state.user,
        isAuthenticated: state.isAuthenticated,
      }),
    },
  ),
)
