<svelte:options
  customElement={{
    tag: 'comment-ranking-sidebar',
    props: {
      baseUrl: { reflect: true, type: 'String', attribute: 'base-url' },
      period: { reflect: true, type: 'String', attribute: 'period' },
      limit: { reflect: true, type: 'Number', attribute: 'limit' },
      title: { reflect: true, type: 'String', attribute: 'title' },
      rankingUrl: { reflect: true, type: 'String', attribute: 'ranking-url' },
      linkText: { reflect: true, type: 'String', attribute: 'link-text' },
      showHeader: { reflect: true, type: 'String', attribute: 'show-header' },
      accentColor: { reflect: true, type: 'String', attribute: 'accent-color' },
      surfaceColor: { reflect: true, type: 'String', attribute: 'surface-color' },
      textColor: { reflect: true, type: 'String', attribute: 'text-color' },
    },
  }}
/>

<script lang="ts">
import CommentNextIcon from './CommentNextIcon.svelte';
import CommentRankingAvatar from './ranking/CommentRankingAvatar.svelte';
import CommentRankingPeriodTabs from './ranking/CommentRankingPeriodTabs.svelte';
import {
  rankingAvatarUrl,
  rankingColorStyle,
  rankingCommentCount,
  rankingDisplayName,
  safeRankingUrl,
} from './ranking/presentation';
import { createRankingState } from './ranking/state.svelte';
import type { CommentRankingPeriod } from './types/ranking';

const {
  baseUrl = '',
  period = 'month',
  limit = 5,
  title = '评论读者墙',
  rankingUrl = '',
  linkText = '查看完整榜单',
  showHeader = true,
  accentColor = '',
  surfaceColor = '',
  textColor = '',
}: {
  baseUrl?: string;
  period?: 'month' | 'year';
  limit?: number;
  title?: string;
  rankingUrl?: string;
  linkText?: string;
  showHeader?: boolean | string;
  accentColor?: string;
  surfaceColor?: string;
  textColor?: string;
} = $props();

const sidebarPeriods: CommentRankingPeriod[] = ['month', 'year'];
let selectedPeriod = $state<CommentRankingPeriod | undefined>();
const activePeriod = $derived(
  selectedPeriod ?? (period === 'year' ? 'year' : 'month')
);
const ranking = createRankingState(() => ({
  baseUrl,
  period: activePeriod,
  limit: Math.min(Math.max(Math.round(Number(limit) || 5), 1), 10),
}));
const items = $derived(ranking.items);

const isHeaderVisible = $derived(
  showHeader !== false && showHeader !== 'false'
);
const componentStyle = $derived(
  rankingColorStyle(accentColor, surfaceColor, textColor)
);
const safeUrl = $derived(safeRankingUrl(rankingUrl));

$effect(() => {
  period;
  selectedPeriod = undefined;
});
</script>

