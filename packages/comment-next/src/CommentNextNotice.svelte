<script lang="ts">
import CommentNextIcon from './CommentNextIcon.svelte';

type CommentNextNoticeVariant = 'error' | 'success' | 'info';

const {
  message = '',
  variant = 'info',
  quiet = false,
}: {
  message?: string;
  variant?: CommentNextNoticeVariant;
  quiet?: boolean;
} = $props();

const icons = { success: 'circleCheck', info: 'loader', error: 'circleAlert' };
</script>

{#if message}
  <p
    class:comment-next-notice-error={variant === 'error'}
    class:comment-next-notice-success={variant === 'success'}
    class:comment-next-notice-info={variant === 'info'}
    class:comment-next-notice-quiet={quiet}
    class="comment-next-notice"
    role={variant === 'error' ? 'alert' : 'status'}
  >
    <CommentNextIcon name={icons[variant]} size={16} />
    <span>{message}</span>
  </p>
{/if}

<style>
  .comment-next-notice {
    display: flex;
    align-items: center;
    gap: 0.4375rem;
    margin: 0.625rem var(--comment-next-composer-gutter) 0;
    padding: 0.4375rem 0.625rem;
    border-radius: 0.5rem;
    background: var(--comment-next-notice-bg);
    color: var(--comment-next-notice-color);
    font-size: 0.8125rem;
    font-weight: 550;
    line-height: 1.45;
    overflow-wrap: anywhere;
  }

  .comment-next-notice:last-child {
    margin-bottom: 0.5rem;
  }

  .comment-next-notice-error {
    --comment-next-notice-color: var(--comment-next-error-color, #b91c1c);
    --comment-next-notice-bg: var(--comment-next-error-bg-color, #fef2f2);
  }

  .comment-next-notice-success {
    --comment-next-notice-color: var(--comment-next-success-color, #15803d);
    --comment-next-notice-bg: var(--comment-next-success-bg-color, #f0fdf4);
  }

  .comment-next-notice-info {
    --comment-next-notice-color: var(--comment-next-composer-accent);
    --comment-next-notice-bg: var(--comment-next-composer-tint);
  }

  .comment-next-notice-info :global(.comment-next-icon) {
    --at-apply: animate-spin;
  }

  .comment-next-notice-quiet {
    margin-top: 0;
    padding: 0.1875rem 0;
    background: transparent;
    color: var(--comment-next-muted-color, #667085);
    font-size: 0.75rem;
  }

  @media (prefers-reduced-motion: reduce) {
    .comment-next-notice-info :global(.comment-next-icon) {
      animation: none;
    }
  }
</style>
