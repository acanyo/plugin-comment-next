import { mount, unmount } from 'svelte';
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';
import CommentNextCommentList from './CommentNextCommentList.svelte';
import { fetchCommentPage, fetchReplyPage } from './services/comments';
import type {
  CommentNextComment,
  CommentNextCommentPage,
} from './types/comment';
import {
  getCommentAnchorHref,
  getCommentAnchorId,
} from './utils/comment-anchor';

vi.mock('./services/comments', async (importOriginal) => ({
  ...(await importOriginal<typeof import('./services/comments')>()),
  fetchCommentPage: vi.fn(),
  fetchReplyPage: vi.fn(),
}));

let component: ReturnType<typeof mount> | undefined;
const locate = vi.fn<(url: URL, options?: RequestInit) => Promise<Response>>();

beforeEach(() => {
  vi.resetAllMocks();
  vi.stubGlobal('fetch', locate);
  vi.stubGlobal('matchMedia', () => ({
    matches: true,
    addEventListener() {},
    removeEventListener() {},
  }));
  HTMLElement.prototype.scrollIntoView = vi.fn();
});

afterEach(async () => {
  if (component) await unmount(component);
  component = undefined;
  window.history.replaceState(null, '', window.location.pathname);
  vi.unstubAllGlobals();
});

function render(hash: string) {
  window.history.replaceState(null, '', hash);
  component = mount(CommentNextCommentList, {
    target: document.body,
    props: {
      group: 'content.halo.run',
      kind: 'Post',
      name: 'article',
      withReplies: false,
      pageSize: 2,
    },
  });
}

function item(id: string): CommentNextComment {
  return { id, content: id, author: { displayName: '读者' } };
}

function page(
  number: number,
  items: CommentNextComment[]
): CommentNextCommentPage {
  return {
    page: number,
    size: 2,
    total: 8,
    totalPages: 4,
    hasNext: number < 4,
    hasPrevious: number > 1,
    items,
  };
}

describe('comment links', () => {
  it('opens a later page with a pinned comment in its regular list position', async () => {
    locate.mockResolvedValueOnce(
      Response.json({ commentName: 'old-comment', page: 3, replyPage: null })
    );
    vi.mocked(fetchCommentPage).mockResolvedValueOnce(
      page(3, [item('previous-comment'), { ...item('old-comment'), top: true }])
    );

    render(getCommentAnchorHref('comment', 'old-comment'));

    await vi.waitFor(() =>
      expect(
        document
          .getElementById(getCommentAnchorId('comment', 'old-comment'))
          ?.hasAttribute('data-comment-next-highlight')
      ).toBe(true)
    );
    expect(
      document.querySelector('[aria-current="page"]')?.textContent?.trim()
    ).toBe('3');
    expect(fetchCommentPage).toHaveBeenCalledExactlyOnceWith(
      expect.objectContaining({ page: 3, separatePinned: false })
    );
    expect(locate.mock.calls[0][0].searchParams.get('separatePinned')).toBe(
      'false'
    );
    expect(
      document.querySelector('.comment-next-comments-list')?.textContent
    ).toContain('置顶');
    expect(
      Array.from(
        document.querySelectorAll('.comment-next-comments-list > article'),
        (node) => node.id
      )
    ).toEqual([
      getCommentAnchorId('comment', 'previous-comment'),
      getCommentAnchorId('comment', 'old-comment'),
    ]);
  });

  it('shows the linked reply page with reply preloading disabled', async () => {
    locate.mockResolvedValueOnce(
      Response.json({ commentName: 'parent', page: 2, replyPage: 4 })
    );
    vi.mocked(fetchCommentPage).mockResolvedValueOnce(
      page(2, [item('parent')])
    );
    vi.mocked(fetchReplyPage).mockResolvedValueOnce(page(4, [item('nested')]));

    render(getCommentAnchorHref('reply', 'nested'));

    await vi.waitFor(() =>
      expect(
        document
          .getElementById(getCommentAnchorId('reply', 'nested'))
          ?.hasAttribute('data-comment-next-highlight')
      ).toBe(true)
    );
    expect(fetchReplyPage).toHaveBeenCalledExactlyOnceWith(
      expect.objectContaining({ commentName: 'parent', page: 4 })
    );
    expect(document.body.textContent).toContain('展开剩余 7 条回复');
  });

  it('loads ordinary comments when a link target is unavailable', async () => {
    locate.mockResolvedValueOnce(new Response(null, { status: 404 }));
    vi.mocked(fetchCommentPage).mockResolvedValueOnce(
      page(1, [item('public-comment')])
    );

    render(getCommentAnchorHref('comment', 'missing'));

    await vi.waitFor(() =>
      expect(document.body.textContent).toContain('无法定位这条评论或回复')
    );
    expect(
      document.getElementById(getCommentAnchorId('comment', 'public-comment'))
    ).not.toBeNull();
  });

  it('keeps the latest hash target when a previous request finishes later', async () => {
    let completeFirst!: (response: Response) => void;
    locate
      .mockImplementationOnce(
        () =>
          new Promise((resolve) => {
            completeFirst = resolve;
          })
      )
      .mockResolvedValueOnce(
        Response.json({ commentName: 'new-link', page: 4, replyPage: null })
      );
    vi.mocked(fetchCommentPage).mockImplementation(async (request) =>
      request.page === 4
        ? page(4, [item('new-link')])
        : page(2, [item('slow-link')])
    );
    render(getCommentAnchorHref('comment', 'slow-link'));
    await vi.waitFor(() => expect(locate).toHaveBeenCalledOnce());

    window.history.replaceState(
      null,
      '',
      getCommentAnchorHref('comment', 'new-link')
    );
    window.dispatchEvent(new HashChangeEvent('hashchange'));
    await vi.waitFor(() =>
      expect(
        document.getElementById(getCommentAnchorId('comment', 'new-link'))
      ).not.toBeNull()
    );
    completeFirst(
      Response.json({ commentName: 'slow-link', page: 2, replyPage: null })
    );
    expect(locate.mock.calls[0][1]?.signal?.aborted).toBe(true);
    await vi.waitFor(() =>
      expect(
        document.querySelector('[aria-current="page"]')?.textContent?.trim()
      ).toBe('4')
    );
    expect(
      document.getElementById(getCommentAnchorId('comment', 'slow-link'))
    ).toBeNull();
  });
});
