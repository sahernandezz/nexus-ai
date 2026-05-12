import { describe, expect, it, vi } from 'vitest'
import { render, screen } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import MessageBubble from './MessageBubble'
import type { ChatMessage } from '@/types/Api'

// AuthenticatedAsset hits apiClient internally; we don't want real fetches in
// these tests. We render a stub so we can just assert the wiring.
vi.mock('./AuthenticatedAsset', () => ({
  default: ({ src, as }: { src: string; as: string }) => (
    <div data-testid="authed-asset" data-src={src} data-kind={as} />
  ),
}))

// Stub the axios client so the Download button doesn't try to hit the network.
vi.mock('@/api/axiosInstance', () => ({
  apiClient: {
    get: vi.fn().mockResolvedValue({ data: new Blob() }),
  },
  tokenStorage: {
    getAccess: vi.fn(),
    setAccess: vi.fn(),
    getRefresh: vi.fn(),
    setRefresh: vi.fn(),
    clear: vi.fn(),
  },
}))

const baseUser = (overrides: Partial<ChatMessage> = {}): ChatMessage => ({
  id: crypto.randomUUID(),
  role: 'user',
  content: '',
  createdAt: '2026-01-01T00:00:00Z',
  ...overrides,
})

const baseAssistant = (overrides: Partial<ChatMessage> = {}): ChatMessage => ({
  id: crypto.randomUUID(),
  role: 'assistant',
  content: '',
  createdAt: '2026-01-01T00:00:00Z',
  ...overrides,
})

describe('MessageBubble', () => {
  it('renders user text', () => {
    render(<MessageBubble message={baseUser({ content: 'hola mundo' })} />)
    expect(screen.getByText('hola mundo')).toBeInTheDocument()
  })

  it('renders assistant markdown content', () => {
    render(
      <MessageBubble
        message={baseAssistant({ content: 'Esto es **negrita**' })}
      />,
    )
    // ReactMarkdown renders <strong> for **bold**
    const bold = screen.getByText('negrita')
    expect(bold.tagName).toBe('STRONG')
  })

  it('shows the cache badge when message.cached is true', () => {
    render(
      <MessageBubble
        message={baseAssistant({ content: 'hola', cached: true, cacheLayer: 'exact' })}
      />,
    )
    expect(screen.getByText(/Cache · exact/)).toBeInTheDocument()
  })

  it('omits the cache badge when not cached', () => {
    render(
      <MessageBubble message={baseAssistant({ content: 'hola' })} />,
    )
    expect(screen.queryByText(/Cache/)).not.toBeInTheDocument()
  })

  describe('attachments', () => {
    it('renders an inline image with a data URL', () => {
      const msg = baseUser({
        attachments: [
          {
            type: 'image',
            name: 'pic.png',
            url: 'data:image/png;base64,abc',
            size: 100,
            mimeType: 'image/png',
          },
        ],
      })
      render(<MessageBubble message={msg} />)
      const img = screen.getByRole('img', { name: 'pic.png' })
      expect(img).toHaveAttribute('src', 'data:image/png;base64,abc')
    })

    it('renders RAG-doc attachments through AuthenticatedAsset', () => {
      const msg = baseUser({
        attachments: [
          {
            type: 'pdf',
            name: 'paper.pdf',
            refId: 'doc-123',
            refKind: 'rag-doc',
            size: 200,
            mimeType: 'application/pdf',
          },
        ],
      })
      render(<MessageBubble message={msg} />)
      const asset = screen.getByTestId('authed-asset')
      expect(asset.getAttribute('data-src')).toBe('/api/rag/documents/doc-123/file')
      expect(asset.getAttribute('data-kind')).toBe('iframe')
    })

    it('renders generic attachments with a Download button that hits the API', async () => {
      const { apiClient } = await import('@/api/axiosInstance')
      const msg = baseAssistant({
        attachments: [
          {
            type: 'file',
            name: 'export.zip',
            refId: 'file-1',
            refKind: 'attachment',
            size: 0,
            mimeType: 'application/zip',
          },
        ],
      })
      render(<MessageBubble message={msg} />)
      const btn = screen.getByRole('button', { name: /Descargar/i })
      const user = userEvent.setup()
      await user.click(btn)
      expect(apiClient.get).toHaveBeenCalledWith(
        '/api/files/file-1',
        { responseType: 'blob' },
      )
    })
  })

  describe('rag action card', () => {
    it('renders the pending state with Indexar/Omitir buttons', async () => {
      const onIndex = vi.fn()
      const onSkip = vi.fn()
      const msg: ChatMessage = {
        id: 'rag-msg',
        role: 'system',
        content: '',
        createdAt: '2026-01-01T00:00:00Z',
        ragAction: { state: 'pending', filename: 'thesis.pdf' },
      }
      render(
        <MessageBubble message={msg} onRagIndex={onIndex} onRagSkip={onSkip} />,
      )
      const user = userEvent.setup()
      await user.click(screen.getByRole('button', { name: /Indexar/i }))
      expect(onIndex).toHaveBeenCalledWith('rag-msg')
      await user.click(screen.getByRole('button', { name: /Omitir/i }))
      expect(onSkip).toHaveBeenCalledWith('rag-msg')
    })

    it('shows a spinner during the indexing state', () => {
      const msg: ChatMessage = {
        id: 'rag-msg',
        role: 'system',
        content: '',
        createdAt: '2026-01-01T00:00:00Z',
        ragAction: { state: 'indexing', filename: 'thesis.pdf' },
      }
      render(<MessageBubble message={msg} />)
      expect(screen.getByText(/Indexando/i)).toBeInTheDocument()
    })

    it('shows the success state when indexed', () => {
      const msg: ChatMessage = {
        id: 'rag-msg',
        role: 'system',
        content: '',
        createdAt: '2026-01-01T00:00:00Z',
        ragAction: { state: 'indexed', filename: 'thesis.pdf', docId: 'd' },
      }
      render(<MessageBubble message={msg} />)
      expect(screen.getByText(/Indexado correctamente/i)).toBeInTheDocument()
    })
  })

  it('renders system hint messages as plain italic line', () => {
    const msg: ChatMessage = {
      id: 'sys',
      role: 'system',
      content: 'Indexa el documento primero',
      createdAt: '2026-01-01T00:00:00Z',
    }
    render(<MessageBubble message={msg} />)
    expect(
      screen.getByText('Indexa el documento primero'),
    ).toBeInTheDocument()
  })
})
