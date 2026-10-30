<script lang="ts">
import CommentNextIcon from '../CommentNextIcon.svelte';
import type { CommentRankingItem } from '../types/ranking';
import CommentRankingAvatar from './CommentRankingAvatar.svelte';
import CommentRankingBadge from './CommentRankingBadge.svelte';
import {
  rankingAvatarUrl,
  rankingCommentCount,
  rankingDisplayName,
  rankingLatestComment,
  rankingUpvoteCount,
} from './presentation';

const { items }: { items: CommentRankingItem[] } = $props();
</script>

<ol class="ranking-podium" class:ranking-podium--single={items.length === 1} aria-label="前三名读者">
  {#each items as item, index (`leader:${item.kind ?? 'reader'}:${item.displayName ?? 'anonymous'}:${index}`)}
    <li class="ranking-podium__reader" data-rank={index + 1} aria-label={`第 ${index + 1} 名：${rankingDisplayName(item)}`}>
      <div class="ranking-podium__portrait">
        <span class="ranking-podium__crown" aria-hidden="true">
          <svg viewBox="0 0 72 56" fill="currentColor">
            <path d="M10 18 24 30 36 9 48 30 62 18 56 49H16Z" />
            <circle cx="10" cy="16" r="5" />
            <circle cx="36" cy="8" r="5" />
            <circle cx="62" cy="16" r="5" />
          </svg>
          <b>{index + 1}</b>
        </span>
        {#if index === 0}
          <svg class="ranking-podium__laurels" viewBox="0 0 220 130" fill="currentColor" aria-hidden="true">
            <g>
              <path d="M42 116C19 97 12 77 15 51" fill="none" stroke="currentColor" stroke-width="2" />
              <ellipse cx="36" cy="108" rx="4" ry="8" transform="rotate(-52 36 108)" />
              <ellipse cx="21" cy="99" rx="4" ry="8" transform="rotate(-45 21 99)" />
              <ellipse cx="29" cy="94" rx="4" ry="8" transform="rotate(28 29 94)" />
              <ellipse cx="12" cy="84" rx="4" ry="8" transform="rotate(-28 12 84)" />
              <ellipse cx="24" cy="78" rx="4" ry="8" transform="rotate(28 24 78)" />
              <ellipse cx="9" cy="68" rx="3.5" ry="7" transform="rotate(-16 9 68)" />
              <ellipse cx="21" cy="62" rx="3.5" ry="7" transform="rotate(28 21 62)" />
              <ellipse cx="16" cy="48" rx="3.5" ry="7" transform="rotate(12 16 48)" />
            </g>
            <g transform="translate(220 0) scale(-1 1)">
              <path d="M42 116C19 97 12 77 15 51" fill="none" stroke="currentColor" stroke-width="2" />
              <ellipse cx="36" cy="108" rx="4" ry="8" transform="rotate(-52 36 108)" />
              <ellipse cx="21" cy="99" rx="4" ry="8" transform="rotate(-45 21 99)" />
              <ellipse cx="29" cy="94" rx="4" ry="8" transform="rotate(28 29 94)" />
              <ellipse cx="12" cy="84" rx="4" ry="8" transform="rotate(-28 12 84)" />
              <ellipse cx="24" cy="78" rx="4" ry="8" transform="rotate(28 24 78)" />
              <ellipse cx="9" cy="68" rx="3.5" ry="7" transform="rotate(-16 9 68)" />
              <ellipse cx="21" cy="62" rx="3.5" ry="7" transform="rotate(28 21 62)" />
              <ellipse cx="16" cy="48" rx="3.5" ry="7" transform="rotate(12 16 48)" />
            </g>
          </svg>
        {/if}
        <div class="ranking-podium__avatar">
          <CommentRankingAvatar src={rankingAvatarUrl(item)} name={rankingDisplayName(item)} loading="eager" />
        </div>
      </div>
      <strong class="ranking-podium__name" title={rankingDisplayName(item)}>{rankingDisplayName(item)}</strong>
      <CommentRankingBadge badge={item.levelBadge} />
      <blockquote title={rankingLatestComment(item)}>“{rankingLatestComment(item)}”</blockquote>
      <dl class="ranking-podium__stats">
        <div>
          <span class="ranking-podium__comment-icon" aria-hidden="true"><CommentNextIcon name="message" size={24} /></span>
          <dt>评论</dt>
          <dd>{rankingCommentCount(item)}</dd>
        </div>
        <div>
          <span class="ranking-podium__heart" aria-hidden="true"><CommentNextIcon name="heartFill" size={25} /></span>
          <dt>获赞</dt>
          <dd>{rankingUpvoteCount(item)}</dd>
        </div>
      </dl>
    </li>
  {/each}
</ol>

<style>
  .ranking-podium {
    display: grid;
    grid-template-columns: repeat(3, minmax(0, 1fr));
    align-items: end;
    gap: 1rem;
    gap: clamp(0.65rem, 2cqi, 1.4rem);
    margin: 0;
    padding: 3rem 0 0;
    list-style: none;
  }

  .ranking-podium__reader {
    --medal-color: #d69a20;
    --podium-tint: #ffd362;
    --ranking-podium-bg: var(--comment-next-badge-first-bg-color, #fff9e9);
    position: relative;
    display: flex;
    min-width: 0;
    flex-direction: column;
    align-items: center;
    padding: 1.4rem 1rem 1.2rem;
    padding: 1.4rem clamp(0.75rem, 2cqi, 1.4rem) 1.2rem;
    border-radius: 1.1rem;
    background: var(--ranking-podium-bg);
    text-align: center;
  }

  [data-rank='1'] {
    grid-column: 2;
    grid-row: 1;
    padding-top: 1.8rem;
  }

  [data-rank='2'] {
    --medal-color: #9caac0;
    --podium-tint: #bbd0f5;
    --ranking-podium-bg: var(--comment-next-badge-level-bg-color, #f1f5fc);
    grid-column: 1;
    grid-row: 1;
  }

  [data-rank='3'] {
    --medal-color: #e69659;
    --podium-tint: #f4bd9c;
    --ranking-podium-bg: var(--comment-next-badge-custom-bg-color, #fcf4ef);
    grid-column: 3;
    grid-row: 1;
  }

  .ranking-podium__portrait {
    position: relative;
    width: 5.6rem;
    width: clamp(5.6rem, 12cqi, 7rem);
    height: 5.6rem;
    height: clamp(5.6rem, 12cqi, 7rem);
    flex: none;
    margin: -0.8rem 0 0.85rem;
  }

  [data-rank='1'] .ranking-podium__portrait {
    width: 6.4rem;
    width: clamp(6.4rem, 14cqi, 8.3rem);
    height: 6.4rem;
    height: clamp(6.4rem, 14cqi, 8.3rem);
    margin-top: -0.7rem;
  }

  .ranking-podium__avatar {
    width: 100%;
    height: 100%;
    overflow: hidden;
    border: 0.28rem solid var(--ranking-surface);
    border-radius: 50%;
    outline: 2px solid var(--medal-color);
  }

  .ranking-podium__crown {
    position: absolute;
    z-index: 2;
    top: -2.95rem;
    left: 50%;
    width: 3.8rem;
    height: 3.1rem;
    color: var(--medal-color);
    transform: translateX(-50%);
  }

  [data-rank='1'] .ranking-podium__crown {
    top: -3.45rem;
    width: 4.5rem;
    height: 3.6rem;
    color: #f8b516;
  }

  .ranking-podium__crown svg {
    width: 100%;
    height: 100%;
  }
  .ranking-podium__crown b {
    position: absolute;
    inset: 0.8rem 0 0;
    display: grid;
    place-items: center;
    color: #fff;
    font-size: 1.3rem;
    font-weight: 750;
  }

  .ranking-podium__laurels {
    position: absolute;
    top: 0;
    left: -34%;
    width: 168%;
    height: 100%;
    color: #f6c34c;
    pointer-events: none;
  }

  .ranking-podium__name {
    max-width: 100%;
    overflow: hidden;
    margin-bottom: 0.6rem;
    font-size: 1.15rem;
    font-size: clamp(1.05rem, 2.4cqi, 1.35rem);
    font-weight: 750;
    line-height: 1.3;
    text-overflow: ellipsis;
    white-space: nowrap;
  }

  blockquote {
    display: -webkit-box;
    min-height: 2.9em;
    overflow: hidden;
    margin: 0.8rem 0 1.1rem;
    color: var(--ranking-muted);
    font-size: 0.8rem;
    line-height: 1.45;
    overflow-wrap: anywhere;
    -webkit-box-orient: vertical;
    -webkit-line-clamp: 2;
    line-clamp: 2;
  }

  .ranking-podium__stats {
    display: grid;
    width: min(100%, 15rem);
    grid-template-columns: repeat(2, minmax(0, 1fr));
    margin: 0;
  }

  .ranking-podium__stats > div {
    display: grid;
    grid-template-columns: auto auto;
    justify-content: center;
    align-items: center;
    column-gap: 0.6rem;
  }

  .ranking-podium__stats > div + div {
    border-left: 1px solid var(--ranking-line);
  }

  .ranking-podium__stats span {
    grid-row: 1 / 3;
  }
  dd {
    grid-column: 2;
    grid-row: 1;
    margin: 0;
    font-size: 1.12rem;
    font-weight: 750;
    font-variant-numeric: tabular-nums;
  }
  dt {
    grid-column: 2;
    grid-row: 2;
    color: var(--ranking-muted);
    font-size: 0.69rem;
    text-align: left;
  }
  .ranking-podium__comment-icon {
    color: #669ed6;
  }
  .ranking-podium__heart {
    color: var(--ranking-heart, #f45c7c);
  }
  .ranking-podium--single {
    grid-template-columns: minmax(0, 1fr);
  }
  .ranking-podium--single [data-rank='1'] {
    grid-column: 1;
  }

  @supports (color: color-mix(in srgb, black, white)) {
    .ranking-podium__reader {
      background: linear-gradient(140deg,
        color-mix(in srgb, var(--podium-tint) 20%, var(--ranking-surface)),
        color-mix(in srgb, var(--podium-tint) 5%, var(--ranking-surface)));
    }

    .ranking-podium__avatar {
      outline: 2px solid color-mix(in srgb, var(--medal-color) 45%, var(--ranking-surface));
    }
  }

  @container (max-width: 34rem) {
    .ranking-podium {
      grid-template-columns: minmax(0, 1fr);
      gap: 3.2rem;
      padding-top: 2.8rem;
    }

    .ranking-podium__reader {
      grid-column: 1;
      grid-row: auto;
      padding: 1.5rem 1rem 1.2rem;
    }

    blockquote {
      min-height: 0;
      max-width: 24rem;
    }
    [data-rank='1'] .ranking-podium__portrait {
      width: 7.5rem;
      height: 7.5rem;
    }
  }

  @supports not (container-type: inline-size) {
    .ranking-podium {
      grid-template-columns: minmax(0, 1fr);
      gap: 3.2rem;
      padding-top: 2.8rem;
    }

    .ranking-podium__reader {
      grid-column: 1;
      grid-row: auto;
      padding: 1.5rem 1rem 1.2rem;
    }

    blockquote {
      min-height: 0;
      max-width: 24rem;
    }
    [data-rank='1'] .ranking-podium__portrait {
      width: 7.5rem;
      height: 7.5rem;
    }
  }
</style>
