<script lang="ts">
import { onMount, tick } from 'svelte';
import { resolveCommentBadges } from './badges/resolve';
import CommentNextCommentItem from './CommentNextCommentItem.svelte';
import CommentNextCommentSkeleton from './CommentNextCommentSkeleton.svelte';
import CommentNextIcon from './CommentNextIcon.svelte';
import { createDemoCommentPage, demoBadgeConfig } from './demo/comments';
import {
  fetchLinkedCommentPage,
  type LinkedReplyPage,
} from './services/comment-location';
import { fetchCommentPage } from './services/comments';
import type {
  CommentNextAiConfig,
  CommentNextAvatarConfig,
  CommentNextEmoteConfig,
  CommentNextReactionConfig,
  CommentNextReportConfig,
  CommentNextSecurityConfig,
  CommentNextUploadConfig,
} from './services/config';
import type {
  CommentNextBadgeConfig,
  CommentNextComment,
  CommentNextCommentPage,
  CommentNextCommentSort,
} from './types/comment';
import type { CommentNextEmotePack } from './types/emote';
import {
  type CommentAnchorTarget,
  findCommentAnchor,
  parseCommentAnchor,
  revealCommentAnchor,
} from './utils/comment-anchor';

const {
  baseUrl = '',
  group = '',
  kind = '',
  version = 'v1alpha1',
  name = '',
  loggedIn = false,
  allowAnonymous = true,
  showCaptcha = false,
  captchaType = 'ALPHANUMERIC',
  captchaConfig,
  demoData = false,
  pageSize = 20,
  replySize = 10,
  withReplies = true,
  showCommenterDevice = true,
  enableImageLightbox = true,
  badgeConfig: configuredBadgeConfig,
  aiConfig,
  reactionConfig,
  reportConfig,
  uploadConfig,
  emoteConfig,
  avatarConfig,
  emotePacks = [],
}: {
  baseUrl?: string;
  group?: string;
  kind?: string;
  version?: string;
  name?: string;
  loggedIn?: boolean;
  allowAnonymous?: boolean;
  showCaptcha?: boolean;
  captchaType?: NonNullable<CommentNextSecurityConfig['captcha']>['type'];
  captchaConfig?: CommentNextSecurityConfig['captcha'];
  demoData?: boolean;
  pageSize?: number;
  replySize?: number;
  withReplies?: boolean;
  showCommenterDevice?: boolean;
  enableImageLightbox?: boolean;
  badgeConfig?: CommentNextBadgeConfig;
  aiConfig?: CommentNextAiConfig;
  reactionConfig?: CommentNextReactionConfig;
  reportConfig?: CommentNextReportConfig;
  uploadConfig?: CommentNextUploadConfig;
  emoteConfig?: CommentNextEmoteConfig;
  avatarConfig?: CommentNextAvatarConfig;
  emotePacks?: CommentNextEmotePack[];
} = $props();

let comments = $state<CommentNextComment[]>([]);
let page = $state(1);
let total = $state(0);
let totalPages = $state(1);
let hasPrevious = $state(false);
let hasNext = $state(false);
let firstCommentId = $state('');
let loading = $state(false);
let errorMessage = $state('');
let anchorMessage = $state('');
let sortMode = $state<CommentNextCommentSort>('latest');
let linkedReplies = $state<LinkedReplyPage>();
// biome-ignore lint/style/useConst: Svelte assigns the section through bind:this.
let listElement = $state<HTMLElement>();
let pendingLoad:
  | { controller: AbortController; target?: CommentAnchorTarget }
  | undefined;
let clearHighlight: (() => void) | undefined;
let refreshTimers: number[] = [];

const badgeConfig = $derived<CommentNextBadgeConfig>(
  demoData ? demoBadgeConfig : (configuredBadgeConfig ?? {})
);
const visibleComments = $derived(
  demoData ? sortComments(comments, sortMode) : comments
);
const paginationItems = $derived(resolvePaginationItems(page, totalPages));
const sortOptions: Array<{ label: string; value: CommentNextCommentSort }> = [
  { label: '最新', value: 'latest' },
  { label: '最热', value: 'hot' },
  { label: '最早', value: 'earliest' },
];
const aiMentionName = $derived(resolveAiMentionName(aiConfig));

