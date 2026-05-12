import { useEffect, useState, useCallback } from 'react'
import { X, FileText, Trash2, Loader2, RefreshCw, ExternalLink, Database } from 'lucide-react'
import { ragApi } from '@/api/ragApi'
import { apiClient } from '@/api/axiosInstance'
import { describeError } from '@/api/errorMessage'
import type { Document } from '@/types/Api'
import clsx from 'clsx'
import Portal from '@/components/layout/Portal'

interface RagDocumentsModalProps {
  onClose: () => void
}

const STATUS_STYLES: Record<Document['status'], string> = {
  PENDING:    'bg-[var(--color-warning)]/15 text-[var(--color-warning)] border-[var(--color-warning)]/30',
  PROCESSING: 'bg-[var(--color-info)]/15    text-[var(--color-info)]    border-[var(--color-info)]/30',
  INDEXED:    'bg-[var(--color-success)]/15 text-[var(--color-success)] border-[var(--color-success)]/30',
  FAILED:     'bg-[var(--color-error)]/15   text-[var(--color-error)]   border-[var(--color-error)]/30',
}

export default function RagDocumentsModal({ onClose }: RagDocumentsModalProps) {
  const [docs, setDocs] = useState<Document[]>([])
  const [loading, setLoading] = useState(true)
  const [deletingId, setDeletingId] = useState<string | null>(null)
  const [error, setError] = useState<string | null>(null)

  const load = useCallback(async () => {
    setLoading(true)
    setError(null)
    try {
      const list = await ragApi.list()
      setDocs(list)
    } catch (e) {
      setError(describeError(e))
    } finally {
      setLoading(false)
    }
  }, [])

  useEffect(() => { void load() }, [load])

  const handleDelete = async (id: string) => {
    if (!confirm('Eliminar este documento del indice RAG?')) return
    setDeletingId(id)
    try {
      await ragApi.delete(id)
      setDocs((d) => d.filter((doc) => doc.id !== id))
    } catch (e) {
      alert(`Error al eliminar: ${describeError(e)}`)
    } finally {
      setDeletingId(null)
    }
  }

  const handleOpen = async (doc: Document) => {
    try {
      const resp = await apiClient.get<Blob>(`/api/rag/documents/${doc.id}/file`, { responseType: 'blob' })
      const url = URL.createObjectURL(resp.data)
      window.open(url, '_blank', 'noopener,noreferrer')
      // Note: not revoking immediately so the new tab can load. Browser GC's it.
    } catch (e) {
      alert(`No se pudo abrir: ${describeError(e)}`)
    }
  }

  return (
    <Portal>
    <div
      className="fixed inset-0 z-50 flex items-center justify-center bg-black/55 backdrop-blur-md"
      onClick={(e) => e.target === e.currentTarget && onClose()}
    >
      <div className="glass-modal w-full max-w-xl mx-4 max-h-[80vh] flex flex-col rounded-3xl border border-[var(--color-surface-border)] shadow-2xl shadow-black/50 overflow-hidden">
        <div className="flex items-center justify-between px-5 py-4 border-b border-[var(--color-surface-border)] shrink-0">
          <div className="flex items-center gap-2">
            <Database size={14} className="text-[var(--color-brand-500)]" />
            <h2 className="font-semibold text-[var(--color-text-primary)] text-[14px]">
              Documentos RAG
            </h2>
            <span className="text-[11px] text-[var(--color-text-muted)]">({docs.length})</span>
          </div>
          <div className="flex items-center gap-1">
            <button
              onClick={load}
              disabled={loading}
              title="Refrescar"
              className="p-1.5 rounded-lg text-[var(--color-text-muted)] hover:text-[var(--color-text-primary)] hover:bg-[var(--color-surface-300)] transition-colors cursor-pointer disabled:opacity-40"
            >
              <RefreshCw size={13} className={loading ? 'animate-spin' : ''} />
            </button>
            <button
              onClick={onClose}
              className="p-1.5 rounded-lg text-[var(--color-text-muted)] hover:text-[var(--color-text-primary)] hover:bg-[var(--color-surface-300)] transition-colors cursor-pointer"
            >
              <X size={15} />
            </button>
          </div>
        </div>

        <div className="flex-1 overflow-y-auto px-3 py-3">
          {loading && docs.length === 0 ? (
            <div className="flex items-center justify-center py-10 gap-2 text-[var(--color-text-muted)]">
              <Loader2 size={14} className="animate-spin" />
              <span className="text-[12px]">Cargando documentos…</span>
            </div>
          ) : error ? (
            <p className="text-[12px] text-[var(--color-error)] px-3 py-4">{error}</p>
          ) : docs.length === 0 ? (
            <div className="flex flex-col items-center justify-center py-12 gap-2 text-[var(--color-text-muted)]">
              <FileText size={20} className="opacity-40" />
              <p className="text-[12px]">No hay documentos indexados todavia.</p>
              <p className="text-[11px] opacity-70">Adjunta un PDF en el chat y pulsa &quot;Indexar&quot;.</p>
            </div>
          ) : (
            <ul className="space-y-1">
              {docs.map((doc) => (
                <li
                  key={doc.id}
                  className="flex items-center gap-2 px-3 py-2 rounded-lg hover:bg-[var(--color-surface-300)] transition-colors group"
                >
                  <FileText size={13} className="text-[var(--color-brand-500)] shrink-0" />
                  <div className="flex-1 min-w-0">
                    <p className="text-[12px] text-[var(--color-text-primary)] truncate">{doc.filename}</p>
                    <p className="text-[10px] text-[var(--color-text-muted)] font-mono truncate">{doc.id}</p>
                  </div>
                  <span
                    className={clsx(
                      'shrink-0 px-1.5 py-0.5 rounded-md text-[10px] font-medium border',
                      STATUS_STYLES[doc.status],
                    )}
                  >
                    {doc.status}
                  </span>
                  <button
                    onClick={() => handleOpen(doc)}
                    title="Abrir archivo"
                    className="shrink-0 p-1 rounded text-[var(--color-text-muted)] hover:text-[var(--color-brand-500)] transition-colors cursor-pointer opacity-0 group-hover:opacity-100"
                  >
                    <ExternalLink size={12} />
                  </button>
                  <button
                    onClick={() => handleDelete(doc.id)}
                    disabled={deletingId === doc.id}
                    title="Eliminar del indice RAG"
                    className="shrink-0 p-1 rounded text-[var(--color-text-muted)] hover:text-[var(--color-error)] transition-colors cursor-pointer disabled:opacity-40 opacity-0 group-hover:opacity-100"
                  >
                    {deletingId === doc.id ? <Loader2 size={12} className="animate-spin" /> : <Trash2 size={12} />}
                  </button>
                </li>
              ))}
            </ul>
          )}
        </div>

        <div className="px-5 py-3 border-t border-[var(--color-surface-border)] shrink-0">
          <p className="text-[11px] text-[var(--color-text-muted)] leading-relaxed">
            Los documentos eliminados aqui se sacan del indice vectorial; las preguntas
            futuras sobre ellos ya no recuperaran contexto.
          </p>
        </div>
      </div>
    </div>
    </Portal>
  )
}
