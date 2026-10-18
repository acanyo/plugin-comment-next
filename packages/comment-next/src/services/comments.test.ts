// @vitest-environment jsdom

import { afterEach, describe, expect, it, vi } from 'vitest';
import { fetchCommentPage, fetchReplyPage } from './comments';

afterEach(() => vi.unstubAllGlobals());

describe('separate pinned comment response', () => {
  it('adapts pinned content and replies without mixing them into normal pagination', async () => {
    vi.stubGlobal(
      'fetch',
      vi.fn().mockResolvedValue(
        Response.json({
          page: 2,
          size: 2,
          total: 3,
          totalPages: 2,
          hasPrevious: true,
          hasNext: false,
          firstCommentName: 'pin',
          pinnedItems: [
            {
              metadata: { name: 'pin' },
              top: true,
              spec: { content: '<p>Pinned</p>' },
              replies: {
                total: 1,
                items: [
                  {
                    metadata: { name: 'reply' },
                    spec: { content: '<p>Reply</p>' },
                  },
                ],
              },
            },
          ],
          items: [
            {
              metadata: { name: 'normal' },
              spec: { content: '<p>Normal</p>' },
            },
          ],
        })
      )
    );

    const result = await fetchCommentPage({
      group: 'content.halo.run',
      kind: 'Post',
      name: 'post',
      version: 'v1alpha1',
      page: 2,
      size: 2,
    });

    expect(result.items.map((item) => item.id)).toEqual(['normal']);
    expect(result.pinnedItems?.map((item) => item.id)).toEqual(['pin']);
    expect(result.pinnedItems?.[0].replies?.[0].id).toBe('reply');
    expect(result).toMatchObject({
      page: 2,
      total: 3,
      totalPages: 2,
      firstCommentId: 'pin',
    });
  });

  it('keeps pinned replies in reply pagination', async () => {
    vi.stubGlobal(
      'fetch',
      vi.fn().mockResolvedValue(
        Response.json({
          total: 1,
          items: [{ metadata: { name: 'pinned-reply' }, top: true }],
        })
      )
    );

    const result = await fetchReplyPage({ commentName: 'comment' });
    expect(result.items[0].id).toBe('pinned-reply');
    expect(result.items[0].top).toBe(true);
  });
});