<aside class="ranking-sidebar" aria-label={title} style={componentStyle || undefined}>
  <header>
    {#if isHeaderVisible}<h2>{title}</h2>{/if}
    <CommentRankingPeriodTabs
      periods={sidebarPeriods}
      active={activePeriod}
      onselect={(value) => (selectedPeriod = value)}
    />
  </header>

  {#if ranking.loading}
    <div class="ranking-sidebar__state" aria-live="polite">正在加载…</div>
  {:else if ranking.error}
    <div class="ranking-sidebar__state ranking-sidebar__state--error" role="alert">{ranking.error}</div>
  {:else if items.length === 0}
    <div class="ranking-sidebar__state">这个周期还没有评论</div>
  {:else}
    <ol>
      {#each items as item, index (`sidebar:${item.kind ?? 'reader'}:${item.displayName ?? 'anonymous'}:${index}`)}
        <li>
          <span class:ranking-sidebar__rank--top={index < 3} class="ranking-sidebar__rank">
            {#if index < 3}<CommentNextIcon name="crown" size={17} />{/if}
            <b>{index + 1}</b>
          </span>
          <span class="ranking-sidebar__avatar">
            <CommentRankingAvatar
              src={rankingAvatarUrl(item)}
              name={rankingDisplayName(item)}
              loading="eager"
            />
          </span>
          <strong title={rankingDisplayName(item)}>{rankingDisplayName(item)}</strong>
          <small><CommentNextIcon name="message" size={14} />{rankingCommentCount(item)}</small>
        </li>
      {/each}
    </ol>
  {/if}

  {#if linkText && safeUrl}
    <a class="ranking-sidebar__link" href={safeUrl}>
      {linkText}<CommentNextIcon name="chevronRight" size={17} />
    </a>
  {/if}
</aside>

<style>
  :host {
    display: block;
    width: 100%;
    min-width: 0;
    max-width: 100%;
    container-type: inline-size;
  }

  .ranking-sidebar {
    --ranking-accent: var(--comment-ranking-accent-color, var(--halo-cw-primary-1-color, #31536f));
    --ranking-surface: var(
      --comment-ranking-surface-color,
      var(--comment-next-surface-bg-color, #fff)
    );
    --ranking-text: var(
      --comment-ranking-text-color,
      var(--comment-next-text-color, #172033)
    );
    --ranking-muted: var(--comment-ranking-muted-color, var(--comment-next-muted-color, #6b7687));
    --ranking-line: var(--comment-ranking-line-color, var(--comment-next-border-subtle-color, #e5e9ee));
    --ranking-control: var(--comment-ranking-control-color, var(--comment-next-surface-bg-color, #f1f4f7));
    --ranking-tab-active: var(--ranking-accent);
    --ranking-tab-text: #fff;
    --ranking-avatar-fallback: var(--ranking-control);

    box-sizing: border-box;
    width: 100%;
    overflow: hidden;
    padding: 1rem;
    padding: clamp(1rem, 7cqi, 1.45rem);
    border: 1px solid var(--ranking-line);
    border-radius: var(--comment-next-radius-lg, 1rem);
    background: var(--ranking-surface);
    color: var(--ranking-text);
    font-family: var(--comment-next-font-family, ui-sans-serif, system-ui, sans-serif);
  }

  .ranking-sidebar,
  .ranking-sidebar * {
    box-sizing: border-box;
  }

  header {
    display: flex;
    flex-wrap: wrap;
    align-items: center;
    justify-content: space-between;
    gap: 0.65rem;
    margin-bottom: 1rem;
  }

  h2 {
    min-width: 0;
    margin: 0;
    overflow: hidden;
    font-size: 1.1rem;
    font-size: clamp(1rem, 7cqi, 1.18rem);
    font-weight: 800;
    letter-spacing: -0.03em;
    line-height: 1.2;
    text-overflow: ellipsis;
    white-space: nowrap;
  }

  ol {
    margin: 0;
    padding: 0;
    list-style: none;
  }

  li {
    display: grid;
    grid-template-columns: 1.6rem 2.35rem minmax(0, 1fr) auto;
    align-items: center;
    gap: 0.65rem;
    min-height: 3.25rem;
    border-bottom: 1px solid var(--ranking-line);
  }

  .ranking-sidebar__rank {
    position: relative;
    display: grid;
    height: 1.8rem;
    place-items: center;
    color: var(--ranking-muted);
  }

  .ranking-sidebar__rank :global(svg) {
    position: absolute;
    top: -0.05rem;
  }

  .ranking-sidebar__rank b {
    position: relative;
    z-index: 1;
    padding-top: 0.45rem;
    font-size: 0.65rem;
    font-variant-numeric: tabular-nums;
  }

  .ranking-sidebar__rank--top {
    color: #e4a51f;
  }

  li:nth-child(2) .ranking-sidebar__rank--top {
    color: #91a2b1;
  }

  li:nth-child(3) .ranking-sidebar__rank--top {
    color: #df8644;
  }

  .ranking-sidebar__avatar {
    display: block;
    width: 2.35rem;
    height: 2.35rem;
    overflow: hidden;
    border: 1px solid var(--ranking-line);
    border-radius: 999px;
  }

  strong {
    min-width: 0;
    overflow: hidden;
    color: var(--ranking-text);
    font-size: 0.78rem;
    font-weight: 700;
    text-overflow: ellipsis;
    white-space: nowrap;
  }

  small {
    display: flex;
    align-items: center;
    gap: 0.28rem;
    color: var(--ranking-muted);
    font-size: 0.7rem;
    font-variant-numeric: tabular-nums;
  }

  .ranking-sidebar__link {
    display: flex;
    min-height: 2.8rem;
    align-items: center;
    justify-content: center;
    gap: 0.35rem;
    margin-top: 1rem;
    border-radius: 0.6rem;
    background: var(--ranking-control);
    color: var(--ranking-text);
    font-size: 0.75rem;
    font-weight: 700;
    text-decoration: none;
    transition: background-color 140ms ease;
  }

  .ranking-sidebar__link:hover {
    background: var(--ranking-line);
  }

  .ranking-sidebar__link:focus-visible {
    outline: 2px solid var(--ranking-accent);
    outline-offset: 2px;
  }
  .ranking-sidebar__state {
    padding: 2.5rem 0;
    color: var(--ranking-muted);
    font-size: 0.76rem;
    text-align: center;
  }

  .ranking-sidebar__state--error {
    color: var(--comment-next-error-color, #dc2626);
  }

  @supports (color: color-mix(in srgb, black, white)) {
    .ranking-sidebar {
      --ranking-muted: color-mix(in srgb, var(--ranking-text) 58%, var(--ranking-surface));
      --ranking-line: color-mix(in srgb, var(--ranking-text) 10%, var(--ranking-surface));
      --ranking-control: color-mix(in srgb, var(--ranking-text) 6%, var(--ranking-surface));
    }

    .ranking-sidebar__link:hover {
      background: color-mix(in srgb, var(--ranking-text) 10%, var(--ranking-surface));
    }
  }

  @container (max-width: 17rem) {
    header {
      align-items: stretch;
      flex-direction: column;
    }
  }

  @supports not (container-type: inline-size) {
    header {
      align-items: stretch;
      flex-direction: column;
    }
  }

  @media (prefers-reduced-motion: reduce) {
    .ranking-sidebar__link {
      transition: none;
    }
  }
</style>
