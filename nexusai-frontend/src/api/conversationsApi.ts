import { apiClient } from './axiosInstance'
import type { ChatMessage, Conversation } from '@/types/Api'

interface ConversationDto {
  id: string
  title: string
  model?: string
  createdAt: string
  updatedAt: string
  messages?: MessageDto[]
}

interface MessageDto {
  clientId: string
  role: 'user' | 'assistant' | 'system'
  content: string
  messageIndex?: number
  attachments?: ChatMessage['attachments']
  ragAction?: ChatMessage['ragAction']
  cached?: boolean
  cacheLayer?: string
  model?: string
  createdAt?: string
}

function dtoToConversation(d: ConversationDto): Conversation {
  return {
    id: d.id,
    sessionId: d.id,
    title: d.title,
    createdAt: d.createdAt,
    updatedAt: d.updatedAt,
    messages: (d.messages ?? []).map((m) => ({
      id: m.clientId,
      role: m.role,
      content: m.content,
      attachments: m.attachments,
      ragAction: m.ragAction,
      cached: m.cached,
      cacheLayer: m.cacheLayer,
      model: m.model,
      createdAt: m.createdAt ?? new Date().toISOString(),
    })),
  }
}

function messageToDto(m: ChatMessage, index: number): MessageDto {
  return {
    clientId: m.id,
    role: m.role,
    content: m.content,
    messageIndex: index,
    attachments: m.attachments,
    ragAction: m.ragAction,
    cached: m.cached,
    cacheLayer: m.cacheLayer,
    model: m.model,
    createdAt: m.createdAt,
  }
}

export const conversationsApi = {
  list: async (): Promise<Conversation[]> => {
    const r = await apiClient.get<ConversationDto[]>('/api/conversations')
    return r.data.map(dtoToConversation)
  },

  get: async (id: string): Promise<Conversation> => {
    const r = await apiClient.get<ConversationDto>(`/api/conversations/${id}`)
    return dtoToConversation(r.data)
  },

  /**
   * Idempotent create. Pass {@code id} so the backend uses our locally
   * generated UUID — same id end-to-end means no id-swap dance and no race
   * window with the message PUTs.
   */
  create: async (id: string, title: string): Promise<Conversation> => {
    const r = await apiClient.post<ConversationDto>('/api/conversations', { id, title })
    return dtoToConversation(r.data)
  },

  rename: async (id: string, title: string): Promise<Conversation> => {
    const r = await apiClient.patch<ConversationDto>(`/api/conversations/${id}`, { title })
    return dtoToConversation(r.data)
  },

  delete: (id: string): Promise<void> =>
    apiClient.delete(`/api/conversations/${id}`).then(() => undefined),

  upsertMessage: (conversationId: string, message: ChatMessage, index: number): Promise<void> =>
    apiClient.put(`/api/conversations/${conversationId}/messages`,
      messageToDto(message, index)).then(() => undefined),

  deleteMessage: (conversationId: string, clientId: string): Promise<void> =>
    apiClient.delete(`/api/conversations/${conversationId}/messages/${clientId}`).then(() => undefined),
}

export const filesApi = {
  upload: async (file: File, conversationId?: string): Promise<{ id: string; url: string; filename: string; contentType: string; size: number }> => {
    const form = new FormData()
    form.append('file', file)
    const params = conversationId ? `?conversationId=${conversationId}` : ''
    const r = await apiClient.post(`/api/files${params}`, form, {
      headers: { 'Content-Type': 'multipart/form-data' },
      timeout: 120_000,
    })
    return r.data
  },
}

export const exportsApi = {
  create: async (conversationId?: string): Promise<{ id: string; status: string }> => {
    const r = await apiClient.post('/api/exports', conversationId ? { conversationId } : {})
    return r.data
  },
  status: async (id: string): Promise<{ id: string; status: 'PENDING' | 'PROCESSING' | 'READY' | 'FAILED'; attachmentId?: string; downloadUrl?: string; errorMsg?: string }> => {
    const r = await apiClient.get(`/api/exports/${id}`)
    return r.data
  },
}
