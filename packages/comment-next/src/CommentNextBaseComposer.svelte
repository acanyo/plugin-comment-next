<script lang="ts">
import { onMount } from 'svelte';
import {
  getAnonymousAvatarUrl,
  getDefaultAnonymousAvatarUrl,
} from './avatar/weavatar';
import CommentNextAccountFields from './CommentNextAccountFields.svelte';
import CommentNextEditor from './CommentNextEditor.svelte';
import CommentNextFooter from './CommentNextFooter.svelte';
import CommentNextIcon from './CommentNextIcon.svelte';
import CommentNextSkeleton from './CommentNextSkeleton.svelte';
import {
  CommentNextCommentError,
  getCommentSubmitErrorMessage,
} from './services/comments';
import {
  loadAnonymousAccount,
  saveAnonymousAccount,
} from './services/anonymous-account';
import { type CurrentUser, fetchCurrentUser } from './services/current-user';
import type { CommentNextComment } from './types/comment';
import type { CommentNextComposerSubmitPayload } from './types/composer';
import type { CommentNextEmoteItem, CommentNextEmotePack } from './types/emote';
import { sanitizeCommentSubmitHtml } from './utils/html';

type CommentNextComposerVariant = 'comment' | 'reply';

type CommentNextEditorRef = {
  getHtml: () => string;
  getText: () => string;
  reset: () => void;
  focus: () => void;
  insertText: (value: string) => void;
  insertImage: (src: string, alt?: string) => void;
};

const {
  baseUrl = '',
  loggedIn = false,
  allowAnonymous = true,
  showCaptcha = false,
  enablePrivate = true,
  loading = false,
  submitting = false,
  commandMenuOpen = false,
  inlineSuggestion = false,
  selectionTools = false,
  placeholder = '写下你的评论...',
  subject = '',
  targetReady = true,
  targetMissingMessage = '评论目标缺失，无法提交。',
  successMessage = '评论提交成功。',
  reviewPendingMessage = '',
  genericErrorMessage = '评论提交失败，请稍后再试。',
  createdEventName = '',
  variant = 'comment',
  compact = false,
  showHeader = false,
  replyToName = '',
  submitLabel = '提交',
  loginLabel = '登录后评论',
  aiLabel = 'AI 写作',
  showAi = true,
  showInsertTools = true,
  emotePacks = [],
  loginRedirectHash = '',
  onSubmit = async () => undefined,
  onCreated = () => {},
  onCancel = () => {},
}: {
  baseUrl?: string;
  loggedIn?: boolean;
  allowAnonymous?: boolean;
  showCaptcha?: boolean;
  enablePrivate?: boolean;
  loading?: boolean;
  submitting?: boolean;
  commandMenuOpen?: boolean;
  inlineSuggestion?: boolean;
  selectionTools?: boolean;
  placeholder?: string;
  subject?: string;
  targetReady?: boolean;
  targetMissingMessage?: string;
  successMessage?: string;
  reviewPendingMessage?: string;
  genericErrorMessage?: string;
  createdEventName?: string;
  variant?: CommentNextComposerVariant;
  compact?: boolean;
  showHeader?: boolean;
  replyToName?: string;
  submitLabel?: string;
  loginLabel?: string;
  aiLabel?: string;
  showAi?: boolean;
  showInsertTools?: boolean;
  emotePacks?: CommentNextEmotePack[];
  loginRedirectHash?: string;
  onSubmit?: (
    payload: CommentNextComposerSubmitPayload
  ) => Promise<unknown> | unknown;
  onCreated?: (result: unknown) => void;
  onCancel?: () => void;
} = $props();

let isAiPanelOpen = $state(false);
let showInlineSuggestion = $state(false);
let showSelectionTools = $state(false);
let previousVariant = $state<CommentNextComposerVariant | undefined>();
let aiMode = $state('polish');
let anonymousDisplayName = $state('');
let anonymousEmail = $state('');
let anonymousWebsite = $state('');
let captchaCode = $state('');
let captchaImage = $state('');
let captchaRefreshKey = $state(0);
let currentUser = $state<CurrentUser | undefined>();
let anonymousAvatarUrl = $state(getDefaultAnonymousAvatarUrl());
let composerElement = $state<HTMLFormElement | undefined>();
let editorRef = $state<CommentNextEditorRef | undefined>();
let editorHtml = $state('');
let localSubmitting = $state(false);
let submitMessage = $state('');
let submitMessageType = $state<'error' | 'success'>('error');

