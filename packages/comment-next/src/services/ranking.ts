import type {
  CommentRankingItem,
  CommentRankingPeriod,
  CommentRankingResponse,
} from '../types/ranking';
import { resolveApiUrl } from './api';

export async function fetchCommentRanking(
  baseUrl: string,
  period: CommentRankingPeriod,
  limit: number,
  signal: AbortSignal
): Promise<CommentRankingItem[]> {
  const safeLimit = Math.min(Math.max(Math.round(Number(limit) || 10), 1), 50);
  const endpoint = resolveApiUrl(
    baseUrl,
    `/apis/api.commentnext.xhhao.com/v1alpha1/comments/ranking?period=${period}&limit=${safeLimit}`
  );
  const response = await fetch(endpoint, { signal });
  if (!response.ok) {
    throw new Error(`排行榜加载失败（${response.status}）`);
  }

  const result = (await response.json()) as CommentRankingResponse;
  return Array.isArray(result.items) ? result.items : [];
}
