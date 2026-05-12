import { useEffect, useState } from 'react'
import { Outlet, useLocation } from 'react-router-dom'
import { Menu, X } from 'lucide-react'
import Sidebar from './Sidebar'

/**
 * Top-level shell. On md+ screens the sidebar is a permanent left rail; on
 * narrow viewports it collapses into a slide-in drawer triggered by a
 * hamburger button so the chat takes the full screen width.
 *
 * The drawer auto-closes whenever the route changes (so picking a chat from
 * the list doesn't leave the overlay open) and on Escape.
 */
export default function AppLayout() {
  const [sidebarOpen, setSidebarOpen] = useState(false)
  const location = useLocation()

  // Close drawer on route change and on Escape — both common expectations.
  useEffect(() => setSidebarOpen(false), [location.pathname])
  useEffect(() => {
    const onKey = (e: KeyboardEvent) => { if (e.key === 'Escape') setSidebarOpen(false) }
    window.addEventListener('keydown', onKey)
    return () => window.removeEventListener('keydown', onKey)
  }, [])

  return (
    <div className="flex h-full relative">
      {/* Permanent rail (md+) + slide-in drawer (mobile) */}
      <div
        className={
          'fixed inset-y-0 left-0 z-40 transition-transform duration-200 md:static md:translate-x-0 ' +
          (sidebarOpen ? 'translate-x-0' : '-translate-x-full md:translate-x-0')
        }
      >
        <Sidebar />
      </div>

      {/* Backdrop for the mobile drawer */}
      {sidebarOpen && (
        <div
          onClick={() => setSidebarOpen(false)}
          className="fixed inset-0 z-30 bg-black/50 md:hidden"
          aria-hidden="true"
        />
      )}

      <main className="flex-1 overflow-hidden flex flex-col min-w-0">
        {/* Mobile-only top bar with hamburger. Hidden on md+ where the
            sidebar is always visible and the chat has its own toolbar. */}
        <div className="md:hidden flex items-center gap-2 h-[44px] shrink-0 px-3 border-b border-[var(--color-surface-border)] bg-[var(--color-surface-200)]">
          <button
            type="button"
            onClick={() => setSidebarOpen((v) => !v)}
            aria-label={sidebarOpen ? 'Cerrar menu' : 'Abrir menu'}
            className="flex items-center justify-center w-9 h-9 rounded-lg text-[var(--color-text-secondary)] hover:bg-[var(--color-surface-300)] transition-colors cursor-pointer"
          >
            {sidebarOpen ? <X size={18} /> : <Menu size={18} />}
          </button>
          <span className="text-[13px] font-semibold text-[var(--color-text-primary)] tracking-tight">
            NexusAI
          </span>
        </div>
        <Outlet />
      </main>
    </div>
  )
}
