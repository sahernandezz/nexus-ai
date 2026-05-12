import { apiClient } from './axiosInstance'

export interface GeneratedImage {
  /** Convenience GET path. Equivalent to `/api/files/{refId}` when refId is set. */
  url: string
  /** Stable id of the persisted MinIO attachment (preferred for chat storage). */
  refId?: string
  refKind?: 'attachment'
  filename?: string
  contentType?: string
  size?: number
}

export const multimodalApi = {
  /**
   * POST /api/multimodal/generate — text → image. Backend persists the bytes
   * to MinIO and returns a {@code refId}, so the chat can reference the
   * image after a reload (DALL-E hosted URLs expire in ~1h, base64 data URLs
   * blow up the persisted store).
   */
  generateImage: (
    prompt: string,
    size = '1024x1024',
    quality = 'standard',
    model?: string,
    conversationId?: string,
  ) =>
    apiClient
      .post<GeneratedImage>(
        '/api/multimodal/generate',
        {
          prompt,
          size,
          quality,
          ...(model ? { model } : {}),
          ...(conversationId ? { conversationId } : {}),
        },
        { timeout: 240_000 },
      )
      .then((r) => r.data),
}