const isLoggedIn = $derived(Boolean(currentUser) || loggedIn);
const isSubmitting = $derived(submitting || localSubmitting);
const showAccountBar = $derived(isLoggedIn || allowAnonymous);
const currentUserDisplayName = $derived(
  currentUser?.displayName || (loggedIn ? '已登录用户' : '')
);
const currentUserAvatar = $derived(currentUser?.avatar || '');
const anonymousMissingRequired = $derived(
  !isLoggedIn &&
    allowAnonymous &&
    (!anonymousDisplayName.trim() || !anonymousEmail.trim())
);
const captchaRequired = $derived(!isLoggedIn && allowAnonymous && showCaptcha);
const captchaMissingRequired = $derived(captchaRequired && !captchaCode.trim());
const submitDisabledReason = $derived(
  anonymousMissingRequired
    ? '请先填写昵称和邮箱'
    : captchaMissingRequired
      ? '请先填写验证码'
      : ''
);
const editorTopRounded = $derived(!showHeader && !showAccountBar);

$effect(() => {
  isAiPanelOpen = commandMenuOpen;
  showInlineSuggestion = inlineSuggestion;
  showSelectionTools = selectionTools;
});

$effect(() => {
  if (previousVariant !== variant) {
    previousVariant = variant;
    aiMode = variant === 'reply' ? 'reply' : 'polish';
  }
});

$effect(() => {
  if (!captchaRequired) {
    captchaCode = '';
  }
});

$effect(() => {
  const email = anonymousEmail;
  let cancelled = false;

  getAnonymousAvatarUrl(email)
    .then((avatarUrl) => {
      if (!cancelled) {
        anonymousAvatarUrl = avatarUrl;
      }
    })
    .catch(() => {
      if (!cancelled) {
        anonymousAvatarUrl = getDefaultAnonymousAvatarUrl();
      }
    });

  return () => {
    cancelled = true;
  };
});

onMount(() => {
  let cancelled = false;

  restoreAnonymousAccount();

  if (variant === 'reply') {
    window.setTimeout(() => {
      editorRef?.focus();
      composerElement?.scrollIntoView({
        block: 'center',
        inline: 'start',
        behavior: 'smooth',
      });
    }, 0);
  }

  fetchCurrentUser(baseUrl)
    .then((user) => {
      if (!cancelled) {
        currentUser = user;
      }
    })
    .catch(() => {
      if (!cancelled) {
        currentUser = undefined;
      }
    });

  return () => {
    cancelled = true;
  };
});

function handleLogin() {
  const redirectHash = loginRedirectHash || window.location.hash;
  window.location.href = `/login?redirect_uri=${encodeURIComponent(
    window.location.pathname + redirectHash
  )}`;
}

async function handleSubmit(event: SubmitEvent) {
  event.preventDefault();

  if (isSubmitting) {
    return;
  }

  submitMessage = '';

  if (!targetReady) {
    showSubmitMessage(targetMissingMessage);
    return;
  }

  if (!isLoggedIn && !allowAnonymous) {
    handleLogin();
    return;
  }

  if (anonymousMissingRequired) {
    showSubmitMessage('请先填写昵称和邮箱。');
    return;
  }

  if (captchaMissingRequired) {
    showSubmitMessage('请先填写验证码。');
    return;
  }

  const content = sanitizeCommentSubmitHtml(editorRef?.getHtml() || editorHtml);

  if (!hasContent(content)) {
    showSubmitMessage(
      variant === 'reply' ? '请先输入回复内容。' : '请先输入评论内容。'
    );
    editorRef?.focus();
    return;
  }

  const form = event.currentTarget as HTMLFormElement;
  const formData = new FormData(form);
  const payload: CommentNextComposerSubmitPayload = {
    content,
    hidden: enablePrivate && formData.get('hidden') === 'on',
    captchaCode,
    owner: isLoggedIn
      ? undefined
      : {
          displayName: anonymousDisplayName.trim(),
          email: anonymousEmail.trim(),
          website: anonymousWebsite.trim(),
        },
  };

  try {
    localSubmitting = true;

    const result = await onSubmit(payload);

    storeAnonymousAccount();
    editorRef?.reset();
    captchaCode = '';
    captchaImage = '';
    captchaRefreshKey += 1;
    showSubmitMessage(resolveSubmitSuccessMessage(result), 'success');

    if (createdEventName) {
      window.dispatchEvent(new CustomEvent(createdEventName));
    }

    onCreated(result);
  } catch (error) {
    if (error instanceof CommentNextCommentError) {
      if (error.captchaRequired) {
        captchaCode = '';
        captchaImage = error.captchaImage ?? '';
        captchaRefreshKey += error.captchaImage ? 0 : 1;
      }

      showSubmitMessage(getCommentSubmitErrorMessage(error));
      return;
    }

    if (error instanceof Error && error.message) {
      showSubmitMessage(error.message);
      return;
    }

    console.error(error);
    showSubmitMessage(genericErrorMessage);
  } finally {
    localSubmitting = false;
  }
}

