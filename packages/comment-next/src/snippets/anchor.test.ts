// @vitest-environment jsdom
import { afterEach, describe, expect, it } from 'vitest';
import { captureAnchor, locateAnchor, snippetRaw } from './anchor';

afterEach(() => document.body.replaceChildren());

function fixture(html: string) {
  document.body.innerHTML = `<article>${html}</article>`;
  const root = document.querySelector('article') as HTMLElement;
  const paragraph = root.querySelector('p') as HTMLElement;
  const range = document.createRange();
  range.selectNodeContents(paragraph);
  return { root, range };
}

describe('persistent article snippets', () => {
  it('finds text across inline formatting again after surrounding article text changes', () => {
    const { root, range } = fixture(
      '<p>这一段<strong>原文</strong>内容</p><p>后续内容</p>'
    );
    const anchor = captureAnchor(root, range);
    root.insertAdjacentHTML('afterbegin', '<p>后来补充的新段落。</p>');
    expect(locateAnchor(root, anchor)?.toString()).toBe('这一段原文内容');
  });

  it('uses context to separate repeated text instead of relying on the old DOM offset', () => {
    const { root, range } = fixture(
      '<p>相同原文</p><p>不同的后文</p><p>相同原文</p>'
    );
    const anchor = captureAnchor(root, range);
    root.insertAdjacentHTML('afterbegin', '<p>新增前文</p>');
    expect(locateAnchor(root, anchor)?.startContainer.parentElement).toBe(
      root.querySelectorAll('p')[1]
    );
  });

  it('leaves ambiguous or removed fragments unattached', () => {
    const { root } = fixture('<p>重复</p><p>重复</p>');
    const anchor = {
      version: 1 as const,
      exact: '重复',
      prefix: '',
      suffix: '',
      start: 0,
    };
    expect(locateAnchor(root, anchor)).toBeUndefined();
    expect(
      locateAnchor(root, { ...anchor, exact: '已删除的内容' })
    ).toBeUndefined();
  });

  it('does not include theme style tags in the persistent context', () => {
    const { root, range } = fixture(
      '<style>p {color:red}</style><p>正文</p><p>后文</p>'
    );
    const anchor = captureAnchor(root, range);
    expect(anchor.prefix).toBe('');
    expect(locateAnchor(root, anchor)?.toString()).toBe('正文');
  });

  it('keeps locating metadata out of the displayed HTML', () => {
    const { root, range } = fixture('<p>原文</p>');
    const anchor = captureAnchor(root, range);
    const raw = snippetRaw('<p>评论</p>', anchor);
    expect(raw).toMatch(/^<!--comment-next-snippet:/);
    expect(
      JSON.parse(
        decodeURIComponent(
          raw.slice('<!--comment-next-snippet:'.length, raw.indexOf('-->'))
        )
      )
    ).toEqual(anchor);
    expect(snippetRaw('<p>普通评论</p>')).toBe('<p>普通评论</p>');
  });
});
