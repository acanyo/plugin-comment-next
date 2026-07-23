<script lang="ts">
import { onMount } from 'svelte';
import CommentNextEmotePreview from './CommentNextEmotePreview.svelte';
import type { CommentNextEmoteItem, CommentNextEmotePack } from './types/emote';

type CommentNextEmotePanelEntry = {
  item: CommentNextEmoteItem;
  packName: string;
  packId: string;
};

type CommentNextEmotePanelTab = {
  id: string;
  name: string;
  count: number;
};

const RECENT_PACK_ID = 'comment-next-recent-emotes';
const RECENT_STORAGE_KEY = 'comment-next:recent-emotes';
const MAX_RECENT_COUNT = 24;

const {
  packs = [],
  fixed = false,
  panelStyle = '',
  onSelect = () => {},
}: {
  packs?: CommentNextEmotePack[];
  fixed?: boolean;
  panelStyle?: string;
  onSelect?: (item: CommentNextEmoteItem) => void;
} = $props();

let activePackId = $state('');
let query = $state('');
let recentItemIds = $state<string[]>([]);
let selectedItemId = $state('');
let previewEntry = $state<CommentNextEmotePanelEntry | undefined>();

const availablePacks = $derived(packs.filter((pack) => pack.items.length));
const allEntries = $derived(
  availablePacks.flatMap((pack) =>
    pack.items.map((item) => ({
      item,
      packName: pack.name,
      packId: pack.id,
    }))
  )
);
const recentEntries = $derived(
  recentItemIds
    .map((id) => allEntries.find((entry) => entry.item.id === id))
    .filter((entry): entry is CommentNextEmotePanelEntry => Boolean(entry))
);
const tabs = $derived<CommentNextEmotePanelTab[]>([
  ...(recentEntries.length
    ? [
        {
          id: RECENT_PACK_ID,
          name: '最近',
          count: recentEntries.length,
        },
      ]
    : []),
  ...availablePacks.map((pack) => ({
    id: pack.id,
    name: pack.name,
    count: pack.items.length,
  })),
]);
const activePack = $derived(
  availablePacks.find((pack) => pack.id === activePackId) ?? availablePacks[0]
);
const activeEntries = $derived(
  activePackId === RECENT_PACK_ID
    ? recentEntries
    : allEntries.filter((entry) => entry.packId === activePack?.id)
);
const normalizedQuery = $derived(query.trim().toLowerCase());
const filteredEntries = $derived(
  normalizedQuery
    ? allEntries.filter((entry) => isEntryMatched(entry, normalizedQuery))
    : activeEntries
);
const hasImageItems = $derived(
  filteredEntries.some((entry) => entry.item.type === 'image')
);
const panelCountText = $derived(
  normalizedQuery
    ? `${filteredEntries.length} 个结果`
    : `${activeEntries.length} 个表情`
);

onMount(() => {
  recentItemIds = loadRecentItemIds();
});

$effect(() => {
  const hasActivePack = tabs.some((tab) => tab.id === activePackId);
  const fallbackId = tabs[0]?.id ?? '';

  if (!hasActivePack && activePackId !== fallbackId) {
    activePackId = fallbackId;
  }
});

function selectTab(tab: CommentNextEmotePanelTab) {
  activePackId = tab.id;
  query = '';
}

function handleSelect(item: CommentNextEmoteItem) {
  selectedItemId = item.id;
  recentItemIds = [
    item.id,
    ...recentItemIds.filter((id) => id !== item.id),
  ].slice(0, MAX_RECENT_COUNT);
  saveRecentItemIds(recentItemIds);
  emitSelect(item);
}

function showPreview(entry: CommentNextEmotePanelEntry) {
  if (
    entry.item.type === 'image' &&
    (entry.item.originSrc || entry.item.src || entry.item.previewSrc)
  ) {
    previewEntry = entry;
  }
}

function hidePreview(itemId: string) {
  if (previewEntry?.item.id === itemId) {
    previewEntry = undefined;
  }
}

function emitSelect(item: CommentNextEmoteItem) {
  if (typeof onSelect === 'function') {
    onSelect(item);
  }
}

function isEntryMatched(
  entry: CommentNextEmotePanelEntry,
  normalizedValue: string
): boolean {
  return [
    entry.item.label,
    entry.item.description,
    entry.item.value,
    entry.packName,
  ]
    .filter(Boolean)
    .some((value) => String(value).toLowerCase().includes(normalizedValue));
}