function showSubmitMessage(
  message: string,
  type: 'error' | 'success' = 'error'
) {
  submitMessage = message;
  submitMessageType = type;
}

function hasContent(html: string): boolean {
  if (!html.trim()) {
    return false;
  }

  const template = document.createElement('template');
  template.innerHTML = html;

  return Boolean(
    template.content.textContent?.trim() ||
      template.content.querySelector('img[src]')
  );
}

function resolveSubmitSuccessMessage(result: unknown): string {
  if (isPendingReviewComment(result)) {
    return (
      reviewPendingMessage ||
      (variant === 'reply'
        ? '回复提交成功，审核后可见。'
        : '评论提交成功，审核后可见。')
    );
  }

  return successMessage;
}

function isPendingReviewComment(result: unknown): result is CommentNextComment {
  return (
    result !== null &&
    typeof result === 'object' &&
    'approved' in result &&
    (result as CommentNextComment).approved === false
  );
}

function restoreAnonymousAccount() {
  const account = loadAnonymousAccount();

  if (!account) {
    return;
  }

  anonymousDisplayName = account.displayName;
  anonymousEmail = account.email;
  anonymousWebsite = account.website ?? '';
}

function storeAnonymousAccount() {
  if (isLoggedIn) {
    return;
  }

  saveAnonymousAccount({
    displayName: anonymousDisplayName,
    email: anonymousEmail,
    website: anonymousWebsite,
  });
}

function handleEmoteSelect(item: CommentNextEmoteItem) {
  if (item.type === 'image') {
    editorRef?.insertImage(item.src || item.value, item.label);
    return;
  }

  editorRef?.insertText(item.value);
}
</script>

<form
  bind:this={composerElement}
  class:comment-next-box={variant === "comment"}
  class:comment-next-reply-composer={variant === "reply"}
  class:comment-next-composer-loading={loading}
  class:comment-next-composer-ai-active={isAiPanelOpen || showInlineSuggestion || showSelectionTools}
  class:comment-next-composer-compact={compact}
  class="comment-next-composer"
  data-base-url={baseUrl}
  data-subject={subject}
  onsubmit={handleSubmit}
