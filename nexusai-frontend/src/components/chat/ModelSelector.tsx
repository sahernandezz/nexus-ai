import { useState, useRef, useEffect } from "react";
import { Sparkles, Check, ChevronDown, ImageIcon } from "lucide-react";
import clsx from "clsx";

interface ModelOption {
  id: string;
  label: string;
  description: string;
}

export const CHAT_MODELS: ModelOption[] = [
  { id: "gpt-4.1", label: "GPT-4.1", description: "Most capable" },
  {
    id: "gpt-4.1-mini",
    label: "GPT-4.1 Mini",
    description: "Faster & cheaper",
  },
  { id: "gpt-4o", label: "GPT-4o", description: "Balanced" },
  { id: "gpt-4o-mini", label: "GPT-4o Mini", description: "Lightweight" },
];

export const IMAGE_MODELS: ModelOption[] = [
  { id: "gpt-image-1", label: "GPT Image 1", description: "Sharp diagrams" },
  { id: "dall-e-3", label: "DALL-E 3", description: "Creative images" },
];

interface ModelSelectorProps {
  chatModel: string;
  imageModel: string;
  onChatModelChange: (model: string) => void;
  onImageModelChange: (model: string) => void;
  isImageMode?: boolean;
  disabled?: boolean;
}

export default function ModelSelector({
  chatModel,
  imageModel,
  onChatModelChange,
  onImageModelChange,
  isImageMode = false,
  disabled = false,
}: ModelSelectorProps) {
  const [open, setOpen] = useState(false);
  const ref = useRef<HTMLDivElement>(null);

  const models = isImageMode ? IMAGE_MODELS : CHAT_MODELS;
  const current = models.find(
    (m) => m.id === (isImageMode ? imageModel : chatModel),
  );
  const onChange = isImageMode ? onImageModelChange : onChatModelChange;

  useEffect(() => {
    if (!open) return;
    const handler = (e: MouseEvent) => {
      if (ref.current && !ref.current.contains(e.target as Node))
        setOpen(false);
    };
    document.addEventListener("mousedown", handler);
    return () => document.removeEventListener("mousedown", handler);
  }, [open]);

  return (
    <div ref={ref} className="relative">
      <button
        type="button"
        onClick={() => !disabled && setOpen((o) => !o)}
        disabled={disabled}
        className={clsx(
          "flex items-center gap-1.5 px-2.5 py-1 rounded-md text-[12px] font-medium",
          "border border-[var(--color-surface-border)] transition-all cursor-pointer",
          "disabled:opacity-50 disabled:cursor-not-allowed",
          open
            ? "bg-[var(--color-surface-400)] text-[var(--color-text-primary)] border-[var(--color-brand-400)]/40"
            : "bg-[var(--color-surface-300)] text-[var(--color-text-secondary)] hover:bg-[var(--color-surface-400)] hover:text-[var(--color-text-primary)]",
        )}
      >
        {isImageMode ? (
          <ImageIcon size={11} className="text-[var(--color-brand-400)]" />
        ) : (
          <Sparkles size={11} className="text-[var(--color-brand-400)]" />
        )}
        <span>
          {current?.label ?? (isImageMode ? "Image model" : "Chat model")}
        </span>
        <ChevronDown
          size={10}
          className={clsx(
            "transition-transform duration-150",
            open && "rotate-180",
          )}
        />
      </button>

      {open && (
        <div
          className={clsx(
            "absolute left-0 top-[calc(100%+6px)] z-50 w-[210px]",
            "rounded-xl border border-[var(--color-surface-border)]",
            "glass-strong shadow-2xl shadow-black/50 overflow-hidden",
          )}
        >
          <div className="px-3 py-2 border-b border-[var(--color-surface-border)]">
            <p className="text-[10px] font-semibold text-[var(--color-text-muted)] uppercase tracking-wider">
              {isImageMode ? "Modelo de imagen" : "Modelo de chat"}
            </p>
          </div>
          {models.map((model) => {
            const active = (isImageMode ? imageModel : chatModel) === model.id;
            return (
              <button
                key={model.id}
                type="button"
                onClick={() => {
                  onChange(model.id);
                  setOpen(false);
                }}
                className={clsx(
                  "w-full flex items-center justify-between gap-3 px-3 py-2.5",
                  "hover:bg-[var(--color-surface-300)] transition-colors cursor-pointer text-left",
                  active && "bg-[var(--color-surface-200)]",
                )}
              >
                <div>
                  <p
                    className={clsx(
                      "text-[12px] font-medium leading-none",
                      active
                        ? "text-[var(--color-brand-400)]"
                        : "text-[var(--color-text-primary)]",
                    )}
                  >
                    {model.label}
                  </p>
                  <p className="text-[11px] text-[var(--color-text-muted)] mt-0.5">
                    {model.description}
                  </p>
                </div>
                {active && (
                  <Check
                    size={12}
                    className="text-[var(--color-brand-400)] shrink-0"
                  />
                )}
              </button>
            );
          })}
        </div>
      )}
    </div>
  );
}
