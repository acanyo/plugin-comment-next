<script lang="ts">
  import CommentNextImageCaptcha from "./CommentNextImageCaptcha.svelte";
  import CommentNextIcon from "./CommentNextIcon.svelte";

  let {
    baseUrl = "",
    commandMenuOpen = false,
    loggedIn = false,
    allowAnonymous = true,
    enablePrivate = true,
    showCaptcha = true,
    captchaImage = "",
    captchaCode = "",
    submitting = false,
    submitDisabled = false,
    submitDisabledReason = "",
    onCaptchaChange = () => {},
    onToggleCommandMenu = () => {},
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
    submitting?: boolean;
    submitDisabled?: boolean;
    submitDisabledReason?: string;
    onCaptchaChange?: (value: string) => void;
    onToggleCommandMenu?: () => void;
    onLogin?: () => void;
  } = $props();

  const insertTools = [
    { key: "smile", icon: "smile", title: "表情" },
    { key: "image", icon: "image", title: "图片" },
  ];
</script>

<div class="comment-next-footer">
  <div class="comment-next-footer-left">
    <div class="comment-next-quick-actions">
      <button
        class:comment-next-quick-button-active={commandMenuOpen}
        class="comment-next-quick-button"
        type="button"
        title="AI 写作"
        aria-label="AI 写作"
        onclick={onToggleCommandMenu}
      >
        <CommentNextIcon name="sparkle" size={15} />
        <span>AI 写作</span>
      </button>
    </div>

    <div class="comment-next-insert-tools" aria-label="插入工具栏">
      {#each insertTools as tool}
        <button class="comment-next-tool-button" type="button" title={tool.title} aria-label={tool.title}>
          <CommentNextIcon name={tool.icon} size={16} />
        </button>
      {/each}
    </div>

  </div>

  <div class="comment-next-submit-area">
    {#if enablePrivate}
      <label class="comment-next-private-option">
        <input type="checkbox" name="hidden" />
        <span>私密</span>
        <CommentNextIcon name="info" size={14} />
      </label>
    {/if}

    {#if showCaptcha && allowAnonymous && !loggedIn}
      <CommentNextImageCaptcha
        {baseUrl}
        image={captchaImage}
        value={captchaCode}
        onChange={onCaptchaChange}
      />
    {/if}

    <button
      class="comment-next-submit-button"
      type="button"
      disabled={submitting || submitDisabled}
      title={submitDisabled ? submitDisabledReason : undefined}
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
      {!loggedIn && !allowAnonymous ? "登录后评论" : "提交"}
    </button>
  </div>
</div>

<style>
  .comment-next-footer {
    display: flex;
    align-items: center;
    justify-content: space-between;
    min-height: 3.5rem;
    gap: 0.75rem;
    box-sizing: border-box;
    padding: 0 0.875rem;
    border-top: 1px solid var(--comment-next-border-subtle-color, #dfe5ec);
    border-radius: 0 0 var(--comment-next-radius-lg, 0.875rem) var(--comment-next-radius-lg, 0.875rem);
    background: var(
      --comment-next-footer-surface-bg,
      linear-gradient(180deg, rgb(250 252 252 / 0.98), rgb(246 249 249 / 0.98)),
      var(--comment-next-footer-bg-color, #fbfcfc)
    );
  }

  .comment-next-footer-left,
  .comment-next-quick-actions,
  .comment-next-insert-tools,
  .comment-next-submit-area,
  .comment-next-private-option {
    display: flex;
    align-items: center;
  }

  .comment-next-quick-actions,
  .comment-next-insert-tools,
  .comment-next-submit-area {
    gap: 0.5rem;
  }

  .comment-next-footer-left {
    min-width: 0;
    gap: 0.625rem;
  }

  .comment-next-quick-button,
  .comment-next-tool-button,
  .comment-next-submit-button {
    font: inherit;
  }

  .comment-next-quick-button {
    display: inline-flex;
    align-items: center;
    justify-content: center;
    width: auto;
    min-width: 5.25rem;
    height: 2.125rem;
    gap: 0.3rem;
    padding: 0 0.75rem;
    border: 1px solid var(--comment-next-ai-border-color, rgb(191 219 254));
    border-radius: 0.5625rem;
    background: var(--comment-next-ai-bg-color, rgb(239 246 255));
    color: var(--comment-next-ai-color, rgb(59, 130, 246));
    cursor: pointer;
    font-size: 0.875rem;
    font-weight: 720;
    transition:
      background-color 140ms ease,
      color 140ms ease,
      border-color 140ms ease,
      box-shadow 140ms ease,
      transform 140ms ease;
  }

  .comment-next-quick-button:hover,
  .comment-next-quick-button-active {
    border-color: var(--comment-next-primary-color, rgb(59, 130, 246));
    background: var(--comment-next-pill-active-bg-color, rgb(239 246 255));
    color: var(--comment-next-primary-color, rgb(59, 130, 246));
    box-shadow: 0 7px 16px rgb(59 130 246 / 0.16);
  }

  .comment-next-quick-button:active {
    transform: translateY(1px);
  }

  .comment-next-insert-tools {
    min-width: 0;
    overflow: hidden;
    gap: 0.125rem;
    padding: 0.1875rem;
    border: 1px solid var(--comment-next-border-subtle-color, #dfe5ec);
    border-radius: 0.625rem;
    background: var(--comment-next-toolbar-bg-color, #ffffff);
  }

  .comment-next-tool-button {
    display: inline-flex;
    align-items: center;
    justify-content: center;
    width: 1.75rem;
    height: 1.75rem;
    padding: 0;
    border: 0;
    border-radius: 0.4375rem;
    background: transparent;
    color: var(--comment-next-icon-color, #39445d);
    cursor: pointer;
    transition:
      background-color 140ms ease,
      color 140ms ease,
      transform 140ms ease;
  }

  .comment-next-tool-button:hover {
    background: var(--comment-next-control-hover-bg-color, #eef2f4);
    color: var(--comment-next-text-color, #172033);
  }

  .comment-next-tool-button:active {
    transform: translateY(1px);
  }

  .comment-next-private-option {
    gap: 0.35rem;
    color: var(--comment-next-muted-color, #6b7687);
    font-size: 0.875rem;
    white-space: nowrap;
  }

  .comment-next-private-option input {
    width: 0.9375rem;
    height: 0.9375rem;
    accent-color: var(--comment-next-primary-color, rgb(59, 130, 246));
  }

  .comment-next-submit-button {
    display: inline-flex;
    align-items: center;
    justify-content: center;
    min-width: 5.75rem;
    height: 2.25rem;
    gap: 0.45rem;
    padding: 0 1rem;
    border: 0;
    border-radius: 0.5625rem;
    background: var(--comment-next-primary-color, #172033);
    color: #ffffff;
    cursor: pointer;
    font-size: 0.9375rem;
    font-weight: 650;
    box-shadow: 0 9px 18px rgb(23 32 51 / 0.14);
    transition:
      background-color 140ms ease,
      box-shadow 140ms ease,
      transform 140ms ease;
  }

  .comment-next-submit-button:hover {
    background: var(--comment-next-primary-hover-color, rgb(59, 130, 246));
    box-shadow: 0 10px 20px rgb(59 130 246 / 0.18);
  }

  .comment-next-submit-button:active {
    transform: translateY(1px);
  }

  .comment-next-submit-button:focus-visible,
  .comment-next-quick-button:focus-visible,
  .comment-next-tool-button:focus-visible {
    outline: 2px solid var(--comment-next-focus-ring-color, rgb(147 197 253));
    outline-offset: 2px;
  }

  .comment-next-submit-button:disabled {
    cursor: not-allowed;
    opacity: 0.72;
  }

  .comment-next-loading-icon {
    animation: comment-next-spin 900ms linear infinite;
  }

  @keyframes comment-next-spin {
    to {
      transform: rotate(360deg);
    }
  }

  @media (max-width: 780px) {
    .comment-next-footer {
      align-items: stretch;
      flex-direction: column;
      padding: 0.875rem 1rem;
    }

    .comment-next-submit-area {
      justify-content: space-between;
      width: 100%;
      flex-wrap: wrap;
    }

    .comment-next-footer-left {
      width: 100%;
      flex-wrap: wrap;
    }

    .comment-next-insert-tools {
      overflow-x: auto;
    }
  }

  @media (prefers-reduced-motion: reduce) {
    .comment-next-quick-button,
    .comment-next-tool-button,
    .comment-next-submit-button {
      transition: none;
    }

    .comment-next-loading-icon {
      animation: none;
    }
  }
</style>
