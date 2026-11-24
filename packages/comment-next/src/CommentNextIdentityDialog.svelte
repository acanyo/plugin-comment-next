<script lang="ts">
import type { Snippet } from 'svelte';
import { onMount, tick } from 'svelte';
import CommentNextIcon from './CommentNextIcon.svelte';
import { isolateTypingShortcuts } from './utils/isolate-typing-shortcuts';

const {
  id,
  loggedIn,
  allowAnonymous,
  displayName,
  avatar,
  guestFields,
  guestSubmitLabel,
  loginLabel,
  onLogin,
  onGuestConfirm,
  onClose,
}: {
  id: string;
  loggedIn: boolean;
  allowAnonymous: boolean;
  displayName: string;
  avatar: Snippet;
  guestFields: Snippet;
  guestSubmitLabel: string;
  loginLabel: string;
  onLogin: () => void;
  onGuestConfirm: () => void;
  onClose: () => void;
} = $props();

let dialog: HTMLDialogElement;
let guestForm = $state<HTMLFormElement>();
let guestChoice = $state<HTMLButtonElement>();
let editingGuest = $state(false);
const showGuestForm = $derived(editingGuest && !loggedIn && allowAnonymous);
const title = $derived(
  loggedIn
    ? '当前评论身份'
    : showGuestForm
      ? '游客资料'
      : allowAnonymous
        ? '选择评论身份'
        : loginLabel
);

onMount(() => {
  const element = dialog;
  if (typeof element.showModal === 'function') element.showModal();
  else element.open = true;
  return () => {
    if (!element.open) return;
    if (typeof element.close === 'function') element.close();
    else element.open = false;
  };
});

async function showGuestDetails() {
  editingGuest = true;
  await tick();
  const input =
    guestForm?.querySelector<HTMLInputElement>('input:invalid') ??
    guestForm?.querySelector<HTMLInputElement>('input');
  input?.focus();
}

async function backToChoices() {
  editingGuest = false;
  await tick();
  guestChoice?.focus();
}

function handleCancel(event: Event) {
  event.preventDefault();
  onClose();
}

function handleBackdrop(event: MouseEvent) {
  if (event.target === dialog) onClose();
}

function handleGuestSubmit(event: SubmitEvent) {
  event.preventDefault();
  if (guestForm?.reportValidity()) onGuestConfirm();
}
</script>

<dialog
  {id}
  bind:this={dialog}
  use:isolateTypingShortcuts
  class="comment-next-identity-dialog"
  aria-labelledby={`${id}-title`}
  oncancel={handleCancel}
  onclick={handleBackdrop}
