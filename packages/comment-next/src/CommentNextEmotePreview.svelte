<script lang="ts">
import type { CommentNextEmoteItem } from './types/emote';

const {
  item,
  packName = '',
}: {
  item: CommentNextEmoteItem;
  packName?: string;
} = $props();

const previewSrc = $derived(
  item.originSrc || item.src || item.previewSrc || ''
);
const previewLabel = $derived(item.description || item.label || packName);
</script>

{#if previewSrc}
  <div class="comment-next-emote-preview" role="status" aria-live="polite">
    <div class="comment-next-emote-preview-image">
      <img
        src={previewSrc}
        alt={previewLabel}
        decoding="async"
      />
    </div>
    {#if previewLabel}
      <span>{previewLabel}</span>
    {/if}
  </div>
{/if}

<style>
  .comment-next-emote-preview {
    --at-apply: pointer-events-none absolute right-3 top-13 z-20 flex w-34 flex-col items-center gap-1.5 rounded-[0.75rem] border border-solid [border-color:var(--comment-next-border-subtle-color,#dfe5ec)] bg-[var(--comment-next-tooltip-bg-color,rgb(255_255_255_/_0.96))] p-2 text-center text-[var(--comment-next-tooltip-text-color,#172033)] shadow-[var(--comment-next-tooltip-shadow,0_14px_30px_rgb(15_23_42_/_0.18))] backdrop-blur-md;
    animation: comment-next-emote-preview-in 120ms ease-out;
  }

  .comment-next-emote-preview-image {
    --at-apply: flex h-30 w-full items-center justify-center overflow-hidden rounded-[0.5rem];
  }

  .comment-next-emote-preview img {
    --at-apply: block max-h-full max-w-full object-contain;
  }

  .comment-next-emote-preview span {
    --at-apply: block max-w-full truncate text-xs;
    font-weight: 650;
  }

  @keyframes comment-next-emote-preview-in {
    from {
      opacity: 0;
      transform: translateY(0.2rem) scale(0.97);
    }

    to {
      opacity: 1;
      transform: translateY(0) scale(1);
    }
  }

  @media (max-width: 780px), (hover: none) and (pointer: coarse) {
    .comment-next-emote-preview {
      display: none;
    }
  }

  @media (prefers-reduced-motion: reduce) {
    .comment-next-emote-preview {
      animation: none;
    }
  }
</style>
