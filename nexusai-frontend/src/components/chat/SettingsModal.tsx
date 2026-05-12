import { X, Globe, Trash2 } from 'lucide-react'
import clsx from 'clsx'
import { useChatStore } from '@/stores/chatStore'
import type { Language } from '@/types/Api'
import Portal from '@/components/layout/Portal'

interface SettingsModalProps {
  onClose: () => void
}

const LANGUAGES: { value: Language; label: string; sublabel: string }[] = [
  { value: 'auto', label: 'Automatico', sublabel: 'Auto' },
  { value: 'es',   label: 'Espanol',    sublabel: 'ES'   },
  { value: 'en',   label: 'English',    sublabel: 'EN'   },
]

export default function SettingsModal({ onClose }: SettingsModalProps) {
  const { language, setLanguage, clearAllConversations } = useChatStore()

  const handleClearAll = () => {
    if (confirm('Borrar todo el historial? Esta accion no se puede deshacer.')) {
      clearAllConversations()
      onClose()
    }
  }

  return (
    <Portal>
    <div
      className="fixed inset-0 z-50 flex items-center justify-center bg-black/55 backdrop-blur-md"
      onClick={(e) => e.target === e.currentTarget && onClose()}
    >
      <div className="glass-modal w-full max-w-sm mx-4 rounded-3xl border border-[var(--color-surface-border)] shadow-2xl shadow-black/50">
        <div className="flex items-center justify-between px-5 py-4 border-b border-[var(--color-surface-border)]">
          <h2 className="font-semibold text-[var(--color-text-primary)] text-[14px]">Configuracion</h2>
          <button onClick={onClose} className="p-1.5 rounded-lg text-[var(--color-text-muted)] hover:text-[var(--color-text-primary)] hover:bg-[var(--color-surface-300)] transition-colors cursor-pointer">
            <X size={15} />
          </button>
        </div>
        <div className="px-5 py-5 space-y-5">
          {/* Language */}
          <div className="space-y-2.5">
            <div className="flex items-center gap-2">
              <Globe size={13} className="text-[var(--color-brand-500)]" />
              <span className="text-[13px] font-medium text-[var(--color-text-primary)]">Idioma de respuestas</span>
            </div>
            <p className="text-[11px] text-[var(--color-text-muted)] leading-relaxed">
              Fuerza al modelo a responder siempre en el idioma seleccionado.
            </p>
            <div className="flex gap-2">
              {LANGUAGES.map((lang) => (
                <button
                  key={lang.value}
                  onClick={() => setLanguage(lang.value)}
                  className={clsx(
                    'flex-1 flex flex-col items-center gap-1 py-2.5 px-2 rounded-xl border text-[11px] font-medium transition-all cursor-pointer',
                    language === lang.value
                      ? 'border-[var(--color-brand-600)] bg-[var(--color-brand-600)]/10 text-[var(--color-brand-500)]'
                      : 'border-[var(--color-surface-border)] bg-[var(--color-surface-300)] text-[var(--color-text-secondary)] hover:border-[var(--color-surface-400)]',
                  )}
                >
                  <span className="text-[11px] font-bold opacity-60">{lang.sublabel}</span>
                  <span>{lang.label}</span>
                </button>
              ))}
            </div>
          </div>

          {/* Danger */}
          <div className="space-y-2 pt-2 border-t border-[var(--color-surface-border)]">
            <p className="text-[11px] font-medium text-[var(--color-text-muted)] uppercase tracking-wide">Zona de peligro</p>
            <button
              onClick={handleClearAll}
              className="flex items-center gap-2 w-full px-3 py-2.5 rounded-xl border border-[var(--color-error)]/20 bg-[var(--color-error)]/5 text-[var(--color-error)] text-[12px] font-medium hover:bg-[var(--color-error)]/10 transition-colors cursor-pointer"
            >
              <Trash2 size={13} />
              Borrar todo el historial
            </button>
          </div>
        </div>
      </div>
    </div>
    </Portal>
  )
}

