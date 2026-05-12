import { useEffect, useState, type ReactNode } from 'react'
import { createPortal } from 'react-dom'

interface PortalProps {
  children: ReactNode
  /** Defaults to `document.body`. Useful in tests to inject a custom root. */
  container?: HTMLElement
}

/**
 * Renders its children into a detached node attached to {@code document.body}
 * (or a custom container).
 *
 * Why this exists: any ancestor with a CSS {@code transform}, {@code filter},
 * {@code perspective} or {@code will-change} property creates a new
 * <a href="https://developer.mozilla.org/docs/Web/CSS/Containing_block">containing
 * block</a> for {@code position: fixed} descendants. That breaks modals,
 * toasts, dropdowns and tooltips: they suddenly stop covering the viewport
 * and dock themselves to the transformed ancestor's box.
 *
 * The mobile sidebar in {@link AppLayout} uses {@code translate-x-*} for its
 * slide-in animation, so any modal rendered as its descendant gets pinned
 * to the 260px-wide sidebar instead of the screen. Portaling to body sidesteps
 * the issue once and for all without contorting the component tree.
 */
export default function Portal({ children, container }: PortalProps) {
  // SSR-safety + lazy mount so we don't try to read `document` during render.
  const [target, setTarget] = useState<HTMLElement | null>(null)

  useEffect(() => {
    setTarget(container ?? document.body)
  }, [container])

  if (!target) return null
  return createPortal(children, target)
}
