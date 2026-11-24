<script lang="ts">
const {
  showFooter = true,
  showSubmitArea = true,
  showIdentity = false,
}: {
  showFooter?: boolean;
  showSubmitArea?: boolean;
  showIdentity?: boolean;
} = $props();
</script>

<div class="comment-next-skeleton" role="status" aria-busy="true" aria-label="评论框加载中">
  <div class="comment-next-skeleton-editor" aria-hidden="true">
    <span class="comment-next-skeleton-shape"></span>
  </div>
  {#if showFooter}
    <div class="comment-next-skeleton-actions comment-next-composer-action-row" aria-hidden="true">
      {#if showIdentity}
        <span class="comment-next-skeleton-shape comment-next-skeleton-avatar"></span>
      {/if}
      <span class="comment-next-skeleton-shape comment-next-skeleton-tool"></span>
      {#if showSubmitArea}
        <span class="comment-next-skeleton-shape comment-next-skeleton-button"></span>
      {/if}
    </div>
  {/if}
</div>

<style>
  .comment-next-skeleton {
    border-radius: inherit;
    animation: comment-next-skeleton-pulse 1s ease-in-out infinite alternate;
  }

  .comment-next-skeleton-shape {
    display: block;
    border-radius: 2rem;
    background: var(--comment-next-skeleton-base-color, #e7ecf2);
  }

  .comment-next-skeleton-editor {
    box-sizing: border-box;
    min-height: var(--comment-next-editor-min-height, var(--comment-next-composer-default-height));
    padding: 1.125rem var(--comment-next-composer-gutter) 0.5rem;
  }

  .comment-next-skeleton-editor .comment-next-skeleton-shape {
    width: min(45%, 8rem);
    height: 0.75rem;
  }

  .comment-next-skeleton-actions {
    display: flex;
    align-items: center;
    gap: 0.5rem;
    min-height: var(--comment-next-composer-control-height);
  }

  .comment-next-skeleton-avatar {
    width: var(--comment-next-composer-avatar-size);
    height: var(--comment-next-composer-avatar-size);
  }

  .comment-next-skeleton-tool {
    width: 1rem;
    height: 1rem;
  }

  .comment-next-skeleton-button {
    width: min(35%, 6.375rem);
    height: var(--comment-next-composer-control-height);
    margin-inline-start: auto;
    border-radius: var(--comment-next-composer-control-radius);
  }

  @keyframes comment-next-skeleton-pulse {
    to { opacity: 0.55; }
  }

  @container comment-next-composer (max-width: 36rem) {
    .comment-next-skeleton-editor {
      min-height: var(--comment-next-editor-mobile-min-height, var(--comment-next-composer-default-mobile-height));
    }
  }

  @media (prefers-reduced-motion: reduce) {
    .comment-next-skeleton {
      animation: none;
    }
  }
</style>
