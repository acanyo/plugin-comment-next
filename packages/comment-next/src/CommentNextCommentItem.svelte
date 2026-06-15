<script lang="ts">
  import CommentNextAvatar from "./CommentNextAvatar.svelte";
  import CommentNextBadge from "./CommentNextBadge.svelte";
  import CommentNextContent from "./CommentNextContent.svelte";
  import CommentNextIcon from "./CommentNextIcon.svelte";
  import CommentNextReplyItem from "./CommentNextReplyItem.svelte";
  import { formatCommentDate, formatRelativeTime } from "./utils/date";
  import type { CommentNextBadge as CommentNextBadgeModel, CommentNextBadgeConfig, CommentNextComment } from "./types/comment";

  let {
    comment,
    badges = [],
    badgeConfig = {},
    first = false,
  }: {
    comment: CommentNextComment;
    badges?: CommentNextBadgeModel[];
    badgeConfig?: CommentNextBadgeConfig;
    first?: boolean;
  } = $props();

  let upvoted = $state(false);
  let upvotes = $state(0);
  let previousCommentId = $state("");

  $effect(() => {
    if (previousCommentId !== comment.id) {
      previousCommentId = comment.id;
      upvoted = false;
      upvotes = comment.stats?.upvotes ?? 0;
    }
  });

  function handleLocalUpvote() {
    if (upvoted) {
      return;
    }

    upvoted = true;
    upvotes += 1;
  }
</script>

<article class:comment-next-comment-item-first={first} class="comment-next-comment-item">
  <div class="comment-next-comment-avatar">
    {#if comment.author.website}
      <a href={comment.author.website} target="_blank" rel="noopener noreferrer nofollow ugc" aria-label={comment.author.displayName}>
        <CommentNextAvatar src={comment.author.avatar} alt={comment.author.displayName} size={38} />
      </a>
    {:else}
      <CommentNextAvatar src={comment.author.avatar} alt={comment.author.displayName} size={38} />
    {/if}
  </div>

  <div class="comment-next-comment-main">
    <header class="comment-next-comment-meta">
      {#if comment.author.website}
        <a
          class="comment-next-comment-author"
          href={comment.author.website}
          target="_blank"
          rel="noopener noreferrer nofollow ugc"
        >
          {comment.author.displayName}
        </a>
      {:else}
        <span class="comment-next-comment-author">{comment.author.displayName}</span>
      {/if}

      <span class="comment-next-comment-badges" aria-label="评论者徽章">
        {#each badges as badge}
          <CommentNextBadge {badge} />
        {/each}
      </span>

      {#if comment.creationTime}
        <time class="comment-next-comment-time" title={formatCommentDate(comment.creationTime)}>
          {formatRelativeTime(comment.creationTime)}
        </time>
      {/if}

      {#if comment.private}
        <span class="comment-next-comment-state">
          <CommentNextIcon name="lock" size={12} />
          私密
        </span>
      {/if}

      {#if comment.approved === false}
        <span class="comment-next-comment-state">待审核</span>
      {/if}
    </header>

    <CommentNextContent content={comment.content} />

    <footer class="comment-next-comment-actions">
      <button
        class:comment-next-comment-action-active={upvoted}
        type="button"
        aria-label="点赞"
        onclick={handleLocalUpvote}
      >
        <CommentNextIcon name={upvoted ? "heartFill" : "heart"} size={15} />
        <span>{upvotes}</span>
      </button>
      <button type="button" aria-label="回复">
        <CommentNextIcon name="message" size={15} />
        <span>{comment.stats?.replies ?? comment.replies?.length ?? 0}</span>
      </button>
    </footer>

    {#if comment.replies?.length}
      <div class="comment-next-replies">
        {#each comment.replies as reply (reply.id)}
          <CommentNextReplyItem {reply} {badgeConfig} />
        {/each}
      </div>
    {/if}
  </div>
</article>

<style>
  .comment-next-comment-item {
    position: relative;
    display: grid;
    grid-template-columns: 2.375rem minmax(0, 1fr);
    gap: 0.875rem;
    padding: 1.125rem 0;
  }

  .comment-next-comment-item:not(.comment-next-comment-item-first) {
    border-top: 1px solid var(--comment-next-border-subtle-color, #e7ecf2);
  }

  .comment-next-comment-avatar {
    padding-top: 0.125rem;
  }

  .comment-next-comment-avatar a {
    display: inline-flex;
    border-radius: 999px;
    color: inherit;
    text-decoration: none;
  }

  .comment-next-comment-main {
    min-width: 0;
  }

  .comment-next-comment-meta {
    display: flex;
    align-items: center;
    min-width: 0;
    gap: 0.45rem;
    flex-wrap: wrap;
    margin-bottom: 0.375rem;
  }

  .comment-next-comment-author {
    max-width: 13rem;
    overflow: hidden;
    color: var(--comment-next-text-color, #172033);
    font-size: 0.9375rem;
    font-weight: 800;
    text-decoration: none;
    text-overflow: ellipsis;
    white-space: nowrap;
  }

  .comment-next-comment-author:hover {
    color: var(--comment-next-primary-color, rgb(59, 130, 246));
  }

  .comment-next-comment-badges {
    display: inline-flex;
    align-items: center;
    gap: 0.25rem;
    flex-wrap: wrap;
  }

  .comment-next-comment-time,
  .comment-next-comment-state {
    display: inline-flex;
    align-items: center;
    gap: 0.2rem;
    color: var(--comment-next-muted-color, #6b7687);
    font-size: 0.75rem;
    font-weight: 560;
  }

  .comment-next-comment-actions {
    display: flex;
    align-items: center;
    gap: 0.375rem;
    margin-top: 0.625rem;
  }

  .comment-next-comment-actions button {
    display: inline-flex;
    align-items: center;
    justify-content: center;
    min-width: 2.25rem;
    height: 1.75rem;
    gap: 0.25rem;
    box-sizing: border-box;
    padding: 0 0.5rem;
    border: 1px solid transparent;
    border-radius: 0.5rem;
    background: transparent;
    color: var(--comment-next-muted-color, #6b7687);
    cursor: pointer;
    font: inherit;
    font-size: 0.8125rem;
    font-weight: 650;
    transition:
      background-color 140ms ease,
      color 140ms ease,
      transform 140ms ease;
  }

  .comment-next-comment-actions button:hover,
  .comment-next-comment-action-active {
    background: var(--comment-next-control-hover-bg-color, #eef2f4);
    color: var(--comment-next-primary-color, rgb(59, 130, 246));
  }

  .comment-next-comment-actions button:active {
    transform: translateY(1px);
  }

  .comment-next-replies {
    position: relative;
    margin-top: 0.875rem;
    padding: 0.125rem 0 0.125rem 0.875rem;
    border-left: 2px solid var(--comment-next-border-subtle-color, #e1e8ef);
  }

  @media (max-width: 640px) {
    .comment-next-comment-item {
      grid-template-columns: 2rem minmax(0, 1fr);
      gap: 0.625rem;
      padding: 1rem 0;
    }

    .comment-next-comment-author {
      max-width: 9rem;
    }
  }
</style>
