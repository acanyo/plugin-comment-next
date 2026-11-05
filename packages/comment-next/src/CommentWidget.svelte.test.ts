import { flushSync, tick } from 'svelte';
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';
import './CommentNext.svelte';
import './CommentWidget.svelte';
import {
  createComment,
  createReply,
  fetchCommentPage,
} from './services/comments';
import { fetchPluginConfig } from './services/config';
import type {
  CommentNextCreatedEventDetail,
  CommentNextReplyTarget,
} from './types/composer';

vi.mock('./services/current-user', () => ({
  fetchCurrentUser: vi.fn(async () => ({
    name: 'reader',
    displayName: '读者',
  })),
}));

vi.mock('./services/config', () => ({
  fetchPluginConfig: vi.fn(async () => ({
    basic: { enableSnippetComment: false },
    reaction: { enabled: false },
  })),
  fetchGlobalInfo: vi.fn(async () => ({ allowAnonymousComments: true })),
}));

vi.mock('./services/emotes', () => ({
  fetchEmotePacks: vi.fn(async () => ({})),
}));

vi.mock('./services/comments', async (importOriginal) => ({
  ...(await importOriginal<typeof import('./services/comments')>()),
  createComment: vi.fn(),
  createReply: vi.fn(),
  fetchCommentPage: vi.fn(async () => ({
    items: [],
    page: 1,
    size: 20,
    total: 0,
    totalPages: 0,
    hasNext: false,
    hasPrevious: false,
  })),
}));

type Widget = HTMLElement & {
  showComments: boolean | string;
  editorOnly: boolean;
  showSubmitArea: boolean;
  showAccountFields: boolean;
  replyTarget?: CommentNextReplyTarget;
  replyTo: (target: CommentNextReplyTarget) => boolean;
  cancelReply: () => boolean;
  setFocus: () => void;
  reset: () => void;
};

beforeEach(() => {
  vi.clearAllMocks();
  localStorage.clear();
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
  vi.mocked(createComment).mockResolvedValue({
    id: 'new-comment',
    content: '新评论',
    approved: true,
    author: { displayName: '读者' },
  });
  vi.mocked(createReply).mockResolvedValue({
    id: 'new-reply',
    content: '新回复',
    approved: false,
    author: { displayName: '读者' },
  });
});

afterEach(async () => {
  document.body.replaceChildren();
  await tick();
  vi.restoreAllMocks();
  vi.unstubAllGlobals();
});

async function widget(
  tag = 'comment-widget',
  attributes: Record<string, string> = {}
) {
  const element = document.createElement(tag) as Widget;
  element.setAttribute('group', 'content.halo.run');
  element.setAttribute('kind', 'Post');
  element.setAttribute('name', 'post-1');
  element.setAttribute('show-comments', 'false');
  for (const [name, value] of Object.entries(attributes))
    element.setAttribute(name, value);
  document.body.append(element);
  await vi.waitFor(() => expect(editor(element)).not.toBeNull());
  return element;
}

function editor(element: Widget) {
  return element.shadowRoot?.querySelector<HTMLDivElement>(
    '[contenteditable="true"]'
  );
}

async function write(element: Widget, content: string) {
  const input = editor(element);
  if (!input) throw new Error('Editor is missing');
  input.textContent = content;
  input.dispatchEvent(new InputEvent('input', { bubbles: true }));
  await tick();
}

function send(element: Widget) {
  element.shadowRoot
    ?.querySelector('form')
    ?.dispatchEvent(
      new SubmitEvent('submit', { bubbles: true, cancelable: true })
    );
}