>
  {#if loading}
    <CommentNextSkeleton showAccountFields={showAccountBar} loggedIn={isLoggedIn} {showCaptcha} {enablePrivate} />
  {:else}
    {#if showHeader}
      <div class="comment-next-reply-composer-head">
        <span class="comment-next-reply-composer-target">
          <CommentNextIcon name="reply" size={14} />
          {#if replyToName}
            回复 <strong>@{replyToName}</strong>
          {:else}
            回复评论
          {/if}
        </span>
        <button type="button" class="comment-next-reply-composer-cancel" onclick={onCancel}>
          取消
        </button>
      </div>
    {/if}

    <CommentNextAccountFields
      visible={showAccountBar}
      loggedIn={isLoggedIn}
      avatarUrl={isLoggedIn ? currentUserAvatar : anonymousAvatarUrl}
      avatarAlt={isLoggedIn ? currentUserDisplayName : anonymousDisplayName}
      userDisplayName={currentUserDisplayName}
      displayName={anonymousDisplayName}
      email={anonymousEmail}
      website={anonymousWebsite}
      onChange={(values) => {
        anonymousDisplayName = values.displayName;
        anonymousEmail = values.email;
        anonymousWebsite = values.website;
      }}
    />

    <CommentNextEditor
      bind:this={editorRef}
      {placeholder}
      aiOpen={isAiPanelOpen}
      inlineSuggestion={showInlineSuggestion}
      selectionTools={showSelectionTools}
      {aiMode}
      topRounded={editorTopRounded}
      onChange={(html) => {
        editorHtml = html;
      }}
      onCloseAiPanel={() => (isAiPanelOpen = false)}
      onModeSelect={(mode) => {
        aiMode = mode;
        isAiPanelOpen = false;
        showInlineSuggestion = true;
        showSelectionTools = false;
      }}
    />

    {#if submitMessage}
      <p class:comment-next-submit-message-success={submitMessageType === "success"} class="comment-next-submit-message">
        {submitMessage}
      </p>
    {/if}

    <CommentNextFooter
      {baseUrl}
      {compact}
      commandMenuOpen={isAiPanelOpen}
      loggedIn={isLoggedIn}
      {allowAnonymous}
      {enablePrivate}
      {showCaptcha}
      {captchaImage}
      {captchaCode}
      {captchaRefreshKey}
      submitting={isSubmitting}
      submitDisabled={anonymousMissingRequired || captchaMissingRequired}
      submitDisabledReason={submitDisabledReason}
      {submitLabel}
      {loginLabel}
      {aiLabel}
      {showAi}
      {showInsertTools}
      {emotePacks}
      onCaptchaChange={(value) => {
        captchaCode = value;
      }}
      onEmoteSelect={handleEmoteSelect}
      onLogin={handleLogin}
      onToggleCommandMenu={() => {
        isAiPanelOpen = !isAiPanelOpen;
        showInlineSuggestion = false;
        showSelectionTools = false;
      }}
    />
  {/if}
</form>

<style>
  .comment-next-composer {
    --at-apply: relative box-border w-full overflow-visible border border-solid [border-color:var(--comment-next-border-color,#d5dde7)] rounded-[var(--comment-next-radius-lg,0.875rem)] [background:var(--comment-next-box-bg,transparent,var(--comment-next-bg-color,#ffffff))] text-[var(--comment-next-text-color,#172033)] shadow-[0_12px_30px_rgb(15_23_42_/_0.04),0_1px_0_rgb(255_255_255_/_0.48)_inset] transition-[border-color,box-shadow,transform] duration-140 ease-in-out;
    font-family: var(
      --comment-next-font-family,
      ui-sans-serif,
      system-ui,
      -apple-system,
      BlinkMacSystemFont,
      "Segoe UI",
      sans-serif
    );
  }

  .comment-next-reply-composer {
    --comment-next-editor-min-height: 6.75rem;
    --at-apply: shadow-[0_10px_24px_rgb(15_23_42_/_0.04),0_1px_0_rgb(255_255_255_/_0.42)_inset];
  }

  .comment-next-composer:focus-within {
    --at-apply: [border-color:var(--comment-next-focus-border-color,#aeb9c6)] shadow-[0_14px_34px_rgb(15_23_42_/_0.05),0_0_0_3px_var(--comment-next-focus-shadow-color,rgb(59_130_246_/_0.14))];
  }

  .comment-next-composer-ai-active {
    --at-apply: [border-color:var(--comment-next-ai-border-color,rgb(191_219_254))];
  }

  .comment-next-composer-loading {
    --at-apply: pointer-events-none overflow-hidden;
  }

  .comment-next-reply-composer-head {
    --at-apply: flex items-center justify-between gap-3 border-b [border-bottom-style:var(--comment-next-divider-style,dashed)] [border-bottom-color:var(--comment-next-divider-color,#d4dde8)] px-3 py-2.5 text-[0.8125rem] text-[var(--comment-next-muted-color,#6b7687)] font-[650];
  }

  .comment-next-reply-composer-target {
    --at-apply: inline-flex min-w-0 items-center gap-[0.35rem];
  }

  .comment-next-reply-composer-target strong {
    --at-apply: text-[var(--comment-next-primary-color,rgb(59,130,246))] font-[760];
  }

  .comment-next-reply-composer-cancel {
    --at-apply: cursor-pointer border-0 bg-transparent text-[0.8125rem] text-[var(--comment-next-muted-color,#6b7687)] font-[650] font-inherit;
  }

  .comment-next-reply-composer-cancel:hover {
    --at-apply: text-[var(--comment-next-text-color,#172033)];
  }

  .comment-next-submit-message {
    --at-apply: m-0 border-t [border-top-style:var(--comment-next-divider-style,dashed)] [border-top-color:var(--comment-next-divider-color,#d4dde8)] px-3.5 py-2.5 text-[0.8125rem] text-[var(--comment-next-error-color,#dc2626)] font-semibold;
  }

  .comment-next-reply-composer .comment-next-submit-message {
    --at-apply: px-3 py-2;
  }

  .comment-next-submit-message-success {
    --at-apply: text-[var(--comment-next-success-color,#16a34a)];
  }

  @media (prefers-reduced-motion: reduce) {
    .comment-next-composer {
      --at-apply: transition-none;
    }
  }
</style>
