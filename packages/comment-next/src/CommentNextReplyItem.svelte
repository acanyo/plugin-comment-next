<script lang="ts">
import { onDestroy } from 'svelte';
import { resolveCommentBadges } from './badges/resolve';
import CommentNextAvatar from './CommentNextAvatar.svelte';
import CommentNextBadge from './CommentNextBadge.svelte';
import CommentNextCommentMeta from './CommentNextCommentMeta.svelte';
import CommentNextContent from './CommentNextContent.svelte';
import CommentNextHipAuthor from './CommentNextHipAuthor.svelte';
import CommentNextIcon from './CommentNextIcon.svelte';
import CommentNextReactionButton from './CommentNextReactionButton.svelte';
import CommentNextReportButton from './CommentNextReportButton.svelte';
import { upvoteCommentTarget } from './services/comments';
import type {
  CommentNextAvatarConfig,
  CommentNextEmoteConfig,
  CommentNextReactionConfig,
  CommentNextReportConfig,
} from './services/config';
import { hasAuthorIdentity } from './services/interaction-plus';
import type {
  CommentNextBadgeConfig,
  CommentNextComment,
} from './types/comment';
import { commentAnchor, getCommentAnchorHref } from './utils/comment-anchor';
import {
  COMMENT_NEXT_UPVOTED_REPLIES_KEY,
  hasRememberedUpvote,
  rememberedUpvoteCount,
  rememberUpvote,
  rememberUpvoteCount,
} from './utils/upvote';

const {
  baseUrl = '',
  reply,
  badgeConfig = {},
  replyToName = '',
  replying = false,
  demoData = false,
  showCommenterDevice = true,
  enableImageLightbox = true,
  aiMentionName = '',
  loggedIn = false,
  reactionConfig,
  reportConfig,
  emoteConfig,
  avatarConfig,
  onReply = () => {},
}: {
  baseUrl?: string;
  reply: CommentNextComment;
  badgeConfig?: CommentNextBadgeConfig;
  replyToName?: string;
  replying?: boolean;
  demoData?: boolean;
  showCommenterDevice?: boolean;
  enableImageLightbox?: boolean;
  aiMentionName?: string;
  loggedIn?: boolean;
  reactionConfig?: CommentNextReactionConfig;
  reportConfig?: CommentNextReportConfig;
  emoteConfig?: CommentNextEmoteConfig;
  avatarConfig?: CommentNextAvatarConfig;
  onReply?: (reply: CommentNextComment) => void;
} = $props();

let previousReplyId = $state('');
let upvoted = $state(false);
let upvotes = $state(0);
let linkCopied = $state(false);
let linkCopyTimer: number | undefined;

const badges = $derived(resolveCommentBadges(reply, { config: badgeConfig }));
const useHipAuthor = $derived(hasAuthorIdentity(reply.author));
const replyReactionEnabled = $derived(
  Boolean(reactionConfig?.enabled && reactionConfig.replyEnabled !== false)
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
    linkCopied = false;
  }
});

onDestroy(() => {
  if (linkCopyTimer !== undefined) {
    window.clearTimeout(linkCopyTimer);
  }
});

async function handleAnchorClick(event: MouseEvent) {
  if (
    event.metaKey ||
    event.ctrlKey ||
    event.shiftKey ||
    event.altKey ||
    event.button !== 0 ||
    typeof window === 'undefined' ||
    !navigator.clipboard?.writeText
  ) {
    return;
  }

  const href = getCommentAnchorHref('reply', reply.id);
  event.preventDefault();
  try {
    window.history.replaceState(null, '', href);
    await navigator.clipboard.writeText(
      new URL(href, window.location.href).href
    );
    linkCopied = true;
    if (linkCopyTimer !== undefined) {
      window.clearTimeout(linkCopyTimer);
    }
    linkCopyTimer = window.setTimeout(() => {
      linkCopied = false;
      linkCopyTimer = undefined;
    }, 1800);
  } catch {
    window.location.hash = href;
  }
}

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

<article
  use:commentAnchor={{ kind: 'reply', id: reply.id }}
  class="comment-next-reply-item"
