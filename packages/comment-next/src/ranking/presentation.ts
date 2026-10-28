import { getDefaultAnonymousAvatarUrl } from '../avatar/weavatar';
import type { CommentRankingItem } from '../types/ranking';

export function rankingDisplayName(item: CommentRankingItem): string {
  return item.displayName?.trim() || '匿名读者';
}

export function rankingAvatarUrl(item: CommentRankingItem): string {
  return item.avatar?.trim() || getDefaultAnonymousAvatarUrl();
}

export function rankingCommentCount(item: CommentRankingItem): number {
  return Math.max(0, Number(item.commentCount) || 0);
}

export function rankingUpvoteCount(item: CommentRankingItem): number {
  return Math.max(0, Number(item.upvoteCount) || 0);
}

export function rankingReaderBadge(rank: number): {
  label: string;
  tone: string;
} {
  if (rank === 1) return { label: '超活跃读者', tone: 'gold' };
  if (rank === 2) return { label: '深度读者', tone: 'violet' };
  if (rank === 3) return { label: '忠实读者', tone: 'blue' };
  if (rank <= 5) return { label: '活跃读者', tone: 'green' };
  if (rank <= 8) return { label: '热心读者', tone: 'blue' };
  return { label: '潜力读者', tone: 'neutral' };
}

export function rankingLatestComment(item: CommentRankingItem): string {
  return item.latestComment?.trim() || '暂无评论摘要';
}

export function safeRankingUrl(value: string): string {
  const url = value.trim();
  // Support theme routes and http(s) links, never script or data URLs.
  if (/^\/(?!\/)/.test(url) || /^#[^\s]+$/.test(url)) return url;
  try {
    const parsed = new URL(url);
    return ['http:', 'https:'].includes(parsed.protocol) ? url : '';
  } catch {
    return '';
  }
}

export function rankingColorStyle(
  accentColor: string,
  surfaceColor: string,
  textColor: string
): string {
  return [
    colorVariable('--comment-ranking-accent-color', accentColor),
    colorVariable('--comment-ranking-surface-color', surfaceColor),
    colorVariable('--comment-ranking-text-color', textColor),
  ]
    .filter(Boolean)
    .join(';');
}

function colorVariable(name: string, value: string): string {
  const color = value.trim();
  return /^#(?:[\da-f]{3}|[\da-f]{6}|[\da-f]{8})$/i.test(color)
    ? `${name}:${color}`
    : '';
}
