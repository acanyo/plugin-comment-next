<script lang="ts">
import CommentNextIcon from './CommentNextIcon.svelte';

const {
  replyToName = '',
  busy = false,
  onCancel = () => {},
}: {
  replyToName?: string;
  busy?: boolean;
  onCancel?: () => void;
} = $props();
</script>

<header class="comment-next-reply-context">
  <span class="comment-next-reply-context-name">
    <CommentNextIcon name="reply" size={15} />
    {#if replyToName}
      回复
      <strong title={replyToName}>@{replyToName}</strong>
    {:else}
      回复评论
    {/if}
  </span>
  <button type="button" aria-label="取消回复" title="取消回复" disabled={busy} onclick={onCancel}>
    <CommentNextIcon name="x" size={16} />
  </button>
</header>

<style>
  .comment-next-reply-context {
    display: flex;
    align-items: center;
    justify-content: space-between;
    min-width: 0;
    gap: 0.5rem;
    padding: 0.125rem 0.375rem 0.125rem var(--comment-next-composer-gutter);
    border-block-end: 1px solid var(--comment-next-composer-rule);
    border-start-start-radius: inherit;
    border-start-end-radius: inherit;
    background: var(--comment-next-composer-rail-bg);
    color: var(--comment-next-muted-color, #667085);
    font-size: 0.8125rem;
    line-height: 1.35;
  }

  .comment-next-reply-context-name {
    display: flex;
    align-items: center;
    gap: 0.4375rem;
    min-width: 0;
    white-space: nowrap;
  }

  .comment-next-reply-context-name strong {
    overflow: hidden;
    color: var(--comment-next-text-color, #172033);
    font-weight: 650;
    text-overflow: ellipsis;
  }

  .comment-next-reply-context button {
    display: grid;
    place-items: center;
    flex: none;
    width: var(--comment-next-composer-control-height);
    height: var(--comment-next-composer-control-height);
    padding: 0;
    border: 0;
    border-radius: var(--comment-next-composer-control-radius);
    background: transparent;
    color: inherit;
    cursor: pointer;
  }

  .comment-next-reply-context button:hover:not(:disabled) {
    background: var(--comment-next-composer-tint);
    color: var(--comment-next-text-color, #172033);
  }

  .comment-next-reply-context button:focus-visible {
    outline: 2px solid var(--comment-next-composer-accent);
    outline-offset: 1px;
  }

  .comment-next-reply-context button:disabled {
    cursor: not-allowed;
    opacity: 0.5;
  }
</style>
