// ─── Generic API Types ────────────────────────────────────────────────────────

export interface ApiError {
  error: string
  message?: string
  status?: number
  path?: string
  timestamp?: string
}

export interface PagedResponse<T> {
  content: T[]
  totalElements: number
  totalPages: number
  size: number
  number: number
  first: boolean
  last: boolean
}

export interface HealthResponse {
  status: 'UP' | 'DOWN'
  version: string
  environment: string
  timestamp: string
}

// ─── Chat Types ──────────────────────────────────────────────────────────

export type Language = 'auto' | 'es' | 'en'

export interface MessageAttachment {
  type: 'image' | 'pdf' | 'file'
  name: string
  /**
   * Inline URL for the asset. Three accepted formats:
   *  - data: URL (small images embedded in the message — persists in localStorage)
   *  - blob:/object URL (ephemeral, only in current tab)
   *  - http(s) URL (e.g. external image generation results)
   */
  url?: string
  /**
   * Persistent reference to an asset stored in MinIO via the backend.
   * When set, the renderer fetches the asset with the user's bearer token
   * and turns it into a blob URL on the fly — survives reloads and chat
   * switches because the metadata is enough to re-fetch.
   */
  refId?: string
  refKind?: 'rag-doc' | 'attachment'
  size: number
  mimeType: string
}

export type RagActionState = 'pending' | 'indexing' | 'indexed' | 'skipped'

export interface RagAction {
  state: RagActionState
  filename: string
  docId?: string
}

export interface ChatMessage {
  id: string
  role: 'user' | 'assistant' | 'system'
  content: string
  createdAt: string
  cached?: boolean
  cacheLayer?: string
  model?: string
  attachments?: MessageAttachment[]
  ragAction?: RagAction
}

export interface Conversation {
  id: string
  title: string
  sessionId: string
  messages: ChatMessage[]
  createdAt: string
  updatedAt: string
}

// ─── Document Types (skeleton for M3) ────────────────────────────────────────

export type DocumentStatus = 'PENDING' | 'PROCESSING' | 'INDEXED' | 'FAILED'

export interface Document {
  id: string
  filename: string
  contentType: string
  sizeBytes: number
  status: DocumentStatus
  errorMsg?: string
  createdAt: string
  updatedAt: string
}

// ─── Agent Types (M6) ────────────────────────────────────────────────────────

export type ToolStatus = 'running' | 'done' | 'error'

export interface ToolCall {
  id: string
  toolName: string
  input: Record<string, unknown>
  output?: string
  status: ToolStatus
  durationMs?: number
}

export interface AgentRunResponse {
  content: string
}

// ─── Metrics Types (M8) ──────────────────────────────────────────────────────

export interface LlmMetrics {
  requests_total: number
  errors_total: number
  tokens_prompt: number
  tokens_completion: number
  latency_p95_ms: number
}

export interface CacheMetrics {
  hits_exact: number
  hits_semantic: number
  misses: number
}

export interface QueueMetrics {
  document_ingest: number
  embedding: number
  dlq_document: number
}

export interface MetricsSummary {
  llm: LlmMetrics
  cache: CacheMetrics
  queues: QueueMetrics
}

