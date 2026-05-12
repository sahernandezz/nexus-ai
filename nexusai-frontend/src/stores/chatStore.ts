import { create } from 'zustand'
import { persist } from 'zustand/middleware'
import type { ChatMessage, Conversation, Language } from '@/types/Api'
import { conversationsApi } from '@/api/conversationsApi'

interface ChatState {
  conversations: Conversation[]
  activeConversationId: string | null
  isStreaming: boolean
  language: Language
  hydrated: boolean
  /**
   * Username this in-memory snapshot belongs to. Used as a defense-in-depth
   * check on hydrate: if the persisted snapshot was written by user A and a
   * different user logs in, we discard the local cache and fetch fresh from
   * the backend instead of leaking any data across users.
   */
  ownerUsername: string | null
  /**
   * Last backend-sync error, surfaced in the UI so the user immediately
   * sees when persistence is failing (instead of silently caching only in
   * the browser). null = all good.
   */
  syncError: string | null

  // Actions
  newConversation: () => string
  loadConversation: (id: string) => void
  deleteConversation: (id: string) => void
  addMessage: (msg: ChatMessage) => void
  updateLastAssistantMessage: (content: string) => void
  updateLastAssistantMeta: (meta: Partial<Pick<ChatMessage, 'cached' | 'cacheLayer' | 'model'>>) => void
  updateMessage: (id: string, updates: Partial<ChatMessage>) => void
  setStreaming: (v: boolean) => void
  setLanguage: (lang: Language) => void
  clearAllConversations: () => void
  hydrateFromBackend: (username: string) => Promise<void>
  reset: () => void
}

function makeConversation(): Conversation {
  const id = crypto.randomUUID()
  const now = new Date().toISOString()
  return { id, title: 'New Chat', sessionId: id, messages: [], createdAt: now, updatedAt: now }
}

// ── Backend sync helpers (fire-and-forget; UI is optimistic) ─────────────────

/** Records a sync failure into chatStore.syncError so the UI shows it. */
const onSyncError = (op: string, e: unknown) => {
  console.error(`[chatStore] ${op} sync failed:`, e)
  // Pull as much detail as possible from axios errors so the UI badge can
  // show the actual backend status / response body — not just a generic
  // "Network Error". Helps diagnose 404 vs 500 vs CORS vs offline.
  let detail = e instanceof Error ? e.message : String(e)
  const ax = e as { response?: { status?: number; data?: unknown }; config?: { url?: string; method?: string } } | undefined
  const status = ax?.response?.status
  const url = ax?.config?.url
  const method = ax?.config?.method?.toUpperCase()
  const body = ax?.response?.data
  if (body && typeof body === 'object' && 'error' in body) {
    detail = String((body as { error: unknown }).error)
  } else if (typeof body === 'string' && body.length < 200) {
    detail = body
  }
  const parts = [op]
  if (method && url) parts.push(`${method} ${url}`)
  if (status) parts.push(`HTTP ${status}`)
  parts.push(detail)
  try {
    useChatStore.setState({ syncError: parts.join(' · ') })
  } catch { /* noop */ }
}

const onSyncSuccess = () => {
  try {
    if (useChatStore.getState().syncError) {
      useChatStore.setState({ syncError: null })
    }
  } catch { /* noop */ }
}

const syncMessage = (conversationId: string, msg: ChatMessage, index: number) => {
  conversationsApi.upsertMessage(conversationId, msg, index)
    .then(onSyncSuccess)
    .catch((e) => onSyncError('message', e))
}

const syncConversation = (conversationId: string, title: string) => {
  conversationsApi.rename(conversationId, title)
    .then(onSyncSuccess)
    .catch((e) => onSyncError('rename', e))
}

const syncCreate = (conv: Conversation) => {
  // We pass our local UUID — backend uses it (idempotent INSERT ON CONFLICT
  // DO NOTHING). Same id end-to-end, so no swap and no race window between
  // this POST and the subsequent message PUTs.
  conversationsApi.create(conv.id, conv.title)
    .then(onSyncSuccess)
    .catch((e) => onSyncError('create', e))
}

const syncDelete = (conversationId: string) => {
  conversationsApi.delete(conversationId)
    .then(onSyncSuccess)
    .catch((e) => onSyncError('delete', e))
}

// ────────────────────────────────────────────────────────────────────────────

