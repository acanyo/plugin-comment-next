import { describe, expect, it } from 'vitest';
import { createSnippetQuoteHtml, normalizeSnippetText } from './text';

describe('article snippet quotes', () => {
  it('keeps the quote as text instead of executing article markup', () => {
    const html = createSnippetQuoteHtml(
      '<img src=x onerror="alert(1)"> & 内容'
    );
    expect(html).toContain(
      '&lt;img src=x onerror=&quot;alert(1)&quot;&gt; &amp; 内容'
    );
    expect(html).not.toContain('<img');
  });

  it('normalizes selection whitespace and limits long excerpts', () => {
    expect(normalizeSnippetText('  第一行\n  第二行\t内容 ')).toBe(
      '第一行 第二行 内容'
    );
    expect(normalizeSnippetText('字'.repeat(1300))).toHaveLength(1200);
    expect(normalizeSnippetText('字'.repeat(1300))).toBe('字'.repeat(1200));
  });
});
