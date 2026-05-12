import { useEffect, useState } from 'react'
import {
  MessageSquare,
  FileText,
  Image as ImageIcon,
  Database,
  Zap,
  TrendingUp,
  Activity,
  Loader2,
  RefreshCw,
  ArrowLeft,
} from 'lucide-react'
import { Link } from 'react-router-dom'
import { statsApi, type StatsOverview } from '@/api/statsApi'
import { describeError } from '@/api/errorMessage'

interface CardProps {
  label: string
  value: string | number
  hint?: string
  icon: React.ReactNode
  accent?: boolean
}

function StatCard({ label, value, hint, icon, accent }: CardProps) {
  return (
    <div
      className={
        'glass rounded-2xl border p-3 sm:p-4 min-w-0 ' +
        (accent
          ? 'border-[var(--color-brand-400)]/40 bg-[var(--color-brand-400)]/5'
          : 'border-[var(--color-surface-border)]')
      }
    >
      <div className="flex items-center gap-2 mb-1.5 text-[var(--color-text-muted)] min-w-0">
        <span className="text-[var(--color-brand-400)] shrink-0">{icon}</span>
        <span className="text-[10px] sm:text-[11px] uppercase tracking-wider font-medium truncate">{label}</span>
      </div>
      <p className="text-[20px] sm:text-[24px] font-semibold tabular-nums text-[var(--color-text-primary)] truncate">{value}</p>
      {hint && <p className="text-[11px] text-[var(--color-text-muted)] mt-1 hidden sm:block">{hint}</p>}
    </div>
  )
}

function MiniBarChart({ data }: { data: { day: string; count: number }[] }) {
  if (data.length === 0) {
    return (
      <p className="text-[12px] text-[var(--color-text-muted)] py-8 text-center">
        Sin actividad en los ultimos 7 dias.
      </p>
    )
  }
  const max = Math.max(...data.map((d) => d.count), 1)
  return (
    <div className="flex items-end gap-1 sm:gap-2 h-[120px] sm:h-[140px] px-1 sm:px-2">
      {data.map((d) => {
        const date = new Date(d.day)
        const dayLabel = date.toLocaleDateString('es', { weekday: 'short' })
        const heightPct = (d.count / max) * 100
        return (
          <div key={d.day} className="flex-1 flex flex-col items-center gap-1 sm:gap-1.5 min-w-0">
            <span className="text-[10px] text-[var(--color-text-muted)] tabular-nums">{d.count}</span>
            <div
              className="w-full rounded-t-md bg-gradient-to-t from-[var(--color-brand-400)] to-[var(--color-brand-300)] transition-all"
              style={{ height: `${heightPct}%`, minHeight: '4px' }}
              title={`${date.toLocaleDateString()}: ${d.count} mensajes`}
            />
            <span className="text-[10px] text-[var(--color-text-muted)] capitalize truncate w-full text-center">{dayLabel}</span>
          </div>
        )
      })}
    </div>
  )
}

