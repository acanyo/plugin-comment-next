<script lang="ts">
import { onMount } from 'svelte';
import CommentNextEmotePanel from './CommentNextEmotePanel.svelte';
import CommentNextIcon from './CommentNextIcon.svelte';
import CommentNextImageCaptcha from './CommentNextImageCaptcha.svelte';
import CommentNextTooltip from './CommentNextTooltip.svelte';
import type {
  CommentNextEmoteItem,
  CommentNextEmotePack,
} from './types/emote';

const {
  baseUrl = '',
  commandMenuOpen = false,
  loggedIn = false,
  allowAnonymous = true,
  enablePrivate = true,
  showCaptcha = true,
  captchaImage = '',
  captchaCode = '',
  captchaRefreshKey = 0,
  submitting = false,
  submitDisabled = false,
  submitDisabledReason = '',
  submitLabel = '提交',
  loginLabel = '登录后评论',
  aiLabel = 'AI 写作',
  compact = false,
  showAi = true,
  showInsertTools = true,
  emotePacks = [],
  onCaptchaChange = () => {},
  onToggleCommandMenu = () => {},
  onEmoteSelect = () => {},
  onLogin = () => {},
}: {
  baseUrl?: string;
  commandMenuOpen?: boolean;
  loggedIn?: boolean;
  allowAnonymous?: boolean;
  enablePrivate?: boolean;
  showCaptcha?: boolean;
  captchaImage?: string;
  captchaCode?: string;
  captchaRefreshKey?: number;
  submitting?: boolean;
  submitDisabled?: boolean;
  submitDisabledReason?: string;
  submitLabel?: string;
  loginLabel?: string;
  aiLabel?: string;
  compact?: boolean;
  showAi?: boolean;
  showInsertTools?: boolean;
  emotePacks?: CommentNextEmotePack[];
  onCaptchaChange?: (value: string) => void;
  onToggleCommandMenu?: () => void;
  onEmoteSelect?: (item: CommentNextEmoteItem) => void;
  onLogin?: () => void;
} = $props();

const insertTools = [
  { key: 'smile', icon: 'smile', title: '表情' },
  { key: 'image', icon: 'image', title: '图片' },
];

let footerElement = $state<HTMLDivElement | undefined>();
let emotePanelOpen = $state(false);

const hasEmotePacks = $derived(emotePacks.some((pack) => pack.items.length));

onMount(() => {
  const handlePointerDown = (event: PointerEvent) => {
    const path = event.composedPath();

    if (!emotePanelOpen || (footerElement && path.includes(footerElement))) {
      return;
    }

    emotePanelOpen = false;
  };

  const handleKeyDown = (event: KeyboardEvent) => {
    if (event.key === 'Escape') {
      emotePanelOpen = false;
    }
  };

  document.addEventListener('pointerdown', handlePointerDown, true);
  document.addEventListener('keydown', handleKeyDown);

  return () => {
    document.removeEventListener('pointerdown', handlePointerDown, true);
    document.removeEventListener('keydown', handleKeyDown);
  };
});

function handleQuickActionClick() {
  emotePanelOpen = false;
  onToggleCommandMenu();
}

function handleInsertToolClick(key: string) {
  if (key === 'smile') {
    if (!hasEmotePacks) {
      return;
    }

    emotePanelOpen = !emotePanelOpen;
  }
}

function handleEmoteSelect(item: CommentNextEmoteItem) {
  onEmoteSelect(item);
}
</script>

