import { useEffect, useRef, useState, useCallback } from "react";
import { FileText, Download as DownloadIcon } from "lucide-react";
import { useChat } from "@/hooks/useChat";
import { useChatStore, useActiveSessionId } from "@/stores/chatStore";
import { ragApi } from "@/api/ragApi";
import { multimodalApi } from "@/api/multimodalApi";
import { filesApi, exportsApi } from "@/api/conversationsApi";
import { apiClient } from "@/api/axiosInstance";
import { describeError } from "@/api/errorMessage";
import MessageBubble from "./MessageBubble";
import ChatInput, { type ChatMode } from "./ChatInput";
import ModelSelector from "./ModelSelector";
import type { MessageAttachment, ChatMessage } from "@/types/Api";

const RAG_INDEXABLE = [
  "application/pdf",
  "text/plain",
  "text/markdown",
  "text/html",
];
const isRagIndexable = (file: File) =>
  RAG_INDEXABLE.includes(file.type) ||
  /\.(pdf|txt|md|html|csv)$/i.test(file.name);

export default function ChatWindow() {
  const [input, setInput] = useState("");
  const [provider] = useState("openai");
  const [chatModel, setChatModel] = useState("gpt-4.1");
  const [imageModel, setImageModel] = useState("gpt-image-1");
  const [mode, setMode] = useState<ChatMode>("chat");
  const [showSyncErrorPanel, setShowSyncErrorPanel] = useState(false);
  const [activeDocumentId, setActiveDocumentId] = useState<
    string | undefined
  >();

  const scrollRef = useRef<HTMLDivElement>(null);

  // ragQueue: ragMessageId → File (kept in memory, not persisted)
  const ragQueueRef = useRef<Map<string, File>>(new Map());
  // userMsgByRag: ragMessageId → user message id that holds the attachment.
  // When the doc is indexed we patch that user message so the preview switches
  // from the ephemeral blob URL to the persistent MinIO-backed reference.
  const userMsgByRagRef = useRef<Map<string, string>>(new Map());
  // deferredQuestionByRag: ragMessageId → user-typed text submitted alongside
  // the PDF. Held back until indexing finishes so the auto-send can use the
  // freshly indexed document as the RAG context — instead of being silently
  // dropped (which is what happened before, leaving the user to retype).
  const deferredQuestionByRagRef = useRef<Map<string, string>>(new Map());

  const sessionId = useActiveSessionId();
  const { updateMessage, addMessage, setStreaming } = useChatStore();
  const updateLastAssistantMessage = useChatStore(
    (s) => s.updateLastAssistantMessage,
  );
  const language = useChatStore((s) => s.language);
  const syncError = useChatStore((s) => s.syncError);

  const langSuffix =
    language === "es"
      ? "\n\n[Responde exclusivamente en espanol]"
      : language === "en"
        ? "\n\n[Respond exclusively in English]"
        : "";

  const { messages, sendMessage, isStreaming, stopStreaming } = useChat(
    sessionId ?? undefined,
    activeDocumentId,
  );

  // Auto-scroll
  useEffect(() => {
    const el = scrollRef.current;
    if (!el) return;
    el.scrollTop = el.scrollHeight;
  }, [messages]);

  // ── RAG actions ────────────────────────────────────────────────────────────

  const pollUntilIndexed = useCallback(
    async (docId: string, messageId: string, filename: string) => {
      const deadline = Date.now() + 90_000; // 90s max
      while (Date.now() < deadline) {
        await new Promise((r) => setTimeout(r, 1500));
        try {
          const status = await ragApi.status(docId);
          if (status.status === "INDEXED") {
            updateMessage(messageId, {
              ragAction: { state: "indexed", filename, docId },
            });
            return true;
          }
          if (status.status === "FAILED") {
            updateMessage(messageId, {
              ragAction: { state: "pending", filename },
              content: `Fallo la indexacion: ${status.errorMsg ?? "error desconocido"}`,
            });
            return false;
          }
        } catch {
          /* keep polling on transient errors */
        }
      }
      updateMessage(messageId, {
        ragAction: { state: "pending", filename },
        content:
          "Tiempo de espera agotado. El documento sigue procesandose en segundo plano.",
      });
      return false;
    },
    [updateMessage],
  );

  const patchUserAttachmentWithDocId = useCallback(
    (userMsgId: string, docId: string) => {
      const messages =
        useChatStore
          .getState()
          .conversations.find(
            (c) => c.id === useChatStore.getState().activeConversationId,
          )?.messages ?? [];
      const msg = messages.find((m) => m.id === userMsgId);
      if (!msg || !msg.attachments?.length) return;
      const next = msg.attachments.map((a) => ({
        ...a,
        // drop the ephemeral blob/object URL — once we have a refId the
        // AuthenticatedAsset will fetch the persistent file from MinIO
        url: undefined,
        refId: docId,
        refKind: "rag-doc" as const,
      }));
      updateMessage(userMsgId, { attachments: next });
    },
    [updateMessage],
  );

  const handleRagIndex = useCallback(
    async (messageId: string) => {
      const file = ragQueueRef.current.get(messageId);
      if (!file) return;
      updateMessage(messageId, {
        ragAction: { state: "indexing", filename: file.name },
      });
      try {
        const doc = await ragApi.upload(file);
        // Mark as indexing while we wait for the async pipeline to finish
        updateMessage(messageId, {
          ragAction: { state: "indexing", filename: file.name, docId: doc.id },
        });
        const ok = await pollUntilIndexed(doc.id, messageId, file.name);
        if (ok) {
          setActiveDocumentId(doc.id);
          const userMsgId = userMsgByRagRef.current.get(messageId);
          if (userMsgId) patchUserAttachmentWithDocId(userMsgId, doc.id);
          // Auto-send the deferred question (the text the user typed when
          // attaching the PDF) using the freshly indexed doc as RAG context.
          // Pass doc.id explicitly because the activeDocumentId state update
          // hasn't propagated through useChat yet within this callback tick.
          const deferred = deferredQuestionByRagRef.current.get(messageId);
          if (deferred && deferred.trim()) {
            void sendMessage(deferred, provider, chatModel, doc.id);
          }
        }
        ragQueueRef.current.delete(messageId);
        userMsgByRagRef.current.delete(messageId);
        deferredQuestionByRagRef.current.delete(messageId);
      } catch (err: unknown) {
        updateMessage(messageId, {
          ragAction: { state: "pending", filename: file.name },
          content: `Error al subir: ${describeError(err)}`,
        });
      }
    },
    [updateMessage, pollUntilIndexed, patchUserAttachmentWithDocId, sendMessage, provider, chatModel],
  );

  const handleRagSkip = useCallback(
    (messageId: string) => {
      const file = ragQueueRef.current.get(messageId);
      updateMessage(messageId, {
        ragAction: { state: "skipped", filename: file?.name ?? "" },
      });
      // If the user typed a question alongside the PDF, send it now as a
      // plain (non-RAG) message — they explicitly chose to skip indexing,
      // but the question itself should still go through.
      const deferred = deferredQuestionByRagRef.current.get(messageId);
      if (deferred && deferred.trim()) {
        void sendMessage(deferred, provider, chatModel, undefined);
      }
      ragQueueRef.current.delete(messageId);
      userMsgByRagRef.current.delete(messageId);
      deferredQuestionByRagRef.current.delete(messageId);
    },
    [updateMessage, sendMessage, provider, chatModel],
  );

  // ── Image generation (DALL-E 3) ────────────────────────────────────────────

  const handleImageGeneration = useCallback(
    async (prompt: string) => {
      addMessage({
        id: crypto.randomUUID(),
        role: "user",
        content: prompt,
        createdAt: new Date().toISOString(),
      });
      const assistantId = crypto.randomUUID();
      addMessage({
        id: assistantId,
        role: "assistant",
        content: "Generando imagen…",
        createdAt: new Date().toISOString(),
        model: "dall-e-3",
      });
      setStreaming(true);
      try {
        const result = await multimodalApi.generateImage(
          prompt + langSuffix,
          "1024x1024",
          "standard",
          imageModel,
          sessionId ?? undefined,
        );
        // Backend persists the bytes to MinIO and returns refId. Prefer
        // refId/refKind so the image reloads on another browser / after the
        // hosted URL TTL expires. Fall back to plain URL only if the backend
        // could not store it (defensive — shouldn't happen in normal flow).
        const att: MessageAttachment = result.refId
          ? {
              type: "image",
              name: result.filename ?? "generated.png",
              size: result.size ?? 0,
              mimeType: result.contentType ?? "image/png",
              refId: result.refId,
              refKind: "attachment",
            }
          : {
              type: "image",
              name: "generated.png",
              url: result.url,
              size: 0,
              mimeType: "image/png",
            };
        updateMessage(assistantId, {
          content: "",
          attachments: [att],
        });
      } catch (err: unknown) {
        updateMessage(assistantId, {
          content: `Error al generar imagen: ${describeError(err)}`,
        });
      } finally {
        setStreaming(false);
      }
    },
    [addMessage, updateMessage, setStreaming, imageModel, sessionId, langSuffix],
  );

  // ── File handling ──────────────────────────────────────────────────────────

  const handleFileSubmit = useCallback(
    async (text: string, file: File) => {
      const isImage = file.type.startsWith("image/");
      const isPdf = file.type === "application/pdf";

      // Optimistic local preview while the upload is in flight.
      const objectUrl =
        isImage || isPdf ? URL.createObjectURL(file) : undefined;

      const baseAttachment: MessageAttachment = {
        type: isImage ? "image" : isPdf ? "pdf" : "file",
        name: file.name,
        url: objectUrl,
        size: file.size,
        mimeType: file.type,
      };

      const userMsgId = crypto.randomUUID();
      addMessage({
        id: userMsgId,
        role: "user",
        content: text || "",
        createdAt: new Date().toISOString(),
        attachments: [baseAttachment],
      });

      // Persist images (and other files we don't RAG-index) to MinIO via
      // /api/files so they show up in the message after a reload / on
      // another browser. PDFs that the user wants in RAG go through the
      // separate index flow below — those get a refKind: 'rag-doc' instead.
      if (isImage) {
        try {
          const uploaded = await filesApi.upload(file);
          updateMessage(userMsgId, {
            attachments: [
              {
                ...baseAttachment,
                url: undefined,
                refId: uploaded.id,
                refKind: "attachment",
              },
            ],
          });
        } catch (err) {
          console.warn(
            "[ChatWindow] image upload failed; preview stays ephemeral",
            err,
          );
        }
      }

      if (isImage) {
        // Vision describe
        const assistantId = crypto.randomUUID();
        addMessage({
          id: assistantId,
          role: "assistant",
          content: "",
          createdAt: new Date().toISOString(),
          model: provider,
        });
        setStreaming(true);
        try {
          const form = new FormData();
          form.append("file", file);
          const visionInstruction =
            (text || "Describe this image in detail.") + langSuffix;
          form.append("instruction", visionInstruction);
          const resp = await apiClient.post<{ description: string }>(
            "/api/multimodal/describe",
            form,
            {
              headers: {
                "Content-Type": "multipart/form-data",
                "X-Content-Type": file.type,
              },
              timeout: 120_000,
            },
          );
          updateLastAssistantMessage(
            resp.data.description ?? "No se pudo generar descripcion.",
          );
        } catch (err: unknown) {
          updateLastAssistantMessage(
            `Error al procesar la imagen: ${describeError(err)}`,
          );
        } finally {
          setStreaming(false);
        }
        return;
      }

      if (isRagIndexable(file)) {
        const ragMsgId = crypto.randomUUID();
        ragQueueRef.current.set(ragMsgId, file);
        userMsgByRagRef.current.set(ragMsgId, userMsgId);
        if (text) deferredQuestionByRagRef.current.set(ragMsgId, text);
        addMessage({
          id: ragMsgId,
          role: "system",
          content: "",
          createdAt: new Date().toISOString(),
          ragAction: { state: "pending", filename: file.name },
        });
        if (text) {
          // Show a hint so the user knows their question is queued and will
          // be answered automatically after indexing — no need to retype.
          addMessage({
            id: crypto.randomUUID(),
            role: "system",
            content:
              "Tu pregunta se enviara automaticamente cuando termine la indexacion.",
            createdAt: new Date().toISOString(),
          });
        }
        return;
      }

      // Unknown / non-indexable type: just acknowledge
      addMessage({
        id: crypto.randomUUID(),
        role: "assistant",
        content:
          "Adjunto recibido. Este tipo de archivo no se puede indexar en RAG todavia.",
        createdAt: new Date().toISOString(),
      });
    },
    [provider, addMessage, updateLastAssistantMessage, setStreaming],
  );

  // ── Export conversation (RabbitMQ -> ZIP -> MinIO) ─────────────────────────

  const handleExport = useCallback(async () => {
    const targetConvId = sessionId ?? undefined;
    // Add a system message acting as a progress card.
    const exportMsgId = crypto.randomUUID();
    addMessage({
      id: exportMsgId,
      role: "system",
      content: "Preparando exportacion del chat…",
      createdAt: new Date().toISOString(),
    });
    try {
      const job = await exportsApi.create(targetConvId);
      // Poll status every 1.5s up to ~2 min.
      const deadline = Date.now() + 120_000;
      while (Date.now() < deadline) {
        await new Promise((r) => setTimeout(r, 1500));
        const s = await exportsApi.status(job.id);
        if (s.status === "READY" && s.attachmentId) {
          updateMessage(exportMsgId, {
            content: "",
            attachments: [
              {
                type: "file",
                name: "chat-export.zip",
                size: 0,
                mimeType: "application/zip",
                refId: s.attachmentId,
                refKind: "attachment",
              },
            ],
          });
          return;
        }
        if (s.status === "FAILED") {
          updateMessage(exportMsgId, {
            content: `Export fallo: ${s.errorMsg ?? "error desconocido"}`,
          });
          return;
        }
      }
      updateMessage(exportMsgId, {
        content:
          "La exportacion esta tardando mas de lo esperado. Sigue en cola.",
      });
    } catch (err) {
      updateMessage(exportMsgId, {
        content: `No se pudo exportar: ${describeError(err)}`,
      });
    }
  }, [sessionId, addMessage, updateMessage]);

  const handleSubmit = (text: string, file?: File) => {
    if (file) {
      void handleFileSubmit(text, file);
      return;
    }
    if (mode === "image") {
      void handleImageGeneration(text);
      return;
    }
    void sendMessage(text, provider, chatModel, activeDocumentId);
  };

  return (
    <div className="flex flex-col h-full">
      {/* Thin toolbar — wraps on narrow viewports so it doesn't overflow */}
      <div className="glass flex flex-wrap items-center justify-between gap-y-1 px-3 sm:px-4 py-1 sm:py-0 sm:h-[46px] shrink-0 border-b border-[var(--color-surface-border)]">
        <div className="flex items-center gap-2 sm:gap-2.5 min-w-0">
          <ModelSelector
            chatModel={chatModel}
            imageModel={imageModel}
            onChatModelChange={setChatModel}
            onImageModelChange={setImageModel}
            isImageMode={mode === "image"}
            disabled={isStreaming}
          />
          {isStreaming && (
            <span className="text-[11px] text-[var(--color-brand-500)] animate-pulse font-medium">
              Generando
            </span>
          )}
          {syncError && (
            <button
              type="button"
              onClick={() => setShowSyncErrorPanel((v) => !v)}
              title="Ver detalle del error (click)"
              className="flex items-center gap-1 px-2 py-0.5 rounded-md text-[10px] font-medium bg-[var(--color-error)]/15 text-[var(--color-error)] border border-[var(--color-error)]/30 cursor-pointer hover:bg-[var(--color-error)]/25"
            >
              ⚠ No sincronizado
            </button>
          )}
        </div>
        <div className="flex items-center gap-2">
          {activeDocumentId && (
            <div className="flex items-center gap-1.5 px-2 py-1 rounded-lg bg-[var(--color-brand-600)]/8 border border-[var(--color-brand-600)]/20">
              <FileText size={10} className="text-[var(--color-brand-500)]" />
              <span className="text-[10px] text-[var(--color-brand-400)] font-mono truncate max-w-[120px]">
                RAG: {activeDocumentId.slice(0, 8)}…
              </span>
              <button
                onClick={() => setActiveDocumentId(undefined)}
                className="text-[var(--color-text-muted)] hover:text-[var(--color-error)] cursor-pointer text-[10px] leading-none"
              >
                x
              </button>
            </div>
          )}
        </div>
      </div>

      {/* Sync error panel — only when the user clicks the toolbar badge */}
      {syncError && showSyncErrorPanel && (
        <div className="px-4 py-2 border-b border-[var(--color-error)]/30 bg-[var(--color-error)]/10 text-[12px] text-[var(--color-error)]">
          <div className="flex items-start gap-2">
            <span className="font-semibold shrink-0">⚠ Sync error:</span>
            <code className="font-mono text-[11px] flex-1 break-all">{syncError}</code>
            <button
              type="button"
              onClick={() => setShowSyncErrorPanel(false)}
              className="shrink-0 text-[10px] underline hover:opacity-80"
            >
              cerrar
            </button>
          </div>
        </div>
      )}

      {/* Messages */}
      <div ref={scrollRef} className="flex-1 overflow-y-auto">
        {messages.length === 0 ? (
          <EmptyState />
        ) : (
          <div className="max-w-2xl mx-auto px-3 sm:px-4 py-4 sm:py-6 space-y-1">
            {messages.map((msg: ChatMessage) => (
              <MessageBubble
                key={msg.id}
                message={msg}
                isStreaming={
                  isStreaming && msg === messages[messages.length - 1]
                }
                onRagIndex={handleRagIndex}
                onRagSkip={handleRagSkip}
              />
            ))}
          </div>
        )}
      </div>

      {/* Input */}
      <div className="shrink-0 max-w-2xl mx-auto w-full px-3 sm:px-4 pb-3 sm:pb-5 pt-2">
        <ChatInput
          value={input}
          onChange={setInput}
          onSubmit={handleSubmit}
          onStop={stopStreaming}
          isStreaming={isStreaming}
          disabled={false}
          mode={mode}
          onModeChange={setMode}
          slashCommands={[
            {
              id: "export",
              label: "Exportar",
              description: "Exportar este chat como ZIP",
              icon: <DownloadIcon size={12} />,
              action: handleExport,
            },
          ]}
        />
        <p className="text-center text-[10px] text-[var(--color-text-muted)] mt-2 leading-none">
          <kbd className="px-1 py-0.5 rounded bg-[var(--color-surface-300)] text-[9px]">
            Enter
          </kbd>{" "}
          enviar{"   "}
          <kbd className="px-1 py-0.5 rounded bg-[var(--color-surface-300)] text-[9px]">
            Shift+Enter
          </kbd>{" "}
          nueva linea{"   "}
          <span className="opacity-60">
            📎 adjuntar · 🪄 generar imagen · / comandos
          </span>
        </p>
      </div>
    </div>
  );
}

function EmptyState() {
  return (
    <div className="flex flex-col items-center justify-center h-full gap-4 text-center px-8 pt-20">
      <div className="w-10 h-10 rounded-2xl flex items-center justify-center bg-[var(--color-surface-300)]">
        <span className="text-lg">✦</span>
      </div>
      <div className="space-y-1 max-w-[360px]">
        <p className="text-[var(--color-text-primary)] font-medium text-[15px]">
          Como puedo ayudarte?
        </p>
        <p className="text-[var(--color-text-muted)] text-[13px] leading-relaxed">
          Chat, vision sobre imagenes, RAG sobre documentos y generacion de
          imagenes / diagramas — todo en una sola conversacion. Adjunta con 📎 o
          activa el modo imagen con 🪄.
        </p>
      </div>
    </div>
  );
}
