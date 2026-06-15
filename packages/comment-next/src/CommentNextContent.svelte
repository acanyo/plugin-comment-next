<script lang="ts">
  import { sanitizeCommentHtml } from "./utils/html";

  let {
    content = "",
  }: {
    content?: string;
  } = $props();

  const safeContent = $derived(sanitizeCommentHtml(content));
</script>

<div class="comment-next-comment-content">
  {@html safeContent}
</div>

<style>
  .comment-next-comment-content {
    color: var(--comment-next-comment-content-color, var(--comment-next-text-color, #172033));
    font-size: 0.9375rem;
    line-height: 1.72;
    word-break: break-word;
  }

  .comment-next-comment-content :global(p) {
    margin: 0;
  }

  .comment-next-comment-content :global(p + p),
  .comment-next-comment-content :global(ul),
  .comment-next-comment-content :global(ol),
  .comment-next-comment-content :global(blockquote),
  .comment-next-comment-content :global(pre) {
    margin-top: 0.625rem;
  }

  .comment-next-comment-content :global(a) {
    color: var(--comment-next-link-color, rgb(59, 130, 246));
    font-weight: 620;
    text-decoration-color: var(--comment-next-link-underline-color, rgb(59 130 246 / 0.35));
    text-underline-offset: 0.18em;
  }

  .comment-next-comment-content :global(blockquote) {
    margin-right: 0;
    margin-left: 0;
    padding: 0.25rem 0 0.25rem 0.875rem;
    border-left: 3px solid var(--comment-next-border-subtle-color, #dfe5ec);
    color: var(--comment-next-muted-color, #6b7687);
  }

  .comment-next-comment-content :global(code) {
    padding: 0.08rem 0.28rem;
    border-radius: 0.25rem;
    background: var(--comment-next-inline-code-bg-color, #eef2f4);
    color: var(--comment-next-text-color, #172033);
    font-family: ui-monospace, SFMono-Regular, Menlo, Monaco, Consolas, monospace;
    font-size: 0.88em;
  }

  .comment-next-comment-content :global(pre) {
    overflow-x: auto;
    padding: 0.75rem;
    border-radius: 0.5rem;
    background: var(--comment-next-code-bg-color, #111827);
    color: #f9fafb;
  }

  .comment-next-comment-content :global(pre code) {
    padding: 0;
    background: transparent;
    color: inherit;
  }
</style>