onMount(() => {
  void loadComments(1, parseCommentAnchor(window.location.hash));

  const handleCreated = () => {
    if (pendingLoad?.target) return;
    clearRefreshTimers();
    void refreshComments({ scrollIntoView: true });
    refreshTimers = [1800, 5200].map((delay) =>
      window.setTimeout(() => void refreshComments(), delay)
    );
  };

  window.addEventListener('halo:comment:created', handleCreated);
  window.addEventListener('hashchange', handleHashChange);

  return () => {
    window.removeEventListener('halo:comment:created', handleCreated);
    window.removeEventListener('hashchange', handleHashChange);
    pendingLoad?.controller.abort();
    clearHighlight?.();
    clearRefreshTimers();
  };
});

function handleHashChange() {
  const target = parseCommentAnchor(window.location.hash);
  clearHighlight?.();
  anchorMessage = '';
  if (!target) {
    if (pendingLoad?.target) void loadComments(page);
    return;
  }

  clearRefreshTimers();
  if (!pendingLoad && focusLinkedTarget(target)) return;
  void loadComments(page, target);
}

function focusLinkedTarget(target: CommentAnchorTarget): boolean {
  const node = listElement && findCommentAnchor(listElement, target);
  if (!node) return false;
  clearHighlight?.();
  clearHighlight = revealCommentAnchor(node);
  return true;
}

async function refreshComments(options: { scrollIntoView?: boolean } = {}) {
  if ((await loadComments(1)) && options.scrollIntoView)
    scrollCommentsIntoView();
}

function clearRefreshTimers() {
  for (const timer of refreshTimers) window.clearTimeout(timer);
  refreshTimers = [];
}

async function selectPage(
  nextPage: number,
  options: { scrollIntoView?: boolean } = {}
) {
  const normalizedPage = clampPage(nextPage);

  if (normalizedPage === page || loading) {
    return;
  }

  clearRefreshTimers();
  if ((await loadComments(normalizedPage)) && options.scrollIntoView)
    scrollCommentsIntoView();
}

async function selectSort(nextSortMode: CommentNextCommentSort) {
  if (sortMode === nextSortMode || loading) {
    return;
  }

  sortMode = nextSortMode;
  clearRefreshTimers();
  await refreshComments();
}

async function loadComments(
  nextPage: number,
  target?: CommentAnchorTarget
): Promise<boolean> {
  pendingLoad?.controller.abort();
  clearHighlight?.();
  const controller = new AbortController();
  const request = { controller, target };
  pendingLoad = request;
  errorMessage = '';
  anchorMessage = '';
  loading = true;

  try {
    if (demoData) {
      applyCommentPage(createDemoCommentPage());
      if (target) {
        await tick();
        if (!controller.signal.aborted) focusLinkedTarget(target);
      }
      return !controller.signal.aborted;
    }
    if (!group || !kind || !name) {
      applyCommentPage();
      return false;
    }

    const options = {
      baseUrl,
      group,
      kind,
      name,
      version,
      page: nextPage,
      size: pageSize,
      replySize,
      withReplies,
      sort: sortMode,
      separatePinned: false,
      signal: controller.signal,
    };
    const linked = target
      ? await fetchLinkedCommentPage(options, target).catch((error) => {
          if (controller.signal.aborted) throw error;
          console.warn('Failed to locate linked comment', error);
          return undefined;
        })
      : undefined;
    if (controller.signal.aborted) return false;
    const data = linked?.data ?? (await fetchCommentPage(options));
    if (controller.signal.aborted) return false;

    applyCommentPage(data, linked?.replies);
    if (target) {
      await tick();
      if (controller.signal.aborted) return false;
      if (
        !focusLinkedTarget(target) &&
        document.querySelectorAll('comment-next, comment-widget').length <= 1
      ) {
        anchorMessage = '无法定位这条评论或回复。';
        scrollCommentsIntoView();
      }
    }
    return true;
  } catch (error) {
    if (controller.signal.aborted) return false;
    console.error(error);
    errorMessage = '评论列表加载失败';
    applyCommentPage();
    return false;
  } finally {
    if (pendingLoad === request) {
      pendingLoad = undefined;
      loading = false;
    }
  }
}

