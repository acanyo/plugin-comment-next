// @vitest-environment jsdom
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';
import { registerSnippetTarget } from './controller';

const cleanups: Array<() => void> = [];

beforeEach(() => {
  vi.useFakeTimers();
  vi.spyOn(HTMLElement.prototype, 'getBoundingClientRect').mockReturnValue(
    new DOMRect(30, 50, 300, 50)
  );
  vi.spyOn(HTMLElement.prototype, 'getClientRects').mockReturnValue([
    new DOMRect(30, 50, 300, 50),
  ] as unknown as DOMRectList);
});

afterEach(() => {
  for (const cleanup of cleanups.splice(0)) cleanup();
  document.body.replaceChildren();
  vi.restoreAllMocks();
  vi.useRealTimers();
});

function target(id: string) {
  const onChange = vi.fn();
  cleanups.push(
    registerSnippetTarget({
      host: document.getElementById(id) as HTMLElement,
      onChange,
    })
  );
  return onChange;
}

function hover(id: string) {
  document
    .getElementById(id)
    ?.dispatchEvent(
      new PointerEvent('pointermove', { bubbles: true, pointerType: 'mouse' })
    );
  vi.advanceTimersByTime(260);
}

describe('article snippet routing', () => {
  it('sends a paragraph to its own comment widget when several posts are present', () => {
    document.body.innerHTML =
      '<main><article><p id="a-text">文章甲正文</p><comment-widget id="a"></comment-widget></article><article><p id="b-text">文章乙正文</p><comment-widget id="b"></comment-widget></article></main>';
    const first = target('a');
    const second = target('b');
    hover('a-text');
    expect(first).toHaveBeenLastCalledWith(
      expect.objectContaining({ text: '文章甲正文' })
    );
    expect(second).toHaveBeenLastCalledWith(undefined);
    hover('b-text');
    expect(first).toHaveBeenLastCalledWith(undefined);
    expect(second).toHaveBeenLastCalledWith(
      expect.objectContaining({ text: '文章乙正文' })
    );
  });

  it('does not fall back to another article when only its widget is available', () => {
    document.body.innerHTML =
      '<main><article><p id="other">其他文章</p></article><article><comment-widget id="current"></comment-widget></article></main>';
    const current = target('current');
    hover('other');
    expect(current).toHaveBeenLastCalledWith(undefined);
  });

  it('supports a comment widget outside the article and dismisses on scroll or Escape', () => {
    document.body.innerHTML =
      '<main><article><p id="text">正文内容</p></article><comment-widget id="widget"></comment-widget></main>';
    const changed = target('widget');
    hover('text');
    expect(changed).toHaveBeenLastCalledWith(
      expect.objectContaining({ text: '正文内容' })
    );
    window.dispatchEvent(new Event('scroll'));
    expect(changed).toHaveBeenLastCalledWith(undefined);
    hover('text');
    document.dispatchEvent(new KeyboardEvent('keydown', { key: 'Escape' }));
    expect(changed).toHaveBeenLastCalledWith(undefined);
  });

  it('leaves no active selection handlers after the widget is removed', () => {
    document.body.innerHTML =
      '<main><article><p id="text">正文内容</p></article><comment-widget id="widget"></comment-widget></main>';
    const changed = target('widget');
    cleanups.pop()?.();
    changed.mockClear();
    hover('text');
    expect(changed).not.toHaveBeenCalled();
  });
});
