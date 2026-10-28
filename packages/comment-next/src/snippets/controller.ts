import type { CommentNextSnippetSelection } from '../types/snippet';
import { createSnippetIntent } from './intent';
import type { SnippetRange } from './ranges';
import {
  paragraphCandidate,
  type SnippetCandidate,
  selectedCandidate,
  snippetPosition,
} from './targets';

type SnippetTarget = {
  host: HTMLElement;
  ranges?: () => SnippetRange[];
  onChange: (selection: CommentNextSnippetSelection | undefined) => void;
};

const targets = new Set<SnippetTarget>();
let stop: (() => void) | undefined;
let refreshCurrentTarget: (() => void) | undefined;
let dismissCurrentTarget: (() => void) | undefined;
let pinCurrentTarget: (() => void) | undefined;

export function dismissSnippetTarget() {
  dismissCurrentTarget?.();
}
export function pinSnippetTarget() {
  pinCurrentTarget?.();
}

export function refreshSnippetTargets() {
  refreshCurrentTarget?.();
}

export function registerSnippetTarget(target: SnippetTarget): () => void {
  targets.add(target);
  stop ??= listenToArticle();
  return () => {
    target.onChange(undefined);
    targets.delete(target);
    if (!targets.size) {
      stop?.();
      stop = undefined;
    }
  };
}

function publish(candidate?: SnippetCandidate, preview = false) {
  const available = [...targets].filter(
    (target) =>
      target.host.isConnected && target.host.getClientRects().length > 0
  );
  const local = candidate
    ? available.filter((target) =>
        target.host.closest('article')?.contains(candidate.source)
      )
    : [];
  // Ambiguous pages with several comment widgets must not send a quote to the wrong post.
  const target =
    local.length === 1
      ? local[0]
      : available.length === 1 && !available[0].host.closest('article')
        ? available[0]
        : undefined;
  const position = candidate && snippetPosition(candidate.rect);
  for (const entry of targets) {
    entry.onChange(
      entry === target && candidate && position
        ? {
            text: candidate.text,
            anchor: candidate.anchor,
            thread: candidate.thread,
            preview,
            ...position,
          }
        : undefined
    );
  }
}

const LAYER_SELECTOR =
  '.comment-next-snippet-trigger, .comment-next-snippet-popover';
const INTERACTIVE_SELECTOR =
  'a, button, input, textarea, select, [contenteditable], pre, code';

