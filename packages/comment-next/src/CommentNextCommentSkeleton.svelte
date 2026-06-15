<script lang="ts">
  const widths = ["68%", "52%", "78%"];
</script>

<div class="comment-next-comment-skeleton" aria-busy="true" aria-label="评论列表加载中">
  {#each widths as width}
    <div class="comment-next-comment-skeleton-row">
      <span class="comment-next-comment-skeleton-avatar"></span>
      <span class="comment-next-comment-skeleton-body">
        <span class="comment-next-comment-skeleton-meta" style={`width: ${width}`}></span>
        <span class="comment-next-comment-skeleton-line"></span>
        <span class="comment-next-comment-skeleton-line comment-next-comment-skeleton-line-short"></span>
      </span>
    </div>
  {/each}
</div>

<style>
  .comment-next-comment-skeleton {
    display: grid;
    gap: 0.875rem;
    padding-top: 1rem;
  }

  .comment-next-comment-skeleton-row {
    display: grid;
    grid-template-columns: 2.25rem minmax(0, 1fr);
    gap: 0.75rem;
  }

  .comment-next-comment-skeleton-avatar,
  .comment-next-comment-skeleton-meta,
  .comment-next-comment-skeleton-line {
    position: relative;
    display: block;
    overflow: hidden;
    background: var(--comment-next-skeleton-base-color, #edf2f5);
  }

  .comment-next-comment-skeleton-avatar::after,
  .comment-next-comment-skeleton-meta::after,
  .comment-next-comment-skeleton-line::after {
    position: absolute;
    inset: 0;
    background: linear-gradient(
      90deg,
      transparent,
      var(--comment-next-skeleton-highlight-color, rgb(255 255 255 / 0.78)),
      transparent
    );
    content: "";
    transform: translateX(-100%);
    animation: comment-next-comment-skeleton-shimmer 1.35s ease-in-out infinite;
  }

  .comment-next-comment-skeleton-avatar {
    width: 2.25rem;
    height: 2.25rem;
    border-radius: 999px;
  }

  .comment-next-comment-skeleton-body {
    display: grid;
    gap: 0.5rem;
    padding-top: 0.125rem;
  }

  .comment-next-comment-skeleton-meta {
    height: 0.875rem;
    border-radius: 999px;
  }

  .comment-next-comment-skeleton-line {
    width: 100%;
    height: 0.75rem;
    border-radius: 999px;
  }

  .comment-next-comment-skeleton-line-short {
    width: 42%;
  }

  @keyframes comment-next-comment-skeleton-shimmer {
    to {
      transform: translateX(100%);
    }
  }
</style>