>
  <div class="comment-next-identity-dialog-content">
    <header class="comment-next-identity-header">
      <h2 id={`${id}-title`}>{title}</h2>
      <button class="comment-next-identity-close" type="button" aria-label="关闭身份弹窗" onclick={onClose}>
        <CommentNextIcon name="x" size={18} />
      </button>
    </header>

    {#if loggedIn}
      <div class="comment-next-identity-profile">
        {@render avatar()}
        <div>
          <strong>{displayName}</strong>
          <span>账号评论</span>
        </div>
      </div>
    {:else if showGuestForm}
      <form bind:this={guestForm} class="comment-next-identity-form" onsubmit={handleGuestSubmit}>
        {@render guestFields()}
        <div class="comment-next-identity-form-actions">
          <button class="comment-next-identity-back" type="button" onclick={backToChoices}>
            <CommentNextIcon name="chevronLeft" size={14} />
            返回
          </button>
          <button class="comment-next-identity-confirm" type="submit">{guestSubmitLabel}</button>
        </div>
      </form>
    {:else}
      {#if !allowAnonymous}
        <p class="comment-next-identity-description">本站仅允许登录用户留言。</p>
      {/if}
      <div class="comment-next-identity-choices">
        <button class="comment-next-identity-choice" type="button" onclick={onLogin}>
          <span class="comment-next-identity-choice-icon"><CommentNextIcon name="login" size={18} /></span>
          <span>账号登录</span>
          <CommentNextIcon name="chevronRight" size={16} />
        </button>
        {#if allowAnonymous}
          <button bind:this={guestChoice} class="comment-next-identity-choice" type="button" onclick={showGuestDetails}>
            <span class="comment-next-identity-choice-icon"><CommentNextIcon name="user" size={18} /></span>
            <span>游客评论</span>
            <CommentNextIcon name="chevronRight" size={16} />
          </button>
        {/if}
      </div>
    {/if}
  </div>
</dialog>

<style>
  .comment-next-identity-dialog {
    position: fixed;
    inset: 0;
    box-sizing: border-box;
    width: min(calc(100% - 1.5rem), 25.5rem);
    max-width: none;
    max-height: calc(100% - 1.5rem);
    max-height: calc(100dvh - 1.5rem);
    margin: auto;
    padding: 0;
    overflow: auto;
    overscroll-behavior: contain;
    border: 1px solid var(--comment-next-composer-rule);
    border-radius: var(--comment-next-composer-radius);
    background-color: var(--comment-next-modal-bg-color, Canvas);
    background-image: linear-gradient(var(--comment-next-composer-background), var(--comment-next-composer-background));
    color: inherit;
    font: inherit;
    box-shadow: 0 0.875rem 2.25rem rgb(0 0 0 / 0.22);
  }

  .comment-next-identity-dialog::backdrop {
    background: var(--comment-next-modal-backdrop-color, rgb(15 23 42 / 0.42));
    background: color-mix(
      in srgb,
      var(--comment-next-modal-backdrop-color, rgb(0 0 0 / 0.64)) 64%,
      transparent
    );
  }

  .comment-next-identity-dialog-content {
    --comment-next-composer-control-height: 2.375rem;
    --comment-next-guest-fields-columns: repeat(2, minmax(0, 1fr));
    --comment-next-guest-website-column: 1 / -1;
    padding: 1rem;
  }

  .comment-next-identity-header,
  .comment-next-identity-profile,
  .comment-next-identity-form-actions {
    display: flex;
    align-items: center;
    gap: 0.75rem;
  }

  .comment-next-identity-header {
    justify-content: space-between;
    margin-block-end: 0.625rem;
  }

  .comment-next-identity-header h2 {
    margin: 0;
    font-size: 1.0625rem;
    font-weight: 650;
    line-height: 1.4;
  }

  .comment-next-identity-close,
  .comment-next-identity-choice,
  .comment-next-identity-back,
  .comment-next-identity-confirm {
    box-sizing: border-box;
    border: 1px solid transparent;
    border-radius: var(--comment-next-composer-control-radius);
    background: transparent;
    color: inherit;
    font: inherit;
    cursor: pointer;
  }

  .comment-next-identity-close {
    display: grid;
    flex: none;
    place-items: center;
    width: 2rem;
    height: 2rem;
    padding: 0;
    color: var(--comment-next-muted-color, #667085);
  }

  .comment-next-identity-description {
    margin: 0 0 0.875rem;
    color: var(--comment-next-muted-color, #667085);
    font-size: 0.875rem;
    line-height: 1.5;
  }

  .comment-next-identity-choices {
    display: grid;
    overflow: hidden;
    border: 1px solid var(--comment-next-composer-rule);
    border-radius: var(--comment-next-composer-control-radius);
  }

  .comment-next-identity-choice {
    display: grid;
    grid-template-columns: auto minmax(0, 1fr) auto;
    align-items: center;
    gap: 0.625rem;
    min-height: 3rem;
    padding: 0.375rem 0.625rem;
    border: 0;
    border-radius: 0;
    font-size: 0.875rem;
    font-weight: 600;
    text-align: start;
  }

  .comment-next-identity-choice + .comment-next-identity-choice {
    border-block-start: 1px solid var(--comment-next-composer-rule);
  }

  .comment-next-identity-choice-icon {
    display: grid;
    place-items: center;
    width: 1.75rem;
    height: 1.75rem;
  }

  .comment-next-identity-choice-icon,
  .comment-next-identity-choice > :global(.comment-next-icon) {
    color: var(--comment-next-muted-color, #667085);
  }

  .comment-next-identity-profile {
    --comment-next-composer-avatar-size: 2.375rem;
    padding: 0.75rem;
    border: 1px solid var(--comment-next-composer-rule);
    border-radius: var(--comment-next-composer-control-radius);
    background: var(--comment-next-composer-rail-bg);
  }

  .comment-next-identity-profile strong,
  .comment-next-identity-profile div > span {
    display: block;
    overflow-wrap: anywhere;
  }

  .comment-next-identity-profile div > span {
    color: var(--comment-next-muted-color, #667085);
    font-size: 0.8125rem;
  }

  .comment-next-identity-form {
    margin: 0;
  }

  .comment-next-identity-form-actions {
    justify-content: flex-end;
    gap: 0.375rem;
    margin-block-start: 0.875rem;
    padding-block-start: 0.625rem;
    border-block-start: 1px solid var(--comment-next-composer-rule);
  }

  .comment-next-identity-back,
  .comment-next-identity-confirm {
    display: inline-flex;
    align-items: center;
    gap: 0.1875rem;
    min-height: var(--comment-next-composer-control-height);
    padding-inline: 0.6875rem;
    font-size: 0.8125rem;
    font-weight: 600;
  }

  .comment-next-identity-back {
    color: var(--comment-next-muted-color, #667085);
  }

  .comment-next-identity-confirm {
    background: var(--comment-next-composer-tint);
    color: var(--comment-next-composer-action-ink);
    font-weight: 650;
  }

  .comment-next-identity-close:hover,
  .comment-next-identity-back:hover,
  .comment-next-identity-choice:hover {
    background: var(--comment-next-composer-rail-bg);
    color: var(--comment-next-text-color, #172033);
  }

  .comment-next-identity-confirm:hover {
    background: var(--comment-next-composer-action-hover);
  }

  .comment-next-identity-confirm:active {
    background: var(--comment-next-composer-submit-bg);
    color: var(--comment-next-composer-submit-text, var(--comment-next-composer-on-accent, #fff));
  }

  .comment-next-identity-dialog button:focus-visible {
    outline: 2px solid var(--comment-next-focus-border-color, currentColor);
    outline-offset: 2px;
  }

  .comment-next-identity-dialog .comment-next-identity-choice:focus-visible {
    outline-offset: -2px;
  }

  @media (max-width: 26rem) {
    .comment-next-identity-dialog-content {
      --comment-next-composer-control-height: 2.625rem;
      --comment-next-guest-fields-columns: minmax(0, 1fr);
    }

    .comment-next-identity-close {
      width: 2.5rem;
      height: 2.5rem;
    }
  }
</style>
