export type CommentAnchorKind = 'comment' | 'reply';

export interface CommentAnchorTarget {
  kind: CommentAnchorKind;
  id: string;
}

export function parseCommentAnchor(
  hash: string
): CommentAnchorTarget | undefined {
  if (!hash.startsWith('#comment-next-')) return undefined;
  try {
    const fragment = decodeURIComponent(hash.slice(1));
    const match = /^comment-next-(comment|reply)-(.+)$/u.exec(fragment);
    if (match)
      return { kind: match[1] === 'reply' ? 'reply' : 'comment', id: match[2] };
  } catch {
    return undefined;
  }
}

export function getCommentAnchorId(
  kind: CommentAnchorKind,
  id: string
): string {
  return `comment-next-${kind}-${id}`;
}

export function getCommentAnchorHref(
  kind: CommentAnchorKind,
  id: string
): string {
  return `#${encodeURIComponent(getCommentAnchorId(kind, id))}`;
}

export function commentAnchor(node: HTMLElement, options: CommentAnchorTarget) {
  node.style.scrollMarginTop = 'var(--comment-next-anchor-offset, 5rem)';
  node.tabIndex = -1;
  node.id = getCommentAnchorId(options.kind, options.id);
  return {
    update(nextOptions: CommentAnchorTarget) {
      node.id = getCommentAnchorId(nextOptions.kind, nextOptions.id);
    },
  };
}

export function findCommentAnchor(
  root: HTMLElement,
  target: CommentAnchorTarget
) {
  const id = getCommentAnchorId(target.kind, target.id);
  return Array.from(root.querySelectorAll<HTMLElement>('article[id]')).find(
    (node) => node.id === id
  );
}

export function revealCommentAnchor(node: HTMLElement): () => void {
  node.setAttribute('data-comment-next-highlight', '');
  node.focus({ preventScroll: true });
  const reduceMotion = window.matchMedia?.(
    '(prefers-reduced-motion: reduce)'
  ).matches;
  node.scrollIntoView?.({
    behavior: reduceMotion ? 'instant' : 'smooth',
    block: 'start',
  });

  const clear = () => node.removeAttribute('data-comment-next-highlight');
  const timer = window.setTimeout(clear, 2800);
  return () => {
    window.clearTimeout(timer);
    clear();
  };
}
