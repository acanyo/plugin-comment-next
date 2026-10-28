// @vitest-environment jsdom
import { afterEach, describe, expect, it, vi } from 'vitest';
import {
  paragraphCandidate,
  selectedCandidate,
  snippetPosition,
} from './targets';

afterEach(() => {
  window.getSelection()?.removeAllRanges();
  document.body.replaceChildren();
  vi.restoreAllMocks();
});

describe('article snippet targeting', () => {
  it('finds paragraphs using existing theme markup without inserting article wrappers', () => {
    document.body.innerHTML =
      '<main><article><div class="post-content"><p>引用<strong>这一段</strong></p></div></article></main>';
    expect(paragraphCandidate(document.querySelector('strong'))?.text).toBe(
      '引用这一段'
    );
    expect(document.querySelectorAll('.post-content').length).toBe(1);
  });

  it('ignores navigation, account inputs and comment content', () => {
    document.body.innerHTML =
      '<main><nav><p>导航内容</p></nav><aside><p>侧栏内容</p></aside><article><p contenteditable="true">输入内容</p><comment-widget><p>评论内容</p></comment-widget></article></main>';
    for (const paragraph of document.querySelectorAll('p')) {
      expect(paragraphCandidate(paragraph)).toBeUndefined();
    }
    expect(paragraphCandidate(document.body)).toBeUndefined();
  });

  it('leaves links and inline code available without opening a comment action', () => {
    document.body.innerHTML =
      '<article><p>正文 <a href="/">链接</a> <code>代码</code></p></article>';
    expect(paragraphCandidate(document.querySelector('a'))).toBeUndefined();
    expect(paragraphCandidate(document.querySelector('code'))).toBeUndefined();
    expect(paragraphCandidate(document.querySelector('p'))?.text).toContain(
      '正文'
    );
  });

  it('quotes only the selected fragment across inline nodes', () => {
    document.body.innerHTML =
      '<article><p>这是<strong>目标片段</strong>的结尾</p></article>';
    const paragraph = document.querySelector('p') as HTMLParagraphElement;
    const range = document.createRange();
    range.setStart(paragraph.firstChild as Node, 1);
    range.setEnd(paragraph.querySelector('strong')?.firstChild as Node, 4);
    range.getClientRects = () =>
      [new DOMRect(30, 50, 100, 24)] as unknown as DOMRectList;
    window.getSelection()?.addRange(range);
    expect(selectedCandidate()?.text).toBe('是目标片段');
  });

  it('does not attach a cross-article selection to one article', () => {
    document.body.innerHTML =
      '<main><article><p>第一篇</p></article><article><p>第二篇</p></article></main>';
    const paragraphs = document.querySelectorAll('p');
    const range = document.createRange();
    range.setStart(paragraphs[0].firstChild as Node, 0);
    range.setEnd(paragraphs[1].firstChild as Node, 3);
    window.getSelection()?.addRange(range);
    expect(selectedCandidate()).toBeUndefined();
  });

  it('reserves room for a larger touch target near the screen edge', () => {
    vi.stubGlobal('innerWidth', 390);
    vi.stubGlobal('matchMedia', () => ({ matches: true }));
    expect(snippetPosition(new DOMRect(20, 30, 360, 24))?.left).toBe(336);
    vi.unstubAllGlobals();
  });

  it('keeps the action inside small viewports and hides offscreen ranges', () => {
    vi.stubGlobal('innerWidth', 390);
    expect(snippetPosition(new DOMRect(20, 30, 360, 24))?.left).toBe(344);
    expect(snippetPosition(new DOMRect(20, -100, 100, 24))).toBeUndefined();
    vi.unstubAllGlobals();
  });
});