function listenToArticle(): () => void {
  const intent = createSnippetIntent(publish);
  let frame: number | undefined;
  let hover: EventTarget | null = null;
  let point: { x: number; y: number } | undefined;
  let previousPoint: { x: number; y: number } | undefined;
  let pressed = false;
  let touchStart: { x: number; y: number } | undefined;
  let moved = false;
  const inLayer = (event: Event) =>
    event
      .composedPath()
      .some((node) => node instanceof Element && node.matches(LAYER_SELECTOR));
  const schedule = () => {
    if (frame !== undefined) return;
    frame = window.requestAnimationFrame(() => {
      frame = undefined;
      if (pressed) return;
      const selection = selectedCandidate();
      if (selection) intent.select(selection);
      else
        intent.hover(savedCandidate(point) ?? paragraphCandidate(hover), moved);
      moved = false;
    });
  };
  const pointerMove = (event: PointerEvent) => {
    if (event.pointerType === 'touch' || pressed) return;
    if (inLayer(event)) {
      intent.hold();
      return;
    }
    const interactive =
      event.target instanceof Element &&
      event.target.closest(INTERACTIVE_SELECTOR);
    hover = interactive ? null : event.target;
    point = interactive ? undefined : { x: event.clientX, y: event.clientY };
    if (
      point &&
      (!previousPoint ||
        Math.hypot(point.x - previousPoint.x, point.y - previousPoint.y) > 6)
    ) {
      previousPoint = point;
      moved = true;
    }
    schedule();
  };
  const pointerDown = (event: PointerEvent) => {
    if (inLayer(event)) return;
    pressed = true;
    intent.dismiss();
    touchStart =
      event.pointerType === 'touch'
        ? { x: event.clientX, y: event.clientY }
        : undefined;
  };
  const pointerUp = (event: PointerEvent) => {
    pressed = false;
    if (inLayer(event)) return;
    const selection = selectedCandidate();
    if (selection) {
      intent.select(selection);
      return;
    }
    if (event.pointerType === 'touch') {
      if (
        !touchStart ||
        Math.hypot(event.clientX - touchStart.x, event.clientY - touchStart.y) >
          8
      )
        return;
      touchStart = undefined;
      if (
        event.target instanceof Element &&
        event.target.closest(INTERACTIVE_SELECTOR)
      )
        return;
      const candidate = savedCandidate({ x: event.clientX, y: event.clientY });
      if (candidate) {
        intent.select(candidate);
      }
    }
  };
  const selectionChange = () => {
    if (!pressed && selectedCandidate()) schedule();
  };
  const keydown = (event: KeyboardEvent) => {
    if (event.key === 'Escape') {
      hover = null;
      point = undefined;
      intent.dismiss();
    }
  };
  const scroll = (event: Event) => {
    if (event.target instanceof Element && event.target.closest(LAYER_SELECTOR))
      return;
    hover = null;
    point = undefined;
    previousPoint = undefined;
    touchStart = undefined;
    intent.reset();
    // A pending frame must not reopen a selection during scrolling.
    if (frame !== undefined) window.cancelAnimationFrame(frame);
    frame = undefined;
  };
  const focus = (event: FocusEvent) => {
    if (inLayer(event)) {
      intent.hold();
      return;
    }
    hover = null;
    point = undefined;
    intent.dismiss();
  };
  const leave = () => {
    hover = null;
    point = undefined;
    intent.hover();
  };
  const cancelPointer = () => {
    pressed = false;
    touchStart = undefined;
    intent.reset();
  };
  refreshCurrentTarget = schedule;
  dismissCurrentTarget = () => {
    hover = null;
    point = undefined;
    intent.dismiss();
  };
  pinCurrentTarget = () => intent.pin();
  document.addEventListener('pointermove', pointerMove, { passive: true });
  document.addEventListener('pointerdown', pointerDown);
  document.addEventListener('pointerup', pointerUp);
  document.addEventListener('pointercancel', cancelPointer);
  document.addEventListener('pointerleave', leave);
  document.addEventListener('selectionchange', selectionChange);
  document.addEventListener('keydown', keydown);
  document.addEventListener('focusin', focus);
  window.addEventListener('scroll', scroll, true);
  window.addEventListener('resize', scroll);
  window.addEventListener('blur', cancelPointer);
  return () => {
    document.removeEventListener('pointermove', pointerMove);
    document.removeEventListener('pointerdown', pointerDown);
    document.removeEventListener('pointerup', pointerUp);
    document.removeEventListener('pointercancel', cancelPointer);
    document.removeEventListener('pointerleave', leave);
    document.removeEventListener('selectionchange', selectionChange);
    document.removeEventListener('keydown', keydown);
    document.removeEventListener('focusin', focus);
    window.removeEventListener('scroll', scroll, true);
    window.removeEventListener('resize', scroll);
    window.removeEventListener('blur', cancelPointer);
    if (frame !== undefined) window.cancelAnimationFrame(frame);
    intent.destroy();
    refreshCurrentTarget = undefined;
    dismissCurrentTarget = undefined;
    pinCurrentTarget = undefined;
  };
}

function savedCandidate(point?: {
  x: number;
  y: number;
}): SnippetCandidate | undefined {
  if (!point) return;
  for (const target of targets) {
    for (const entry of target.ranges?.() ?? []) {
      if (!entry.range.startContainer.isConnected) continue;
      const rect = [...entry.range.getClientRects()].find(
        (rect) =>
          point.x >= rect.left - 2 &&
          point.x <= rect.right + 2 &&
          point.y >= rect.top - 2 &&
          point.y <= rect.bottom + 2
      );
      if (rect)
        return {
          text: entry.thread.anchor.exact,
          anchor: entry.thread.anchor,
          source: entry.range.startContainer.parentElement ?? target.host,
          rect: entry.range.getBoundingClientRect(),
          thread: entry.thread,
        };
    }
  }
}
