<script lang="ts">
import { sanitizeCommentHtml } from './utils/html';

const {
  content = '',
}: {
  content?: string;
} = $props();

const safeContent = $derived(sanitizeCommentHtml(content));
</script>

<div class="comment-next-comment-content break-words text-[0.9375rem] text-[var(--comment-next-comment-content-color,var(--comment-next-text-color,#172033))] leading-[1.72]">
  {@html safeContent}
</div>

<style>
  .comment-next-comment-content :global(p) {
    --at-apply: m-0;
  }

  .comment-next-comment-content :global(p + p),
  .comment-next-comment-content :global(ul),
  .comment-next-comment-content :global(ol),
  .comment-next-comment-content :global(blockquote),
  .comment-next-comment-content :global(pre) {
    --at-apply: mt-2.5;
  }

  .comment-next-comment-content :global(a) {
    --at-apply: text-[var(--comment-next-link-color,rgb(59,130,246))] font-[620] decoration-[var(--comment-next-link-underline-color,rgb(59_130_246_/_0.35))] underline-offset-[0.18em];
  }

  .comment-next-comment-content :global(blockquote) {
    --at-apply: mx-0 py-1 pl-3.5 pr-0 border-l-3 border-l-solid [border-left-color:var(--comment-next-border-subtle-color,#dfe5ec)] text-[var(--comment-next-muted-color,#6b7687)];
  }

  .comment-next-comment-content :global(code) {
    --at-apply: rounded px-[0.28rem] py-[0.08rem] bg-[var(--comment-next-inline-code-bg-color,#eef2f4)] text-[0.88em] text-[var(--comment-next-text-color,#172033)] font-mono;
  }

  .comment-next-comment-content :global(pre) {
    --at-apply: overflow-x-auto rounded-lg bg-[var(--comment-next-code-bg-color,#111827)] p-3 text-[#f9fafb];
  }

  .comment-next-comment-content :global(pre code) {
    --at-apply: bg-transparent p-0 text-inherit;
  }

  .comment-next-comment-content :global(.comment-next-emote-image) {
    --at-apply: mx-0.5 inline-block max-h-18 max-w-36 align-middle object-contain;
  }
</style>
