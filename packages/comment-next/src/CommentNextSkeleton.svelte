<script lang="ts">
  let {
    showAccountFields = true,
    loggedIn = false,
    showCaptcha = false,
    enablePrivate = false,
  }: {
    showAccountFields?: boolean;
    loggedIn?: boolean;
    showCaptcha?: boolean;
    enablePrivate?: boolean;
  } = $props();

  const editorRows = ["72%", "48%", "64%"];
  const accountRows = ["4rem", "4.25rem", "3.75rem"];
  const tools = Array.from({ length: 2 });
</script>

<div class="comment-next-skeleton" aria-busy="true" aria-label="评论框加载中">
  {#if showAccountFields}
    <div class:comment-next-skeleton-fields-logged-in={loggedIn} class="comment-next-skeleton-fields">
      <span class="comment-next-skeleton-account-avatar"></span>
      {#if loggedIn}
        <span class="comment-next-skeleton-current-user">
          <span class="comment-next-skeleton-field-line" style="width: 4rem"></span>
          <span class="comment-next-skeleton-field-line" style="width: 8rem"></span>
        </span>
      {:else}
        {#each accountRows as width}
          <span class="comment-next-skeleton-field">
            <span class="comment-next-skeleton-field-icon"></span>
            <span class="comment-next-skeleton-field-line" style={`width: ${width}`}></span>
          </span>
        {/each}
      {/if}
    </div>
  {/if}

  <div class="comment-next-skeleton-editor">
    <div class="comment-next-skeleton-topline">
      <span class="comment-next-skeleton-dot"></span>
      <span class="comment-next-skeleton-bar comment-next-skeleton-bar-short"></span>
    </div>
    <div class="comment-next-skeleton-lines">
      {#each editorRows as width}
        <span class="comment-next-skeleton-bar" style={`width: ${width}`}></span>
      {/each}
    </div>
  </div>

  <div class="comment-next-skeleton-footer">
    <div class="comment-next-skeleton-tools">
      <span class="comment-next-skeleton-ai-action"></span>
      {#each tools as _}
        <span class="comment-next-skeleton-tool"></span>
      {/each}
    </div>
    <div class="comment-next-skeleton-actions">
      {#if enablePrivate}
        <span class="comment-next-skeleton-chip"></span>
      {/if}
      {#if showCaptcha}
        <span class="comment-next-skeleton-captcha"></span>
      {/if}
      <span class="comment-next-skeleton-submit"></span>
    </div>
  </div>
</div>

<style>
  .comment-next-skeleton {
    overflow: hidden;
    border-radius: var(--comment-next-radius-lg, 0.75rem);
    background: var(--comment-next-bg-color, #ffffff);
  }

  .comment-next-skeleton-editor {
    min-height: var(--comment-next-editor-min-height, 12.5rem);
    box-sizing: border-box;
    padding: 1.125rem;
    background: var(
      --comment-next-skeleton-editor-bg,
      linear-gradient(180deg, rgb(251 253 253 / 0.94), rgb(255 255 255 / 0.96)),
      var(--comment-next-editor-bg-color, #ffffff)
    );
  }

  .comment-next-skeleton-topline,
  .comment-next-skeleton-fields,
  .comment-next-skeleton-footer,
  .comment-next-skeleton-tools,
  .comment-next-skeleton-actions {
    display: flex;
    align-items: center;
  }

  .comment-next-skeleton-topline {
    gap: 0.5rem;
  }

  .comment-next-skeleton-lines {
    display: grid;
    gap: 0.625rem;
    margin-top: 1.5rem;
  }

  .comment-next-skeleton-bar,
  .comment-next-skeleton-dot,
  .comment-next-skeleton-field-icon,
  .comment-next-skeleton-field-line,
  .comment-next-skeleton-account-avatar,
  .comment-next-skeleton-ai-action,
  .comment-next-skeleton-tool,
  .comment-next-skeleton-chip,
  .comment-next-skeleton-captcha,
  .comment-next-skeleton-submit {
    position: relative;
    overflow: hidden;
    background: var(--comment-next-skeleton-base-color, #edf2f5);
  }

  .comment-next-skeleton-bar::after,
  .comment-next-skeleton-dot::after,
  .comment-next-skeleton-field-icon::after,
  .comment-next-skeleton-field-line::after,
  .comment-next-skeleton-account-avatar::after,
  .comment-next-skeleton-ai-action::after,
  .comment-next-skeleton-tool::after,
  .comment-next-skeleton-chip::after,
  .comment-next-skeleton-captcha::after,
  .comment-next-skeleton-submit::after {
    position: absolute;
    inset: 0;
    background: linear-gradient(
      90deg,
      transparent,
      var(--comment-next-skeleton-highlight-color, rgb(255 255 255 / 0.78)),
      transparent
    );
    animation: comment-next-skeleton-shimmer 1.35s ease-in-out infinite;
    content: "";
    transform: translateX(-100%);
  }

  .comment-next-skeleton-dot {
    width: 1.75rem;
    height: 1.75rem;
    border-radius: 999px;
  }

  .comment-next-skeleton-bar {
    display: block;
    height: 0.75rem;
    border-radius: 999px;
  }

  .comment-next-skeleton-bar-short {
    width: 8.5rem;
  }

  .comment-next-skeleton-fields {
    display: grid;
    grid-template-columns: auto repeat(3, minmax(0, 1fr));
    align-items: center;
    gap: 0;
    box-sizing: border-box;
    padding: 0.625rem 0.875rem;
    border-bottom: 1px solid var(--comment-next-border-subtle-color, #e0e6ee);
    border-radius: var(--comment-next-radius-lg, 0.875rem) var(--comment-next-radius-lg, 0.875rem) 0 0;
    background: var(
      --comment-next-account-fields-bg,
      linear-gradient(180deg, rgb(252 253 253 / 0.98), rgb(249 251 251 / 0.98))
    );
  }

  .comment-next-skeleton-fields-logged-in {
    grid-template-columns: auto minmax(0, 1fr);
  }

  .comment-next-skeleton-account-avatar {
    width: 2.125rem;
    height: 2.125rem;
    margin-right: 0.75rem;
    border-radius: 999px;
  }

  .comment-next-skeleton-current-user {
    display: flex;
    min-width: 0;
    align-items: center;
    gap: 0.5rem;
  }

  .comment-next-skeleton-field {
    position: relative;
    display: flex;
    align-items: center;
    height: 2.25rem;
    gap: 0.625rem;
    box-sizing: border-box;
    padding: 0 0.875rem;
    background: transparent;
  }

  .comment-next-skeleton-field + .comment-next-skeleton-field::before {
    position: absolute;
    top: 0.45rem;
    bottom: 0.45rem;
    left: 0;
    width: 1px;
    background: var(--comment-next-field-divider-color, #dbe4ed);
    content: "";
  }

  .comment-next-skeleton-field-icon {
    width: 1rem;
    height: 1rem;
    border-radius: 0.375rem;
  }

  .comment-next-skeleton-field-line {
    height: 0.75rem;
    border-radius: 999px;
  }

  .comment-next-skeleton-footer {
    justify-content: space-between;
    min-height: 3.625rem;
    gap: 0.875rem;
    padding: 0 0.875rem;
    border-top: 1px solid var(--comment-next-border-subtle-color, #e2e8ef);
    background: var(--comment-next-footer-bg-color, #fbfcfc);
  }

  .comment-next-skeleton-tools,
  .comment-next-skeleton-actions {
    gap: 0.5rem;
  }

  .comment-next-skeleton-ai-action {
    width: 5.75rem;
    height: 2.125rem;
    border-radius: 0.5625rem;
  }

  .comment-next-skeleton-tool {
    width: 1.875rem;
    height: 1.875rem;
    border-radius: 0.5rem;
  }

  .comment-next-skeleton-chip {
    width: 3rem;
    height: 1.625rem;
    border-radius: 999px;
  }

  .comment-next-skeleton-captcha {
    width: 7.75rem;
    height: 2rem;
    border-radius: 0.5rem;
  }

  .comment-next-skeleton-submit {
    width: 5.75rem;
    height: 2.25rem;
    border-radius: 0.5rem;
  }

  @keyframes comment-next-skeleton-shimmer {
    to {
      transform: translateX(100%);
    }
  }

  @media (max-width: 780px) {
    .comment-next-skeleton-footer {
      align-items: stretch;
      flex-direction: column;
      padding: 0.875rem;
    }

    .comment-next-skeleton-actions {
      justify-content: space-between;
      flex-wrap: wrap;
    }
  }

  @media (max-width: 720px) {
    .comment-next-skeleton-fields {
      grid-template-columns: auto minmax(0, 1fr);
      gap: 0.125rem;
      padding: 0.625rem;
    }

    .comment-next-skeleton-fields:not(.comment-next-skeleton-fields-logged-in) .comment-next-skeleton-account-avatar {
      grid-row: span 3;
      align-self: start;
      margin-top: 0.25rem;
    }

    .comment-next-skeleton-fields:not(.comment-next-skeleton-fields-logged-in) .comment-next-skeleton-field {
      grid-column: 2;
    }

    .comment-next-skeleton-field + .comment-next-skeleton-field::before {
      top: -0.0625rem;
      right: 0.75rem;
      bottom: auto;
      left: 0.75rem;
      width: auto;
      height: 1px;
    }
  }

  @media (prefers-reduced-motion: reduce) {
    .comment-next-skeleton-bar::after,
    .comment-next-skeleton-dot::after,
    .comment-next-skeleton-field-icon::after,
    .comment-next-skeleton-field-line::after,
    .comment-next-skeleton-account-avatar::after,
    .comment-next-skeleton-ai-action::after,
    .comment-next-skeleton-tool::after,
    .comment-next-skeleton-chip::after,
    .comment-next-skeleton-captcha::after,
    .comment-next-skeleton-submit::after {
      animation: none;
    }
  }
</style>
