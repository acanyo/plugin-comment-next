import type { CommentNextBadgeConfig, CommentNextComment, CommentNextCommentPage } from "../types/comment";

export const demoBadgeConfig: CommentNextBadgeConfig = {
  levelRules: [
    {
      id: "level-regular",
      label: "常驻",
      minComments: 8,
      icon: "sparkle",
      title: "累计评论达到 8 条",
    },
    {
      id: "level-core",
      label: "核心读者",
      minComments: 30,
      icon: "crown",
      title: "累计评论达到 30 条",
    },
  ],
  adminIdentifiers: [{ username: "handsome" }],
  customRules: [
    {
      id: "special-builder",
      label: "共建者",
      match: { email: "builder@example.com" },
      icon: "star",
      title: "站点手动授予的专属徽章",
    },
  ],
};

export function createDemoCommentPage(): CommentNextCommentPage {
  const items: CommentNextComment[] = [
    {
      id: "demo-comment-1",
      content: "<p>这版评论框的方向很清楚，富文本和 AI 辅助如果能保持轻量，会比传统 Markdown 评论舒服很多。</p>",
      creationTime: new Date(Date.now() - 1000 * 60 * 18).toISOString(),
      approved: true,
      author: {
        displayName: "青栀",
        avatar: "https://weavatar.com/avatar/80b7f6b7bffdd6f334f8988b5d47514de64cd0930d4ff7dfd6c71e1db7a81f23?s=96&d=mp",
        website: "https://www.xhhao.com",
        email: "reader@example.com",
        activeCommentCount: 12,
      },
      stats: {
        upvotes: 12,
        replies: 2,
      },
      replies: [
        {
          id: "demo-reply-1",
          content: "<p>是的，所以后续功能我也会优先保证评论输入不被复杂工具打断。</p>",
          creationTime: new Date(Date.now() - 1000 * 60 * 8).toISOString(),
          approved: true,
          author: {
            displayName: "Handsome",
            username: "handsome",
            role: "admin",
            activeCommentCount: 86,
          },
          stats: {
            upvotes: 3,
          },
        },
        {
          id: "demo-reply-2",
          content: "<p>首评徽章这个点挺有记忆点，适合保留。</p>",
          creationTime: new Date(Date.now() - 1000 * 60 * 3).toISOString(),
          approved: true,
          author: {
            displayName: "Ming",
            email: "builder@example.com",
            activeCommentCount: 31,
          },
          stats: {
            upvotes: 1,
          },
        },
      ],
    },
    {
      id: "demo-comment-2",
      content: "<p>等级徽章建议不要太花，最好能让管理员在插件设置里按评论数、邮箱、用户名分别配置。</p>",
      creationTime: new Date(Date.now() - 1000 * 60 * 55).toISOString(),
      approved: true,
      author: {
        displayName: "Ming",
        email: "builder@example.com",
        activeCommentCount: 31,
      },
      stats: {
        upvotes: 7,
        replies: 0,
      },
    },
  ];

  return {
    page: 1,
    size: 20,
    total: items.length,
    totalPages: 1,
    hasNext: false,
    hasPrevious: false,
    items,
  };
}
