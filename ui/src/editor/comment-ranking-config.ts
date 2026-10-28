export type CommentRankingPeriod =
  | 'all'
  | 'week'
  | 'month'
  | 'quarter'
  | 'year';

export interface CommentRankingBlockAttrs {
  period: CommentRankingPeriod;
  limit: number;
  title: string;
  subtitle: string;
  showHeader: boolean;
  accentColor: string;
  surfaceColor: string;
  textColor: string;
}

export interface CommentRankingSidebarBlockAttrs {
  period: 'month' | 'year';
  limit: number;
  title: string;
  rankingUrl: string;
  linkText: string;
  showHeader: boolean;
  accentColor: string;
  surfaceColor: string;
  textColor: string;
}

export const COMMENT_RANKING_PERIODS: CommentRankingPeriod[] = [
  'all',
  'week',
  'month',
  'quarter',
  'year',
];

export const DEFAULT_COMMENT_RANKING_ATTRS: CommentRankingBlockAttrs = {
  period: 'month',
  limit: 10,
  title: '评论读者墙',
  subtitle: '感谢每一位认真阅读、真诚评论的朋友',
  showHeader: true,
  accentColor: '',
  surfaceColor: '',
  textColor: '',
};

export const DEFAULT_COMMENT_RANKING_SIDEBAR_ATTRS: CommentRankingSidebarBlockAttrs =
  {
    period: 'month',
    limit: 5,
    title: '评论读者墙',
    rankingUrl: '',
    linkText: '查看完整榜单',
    showHeader: true,
    accentColor: '',
    surfaceColor: '',
    textColor: '',
  };

export function normalizeRankingPeriod(value: unknown): CommentRankingPeriod {
  return COMMENT_RANKING_PERIODS.includes(value as CommentRankingPeriod)
    ? (value as CommentRankingPeriod)
    : DEFAULT_COMMENT_RANKING_ATTRS.period;
}

export function normalizeRankingSidebarPeriod(
  value: unknown
): 'month' | 'year' {
  return value === 'year' ? 'year' : 'month';
}

export function normalizeRankingLimit(value: unknown): number {
  const parsed = Number(value);
  return Number.isFinite(parsed) && parsed > 0
    ? Math.min(Math.max(Math.round(parsed), 1), 50)
    : DEFAULT_COMMENT_RANKING_ATTRS.limit;
}

export function normalizeRankingColor(
  value: unknown,
  fallback: string
): string {
  const color = String(value || '').trim();
  return /^#(?:[\da-f]{3}|[\da-f]{6}|[\da-f]{8})$/i.test(color)
    ? color
    : fallback;
}

export function parseShowHeader(value: string | null): boolean {
  return value === null
    ? DEFAULT_COMMENT_RANKING_ATTRS.showHeader
    : value !== 'false';
}

export function normalizeRankingSidebarLimit(value: unknown): number {
  const parsed = Number(value);
  return Number.isFinite(parsed) && parsed > 0
    ? Math.min(Math.max(Math.round(parsed), 1), 10)
    : DEFAULT_COMMENT_RANKING_SIDEBAR_ATTRS.limit;
}
