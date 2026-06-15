<script lang="ts">
  import CommentNextIcon from "./CommentNextIcon.svelte";
  import { fetchCaptchaImage } from "./services/captcha";

  let {
    baseUrl = "",
    image = "",
    value = "",
    onChange = () => {},
  }: {
    baseUrl?: string;
    image?: string;
    value?: string;
    onChange?: (value: string) => void;
  } = $props();

  let captchaImage = $state("");
  let loading = $state(false);
  let error = $state("");
  let lastBaseUrl = $state<string | undefined>();
  let lastImage = $state<string | undefined>();

  $effect(() => {
    if (image && image !== lastImage) {
      lastImage = image;
      captchaImage = image;
      error = "";
    }
  });

  $effect(() => {
    if (!image && lastBaseUrl !== baseUrl) {
      lastBaseUrl = baseUrl;
      void refreshCaptcha();
    }
  });

  async function refreshCaptcha() {
    try {
      loading = true;
      error = "";
      captchaImage = await fetchCaptchaImage(baseUrl);
      onChange("");
    } catch (captchaError) {
      console.error(captchaError);
      error = "验证码加载失败";
    } finally {
      loading = false;
    }
  }
</script>

<div class="comment-next-image-captcha" aria-label="验证码">
  <button
    class="comment-next-image-captcha-preview"
    type="button"
    title="刷新验证码"
    aria-label="刷新验证码"
    disabled={loading}
    onclick={refreshCaptcha}
  >
    {#if loading}
      <span class="comment-next-image-captcha-loading">
        <CommentNextIcon name="loader" size={16} />
      </span>
    {:else if captchaImage}
      <img src={captchaImage} alt="验证码" />
    {:else}
      <CommentNextIcon name="refresh" size={15} />
    {/if}
  </button>

  <input
    name="captchaCode"
    type="text"
    placeholder={error || "验证码"}
    autocomplete="off"
    value={value}
    oninput={(event) => onChange(event.currentTarget.value)}
  />
</div>

<style>
  .comment-next-image-captcha {
    display: flex;
    align-items: center;
    gap: 0.5rem;
  }

  .comment-next-image-captcha-preview {
    display: inline-flex;
    align-items: center;
    justify-content: center;
    width: 6rem;
    height: 2rem;
    overflow: hidden;
    padding: 0;
    border: 1px solid var(--comment-next-border-subtle-color, #dfe5ec);
    border-radius: 0.5625rem;
    background: var(--comment-next-field-bg-color, #fbfcfd);
    color: var(--comment-next-muted-color, #6b7687);
    cursor: pointer;
  }

  .comment-next-image-captcha-preview:disabled {
    cursor: wait;
    opacity: 0.72;
  }

  .comment-next-image-captcha-preview img {
    display: block;
    width: 100%;
    height: 100%;
    object-fit: cover;
  }

  .comment-next-image-captcha input {
    width: 5.75rem;
    height: 2rem;
    box-sizing: border-box;
    padding: 0 0.75rem;
    border: 1px solid var(--comment-next-border-subtle-color, #dfe5ec);
    border-radius: 0.5625rem;
    outline: 0;
    background: var(--comment-next-field-bg-color, #fbfcfd);
    color: var(--comment-next-text-color, #172033);
    font: inherit;
    font-size: 0.875rem;
  }

  .comment-next-image-captcha input:focus {
    border-color: var(--comment-next-primary-color, rgb(59, 130, 246));
    box-shadow: 0 0 0 3px var(--comment-next-focus-shadow-color, rgb(59 130 246 / 0.14));
  }

  .comment-next-image-captcha-loading {
    animation: comment-next-image-captcha-spin 900ms linear infinite;
  }

  @keyframes comment-next-image-captcha-spin {
    to {
      transform: rotate(360deg);
    }
  }

  @media (prefers-reduced-motion: reduce) {
    .comment-next-image-captcha-loading {
      animation: none;
    }
  }
</style>
