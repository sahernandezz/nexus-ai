import { useRef, useState, useCallback } from 'react'
import { tokenStorage } from '@/api/axiosInstance'

export interface StreamMeta {
  cached?: boolean
  layer?: string
  [k: string]: unknown
}

interface UseStreamOptions {
  onChunk?: (chunk: string) => void
  onMeta?: (meta: StreamMeta) => void
  onError?: (error: Error) => void
  onDone?: () => void
}

interface UseStreamReturn {
  stream: (url: string, body: Record<string, unknown>) => Promise<void>
  isStreaming: boolean
  abort: () => void
}

/**
 * Reads a text/event-stream response token-by-token using the Fetch API.
 * Works with POST requests (unlike native EventSource which is GET-only).
 *
 * Spring WebFlux SSE format:  data:<token>\n\n
 *   ─ NO separator space between "data:" and the value.
 *   ─ So " es" (space-prefixed token) arrives as: data: es
 *   ─ We must slice exactly 5 chars ("data:") and keep the rest verbatim.
 *   ─ Events are separated by \n\n; multi-line data lines are joined with \n.
 */
export function useStream({ onChunk, onMeta, onError, onDone }: UseStreamOptions = {}): UseStreamReturn {
  const [isStreaming, setIsStreaming] = useState(false)
  const abortControllerRef = useRef<AbortController | null>(null)

  const abort = useCallback(() => {
    abortControllerRef.current?.abort()
    setIsStreaming(false)
  }, [])

  const stream = useCallback(
    async (url: string, body: Record<string, unknown>) => {
      abortControllerRef.current?.abort()
      const controller = new AbortController()
      abortControllerRef.current = controller

      setIsStreaming(true)

      try {
        const apiBase = import.meta.env.VITE_API_URL ?? ''
        const response = await fetch(`${apiBase}${url}`, {
          method: 'POST',
          headers: {
            'Content-Type': 'application/json',
            Accept: 'text/event-stream',
            ...(tokenStorage.getAccess()
              ? { Authorization: `Bearer ${tokenStorage.getAccess()}` }
              : {}),
          },
          body: JSON.stringify(body),
          signal: controller.signal,
        })

        if (!response.ok) {
          const raw = await response.text().catch(() => '')
          let serverMsg: string | null = null
          try {
            const body = JSON.parse(raw)
            serverMsg = body?.error ?? body?.message ?? null
          } catch { /* not JSON */ }
          throw new Error(serverMsg ?? raw ?? `HTTP ${response.status}`)
        }

        if (!response.body) throw new Error('Response body is null')

        const reader = response.body.getReader()
        const decoder = new TextDecoder()
        let buffer = ''

        while (true) {
          const { done, value } = await reader.read()
          if (done) break

          buffer += decoder.decode(value, { stream: true })

          // ── SSE event parsing ─────────────────────────────────────────────
          // Events are separated by a blank line (\n\n).
          // Within each event there may be multiple "data:" lines (for tokens
          // that contain newline characters); they must be joined with \n.
          //
          // IMPORTANT: Spring WebFlux writes "data:" + value with NO separator
          // space, so we slice exactly 5 chars and keep the rest verbatim.
          // A token like " es" arrives on the wire as "data: es" — the leading
          // space is part of the token, NOT a protocol separator.
          const events = buffer.split('\n\n')
          buffer = events.pop() ?? ''   // last (potentially incomplete) event

          for (const event of events) {
            if (!event.trim()) continue            // skip blank event separators

            // Collect data: lines and the optional event: name within this event
            const dataLines: string[] = []
            let eventName = 'message'
            for (const line of event.split('\n')) {
              if (line.startsWith('data:')) {
                dataLines.push(line.slice(5))     // slice "data:" (5 chars), no trim
              } else if (line.startsWith('event:')) {
                eventName = line.slice(6).trim()
              }
              // id:, retry:, and : comment lines are intentionally ignored
            }

            if (dataLines.length === 0) continue

            // Re-join multi-line data values (tokens containing \n)
            const data = dataLines.join('\n')

            if (data === '[DONE]') continue        // OpenAI-style stream end
            if (data === '') continue              // empty keep-alive event

            if (eventName === 'meta') {
              try {
                onMeta?.(JSON.parse(data) as StreamMeta)
              } catch {
                /* ignore malformed meta */
              }
              continue
            }

            onChunk?.(data)
          }
        }

        onDone?.()
      } catch (err: unknown) {
        if (err instanceof Error && err.name === 'AbortError') return
        const error = err instanceof Error ? err : new Error(String(err))
        onError?.(error)
      } finally {
        setIsStreaming(false)
      }
    },
    [onChunk, onError, onDone],
  )

  return { stream, isStreaming, abort }
}

