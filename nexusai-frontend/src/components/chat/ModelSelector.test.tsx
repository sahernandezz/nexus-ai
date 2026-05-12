import { describe, expect, it, vi } from 'vitest'
import { render, screen } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import ModelSelector, { CHAT_MODELS, IMAGE_MODELS } from './ModelSelector'

interface RenderOpts {
  chatModel?: string
  imageModel?: string
  isImageMode?: boolean
  disabled?: boolean
}

function renderSelector(opts: RenderOpts = {}) {
  const onChat = vi.fn()
  const onImg = vi.fn()
  const utils = render(
    <ModelSelector
      chatModel={opts.chatModel ?? 'gpt-4.1'}
      imageModel={opts.imageModel ?? 'gpt-image-1'}
      onChatModelChange={onChat}
      onImageModelChange={onImg}
      isImageMode={opts.isImageMode}
      disabled={opts.disabled}
    />,
  )
  return { ...utils, onChat, onImg }
}

describe('ModelSelector', () => {
  it('shows the current chat model label', () => {
    renderSelector({ chatModel: 'gpt-4.1' })
    expect(screen.getByRole('button', { name: /GPT-4\.1/ })).toBeInTheDocument()
  })

  it('shows the current image model when in image mode', () => {
    renderSelector({ imageModel: 'dall-e-3', isImageMode: true })
    expect(screen.getByRole('button', { name: /DALL-E 3/ })).toBeInTheDocument()
  })

  it('opens the dropdown and lists all chat models', async () => {
    renderSelector()
    const user = userEvent.setup()
    await user.click(screen.getByRole('button'))

    // Header
    expect(screen.getByText('Modelo de chat')).toBeInTheDocument()
    // All chat-mode options visible (the active model appears twice — in the
    // trigger label and inside the dropdown panel — so use getAllByText).
    for (const m of CHAT_MODELS) {
      expect(screen.getAllByText(m.label).length).toBeGreaterThanOrEqual(1)
    }
  })

  it('lists image models in image mode', async () => {
    renderSelector({ isImageMode: true })
    const user = userEvent.setup()
    await user.click(screen.getByRole('button'))
    expect(screen.getByText('Modelo de imagen')).toBeInTheDocument()
    for (const m of IMAGE_MODELS) {
      expect(screen.getAllByText(m.label).length).toBeGreaterThanOrEqual(1)
    }
    // None of the chat-only models leak in
    expect(screen.queryByText('GPT-4o Mini')).not.toBeInTheDocument()
  })

  it('calls onChatModelChange when a chat option is clicked', async () => {
    const { onChat, onImg } = renderSelector({ chatModel: 'gpt-4.1' })
    const user = userEvent.setup()
    await user.click(screen.getByRole('button'))
    await user.click(screen.getByText('GPT-4o Mini'))
    expect(onChat).toHaveBeenCalledExactlyOnceWith('gpt-4o-mini')
    expect(onImg).not.toHaveBeenCalled()
  })

  it('calls onImageModelChange when an image option is clicked', async () => {
    const { onChat, onImg } = renderSelector({
      imageModel: 'gpt-image-1',
      isImageMode: true,
    })
    const user = userEvent.setup()
    await user.click(screen.getByRole('button'))
    await user.click(screen.getByText('DALL-E 3'))
    expect(onImg).toHaveBeenCalledExactlyOnceWith('dall-e-3')
    expect(onChat).not.toHaveBeenCalled()
  })

  it('does not open when disabled', async () => {
    renderSelector({ disabled: true })
    const user = userEvent.setup()
    await user.click(screen.getByRole('button'))
    expect(screen.queryByText('Modelo de chat')).not.toBeInTheDocument()
  })
})
