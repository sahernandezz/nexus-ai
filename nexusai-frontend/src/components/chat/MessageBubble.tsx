import clsx from "clsx";
import ReactMarkdown from "react-markdown";
import remarkGfm from "remark-gfm";
import {
  FileText,
  Database,
  Check,
  X,
  Loader2,
  Zap,
  Download,
} from "lucide-react";
import type { ChatMessage, MessageAttachment } from "@/types/Api";
import AuthenticatedAsset from "./AuthenticatedAsset";
import { apiClient } from "@/api/axiosInstance";

function refSrc(att: MessageAttachment): string | null {
  if (att.refKind === "rag-doc" && att.refId)
    return `/api/rag/documents/${att.refId}/file`;
  if (att.refKind === "attachment" && att.refId)
    return `/api/files/${att.refId}`;
  return null;
}

async function downloadRef(att: MessageAttachment) {
  const src = refSrc(att);
  if (!src) return;
  const resp = await apiClient.get<Blob>(src, { responseType: "blob" });
  const url = URL.createObjectURL(resp.data);
  const a = document.createElement("a");
  a.href = url;
  a.download = att.name;
  document.body.appendChild(a);
  a.click();
  a.remove();
  setTimeout(() => URL.revokeObjectURL(url), 60_000);
}
interface MessageBubbleProps {
  message: ChatMessage;
  isStreaming?: boolean;
  onRagIndex?: (messageId: string) => void;
  onRagSkip?: (messageId: string) => void;
}