export default function DashboardPage() {
  const [data, setData] = useState<StatsOverview | null>(null)
  const [loading, setLoading] = useState(true)
  const [error, setError] = useState<string | null>(null)
  const [refreshing, setRefreshing] = useState(false)

  const load = async () => {
    setError(null)
    try {
      const overview = await statsApi.overview()
      setData(overview)
    } catch (e) {
      setError(describeError(e))
    } finally {
      setLoading(false)
      setRefreshing(false)
    }
  }

  useEffect(() => { void load() }, [])

  const handleRefresh = () => { setRefreshing(true); void load() }

  return (
    <div className="flex flex-col h-full overflow-y-auto">
      {/* Header — wraps on small screens; the "actualizado" timestamp is
          hidden on phones where the row would otherwise stretch a second
          line just for the dot + clock. */}
      <div className="glass sticky top-0 z-10 flex flex-wrap items-center justify-between gap-y-1 px-3 sm:px-6 py-2 sm:py-0 sm:h-[52px] shrink-0 border-b border-[var(--color-surface-border)]">
        <div className="flex items-center gap-2 sm:gap-3 min-w-0">
          <Link
            to="/chat"
            className="flex items-center gap-1 text-[12px] text-[var(--color-text-muted)] hover:text-[var(--color-text-primary)] transition-colors shrink-0"
          >
            <ArrowLeft size={13} />
            <span className="hidden sm:inline">Volver al chat</span>
            <span className="sm:hidden">Chat</span>
          </Link>
          <span className="text-[11px] text-[var(--color-surface-border)]">·</span>
          <h1 className="text-[14px] font-semibold text-[var(--color-text-primary)]">Dashboard</h1>
          {data && (
            <span className="hidden md:inline text-[10px] text-[var(--color-text-muted)] font-mono truncate">
              actualizado {new Date(data.generatedAt).toLocaleTimeString()}
            </span>
          )}
        </div>
        <button
          type="button"
          onClick={handleRefresh}
          disabled={refreshing}
          className="flex items-center gap-1.5 px-2.5 py-1 rounded-lg text-[11px] font-medium text-[var(--color-text-secondary)] hover:text-[var(--color-brand-400)] hover:bg-[var(--color-surface-300)] transition-colors cursor-pointer disabled:opacity-40 shrink-0"
        >
          <RefreshCw size={12} className={refreshing ? 'animate-spin' : ''} />
          Refrescar
        </button>
      </div>

      <div className="flex-1 px-3 sm:px-6 py-4 sm:py-6 max-w-5xl mx-auto w-full space-y-5 sm:space-y-6">
        {loading ? (
          <div className="flex items-center justify-center py-20 gap-2 text-[var(--color-text-muted)]">
            <Loader2 size={14} className="animate-spin" />
            <span className="text-[12px]">Cargando estadisticas…</span>
          </div>
        ) : error ? (
          <div className="rounded-xl border border-[var(--color-error)]/30 bg-[var(--color-error)]/5 px-4 py-3 text-[12px] text-[var(--color-error)]">
            {error}
          </div>
        ) : data ? (
          <>
            {/* Usage stats */}
            <section>
              <h2 className="text-[11px] font-semibold text-[var(--color-text-muted)] uppercase tracking-wider mb-3 flex items-center gap-2">
                <Activity size={12} />
                Tu uso
              </h2>
              <div className="grid grid-cols-2 sm:grid-cols-3 lg:grid-cols-5 gap-3">
                <StatCard label="Conversaciones" value={data.usage.conversations} icon={<MessageSquare size={13} />} />
                <StatCard label="Mensajes"       value={data.usage.messages}      icon={<MessageSquare size={13} />} />
                <StatCard label="Adjuntos"       value={data.usage.attachments}   icon={<ImageIcon size={13} />} />
                <StatCard label="Documentos RAG" value={data.usage.ragDocuments}  icon={<FileText size={13} />} />
                <StatCard label="Exports"        value={data.usage.exports}       icon={<TrendingUp size={13} />} />
              </div>
            </section>

            {/* Cache */}
            <section>
              <h2 className="text-[11px] font-semibold text-[var(--color-text-muted)] uppercase tracking-wider mb-3 flex items-center gap-2">
                <Zap size={12} />
                Cache semantica
              </h2>
              <div className="grid grid-cols-2 md:grid-cols-4 gap-3">
                <StatCard
                  label="Hit rate"
                  value={`${data.cache.hitRate}%`}
                  hint="Porcentaje de respuestas servidas desde cache"
                  icon={<Zap size={13} />}
                  accent
                />
                <StatCard label="Hits exactos"   value={data.cache.hitsExact}    hint="Match exacto en Redis" icon={<Zap size={13} />} />
                <StatCard label="Hits semanticos" value={data.cache.hitsSemantic} hint="Match por similaridad" icon={<Zap size={13} />} />
                <StatCard label="Misses"         value={data.cache.misses}       hint="Fueron al LLM" icon={<Zap size={13} />} />
              </div>
            </section>

            {/* LLM */}
            <section>
              <h2 className="text-[11px] font-semibold text-[var(--color-text-muted)] uppercase tracking-wider mb-3 flex items-center gap-2">
                <Database size={12} />
                Modelo LLM
              </h2>
              <div className="grid grid-cols-1 sm:grid-cols-3 gap-3">
                <StatCard label="Llamadas"          value={data.llm.requests}         icon={<Database size={13} />} />
                <StatCard label="Tokens prompt"     value={data.llm.tokensPrompt}     icon={<Database size={13} />} />
                <StatCard label="Tokens completion" value={data.llm.tokensCompletion} icon={<Database size={13} />} />
              </div>
            </section>

            {/* Daily activity chart */}
            <section className="glass rounded-2xl border border-[var(--color-surface-border)] p-4 sm:p-5">
              <div className="flex items-center justify-between mb-4">
                <div>
                  <h2 className="text-[13px] font-semibold text-[var(--color-text-primary)]">Actividad reciente</h2>
                  <p className="text-[11px] text-[var(--color-text-muted)]">Mensajes enviados en los ultimos 7 dias</p>
                </div>
              </div>
              <MiniBarChart data={data.daily} />
            </section>

            {/* Queues */}
            <section>
              <h2 className="text-[11px] font-semibold text-[var(--color-text-muted)] uppercase tracking-wider mb-3">
                Colas RabbitMQ
              </h2>
              <div className="grid grid-cols-2 md:grid-cols-4 gap-3">
                <StatCard label="Document ingest" value={data.queues.documentIngest} hint="Pendientes de procesar" icon={<Activity size={13} />} />
                <StatCard label="Export jobs"     value={data.queues.exportJob}      hint="Pendientes de exportar" icon={<Activity size={13} />} />
                <StatCard label="DLQ documents"   value={data.queues.dlqDocument}    hint="Fallidos" icon={<Activity size={13} />} />
                <StatCard label="DLQ embeddings"  value={data.queues.dlqEmbedding}   hint="Fallidos" icon={<Activity size={13} />} />
              </div>
            </section>
          </>
        ) : null}
      </div>
    </div>
  )
}