>
  <div class="comment-next-reply-avatar">
    {#if useHipAuthor && reply.author.identity}
      <CommentNextHipAuthor
        identity={reply.author.identity}
        avatarConfig={avatarConfig}
        displayName={reply.author.displayName}
        avatar={reply.author.avatar}
        variant="avatar"
        size={29}
      />
    {:else}
      <CommentNextAvatar config={avatarConfig} seed={reply.author.name} src={reply.author.avatar} alt={reply.author.displayName} size={29} />
    {/if}
  </div>
  <div class="comment-next-reply-main">
    <header class="comment-next-reply-meta">
      {#if useHipAuthor && reply.author.identity}
        <span class="comment-next-reply-author-hip">
          <CommentNextHipAuthor
            identity={reply.author.identity}
            avatarConfig={avatarConfig}
            displayName={reply.author.displayName}
            variant="identity"
          />
        </span>
      {:else}
        <span class="comment-next-reply-author" title={reply.author.displayName}>{reply.author.displayName}</span>
      {/if}
      {#if badges.length}
        <span class="comment-next-reply-badges">
          {#each badges as badge}
            <CommentNextBadge {badge} />
          {/each}
        </span>
      {/if}
      {#if reply.top || reply.featured}
        <span class="comment-next-reply-flags" aria-label="回复状态">
          {#if reply.top}
            <span class="comment-next-reply-flag comment-next-reply-flag-pinned">
              <CommentNextIcon name="pin" size={11} />
              置顶
            </span>
          {/if}
          {#if reply.featured}
            <span class="comment-next-reply-flag comment-next-reply-flag-featured">
              <CommentNextIcon name="star" size={11} />
              精选
            </span>
          {/if}
        </span>
      {/if}
      {#if reply.approved === false}
        <span class="comment-next-reply-state">待审核</span>
      {/if}
      {#if replyToName}
        <span class="comment-next-reply-to">回复 <strong>@{replyToName}</strong></span>
      {/if}
    </header>

    <CommentNextCommentMeta entry={reply} {showCommenterDevice} />

    <CommentNextContent
      content={reply.content}
      {aiMentionName}
      {enableImageLightbox}
      allowedLottieHosts={emoteConfig?.allowedHosts}
    />

    <div class="comment-next-reply-actions">
      {#if replyReactionEnabled}
        <CommentNextReactionButton
          {baseUrl}
          targetType="REPLY"
          name={reply.id}
          {loggedIn}
          config={reactionConfig}
          {demoData}
        />
      {:else}
        <button
          class:comment-next-reply-action-liked={upvoted}
          type="button"
          aria-label="点赞回复"
          aria-pressed={upvoted}
          onclick={handleUpvote}
        >
          <CommentNextIcon name={upvoted ? "heartFill" : "heart"} size={13} />
          {upvotes}
        </button>
      {/if}
      <button type="button" aria-expanded={replying} onclick={() => onReply(reply)}>
        <CommentNextIcon name="reply" size={13} />
        回复
      </button>
      <CommentNextReportButton
        {baseUrl}
        targetType="REPLY"
        name={reply.id}
        {loggedIn}
        config={reportConfig}
        {demoData}
        compact
      />
      <a
        class="comment-next-reply-anchor"
        class:comment-next-reply-anchor-copied={linkCopied}
        href={getCommentAnchorHref('reply', reply.id)}
        aria-label={linkCopied ? '已复制回复链接' : '复制并定位到此回复'}
        title={linkCopied ? '已复制链接' : '复制回复链接'}
        onclick={handleAnchorClick}
      >
        <CommentNextIcon name={linkCopied ? 'check' : 'link'} size={13} />
        {linkCopied ? '已复制' : '链接'}
      </a>
    </div>
  </div>
</article>

<style>
  .comment-next-reply-item {
    --reply-avatar-size: var(--comment-next-reply-avatar-size, 1.8125rem);
    --reply-action-height: 1.75rem;
    display: grid;
    grid-template-columns: var(--reply-avatar-size) minmax(0, 1fr);
    column-gap: 0.5625rem;
    min-width: 0;
    padding-block-start: var(--comment-next-style-reply-space, 0.625rem);
  }

  .comment-next-reply-avatar {
    --comment-next-avatar-layout-size: var(--reply-avatar-size);
    position: relative;
    align-self: start;
    padding-block-start: 0.125rem;
    line-height: 0;
  }

  .comment-next-reply-avatar::before {
    --reply-indent: var(--comment-next-reply-indent, 0.875rem);
    content: '';
    position: absolute;
    inset-block-end: calc(var(--reply-avatar-size) / 2);
    inset-inline-start: calc(-1.5px - var(--reply-indent));
    inline-size: calc(var(--reply-indent) - 0.125rem);
    block-size: 0.375rem;
    border-inline-start: 1.5px solid var(--comment-next-thread-line-color, var(--comment-next-reply-guide-color, #c8d8ee));
    border-block-end: 1.5px solid var(--comment-next-thread-line-color, var(--comment-next-reply-guide-color, #c8d8ee));
    border-end-start-radius: 0.375rem;
    pointer-events: none;
  }

  .comment-next-reply-avatar :global(hip-user-avatar) {
    --hip-avatar-size: var(--reply-avatar-size);
  }

  .comment-next-reply-main {
    min-width: 0;
  }

  .comment-next-reply-main > :global(.comment-next-comment-content) {
    font-size: 0.875rem;
    line-height: 1.6;
  }

  .comment-next-reply-meta {
    display: flex;
    flex-wrap: wrap;
    align-items: center;
    gap: 0.2rem 0.375rem;
    min-width: 0;
    margin-block-end: 0.125rem;
  }

  .comment-next-reply-author {
    --at-apply: max-w-[min(10rem,100%)] overflow-hidden text-ellipsis whitespace-nowrap text-sm text-[var(--comment-next-text-color,#172033)] font-[690] leading-[1.25rem];
  }

  .comment-next-reply-author-hip {
    --at-apply: inline-flex max-w-full min-w-0 items-center overflow-visible whitespace-normal text-sm text-[var(--comment-next-text-color,#172033)] font-[690] leading-[1.25rem];
  }

  .comment-next-reply-badges,
  .comment-next-reply-flags {
    display: inline-flex;
    flex-wrap: wrap;
    align-items: center;
    gap: 0.2rem;
    max-width: 100%;
  }

  .comment-next-reply-flag {
    --at-apply: inline-flex h-5 items-center gap-0.5 rounded-md px-1.5 text-[0.6875rem] font-[650] leading-none;
  }

  .comment-next-reply-flag-pinned {
    --at-apply: bg-[var(--comment-next-pinned-pill-bg-color,rgb(254_243_199))] text-[var(--comment-next-pinned-text-color,rgb(146_64_14))];
  }

  .comment-next-reply-flag-featured {
    --at-apply: bg-[var(--comment-next-featured-pill-bg-color,rgb(204_251_241))] text-[var(--comment-next-featured-text-color,rgb(15_118_110))];
  }

  .comment-next-reply-to {
    --at-apply: text-xs text-[var(--comment-next-muted-color,#6b7687)];
    min-width: 0;
    overflow-wrap: anywhere;
  }

  .comment-next-reply-state {
    --at-apply: inline-flex h-5 items-center rounded-md border border-solid px-1.5 text-[0.6875rem] font-[600] leading-none;
    border-color: color-mix(in srgb, #d97706 28%, transparent);
    background: color-mix(in srgb, #f59e0b 12%, transparent);
    color: #b45309;
  }

  .comment-next-reply-to strong {
    --at-apply: text-[var(--comment-next-link-color,var(--comment-next-primary-color,rgb(59,130,246)))] font-[620];
  }

  .comment-next-reply-actions {
    display: flex;
    flex-wrap: wrap;
    align-items: center;
    gap: 0.125rem 0.25rem;
    margin-block-start: 0.25rem;
    margin-inline-start: -0.35rem;
  }

  .comment-next-reply-actions > button,
  .comment-next-reply-anchor,
  .comment-next-reply-actions :global(.comment-next-report-button),
  .comment-next-reply-actions :global(.comment-next-target-reaction-button) {
    box-sizing: border-box;
    min-height: var(--reply-action-height);
    padding-inline: 0.35rem;
    border-radius: 0.375rem;
    font-size: 0.75rem;
    font-weight: 600;
    font-variant-numeric: tabular-nums;
    transition: background-color 140ms ease, color 140ms ease, opacity 140ms ease;
  }

  .comment-next-reply-anchor-copied,
  .comment-next-reply-anchor-copied:hover {
    color: var(--comment-next-primary-color, #3b82f6);
  }

  @media (hover: hover) and (pointer: fine) {
    .comment-next-reply-anchor,
    .comment-next-reply-actions :global(.comment-next-report-button) {
      opacity: 0.45;
    }

    .comment-next-reply-item:hover .comment-next-reply-anchor,
    .comment-next-reply-item:hover .comment-next-reply-actions :global(.comment-next-report-button),
    .comment-next-reply-item:focus-within .comment-next-reply-anchor,
    .comment-next-reply-item:focus-within .comment-next-reply-actions :global(.comment-next-report-button),
    .comment-next-reply-anchor-copied {
      opacity: 1;
    }
  }

  .comment-next-reply-actions > button,
  .comment-next-reply-anchor {
    display: inline-flex;
    align-items: center;
    gap: 0.25rem;
    padding-block: 0;
    border: 0;
    background: transparent;
    color: var(--comment-next-muted-color, #6b7687);
    cursor: pointer;
    font-family: inherit;
    line-height: 1.2;
    text-decoration: none;
  }

  .comment-next-reply-actions > button:hover,
  .comment-next-reply-actions > button[aria-expanded='true'],
  .comment-next-reply-anchor:hover {
    background: var(--comment-next-control-hover-bg-color, #eef2f4);
    color: var(--comment-next-primary-color, #3b82f6);
  }

  .comment-next-reply-actions :global(.comment-next-report-button:hover:not(:disabled)) {
    background: var(--comment-next-control-hover-bg-color, #eef2f4);
  }

  .comment-next-reply-actions > button:focus-visible,
  .comment-next-reply-anchor:focus-visible,
  .comment-next-reply-actions :global(.comment-next-report-button:focus-visible),
  .comment-next-reply-actions :global(.comment-next-target-reaction-button:focus-visible) {
    outline: 2px solid var(--comment-next-focus-border-color, #3b82f6);
    outline-offset: 1px;
  }

  .comment-next-reply-actions > button.comment-next-reply-action-liked {
    color: var(--comment-next-like-color, #ef4444);
  }

  @container comment-next-thread (max-width: 36rem) {
    .comment-next-reply-item {
      --reply-action-height: 2rem;
      column-gap: 0.4375rem;
    }

    .comment-next-reply-meta {
      gap: 0.1875rem 0.3rem;
    }
  }
</style>
