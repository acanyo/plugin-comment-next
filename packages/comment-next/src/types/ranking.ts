import type { CommentNextBadge } from './comment';

export type CommentRankingPeriod =
  | 'all'
  | 'week'
  | 'month'
  | 'quarter'
  | 'year';

export interface CommentRankingItem {
  displayName?: string;
  avatar?: string;
  kind?: string;
  /** Total authored comments and replies in the selected period. */
  commentCount?: number;
  upvoteCount?: number;
  latestComment?: string;
  latestCommentAt?: string;
  levelBadge?: CommentNextBadge | null;
  website?: string | null;
  emailHash?: string | null;
  /** Authored replies included in commentCount. */
  repliesCount?: number;
}

export interface CommentRankingResponse {
  period?: CommentRankingPeriod;
  limit?: number;
  items?: CommentRankingItem[];
}

export const COMMENT_RANKING_PERIOD_LABELS: Record<
  CommentRankingPeriod,
  string
> = {
  all: '全部',
  week: '本周',
  month: '本月',
  quarter: '本季',
  year: '本年',
};

export function normalizeCommentRankingPeriod(
  value: unknown
): CommentRankingPeriod {
  return typeof value === 'string' &&
    Object.hasOwn(COMMENT_RANKING_PERIOD_LABELS, value)
    ? (value as CommentRankingPeriod)
    : 'all';
}