function applyCommentPage(
  data?: CommentNextCommentPage,
  linked?: LinkedReplyPage
) {
  comments = data?.items ?? [];
  linkedReplies = linked;
  page = data?.page ?? 1;
  total = data?.total ?? 0;
  totalPages = Math.max(data?.totalPages ?? 1, 1);
  hasPrevious = Boolean(data?.hasPrevious);
  hasNext = Boolean(data?.hasNext);
  firstCommentId = data?.firstCommentId ?? '';
}

function isFirstComment(comment: CommentNextComment): boolean {
  return Boolean(firstCommentId && comment.id === firstCommentId);
}

function sortComments(
  items: CommentNextComment[],
  mode: CommentNextCommentSort
): CommentNextComment[] {
  return [...items].sort((left, right) => {
    const upvotesDiff =
      mode === 'hot'
        ? (right.stats?.upvotes ?? 0) - (left.stats?.upvotes ?? 0)
        : 0;
    if (upvotesDiff) {
      return upvotesDiff;
    }

    const timeDifference = commentTime(left) - commentTime(right);
    return mode === 'earliest' ? timeDifference : -timeDifference;
  });
}

function commentTime(comment: CommentNextComment): number {
  return comment.creationTime ? new Date(comment.creationTime).getTime() : 0;
}

function clampPage(nextPage: number): number {
  return Math.min(Math.max(nextPage, 1), Math.max(totalPages, 1));
}

function scrollCommentsIntoView() {
  requestAnimationFrame(() => {
    const root = listElement?.getRootNode();
    (root instanceof ShadowRoot ? root.host : listElement)?.scrollIntoView?.({
      block: 'start',
      behavior: 'smooth',
    });
  });
}

function resolveAiMentionName(config?: CommentNextAiConfig): string {
  const value =
    config?.assistantMentionName ||
    config?.assistantDisplayName ||
    config?.assistantName ||
    '';

  if (!value.trim()) {
    return '';
  }

  return value.trim().startsWith('@') ? value.trim() : `@${value.trim()}`;
}

function resolvePaginationItems(
  currentPage: number,
  pageCount: number
): Array<number | 'ellipsis'> {
  const normalizedPageCount = Math.max(pageCount, 1);

  if (normalizedPageCount <= 7) {
    return Array.from({ length: normalizedPageCount }, (_, index) => index + 1);
  }

  const pages = new Set<number>([
    1,
    normalizedPageCount,
    currentPage - 1,
    currentPage,
    currentPage + 1,
  ]);

  if (currentPage <= 3) {
    pages.add(2);
    pages.add(3);
    pages.add(4);
  }

  if (currentPage >= normalizedPageCount - 2) {
    pages.add(normalizedPageCount - 3);
    pages.add(normalizedPageCount - 2);
    pages.add(normalizedPageCount - 1);
  }

  const sortedPages = [...pages]
    .filter((item) => item >= 1 && item <= normalizedPageCount)
    .sort((left, right) => left - right);
  const items: Array<number | 'ellipsis'> = [];

  for (const item of sortedPages) {
    const previous = items.at(-1);
    if (typeof previous === 'number' && item - previous > 1) {
      items.push('ellipsis');
    }
    items.push(item);
  }

  return items;
}
</script>

