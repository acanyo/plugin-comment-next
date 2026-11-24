<script lang="ts">
import { untrack } from 'svelte';
import CommentNextAvatar from './CommentNextAvatar.svelte';
import CommentNextBadge from './CommentNextBadge.svelte';
import CommentNextCommentMeta from './CommentNextCommentMeta.svelte';
import CommentNextContent from './CommentNextContent.svelte';
import CommentNextHipAuthor from './CommentNextHipAuthor.svelte';
import CommentNextIcon from './CommentNextIcon.svelte';
import CommentNextReactionButton from './CommentNextReactionButton.svelte';
import CommentNextReplyComposer from './CommentNextReplyComposer.svelte';
import CommentNextReplyItem from './CommentNextReplyItem.svelte';
import CommentNextReportButton from './CommentNextReportButton.svelte';
import CommentNextTooltip from './CommentNextTooltip.svelte';
import { fetchReplyPage, upvoteCommentTarget } from './services/comments';
import type {
  CommentNextAiConfig,
  CommentNextAvatarConfig,
  CommentNextEmoteConfig,
  CommentNextReactionConfig,
  CommentNextReportConfig,
  CommentNextSecurityConfig,
  CommentNextUploadConfig,
} from './services/config';
import { hasAuthorIdentity } from './services/interaction-plus';
import type {
  CommentNextBadgeConfig,
  CommentNextBadge as CommentNextBadgeModel,
  CommentNextComment,
  CommentNextCommentPage,
  CommentNextPageInfo,
} from './types/comment';
import type { CommentNextEmotePack } from './types/emote';
import { commentAnchor, getCommentAnchorHref } from './utils/comment-anchor';
import {
  COMMENT_NEXT_UPVOTED_COMMENTS_KEY,
  hasRememberedUpvote,
  rememberedUpvoteCount,
  rememberUpvote,
  rememberUpvoteCount,
} from './utils/upvote';

const {
  baseUrl = '',
  comment,
  badges = [],
  badgeConfig = {},
  loggedIn = false,
  allowAnonymous = true,
  showCaptcha = false,
  captchaType = 'ALPHANUMERIC',
  captchaConfig,
  demoData = false,
  replySize = 10,
  linkedReplyPage,
  showCommenterDevice = true,
  enableImageLightbox = true,
  aiConfig,
  reactionConfig,
  reportConfig,
  aiMentionName = '',
  uploadConfig,
  emoteConfig,
  avatarConfig,
  emotePacks = [],
}: {
  baseUrl?: string;
  comment: CommentNextComment;
  badges?: CommentNextBadgeModel[];
  badgeConfig?: CommentNextBadgeConfig;
  loggedIn?: boolean;
  allowAnonymous?: boolean;
  showCaptcha?: boolean;
  captchaType?: NonNullable<CommentNextSecurityConfig['captcha']>['type'];
  captchaConfig?: CommentNextSecurityConfig['captcha'];
  demoData?: boolean;
  replySize?: number;
  linkedReplyPage?: CommentNextCommentPage;
  showCommenterDevice?: boolean;
  enableImageLightbox?: boolean;
  aiConfig?: CommentNextAiConfig;
  reactionConfig?: CommentNextReactionConfig;
  reportConfig?: CommentNextReportConfig;
  aiMentionName?: string;
  uploadConfig?: CommentNextUploadConfig;
  emoteConfig?: CommentNextEmoteConfig;
  avatarConfig?: CommentNextAvatarConfig;
  emotePacks?: CommentNextEmotePack[];
} = $props();

let upvoted = $state(false);
let upvotes = $state(0);
let previousCommentId = $state('');
let replyComposerOpen = $state(false);
let quoteReply = $state<CommentNextComment | undefined>();
let replies = $state<CommentNextComment[]>(
  untrack(() => comment.replies ?? [])
);
let replyPage = $state<CommentNextPageInfo | undefined>(
  untrack(() => comment.replyPage)
);
let replyAuthors = $state<Record<string, string>>(
  buildReplyAuthorMap(untrack(() => comment.replies ?? []))
);
let repliesLoading = $state(false);
let repliesError = $state('');

