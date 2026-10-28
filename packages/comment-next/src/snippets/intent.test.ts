// @vitest-environment jsdom
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';
import { createSnippetIntent } from './intent';
import type { SnippetCandidate } from './targets';

beforeEach(() => vi.useFakeTimers());
afterEach(() => vi.useRealTimers());

function fixture() {
  const publish = vi.fn();
  const intent = createSnippetIntent(publish);
  const anchor = {
    version: 1 as const,
    exact: '文章片段',
    prefix: '',
    suffix: '',
    start: 0,
  };
  const candidate: SnippetCandidate = {
    source: document.createElement('p'),
    text: anchor.exact,
    anchor,
    rect: new DOMRect(20, 50, 300, 40),
    thread: { key: 'thread', total: 7, anchor },
  };
  return { publish, intent, candidate };
}

describe('snippet hover intent', () => {
  it('ignores a quick pass and movement across a paragraph before dwelling', () => {
    const { publish, intent, candidate } = fixture();
    intent.hover(candidate);
    vi.advanceTimersByTime(180);
    intent.hover();
    vi.advanceTimersByTime(600);
    expect(publish).not.toHaveBeenCalled();
    intent.hover(candidate);
    vi.advanceTimersByTime(180);
    intent.hover(candidate, true);
    vi.advanceTimersByTime(180);
    expect(publish).not.toHaveBeenCalled();
    vi.advanceTimersByTime(60);
    expect(publish).toHaveBeenLastCalledWith(candidate, false);
    intent.destroy();
  });

  it('shows only the count during prolonged hover and keeps the position stable', () => {
    const { publish, intent, candidate } = fixture();
    intent.hover(candidate);
    vi.advanceTimersByTime(240);
    expect(publish).toHaveBeenLastCalledWith(candidate, false);
    intent.hover({ ...candidate, rect: new DOMRect(200, 100, 200, 20) });
    expect(publish).toHaveBeenCalledTimes(1);
    vi.advanceTimersByTime(2000);
    intent.hold();
    vi.advanceTimersByTime(2000);
    expect(publish).toHaveBeenCalledTimes(1);
    expect(publish).toHaveBeenLastCalledWith(candidate, false);
    intent.destroy();
  });

  it('allows crossing a gap into the bubble without opening content', () => {
    const { publish, intent, candidate } = fixture();
    intent.hover(candidate);
    vi.advanceTimersByTime(480);
    intent.hover();
    vi.advanceTimersByTime(220);
    intent.hold();
    vi.advanceTimersByTime(600);
    expect(publish).toHaveBeenLastCalledWith(candidate, false);
    intent.hover();
    vi.advanceTimersByTime(220);
    intent.hover();
    vi.advanceTimersByTime(100);
    expect(publish).toHaveBeenLastCalledWith();
    intent.destroy();
  });

  it('pins on click and suppresses reappearing until the dismissed fragment is left', () => {
    const { publish, intent, candidate } = fixture();
    intent.select(candidate);
    intent.pin();
    intent.hover();
    vi.advanceTimersByTime(1000);
    expect(publish).toHaveBeenLastCalledWith(candidate, true);
    intent.dismiss();
    publish.mockClear();
    intent.hover(candidate);
    vi.advanceTimersByTime(1000);
    expect(publish).not.toHaveBeenCalled();
    intent.hover();
    intent.hover(candidate);
    vi.advanceTimersByTime(240);
    expect(publish).toHaveBeenLastCalledWith(candidate, false);
    intent.destroy();
  });

  it('immediately shows a selected quote and cancels pending work on scroll or destroy', () => {
    const { publish, intent, candidate } = fixture();
    intent.select({ ...candidate, thread: undefined });
    expect(publish).toHaveBeenLastCalledWith(
      expect.objectContaining({ text: '文章片段' }),
      false
    );
    intent.hover(candidate);
    intent.reset();
    publish.mockClear();
    vi.advanceTimersByTime(1000);
    expect(publish).not.toHaveBeenCalled();
    intent.hover(candidate);
    intent.destroy();
    vi.advanceTimersByTime(1000);
    expect(publish).not.toHaveBeenCalled();
  });
});
