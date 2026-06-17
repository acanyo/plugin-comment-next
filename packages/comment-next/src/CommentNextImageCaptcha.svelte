<script lang="ts">
import CommentNextIcon from './CommentNextIcon.svelte';
import { fetchCaptchaImage } from './services/captcha';

const {
  baseUrl = '',
  image = '',
  refreshKey = 0,
  value = '',
  onChange = () => {},
}: {
  baseUrl?: string;
  image?: string;
  refreshKey?: number;
  value?: string;
  onChange?: (value: string) => void;
} = $props();

let captchaImage = $state('');
let loading = $state(false);
let error = $state('');
let lastBaseUrl = $state<string | undefined>();
let lastImage = $state<string | undefined>();
let lastRefreshKey = $state<number | undefined>();

$effect(() => {
  if (image && image !== lastImage) {
    lastImage = image;
    captchaImage = image;
    error = '';
  }
});

$effect(() => {
  if (!image && (lastBaseUrl !== baseUrl || lastRefreshKey !== refreshKey)) {
    lastBaseUrl = baseUrl;
    lastRefreshKey = refreshKey;
    void refreshCaptcha();
  }
});

async function refreshCaptcha() {
  try {
    loading = true;
    error = '';
    captchaImage = await fetchCaptchaImage(baseUrl);
    onChange('');
  } catch (captchaError) {
    console.error(captchaError);
    error = '验证码加载失败';
  } finally {
    loading = false;
  }
}
</script>

<div class="comment-next-image-captcha flex items-center gap-2" aria-label="验证码">
  <button
    class="comment-next-image-captcha-preview inline-flex h-8 w-24 items-center justify-center overflow-hidden rounded-[0.5625rem] border border-solid [border-color:var(--comment-next-border-subtle-color,#dfe5ec)] bg-[var(--comment-next-field-bg-color,#fbfcfd)] p-0 text-[var(--comment-next-muted-color,#6b7687)] disabled:cursor-wait disabled:opacity-72"
    type="button"
    aria-label="刷新验证码"
    disabled={loading}
    onclick={refreshCaptcha}
  >
    {#if loading}
      <span class="comment-next-image-captcha-loading animate-spin motion-reduce:animate-none">
        <CommentNextIcon name="loader" size={16} />
      </span>
    {:else if captchaImage}
      <img class="block h-full w-full object-cover" src={captchaImage} alt="验证码" />
    {:else}
      <CommentNextIcon name="refresh" size={15} />
    {/if}
  </button>

  <input
    class="h-8 w-[5.75rem] box-border rounded-[0.5625rem] border border-solid [border-color:var(--comment-next-border-subtle-color,#dfe5ec)] bg-[var(--comment-next-field-bg-color,#fbfcfd)] px-3 text-[0.875rem] text-[var(--comment-next-text-color,#172033)] outline-none focus:[border-color:var(--comment-next-primary-color,rgb(59,130,246))] focus:shadow-[0_0_0_3px_var(--comment-next-focus-shadow-color,rgb(59_130_246_/_0.14))]"
    name="captchaCode"
    type="text"
    placeholder={error || "验证码"}
    autocomplete="off"
    value={value}
    oninput={(event) => onChange(event.currentTarget.value)}
  />
</div>
