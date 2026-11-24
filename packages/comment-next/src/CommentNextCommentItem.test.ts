import { render } from 'svelte/server';
import { describe, expect, it } from 'vitest';
import CommentNextCommentItem from './CommentNextCommentItem.svelte';
import type { CommentNextComment } from './types/comment';

describe('CommentNextCommentItem reply pagination', () => {
  it('shows how many replies remain beyond the initial page', () => {
    const { body } = render(CommentNextCommentItem, {
      props: {
        comment: commentWithFiveOfSevenReplies(),
        replySize: 5,
      },
    });

    expect(body).toContain('展开剩余 2 条回复');
  });
});

it('shows saved locations on comments and replies that have no timestamp', () => {
  const { body } = render(CommentNextCommentItem, {
    props: {
      comment: {
        id: 'located-comment',
        content: '<p>Comment</p>',
        author: { displayName: 'Commenter' },
        ipLocation: '广东',
        replies: [
          {
            id: 'located-reply',
            content: '<p>Reply</p>',
            author: { displayName: 'Replier' },
            ipLocation: '日本',
          },
        ],
      },
      showCommenterDevice: false,
    },
  });

  expect(body).toContain('title="IP 属地：广东"');
  expect(body).toContain('title="IP 属地：日本"');
  expect(body).not.toContain('<time');
});

function commentWithFiveOfSevenReplies(): CommentNextComment {
  return {
    id: 'comment-1',
    content: '<p>Comment</p>',
    author: { displayName: 'Commenter' },
    stats: { replies: 7 },
    replyPage: {
      page: 1,
      size: 5,
      total: 7,
      totalPages: 2,
      hasNext: true,
      hasPrevious: false,
    },
    replies: Array.from({ length: 5 }, (_, index) => ({
      id: `reply-${index + 1}`,
      content: `<p>reply-${index + 1}</p>`,
      author: { displayName: `User ${index + 1}` },
    })),
  };
}
