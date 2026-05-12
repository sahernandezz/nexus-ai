import { useState } from 'react'
import { useNavigate } from 'react-router-dom'
import {
  MessageSquare,
  LogOut,
  Sparkles,
  Plus,
  Settings,
  Trash2,
  Database,
  BarChart3,
} from 'lucide-react'
import { useAuthStore } from '@/stores/authStore'
import { useChatStore } from '@/stores/chatStore'
import clsx from 'clsx'
import SettingsModal from '@/components/chat/SettingsModal'
import RagDocumentsModal from '@/components/chat/RagDocumentsModal'

function groupConversations(conversations: ReturnType<typeof useChatStore.getState>['conversations']) {
  const now = new Date()
  const today = now.toDateString()
  const yesterday = new Date(now.getTime() - 86400000).toDateString()
  const lastWeek = new Date(now.getTime() - 7 * 86400000)

  const groups: { label: string; items: typeof conversations }[] = [
    { label: 'Hoy', items: [] },
    { label: 'Ayer', items: [] },
    { label: 'Ultimos 7 dias', items: [] },
    { label: 'Anterior', items: [] },
  ]

  for (const c of conversations) {
    const d = new Date(c.updatedAt)
    if (d.toDateString() === today) groups[0].items.push(c)
    else if (d.toDateString() === yesterday) groups[1].items.push(c)
    else if (d >= lastWeek) groups[2].items.push(c)
    else groups[3].items.push(c)
  }

  return groups.filter((g) => g.items.length > 0)
}

