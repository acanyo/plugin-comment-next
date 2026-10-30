<svelte:options
  customElement={{
    tag: 'comment-ranking',
    props: {
      baseUrl: { reflect: true, type: 'String', attribute: 'base-url' },
      period: { reflect: true, type: 'String', attribute: 'period' },
      limit: { reflect: true, type: 'Number', attribute: 'limit' },
      title: { reflect: true, type: 'String', attribute: 'title' },
      subtitle: { reflect: true, type: 'String', attribute: 'subtitle' },
      showHeader: { reflect: true, type: 'String', attribute: 'show-header' },
      accentColor: { reflect: true, type: 'String', attribute: 'accent-color' },
      surfaceColor: { reflect: true, type: 'String', attribute: 'surface-color' },
      textColor: { reflect: true, type: 'String', attribute: 'text-color' },
    },
  }}
/>

<script lang="ts">
import CommentRankingPeriodTabs from './ranking/CommentRankingPeriodTabs.svelte';
import CommentRankingPodium from './ranking/CommentRankingPodium.svelte';
import CommentRankingTable from './ranking/CommentRankingTable.svelte';
import { rankingColorStyle } from './ranking/presentation';
import { createRankingState } from './ranking/state.svelte';
import {
  type CommentRankingPeriod,
  normalizeCommentRankingPeriod,
} from './types/ranking';

const {
  baseUrl = '',
  period = 'month',
  limit = 10,
  title = '评论读者墙',
  subtitle = '感谢每一位认真阅读、真诚评论的朋友',
  showHeader = true,
  accentColor = '',
  surfaceColor = '',
  textColor = '',
}: {
  baseUrl?: string;
  period?: CommentRankingPeriod;
  limit?: number;
  title?: string;
  subtitle?: string;
  showHeader?: boolean | string;
  accentColor?: string;
  surfaceColor?: string;
  textColor?: string;
} = $props();

const pagePeriods = $derived<CommentRankingPeriod[]>(
  period === 'quarter'
    ? ['week', 'month', 'quarter', 'year', 'all']
    : ['week', 'month', 'year', 'all']
);
let selectedPeriod = $state<CommentRankingPeriod | undefined>();
const activePeriod = $derived(
  selectedPeriod ?? normalizeCommentRankingPeriod(period)
);
const ranking = createRankingState(() => ({
  baseUrl,
  period: activePeriod,
  limit,
}));
const items = $derived(ranking.items);
const isHeaderVisible = $derived(
  showHeader !== false && showHeader !== 'false'
);
const componentStyle = $derived(
  rankingColorStyle(accentColor, surfaceColor, textColor)
);

$effect(() => {
  period;
  selectedPeriod = undefined;
});
</script>

<section class="ranking-page" aria-label={title} style={componentStyle || undefined}>
  <header class="ranking-page__header">
    {#if isHeaderVisible}
      <div class="ranking-page__heading">
        <h2>{title}</h2>
        {#if subtitle}<p>{subtitle}<span aria-hidden="true">♥</span></p>{/if}
      </div>
    {/if}
    <CommentRankingPeriodTabs periods={pagePeriods} active={activePeriod} onselect={(value) => (selectedPeriod = value)} />
  </header>

  {#if ranking.loading}
    <div class="ranking-page__state" aria-live="polite">正在加载读者排行…</div>
  {:else if ranking.error}
    <div class="ranking-page__state ranking-page__state--error" role="alert">{ranking.error}</div>
  {:else if items.length === 0}
    <div class="ranking-page__state">这个周期还没有评论，期待第一位读者。</div>
  {:else}
    <CommentRankingPodium items={items.slice(0, 3)} />
    {#if items.length > 3}<CommentRankingTable items={items.slice(3)} />{/if}
  {/if}
</section>

<style>
  :host {
    display: block;
    width: 100%;
    min-width: 0;
    max-width: 100%;
    container-type: inline-size;
  }

  .ranking-page {
    --ranking-accent: var(--comment-ranking-accent-color, var(--halo-cw-primary-1-color, #31536f));
    --ranking-surface: var(--comment-ranking-surface-color, var(--comment-next-surface-bg-color, #fff));
    --ranking-text: var(--comment-ranking-text-color, var(--comment-next-text-color, #172033));
    --ranking-muted: var(--comment-ranking-muted-color, var(--comment-next-muted-color, #6b7687));
    --ranking-line: var(--comment-ranking-line-color, var(--comment-next-border-subtle-color, #e5e9ee));
    --ranking-control: var(--comment-ranking-control-color, var(--comment-next-surface-bg-color, #f1f4f7));
    --ranking-tab-active: var(--ranking-accent);
    --ranking-tab-text: #fff;
    --ranking-avatar-fallback: var(--ranking-control);

    box-sizing: border-box;
    width: 100%;
    padding: 1.25rem;
    padding: clamp(1rem, 3cqi, 2rem);
    border: 1px solid var(--ranking-line);
    border-radius: var(--comment-next-radius-lg, 1rem);
    background: var(--ranking-surface);
    color: var(--ranking-text);
    font-family: var(--comment-next-font-family, ui-sans-serif, system-ui, sans-serif);
  }

  .ranking-page :global(*) {
    box-sizing: border-box;
  }
  .ranking-page__header {
    display: flex;
    flex-wrap: wrap;
    align-items: flex-start;
    justify-content: space-between;
    gap: 1.5rem;
    margin-bottom: 1.5rem;
  }
  .ranking-page__heading {
    min-width: 0;
  }
  h2 {
    margin: 0;
    font-size: 1.4rem;
    font-size: clamp(1.25rem, 3.2cqi, 1.6rem);
    font-weight: 750;
    letter-spacing: -0.03em;
    line-height: 1.2;
  }
  p {
    margin: 0.5rem 0 0;
    color: var(--ranking-muted);
    font-size: 0.82rem;
    line-height: 1.5;
  }
  p span {
    margin-left: 0.35rem;
    color: var(--ranking-heart, #f45c7c);
  }
  .ranking-page__state {
    padding: 4rem 1.25rem;
    color: var(--ranking-muted);
    font-size: 0.84rem;
    text-align: center;
  }
  .ranking-page__state--error {
    color: var(--comment-next-error-color, #dc2626);
  }

  @supports (color: color-mix(in srgb, black, white)) {
    .ranking-page {
      --ranking-muted: color-mix(in srgb, var(--ranking-text) 62%, var(--ranking-surface));
      --ranking-line: color-mix(in srgb, var(--ranking-text) 8%, var(--ranking-surface));
      --ranking-control: color-mix(in srgb, var(--ranking-text) 3%, var(--ranking-surface));
    }
  }

  @container (max-width: 38rem) {
    .ranking-page__header {
      align-items: stretch;
      flex-direction: column;
      gap: 1rem;
    }
  }

  @supports not (container-type: inline-size) {
    .ranking-page__header {
      align-items: stretch;
      flex-direction: column;
      gap: 1rem;
    }
  }
</style>
