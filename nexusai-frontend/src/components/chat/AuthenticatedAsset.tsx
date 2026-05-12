import { useEffect, useState } from 'react'
import { Loader2, AlertCircle } from 'lucide-react'
import { apiClient } from '@/api/axiosInstance'
import { describeError } from '@/api/errorMessage'

interface AuthenticatedAssetProps {
  /** Backend URL relative to the API base (must be auth-protected). */
  src: string
  alt?: string
  /** Render mode: 'img' for images, 'iframe' for PDFs, 'link' for downloads. */
  as: 'img' | 'iframe'
  className?: string
  style?: React.CSSProperties
  title?: string
}

/**
 * Fetches an authenticated asset from the API with the user's bearer token,
 * turns it into a local blob URL, and renders it as an <img> or <iframe>.
 *
 * Why this exists: <img src> and <iframe src> can't carry an Authorization
 * header. So we have to download the bytes ourselves, then point the element
 * at a blob: URL. The blob URL is revoked on unmount to free memory.
 */
export default function AuthenticatedAsset({
  src,
  alt,
  as,
  className,
  style,
  title,
}: AuthenticatedAssetProps) {
  const [blobUrl, setBlobUrl] = useState<string | null>(null)
  const [error, setError] = useState<string | null>(null)
  const [loading, setLoading] = useState(true)

  useEffect(() => {
    let revoked = false
    let createdUrl: string | null = null
    setLoading(true)
    setError(null)

    apiClient
      .get<Blob>(src, { responseType: 'blob' })
      .then((resp) => {
        if (revoked) return
        createdUrl = URL.createObjectURL(resp.data)
        setBlobUrl(createdUrl)
      })
      .catch((err: unknown) => {
        if (revoked) return
        setError(describeError(err))
      })
      .finally(() => {
        if (!revoked) setLoading(false)
      })

    return () => {
      revoked = true
      if (createdUrl) URL.revokeObjectURL(createdUrl)
    }
  }, [src])

  if (loading) {
    return (
      <div className={className} style={style}>
        <div className="flex items-center justify-center w-full h-full text-[var(--color-text-muted)] gap-2 py-6">
          <Loader2 size={14} className="animate-spin" />
          <span className="text-[11px]">Cargando…</span>
        </div>
      </div>
    )
  }

  if (error || !blobUrl) {
    return (
      <div className={className} style={style}>
        <div className="flex items-center justify-center w-full h-full text-[var(--color-error)] gap-2 py-6">
          <AlertCircle size={14} />
          <span className="text-[11px]">{error ?? 'Sin contenido'}</span>
        </div>
      </div>
    )
  }

  if (as === 'img') {
    return <img src={blobUrl} alt={alt ?? ''} className={className} style={style} title={title} />
  }
  return (
    <iframe
      src={blobUrl}
      title={title ?? alt ?? 'preview'}
      className={className}
      style={style}
    />
  )
}
