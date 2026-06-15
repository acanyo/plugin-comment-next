<script lang="ts">
  import type { CommentNextBadge } from "./types/comment";
  import CommentNextIcon from "./CommentNextIcon.svelte";

  let {
    badge,
  }: {
    badge: CommentNextBadge;
  } = $props();
</script>

<span class={`comment-next-badge comment-next-badge-${badge.tone}`} title={badge.title ?? badge.label}>
  {#if badge.icon}
    <CommentNextIcon name={badge.icon} size={12} />
  {/if}
  <span>{badge.label}</span>
</span>

<style>
  .comment-next-badge {
    display: inline-flex;
    align-items: center;
    max-width: 8rem;
    height: 1.25rem;
    gap: 0.2rem;
    box-sizing: border-box;
    padding: 0 0.4rem;
    border: 1px solid var(--comment-next-badge-border-color, rgb(15 23 42 / 0.08));
    border-radius: 0.375rem;
    background: var(--comment-next-badge-bg-color, #f3f6f8);
    color: var(--comment-next-badge-color, #4b5870);
    font-size: 0.6875rem;
    font-weight: 760;
    line-height: 1;
    white-space: nowrap;
  }

  .comment-next-badge span {
    min-width: 0;
    overflow: hidden;
    text-overflow: ellipsis;
  }

  .comment-next-badge-first {
    --comment-next-badge-bg-color: var(--comment-next-badge-first-bg-color, #fff7d7);
    --comment-next-badge-border-color: var(--comment-next-badge-first-border-color, #ead27a);
    --comment-next-badge-color: var(--comment-next-badge-first-color, #7a5700);
  }

  .comment-next-badge-admin {
    --comment-next-badge-bg-color: var(--comment-next-badge-admin-bg-color, #e9f7f4);
    --comment-next-badge-border-color: var(--comment-next-badge-admin-border-color, rgb(191 219 254));
    --comment-next-badge-color: var(--comment-next-badge-admin-color, #0f665f);
  }

  .comment-next-badge-level {
    --comment-next-badge-bg-color: var(--comment-next-badge-level-bg-color, #edf2ff);
    --comment-next-badge-border-color: var(--comment-next-badge-level-border-color, #bcc8ee);
    --comment-next-badge-color: var(--comment-next-badge-level-color, #364b92);
  }

  .comment-next-badge-custom {
    --comment-next-badge-bg-color: var(--comment-next-badge-custom-bg-color, #f6eefb);
    --comment-next-badge-border-color: var(--comment-next-badge-custom-border-color, #d8c1e8);
    --comment-next-badge-color: var(--comment-next-badge-custom-color, #6a3d86);
  }
</style>
