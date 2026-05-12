import { describe, expect, it, beforeEach, vi } from 'vitest'
import { act, renderHook, waitFor } from '@testing-library/react'
import { useStream } from './useStream'

/**
 * Builds a Response with a ReadableStream body that yields each chunk
 * verbatim. Used to simulate Spring WebFlux SSE output.
 */
function sseResponse(chunks: string[], status = 200): Response {
  const encoder = new TextEncoder()
  const stream = new ReadableStream<Uint8Array>({
    start(controller) {
      for (const c of chunks) controller.enqueue(encoder.encode(c))
      controller.close()
    },
  })
  return new Response(stream, {
    status,
    headers: { 'Content-Type': 'text/event-stream' },
  })
}

beforeEach(() => {
  vi.restoreAllMocks()
})

describe('useStream', () => {
  it('parses default-event chunks via onChunk', async () => {
    vi.spyOn(globalThis, 'fetch').mockResolvedValue(
      sseResponse(['data:Hello\n\n', 'data: world\n\n']),
    )

    const onChunk = vi.fn()
    const onDone = vi.fn()
    const { result } = renderHook(() => useStream({ onChunk, onDone }))

    await act(async () => {
      await result.current.stream('/api/chat/stream', { message: 'hi' })
    })

    // First chunk: "data:Hello" → "Hello"
    // Second chunk: "data: world" → " world" (the space is part of the token)
    expect(onChunk).toHaveBeenNthCalledWith(1, 'Hello')
    expect(onChunk).toHaveBeenNthCalledWith(2, ' world')
    expect(onDone).toHaveBeenCalledOnce()
  })

  it('routes "event: meta" chunks to onMeta as JSON', async () => {
    vi.spyOn(globalThis, 'fetch').mockResolvedValue(
      sseResponse([
        'event:meta\ndata:{"cached":true,"layer":"exact"}\n\n',
        'event:message\ndata:hola\n\n',
      ]),
    )

    const onChunk = vi.fn()
    const onMeta = vi.fn()
    const { result } = renderHook(() => useStream({ onChunk, onMeta }))

    await act(async () => {
      await result.current.stream('/api/chat/stream', { message: 'hi' })
    })

    expect(onMeta).toHaveBeenCalledExactlyOnceWith({
      cached: true,
      layer: 'exact',
    })
    expect(onChunk).toHaveBeenCalledExactlyOnceWith('hola')
  })

  it('treats unnamed events as "message" by default', async () => {
    vi.spyOn(globalThis, 'fetch').mockResolvedValue(
      sseResponse(['data:plain\n\n']),
    )

    const onChunk = vi.fn()
    const onMeta = vi.fn()
    const { result } = renderHook(() => useStream({ onChunk, onMeta }))

    await act(async () => {
      await result.current.stream('/api/chat/stream', {})
    })

    expect(onChunk).toHaveBeenCalledExactlyOnceWith('plain')
    expect(onMeta).not.toHaveBeenCalled()
  })

  it('joins multi-line data: values with \\n', async () => {
    vi.spyOn(globalThis, 'fetch').mockResolvedValue(
      sseResponse(['data:line one\ndata:line two\n\n']),
    )

    const onChunk = vi.fn()
    const { result } = renderHook(() => useStream({ onChunk }))

    await act(async () => {
      await result.current.stream('/api/chat/stream', {})
    })

    expect(onChunk).toHaveBeenCalledExactlyOnceWith('line one\nline two')
  })

  it('skips [DONE] sentinel and empty keep-alives', async () => {
    vi.spyOn(globalThis, 'fetch').mockResolvedValue(
      sseResponse([
        'data:hi\n\n',
        'data:[DONE]\n\n',
        'data:\n\n',
      ]),
    )

    const onChunk = vi.fn()
    const { result } = renderHook(() => useStream({ onChunk }))

    await act(async () => {
      await result.current.stream('/api/chat/stream', {})
    })

    expect(onChunk).toHaveBeenCalledExactlyOnceWith('hi')
  })

  it('extracts body.error from a 4xx response and forwards it via onError', async () => {
    vi.spyOn(globalThis, 'fetch').mockResolvedValue(
      new Response('{"error":"el mensaje no puede estar vacio"}', {
        status: 400,
        headers: { 'Content-Type': 'application/json' },
      }),
    )

    const onError = vi.fn()
    const { result } = renderHook(() => useStream({ onError }))

    await act(async () => {
      await result.current.stream('/api/chat/stream', {})
    })

    await waitFor(() => expect(onError).toHaveBeenCalled())
    expect((onError.mock.calls[0][0] as Error).message).toBe(
      'el mensaje no puede estar vacio',
    )
  })

  it('falls back to status text when the body is not JSON', async () => {
    vi.spyOn(globalThis, 'fetch').mockResolvedValue(
      new Response('Internal kaboom', { status: 500 }),
    )

    const onError = vi.fn()
    const { result } = renderHook(() => useStream({ onError }))

    await act(async () => {
      await result.current.stream('/api/chat/stream', {})
    })

    await waitFor(() => expect(onError).toHaveBeenCalled())
    expect((onError.mock.calls[0][0] as Error).message).toBe('Internal kaboom')
  })

  it('toggles isStreaming around a successful run', async () => {
    vi.spyOn(globalThis, 'fetch').mockResolvedValue(
      sseResponse(['data:ok\n\n']),
    )
    const { result } = renderHook(() => useStream())

    expect(result.current.isStreaming).toBe(false)
    await act(async () => {
      await result.current.stream('/api/chat/stream', {})
    })
    expect(result.current.isStreaming).toBe(false)
  })
})
