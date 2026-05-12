import {
  useRef,
  useState,
  useCallback,
  useEffect,
  type KeyboardEvent,
  type DragEvent,
} from "react";
import {
  ArrowUp,
  Square,
  Paperclip,
  X,
  FileText,
  ImageIcon,
  Wand2,
} from "lucide-react";
import clsx from "clsx";

export type ChatMode = "chat" | "image";

export interface SlashCommand {
  id: string;
  label: string;
  description: string;
  icon?: React.ReactNode;
  action: () => void;
}

interface ChatInputProps {
  value: string;
  onChange: (v: string) => void;
  onSubmit: (v: string, file?: File) => void;
  onStop: () => void;
  isStreaming: boolean;
  disabled?: boolean;
  placeholder?: string;
  mode: ChatMode;
  onModeChange: (m: ChatMode) => void;
  slashCommands?: SlashCommand[];
  /**
   * Hard cap on characters that can be sent in a single message. The textarea
   * uses {@code maxLength} so the browser blocks extra typing/paste; the
   * counter + send button still re-validate so the limit can't be bypassed.
   * Defaults to 8000 — generous enough for code snippets and long prompts
   * but well under the OpenAI per-message budget.
   */
  maxLength?: number;
}

/** Default cap. Picked to match a typical paid-tier ChatGPT message ceiling. */
const DEFAULT_MAX_CHARS = 8000;

