import type {
  CommentNextAuthor,
  CommentNextBadge,
  CommentNextBadgeConfig,
  CommentNextBadgeIdentifier,
  CommentNextComment,
} from "../types/comment";

const DEFAULT_FIRST_COMMENT_BADGE: CommentNextBadge = {
  id: "first-comment",
  label: "首评",
  tone: "first",
  icon: "medal",
  title: "本文第一位评论者",
};

const DEFAULT_ADMIN_BADGE: CommentNextBadge = {
  id: "administrator",
  label: "站长",
  tone: "admin",
  icon: "shield",
  title: "站点管理员",
};

export function resolveCommentBadges(
  comment: CommentNextComment,
  {
    isFirstComment = false,
    config = {},
  }: {
    isFirstComment?: boolean;
    config?: CommentNextBadgeConfig;
  } = {}
): CommentNextBadge[] {
  const badges: CommentNextBadge[] = [];

  if (isFirstComment && config.enableFirstCommentBadge !== false) {
    badges.push({ ...DEFAULT_FIRST_COMMENT_BADGE, ...config.firstCommentBadge });
  }

  if (isAdminAuthor(comment.author, config.adminIdentifiers)) {
    badges.push({ ...DEFAULT_ADMIN_BADGE, ...config.adminBadge });
  }

  const levelBadge = resolveLevelBadge(comment.author, config);
  if (levelBadge) {
    badges.push(levelBadge);
  }

  for (const customRule of config.customRules ?? []) {
    if (matchesAuthor(comment.author, customRule.match)) {
      badges.push({
        id: customRule.id,
        label: customRule.label,
        tone: customRule.tone ?? "custom",
        icon: customRule.icon ?? "star",
        title: customRule.title,
      });
    }
  }

  badges.push(...(comment.author.badges ?? []));

  return dedupeBadges(badges);
}

function resolveLevelBadge(author: CommentNextAuthor, config: CommentNextBadgeConfig): CommentNextBadge | undefined {
  const commentCount = author.activeCommentCount ?? 0;
  const matchedRule = [...(config.levelRules ?? [])]
    .sort((left, right) => right.minComments - left.minComments)
    .find((rule) => commentCount >= rule.minComments);

  if (!matchedRule) {
    return undefined;
  }

  return {
    id: matchedRule.id,
    label: matchedRule.label,
    tone: matchedRule.tone ?? "level",
    icon: matchedRule.icon ?? "sparkle",
    title: matchedRule.title ?? `累计 ${commentCount} 条活跃评论`,
  };
}

function isAdminAuthor(author: CommentNextAuthor, adminIdentifiers: CommentNextBadgeIdentifier[] = []): boolean {
  if (author.role === "admin") {
    return true;
  }

  return adminIdentifiers.some((identifier) => matchesAuthor(author, identifier));
}

function matchesAuthor(author: CommentNextAuthor, identifier: CommentNextBadgeIdentifier): boolean {
  return (
    equalsIdentity(author.username, identifier.username) ||
    equalsIdentity(author.email, identifier.email) ||
    equalsIdentity(author.displayName, identifier.displayName)
  );
}

function equalsIdentity(left?: string, right?: string): boolean {
  if (!left || !right) {
    return false;
  }

  return left.trim().toLowerCase() === right.trim().toLowerCase();
}

function dedupeBadges(badges: CommentNextBadge[]): CommentNextBadge[] {
  const visited = new Set<string>();

  return badges.filter((badge) => {
    if (visited.has(badge.id)) {
      return false;
    }

    visited.add(badge.id);
    return true;
  });
}
