import { beforeEach, describe, expect, it, vi } from 'vitest'

// We mock the conversationsApi BEFORE importing the store so the store's
// fire-and-forget syncs hit the mock. Using vi.hoisted to share the spies.
const { mockApi } = vi.hoisted(() => ({
  mockApi: {
    create:        vi.fn().mockResolvedValue({ id: 'srv-id', sessionId: 'srv-id', title: 'New Chat', createdAt: '', updatedAt: '', messages: [] }),
    rename:        vi.fn().mockResolvedValue({ id: '', sessionId: '', title: '', createdAt: '', updatedAt: '', messages: [] }),
    delete:        vi.fn().mockResolvedValue(undefined),
    upsertMessage: vi.fn().mockResolvedValue(undefined),
    list:          vi.fn().mockResolvedValue([]),
    get:           vi.fn().mockResolvedValue({ id: '', sessionId: '', title: '', createdAt: '', updatedAt: '', messages: [] }),
  },
}))

vi.mock('@/api/conversationsApi', () => ({
  conversationsApi: mockApi,
}))

import { useChatStore } from './chatStore'
import type { ChatMessage } from '@/types/Api'

beforeEach(() => {
  // Reset Zustand state before every test so they don't leak.
  useChatStore.setState({
    conversations: [],
    activeConversationId: null,
    isStreaming: false,
    language: 'auto',
    hydrated: false,
  })
  vi.clearAllMocks()
  localStorage.clear()
})

const userMsg = (content: string, id = crypto.randomUUID()): ChatMessage => ({
  id,
  role: 'user',
  content,
  createdAt: new Date().toISOString(),
})

describe('chatStore', () => {
  describe('newConversation', () => {
    it('creates a new conversation and sets it active', () => {
      const id = useChatStore.getState().newConversation()
      const state = useChatStore.getState()
      expect(state.conversations).toHaveLength(1)
      expect(state.conversations[0].id).toBe(id)
      expect(state.activeConversationId).toBe(id)
      expect(state.conversations[0].messages).toEqual([])
      expect(state.conversations[0].title).toBe('New Chat')
    })

    it('triggers a backend create sync', () => {
      useChatStore.getState().newConversation()
      expect(mockApi.create).toHaveBeenCalledOnce()
    })
  })

  describe('addMessage', () => {
    it('auto-creates a conversation when none is active', () => {
      useChatStore.getState().addMessage(userMsg('hola'))
      const state = useChatStore.getState()
      expect(state.activeConversationId).not.toBeNull()
      expect(state.conversations).toHaveLength(1)
      expect(state.conversations[0].messages).toHaveLength(1)
      expect(mockApi.create).toHaveBeenCalledOnce()
      expect(mockApi.upsertMessage).toHaveBeenCalledOnce()
    })

    it('appends to the active conversation', () => {
      useChatStore.getState().newConversation()
      useChatStore.getState().addMessage(userMsg('first'))
      useChatStore.getState().addMessage(userMsg('second'))
      const conv = useChatStore.getState().conversations[0]
      expect(conv.messages.map((m) => m.content)).toEqual(['first', 'second'])
    })

    it('uses the first user message text as the conversation title', () => {
      useChatStore.getState().addMessage(userMsg('Esto es lo que pregunto'))
      expect(useChatStore.getState().conversations[0].title).toBe(
        'Esto es lo que pregunto',
      )
    })

    it('truncates long titles to ~44 chars with an ellipsis', () => {
      const long =
        'A'.repeat(80)
      useChatStore.getState().addMessage(userMsg(long))
      const title = useChatStore.getState().conversations[0].title
      expect(title.endsWith('…')).toBe(true)
      expect(title.length).toBeLessThan(60)
    })
  })

  describe('updateLastAssistantMessage', () => {
    it('updates the last assistant message content in place', () => {
      const store = useChatStore.getState()
      store.newConversation()
      store.addMessage(userMsg('hi'))
      store.addMessage({
        id: crypto.randomUUID(),
        role: 'assistant',
        content: '',
        createdAt: new Date().toISOString(),
      })
      store.updateLastAssistantMessage('hola que tal')
      const msgs = useChatStore.getState().conversations[0].messages
      expect(msgs[1].content).toBe('hola que tal')
    })

    it('does nothing when there is no assistant message yet', () => {
      const store = useChatStore.getState()
      store.newConversation()
      store.addMessage(userMsg('hi'))
      store.updateLastAssistantMessage('should be ignored')
      const msgs = useChatStore.getState().conversations[0].messages
      expect(msgs).toHaveLength(1)
      expect(msgs[0].role).toBe('user')
    })
  })

  describe('updateLastAssistantMeta', () => {
    it('marks the last assistant message as cached', () => {
      const store = useChatStore.getState()
      store.newConversation()
      store.addMessage({
        id: crypto.randomUUID(),
        role: 'assistant',
        content: 'cached payload',
        createdAt: new Date().toISOString(),
      })
      store.updateLastAssistantMeta({ cached: true, cacheLayer: 'exact' })
      const msg = useChatStore.getState().conversations[0].messages[0]
      expect(msg.cached).toBe(true)
      expect(msg.cacheLayer).toBe('exact')
    })
  })

  describe('updateMessage', () => {
    it('patches a specific message by id', () => {
      const store = useChatStore.getState()
      store.newConversation()
      const msg = userMsg('hi')
      store.addMessage(msg)
      store.updateMessage(msg.id, { content: 'edited' })
      expect(
        useChatStore.getState().conversations[0].messages[0].content,
      ).toBe('edited')
    })
  })

  describe('deleteConversation', () => {
    it('removes the conversation and updates activeConversationId', () => {
      const store = useChatStore.getState()
      const a = store.newConversation()
      const b = store.newConversation()
      // The newest conversation is at the head; b is active.
      expect(useChatStore.getState().activeConversationId).toBe(b)
      store.deleteConversation(b)
      const state = useChatStore.getState()
      expect(state.conversations.map((c) => c.id)).toEqual([a])
      expect(state.activeConversationId).toBe(a)
      expect(mockApi.delete).toHaveBeenCalledWith(b)
    })

    it('clears active when removing the last conversation', () => {
      const store = useChatStore.getState()
      const id = store.newConversation()
      store.deleteConversation(id)
      expect(useChatStore.getState().conversations).toEqual([])
      expect(useChatStore.getState().activeConversationId).toBeNull()
    })
  })

  describe('setLanguage', () => {
    it('updates the language', () => {
      useChatStore.getState().setLanguage('es')
      expect(useChatStore.getState().language).toBe('es')
    })
  })

  describe('reset', () => {
    it('drops conversations and active id', () => {
      useChatStore.getState().newConversation()
      useChatStore.getState().reset()
      expect(useChatStore.getState().conversations).toEqual([])
      expect(useChatStore.getState().activeConversationId).toBeNull()
      expect(useChatStore.getState().hydrated).toBe(false)
    })
  })
})
