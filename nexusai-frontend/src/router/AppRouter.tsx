import { createBrowserRouter, RouterProvider, Navigate } from 'react-router-dom'
import ProtectedRoute from './ProtectedRoute'
import AppLayout from '@/components/layout/AppLayout'
import LoginPage from '@/pages/LoginPage'
import ChatPage from '@/pages/ChatPage'

const router = createBrowserRouter([
  { path: '/login', element: <LoginPage /> },

  {
    element: <ProtectedRoute />,
    children: [
      {
        element: <AppLayout />,
        children: [
          { index: true, element: <Navigate to="/chat" replace /> },
          { path: '/chat', element: <ChatPage /> },
          {
            path: '/dashboard',
            lazy: () =>
              import('@/pages/DashboardPage').then((m) => ({ Component: m.default })),
          },
        ],
      },
    ],
  },

  {
    path: '/unauthorized',
    element: (
      <div className="flex items-center justify-center h-screen">
        <p className="text-red-400 text-lg">403 — Unauthorized</p>
      </div>
    ),
  },
  { path: '*', element: <Navigate to="/chat" replace /> },
])

export default function AppRouter() {
  return <RouterProvider router={router} />
}
