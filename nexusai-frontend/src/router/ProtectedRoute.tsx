import { useEffect } from 'react'
import { Navigate, Outlet, useLocation } from 'react-router-dom'
import { useAuthStore } from '@/stores/authStore'
import { useChatStore } from '@/stores/chatStore'
import { tokenStorage } from '@/api/axiosInstance'
import type { UserRole } from '@/types/Auth'

interface ProtectedRouteProps {
  requiredRole?: UserRole
}

export default function ProtectedRoute({ requiredRole }: ProtectedRouteProps) {
  const location = useLocation()
  const { isAuthenticated, hasRole, user } = useAuthStore()
  const hydrated = useChatStore((s) => s.hydrated)
  const ownerUsername = useChatStore((s) => s.ownerUsername)
  const hydrate = useChatStore((s) => s.hydrateFromBackend)

  const hasToken = Boolean(tokenStorage.getAccess())
  const username = user?.username ?? null

  // Pull persisted conversations from the backend the first time the user
  // hits a protected route — so opening the app in a different browser
  // immediately shows their full history.
  //
  // Also re-hydrates if the persisted snapshot belongs to a different user
  // (defense in depth against same-tab user switches that somehow bypassed
  // the normal logout/login wipe).
  useEffect(() => {
    if (!isAuthenticated || !hasToken || !username) return
    const needsHydrate = !hydrated || ownerUsername !== username
    if (needsHydrate) void hydrate(username)
  }, [isAuthenticated, hasToken, hydrated, hydrate, username, ownerUsername])

  if (!isAuthenticated || !hasToken) {
    return <Navigate to="/login" state={{ from: location }} replace />
  }

  if (requiredRole && !hasRole(requiredRole)) {
    return <Navigate to="/unauthorized" replace />
  }

  return <Outlet />
}