export default function Sidebar() {
  const navigate = useNavigate()
  const { user, logout } = useAuthStore()
  const { conversations, activeConversationId, newConversation, loadConversation, deleteConversation } = useChatStore()
  const [showSettings, setShowSettings] = useState(false)
  const [showRagDocs, setShowRagDocs] = useState(false)
  const [hoveredId, setHoveredId] = useState<string | null>(null)

  const handleLogout = () => { logout(); navigate('/login') }

  const handleNew = () => {
    newConversation()
    navigate('/chat')
  }

  const handleLoad = (id: string) => {
    loadConversation(id)
    navigate('/chat')
  }

  const groups = groupConversations(conversations)
  const initials = user?.username ? user.username.slice(0, 2).toUpperCase() : 'U'

  return (
    <>
      <aside
        style={{ width: 'var(--width-sidebar)' }}
        className="glass flex flex-col h-full shrink-0 border-r border-[var(--color-surface-border)] max-w-[85vw]"
      >
        {/* Logo + New Chat */}
        <div className="flex items-center justify-between px-3 h-[52px] border-b border-[var(--color-surface-border)]">
          <div className="flex items-center gap-2">
            <div className="flex items-center justify-center w-6 h-6 rounded-lg bg-[var(--color-brand-400)] shadow-sm shadow-[var(--color-brand-600)]/40">
              <Sparkles size={12} className="text-black" />
            </div>
            <span className="font-semibold text-[var(--color-text-primary)] text-[13px] tracking-tight">NexusAI</span>
          </div>
          <button
            onClick={handleNew}
            title="Nuevo chat"
            className="flex items-center justify-center w-7 h-7 rounded-lg text-[var(--color-text-muted)] hover:text-[var(--color-text-primary)] hover:bg-[var(--color-surface-300)] transition-colors cursor-pointer"
          >
            <Plus size={15} />
          </button>
        </div>

        {/* Conversation List */}
        <div className="flex-1 overflow-y-auto py-2 px-2 space-y-3 min-h-0">
          {conversations.length === 0 && (
            <button
              onClick={handleNew}
              className="flex items-center gap-2 w-full px-2.5 py-2 rounded-lg text-[12px] text-[var(--color-text-muted)] hover:bg-[var(--color-surface-200)] hover:text-[var(--color-text-secondary)] transition-colors cursor-pointer"
            >
              <MessageSquare size={13} />
              Iniciar conversacion
            </button>
          )}

          {groups.map((group) => (
            <div key={group.label}>
              <p className="px-2 py-1 text-[10px] font-medium text-[var(--color-text-muted)] uppercase tracking-wide">
                {group.label}
              </p>
              <div className="space-y-0.5">
                {group.items.map((conv) => (
                  <div
                    key={conv.id}
                    className={clsx(
                      'group relative flex items-center gap-1 px-2.5 py-2 rounded-lg cursor-pointer transition-colors',
                      conv.id === activeConversationId
                        ? 'bg-[var(--color-surface-300)] text-[var(--color-text-primary)]'
                        : 'text-[var(--color-text-secondary)] hover:bg-[var(--color-surface-200)]',
                    )}
                    onClick={() => handleLoad(conv.id)}
                    onMouseEnter={() => setHoveredId(conv.id)}
                    onMouseLeave={() => setHoveredId(null)}
                  >
                    <span className="flex-1 text-[12px] truncate leading-none">{conv.title}</span>
                    {hoveredId === conv.id && (
                      <button
                        onClick={(e) => { e.stopPropagation(); deleteConversation(conv.id) }}
                        className="shrink-0 p-0.5 rounded text-[var(--color-text-muted)] hover:text-[var(--color-error)] transition-colors cursor-pointer"
                      >
                        <Trash2 size={11} />
                      </button>
                    )}
                  </div>
                ))}
              </div>
            </div>
          ))}
        </div>

        {/* Shortcuts */}
        <div className="px-2 pt-2 border-t border-[var(--color-surface-border)] space-y-0.5">
          <button
            onClick={() => setShowRagDocs(true)}
            className="flex items-center gap-2 w-full px-2.5 py-1.5 rounded-lg text-[12px] font-medium text-[var(--color-text-muted)] hover:bg-[var(--color-surface-200)] hover:text-[var(--color-text-secondary)] transition-colors cursor-pointer"
          >
            <Database size={13} className="text-[var(--color-brand-400)]" />
            Documentos RAG
          </button>
          <button
            onClick={() => navigate('/dashboard')}
            className="flex items-center gap-2 w-full px-2.5 py-1.5 rounded-lg text-[12px] font-medium text-[var(--color-text-muted)] hover:bg-[var(--color-surface-200)] hover:text-[var(--color-text-secondary)] transition-colors cursor-pointer"
          >
            <BarChart3 size={13} className="text-[var(--color-brand-400)]" />
            Dashboard
          </button>
        </div>

        {/* User row */}
        <div className="px-2 pb-2 border-t border-[var(--color-surface-border)] pt-2 mt-1">
          <div className="flex items-center gap-2 px-2 py-1.5 rounded-lg hover:bg-[var(--color-surface-200)] transition-colors group">
            <div className="w-6 h-6 rounded-full bg-[var(--color-brand-400)] flex items-center justify-center text-[10px] font-semibold text-black shrink-0">
              {initials}
            </div>
            <p className="text-[12px] font-medium text-[var(--color-text-secondary)] truncate flex-1 leading-none">{user?.username ?? '?'}</p>
            <button
              onClick={() => setShowSettings(true)}
              title="Configuracion"
              className="p-1 rounded text-[var(--color-text-muted)] hover:text-[var(--color-brand-500)] transition-colors cursor-pointer"
            >
              <Settings size={13} />
            </button>
            <button
              onClick={handleLogout}
              title="Cerrar sesion"
              className="p-1 rounded text-[var(--color-text-muted)] hover:text-[var(--color-error)] transition-colors cursor-pointer"
            >
              <LogOut size={13} />
            </button>
          </div>
        </div>
      </aside>

      {showSettings && <SettingsModal onClose={() => setShowSettings(false)} />}
      {showRagDocs && <RagDocumentsModal onClose={() => setShowRagDocs(false)} />}
    </>
  )
}