{#snippet renderComment(comment: CommentNextComment)}
  <CommentNextCommentItem
    {baseUrl}
    {comment}
    badges={resolveCommentBadges(comment, { isFirstComment: isFirstComment(comment), config: badgeConfig })}
    {badgeConfig}
    {loggedIn}
    {allowAnonymous}
    {showCaptcha}
    {captchaType}
    {captchaConfig}
    {demoData}
    {replySize}
    linkedReplyPage={linkedReplies?.commentName === comment.id ? linkedReplies.data : undefined}
    {showCommenterDevice}
    {enableImageLightbox}
    {aiConfig}
    {reactionConfig}
    {reportConfig}
    {aiMentionName}
    {uploadConfig}
    {emoteConfig}
    {avatarConfig}
    {emotePacks}
  />
{/snippet}

<section bind:this={listElement} class="comment-next-comments" aria-label="评论列表" aria-busy={loading}>
  <div class="comment-next-comments-bar">
    <div class="comment-next-comments-tabs" role="group" aria-label="评论排序">
      {#each sortOptions as option}
        <button
          class:comment-next-comments-tab-active={sortMode === option.value}
          type="button"
          disabled={loading && sortMode !== option.value}
          aria-pressed={sortMode === option.value}
          onclick={() => selectSort(option.value)}
        >
          {option.label}
        </button>
      {/each}
    </div>
    {#if errorMessage}
      <button class="comment-next-comments-retry" type="button" onclick={() => loadComments(page, parseCommentAnchor(window.location.hash))}>
        <CommentNextIcon name="refresh" size={13} />
        重试
      </button>
    {:else if total > 0}
      <span class="comment-next-comments-count">{total.toLocaleString('zh-CN')} 条评论</span>
    {/if}
  </div>

  {#if anchorMessage}
    <p class="comment-next-anchor-notice" role="status">{anchorMessage}</p>
  {/if}

  {#if loading && !comments.length}
    <CommentNextCommentSkeleton />
  {:else if visibleComments.length}
    <div class="comment-next-comments-list">
      {#each visibleComments as comment (comment.id)}
        {@render renderComment(comment)}
      {/each}
    </div>
  {:else}
    <div class="comment-next-comments-empty">
      <span class="comment-next-comments-empty-icon">
        <CommentNextIcon name="message" size={18} />
      </span>
      <span>{errorMessage || "还没有评论"}</span>
    </div>
  {/if}

  {#if totalPages > 1}
    <nav class="comment-next-comments-pagination" aria-label={`评论分页，共 ${total} 条`}>
      <button
        class="comment-next-comments-page-control"
        type="button"
        disabled={loading || !hasPrevious}
        onclick={() => selectPage(page - 1, { scrollIntoView: true })}
      >
        上一页
      </button>

      <div class="comment-next-comments-page-list">
        {#each paginationItems as item, index}
          {#if item === 'ellipsis'}
            <span class="comment-next-comments-page-ellipsis" aria-hidden="true">...</span>
          {:else}
            <button
              class:comment-next-comments-page-active={item === page}
              type="button"
              disabled={loading || item === page}
              aria-current={item === page ? "page" : undefined}
              aria-label={`第 ${item} 页`}
              onclick={() => selectPage(item, { scrollIntoView: true })}
            >
              {item}
            </button>
          {/if}
        {/each}
      </div>

      <button
        class="comment-next-comments-page-control"
        type="button"
        disabled={loading || !hasNext}
        onclick={() => selectPage(page + 1, { scrollIntoView: true })}
      >
        {#if loading}
          <span class="comment-next-comments-pagination-loading">
            <CommentNextIcon name="loader" size={14} />
          </span>
        {/if}
        下一页
      </button>
    </nav>
  {/if}
</section>

<style>
  .comment-next-anchor-notice {
    margin: 0 0 0.75rem;
    color: var(--comment-next-muted-color, #64748b);
    font-size: 0.875rem;
  }

  .comment-next-comments :global([data-comment-next-highlight]) {
    background-color: color-mix(in srgb, var(--comment-next-primary-color, #2563eb) 12%, transparent);
    border-radius: 0.5rem;
    outline: 2px solid color-mix(in srgb, var(--comment-next-primary-color, #2563eb) 65%, transparent);
    outline-offset: 2px;
  }

  .comment-next-comments {
    --at-apply: box-border w-full text-[var(--comment-next-text-color,#172033)];
    max-width: 100%;
    min-width: 0;
    font-family: inherit;
  }

  :global([data-comment-list-style='cards']) .comment-next-comments-list {
    display: grid;
    gap: 0.625rem;
    padding-block-start: 0.625rem;
  }

  .comment-next-comments-bar {
    --at-apply: flex min-h-10 flex-wrap items-center justify-between gap-x-2 border-b border-b-solid [border-bottom-color:var(--comment-next-border-subtle-color,#e3e9f0)];
    padding-block-end: 0.375rem;
  }

  .comment-next-comments-tabs {
    --at-apply: inline-flex min-w-0 flex-wrap items-center gap-0.5;
  }

  .comment-next-comments-count {
    --at-apply: py-1 text-xs text-[var(--comment-next-muted-color,#6b7687)];
    margin-inline-start: auto;
    font-variant-numeric: tabular-nums;
    white-space: nowrap;
  }

  .comment-next-comments-tabs button {
    --at-apply: inline-flex min-h-8 cursor-pointer items-center justify-center rounded-md border-0 bg-transparent px-2.5 py-0 text-[0.8125rem] text-[var(--comment-next-muted-color,#6b7687)] font-[650] font-inherit transition-colors duration-140 ease-in-out;
  }

  .comment-next-comments-tabs button:enabled:hover {
    background: var(--comment-next-control-hover-bg-color, #eef2f4);
    color: var(--comment-next-text-color, #172033);
  }

  .comment-next-comments-tabs .comment-next-comments-tab-active,
  .comment-next-comments-tabs .comment-next-comments-tab-active:enabled:hover {
    color: var(--comment-next-primary-color, #2563eb);
    background: var(--comment-next-pill-active-bg-color, #eff6ff);
  }

  .comment-next-comments-tabs button:focus-visible,
  .comment-next-comments-retry:focus-visible,
  .comment-next-comments-pagination button:focus-visible {
    outline: 2px solid var(--comment-next-primary-color, #2563eb);
    outline-offset: 1px;
  }

  .comment-next-comments-tabs button:disabled {
    --at-apply: cursor-wait opacity-62;
  }

  .comment-next-comments-retry,
  .comment-next-comments-pagination button {
    --at-apply: inline-flex cursor-pointer items-center justify-center gap-[0.35rem] border border-solid [border-color:var(--comment-next-border-subtle-color,#dfe5ec)] bg-[var(--comment-next-toolbar-bg-color,#ffffff)] text-[0.8125rem] text-[var(--comment-next-muted-color,#6b7687)] font-[680] font-inherit transition-[background-color,color,transform] duration-140 ease-in-out;
  }

  .comment-next-comments-retry {
    --at-apply: ml-auto h-7 rounded-lg px-2.5 py-0;
  }

  .comment-next-comments-retry:hover,
  .comment-next-comments-pagination button:hover {
    --at-apply: bg-[var(--comment-next-control-hover-bg-color,#eef2f4)] text-[var(--comment-next-primary-color,rgb(59,130,246))];
  }

  .comment-next-comments-list {
    --at-apply: grid;
    max-width: 100%;
    min-width: 0;
  }

  .comment-next-comments-list > :global(.comment-next-comment-item + .comment-next-comment-item) {
    border-block-start: 1px solid var(--comment-next-border-subtle-color, #e3e9f0);
  }

  .comment-next-comments-empty {
    --at-apply: flex min-h-28 items-center justify-center gap-2 text-sm text-[var(--comment-next-muted-color,#6b7687)];
  }

  .comment-next-comments-empty-icon {
    --at-apply: inline-flex h-8 w-8 items-center justify-center rounded-full border border-solid [border-color:var(--comment-next-border-subtle-color,#dfe5ec)] bg-[var(--comment-next-toolbar-bg-color,#ffffff)];
  }

  .comment-next-comments-pagination {
    --at-apply: flex flex-wrap items-center justify-center gap-2 pt-4;
  }

  .comment-next-comments-page-list {
    --at-apply: inline-flex items-center gap-1.5;
  }

  .comment-next-comments-pagination button {
    --at-apply: h-8 min-w-8 rounded-[0.625rem] px-2.5 py-0 tabular-nums;
  }

  .comment-next-comments-page-control {
    --at-apply: min-w-16 px-3.5;
  }

  .comment-next-comments-pagination .comment-next-comments-page-active {
    --at-apply: [border-color:var(--comment-next-primary-color,rgb(59,130,246))] bg-[var(--comment-next-pill-active-bg-color,rgb(239_246_255))] text-[var(--comment-next-primary-color,rgb(59,130,246))];
  }

  .comment-next-comments-pagination button:disabled {
    --at-apply: cursor-not-allowed opacity-56;
  }

  .comment-next-comments-page-ellipsis {
    --at-apply: inline-flex h-8 min-w-5 items-center justify-center text-xs text-[var(--comment-next-muted-color,#6b7687)];
  }

  .comment-next-comments-pagination-loading {
    --at-apply: animate-spin;
  }

  @media (prefers-reduced-motion: reduce) {
    .comment-next-comments-retry,
    .comment-next-comments-tabs button,
    .comment-next-comments-pagination button {
      --at-apply: transition-none;
    }

    .comment-next-comments-pagination-loading {
      --at-apply: animate-none;
    }
  }
</style>
