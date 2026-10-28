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
  commentCount?: number;
  upvoteCount?: number;
  latestComment?: string;
  latestCommentAt?: string;
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
