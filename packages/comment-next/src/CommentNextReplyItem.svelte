<script lang="ts">
import { resolveCommentBadges } from './badges/resolve';
import CommentNextAvatar from './CommentNextAvatar.svelte';
import CommentNextBadge from './CommentNextBadge.svelte';
import CommentNextContent from './CommentNextContent.svelte';
import CommentNextEnvironmentTags from './CommentNextEnvironmentTags.svelte';
import CommentNextIcon from './CommentNextIcon.svelte';
import { upvoteCommentTarget } from './services/comments';
import type {
  CommentNextBadgeConfig,
  CommentNextComment,
} from './types/comment';
import { formatRelativeTime } from './utils/date';
import {
  COMMENT_NEXT_UPVOTED_REPLIES_KEY,
  hasRememberedUpvote,
  rememberUpvote,
  rememberedUpvoteCount,
  rememberUpvoteCount,
} from './utils/upvote';
import { getCommentEnvironmentTags } from './utils/user-agent';

const {
  baseUrl = '',
  reply,
  badgeConfig = {},
  replyToName = '',
  demoData = false,
  showCommenterDevice = true,
  onReply = () => {},
}: {
  baseUrl?: string;
  reply: CommentNextComment;
  badgeConfig?: CommentNextBadgeConfig;
  replyToName?: string;
  demoData?: boolean;
  showCommenterDevice?: boolean;
  onReply?: (reply: CommentNextComment) => void;
} = $props();

let previousReplyId = $state('');
let upvoted = $state(false);
let upvotes = $state(0);

const badges = $derived(resolveCommentBadges(reply, { config: badgeConfig }));
const environmentTags = $derived(
  getCommentEnvironmentTags({
    userAgent: reply.userAgent,
  })
);

$effect(() => {
  if (previousReplyId !== reply.id) {
    previousReplyId = reply.id;
    const rememberedUpvote = hasRememberedUpvote(
      COMMENT_NEXT_UPVOTED_REPLIES_KEY,
      reply.id
    );
    upvoted = rememberedUpvote;
    upvotes = Math.max(
      reply.stats?.upvotes ?? 0,
      rememberedUpvoteCount(COMMENT_NEXT_UPVOTED_REPLIES_KEY, reply.id) ??
        (rememberedUpvote ? 1 : 0)
    );
  }
});

async function handleUpvote() {
  if (upvoted) {
    return;
  }

  upvoted = true;
  upvotes += 1;

  try {
    if (!demoData) {
      await upvoteCommentTarget({
        baseUrl,
        name: reply.id,
        plural: 'replies',
      });
    }
    rememberUpvote(COMMENT_NEXT_UPVOTED_REPLIES_KEY, reply.id);
    rememberUpvoteCount(COMMENT_NEXT_UPVOTED_REPLIES_KEY, reply.id, upvotes);
  } catch (error) {
    const rememberedUpvote = hasRememberedUpvote(
      COMMENT_NEXT_UPVOTED_REPLIES_KEY,
      reply.id
    );
    upvoted = rememberedUpvote;
    upvotes = Math.max(
      reply.stats?.upvotes ?? 0,
      rememberedUpvoteCount(COMMENT_NEXT_UPVOTED_REPLIES_KEY, reply.id) ??
        (rememberedUpvote ? 1 : 0)
    );
    console.warn('Failed to upvote reply', error);
  }
}
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
      {#if reply.approved === false}
        <span class="comment-next-reply-state">待审核</span>
      {/if}
    </header>

    <div class="comment-next-reply-submeta">
      {#if reply.creationTime}
        <time>{formatRelativeTime(reply.creationTime)}</time>
      {/if}
      {#if showCommenterDevice}
        <CommentNextEnvironmentTags tags={environmentTags} compact />
      {/if}
      {#if replyToName}
        <span>回复 <strong>@{replyToName}</strong>：</span>
      {/if}
    </div>

    <CommentNextContent content={reply.content} />

    <div class="comment-next-reply-actions">
      <button
        class:comment-next-reply-action-liked={upvoted}
        type="button"
        aria-label="点赞回复"
        onclick={handleUpvote}
      >
        <CommentNextIcon name={upvoted ? "heartFill" : "heart"} size={13} />
        {upvotes}
      </button>
      <button type="button" onclick={() => onReply(reply)}>
        <CommentNextIcon name="reply" size={13} />
        回复
      </button>
    </div>
  </div>
</article>

<style>
  .comment-next-reply-item {
    --at-apply: grid grid-cols-[1.75rem_minmax(0,1fr)] gap-2.5 px-0 pb-0 pt-3;
  }

  .comment-next-reply-avatar {
    --at-apply: pt-0.5;
  }

  .comment-next-reply-main {
    --at-apply: min-w-0 border-b border-b-solid [border-bottom-color:var(--comment-next-border-subtle-color,#e7ecf2)] pb-3;
  }

  .comment-next-reply-item:last-child .comment-next-reply-main {
    --at-apply: border-b-0 pb-0;
  }

  .comment-next-reply-meta {
    --at-apply: mb-[0.05rem] flex min-w-0 flex-wrap items-center gap-1.5;
  }

  .comment-next-reply-author {
    --at-apply: max-w-40 overflow-hidden text-ellipsis whitespace-nowrap text-sm text-[var(--comment-next-text-color,#172033)] font-[760];
  }

  .comment-next-reply-submeta,
  .comment-next-reply-state {
    --at-apply: text-xs text-[var(--comment-next-muted-color,#6b7687)];
  }

  .comment-next-reply-submeta {
    --at-apply: mb-[0.4rem] flex flex-wrap gap-[0.45rem] font-[560];
  }

  .comment-next-reply-submeta strong {
    --at-apply: text-[var(--comment-next-primary-color,rgb(59,130,246))] font-[650];
  }

  .comment-next-reply-actions {
    --at-apply: mt-[0.45rem] flex gap-3;
  }

  .comment-next-reply-actions button {
    --at-apply: inline-flex h-[1.375rem] cursor-pointer items-center gap-1 border-0 rounded-none bg-transparent p-0 text-xs text-[var(--comment-next-muted-color,#6b7687)] font-[650] font-inherit;
  }

  .comment-next-reply-actions button:hover {
    --at-apply: text-[var(--comment-next-primary-color,rgb(59,130,246))];
  }

  .comment-next-reply-actions .comment-next-reply-action-liked,
  .comment-next-reply-actions .comment-next-reply-action-liked:hover {
    --at-apply: text-[var(--comment-next-like-color,#ef4444)];
  }
</style>