<div bind:this={footerElement} class:comment-next-footer-compact={compact} class="comment-next-footer">
  <div class="comment-next-footer-left">
    {#if showAi}
    <div class="comment-next-quick-actions">
      <button
        class:comment-next-quick-button-active={commandMenuOpen}
        class="comment-next-quick-button"
        type="button"
        aria-label="AI 写作"
        onclick={handleQuickActionClick}
      >
        <CommentNextIcon name="sparkle" size={15} />
        <span>{aiLabel}</span>
      </button>
    </div>
    {/if}

    {#if showInsertTools}
    <div class="comment-next-insert-tools-region">
      {#if emotePanelOpen && hasEmotePacks}
        <CommentNextEmotePanel packs={emotePacks} onSelect={handleEmoteSelect} />
      {/if}

      <div class="comment-next-insert-tools" aria-label="插入工具栏">
        {#each insertTools as tool}
          <button
            class:comment-next-tool-button-active={tool.key === "smile" && emotePanelOpen}
            class="comment-next-tool-button"
            type="button"
            aria-label={tool.title}
            aria-expanded={tool.key === "smile" ? emotePanelOpen : undefined}
            disabled={tool.key === "smile" && !hasEmotePacks}
            onclick={() => handleInsertToolClick(tool.key)}
          >
            <CommentNextIcon name={tool.icon} size={16} />
          </button>
        {/each}
      </div>
    </div>
    {/if}

  </div>

  <div class="comment-next-submit-area">
    {#if enablePrivate}
      <div class="comment-next-private-control">
        <CommentNextTooltip text="仅自己和管理员可见" align="end" mobileAlign="start">
          <label class="comment-next-private-option">
            <input class="comment-next-private-input" type="checkbox" name="hidden" aria-label="私密评论" />
            <span class="comment-next-private-chip">
              <CommentNextIcon name="lock" size={14} />
              <span>私密</span>
            </span>
          </label>
        </CommentNextTooltip>
      </div>
    {/if}

    {#if showCaptcha && allowAnonymous && !loggedIn}
      <CommentNextImageCaptcha
        {baseUrl}
        image={captchaImage}
        refreshKey={captchaRefreshKey}
        value={captchaCode}
        onChange={onCaptchaChange}
      />
    {/if}

    <button
      class="comment-next-submit-button"
      type={!loggedIn && !allowAnonymous ? "button" : "submit"}
      disabled={submitting || submitDisabled}
      onclick={!loggedIn && !allowAnonymous ? onLogin : undefined}
    >
      {#if submitting}
        <span class="comment-next-loading-icon">
          <CommentNextIcon name="loader" size={17} />
        </span>
      {:else if !loggedIn && !allowAnonymous}
        <CommentNextIcon name="login" size={17} />
      {:else}
        <CommentNextIcon name="send" size={17} />
      {/if}
      {!loggedIn && !allowAnonymous ? loginLabel : submitLabel}
    </button>
  </div>
</div>

<style>
  .comment-next-footer {
    --at-apply: flex min-h-14 box-border items-center justify-between gap-3 border-t [border-top-style:var(--comment-next-divider-style,dashed)] [border-top-color:var(--comment-next-divider-color,#d4dde8)] rounded-b-[var(--comment-next-radius-lg,0.875rem)] [background:var(--comment-next-footer-surface-bg,transparent,var(--comment-next-footer-bg-color,#fbfcfc))] px-3.5 py-0;
  }

  .comment-next-footer-left,
  .comment-next-quick-actions,
  .comment-next-insert-tools-region,
  .comment-next-insert-tools,
  .comment-next-submit-area,
  .comment-next-private-control,
  .comment-next-private-option {
    --at-apply: flex items-center;
  }

  .comment-next-quick-actions,
  .comment-next-insert-tools,
  .comment-next-submit-area {
    --at-apply: gap-2;
  }

  .comment-next-footer-left {
    --at-apply: min-w-0 gap-2.5;
  }

  .comment-next-insert-tools-region {
    --at-apply: relative min-w-0;
  }

  .comment-next-footer-compact {
    --at-apply: min-h-12 px-2.5 py-0;
  }

  .comment-next-footer-compact .comment-next-quick-button {
    --at-apply: h-[1.9375rem] min-w-[4.75rem] px-2.5 py-0 text-[0.8125rem];
  }

  .comment-next-footer-compact .comment-next-submit-button {
    --at-apply: h-8 min-w-18 px-3.5 py-0 text-sm;
  }

  .comment-next-quick-button,
  .comment-next-tool-button,
  .comment-next-submit-button {
    --at-apply: font-inherit;
  }

  .comment-next-quick-button {
    --at-apply: inline-flex h-[2.125rem] w-auto min-w-[5.25rem] cursor-pointer items-center justify-center gap-[0.3rem] rounded-[0.5625rem] border border-solid [border-color:var(--comment-next-ai-border-color,rgb(191_219_254))] bg-[var(--comment-next-ai-bg-color,rgb(239_246_255))] px-3 py-0 text-sm text-[var(--comment-next-ai-color,rgb(59,130,246))] font-[720] transition-[background-color,color,border-color,box-shadow,transform] duration-140 ease-in-out;
  }

  .comment-next-quick-button:hover,
  .comment-next-quick-button-active {
    --at-apply: [border-color:var(--comment-next-primary-color,rgb(59,130,246))] bg-[var(--comment-next-pill-active-bg-color,rgb(239_246_255))] text-[var(--comment-next-primary-color,rgb(59,130,246))] shadow-[0_7px_16px_rgb(59_130_246_/_0.16)];
  }

  .comment-next-quick-button:active {
    --at-apply: translate-y-px;
  }

  .comment-next-insert-tools {
    --at-apply: min-w-0 overflow-visible gap-0.5 rounded-[0.625rem] border border-solid [border-color:var(--comment-next-border-subtle-color,#dfe5ec)] bg-[var(--comment-next-toolbar-bg-color,#ffffff)] p-[0.1875rem];
  }

  .comment-next-tool-button {
    --at-apply: inline-flex h-7 w-7 cursor-pointer items-center justify-center rounded-[0.4375rem] border-0 bg-transparent p-0 text-[var(--comment-next-icon-color,#39445d)] transition-[background-color,color,transform] duration-140 ease-in-out;
  }

  .comment-next-tool-button:hover {
    --at-apply: bg-[var(--comment-next-control-hover-bg-color,#eef2f4)] text-[var(--comment-next-text-color,#172033)];
  }

  .comment-next-tool-button:disabled {
    --at-apply: cursor-not-allowed opacity-45;
  }

  .comment-next-tool-button-active {
    --at-apply: bg-[var(--comment-next-pill-active-bg-color,rgb(239_246_255))] text-[var(--comment-next-primary-color,rgb(59,130,246))];
  }

  .comment-next-tool-button:active {
    --at-apply: translate-y-px;
  }

  .comment-next-private-control {
    --at-apply: gap-1;
  }

  .comment-next-private-option {
    --at-apply: h-8 cursor-pointer whitespace-nowrap;
  }

  .comment-next-private-input {
    --at-apply: sr-only;
  }

  .comment-next-private-chip {
    --at-apply: inline-flex h-8 box-border items-center justify-center gap-1.5 rounded-[0.5625rem] border border-solid border-transparent bg-transparent px-2.5 py-0 text-sm text-[var(--comment-next-muted-color,#6b7687)] font-[650] transition-[background-color,border-color,color,box-shadow,transform] duration-140 ease-in-out;
  }

  .comment-next-private-option:hover .comment-next-private-chip {
    --at-apply: bg-[var(--comment-next-control-hover-bg-color,#eef2f4)] text-[var(--comment-next-text-color,#172033)];
  }

  .comment-next-private-input:checked + .comment-next-private-chip {
    --at-apply: [border-color:rgb(147_197_253_/_0.55)] bg-[var(--comment-next-pill-active-bg-color,rgb(239_246_255))] text-[var(--comment-next-primary-color,rgb(59,130,246))] shadow-[0_5px_14px_rgb(59_130_246_/_0.10)];
  }

  .comment-next-private-input:focus-visible + .comment-next-private-chip {
    --at-apply: outline-2 outline-solid outline-offset-2 [outline-color:var(--comment-next-focus-ring-color,rgb(147_197_253))];
  }

  .comment-next-private-input:active + .comment-next-private-chip {
    --at-apply: translate-y-px;
  }

  .comment-next-submit-button {
    --at-apply: inline-flex h-9 min-w-[5.75rem] cursor-pointer items-center justify-center gap-[0.45rem] rounded-[0.5625rem] border-0 bg-[var(--comment-next-primary-color,#172033)] px-4 py-0 text-[0.9375rem] text-white font-[650] shadow-[0_9px_18px_rgb(23_32_51_/_0.14)] transition-[background-color,box-shadow,transform] duration-140 ease-in-out;
  }

  .comment-next-submit-button:hover {
    --at-apply: bg-[var(--comment-next-primary-hover-color,rgb(59,130,246))] shadow-[0_10px_20px_rgb(59_130_246_/_0.18)];
  }

  .comment-next-submit-button:active {
    --at-apply: translate-y-px;
  }

  .comment-next-submit-button:focus-visible,
  .comment-next-quick-button:focus-visible,
  .comment-next-tool-button:focus-visible {
    --at-apply: outline-2 outline-solid outline-offset-2 [outline-color:var(--comment-next-focus-ring-color,rgb(147_197_253))];
  }

  .comment-next-submit-button:disabled {
    --at-apply: cursor-not-allowed opacity-72;
  }

  .comment-next-loading-icon {
    --at-apply: animate-spin;
  }

  @media (max-width: 780px) {
    .comment-next-footer {
      --at-apply: flex-col items-stretch px-4 py-3.5;
    }

    .comment-next-submit-area {
      --at-apply: w-full flex-wrap justify-between;
    }

    .comment-next-footer-left {
      --at-apply: w-full flex-wrap;
    }

    .comment-next-insert-tools {
      --at-apply: overflow-x-auto;
    }

    .comment-next-insert-tools-region {
      --at-apply: w-full;
    }
  }

  @media (prefers-reduced-motion: reduce) {
    .comment-next-quick-button,
    .comment-next-tool-button,
    .comment-next-submit-button {
      --at-apply: transition-none;
    }

    .comment-next-loading-icon {
      --at-apply: animate-none;
    }
  }
</style>
