import type { CommentNextSnippetThread } from '../types/snippet';
import { locateAnchor } from './anchor';

export interface SnippetRange {
  thread: CommentNextSnippetThread;
  range: Range;
}

export function resolveSnippetRanges(
  host: HTMLElement,
  threads: CommentNextSnippetThread[]
): SnippetRange[] {
  const article = host.closest('article');
  const scope = article ?? document;
  let roots = [
    ...scope.querySelectorAll(
      '[itemprop="articleBody"], .post-content, .article-content, .entry-content, .markdown-body, .prose, .halo-richtext'
    ),
  ];
  if (article?.matches('.markdown-body,.prose,[itemprop="articleBody"]'))
    roots.unshift(article);
  if (!roots.length)
    roots = article ? [article] : [...document.querySelectorAll('article')];
  if (!roots.length) roots = [...document.querySelectorAll('main')];
  roots = roots.filter(
    (root) =>
      !root.closest('nav,aside,header,footer,comment-widget,comment-next')
  );
  const ranges: SnippetRange[] = [];
  for (const thread of threads) {
    const matches: Range[] = [];
    for (const root of roots) {
      const range = locateAnchor(root, thread.anchor);
      if (
        range &&
        !matches.some(
          (other) =>
            other.startContainer === range.startContainer &&
            other.startOffset === range.startOffset
        )
      )
        matches.push(range);
    }
    if (matches.length === 1) {
      const range = matches[0];
      const existing = ranges.find(
        (entry) =>
          entry.range.startContainer === range.startContainer &&
          entry.range.startOffset === range.startOffset &&
          entry.range.endContainer === range.endContainer &&
          entry.range.endOffset === range.endOffset
      );
      if (existing)
        existing.thread = {
          ...existing.thread,
          key: `${existing.thread.key},${thread.key}`,
          total: existing.thread.total + thread.total,
        };
      else ranges.push({ thread, range });
    }
  }
  return ranges;
}
