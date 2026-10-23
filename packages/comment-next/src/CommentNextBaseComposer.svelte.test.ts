import { flushSync, mount, tick, unmount } from 'svelte';
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';
import CommentNextBaseComposer from './CommentNextBaseComposer.svelte';

vi.mock('./services/current-user', () => ({
  fetchCurrentUser: vi.fn(async () => undefined),
}));

let component: ReturnType<typeof mount>;
let resolveSuggestion: (response: Response) => void;
let fetchMock: ReturnType<typeof vi.fn>;

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
  fetchMock = vi.fn(
    () =>
      new Promise<Response>((resolve) => {
        resolveSuggestion = resolve;
      })
  );
  vi.stubGlobal('fetch', fetchMock);
  component = mount(CommentNextBaseComposer, {
    target: document.body,
    props: {
      loggedIn: true,
      subject: 'test-article',
      aiConfig: {
        enabled: true,
        foundationAvailable: true,
        mentionAutoReplyEnabled: true,
      },
      uploadConfig: { enabled: true, authenticatedProvider: 'HALO_ATTACHMENT' },
      onSubmit: async () => ({ approved: true }),
    },
  });
  flushSync();
});

afterEach(async () => {
  await unmount(component);
  document.body.innerHTML = '';
  vi.unstubAllGlobals();
});

describe('AI writing across editor changes', () => {
  it('shows loading and results again after accepting and submitting comments', async () => {
    await generateSuggestion();
    editor().click();
    document.body.dispatchEvent(
      new PointerEvent('pointerdown', { bubbles: true })
    );
    editor().dispatchEvent(
      new KeyboardEvent('keydown', { key: 'Escape', bubbles: true })
    );
    await tick();
    expect(document.body.textContent).toContain('正在整理评论建议...');
    await completeSuggestion('第一次建议');
    await clickButton('接受');
    expect(editor().textContent).toBe('第一次建议');

    await generateSuggestion();
    await completeSuggestion('第二次建议');
    await clickButton('接受');
    expect(editor().textContent).toBe('第二次建议');

    await clickButton('提交');
    await vi.waitFor(() => expect(editor().textContent).toBe(''));

    await generateSuggestion();
    await completeSuggestion('第三次建议');
    await clickButton('接受');
    expect(editor().textContent).toBe('第三次建议');
    expect(fetchMock).toHaveBeenCalledTimes(3);
  });

  it('keeps the draft when closing a suggestion and supports inserting the next result', async () => {
    editor().textContent = '原有草稿';
    editor().dispatchEvent(new InputEvent('input', { bubbles: true }));
    await tick();
    await generateSuggestion();
    await completeSuggestion('未采用的建议');
    const close = document.querySelector<HTMLButtonElement>(
      '[aria-label="关闭评论助手建议"]'
    );
    expect(close).not.toBeNull();
    close?.click();
    await tick();
    expect(editor().textContent).toBe('原有草稿');

    await generateSuggestion();
    await completeSuggestion('补充内容');
    editor().focus();
    const range = document.createRange();
    range.selectNodeContents(editor());
    range.collapse(false);
    window.getSelection()?.removeAllRanges();
    window.getSelection()?.addRange(range);
    await clickButton('插入');
    expect(editor().textContent).toBe('原有草稿补充内容');

    await generateSuggestion();
    await completeSuggestion('再次生成');
  });

  it('restores the toolbar after a generation error and allows trying again', async () => {
    await generateSuggestion();
    resolveSuggestion(
      new Response(JSON.stringify({ detail: 'AI 服务暂时不可用' }), {
        status: 502,
        headers: { 'Content-Type': 'application/json' },
      })
    );
    await vi.waitFor(() =>
      expect(document.body.textContent).toContain('AI 服务暂时不可用')
    );
    for (const selector of [
      '[data-comment-next-tool="at"]',
      '[data-comment-next-tool="image"]',
      '[aria-label="AI 评论"]',
    ]) {
      expect(
        document.querySelector<HTMLButtonElement>(selector)?.disabled
      ).toBe(false);
    }
    await generateSuggestion();
    await completeSuggestion('重试后的建议');
  });
});

function editor(): HTMLDivElement {
  const element = document.querySelector<HTMLDivElement>(
    '[role="textbox"][aria-label="评论内容"]'
  );
  if (!element) throw new Error('Comment editor was not mounted');
  return element;
}

async function clickButton(label: string) {
  const button = Array.from(document.querySelectorAll('button')).find(
    (element) => element.textContent?.trim() === label
  );
  expect(button, `button ${label}`).toBeDefined();
  button?.click();
  await tick();
}

async function generateSuggestion() {
  await clickButton('AI 评论');
  await clickButton('生成评论');
  await clickButton('内容实用');
  await clickButton('生成');
  expect(document.body.textContent).toContain('正在整理评论建议...');
  expect(editor().textContent).not.toContain('正在整理评论建议...');
  for (const selector of [
    '[data-comment-next-tool="at"]',
    '[data-comment-next-tool="image"]',
    '[aria-label="AI 评论"]',
  ]) {
    expect(document.querySelector<HTMLButtonElement>(selector)?.disabled).toBe(
      true
    );
  }
}

async function completeSuggestion(text: string) {
  resolveSuggestion(
    new Response(JSON.stringify({ text }), {
      headers: { 'Content-Type': 'application/json' },
    })
  );
  await vi.waitFor(() => {
    expect(
      document.querySelector('.comment-next-ai-suggestion')?.textContent
    ).toContain(text);
  });
  expect(
    document.querySelector<HTMLButtonElement>('[aria-label="AI 评论"]')
      ?.disabled
  ).toBe(false);
}