describe.each(['comment-widget', 'comment-next'])(
  '%s standalone composer',
  (tag) => {
    it('hides the list with a string attribute and allows switching it back on', async () => {
      const element = await widget(tag);
      expect(fetchCommentPage).not.toHaveBeenCalled();
      element.showComments = true;
      await vi.waitFor(() => expect(fetchCommentPage).toHaveBeenCalledOnce());
      element.showComments = false;
      await tick();
      expect(
        element.shadowRoot?.querySelector('.comment-next-comments')
      ).toBeNull();
      element.removeAttribute('show-comments');
      await vi.waitFor(() => expect(fetchCommentPage).toHaveBeenCalledTimes(2));
    });

    it('keeps the draft on reply and cancel, and submits a nested reply with a local event', async () => {
      const element = await widget(tag);
      const created = vi.fn();
      element.addEventListener('created', created);
      await write(element, '保留草稿');
      expect(
        element.replyTo({ commentName: 'parent', displayName: '主评论者' })
      ).toBe(true);
      await tick();
      expect(element.shadowRoot?.textContent).toContain('@主评论者');
      expect(editor(element)?.textContent).toBe('保留草稿');
      expect(element.cancelReply()).toBe(true);
      await tick();
      expect(editor(element)?.textContent).toBe('保留草稿');
      expect(element.replyTarget).toBeUndefined();

      element.replyTarget = {
        commentName: 'parent',
        replyName: 'quoted-reply',
        displayName: '被回复者',
      };
      await tick();
      send(element);
      await vi.waitFor(() => expect(created).toHaveBeenCalledOnce());
      expect(createComment).not.toHaveBeenCalled();
      expect(createReply).toHaveBeenCalledWith(
        expect.objectContaining({
          commentName: 'parent',
          quoteReply: 'quoted-reply',
          content: '保留草稿',
        })
      );
      const event = created.mock
        .calls[0][0] as CustomEvent<CommentNextCreatedEventDetail>;
      expect(event.target).toBe(element);
      expect(event.bubbles).toBe(true);
      expect(event.composed).toBe(true);
      expect(event.detail).toMatchObject({
        type: 'reply',
        commentName: 'parent',
        subject: {
          group: 'content.halo.run',
          kind: 'Post',
          name: 'post-1',
          version: 'v1alpha1',
        },
        replyTarget: { replyName: 'quoted-reply' },
        result: { id: 'new-reply', approved: false },
      });
      await tick();
      expect(editor(element)?.textContent).toBe('');
      expect(element.replyTarget).toBeUndefined();
      expect(element.shadowRoot?.textContent).toContain(
        '回复提交成功，审核后可见'
      );
      expect(fetchCommentPage).not.toHaveBeenCalled();
    });

    it('focuses and resets the normal composer, then publishes a main comment', async () => {
      const element = await widget(tag);
      element.setFocus();
      expect(element.shadowRoot?.activeElement).toBe(editor(element));
      await write(element, '旧草稿');
      element.reset();
      await tick();
      expect(editor(element)?.textContent).toBe('');
      await write(element, '新的主评论');
      const created = vi.fn();
      element.addEventListener('created', created);
      send(element);
      await vi.waitFor(() => expect(created).toHaveBeenCalledOnce());
      expect(createReply).not.toHaveBeenCalled();
      expect(createComment).toHaveBeenCalledWith(
        expect.objectContaining({
          group: 'content.halo.run',
          kind: 'Post',
          name: 'post-1',
          content: '新的主评论',
        })
      );
      expect(created.mock.calls[0][0].detail).toMatchObject({
        type: 'comment',
        commentName: 'new-comment',
      });
    });

    it('accepts an initial JSON reply target and keeps it with the draft after a failed request', async () => {
      vi.mocked(createReply).mockRejectedValueOnce(
        new Error('网络错误，请重试')
      );
      const element = await widget(tag, {
        'reply-target': JSON.stringify({
          commentName: 'initial-parent',
          displayName: '目标读者',
        }),
      });
      await write(element, '待重试回复');
      send(element);
      await vi.waitFor(() =>
        expect(element.shadowRoot?.textContent).toContain('网络错误，请重试')
      );
      expect(editor(element)?.textContent).toBe('待重试回复');
      expect(element.replyTarget?.commentName).toBe('initial-parent');
      expect(element.shadowRoot?.textContent).toContain('@目标读者');
    });

    it('rejects incomplete reply targets instead of silently creating a main comment', async () => {
      const element = await widget(tag);
      expect(element.replyTo({ commentName: ' ' })).toBe(false);
      element.replyTarget = { commentName: '' };
      await tick();
      await write(element, '不能发错位置');
      send(element);
      await tick();
      expect(createComment).not.toHaveBeenCalled();
      expect(createReply).not.toHaveBeenCalled();
      expect(element.shadowRoot?.textContent).toContain(
        '回复目标缺少主评论标识'
      );
    });
  }
);

it('does not change targets during submission and does not affect another composer', async () => {
  const first = await widget();
  const second = await widget();
  const created = vi.fn();
  second.addEventListener('created', created);
  let resolveReply!: (value: Awaited<ReturnType<typeof createReply>>) => void;
  vi.mocked(createReply).mockImplementationOnce(
    () =>
      new Promise((resolve) => {
        resolveReply = resolve;
      })
  );
  first.replyTo({ commentName: 'original-parent' });
  await tick();
  await write(first, '目标不能切换');
  send(first);
  await vi.waitFor(() => expect(createReply).toHaveBeenCalledOnce());
  expect(first.replyTo({ commentName: 'wrong-parent' })).toBe(false);
  expect(first.cancelReply()).toBe(false);
  expect(second.replyTarget).toBeUndefined();
  expect(editor(second)?.textContent).toBe('');
  resolveReply({
    id: 'new-reply',
    content: '回复',
    approved: true,
    author: { displayName: '读者' },
  });
  await vi.waitFor(() => expect(editor(first)?.textContent).toBe(''));
  expect(created).not.toHaveBeenCalled();
});

it('keeps the existing editorOnly update mode without submitting', async () => {
  const element = await widget('comment-next', { 'editor-only': '' });
  element.showSubmitArea = false;
  element.showAccountFields = false;
  await tick();
  const update = vi.fn();
  element.addEventListener('update', update);
  await write(element, '后台编辑内容');
  expect(update).toHaveBeenCalled();
  expect(update.mock.calls.at(-1)?.[0].detail.content).toBe('后台编辑内容');
  expect(element.replyTo({ commentName: 'parent' })).toBe(false);
  expect(createComment).not.toHaveBeenCalled();
  element.reset();
  await tick();
  expect(editor(element)?.textContent).toBe('');
});

it('does not render reactions or load snippet comments when the list is hidden', async () => {
  vi.mocked(fetchPluginConfig).mockResolvedValueOnce({
    basic: { enableSnippetComment: true },
    reaction: { enabled: true, subjectEnabled: true },
  });
  const element = await widget();
  flushSync();
  expect(element.shadowRoot?.querySelector('form')).not.toBeNull();
  expect(
    element.shadowRoot?.querySelector('.comment-next-reaction-bar')
  ).toBeNull();
  expect(fetchCommentPage).not.toHaveBeenCalled();
});
