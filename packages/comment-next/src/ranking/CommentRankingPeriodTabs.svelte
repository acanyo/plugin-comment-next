<script lang="ts">
import {
  COMMENT_RANKING_PERIOD_LABELS,
  type CommentRankingPeriod,
} from '../types/ranking';

const {
  periods,
  active,
  onselect,
}: {
  periods: CommentRankingPeriod[];
  active: CommentRankingPeriod;
  onselect: (period: CommentRankingPeriod) => void;
} = $props();
</script>

<div class="ranking-tabs" aria-label="排行榜统计周期">
  {#each periods as value (value)}
    <button
      type="button"
      class:ranking-tabs__active={value === active}
      aria-pressed={value === active}
      onclick={() => onselect(value)}
    >
      {COMMENT_RANKING_PERIOD_LABELS[value]}
    </button>
  {/each}
</div>

<style>
  .ranking-tabs {
    display: flex;
    align-items: center;
    gap: 0.18rem;
    padding: 0.2rem;
    border-radius: 0.65rem;
    background: var(--ranking-control, #f1f4f7);
  }

  button {
    min-width: 3.5rem;
    border: 0;
    border-radius: 0.48rem;
    padding: 0.48rem 0.78rem;
    background: transparent;
    color: var(--ranking-muted, #6b7687);
    cursor: pointer;
    font: inherit;
    font-size: 0.75rem;
    font-weight: 650;
    line-height: 1;
    transition:
      background-color 140ms ease,
      color 140ms ease;
  }

  button:hover {
    color: var(--ranking-text, #172033);
  }

  button:focus-visible {
    outline: 2px solid var(--ranking-accent, #31536f);
    outline-offset: 2px;
  }

  .ranking-tabs__active {
    background: var(--ranking-tab-active, #23384a);
    color: var(--ranking-tab-text, #fff);
  }

  @container (max-width: 30rem) {
    button {
      min-width: 0;
      padding-inline: 0.65rem;
    }
  }

  @media (prefers-reduced-motion: reduce) {
    button {
      transition: none;
    }
  }
</style>