const shownReplies = $derived.by(() => {
  if (!linkedReplyPage) return replies;
  const loadedNames = new Set(replies.map((reply) => reply.id));
  return [
    ...replies,
    ...linkedReplyPage.items.filter((reply) => !loadedNames.has(reply.id)),
  ];
});
const replyCount = $derived(
  replyPage?.total ??
    linkedReplyPage?.total ??
    comment.stats?.replies ??
    replies.length
);
const hasMoreReplies = $derived(
  replyCount > shownReplies.length ||
    (!linkedReplyPage && Boolean(replyPage?.hasNext))
);
const remainingReplyCount = $derived(
  Math.max(replyCount - shownReplies.length, 0)
);
const commentReactionEnabled = $derived(
  Boolean(reactionConfig?.enabled && reactionConfig.commentEnabled !== false)
);
const useHipAuthor = $derived(hasAuthorIdentity(comment.author));

$effect(() => {
  if (previousCommentId !== comment.id) {
    previousCommentId = comment.id;
    const rememberedUpvote = hasRememberedUpvote(
      COMMENT_NEXT_UPVOTED_COMMENTS_KEY,
      comment.id
    );
    upvoted = rememberedUpvote;
    upvotes = Math.max(
      comment.stats?.upvotes ?? 0,
      rememberedUpvoteCount(COMMENT_NEXT_UPVOTED_COMMENTS_KEY, comment.id) ??
        (rememberedUpvote ? 1 : 0)
    );
    replyComposerOpen = false;
    quoteReply = undefined;
    replies = comment.replies ?? [];
    replyPage = comment.replyPage;
    replyAuthors = buildReplyAuthorMap(replies);
    repliesError = '';
  }
});

async function handleLocalUpvote() {
  if (upvoted) {
    return;
  }

  upvoted = true;
  upvotes += 1;

  try {
    if (!demoData) {
      await upvoteCommentTarget({
        baseUrl,
        name: comment.id,
        plural: 'comments',
      });
    }
    rememberUpvote(COMMENT_NEXT_UPVOTED_COMMENTS_KEY, comment.id);
    rememberUpvoteCount(COMMENT_NEXT_UPVOTED_COMMENTS_KEY, comment.id, upvotes);
  } catch (error) {
    const rememberedUpvote = hasRememberedUpvote(
      COMMENT_NEXT_UPVOTED_COMMENTS_KEY,
      comment.id
    );
    upvoted = rememberedUpvote;
    upvotes = Math.max(
      comment.stats?.upvotes ?? 0,
      rememberedUpvoteCount(COMMENT_NEXT_UPVOTED_COMMENTS_KEY, comment.id) ??
        (rememberedUpvote ? 1 : 0)
    );
    console.warn('Failed to upvote comment', error);
  }
}

function handleReplyAction() {
  if (repliesLoading) {
    return;
  }

  openCommentReply();
}

function openCommentReply() {
  if (replyComposerOpen && !quoteReply) {
    closeReplyComposer();
    return;
  }

  quoteReply = undefined;
  replyComposerOpen = true;
}

function openQuoteReply(reply: CommentNextComment) {
  if (replyComposerOpen && quoteReply?.id === reply.id) {
    closeReplyComposer();
    return;
  }

  rememberReplyAuthor(reply);
  quoteReply = reply;
  replyComposerOpen = true;
}

function closeReplyComposer() {
  replyComposerOpen = false;
  quoteReply = undefined;
}

function handleReplyCreated(reply?: CommentNextComment) {
  if (reply?.id) {
    const existed = replies.some((item) => item.id === reply.id);
    replies = upsertReply(replies, reply);
    replyAuthors = {
      ...replyAuthors,
      ...buildReplyAuthorMap(replies),
      [reply.id]: reply.author.displayName,
    };
    if (!existed) {
      replyPage = incrementReplyTotal(replyPage, replies.length);
    }
  }

  closeReplyComposer();
  void reloadReplies();
  window.setTimeout(() => void reloadReplies(), 1800);
  window.setTimeout(() => void reloadReplies(), 5200);
}

function resolveReplyToName(reply: CommentNextComment): string {
  if (!reply.quoteReplyId) {
    return comment.author.displayName;
  }

  return (
    reply.replyToName ||
    replyAuthors[reply.quoteReplyId] ||
    shownReplies.find((item) => item.id === reply.quoteReplyId)?.author
      .displayName ||
    ''
  );
}

function rememberReplyAuthor(reply: CommentNextComment) {
  if (!reply.id || !reply.author.displayName) {
    return;
  }

  replyAuthors = {
    ...replyAuthors,
    [reply.id]: reply.author.displayName,
  };
}

function buildReplyAuthorMap(
  items: CommentNextComment[]
): Record<string, string> {
  return items.reduce<Record<string, string>>((result, item) => {
    if (item.id && item.author.displayName) {
      result[item.id] = item.author.displayName;
    }
    return result;
  }, {});
}

