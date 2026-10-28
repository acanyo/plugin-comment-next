import { flushSync, mount, tick, unmount } from 'svelte';
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';
import CommentNextBaseComposer from './CommentNextBaseComposer.svelte';
import type { CommentNextComposerSubmitPayload } from './types/composer';

vi.mock('./services/current-user', () => ({
  fetchCurrentUser: async () => undefined,
}));

let component: ReturnType<typeof mount>;
let submit = vi.fn(
  async (_payload: CommentNextComposerSubmitPayload): Promise<unknown> => ({
    approved: true,
  })
);

beforeEach(() => {
  vi.stubGlobal('matchMedia', () => ({
    matches: false,
    addEventListener() {},
    removeEventListener() {},
  }));
  vi.stubGlobal(
    'ResizeObserver',
    class {
      observe() {}
      disconnect() {}
    }
  );
  HTMLElement.prototype.scrollIntoView = vi.fn();
  submit = vi.fn(
    async (_payload: CommentNextComposerSubmitPayload): Promise<unknown> => ({
      approved: true,
    })
  );
  component = mount(CommentNextBaseComposer, {
    target: document.body,
    props: { loggedIn: true, showAi: false, onSubmit: submit },
  });
  flushSync();
});

afterEach(async () => {
  await unmount(component);
  document.body.replaceChildren();
  vi.restoreAllMocks();
  vi.unstubAllGlobals();
});

function quote(text: string): boolean {
  return (
    component as {
      quoteSnippet: (
        anchor: import('./types/snippet').CommentNextSnippetAnchor
      ) => boolean;
    }
  ).quoteSnippet({ version: 1, exact: text, prefix: '', suffix: '', start: 0 });
}

function write(text: string) {
  const editor = document.querySelector(
    '[contenteditable="true"]'
  ) as HTMLDivElement;
  editor.textContent = text;
  editor.dispatchEvent(new InputEvent('input', { bubbles: true }));
}

function send() {
  document
    .querySelector('form')
    ?.dispatchEvent(
      new SubmitEvent('submit', { bubbles: true, cancelable: true })
    );
}

describe('article snippet composer', () => {
  it('preserves the draft, submits the escaped excerpt and clears it after success', async () => {
    write('我的观点');
    expect(quote('原文 <script> & 内容')).toBe(true);
    await tick();
    expect(document.querySelector('[contenteditable]')?.textContent).toBe(
      '我的观点'
    );
    send();
    await vi.waitFor(() => expect(submit).toHaveBeenCalledOnce());
    expect(submit.mock.calls[0][0].content).toContain('<blockquote');
    expect(submit.mock.calls[0][0].content).toContain(
      '原文 &lt;script&gt; &amp; 内容'
    );
    expect(submit.mock.calls[0][0].content).toContain('我的观点');
    await vi.waitFor(() =>
      expect(document.querySelector('[aria-label="引用的文章片段"]')).toBeNull()
    );
  });

  it('keeps the excerpt and draft when submission fails', async () => {
    submit.mockRejectedValueOnce(new Error('请稍后重试'));
    quote('原文片段');
    write('未提交的观点');
    send();
    await vi.waitFor(() =>
      expect(document.body.textContent).toContain('请稍后重试')
    );
    expect(
      document.querySelector('[aria-label="引用的文章片段"]')?.textContent
    ).toContain('原文片段');
    expect(document.querySelector('[contenteditable]')?.textContent).toBe(
      '未提交的观点'
    );
  });

  it('removes the quote without clearing the comment draft', async () => {
    quote('要移除的片段');
    write('保留观点');
    await tick();
    (
      document.querySelector(
        '[aria-label="移除文章片段引用"]'
      ) as HTMLButtonElement
    ).click();
    send();
    await vi.waitFor(() => expect(submit).toHaveBeenCalledOnce());
    expect(submit.mock.calls[0][0].content).toBe('保留观点');
  });

  it('requires a comment instead of submitting the excerpt alone', async () => {
    quote('只有引用');
    send();
    await tick();
    expect(submit).not.toHaveBeenCalled();
    expect(document.body.textContent).toContain('请先输入评论内容');
  });
});
