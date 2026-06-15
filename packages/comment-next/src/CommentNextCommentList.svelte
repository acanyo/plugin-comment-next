<script lang="ts">
  import { onMount } from "svelte";
  import { resolveCommentBadges } from "./badges/resolve";
  import CommentNextCommentItem from "./CommentNextCommentItem.svelte";
  import CommentNextCommentSkeleton from "./CommentNextCommentSkeleton.svelte";
  import CommentNextIcon from "./CommentNextIcon.svelte";
  import { createDemoCommentPage, demoBadgeConfig } from "./demo/comments";
  import { fetchCommentPage } from "./services/comments";
  import type { CommentNextBadgeConfig, CommentNextComment } from "./types/comment";

  let {
    baseUrl = "",
    group = "",
    kind = "",
    version = "v1alpha1",
    name = "",
    demoData = false,
    pageSize = 20,
    replySize = 10,
    withReplies = true,
    badgeConfig: configuredBadgeConfig,
  }: {
    baseUrl?: string;
    group?: string;
    kind?: string;
    version?: string;
    name?: string;
    demoData?: boolean;
    pageSize?: number;
    replySize?: number;
    withReplies?: boolean;
    badgeConfig?: CommentNextBadgeConfig;
  } = $props();

  let comments = $state<CommentNextComment[]>([]);
  let page = $state(1);
  let total = $state(0);
  let hasNext = $state(false);
  let loading = $state(false);
  let errorMessage = $state("");

  const badgeConfig = $derived<CommentNextBadgeConfig>(demoData ? demoBadgeConfig : (configuredBadgeConfig ?? {}));

  onMount(() => {
    void refreshComments();

    const handleCreated = () => {
      void refreshComments({ scrollIntoView: true });
    };

    window.addEventListener("halo:comment:created", handleCreated);

    return () => {
      window.removeEventListener("halo:comment:created", handleCreated);
    };
  });

  async function refreshComments(options: { scrollIntoView?: boolean } = {}) {
    page = 1;
    await loadComments({ append: false });

    if (options.scrollIntoView) {
      requestAnimationFrame(() => {
        document.querySelector("comment-next, comment-widget")?.scrollIntoView({
          block: "start",
          behavior: "smooth",
        });
      });
    }
  }

  async function loadMore() {
    if (!hasNext || loading) {
      return;
    }

    page += 1;
    await loadComments({ append: true });
  }

  async function loadComments({ append }: { append: boolean }) {
    if (demoData) {
      const demoPage = createDemoCommentPage();
      comments = demoPage.items;
      total = demoPage.total;
      hasNext = demoPage.hasNext;
      errorMessage = "";
      return;
    }

    if (!group || !kind || !name) {
      comments = [];
      total = 0;
      hasNext = false;
      return;
    }

    try {
      loading = true;
      errorMessage = "";

      const data = await fetchCommentPage({
        baseUrl,
        group,
        kind,
        name,
        version,
        page,
        size: pageSize,
        replySize,
        withReplies,
      });

      comments = append ? [...comments, ...data.items] : data.items;
      total = data.total;
      hasNext = data.hasNext;
    } catch (error) {
      console.error(error);
      errorMessage = "评论列表加载失败";
      if (!append) {
        comments = [];
        total = 0;
        hasNext = false;
      }
    } finally {
      loading = false;
    }
  }
</script>

<section class="comment-next-comments" aria-label="评论列表">
  <div class="comment-next-comments-bar">
    <span class="comment-next-comments-count">共 {total} 条评论</span>
    {#if errorMessage}
      <button class="comment-next-comments-retry" type="button" onclick={() => refreshComments()}>
        <CommentNextIcon name="refresh" size={13} />
        重试
      </button>
    {/if}
  </div>

  {#if loading && !comments.length}
    <CommentNextCommentSkeleton />
  {:else if comments.length}
    <div class="comment-next-comments-list">
      {#each comments as comment, index (comment.id)}
        <CommentNextCommentItem
          {comment}
          badges={resolveCommentBadges(comment, { isFirstComment: index === 0, config: badgeConfig })}
          {badgeConfig}
          first={index === 0}
        />
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

  {#if hasNext}
    <div class="comment-next-comments-more">
      <button type="button" disabled={loading} onclick={loadMore}>
        {#if loading}
          <span class="comment-next-comments-more-loading">
            <CommentNextIcon name="loader" size={15} />
          </span>
        {/if}
        加载更多
      </button>
    </div>
  {/if}
</section>

<style>
  .comment-next-comments {
    width: 100%;
    box-sizing: border-box;
    color: var(--comment-next-text-color, #172033);
    font-family: var(
      --comment-next-font-family,
      ui-sans-serif,
      system-ui,
      -apple-system,
      BlinkMacSystemFont,
      "Segoe UI",
      sans-serif
    );
  }

  .comment-next-comments-bar {
    display: flex;
    align-items: center;
    justify-content: space-between;
    min-height: 2rem;
    gap: 0.75rem;
    border-bottom: 1px solid var(--comment-next-border-subtle-color, #e3e9f0);
  }

  .comment-next-comments-count {
    color: var(--comment-next-muted-color, #6b7687);
    font-size: 0.8125rem;
    font-weight: 720;
  }

  .comment-next-comments-retry,
  .comment-next-comments-more button {
    display: inline-flex;
    align-items: center;
    justify-content: center;
    gap: 0.35rem;
    border: 1px solid var(--comment-next-border-subtle-color, #dfe5ec);
    background: var(--comment-next-toolbar-bg-color, #ffffff);
    color: var(--comment-next-muted-color, #6b7687);
    cursor: pointer;
    font: inherit;
    font-size: 0.8125rem;
    font-weight: 680;
    transition:
      background-color 140ms ease,
      color 140ms ease,
      transform 140ms ease;
  }

  .comment-next-comments-retry {
    height: 1.75rem;
    padding: 0 0.625rem;
    border-radius: 0.5rem;
  }

  .comment-next-comments-retry:hover,
  .comment-next-comments-more button:hover {
    background: var(--comment-next-control-hover-bg-color, #eef2f4);
    color: var(--comment-next-primary-color, rgb(59, 130, 246));
  }

  .comment-next-comments-list {
    display: grid;
  }

  .comment-next-comments-empty {
    display: flex;
    align-items: center;
    justify-content: center;
    min-height: 7rem;
    gap: 0.5rem;
    color: var(--comment-next-muted-color, #6b7687);
    font-size: 0.875rem;
  }

  .comment-next-comments-empty-icon {
    display: inline-flex;
    align-items: center;
    justify-content: center;
    width: 2rem;
    height: 2rem;
    border: 1px solid var(--comment-next-border-subtle-color, #dfe5ec);
    border-radius: 999px;
    background: var(--comment-next-toolbar-bg-color, #ffffff);
  }

  .comment-next-comments-more {
    display: flex;
    justify-content: center;
    padding-top: 0.5rem;
  }

  .comment-next-comments-more button {
    min-width: 6.5rem;
    height: 2rem;
    padding: 0 0.875rem;
    border-radius: 0.625rem;
  }

  .comment-next-comments-more button:disabled {
    cursor: wait;
    opacity: 0.7;
  }

  .comment-next-comments-more-loading {
    animation: comment-next-comments-spin 900ms linear infinite;
  }

  @keyframes comment-next-comments-spin {
    to {
      transform: rotate(360deg);
    }
  }

  @media (prefers-reduced-motion: reduce) {
    .comment-next-comments-retry,
    .comment-next-comments-more button {
      transition: none;
    }

    .comment-next-comments-more-loading {
      animation: none;
    }
  }
</style>
