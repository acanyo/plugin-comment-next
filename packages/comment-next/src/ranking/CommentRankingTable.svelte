<script lang="ts">
import CommentNextIcon from '../CommentNextIcon.svelte';
import type { CommentRankingItem } from '../types/ranking';
import { formatRelativeTime } from '../utils/date';
import CommentRankingAvatar from './CommentRankingAvatar.svelte';
import CommentRankingBadge from './CommentRankingBadge.svelte';
import {
  rankingAvatarUrl,
  rankingCommentCount,
  rankingDisplayName,
  rankingLatestComment,
  rankingUpvoteCount,
} from './presentation';

const {
  items,
  startRank = 4,
}: { items: CommentRankingItem[]; startRank?: number } = $props();
</script>

<div class="ranking-table" role="table" aria-label="其余读者排行榜">
  <div class="ranking-table__head" role="row">
    <span role="columnheader">排名</span>
    <span role="columnheader">读者</span>
    <span role="columnheader">评论数</span>
    <span role="columnheader">获赞数</span>
    <span role="columnheader">最近评论</span>
  </div>
  <div role="rowgroup">
    {#each items as item, index (`row:${item.kind ?? 'reader'}:${item.displayName ?? 'anonymous'}:${index}`)}
      <div class="ranking-table__row" role="row">
        <span class="ranking-table__rank" role="cell">{index + startRank}</span>
        <span class="ranking-table__reader" role="cell">
          <span class="ranking-table__avatar">
            <CommentRankingAvatar src={rankingAvatarUrl(item)} name={rankingDisplayName(item)} loading={index < 6 ? 'eager' : 'lazy'} />
          </span>
          <strong title={rankingDisplayName(item)}>{rankingDisplayName(item)}</strong>
          <span class="ranking-table__badge"><CommentRankingBadge rank={index + startRank} /></span>
        </span>
        <span class="ranking-table__count" role="cell" aria-label={`${rankingCommentCount(item)} 条评论`}>
          <CommentNextIcon name="message" size={15} />{rankingCommentCount(item)}
        </span>
        <span class="ranking-table__upvotes" role="cell" aria-label={`${rankingUpvoteCount(item)} 次获赞`}>
          <CommentNextIcon name="heartFill" size={17} />{rankingUpvoteCount(item)}
        </span>
        <span class="ranking-table__latest" role="cell">
          <span title={rankingLatestComment(item)}>{rankingLatestComment(item)}</span>
          <time datetime={item.latestCommentAt || undefined}>{formatRelativeTime(item.latestCommentAt)}</time>
        </span>
      </div>
    {/each}
  </div>
</div>

<style>
  .ranking-table {
    margin-top: 1.6rem;
  }
  .ranking-table__head,
  .ranking-table__row {
    display: grid;
    grid-template-columns: 2.6rem minmax(11rem, 1.4fr) 4.7rem 4.7rem minmax(10rem, 2fr);
    align-items: center;
    gap: 0.65rem;
    padding-inline: 0.9rem;
  }

  .ranking-table__head {
    min-height: 2.7rem;
    border-radius: 0.65rem 0.65rem 0 0;
    background: var(--ranking-control);
    color: var(--ranking-muted);
    font-size: 0.75rem;
    font-weight: 650;
  }

  .ranking-table__row {
    min-height: 4rem;
    border-bottom: 1px solid var(--ranking-line);
    font-size: 0.8rem;
  }

  .ranking-table__rank {
    font-weight: 650;
    font-variant-numeric: tabular-nums;
  }
  .ranking-table__reader {
    display: flex;
    min-width: 0;
    align-items: center;
    gap: 0.8rem;
  }
  .ranking-table__avatar {
    width: 2.5rem;
    height: 2.5rem;
    flex: none;
    border-radius: 50%;
  }
  .ranking-table__reader strong {
    min-width: 0;
    overflow: hidden;
    font-weight: 650;
    text-overflow: ellipsis;
    white-space: nowrap;
  }

  .ranking-table__badge {
    flex: none;
    margin-left: auto;
  }
  .ranking-table__count,
  .ranking-table__upvotes {
    display: flex;
    align-items: center;
    gap: 0.5rem;
    color: var(--ranking-muted);
    font-variant-numeric: tabular-nums;
  }

  .ranking-table__upvotes :global(svg) {
    color: var(--ranking-heart, #f45c7c);
  }
  .ranking-table__latest {
    display: grid;
    min-width: 0;
    grid-template-columns: minmax(0, 1fr) auto;
    align-items: center;
    gap: 0.8rem;
    color: var(--ranking-muted);
    font-size: 0.76rem;
  }

  .ranking-table__latest > span {
    overflow: hidden;
    text-overflow: ellipsis;
    white-space: nowrap;
  }
  time {
    color: var(--ranking-muted);
    font-size: 0.7rem;
    white-space: nowrap;
  }

  @container (max-width: 54rem) {
    .ranking-table__head,
    .ranking-table__row {
      grid-template-columns: 2rem minmax(7rem, 1.4fr) 3.8rem 3.8rem minmax(8rem, 1.5fr);
      gap: 0.55rem;
      padding-inline: 0.5rem;
    }

    .ranking-table__badge {
      display: none;
    }
    .ranking-table__reader {
      gap: 0.6rem;
    }
  }

  @container (max-width: 38rem) {
    .ranking-table__head {
      display: none;
    }
    .ranking-table__row {
      grid-template-columns: 1.5rem minmax(0, 1fr) auto auto;
      min-height: 4.5rem;
      column-gap: 0.6rem;
      row-gap: 0.25rem;
      padding: 0.8rem 0.2rem;
    }

    .ranking-table__avatar {
      width: 2rem;
      height: 2rem;
    }
    .ranking-table__latest {
      grid-column: 2 / -1;
      font-size: 0.72rem;
    }
    .ranking-table__count,
    .ranking-table__upvotes {
      gap: 0.25rem;
      font-size: 0.73rem;
    }
    time {
      display: none;
    }
  }
</style>