function loadRecentItemIds(): string[] {
  try {
    const value = localStorage.getItem(RECENT_STORAGE_KEY);
    const parsed = value ? JSON.parse(value) : [];

    return Array.isArray(parsed)
      ? parsed.filter((item): item is string => typeof item === 'string')
      : [];
  } catch {
    return [];
  }
}

function saveRecentItemIds(ids: string[]) {
  try {
    localStorage.setItem(RECENT_STORAGE_KEY, JSON.stringify(ids));
  } catch {
    // Ignore storage failures in private mode.
  }
}
</script>

{#if activePack}
  <div
    class:comment-next-emote-panel-fixed={fixed}
    class="comment-next-emote-panel"
    style={panelStyle}
    role="dialog"
    tabindex="-1"
    aria-label="表情包"
    onpointerdown={(event) => event.stopPropagation()}
  >
    <div class="comment-next-emote-head">
      <div class="comment-next-emote-search">
        <span aria-hidden="true">⌕</span>
        <input
          bind:value={query}
          type="search"
          autocomplete="off"
          spellcheck="false"
          placeholder="搜索表情"
          aria-label="搜索表情"
        />
      </div>
      <span class="comment-next-emote-count">{panelCountText}</span>
    </div>

    <div
      class:comment-next-emote-body-searching={normalizedQuery}
      class="comment-next-emote-body"
    >
      {#if !normalizedQuery}
        <div class="comment-next-emote-tabs" aria-label="表情分类">
          {#each tabs as tab}
            <button
              class:comment-next-emote-tab-active={activePackId === tab.id}
              type="button"
              aria-pressed={activePackId === tab.id}
              onclick={() => selectTab(tab)}
            >
              <span>{tab.name}</span>
              <small>{tab.count}</small>
            </button>
          {/each}
        </div>
      {/if}

      <div class="comment-next-emote-content">
        {#if filteredEntries.length}
          <div
            class:comment-next-emote-grid-image={hasImageItems}
            class="comment-next-emote-grid"
            aria-label={normalizedQuery ? '表情搜索结果' : `${activePack.name}表情`}
          >
            {#each filteredEntries as entry}
              <button
                class:comment-next-emote-item-image={entry.item.type === "image"}
                class:comment-next-emote-item-selected={selectedItemId === entry.item.id}
                class="comment-next-emote-item"
                type="button"
                title={entry.item.description || entry.item.label}
                aria-label={entry.item.description || entry.item.label}
                onpointerenter={() => showPreview(entry)}
                onpointerleave={() => hidePreview(entry.item.id)}
                onfocus={() => showPreview(entry)}
                onblur={() => hidePreview(entry.item.id)}
                onclick={() => handleSelect(entry.item)}
              >
                {#if entry.item.type === "image" && (entry.item.previewSrc || entry.item.src)}
                  <img
                    src={entry.item.previewSrc || entry.item.src}
                    alt={entry.item.label}
                    loading="lazy"
                    decoding="async"
                  />
                {:else}
                  <span>{entry.item.value}</span>
                {/if}
              </button>
            {/each}
          </div>
        {:else}
          <div class="comment-next-emote-empty">没有匹配的表情</div>
        {/if}
      </div>
    </div>

    {#if previewEntry}
      <CommentNextEmotePreview
        item={previewEntry.item}
        packName={previewEntry.packName}
      />
    {/if}
  </div>
{/if}

<style>
  .comment-next-emote-panel {
    --at-apply: absolute bottom-[calc(100%+0.5rem)] left-0 z-40 box-border w-[min(30rem,calc(100vw-2rem))] overflow-hidden rounded-[var(--comment-next-radius-lg,0.875rem)] border border-solid [border-color:var(--comment-next-border-subtle-color,#dfe5ec)] bg-[var(--comment-next-emote-panel-bg-color,rgb(255_255_255_/_0.98))] text-[var(--comment-next-text-color,#172033)] shadow-[0_20px_48px_rgb(15_23_42_/_0.16),0_1px_0_rgb(255_255_255_/_0.82)_inset] backdrop-blur-md;
    animation: comment-next-emote-panel-in 150ms cubic-bezier(0.2, 0.8, 0.2, 1);
  }

  .comment-next-emote-panel-fixed {
    position: fixed !important;
    top: var(--comment-next-emote-fixed-top, 1rem) !important;
    right: auto !important;
    bottom: auto !important;
    left: var(--comment-next-emote-fixed-left, 1rem) !important;
    z-index: var(--comment-next-emote-fixed-z-index, 9999) !important;
    width: var(--comment-next-emote-fixed-width, min(30rem, calc(100vw - 2rem))) !important;
    height: var(--comment-next-emote-fixed-height, auto);
    max-height: var(--comment-next-emote-fixed-max-height, 22.5rem);
    display: flex;
    flex-direction: column;
  }

  .comment-next-emote-head {
    --at-apply: flex items-center gap-2 border-b border-solid [border-color:var(--comment-next-border-subtle-color,#dfe5ec)] bg-[var(--comment-next-emote-head-bg-color,rgb(248_250_252_/_0.88))] px-2.5 py-2;
  }

  .comment-next-emote-search {
    --at-apply: flex h-8 min-w-0 flex-1 items-center gap-1.5 rounded-[0.5625rem] border border-solid [border-color:var(--comment-next-border-subtle-color,#dfe5ec)] bg-[var(--comment-next-bg-color,#ffffff)] px-2 text-[var(--comment-next-muted-color,#6b7687)] transition-[border-color,box-shadow] duration-140 ease-in-out;
  }

  .comment-next-emote-search:focus-within {
    --at-apply: [border-color:var(--comment-next-focus-border-color,#aeb9c6)] shadow-[0_0_0_3px_var(--comment-next-focus-shadow-color,rgb(59_130_246_/_0.12))];
  }

  .comment-next-emote-search span {
    --at-apply: text-[0.875rem] leading-none text-[var(--comment-next-muted-color,#6b7687)];
  }

  .comment-next-emote-search input {
    --at-apply: h-full min-w-0 flex-1 border-0 bg-transparent p-0 text-[0.8125rem] text-[var(--comment-next-text-color,#172033)] outline-none font-inherit;
  }

  .comment-next-emote-search input::placeholder {
    --at-apply: text-[var(--comment-next-placeholder-color,#8b96a7)];
  }

  .comment-next-emote-count {
    --at-apply: shrink-0 text-xs text-[var(--comment-next-muted-color,#6b7687)] tabular-nums;
  }

  .comment-next-emote-body {
    --at-apply: grid min-h-56 grid-cols-[7.5rem_minmax(0,1fr)];
  }

  .comment-next-emote-body-searching {
    --at-apply: grid-cols-1;
  }

  .comment-next-emote-tabs {
    --at-apply: max-h-70 overflow-y-auto border-r border-solid [border-color:var(--comment-next-border-subtle-color,#dfe5ec)] bg-[var(--comment-next-emote-tabs-bg-color,#f6f8fb)] p-1.5;
  }

  .comment-next-emote-panel-fixed .comment-next-emote-tabs {
    max-height: calc(var(--comment-next-emote-fixed-max-height, 22.5rem) - 3.25rem);
  }

  .comment-next-emote-panel-fixed .comment-next-emote-body {
    flex: 1 1 auto;
    min-height: 0;
  }

  .comment-next-emote-panel-fixed .comment-next-emote-content {
    min-height: 0;
    overflow: hidden;
  }

  .comment-next-emote-tabs button {
    --at-apply: flex h-8 w-full cursor-pointer items-center justify-between gap-2 rounded-[0.5rem] border-0 bg-transparent px-2 py-0 text-left text-[0.8125rem] text-[var(--comment-next-muted-color,#6b7687)] font-[650] font-inherit transition-[background-color,color,box-shadow,transform] duration-140 ease-in-out;
  }

  .comment-next-emote-tabs button + button {
    --at-apply: mt-0.5;
  }

  .comment-next-emote-tabs button span {
    --at-apply: min-w-0 truncate;
  }

  .comment-next-emote-tabs button small {
    --at-apply: text-[0.6875rem] text-[var(--comment-next-placeholder-color,#8b96a7)] tabular-nums;
  }

  .comment-next-emote-tabs button:hover,
  .comment-next-emote-tab-active {
    --at-apply: bg-[var(--comment-next-bg-color,#ffffff)] text-[var(--comment-next-primary-color,rgb(59,130,246))] shadow-[0_1px_4px_rgb(15_23_42_/_0.08)];
  }

  .comment-next-emote-content {
    --at-apply: min-w-0 p-2;
  }

  .comment-next-emote-grid {
    --at-apply: grid max-h-64 grid-cols-[repeat(auto-fill,minmax(3.25rem,1fr))] gap-1.5 overflow-y-auto pr-1;
  }

  .comment-next-emote-panel-fixed .comment-next-emote-grid {
    max-height: calc(var(--comment-next-emote-fixed-max-height, 22.5rem) - 4.5rem);
  }

  .comment-next-emote-grid-image {
    --at-apply: grid-cols-[repeat(auto-fill,minmax(2.625rem,1fr))];
  }

  .comment-next-emote-item {
    --at-apply: relative flex h-10 min-w-0 cursor-pointer items-center justify-center overflow-hidden rounded-[0.625rem] border border-solid border-transparent bg-transparent px-1.5 py-0 text-center text-[0.875rem] text-[var(--comment-next-text-color,#172033)] font-[650] font-inherit transition-[background-color,border-color,color,box-shadow,transform] duration-120 ease-in-out;
  }

  .comment-next-emote-item:hover,
  .comment-next-emote-item:focus-visible {
    --at-apply: [border-color:var(--comment-next-emote-item-hover-border-color,rgb(191_219_254))] bg-[var(--comment-next-control-hover-bg-color,#eef2f4)] text-[var(--comment-next-primary-color,rgb(59,130,246))] outline-none shadow-[0_6px_14px_rgb(15_23_42_/_0.08)];
  }

  .comment-next-emote-item:active {
    --at-apply: translate-y-px;
  }

  .comment-next-emote-item-selected {
    --at-apply: [border-color:var(--comment-next-primary-color,rgb(59,130,246))] bg-[var(--comment-next-pill-active-bg-color,rgb(239_246_255))] text-[var(--comment-next-primary-color,rgb(59,130,246))];
    animation: comment-next-emote-selected 220ms cubic-bezier(0.2, 0.8, 0.2, 1);
  }

  .comment-next-emote-item span {
    --at-apply: block min-w-0 overflow-hidden text-ellipsis whitespace-nowrap;
  }

  .comment-next-emote-item-image {
    --at-apply: h-11 p-1;
  }

  .comment-next-emote-item-image img {
    --at-apply: block max-h-full max-w-full object-contain;
  }

  .comment-next-emote-empty {
    --at-apply: flex h-44 items-center justify-center rounded-[0.75rem] border border-dashed [border-color:var(--comment-next-border-subtle-color,#dfe5ec)] text-sm text-[var(--comment-next-muted-color,#6b7687)];
  }

  @keyframes comment-next-emote-panel-in {
    from {
      opacity: 0;
      transform: translateY(0.35rem) scale(0.98);
    }

    to {
      opacity: 1;
      transform: translateY(0) scale(1);
    }
  }

  @keyframes comment-next-emote-selected {
    0% {
      transform: scale(0.96);
    }

    100% {
      transform: scale(1);
    }
  }

  @media (max-width: 780px) {
    .comment-next-emote-panel {
      --at-apply: right-0 left-0 w-full;
    }

    .comment-next-emote-body {
      --at-apply: block min-h-0;
    }

    .comment-next-emote-tabs {
      --at-apply: flex max-h-none gap-1 overflow-x-auto border-r-0 border-b p-1.5;
    }

    .comment-next-emote-tabs button {
      --at-apply: h-8 w-auto shrink-0;
    }

    .comment-next-emote-content {
      --at-apply: p-2;
    }

    .comment-next-emote-grid {
      --at-apply: max-h-[45vh] grid-cols-[repeat(auto-fill,minmax(3rem,1fr))];
    }

    .comment-next-emote-grid-image {
      --at-apply: grid-cols-[repeat(auto-fill,minmax(2.75rem,1fr))];
    }
  }

  @media (prefers-reduced-motion: reduce) {
    .comment-next-emote-panel {
      --at-apply: animate-none;
      animation: none;
    }

    .comment-next-emote-tabs button,
    .comment-next-emote-item {
      --at-apply: transition-none;
    }

    .comment-next-emote-item-selected {
      animation: none;
    }
  }
</style>