function upsertReply(
  items: CommentNextComment[],
  reply: CommentNextComment
): CommentNextComment[] {
  if (items.some((item) => item.id === reply.id)) {
    return items.map((item) => (item.id === reply.id ? reply : item));
  }

  return [...items, reply];
}

function incrementReplyTotal(
  pageInfo: CommentNextPageInfo | undefined,
  fallbackTotal: number
): CommentNextPageInfo {
  if (!pageInfo) {
    return {
      page: 1,
      size: replySize,
      total: fallbackTotal,
      totalPages: 1,
      hasNext: false,
      hasPrevious: false,
    };
  }

  const total = Math.max(pageInfo.total + 1, fallbackTotal);
  return {
    ...pageInfo,
    total,
    totalPages: Math.max(Math.ceil(total / Math.max(pageInfo.size, 1)), 1),
  };
}

async function loadRemainingReplies() {
  if (repliesLoading || !hasMoreReplies) {
    return;
  }

  let nextPage = replyPage ? replyPage.page + 1 : 1;
  let append = Boolean(replyPage);
  let shouldContinue = true;

  while (shouldContinue) {
    const loadedPage = await loadReplies({
      page: nextPage,
      append,
    });

    if (!loadedPage?.hasNext) {
      shouldContinue = false;
      continue;
    }

    nextPage = loadedPage.page + 1;
    append = true;
  }
}

async function reloadReplies() {
  await loadReplies({ page: 1, append: false });
}

async function loadReplies({
  page,
  append,
}: {
  page: number;
  append: boolean;
}): Promise<CommentNextPageInfo | undefined> {
  try {
    repliesLoading = true;
    repliesError = '';

    const data = await fetchReplyPage({
      baseUrl,
      commentName: comment.id,
      page,
      size: replySize,
    });

    replies = append ? [...replies, ...data.items] : data.items;
    replyAuthors = {
      ...replyAuthors,
      ...buildReplyAuthorMap(replies),
    };
    replyPage = {
      page: data.page,
      size: data.size,
      total: data.total,
      totalPages: data.totalPages,
      hasNext: data.hasNext,
      hasPrevious: data.hasPrevious,
    };
    return replyPage;
  } catch (error) {
    console.error(error);
    repliesError = '回复加载失败';
    return undefined;
  } finally {
    repliesLoading = false;
  }
}
</script>

<article
  use:commentAnchor={{ kind: 'comment', id: comment.id }}
  class="comment-next-comment-item"