export const useChatStore = create<ChatState>()(
  persist(
    (set, get) => ({
      conversations: [],
      activeConversationId: null,
      isStreaming: false,
      language: 'auto',
      hydrated: false,
      ownerUsername: null,
      syncError: null,

      newConversation: () => {
        const conv = makeConversation()
        set((s) => ({ conversations: [conv, ...s.conversations], activeConversationId: conv.id }))
        syncCreate(conv)
        return conv.sessionId
      },

      loadConversation: (id) => set({ activeConversationId: id }),

      deleteConversation: (id) => {
        set((s) => {
          const filtered = s.conversations.filter((c) => c.id !== id)
          const nextActive =
            s.activeConversationId === id ? (filtered[0]?.id ?? null) : s.activeConversationId
          return { conversations: filtered, activeConversationId: nextActive }
        })
        syncDelete(id)
      },

      addMessage: (msg) => {
        let needsCreate: Conversation | null = null
        set((s) => {
          let { conversations, activeConversationId } = s
          if (!activeConversationId || !conversations.find((c) => c.id === activeConversationId)) {
            const conv = makeConversation()
            conversations = [conv, ...conversations]
            activeConversationId = conv.id
            needsCreate = conv
          }
          const now = new Date().toISOString()
          return {
            activeConversationId,
            conversations: conversations.map((c) => {
              if (c.id !== activeConversationId) return c
              const messages = [...c.messages, msg]
              const title =
                c.title === 'New Chat' && msg.role === 'user' && msg.content
                  ? msg.content.slice(0, 44) + (msg.content.length > 44 ? '…' : '')
                  : c.title
              return { ...c, messages, title, updatedAt: now }
            }),
          }
        })
        if (needsCreate) syncCreate(needsCreate)
        const after = get()
        const conv = after.conversations.find((c) => c.id === after.activeConversationId)
        if (conv) {
          if (conv.title !== 'New Chat') syncConversation(conv.id, conv.title)
          syncMessage(conv.id, msg, conv.messages.length - 1)
        }
      },

      updateLastAssistantMessage: (content) => {
        let updatedConvId: string | null = null
        let updatedMsg: ChatMessage | null = null
        let msgIndex = 0
        set((s) => ({
          conversations: s.conversations.map((c) => {
            if (c.id !== s.activeConversationId) return c
            const msgs = [...c.messages]
            for (let i = msgs.length - 1; i >= 0; i--) {
              if (msgs[i].role === 'assistant') {
                msgs[i] = { ...msgs[i], content }
                updatedMsg = msgs[i]
                msgIndex = i
                updatedConvId = c.id
                break
              }
            }
            return { ...c, messages: msgs }
          }),
        }))
        if (updatedConvId && updatedMsg) syncMessage(updatedConvId, updatedMsg, msgIndex)
      },

      updateLastAssistantMeta: (meta) => {
        let updatedConvId: string | null = null
        let updatedMsg: ChatMessage | null = null
        let msgIndex = 0
        set((s) => ({
          conversations: s.conversations.map((c) => {
            if (c.id !== s.activeConversationId) return c
            const msgs = [...c.messages]
            for (let i = msgs.length - 1; i >= 0; i--) {
              if (msgs[i].role === 'assistant') {
                msgs[i] = { ...msgs[i], ...meta }
                updatedMsg = msgs[i]
                msgIndex = i
                updatedConvId = c.id
                break
              }
            }
            return { ...c, messages: msgs }
          }),
        }))
        if (updatedConvId && updatedMsg) syncMessage(updatedConvId, updatedMsg, msgIndex)
      },

      updateMessage: (id, updates) => {
        let updatedConvId: string | null = null
        let updatedMsg: ChatMessage | null = null
        let msgIndex = 0
        set((s) => ({
          conversations: s.conversations.map((c) => {
            if (c.id !== s.activeConversationId) return c
            const messages = c.messages.map((m, i) => {
              if (m.id === id) {
                const next = { ...m, ...updates }
                updatedMsg = next
                msgIndex = i
                updatedConvId = c.id
                return next
              }
              return m
            })
            return { ...c, messages }
          }),
        }))
        if (updatedConvId && updatedMsg) syncMessage(updatedConvId, updatedMsg, msgIndex)
      },

      setStreaming: (isStreaming) => set({ isStreaming }),

      setLanguage: (language) => set({ language }),

      clearAllConversations: () => {
        const ids = get().conversations.map((c) => c.id)
        set({ conversations: [], activeConversationId: null })
        ids.forEach(syncDelete)
      },

      reset: () => set({
        conversations: [],
        activeConversationId: null,
        hydrated: false,
        ownerUsername: null,
      }),

      hydrateFromBackend: async (username: string) => {
        // Defense-in-depth: if the persisted snapshot was for a different
        // user (somebody manually swapped the JWT, edited localStorage,
        // etc.) wipe the cache before fetching the current user's data.
        if (get().ownerUsername && get().ownerUsername !== username) {
          set({ conversations: [], activeConversationId: null })
        }
        try {
          const list = await conversationsApi.list()
          const full = await Promise.all(list.map((c) => conversationsApi.get(c.id).catch(() => c)))
          set({
            conversations: full,
            activeConversationId: full[0]?.id ?? null,
            hydrated: true,
            ownerUsername: username,
          })
        } catch (e) {
          console.warn('[chatStore] hydrate failed (using local cache)', e)
          set({ hydrated: true, ownerUsername: username })
        }
      },
    }),
    {
      name: 'nexusai-chat-store',
      partialize: (s) => ({
        conversations: s.conversations,
        activeConversationId: s.activeConversationId,
        language: s.language,
        ownerUsername: s.ownerUsername,
      }),
    },
  ),
)

const EMPTY_MESSAGES: ChatMessage[] = []

export const useActiveMessages = (): ChatMessage[] =>
  useChatStore((s) => s.conversations.find((c) => c.id === s.activeConversationId)?.messages ?? EMPTY_MESSAGES)

export const useActiveSessionId = (): string | null =>
  useChatStore((s) => s.conversations.find((c) => c.id === s.activeConversationId)?.sessionId ?? null)
