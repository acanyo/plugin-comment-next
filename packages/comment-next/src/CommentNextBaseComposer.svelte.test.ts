import { type ComponentProps, flushSync, mount, tick, unmount } from 'svelte';
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';
import CommentNextBaseComposer from './CommentNextBaseComposer.svelte';
import {
  commentDraftKey,
  rememberCommentDraftForLogin,
} from './services/comment-drafts';
import { fetchCurrentUser } from './services/current-user';

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

    await clickButton('发布评论');
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

describe('commenting identity', () => {
  beforeEach(() => {
    localStorage.clear();
    sessionStorage.clear();
  });

  it('opens both options from the avatar and saves guest details without publishing', async () => {
    const onSubmit = vi.fn();
    await remount({ onSubmit });
    expect(document.querySelector('input[name="displayName"]')).toBeNull();

    await clickAvatar();
    expect(identityDialog()?.textContent).toContain('账号登录');
    expect(identityDialog()?.textContent).toContain('游客评论');
    await clickButton('游客评论');

    expect(document.activeElement).toBe(
      identityDialog()?.querySelector('input[name="displayName"]')
    );
    await fillGuestDetails();
    await clickButton('保存资料');
    expect(identityDialog()).toBeNull();
    expect(onSubmit).not.toHaveBeenCalled();

    await clickAvatar();
    await clickButton('游客评论');
    expect(
      identityDialog()?.querySelector<HTMLInputElement>(
        'input[name="displayName"]'
      )?.value
    ).toBe('测试读者');
    identityDialog()?.dispatchEvent(new Event('cancel', { cancelable: true }));
    await tick();
    expect(document.activeElement?.getAttribute('aria-label')).toBe(
      '选择评论身份'
    );
  });

  it('validates the comment first, then publishes once after guest confirmation', async () => {
    const onSubmit = vi.fn(async () => ({ approved: true }));
    await remount({ onSubmit });

    await clickButton('发布评论');
    expect(identityDialog()).toBeNull();
    expect(document.body.textContent).toContain('请先输入评论内容');

    editor().textContent = '先写下的评论';
    editor().dispatchEvent(new InputEvent('input', { bubbles: true }));
    await clickButton('发布评论');
    expect(onSubmit).not.toHaveBeenCalled();
    await clickButton('游客评论');
    const guestSubmit = identityDialog()?.querySelector<HTMLButtonElement>(
      'button[type="submit"]'
    );
    expect(guestSubmit?.textContent).toBe('发布评论');
    guestSubmit?.click();
    await tick();
    expect(onSubmit).not.toHaveBeenCalled();

    await fillGuestDetails();
    guestSubmit?.click();
    await vi.waitFor(() => expect(onSubmit).toHaveBeenCalledTimes(1));
    expect(onSubmit).toHaveBeenCalledWith(
      expect.objectContaining({
        content: '先写下的评论',
        owner: {
          displayName: '测试读者',
          email: 'reader@halo.run',
          website: '',
        },
      })
    );
    expect(identityDialog()).toBeNull();
  });

  it('offers only account login when the site requires it', async () => {
    await remount({ allowAnonymous: false });
    await clickButton('登录后评论');

    expect(identityDialog()?.textContent).toContain('账号登录');
    expect(identityDialog()?.textContent).not.toContain('游客评论');
    identityDialog()?.dispatchEvent(new Event('cancel', { cancelable: true }));
    await tick();
    expect(identityDialog()).toBeNull();
  });

  it('continues a confirmed guest comment to the configured CAPTCHA', async () => {
    const onSubmit = vi.fn();
    await remount({ onSubmit, showCaptcha: true });
    editor().textContent = '需要安全验证的评论';
    editor().dispatchEvent(new InputEvent('input', { bubbles: true }));

    await clickButton('发布评论');
    await clickButton('游客评论');
    await fillGuestDetails();
    identityDialog()
      ?.querySelector<HTMLButtonElement>('button[type="submit"]')
      ?.click();

    await vi.waitFor(() =>
      expect(document.body.textContent).toContain('安全验证')
    );
    expect(identityDialog()).toBeNull();
    expect(onSubmit).not.toHaveBeenCalled();
  });

  it.each([false, true])(
    'restores the guest draft after login when appropriate (account draft %s)',
    async (accountHasDraft) => {
      const target = 'test-draft-login-target';
      const guestDraft = { html: '登录前写的评论', privateComment: false };
      const guestKey = commentDraftKey(target);
      const accountKey = commentDraftKey(target, 'reader');
      localStorage.setItem(guestKey, JSON.stringify(guestDraft));
      if (accountHasDraft) {
        localStorage.setItem(
          accountKey,
          JSON.stringify({ html: '账号的旧草稿', privateComment: false })
        );
      }
      rememberCommentDraftForLogin(target, guestDraft);
      vi.mocked(fetchCurrentUser).mockResolvedValueOnce({
        name: 'reader',
        displayName: '读者',
      });
      await remount({ draftTarget: target });

      const expectedDraft = accountHasDraft ? '账号的旧草稿' : '登录前写的评论';
      await vi.waitFor(() => expect(editor().textContent).toBe(expectedDraft));
      expect(localStorage.getItem(accountKey)).toContain(expectedDraft);
      expect(localStorage.getItem(guestKey) !== null).toBe(accountHasDraft);
    }
  );
});

function identityDialog() {
  return document.querySelector<HTMLDialogElement>(
    'dialog.comment-next-identity-dialog[open]'
  );
}

async function remount(props: ComponentProps<typeof CommentNextBaseComposer>) {
  await unmount(component);
  component = mount(CommentNextBaseComposer, { target: document.body, props });
  await tick();
}

async function clickAvatar() {
  document
    .querySelector<HTMLButtonElement>(
      'button[aria-haspopup="dialog"][aria-label="选择评论身份"]'
    )
    ?.click();
  await tick();
}

async function fillGuestDetails() {
  for (const [name, value] of [
    ['displayName', '测试读者'],
    ['email', 'reader@halo.run'],
  ]) {
    const input = identityDialog()?.querySelector<HTMLInputElement>(
      `input[name="${name}"]`
    );
    if (!input) throw new Error(`Missing guest field ${name}`);
    input.value = value;
    input.dispatchEvent(new InputEvent('input', { bubbles: true }));
    await tick();
  }
}

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
