import { useState, type FormEvent } from 'react'
import { useNavigate, useLocation } from 'react-router-dom'
import { Sparkles, Eye, EyeOff, Loader2 } from 'lucide-react'
import { useAuthStore } from '@/stores/authStore'

export default function LoginPage() {
  const navigate = useNavigate()
  const location = useLocation()
  const { login, isLoading, error, clearError } = useAuthStore()

  const [username, setUsername] = useState('')
  const [password, setPassword] = useState('')
  const [showPassword, setShowPassword] = useState(false)

  const from = (location.state as { from?: Location })?.from?.pathname ?? '/chat'

  const handleSubmit = async (e: FormEvent<HTMLFormElement>) => {
    e.preventDefault()
    clearError()
    try {
      await login({ username, password })
      navigate(from, { replace: true })
    } catch { /* error is set in the store */ }
  }

  return (
    <div className="min-h-screen flex items-center justify-center px-4">
      <div className="w-full max-w-[340px]">

        {/* Wordmark */}
        <div className="flex flex-col items-center mb-8 gap-4">
          <div className="flex items-center justify-center w-10 h-10 rounded-2xl bg-[var(--color-brand-400)] shadow-lg shadow-[var(--color-brand-600)]/30">
            <Sparkles size={20} className="text-black" />
          </div>
          <div className="text-center">
            <h1 className="text-[15px] font-semibold text-[var(--color-text-primary)] tracking-tight">
              Sign in to NexusAI
            </h1>
            <p className="text-[13px] text-[var(--color-text-muted)] mt-0.5">
              Your enterprise AI platform
            </p>
          </div>
        </div>

        {/* Form card */}
        <div className="glass-strong rounded-3xl border border-[var(--color-surface-border)]
                        p-5 space-y-4 shadow-2xl shadow-black/40">

          {error && (
            <div className="px-3 py-2.5 rounded-lg text-[13px]
                            bg-red-500/8 border border-red-500/15 text-[var(--color-error)]">
              {error}
            </div>
          )}

          <form onSubmit={handleSubmit} className="space-y-3">
            <div className="space-y-1.5">
              <label htmlFor="username"
                className="block text-[12px] font-medium text-[var(--color-text-secondary)] tracking-wide">
                Username
              </label>
              <input
                id="username"
                type="text"
                autoComplete="username"
                autoFocus
                required
                value={username}
                onChange={(e) => setUsername(e.target.value)}
                placeholder="admin"
                className="w-full px-3 py-2 rounded-lg text-[13px]
                           bg-[var(--color-surface-300)]
                           border border-[var(--color-surface-border)]
                           text-[var(--color-text-primary)]
                           placeholder:text-[var(--color-text-muted)]
                           outline-none focus:border-[var(--color-brand-400)]
                           focus:ring-2 focus:ring-[var(--color-brand-400)]/15
                           transition-all"
              />
            </div>

            <div className="space-y-1.5">
              <label htmlFor="password"
                className="block text-[12px] font-medium text-[var(--color-text-secondary)] tracking-wide">
                Password
              </label>
              <div className="relative">
                <input
                  id="password"
                  type={showPassword ? 'text' : 'password'}
                  autoComplete="current-password"
                  required
                  value={password}
                  onChange={(e) => setPassword(e.target.value)}
                  placeholder="••••••••"
                  className="w-full px-3 py-2 pr-9 rounded-lg text-[13px]
                             bg-[var(--color-surface-300)]
                             border border-[var(--color-surface-border)]
                             text-[var(--color-text-primary)]
                             placeholder:text-[var(--color-text-muted)]
                             outline-none focus:border-[var(--color-brand-500)]
                             focus:ring-2 focus:ring-[var(--color-brand-500)]/15
                             transition-all"
                />
                <button
                  type="button"
                  onClick={() => setShowPassword((v) => !v)}
                  tabIndex={-1}
                  className="absolute right-2.5 top-1/2 -translate-y-1/2
                             text-[var(--color-text-muted)] hover:text-[var(--color-text-secondary)]
                             transition-colors cursor-pointer"
                >
                  {showPassword ? <EyeOff size={14} /> : <Eye size={14} />}
                </button>
              </div>
            </div>

            <button
              type="submit"
              disabled={isLoading || !username || !password}
              className="w-full flex items-center justify-center gap-2
                         px-4 py-2 rounded-xl text-[13px] font-semibold
                         bg-[var(--color-brand-400)] text-black
                         hover:bg-[var(--color-brand-500)]
                         disabled:opacity-40 disabled:cursor-not-allowed
                         transition-colors cursor-pointer mt-1
                         shadow-md shadow-[var(--color-brand-600)]/30"
            >
              {isLoading ? (
                <><Loader2 size={14} className="animate-spin" /> Signing in…</>
              ) : (
                'Continue'
              )}
            </button>
          </form>
        </div>

        <p className="text-center text-[11px] text-[var(--color-text-muted)] mt-4">
          Dev credentials:{' '}
          <span className="font-mono text-[var(--color-text-secondary)]">admin / admin123</span>
        </p>
      </div>
    </div>
  )
}
