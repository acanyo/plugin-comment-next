import { fetchCommentRanking } from '../services/ranking';
import type {
  CommentRankingItem,
  CommentRankingPeriod,
} from '../types/ranking';

interface RankingQuery {
  baseUrl: string;
  period: CommentRankingPeriod;
  limit: number;
}

/** Each component owns its query and cancels requests when its inputs change. */
export function createRankingState(query: () => RankingQuery) {
  let items = $state<CommentRankingItem[]>([]);
  let loading = $state(true);
  let error = $state('');

  $effect(() => {
    const { baseUrl, period, limit } = query();
    const controller = new AbortController();
    loading = true;
    error = '';

    void fetchCommentRanking(baseUrl, period, limit, controller.signal)
      .then((result) => {
        if (!controller.signal.aborted) items = result;
      })
      .catch((fetchError: unknown) => {
        if (controller.signal.aborted) return;
        error =
          fetchError instanceof Error ? fetchError.message : '排行榜加载失败';
        items = [];
      })
      .finally(() => {
        if (!controller.signal.aborted) loading = false;
      });

    return () => controller.abort();
  });

  return {
    get items() {
      return items;
    },
    get loading() {
      return loading;
    },
    get error() {
      return error;
    },
  };
}
