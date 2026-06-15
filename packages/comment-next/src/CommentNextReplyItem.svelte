<script lang="ts">
  import { resolveCommentBadges } from "./badges/resolve";
  import CommentNextAvatar from "./CommentNextAvatar.svelte";
  import CommentNextBadge from "./CommentNextBadge.svelte";
  import CommentNextContent from "./CommentNextContent.svelte";
  import { formatCommentDate, formatRelativeTime } from "./utils/date";
  import type { CommentNextBadgeConfig, CommentNextComment } from "./types/comment";

  let {
    reply,
    badgeConfig = {},
  }: {
    reply: CommentNextComment;
    badgeConfig?: CommentNextBadgeConfig;
  } = $props();

  const badges = $derived(resolveCommentBadges(reply, { config: badgeConfig }));
</script>

<article class="comment-next-reply-item">
  <div class="comment-next-reply-avatar">
    <CommentNextAvatar src={reply.author.avatar} alt={reply.author.displayName} size={28} />
  </div>
  <div class="comment-next-reply-main">
    <header class="comment-next-reply-meta">
      <span class="comment-next-reply-author">{reply.author.displayName}</span>
      {#each badges as badge}
        <CommentNextBadge {badge} />
      {/each}
      {#if reply.creationTime}
        <time title={formatCommentDate(reply.creationTime)}>{formatRelativeTime(reply.creationTime)}</time>
      {/if}
      {#if reply.approved === false}
        <span class="comment-next-reply-state">待审核</span>
      {/if}
    </header>
    <CommentNextContent content={reply.content} />
  </div>
</article>

<style>
  .comment-next-reply-item {
    display: grid;
    grid-template-columns: 1.75rem minmax(0, 1fr);
    gap: 0.625rem;
    padding: 0.75rem 0 0;
  }

  .comment-next-reply-avatar {
    padding-top: 0.125rem;
  }

  .comment-next-reply-main {
    min-width: 0;
    padding-bottom: 0.75rem;
    border-bottom: 1px solid var(--comment-next-border-subtle-color, #e7ecf2);
  }

  .comment-next-reply-item:last-child .comment-next-reply-main {
    padding-bottom: 0;
    border-bottom: 0;
  }

  .comment-next-reply-meta {
    display: flex;
    align-items: center;
    min-width: 0;
    gap: 0.375rem;
    flex-wrap: wrap;
    margin-bottom: 0.25rem;
  }

  .comment-next-reply-author {
    max-width: 10rem;
    overflow: hidden;
    color: var(--comment-next-text-color, #172033);
    font-size: 0.875rem;
    font-weight: 760;
    text-overflow: ellipsis;
    white-space: nowrap;
  }

  .comment-next-reply-meta time,
  .comment-next-reply-state {
    color: var(--comment-next-muted-color, #6b7687);
    font-size: 0.75rem;
  }
</style>
