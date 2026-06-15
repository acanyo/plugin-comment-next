<script lang="ts">
  import CommentNextIcon from "./CommentNextIcon.svelte";

  let {
    src = "",
    alt = "",
    size = 36,
  }: {
    src?: string;
    alt?: string;
    size?: number;
  } = $props();

  let failed = $state(false);
  let previousSrc = $state("");
  const placeholderText = $derived(getPlaceholderText(alt));

  $effect(() => {
    if (previousSrc !== src) {
      previousSrc = src;
      failed = false;
    }
  });

  function getPlaceholderText(value: string): string {
    const trimmedValue = value.trim();

    if (!trimmedValue) {
      return "";
    }

    const words = trimmedValue.split(/\s+/).filter(Boolean);

    if (words.length > 1) {
      return `${words[0].charAt(0)}${words[1].charAt(0)}`.toUpperCase();
    }

    return trimmedValue.charAt(0).toUpperCase();
  }
</script>

<span class="comment-next-avatar" style={`--comment-next-avatar-size: ${size}px`} aria-label={alt || "头像"}>
  {#if src && !failed}
    <img class="comment-next-avatar-image" {src} {alt} loading="lazy" decoding="async" onerror={() => (failed = true)} />
  {:else if placeholderText}
    <span class="comment-next-avatar-placeholder">{placeholderText}</span>
  {:else}
    <CommentNextIcon name="user" size={Math.round(size * 0.46)} />
  {/if}
</span>

<style>
  .comment-next-avatar {
    display: inline-flex;
    align-items: center;
    justify-content: center;
    flex: 0 0 auto;
    width: var(--comment-next-avatar-size, 36px);
    height: var(--comment-next-avatar-size, 36px);
    overflow: hidden;
    border: 1px solid var(--comment-next-avatar-border-color, rgb(15 23 42 / 0.08));
    border-radius: 999px;
    background:
      linear-gradient(180deg, rgb(255 255 255 / 0.88), rgb(238 244 244 / 0.9)),
      var(--comment-next-field-bg-color, #fbfcfd);
    color: var(--comment-next-muted-color, #6b7687);
    box-shadow: 0 1px 2px rgb(15 23 42 / 0.06);
  }

  .comment-next-avatar-image {
    display: block;
    width: 100%;
    height: 100%;
    object-fit: cover;
  }

  .comment-next-avatar-placeholder {
    color: var(--comment-next-text-color, #172033);
    font-size: 0.8125rem;
    font-weight: 760;
    line-height: 1;
    user-select: none;
  }
</style>