export default function ChatInput({
  value,
  onChange,
  onSubmit,
  onStop,
  isStreaming,
  disabled,
  placeholder,
  mode,
  onModeChange,
  slashCommands = [],
  maxLength = DEFAULT_MAX_CHARS,
}: ChatInputProps) {
  const textareaRef = useRef<HTMLTextAreaElement>(null);
  const fileInputRef = useRef<HTMLInputElement>(null);
  const [attachedFile, setAttachedFile] = useState<File | null>(null);
  const [previewUrl, setPreviewUrl] = useState<string | null>(null);
  const [isDragOver, setIsDragOver] = useState(false);
  const [cmdIdx, setCmdIdx] = useState(0);

  const attachFile = useCallback(
    (file: File | null) => {
      if (previewUrl) URL.revokeObjectURL(previewUrl);
      if (!file) {
        setAttachedFile(null);
        setPreviewUrl(null);
        return;
      }
      setAttachedFile(file);
      const isImage = file.type.startsWith("image/");
      const isPdf = file.type === "application/pdf";
      setPreviewUrl(isImage || isPdf ? URL.createObjectURL(file) : null);
    },
    [previewUrl],
  );

  // ── Slash command menu ────────────────────────────────────────────────────
  const isSlashQuery = value.startsWith("/") && !value.includes(" ");
  const slashFilter = value.slice(1).toLowerCase();
  const filteredCmds = slashCommands.filter(
    (c) =>
      c.id.toLowerCase().startsWith(slashFilter) ||
      c.label.toLowerCase().startsWith(slashFilter),
  );
  const showCmdMenu = isSlashQuery && filteredCmds.length > 0;

  // Reset index when filter changes
  useEffect(() => {
    setCmdIdx(0);
  }, [slashFilter]);

  const execCommand = (cmd: SlashCommand) => {
    cmd.action();
    onChange("");
    setCmdIdx(0);
    textareaRef.current?.focus();
  };

  const handleKeyDown = (e: KeyboardEvent<HTMLTextAreaElement>) => {
    if (showCmdMenu) {
      if (e.key === "ArrowDown") {
        e.preventDefault();
        setCmdIdx((i) => (i + 1) % filteredCmds.length);
        return;
      }
      if (e.key === "ArrowUp") {
        e.preventDefault();
        setCmdIdx((i) => (i - 1 + filteredCmds.length) % filteredCmds.length);
        return;
      }
      if (e.key === "Enter") {
        e.preventDefault();
        execCommand(filteredCmds[cmdIdx]);
        return;
      }
      if (e.key === "Escape") {
        e.preventDefault();
        onChange("");
        return;
      }
      if (e.key === "Tab") {
        e.preventDefault();
        onChange("/" + filteredCmds[cmdIdx].id + " ");
        return;
      }
    }
    if (e.key === "Enter" && !e.shiftKey) {
      e.preventDefault();
      handleSubmit();
    }
  };

  const handleSubmit = () => {
    if (isStreaming) {
      onStop();
      return;
    }
    const trimmed = value.trim();
    if (!trimmed && !attachedFile) return;
    if (disabled) return;
    // Re-validate the cap on submit. The textarea's `maxLength` already
    // blocks typing past the limit, but a buggy parent that bypasses
    // onChange (or a future controlled-input path) shouldn't be able to
    // sneak an oversized message through.
    if (trimmed.length > maxLength) return;
    onSubmit(trimmed, attachedFile ?? undefined);
    onChange("");
    attachFile(null);
    if (textareaRef.current) textareaRef.current.style.height = "auto";
  };

  const handleInput = (e: React.ChangeEvent<HTMLTextAreaElement>) => {
    // Defensive trim — `maxLength` on <textarea> covers typing, but pastes
    // from some sources (Safari, certain PWAs) have historically slipped
    // through the attribute on edge cases. Slice as a belt-and-suspenders.
    const next = e.target.value.length > maxLength
      ? e.target.value.slice(0, maxLength)
      : e.target.value;
    onChange(next);
    const el = e.target;
    el.style.height = "auto";
    el.style.height = `${Math.min(el.scrollHeight, 200)}px`;
  };

  const handleFileChange = (e: React.ChangeEvent<HTMLInputElement>) => {
    attachFile(e.target.files?.[0] ?? null);
    e.target.value = "";
  };

  const handleDragOver = (e: DragEvent) => {
    e.preventDefault();
    setIsDragOver(true);
  };
  const handleDragLeave = () => setIsDragOver(false);
  const handleDrop = (e: DragEvent) => {
    e.preventDefault();
    setIsDragOver(false);
    const file = e.dataTransfer.files?.[0];
    if (file) attachFile(file);
  };

  const isImage = attachedFile?.type.startsWith("image/");
  const isPdf = attachedFile?.type === "application/pdf";
  const charCount = value.length;
  const overLimit = charCount > maxLength;
  const canSend =
    !disabled && (!!value.trim() || !!attachedFile) && !isStreaming && !overLimit;
  const isImageMode = mode === "image";

  // Counter visibility: only show once the user is past 75% of the cap.
  // Quiet by default, yellow at 90%, red when at/over the limit.
  const showCounter = charCount >= Math.floor(maxLength * 0.75);
  const counterColor = overLimit
    ? 'text-[var(--color-error)]'
    : charCount >= Math.floor(maxLength * 0.9)
      ? 'text-[var(--color-warning)]'
      : 'text-[var(--color-text-muted)]';

  const computedPlaceholder =
    placeholder ??
    (isImageMode
      ? "Describe el diagrama o imagen a generar…"
      : attachedFile
        ? "Anade un mensaje…"
        : "Escribe un mensaje…");

  return (
    <div
      className="flex flex-col gap-2 relative"
      onDragOver={handleDragOver}
      onDragLeave={handleDragLeave}
      onDrop={handleDrop}
    >
      {/* ── Slash command menu ─────────────────────────────────────────────── */}
      {showCmdMenu && (
        <div
          className={clsx(
            "absolute bottom-[calc(100%+8px)] left-0 right-0 z-50",
            "rounded-xl border border-[var(--color-surface-border)]",
            "glass-strong shadow-2xl shadow-black/50 overflow-hidden",
          )}
        >
          <div className="px-3 py-2 border-b border-[var(--color-surface-border)]">
            <p className="text-[10px] font-semibold text-[var(--color-text-muted)] uppercase tracking-wider">
              Comandos
            </p>
          </div>
          {filteredCmds.map((cmd, i) => (
            <button
              key={cmd.id}
              type="button"
              onMouseDown={(e) => {
                e.preventDefault();
                execCommand(cmd);
              }}
              onMouseEnter={() => setCmdIdx(i)}
              className={clsx(
                "w-full flex items-center gap-3 px-3 py-2.5 text-left cursor-pointer transition-colors",
                i === cmdIdx
                  ? "bg-[var(--color-surface-300)]"
                  : "hover:bg-[var(--color-surface-200)]",
              )}
            >
              {cmd.icon && (
                <span className="shrink-0 text-[var(--color-brand-400)]">
                  {cmd.icon}
                </span>
              )}
              <div>
                <span className="text-[12px] font-medium text-[var(--color-text-primary)]">
                  /{cmd.id}
                </span>
                <span className="ml-2 text-[11px] text-[var(--color-text-muted)]">
                  {cmd.description}
                </span>
              </div>
            </button>
          ))}
        </div>
      )}

      {/* Attachment preview */}
      {attachedFile && (
        <div className="glass-strong relative rounded-2xl border border-[var(--color-surface-border)] overflow-hidden shadow-lg shadow-black/30">
          {isImage && previewUrl ? (
            <div className="relative">
              <img
                src={previewUrl}
                alt={attachedFile.name}
                className="w-full max-h-[180px] object-contain"
              />
              <button
                type="button"
                onClick={() => attachFile(null)}
                className="absolute top-2 right-2 flex items-center justify-center w-6 h-6 rounded-full bg-black/60 text-white hover:bg-black/80 transition-colors cursor-pointer"
              >
                <X size={12} />
              </button>
            </div>
          ) : isPdf && previewUrl ? (
            <div>
              <div className="flex items-center gap-2 px-3 py-2 border-b border-[var(--color-surface-border)]">
                <FileText
                  size={12}
                  className="text-[var(--color-brand-500)] shrink-0"
                />
                <span className="text-[11px] text-[var(--color-text-primary)] truncate flex-1">
                  {attachedFile.name}
                </span>
                <span className="text-[10px] text-[var(--color-text-muted)]">
                  {(attachedFile.size / 1024).toFixed(0)} KB
                </span>
                <button
                  type="button"
                  onClick={() => attachFile(null)}
                  className="text-[var(--color-text-muted)] hover:text-[var(--color-error)] cursor-pointer"
                >
                  <X size={12} />
                </button>
              </div>
              <iframe
                src={previewUrl}
                title={attachedFile.name}
                className="w-full"
                style={{ height: 160, border: "none", background: "#1a1a1a" }}
              />
            </div>
          ) : (
            <div className="flex items-center gap-2 px-3 py-2">
              <FileText
                size={12}
                className="text-[var(--color-brand-400)] shrink-0"
              />
              <span className="text-[12px] text-[var(--color-text-primary)] truncate flex-1">
                {attachedFile.name}
              </span>
              <span className="text-[11px] text-[var(--color-text-muted)]">
                {(attachedFile.size / 1024).toFixed(0)} KB
              </span>
              <button
                type="button"
                onClick={() => attachFile(null)}
                className="text-[var(--color-text-muted)] hover:text-[var(--color-error)] cursor-pointer"
              >
                <X size={12} />
              </button>
            </div>
          )}
        </div>
      )}

      {/* Input area */}
      <div
        className={clsx(
          "glass-strong flex items-end gap-2 px-3 py-2.5 rounded-2xl border transition-all shadow-lg shadow-black/30",
          isImageMode && "ring-1 ring-[var(--color-brand-400)]/40",
          isDragOver
            ? "border-[var(--color-brand-400)] ring-2 ring-[var(--color-brand-400)]/25"
            : "border-[var(--color-surface-border)] focus-within:border-[var(--color-brand-400)]/60 focus-within:ring-2 focus-within:ring-[var(--color-brand-400)]/15",
        )}
      >
        <input
          ref={fileInputRef}
          type="file"
          accept="image/*,.pdf,.txt,.md,.docx,.csv"
          className="hidden"
          onChange={handleFileChange}
        />

        <button
          type="button"
          onClick={() => fileInputRef.current?.click()}
          disabled={disabled || isStreaming || isImageMode}
          title={
            isImageMode
              ? "No disponible en modo imagen"
              : "Adjuntar archivo (o arrastra y suelta)"
          }
          className="flex-shrink-0 flex items-center justify-center w-7 h-7 rounded-lg
                     text-[var(--color-text-muted)] hover:text-[var(--color-text-secondary)]
                     hover:bg-[var(--color-surface-300)] transition-all cursor-pointer
                     disabled:opacity-30 disabled:cursor-not-allowed"
        >
          {attachedFile ? (
            isImage ? (
              <ImageIcon size={14} className="text-[var(--color-brand-400)]" />
            ) : (
              <FileText size={14} className="text-[var(--color-brand-400)]" />
            )
          ) : (
            <Paperclip size={14} />
          )}
        </button>

        <button
          type="button"
          onClick={() => onModeChange(isImageMode ? "chat" : "image")}
          disabled={disabled || isStreaming}
          title={
            isImageMode ? "Volver a modo chat" : "Generar imagen / diagrama"
          }
          className={clsx(
            "flex-shrink-0 flex items-center justify-center w-7 h-7 rounded-lg transition-all cursor-pointer disabled:opacity-30 disabled:cursor-not-allowed",
            isImageMode
              ? "bg-[var(--color-brand-400)] text-black hover:bg-[var(--color-brand-500)]"
              : "text-[var(--color-text-muted)] hover:text-[var(--color-text-secondary)] hover:bg-[var(--color-surface-300)]",
          )}
        >
          <Wand2 size={14} />
        </button>

        <textarea
          ref={textareaRef}
          rows={1}
          value={value}
          onChange={handleInput}
          onKeyDown={handleKeyDown}
          disabled={disabled}
          // Browser-level cap — blocks typing AND most paste paths.
          // The handleInput slice() above is the safety net.
          maxLength={maxLength}
          aria-invalid={overLimit}
          placeholder={
            isDragOver ? "Suelta el archivo aqui…" : computedPlaceholder
          }
          className="flex-1 resize-none bg-transparent outline-none text-[13px]
                     text-[var(--color-text-primary)]
                     placeholder:text-[var(--color-text-muted)]
                     leading-relaxed min-h-[22px] max-h-[200px]
                     disabled:opacity-50"
        />

        <button
          type="button"
          onClick={handleSubmit}
          disabled={!canSend && !isStreaming}
          className={clsx(
            "flex-shrink-0 flex items-center justify-center w-7 h-7 rounded-lg transition-all cursor-pointer",
            "disabled:opacity-30 disabled:cursor-not-allowed",
            isStreaming
              ? "bg-[var(--color-error)]/80 hover:bg-[var(--color-error)] text-white"
              : "bg-[var(--color-brand-400)] hover:bg-[var(--color-brand-500)] text-black",
          )}
          title={
            isStreaming ? "Detener" : isImageMode ? "Generar imagen" : "Enviar"
          }
        >
          {isStreaming ? (
            <Square size={11} fill="currentColor" />
          ) : (
            <ArrowUp size={13} strokeWidth={2.5} />
          )}
        </button>
      </div>

      {/* Footer row: image-mode hint on the left, char counter on the right.
          Both are optional — the row stays out of the way until needed. */}
      {(isImageMode || showCounter) && (
        <div className="flex items-center gap-2 px-1">
          {isImageMode && (
            <p className="flex-1 text-[11px] text-[var(--color-brand-400)] leading-relaxed">
              Modo imagen activo — el siguiente mensaje se envia a DALL-E 3 para
              generar un diagrama o ilustracion.
            </p>
          )}
          {showCounter && (
            <span
              className={clsx('ml-auto text-[10px] tabular-nums', counterColor)}
              title={overLimit ? `Maximo ${maxLength} caracteres` : undefined}
              aria-live="polite"
            >
              {charCount.toLocaleString()} / {maxLength.toLocaleString()}
              {overLimit && ' · limite superado'}
            </span>
          )}
        </div>
      )}
    </div>
  );
}
