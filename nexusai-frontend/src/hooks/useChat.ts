import { useCallback, useEffect, useRef } from 'react'
import { useQuery } from '@tanstack/react-query'
import { useStream } from './useStream'
import { useChatStore, useActiveMessages } from '@/stores/chatStore'
import { apiClient } from '@/api/axiosInstance'
import { describeError } from '@/api/errorMessage'
import type { ChatMessage } from '@/types/Api'

/**
 * Main chat hook — combines streaming SSE and Zustand store.
 * Exposes sendMessage, stopStreaming, messages, isStreaming, and available models.
 */
export function useChat(sessionId?: string, documentId?: string) {
  // `useActiveMessages` uses an inline selector — free from the Object.assign getter bug
  const messages: ChatMessage[] = useActiveMessages()

  const {
    addMessage,
    updateLastAssistantMessage,
    updateLastAssistantMeta,
    setStreaming,
    language,
  } = useChatStore()

  const pendingChunksRef = useRef('')
  // Tracks whether the in-flight stream has produced any content. If a stream
  // completes with zero non-empty chunks (e.g. cache layer returned an empty
  // entry, or the LLM answered with nothing) we surface a visible message so
  // the user does not stare at an invisible assistant bubble.
  const receivedAnyChunkRef = useRef(false)

  // ── Available models ───────────────────────────────────────────────────────
  const { data: modelsData } = useQuery({
    queryKey: ['chat', 'models'],
    queryFn: () =>
      apiClient.get<{ providers: string[] }>('/api/chat/models').then((r) => r.data),
    staleTime: Infinity,
    retry: false,
  })

  // ── Streaming hook ─────────────────────────────────────────────────────────
  const { stream, isStreaming, abort } = useStream({
    onChunk: (chunk) => {
      if (chunk) receivedAnyChunkRef.current = true
      pendingChunksRef.current += chunk
      updateLastAssistantMessage(pendingChunksRef.current)
    },
    onMeta: (meta) => {
      if (meta.cached) {
        updateLastAssistantMeta({ cached: true, cacheLayer: typeof meta.layer === 'string' ? meta.layer : undefined })
      }
    },
    onError: (error) => {
      updateLastAssistantMessage(`⚠️ ${describeError(error)}`)
      setStreaming(false)
    },
    onDone: () => {
      // Stream finished cleanly but no content arrived — surface a placeholder
      // so the empty assistant bubble doesn't look like a UI bug to the user.
      if (!receivedAnyChunkRef.current && !pendingChunksRef.current) {
        updateLastAssistantMessage(
          '⚠️ El servidor cerró la conexión sin enviar contenido. Intenta de nuevo.',
        )
      }
      setStreaming(false)
      pendingChunksRef.current = ''
      receivedAnyChunkRef.current = false
    },
  })

  // Sync streaming flag
  useEffect(() => {
    setStreaming(isStreaming)
  }, [isStreaming, setStreaming])

  // ── Send message ───────────────────────────────────────────────────────────
  const sendMessage = useCallback(
    async (text: string, provider: string = 'openai', model?: string, docIdOverride?: string) => {
      if (!text.trim() || isStreaming) return

      // docIdOverride lets ChatWindow pass the current activeDocumentId at call-time,
      // bypassing any React state propagation delay.
      const effectiveDocId = docIdOverride !== undefined ? docIdOverride : documentId

      // Build API text — append language instruction (invisible to UI)
      const langSuffix =
        language === 'es'
          ? '\n\n[Responde exclusivamente en español]'
          : language === 'en'
            ? '\n\n[Respond exclusively in English]'
            : ''
      const apiText = text.trim() + langSuffix

      // Add user message
      addMessage({
        id: crypto.randomUUID(),
        role: 'user',
        content: text.trim(),
        createdAt: new Date().toISOString(),
      })

      // Add empty placeholder for assistant
      pendingChunksRef.current = ''
      receivedAnyChunkRef.current = false
      addMessage({
        id: crypto.randomUUID(),
        role: 'assistant',
        content: '',
        createdAt: new Date().toISOString(),
        model: provider,
      })

      // Use RAG endpoint when a document is selected, otherwise regular chat
      const endpoint = effectiveDocId ? '/api/rag/stream' : '/api/chat/stream'
      const payload = effectiveDocId
        ? { message: apiText, documentId: effectiveDocId, sessionId, provider }
        : { message: apiText, sessionId, provider, model, stream: true }

      await stream(endpoint, payload)
    },
    [isStreaming, addMessage, stream, sessionId, documentId, language],
  )

  return {
    messages,
    sendMessage,
    isStreaming,
    stopStreaming: abort,
    availableProviders: modelsData?.providers ?? [],
  }
}
