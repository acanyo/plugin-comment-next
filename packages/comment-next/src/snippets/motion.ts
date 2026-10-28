import { cubicOut } from 'svelte/easing';
import type { TransitionConfig } from 'svelte/transition';

export function snippetTransition(
  _node: Element,
  { duration = 160 }: { duration?: number } = {}
): TransitionConfig {
  const reduceMotion = window.matchMedia?.(
    '(prefers-reduced-motion: reduce)'
  ).matches;
  return {
    duration: reduceMotion ? 0 : duration,
    easing: cubicOut,
    css: (t) =>
      `opacity:${t};transform:translate3d(0,${(1 - t) * 4}px,0);pointer-events:${t < 0.5 ? 'none' : 'auto'}`,
  };
}