>
  <div class="comment-next-comment-avatar">
    {#if useHipAuthor && comment.author.identity}
      <CommentNextHipAuthor
        identity={comment.author.identity}
        avatarConfig={avatarConfig}
        displayName={comment.author.displayName}
        avatar={comment.author.avatar}
        variant="avatar"
        size={38}
      />
    {:else if comment.author.website}
      <a href={comment.author.website} target="_blank" rel="noopener noreferrer nofollow ugc" aria-label={comment.author.displayName}>
        <CommentNextAvatar config={avatarConfig} seed={comment.author.name} src={comment.author.avatar} alt={comment.author.displayName} size={38} />
      </a>
    {:else}
      <CommentNextAvatar config={avatarConfig} seed={comment.author.name} src={comment.author.avatar} alt={comment.author.displayName} size={38} />
    {/if}
  </div>

  <div class="comment-next-comment-main">
    <header class="comment-next-comment-meta">
      {#if useHipAuthor && comment.author.identity}
        <span class="comment-next-comment-author-hip">
          <CommentNextHipAuthor
            identity={comment.author.identity}
            avatarConfig={avatarConfig}
            displayName={comment.author.displayName}
            variant="identity"
          />
        </span>
      {:else if comment.author.website}
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

      {#if badges.length}
        <span class="comment-next-comment-badges" aria-label="评论者徽章">
          {#each badges as badge}
            <CommentNextBadge {badge} />
          {/each}
        </span>
      {/if}

      {#if comment.top || comment.featured}
        <span class="comment-next-comment-flags" aria-label="评论状态">
          {#if comment.top}
            <CommentNextTooltip text="这条评论已被置顶" align="start">
              <span class="comment-next-comment-flag comment-next-comment-flag-pinned">
                <CommentNextIcon name="pin" size={12} />
                置顶
              </span>
            </CommentNextTooltip>
          {/if}
          {#if comment.featured}
            <CommentNextTooltip text="这条评论已被站长精选" align="start">
              <span class="comment-next-comment-flag comment-next-comment-flag-featured">
                <CommentNextIcon name="star" size={12} />
                精选
              </span>
            </CommentNextTooltip>
          {/if}
        </span>
      {/if}

      {#if comment.private}
        <CommentNextTooltip text="只有评论者和管理员可见" align="start">
          <span class="comment-next-comment-state">
            <CommentNextIcon name="lock" size={12} />
            私密
          </span>
        </CommentNextTooltip>
      {/if}

      {#if comment.approved === false}
        <span class="comment-next-comment-state">待审核</span>
      {/if}
    </header>

    <CommentNextCommentMeta entry={comment} {showCommenterDevice} />

    <CommentNextContent
      content={comment.content}
      {aiMentionName}
      {enableImageLightbox}
      allowedLottieHosts={emoteConfig?.allowedHosts}
    />

    <footer class="comment-next-comment-actions">
      {#if commentReactionEnabled}
        <CommentNextReactionButton
          {baseUrl}
          targetType="COMMENT"
          name={comment.id}
          {loggedIn}
          config={reactionConfig}
          {demoData}
        />
      {:else}
        <button
          class:comment-next-comment-action-liked={upvoted}
          type="button"
          aria-label="点赞"
          aria-pressed={upvoted}
          onclick={handleLocalUpvote}
        >
          <CommentNextIcon name={upvoted ? "heartFill" : "heart"} size={15} />
          <span>{upvotes}</span>
        </button>
      {/if}
      <button type="button" aria-label="回复" aria-expanded={replyComposerOpen && !quoteReply}
        disabled={repliesLoading} onclick={handleReplyAction}>
        <span class:comment-next-comment-action-loading={repliesLoading}>
          <CommentNextIcon name={repliesLoading ? "loader" : "reply"} size={15} />
        </span>
        <span>{repliesLoading ? "加载中" : "回复"}</span>
        {#if replyCount > 0}
          <span>{replyCount}</span>
        {/if}
      </button>
      <CommentNextReportButton
        {baseUrl}
        targetType="COMMENT"
        name={comment.id}
        {loggedIn}
        config={reportConfig}
        {demoData}
      />
      <a
        class="comment-next-comment-anchor"
        href={getCommentAnchorHref('comment', comment.id)}
        aria-label="跳转到此评论"
        title="评论链接"
      >
        <CommentNextIcon name="link" size={14} />
        <span>链接</span>
      </a>
    </footer>

    {#if replyComposerOpen && !quoteReply}
      <div class="comment-next-reply-composer-slot">
        <CommentNextReplyComposer
          {baseUrl}
          commentId={comment.id}
          {loggedIn}
          {allowAnonymous}
          {showCaptcha}
          {captchaType}
          {captchaConfig}
          {aiConfig}
          {uploadConfig}
          {emoteConfig}
          {avatarConfig}
          {emotePacks}
          replyToName={comment.author.displayName}
          onCancel={closeReplyComposer}
          onCreated={handleReplyCreated}
        />
      </div>
    {/if}

    {#if shownReplies.length || repliesLoading || repliesError || hasMoreReplies}
      <div class="comment-next-replies" role="group" aria-label={`回复 ${comment.author.displayName}`}>
        {#each shownReplies as reply (reply.id)}
          <CommentNextReplyItem
            {baseUrl}
            {reply}
            {badgeConfig}
            {demoData}
            {showCommenterDevice}
            {enableImageLightbox}
            {aiMentionName}
            {loggedIn}
            reactionConfig={reactionConfig}
            reportConfig={reportConfig}
            {emoteConfig}
            {avatarConfig}
            replyToName={resolveReplyToName(reply)}
            replying={replyComposerOpen && quoteReply?.id === reply.id}
            onReply={openQuoteReply}
          />
          {#if replyComposerOpen && quoteReply?.id === reply.id}
            <div class="comment-next-reply-composer-slot comment-next-reply-composer-slot-nested">
              <CommentNextReplyComposer
                {baseUrl}
                commentId={comment.id}
                {loggedIn}
                {allowAnonymous}
                {showCaptcha}
                {captchaType}
                {captchaConfig}
                {aiConfig}
                {uploadConfig}
                {emoteConfig}
                {avatarConfig}
                {emotePacks}
                replyToName={reply.author.displayName}
                quoteReply={reply}
                onCancel={closeReplyComposer}
                onCreated={handleReplyCreated}
              />
            </div>
          {/if}
        {/each}

        {#if repliesError}
          <div class="comment-next-replies-message">
            <span>{repliesError}</span>
            <button type="button" onclick={loadRemainingReplies}>重试</button>
          </div>
        {:else if repliesLoading}
          <div class="comment-next-replies-message">
            <span class="comment-next-replies-more-loading">
              <CommentNextIcon name="loader" size={14} />
            </span>
            <span>正在加载回复</span>
          </div>
        {:else if hasMoreReplies}
          <div class="comment-next-replies-message">
            <button type="button" onclick={loadRemainingReplies}>
              {shownReplies.length ? `展开剩余 ${remainingReplyCount} 条回复` : `查看 ${remainingReplyCount} 条回复`}
              <CommentNextIcon name="chevronDown" size={13} />
            </button>
          </div>
        {/if}
      </div>
    {/if}
  </div>
</article>

<style>
  .comment-next-comment-item {
    --comment-next-item-avatar-size: clamp(1.5rem, var(--comment-next-theme-avatar-size, 2.375rem), 3rem);
    position: relative;
    display: grid;
    gap: 0.6875rem;
    padding-block: var(--comment-next-style-item-space, 1rem);
    grid-template-columns: var(--comment-next-item-avatar-size) minmax(0, 1fr);
    box-sizing: border-box;
    max-width: 100%;
    min-width: 0;
  }

  :global([data-comment-list-style='cards']) .comment-next-comment-item {
    border: 1px solid var(--comment-next-border-subtle-color, #e7ecf2);
    border-radius: var(--comment-next-style-radius, var(--comment-next-radius-lg, 0.875rem));
    padding-inline: clamp(0.625rem, 2.2cqi, 1rem);
    background: var(--comment-next-surface-bg-color, Canvas);
  }

  .comment-next-comment-avatar {
    --comment-next-avatar-layout-size: var(--comment-next-item-avatar-size);
    --at-apply: min-w-0 justify-self-start self-start overflow-visible pt-0.5 leading-none;
  }

  .comment-next-comment-avatar a {
    --at-apply: inline-flex h-fit w-fit flex-none items-center justify-center rounded-full text-inherit no-underline leading-none;
  }

  .comment-next-comment-avatar :global(hip-user-avatar) {
    --hip-avatar-size: var(--comment-next-item-avatar-size);
  }

  .comment-next-comment-main {
    --at-apply: min-w-0;
    box-sizing: border-box;
    max-width: 100%;
  }

  .comment-next-comment-meta {
    display: flex;
    min-width: 0;
    flex-wrap: wrap;
    align-items: center;
    gap: 0.1875rem 0.4375rem;
  }

  .comment-next-comment-author {
    --at-apply: max-w-full overflow-hidden text-ellipsis whitespace-nowrap text-[0.9375rem] text-[var(--comment-next-text-color,#172033)] font-[720] no-underline;
    line-height: 1.4;
  }

  .comment-next-comment-author:hover {
    --at-apply: text-[var(--comment-next-primary-color,rgb(59,130,246))];
  }

  .comment-next-comment-author-hip {
    --at-apply: inline-flex max-w-full min-w-0 items-center overflow-visible whitespace-normal text-[0.9375rem] text-[var(--comment-next-text-color,#172033)] font-[720];
  }

  .comment-next-comment-author-hip :global(hip-user-identity) {
    max-width: 100%;
  }

  .comment-next-comment-badges,
  .comment-next-comment-flags {
    --at-apply: inline-flex min-w-0 flex-wrap items-center gap-1;
  }

  .comment-next-comment-flag {
    --at-apply: inline-flex h-5 items-center gap-0.5 rounded-md px-1.5 text-[0.6875rem] font-[650] leading-none;
  }

  .comment-next-comment-flag-pinned {
    --at-apply: bg-[var(--comment-next-pinned-pill-bg-color,rgb(254_243_199))] text-[var(--comment-next-pinned-text-color,rgb(146_64_14))];
  }

  .comment-next-comment-flag-featured {
    --at-apply: bg-[var(--comment-next-featured-pill-bg-color,rgb(204_251_241))] text-[var(--comment-next-featured-text-color,rgb(15_118_110))];
  }

  .comment-next-comment-state {
    --at-apply: inline-flex items-center gap-2 text-xs text-[var(--comment-next-muted-color,#6b7687)] font-[560];
  }

  .comment-next-comment-actions {
    --at-apply: flex flex-wrap items-center gap-x-1 gap-y-0.5;
    margin-block-start: 0.3125rem;
    margin-inline-start: -0.35rem;
  }

  .comment-next-comment-actions > button,
  .comment-next-comment-anchor,
  .comment-next-comment-actions :global(.comment-next-target-reaction-button),
  .comment-next-comment-actions :global(.comment-next-report-button) {
    --at-apply: inline-flex min-h-7 min-w-0 box-border cursor-pointer items-center justify-start gap-1 rounded-md border-0 bg-transparent px-[0.35rem] py-0 text-xs text-[var(--comment-next-muted-color,#6b7687)] font-[620] font-inherit;
    text-decoration: none;
    font-variant-numeric: tabular-nums;
  }

  .comment-next-comment-actions > button:enabled:hover,
  .comment-next-comment-actions > button[aria-expanded='true'],
  .comment-next-comment-anchor:hover,
  .comment-next-replies-message button:hover {
    color: var(--comment-next-primary-color, #2563eb);
    background: var(--comment-next-control-hover-bg-color, #eef2f4);
  }

  .comment-next-comment-actions > :is(button, a):focus-visible,
  .comment-next-comment-actions :global(.comment-next-target-reaction-button:focus-visible),
  .comment-next-comment-actions :global(.comment-next-report-button:focus-visible),
  .comment-next-replies-message button:focus-visible {
    outline: 2px solid var(--comment-next-primary-color, #2563eb);
    outline-offset: 1px;
  }

  .comment-next-comment-actions > button:disabled {
    --at-apply: cursor-wait opacity-76;
  }

  .comment-next-comment-actions .comment-next-comment-action-liked,
  .comment-next-comment-actions .comment-next-comment-action-liked:hover {
    --at-apply: text-[var(--comment-next-like-color,#ef4444)];
  }

  .comment-next-comment-action-loading {
    --at-apply: animate-spin;
  }

  .comment-next-replies {
    --comment-next-reply-indent: 0.875rem;
    --comment-next-reply-avatar-size: 1.8125rem;
    --comment-next-thread-line-color: var(--comment-next-border-subtle-color, #e3e9f0);
    --comment-next-thread-line-color: color-mix(in srgb, var(--comment-next-reply-guide-color, #c8d8ee) 60%, var(--comment-next-border-subtle-color, #e3e9f0));
    position: relative;
    margin-block-start: 0.5rem;
    padding-inline-start: var(--comment-next-reply-indent);
    border-inline-start: 1px dashed var(--comment-next-thread-line-color);
    box-sizing: border-box;
    max-width: 100%;
    min-width: 0;
  }

  .comment-next-reply-composer-slot {
    margin-block: 0.625rem;
    box-sizing: border-box;
    max-width: 100%;
    min-width: 0;
  }

  .comment-next-reply-composer-slot-nested {
    margin-inline-start: calc(var(--comment-next-reply-avatar-size) + 0.5625rem);
  }

  .comment-next-replies-message {
    --at-apply: flex flex-wrap items-center gap-2 py-1 text-xs text-[var(--comment-next-muted-color,#6b7687)];
  }

  .comment-next-replies-message button {
    --at-apply: inline-flex min-h-8 cursor-pointer items-center justify-center gap-[0.35rem] rounded-md border-0 bg-transparent px-1.5 py-0 text-xs text-[var(--comment-next-primary-color,#2563eb)] font-[650] font-inherit;
  }

  .comment-next-replies-more-loading {
    --at-apply: animate-spin;
  }

  @container comment-next-thread (max-width: 36rem) {
    .comment-next-comment-item {
      --comment-next-item-avatar-size: clamp(1.5rem, var(--comment-next-theme-avatar-size, 2.0625rem), 3rem);
      gap: 0.5625rem;
      padding-block: var(--comment-next-style-item-space, 0.875rem);
    }

    .comment-next-comment-actions > button,
    .comment-next-comment-anchor,
    .comment-next-comment-actions :global(.comment-next-target-reaction-button),
    .comment-next-comment-actions :global(.comment-next-report-button) {
      min-height: 2rem;
    }

    .comment-next-reply-composer-slot-nested {
      margin-inline-start: 0;
    }

    .comment-next-replies {
      --comment-next-reply-indent: 0.625rem;
      --comment-next-reply-avatar-size: 1.625rem;
    }
  }

  @media (prefers-reduced-motion: reduce) {
    .comment-next-replies-message button {
      --at-apply: transition-none;
    }

    .comment-next-comment-action-loading,
    .comment-next-replies-more-loading {
      --at-apply: animate-none;
    }
  }
</style>