export default function MessageBubble({
  message,
  isStreaming,
  onRagIndex,
  onRagSkip,
}: MessageBubbleProps) {
  const isUser = message.role === "user";
  const isAssistant = message.role === "assistant";
  const isSystem = message.role === "system";

  // ── System hint (no RAG action, just informational text) ───────────────────
  if (isSystem && !message.ragAction && message.content) {
    return (
      <div className="flex justify-center py-1.5">
        <p className="text-[11px] text-[var(--color-text-muted)] italic">
          {message.content}
        </p>
      </div>
    );
  }

  // ── RAG action card ────────────────────────────────────────────────────────
  if (isSystem && message.ragAction) {
    const { state, filename } = message.ragAction;
    return (
      <div className="flex justify-center py-2">
        <div className="max-w-sm w-full rounded-xl border border-[var(--color-brand-600)]/25 bg-[var(--color-brand-600)]/5 px-4 py-3">
          <div className="flex items-start gap-2.5">
            <Database
              size={14}
              className="text-[var(--color-brand-500)] shrink-0 mt-0.5"
            />
            <div className="flex-1 min-w-0">
              <p className="text-[12px] font-medium text-[var(--color-text-primary)] leading-snug">
                Indexar en RAG
              </p>
              <p className="text-[11px] text-[var(--color-text-muted)] truncate mt-0.5">
                {filename}
              </p>
              {state === "pending" && (
                <div className="flex gap-2 mt-2.5">
                  <button
                    onClick={() => onRagIndex?.(message.id)}
                    className="flex items-center gap-1.5 px-3 py-1.5 rounded-lg bg-[var(--color-brand-400)] hover:bg-[var(--color-brand-500)] text-black text-[11px] font-medium transition-colors cursor-pointer"
                  >
                    <Check size={11} /> Indexar
                  </button>
                  <button
                    onClick={() => onRagSkip?.(message.id)}
                    className="flex items-center gap-1.5 px-3 py-1.5 rounded-lg bg-[var(--color-surface-300)] hover:bg-[var(--color-surface-400)] text-[var(--color-text-secondary)] text-[11px] font-medium transition-colors cursor-pointer"
                  >
                    <X size={11} /> Omitir
                  </button>
                </div>
              )}
              {state === "indexing" && (
                <div className="flex items-center gap-1.5 mt-2 text-[11px] text-[var(--color-brand-500)]">
                  <Loader2 size={11} className="animate-spin" /> Indexando...
                </div>
              )}
              {state === "indexed" && (
                <div className="flex items-center gap-1.5 mt-2 text-[11px] text-[var(--color-success)]">
                  <Check size={11} /> Indexado correctamente
                </div>
              )}
              {state === "skipped" && (
                <p className="mt-1 text-[11px] text-[var(--color-text-muted)]">
                  Omitido
                </p>
              )}
              {message.content && (
                <p className="mt-2 text-[11px] text-[var(--color-error)] leading-snug">
                  {message.content}
                </p>
              )}
            </div>
          </div>
        </div>
      </div>
    );
  }

  return (
    <div className={clsx("flex gap-3 py-3", isUser && "flex-row-reverse")}>
      {/* Avatar */}
      <div
        className={clsx(
          "flex-shrink-0 w-7 h-7 rounded-full flex items-center justify-center text-[11px] font-semibold mt-0.5",
          isUser
            ? "bg-[var(--color-surface-400)] text-[var(--color-text-secondary)]"
            : "bg-[var(--color-brand-400)] text-black",
        )}
      >
        {isUser ? "U" : "✦"}
      </div>

      {/* Content */}
      <div
        className={clsx(
          // Wider bubbles on small viewports so a 360px screen isn't crammed
          // into a 288px column with the avatar gap eating the rest.
          "min-w-0 max-w-[85%] sm:max-w-[80%] space-y-1.5",
          isUser && "items-end flex flex-col",
        )}
      >
        {message.attachments && message.attachments.length > 0 && (
          <div className={clsx("flex flex-col gap-2", isUser && "items-end")}>
            {message.attachments.map((att, i) => {
              const persistent = refSrc(att);
              if (att.type === "image") {
                if (persistent) {
                  return (
                    <AuthenticatedAsset
                      key={i}
                      as="img"
                      src={persistent}
                      alt={att.name}
                      className="w-full max-w-[320px] max-h-[260px] rounded-xl object-cover border border-[var(--color-surface-border)]"
                    />
                  );
                }
                if (att.url) {
                  return (
                    <img
                      key={i}
                      src={att.url}
                      alt={att.name}
                      className="w-full max-w-[320px] max-h-[260px] rounded-xl object-cover border border-[var(--color-surface-border)]"
                    />
                  );
                }
              }
              if (att.type === "pdf") {
                if (persistent) {
                  return (
                    <div
                      key={i}
                      className="rounded-xl border border-[var(--color-surface-border)] overflow-hidden w-full"
                      style={{ maxWidth: 320 }}
                    >
                      <div className="flex items-center gap-2 px-3 py-2 bg-[var(--color-surface-300)] border-b border-[var(--color-surface-border)]">
                        <FileText
                          size={12}
                          className="text-[var(--color-brand-500)] shrink-0"
                        />
                        <span className="text-[11px] text-[var(--color-text-primary)] truncate flex-1">
                          {att.name}
                        </span>
                      </div>
                      <AuthenticatedAsset
                        as="iframe"
                        src={persistent}
                        title={att.name}
                        className="w-full"
                        style={{
                          height: 200,
                          border: "none",
                          background: "#1a1a1a",
                        }}
                      />
                    </div>
                  );
                }
                if (att.url) {
                  return (
                    <div
                      key={i}
                      className="rounded-xl border border-[var(--color-surface-border)] overflow-hidden w-full"
                      style={{ maxWidth: 320 }}
                    >
                      <div className="flex items-center gap-2 px-3 py-2 bg-[var(--color-surface-300)] border-b border-[var(--color-surface-border)]">
                        <FileText
                          size={12}
                          className="text-[var(--color-brand-500)] shrink-0"
                        />
                        <span className="text-[11px] text-[var(--color-text-primary)] truncate flex-1">
                          {att.name}
                        </span>
                        <a
                          href={att.url}
                          target="_blank"
                          rel="noopener noreferrer"
                          className="text-[10px] text-[var(--color-brand-400)] hover:underline shrink-0"
                        >
                          Abrir
                        </a>
                      </div>
                      <iframe
                        src={att.url}
                        title={att.name}
                        className="w-full"
                        style={{
                          height: 200,
                          border: "none",
                          background: "#1a1a1a",
                        }}
                      />
                    </div>
                  );
                }
              }
              return (
                <div
                  key={i}
                  className="flex items-center gap-2 px-3 py-2 rounded-xl bg-[var(--color-surface-300)] border border-[var(--color-surface-border)] text-[12px]"
                >
                  <FileText
                    size={14}
                    className="text-[var(--color-brand-500)] shrink-0"
                  />
                  <span className="text-[var(--color-text-primary)] truncate max-w-[200px]">
                    {att.name}
                  </span>
                  {att.size > 0 && (
                    <span className="text-[var(--color-text-muted)] shrink-0">
                      {(att.size / 1024).toFixed(0)} KB
                    </span>
                  )}
                  {persistent && (
                    <button
                      onClick={() => {
                        downloadRef(att).catch((err) => {
                          console.error('[MessageBubble] download failed', err)
                          alert('No se pudo descargar el archivo. Por favor, inténtalo de nuevo.')
                        })
                      }}
                      title="Descargar"
                      className="ml-auto flex items-center gap-1 px-2 py-1 rounded-lg text-[11px] font-medium bg-[var(--color-brand-400)] text-black hover:bg-[var(--color-brand-500)] transition-colors cursor-pointer"
                    >
                      <Download size={11} />
                      Descargar
                    </button>
                  )}
                </div>
              );
            })}
          </div>
        )}

        {/* Text content */}
        {message.content ? (
          isUser ? (
            <div className="px-3.5 py-2.5 rounded-2xl rounded-tr-sm text-[13px] leading-relaxed bg-[var(--color-surface-300)] text-[var(--color-text-primary)] whitespace-pre-wrap break-words">
              {message.content}
            </div>
          ) : isAssistant ? (
            <div
              className={clsx(
                "prose-chat text-[13px] relative",
                isStreaming && "streaming-cursor",
              )}
            >
              {message.cached && (
                <span
                  title={`Respuesta servida desde cache (${message.cacheLayer ?? "exact"})`}
                  className="inline-flex items-center gap-1 px-1.5 py-0.5 mb-1.5 rounded-md text-[10px] font-medium bg-[var(--color-brand-600)]/15 text-[var(--color-brand-400)] border border-[var(--color-brand-600)]/30"
                >
                  <Zap size={9} />
                  Cache{message.cacheLayer ? ` · ${message.cacheLayer}` : ""}
                </span>
              )}
              <ReactMarkdown
                remarkPlugins={[remarkGfm]}
                components={{
                  p: ({ children }) => (
                    <p className="mb-2 last:mb-0 whitespace-pre-wrap">
                      {children}
                    </p>
                  ),
                  strong: ({ children }) => (
                    <strong className="font-semibold">{children}</strong>
                  ),
                  em: ({ children }) => <em className="italic">{children}</em>,
                  code: ({ children, className }) => {
                    const isBlock = className?.includes("language-");
                    return isBlock ? (
                      <code
                        className={clsx(
                          "block bg-black/25 rounded-lg px-3 py-2 text-[0.8em] font-mono my-2 overflow-x-auto whitespace-pre",
                          className,
                        )}
                      >
                        {children}
                      </code>
                    ) : (
                      <code className="bg-[var(--color-brand-600)]/12 text-[var(--color-brand-400)] rounded px-1 py-0.5 text-[0.85em] font-mono">
                        {children}
                      </code>
                    );
                  },
                  pre: ({ children }) => (
                    <pre className="bg-black/25 border border-[var(--color-surface-border)] rounded-xl my-2 overflow-x-auto">
                      {children}
                    </pre>
                  ),
                  h1: ({ children }) => (
                    <h1 className="text-[15px] font-semibold mt-3 mb-1">
                      {children}
                    </h1>
                  ),
                  h2: ({ children }) => (
                    <h2 className="text-[14px] font-semibold mt-3 mb-1">
                      {children}
                    </h2>
                  ),
                  h3: ({ children }) => (
                    <h3 className="text-[13px] font-semibold mt-2 mb-1">
                      {children}
                    </h3>
                  ),
                  ul: ({ children }) => (
                    <ul className="list-disc list-outside ml-4 my-1.5 space-y-1">
                      {children}
                    </ul>
                  ),
                  ol: ({ children }) => (
                    <ol className="list-decimal list-outside ml-4 my-1.5 space-y-1">
                      {children}
                    </ol>
                  ),
                  li: ({ children }) => <li className="pl-0.5">{children}</li>,
                  hr: () => (
                    <hr className="my-3 border-[var(--color-surface-border)]" />
                  ),
                  blockquote: ({ children }) => (
                    <blockquote className="border-l-2 border-[var(--color-surface-400)] pl-3 my-2 text-[var(--color-text-secondary)] italic">
                      {children}
                    </blockquote>
                  ),
                  a: ({ href, children }) => (
                    <a
                      href={href}
                      target="_blank"
                      rel="noopener noreferrer"
                      className="text-[var(--color-brand-400)] underline underline-offset-2 hover:opacity-80"
                    >
                      {children}
                    </a>
                  ),
                }}
              >
                {message.content}
              </ReactMarkdown>
            </div>
          ) : null
        ) : isAssistant && isStreaming ? (
          <StreamingDots />
        ) : null}
      </div>
    </div>
  );
}

function StreamingDots() {
  return (
    <span className="flex items-center gap-1 h-5 mt-0.5">
      {[0, 1, 2].map((i) => (
        <span
          key={i}
          className="w-1.5 h-1.5 rounded-full bg-[var(--color-surface-500)]"
          style={{ animation: `blink 1.2s ${i * 0.2}s ease-in-out infinite` }}
        />
      ))}
    </span>
  );
}
