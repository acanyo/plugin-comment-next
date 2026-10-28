import type { CommentNextSnippetAnchor } from '../types/snippet';
import { normalizeSnippetText } from './text';

const MARKER = '<!--comment-next-snippet:';

export function snippetRaw(
  content: string,
  anchor?: CommentNextSnippetAnchor
): string {
  return anchor
    ? `${MARKER}${encodeURIComponent(JSON.stringify(anchor))}-->${content}`
    : content;
}

export function captureAnchor(
  root: Element,
  range: Range
): CommentNextSnippetAnchor {
  const before = range.cloneRange();
  before.selectNodeContents(root);
  before.setEnd(range.startContainer, range.startOffset);
  const after = range.cloneRange();
  after.selectNodeContents(root);
  after.setStart(range.endContainer, range.endOffset);
  const prefix = whitespace(rangeText(before)).trimStart();
  const exact = normalizeSnippetText(range.toString());
  const remainder = whitespace(range.toString()).trim().slice(exact.length);
  return {
    version: 1,
    exact,
    prefix: prefix.slice(-64),
    suffix: (remainder + whitespace(rangeText(after)).trimEnd()).slice(0, 64),
    start: prefix.length,
  };
}

export function locateAnchor(
  root: Element,
  anchor: CommentNextSnippetAnchor
): Range | undefined {
  const walker = document.createTreeWalker(root, NodeFilter.SHOW_TEXT, {
    acceptNode: (node) =>
      node.parentElement?.closest(
        'script,style,noscript,comment-widget,comment-next,[hidden],[aria-hidden="true"]'
      )
        ? NodeFilter.FILTER_REJECT
        : NodeFilter.FILTER_ACCEPT,
  });
  const points: Array<{ node: Node; offset: number }> = [];
  let text = '';
  while (walker.nextNode()) {
    const node = walker.currentNode;
    const value = node.textContent ?? '';
    for (let offset = 0; offset < value.length; offset++) {
      const char = /\s/.test(value[offset]) ? ' ' : value[offset];
      if (char === ' ' && (!text || text.endsWith(' '))) continue;
      text += char;
      points.push({ node, offset });
    }
  }
  const matches: number[] = [];
  let cursor = text.indexOf(anchor.exact);
  while (cursor >= 0) {
    matches.push(cursor);
    cursor = text.indexOf(anchor.exact, cursor + 1);
  }
  const contextual = matches.filter(
    (start) =>
      (!anchor.prefix || text.slice(0, start).endsWith(anchor.prefix)) &&
      (!anchor.suffix ||
        text.slice(start + anchor.exact.length).startsWith(anchor.suffix))
  );
  const start =
    contextual.length === 1
      ? contextual[0]
      : matches.length === 1
        ? matches[0]
        : undefined;
  // Ambiguous repeated text remains unanchored instead of pointing to the wrong occurrence.
  if (
    start === undefined ||
    !points[start] ||
    !points[start + anchor.exact.length - 1]
  )
    return;
  const first = points[start];
  const last = points[start + anchor.exact.length - 1];
  const range = document.createRange();
  range.setStart(first.node, first.offset);
  range.setEnd(last.node, last.offset + 1);
  return range;
}

function whitespace(value: string): string {
  return value.replaceAll(/\s+/g, ' ');
}

function rangeText(range: Range): string {
  const fragment = range.cloneContents();
  fragment
    .querySelectorAll(
      'script,style,noscript,comment-widget,comment-next,[hidden],[aria-hidden="true"]'
    )
    .forEach((node) => {
      node.remove();
    });
  return fragment.textContent ?? '';
}
