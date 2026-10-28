import type {
  CommentNextSnippetAnchor,
  CommentNextSnippetThread,
} from '../types/snippet';
import { captureAnchor } from './anchor';
import { normalizeSnippetText } from './text';

// Prefer a theme's existing content container; semantic article/main work as fallbacks.
const CONTENT_SELECTOR = [
  '[itemprop="articleBody"]',
  '.post-content',
  '.article-content',
  '.entry-content',
  '.markdown-body',
  '.halo-richtext',
  '.prose',
  '#article-content',
  '#post-content',
].join(',');
const EXCLUDED_SELECTOR = [
  'comment-widget',
  'comment-next',
  'comment-next-content',
  'comment-ranking',
  'comment-ranking-sidebar',
  'nav',
  'aside',
  'header',
  'footer',
  '[role="navigation"]',
  'input',
  'textarea',
  'select',
  'button',
  '[contenteditable]',
  '[hidden]',
  '[aria-hidden="true"]',
  '[data-comment-next-snippet-ignore]',
].join(',');

export interface SnippetCandidate {
  text: string;
  source: Element;
  rect: DOMRect;
  anchor: CommentNextSnippetAnchor;
  thread?: CommentNextSnippetThread;
}

export function paragraphCandidate(
  node: EventTarget | null
): SnippetCandidate | undefined {
  if (
    !(node instanceof Element) ||
    node.getRootNode() !== document ||
    node.closest(
      'a, button, input, textarea, select, [contenteditable], pre, code'
    )
  )
    return;
  const paragraph = node.closest('p, li, blockquote');
  if (
    !paragraph ||
    !isArticleText(paragraph) ||
    paragraph.closest('a, pre, code')
  )
    return;
  const text = normalizeSnippetText(paragraph.textContent ?? '');
  if (text.length < 2) return;
  const range = document.createRange();
  range.selectNodeContents(paragraph);
  const root = articleContainer(paragraph);
  if (!root) return;
  return {
    text,
    source: paragraph,
    rect: paragraph.getBoundingClientRect(),
    anchor: captureAnchor(root, range),
  };
}

export function selectedCandidate(): SnippetCandidate | undefined {
  const selection = window.getSelection();
  if (!selection || selection.isCollapsed || selection.rangeCount !== 1) return;
  const range = selection.getRangeAt(0);
  const start = elementForNode(range.startContainer);
  const end = elementForNode(range.endContainer);
  if (!start || !end || !isArticleText(start) || !isArticleText(end)) return;
  const container = articleContainer(start);
  if (!container || !container.contains(end)) return;
  const text = normalizeSnippetText(range.toString());
  if (text.length < 2) return;
  const rects = Array.from(range.getClientRects());
  const rect = rects.at(-1) ?? range.getBoundingClientRect();
  return { text, source: start, rect, anchor: captureAnchor(container, range) };
}

export function snippetPosition(
  rect: DOMRect
): { left: number; top: number } | undefined {
  const size = window.matchMedia?.('(pointer: coarse)').matches ? 44 : 36;
  const padding = 10;
  if (
    rect.width <= 0 ||
    rect.height <= 0 ||
    rect.bottom < 0 ||
    rect.top > window.innerHeight
  )
    return;
  return {
    left: Math.min(
      Math.max(padding, rect.right + 8),
      Math.max(padding, window.innerWidth - size - padding)
    ),
    top: Math.min(
      Math.max(padding, rect.top),
      Math.max(padding, window.innerHeight - size - padding)
    ),
  };
}

export function articleContainer(element: Element): Element | null {
  return element.closest(CONTENT_SELECTOR) ?? element.closest('article, main');
}

function isArticleText(element: Element): boolean {
  return (
    element.getRootNode() === document &&
    !element.closest(EXCLUDED_SELECTOR) &&
    Boolean(articleContainer(element))
  );
}

function elementForNode(node: Node): Element | null {
  return node instanceof Element ? node : node.parentElement;
}
