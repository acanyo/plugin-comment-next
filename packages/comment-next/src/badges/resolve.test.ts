import { describe, expect, it } from 'vitest';
import type { CommentNextBadge, CommentNextComment } from '../types/comment';
import type { CommentNextAuthorIdentity } from '../types/interaction-plus';
import { resolveCommentBadges } from './resolve';

const assignedBadges: CommentNextBadge[] = [
  { id: 'supporter', label: '共建者', tone: 'custom' },
  { id: 'regular', label: '常驻', tone: 'level' },
  { id: 'reader', label: '读者', tone: 'muted' },
];

function commentWithIdentity(
  identity?: CommentNextAuthorIdentity
): CommentNextComment {
  return {
    id: 'comment',
    content: '<p>评论</p>',
    author: {
      displayName: '评论者',
      role: 'admin',
      badges: assignedBadges,
      identity,
    },
  };
}

describe('comment badges alongside interaction-plus decorations', () => {
  it.each([
    ['无互动增强身份', undefined],
    ['未佩戴装扮', { userName: 'reader' }],
    [
      '头像框',
      { userName: 'reader', decorations: { avatarFrame: { id: 'frame' } } },
    ],
    [
      '昵称颜色',
      { userName: 'reader', decorations: { nameStyle: { id: 'style' } } },
    ],
    [
      '卡片背景',
      {
        userName: 'reader',
        decorations: { cardBackground: { id: 'background' } },
      },
    ],
    ['头衔', { userName: 'reader', decorations: { title: { id: 'title' } } }],
    [
      '主徽章',
      { userName: 'reader', decorations: { primaryBadge: { id: 'primary' } } },
    ],
    [
      '徽章展柜',
      {
        userName: 'reader',
        decorations: { badgeShowcase: [{ id: 'showcase' }] },
      },
    ],
    ['身份标识', { userName: 'reader', identityMarks: [{ id: 'mark' }] }],
  ] satisfies [string, CommentNextAuthorIdentity | undefined][])(
    '%s does not suppress comment badges',
    (_label, identity) => {
      const comment = commentWithIdentity(identity);
      const snapshot = structuredClone(comment);
      const badges = resolveCommentBadges(comment, { isFirstComment: true });

      expect(badges.map((badge) => badge.id)).toEqual([
        'first-comment',
        'administrator',
        ...assignedBadges.map((badge) => badge.id),
      ]);
      expect(comment).toEqual(snapshot);
    }
  );

  it('keeps configured badges and deduplicates their IDs with decorations present', () => {
    const comment = commentWithIdentity({
      userName: 'reader',
      decorations: { avatarFrame: { id: 'frame' } },
    });
    comment.author.badges = [
      ...assignedBadges,
      { id: 'administrator', label: '重复站长', tone: 'admin' },
      assignedBadges[0],
    ];

    const badges = resolveCommentBadges(comment, {
      isFirstComment: true,
      config: {
        enableFirstCommentBadge: false,
        adminBadge: { label: '管理员', color: '#123456' },
      },
    });

    expect(badges).toEqual([
      expect.objectContaining({
        id: 'administrator',
        label: '管理员',
        color: '#123456',
      }),
      ...assignedBadges,
    ]);
  });

  it('does not add an administrator badge for decorated members', () => {
    const comment = commentWithIdentity({
      userName: 'reader',
      decorations: { nameStyle: { id: 'style' } },
    });
    comment.author.role = 'member';

    expect(resolveCommentBadges(comment)).toEqual(assignedBadges);
  });
});
