<script lang="ts">
import { onDestroy, onMount, tick, untrack } from 'svelte';
import {
  getAnonymousAvatarUrl,
  getDefaultAnonymousAvatarUrl,
} from './avatar/weavatar';
import CommentNextAccountFields from './CommentNextAccountFields.svelte';
import CommentNextAvatar from './CommentNextAvatar.svelte';
import CommentNextCaptchaDialog from './CommentNextCaptchaDialog.svelte';
import CommentNextComposerHeader from './CommentNextComposerHeader.svelte';
import CommentNextEditor from './CommentNextEditor.svelte';
import CommentNextFooter from './CommentNextFooter.svelte';
import CommentNextIcon from './CommentNextIcon.svelte';
import CommentNextIdentityDialog from './CommentNextIdentityDialog.svelte';
import CommentNextNotice from './CommentNextNotice.svelte';
import CommentNextSkeleton from './CommentNextSkeleton.svelte';
import {
  type CommentNextAiMode,
  generateCommentAiSuggestion,
  getAiSuggestionErrorMessage,
} from './services/ai';
import {
  isValidAnonymousAccount,
  loadAnonymousAccount,
  saveAnonymousAccount,
} from './services/anonymous-account';
import {
  type CommentNextCaptchaConfig,
  isLocalImageCaptcha,
} from './services/captcha';
import {
  type CommentDraft,
  cleanDraftHtml,
  clearCommentDraft,
  commentDraftKey,
  readCommentDraft,
  rememberCommentDraftForLogin,
  saveCommentDraft,
  takeCommentDraftAfterLogin,
} from './services/comment-drafts';
import {
  CommentNextCommentError,
  getCommentSubmitErrorMessage,
} from './services/comments';
import type {
  CommentNextAiConfig,
  CommentNextAvatarConfig,
  CommentNextEmoteConfig,
  CommentNextSecurityConfig,
  CommentNextUploadConfig,
} from './services/config';
import { type CurrentUser, fetchCurrentUser } from './services/current-user';
import { fetchQqProfile, isQqEmail } from './services/qq-profile';
import {
  CommentNextUploadError,
  getImageUploadErrorMessage,
  uploadCommentImage,
} from './services/uploads';
import { createSnippetQuoteHtml, normalizeSnippetText } from './snippets/text';
import type { CommentNextComment } from './types/comment';
import type { CommentNextComposerSubmitPayload } from './types/composer';
import type { CommentNextEditorImageKind } from './types/editor';
import type { CommentNextEmoteItem, CommentNextEmotePack } from './types/emote';
import type { CommentNextSnippetAnchor } from './types/snippet';
import { sanitizeCommentSubmitHtml } from './utils/html';
import { inferImageContentType, isImageContentType } from './utils/image-files';
import { isolateTypingShortcuts } from './utils/isolate-typing-shortcuts';
import {
  COMMENT_NEXT_MODAL_OPEN_EVENT,
  notifyCommentNextModalOpen,
} from './utils/overlays';

type CommentNextComposerVariant = 'comment' | 'reply';
type IdentityIntent = 'profile' | 'submit';

type CommentNextLimitedMedia = {
  type: 'emote' | 'lottie';
  width: number;
  height: number;
};

const DEFAULT_EMOTE_MEDIA_LIMIT = 512;
const MIN_EMOTE_MEDIA_LIMIT = 24;
const MAX_EMOTE_MEDIA_LIMIT = 4096;

type CommentNextEditorRef = {
  getHtml: () => string;
  getText: () => string;
  setHtml: (html: string) => void;
  focus: () => void;
  insertText: (value: string) => void;
  mentionAssistant: () => void;
  replaceText: (value: string) => void;
  insertImage: (
    src: string,
    alt?: string,
    kind?: CommentNextEditorImageKind
  ) => void;
  insertLottie: (item: CommentNextEmoteItem) => void;
  replaceImageSrc: (sourceSrc: string, targetSrc: string, alt?: string) => void;
  getLimitedMediaDimensions: () => CommentNextLimitedMedia[];
  consumeCommandTrigger: () => void;
};

type PendingImageUpload = {
  src: string;
  file: File;
};

type DraftSlot = {
  target: string;
  key?: string;
  saved?: string;
  synchronized?: boolean;
};

type SuspendedDraft = {
  draft: CommentDraft;
  uploads: Map<string, PendingImageUpload>;
  saved?: string;
};

const {
  baseUrl = '',
  loggedIn = false,
  allowAnonymous = true,
  showCaptcha = false,
  captchaType = 'ALPHANUMERIC',
  captchaConfig,
  enablePrivate = true,
  loading = false,
  submitting = false,
  commandMenuOpen = false,
  inlineSuggestion = false,
  selectionTools = false,
  placeholder = '写下你的评论...',
  subject = '',
  draftTarget,
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
  submitLabel = '发布评论',
  loginLabel = '登录后评论',
  aiLabel = 'AI 评论',
  aiAssistantName = '评论助手',
  showAi = true,
  showInsertTools = true,
  showAccountFields = true,
  showFooter = true,
  showSubmitArea = true,
  allowImages = true,
  aiConfig,
  uploadConfig,
  emoteConfig,
  avatarConfig,
  emotePacks = [],
  loginRedirectHash = '',
  onChange = () => {},
  onSubmit = async () => undefined,
  onCreated = () => {},
  onCancel = () => {},
}: {
  baseUrl?: string;
  loggedIn?: boolean;
  allowAnonymous?: boolean;
  showCaptcha?: boolean;
  captchaType?: NonNullable<CommentNextSecurityConfig['captcha']>['type'];
  captchaConfig?: CommentNextCaptchaConfig;
  enablePrivate?: boolean;
  loading?: boolean;
  submitting?: boolean;
  commandMenuOpen?: boolean;
  inlineSuggestion?: boolean;
  selectionTools?: boolean;
  placeholder?: string;
  subject?: string;
  draftTarget?: string;
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
  aiAssistantName?: string;
  showAi?: boolean;
  showInsertTools?: boolean;
  showAccountFields?: boolean;
  showFooter?: boolean;
  showSubmitArea?: boolean;
  allowImages?: boolean;
  aiConfig?: CommentNextAiConfig;
  uploadConfig?: CommentNextUploadConfig;
  emoteConfig?: CommentNextEmoteConfig;
  avatarConfig?: CommentNextAvatarConfig;
  emotePacks?: CommentNextEmotePack[];
  loginRedirectHash?: string;
  onChange?: (html: string) => void;
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
let aiMode = $state<CommentNextAiMode>('generate');
let aiGenerating = $state(false);
let aiSuggestionText = $state('');
let aiSuggestionMode = $state<CommentNextAiMode>('generate');
let aiSuggestionTags = $state<string[]>([]);
let anonymousDisplayName = $state('');
let anonymousEmail = $state('');
let anonymousWebsite = $state('');
let captchaCode = $state('');
let captchaImage = $state('');
let captchaRefreshKey = $state(0);
let captchaDialogOpen = $state(false);
let captchaDialogError = $state('');
let externalCaptchaStarting = $state(false);
let userLookup = $state<{ baseUrl: string; user?: CurrentUser }>();
let anonymousAvatarUrl = $state(getDefaultAnonymousAvatarUrl());
let qqProfileLookupRequestId = 0;
// biome-ignore lint/style/useConst: Svelte assigns this through bind:this.
let composerElement = $state<HTMLFormElement | undefined>();
// biome-ignore lint/style/useConst: Svelte assigns this through bind:this.
let editorRef = $state<CommentNextEditorRef | undefined>();
let editorHtml = $state('');
let localSubmitting = $state(false);
let imageUploading = $state(false);
let submitMessage = $state('');
let submitMessageType = $state<'error' | 'success'>('error');
let snippetAnchor = $state<CommentNextSnippetAnchor | undefined>();
let privateComment = $state(false);
let restoredDraft = $state(false);
let identityPrompt = $state<{
  intent: IdentityIntent;
  draftTarget?: string;
  trigger?: HTMLElement;
}>();
const componentId = $props.id();
const identityDialogId = `${componentId}-identity`;
let pendingImageUploads = new Map<string, PendingImageUpload>();
const suspendedDrafts = new Map<string, SuspendedDraft>();
let draftSlot: DraftSlot | undefined;
let draftEditor: CommentNextEditorRef | undefined;

const currentUser = $derived(
  userLookup?.baseUrl === baseUrl ? userLookup?.user : undefined
);
const isLoggedIn = $derived(Boolean(currentUser) || loggedIn);
const isSubmitting = $derived(submitting || localSubmitting);
const canSubmitPrivate = $derived(
  enablePrivate && isLoggedIn && showFooter && showSubmitArea
);
const snippetQuote = $derived(normalizeSnippetText(snippetAnchor?.exact ?? ''));
const draftKey = $derived(
  draftTarget && userLookup?.baseUrl === baseUrl && (currentUser || !loggedIn)
    ? commentDraftKey(draftTarget, currentUser?.name)
    : undefined
);
const isReply = $derived(variant === 'reply');
const showAnonymousFields = $derived(
  showAccountFields && !isLoggedIn && allowAnonymous
);
const useIdentityDialog = $derived(
  showAccountFields && showFooter && showSubmitArea
);
const showInlineAccountFields = $derived(
  showAnonymousFields && !useIdentityDialog
);
const currentUserDisplayName = $derived(
  currentUser?.displayName ||
    currentUser?.name ||
    (loggedIn ? '已登录用户' : '')
);
const composerIdentity = $derived(
  isLoggedIn ? currentUserDisplayName : anonymousDisplayName.trim() || '访客'
);
const composerAvatarUrl = $derived(
  showAccountFields && isLoggedIn
    ? currentUser?.avatar
    : showAnonymousFields && anonymousEmail.trim()
      ? anonymousAvatarUrl
      : undefined
);
const anonymousMissingRequired = $derived(
  !isLoggedIn &&
    allowAnonymous &&
    (!anonymousDisplayName.trim() || !anonymousEmail.trim())
);
const captchaRequired = $derived(!isLoggedIn && allowAnonymous && showCaptcha);
const captchaMissingRequired = $derived(captchaRequired && !captchaCode.trim());
const captchaMissingMessage = $derived(
  isLocalImageCaptcha(captchaType) ? '请先填写验证码' : '请先完成验证'
);
const externalCaptchaRequired = $derived(
  captchaRequired && !isLocalImageCaptcha(captchaType)
);
const resolvedSubmitLabel = $derived(
  externalCaptchaStarting
    ? '验证中'
    : imageUploading
      ? '上传图片中'
      : isSubmitting
        ? '发布中'
        : submitLabel
);
const imageUploadEnabled = $derived(resolveImageUploadEnabled());
const imageAccept = 'image/*';
const aiWritingEnabled = $derived(showAi && resolveAiWritingEnabled());
const aiMentionEnabled = $derived(
  aiWritingEnabled && Boolean(aiConfig?.mentionAutoReplyEnabled)
);
const resolvedAiLabel = $derived(
  aiConfig?.buttonLabel?.trim() === 'AI 助手'
    ? 'AI 评论'
    : resolveTextOption(aiConfig?.buttonLabel, aiLabel, 'AI 评论')
);
const resolvedAiAssistantName = $derived(
  resolveTextOption(
    aiConfig?.assistantDisplayName || aiConfig?.assistantName,
    aiAssistantName,
    '评论助手'
  )
);
const resolvedAiAssistantMentionName = $derived(
  resolveMentionName(aiConfig?.assistantMentionName, resolvedAiAssistantName)
);
onMount(() => {
  const handleModalOpen = () => {
    closeFloatingPanels();
  };

  window.addEventListener(COMMENT_NEXT_MODAL_OPEN_EVENT, handleModalOpen);

  return () => {
    window.removeEventListener(COMMENT_NEXT_MODAL_OPEN_EVENT, handleModalOpen);
  };
});

$effect(() => {
  if (!aiWritingEnabled) isAiPanelOpen = false;
});

$effect(() => {
  isAiPanelOpen = commandMenuOpen;
  showInlineSuggestion = inlineSuggestion;
  showSelectionTools = selectionTools;
});

$effect(() => {
  if (previousVariant !== variant) {
    previousVariant = variant;
    aiMode = variant === 'reply' ? 'reply' : 'generate';
    aiSuggestionMode = aiMode;
  }
});

$effect(() => {
  if (!captchaRequired) {
    captchaCode = '';
    captchaDialogError = '';
    captchaDialogOpen = false;
    externalCaptchaStarting = false;
  }
});

$effect(() => {
  if (
    identityPrompt &&
    (loading ||
      !useIdentityDialog ||
      identityPrompt.draftTarget !== draftTarget)
  ) {
    identityPrompt = undefined;
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
});

$effect(() => {
  const lookupBaseUrl = baseUrl;
  let cancelled = false;
  fetchCurrentUser(lookupBaseUrl).then(
    (user) => {
      if (!cancelled) userLookup = { baseUrl: lookupBaseUrl, user };
    },
    () => {
      if (!cancelled) userLookup = { baseUrl: lookupBaseUrl };
    }
  );

  return () => {
    cancelled = true;
  };
});

$effect(() => {
  const editor = editorRef;
  const target = draftTarget;
  const key = draftKey;
  const allowPrivate = canSubmitPrivate;
  if (editor && !isSubmitting) {
    untrack(() => syncDraft(editor, target, key, allowPrivate));
  }
});

onDestroy(() => {
  qqProfileLookupRequestId += 1;
  clearPendingImageUploads();
  for (const draft of suspendedDrafts.values())
    clearPendingImageUploads(draft.uploads);
});

export function focus() {
  editorRef?.focus();
}

export function isBusy(): boolean {
  return (
    isSubmitting ||
    loading ||
    imageUploading ||
    Boolean(identityPrompt) ||
    captchaDialogOpen ||
    externalCaptchaStarting
  );
}

export function reset() {
  snippetAnchor = undefined;
  privateComment = false;
  restoredDraft = false;
  clearPendingImageUploads();
  editorRef?.setHtml('');
  handleEditorChange('');
}

export function quoteSnippet(anchor: CommentNextSnippetAnchor): boolean {
  if (
    isSubmitting ||
    loading ||
    variant !== 'comment' ||
    !normalizeSnippetText(anchor.exact)
  ) {
    return false;
  }
  snippetAnchor = anchor;
  persistDraft();
  composerElement?.scrollIntoView({ block: 'center', behavior: 'instant' });
  editorRef?.focus();
  return true;
}

function syncDraft(
  editor: CommentNextEditorRef,
  target: string | undefined,
  key: string | undefined,
  allowPrivate: boolean
) {
  if (
    draftSlot?.target !== target ||
    (draftSlot?.key && draftSlot.key !== key)
  ) {
    suspendDraft();
    draftSlot = target ? { target, key } : undefined;
    restoreDraft(editor, allowPrivate);
  } else if (draftSlot && key && !draftSlot.key) {
    draftSlot.key = key;
    // Input entered while the current-user lookup was running takes precedence.
    if (hasContent(editorHtml) || snippetAnchor) {
      if (draftEditor !== editor) editor.setHtml(editorHtml);
      persistDraft();
    } else {
      restoreDraft(editor, allowPrivate);
    }
  } else if (draftSlot && draftEditor !== editor) {
    editor.setHtml(editorHtml);
  }
  draftEditor = editor;
}

function suspendDraft() {
  if (
    draftSlot &&
    (pendingImageUploads.size || !draftSlot.synchronized) &&
    (hasContent(editorHtml) || snippetAnchor)
  ) {
    suspendedDrafts.set(draftSlot.key ?? draftSlot.target, {
      draft: { html: editorHtml, snippet: snippetAnchor, privateComment },
      uploads: pendingImageUploads,
      saved: draftSlot.saved,
    });
  } else {
    clearPendingImageUploads();
  }
  pendingImageUploads = new Map();
}

function restoreDraft(editor: CommentNextEditorRef, allowPrivate: boolean) {
  const slot = draftSlot;
  const memoryKey = slot?.key ?? slot?.target;
  const cached = memoryKey ? suspendedDrafts.get(memoryKey) : undefined;
  const pending =
    !cached && slot?.key ? suspendedDrafts.get(slot.target) : undefined;
  const suspended = cached ?? pending;
  const stored =
    !suspended && slot?.key ? readCommentDraft(slot.key) : undefined;
  const afterLogin =
    slot?.key && currentUser
      ? takeCommentDraftAfterLogin(slot.target)
      : undefined;
  const draft = suspended?.draft ?? stored?.draft ?? afterLogin;
  const usable =
    draft && (!draft.privateComment || allowPrivate) ? draft : undefined;
  if (suspended && usable && slot) {
    suspendedDrafts.delete(pending ? slot.target : (slot.key ?? slot.target));
    pendingImageUploads = suspended.uploads;
  }
  if (slot) {
    slot.saved = usable ? (suspended?.saved ?? stored?.serialized) : undefined;
    slot.synchronized = Boolean(usable && stored);
  }
  editorHtml = usable
    ? suspended
      ? usable.html
      : cleanDraftHtml(usable.html, allowImages, emoteConfig?.allowedHosts)
    : '';
  snippetAnchor = usable?.snippet;
  privateComment = usable?.privateComment ?? false;
  restoredDraft = hasContent(editorHtml) || Boolean(snippetAnchor);
  editor.setHtml(editorHtml);
  if (usable && (pending || usable === afterLogin)) persistDraft();
  if (afterLogin && usable === afterLogin && slot?.synchronized) {
    clearCommentDraft(commentDraftKey(slot.target), JSON.stringify(afterLogin));
  }
  onChange(editorHtml);
}

function snapshotDraft(): CommentDraft | undefined {
  const html = cleanDraftHtml(
    editorHtml,
    allowImages,
    emoteConfig?.allowedHosts
  );
  return hasContent(html) || snippetQuote
    ? { html, snippet: snippetAnchor, privateComment }
    : undefined;
}

function persistDraft() {
  const slot = draftSlot;
  if (!slot?.key) return;
  const draft = snapshotDraft();
  if (!draft) {
    if (slot.saved) clearCommentDraft(slot.key, slot.saved);
    slot.saved = undefined;
    slot.synchronized = !hasContent(editorHtml);
    if (!hasContent(editorHtml)) restoredDraft = false;
    return;
  }
  const serialized = JSON.stringify(draft);
  if (serialized === slot.saved) return;
  slot.synchronized = saveCommentDraft(slot.key, serialized);
  if (slot.synchronized) slot.saved = serialized;
}

function handleEditorChange(html: string) {
  editorHtml = html;
  pruneUnusedPendingImages(html);
  persistDraft();
  onChange(html);
}

function openIdentityDialog(intent: IdentityIntent, trigger?: HTMLElement) {
  if (isBusy()) return;
  const root = composerElement?.getRootNode();
  const active =
    root instanceof ShadowRoot ? root.activeElement : document.activeElement;
  const focusedControl =
    active instanceof HTMLElement && composerElement?.contains(active)
      ? active
      : undefined;
  notifyCommentNextModalOpen('identity');
  identityPrompt = {
    intent,
    draftTarget,
    trigger:
      trigger ??
      focusedControl ??
      composerElement?.querySelector<HTMLElement>(
        '.comment-next-submit-button'
      ) ??
      undefined,
  };
}

async function closeIdentityDialog() {
  const trigger = identityPrompt?.trigger;
  identityPrompt = undefined;
  await tick();
  if (!identityPrompt) trigger?.focus({ preventScroll: true });
}

async function confirmGuestIdentity() {
  const prompt = identityPrompt;
  if (!prompt) return;
  await closeIdentityDialog();
  if (prompt.draftTarget !== draftTarget || isLoggedIn || !allowAnonymous)
    return;
  if (prompt.intent === 'profile') {
    storeAnonymousAccount();
    return;
  }
  await submitComposer({ allowCaptchaDialog: true });
}

function handleLogin() {
  if (!isLoggedIn) rememberCommentDraftForLogin(draftTarget, snapshotDraft());
  const redirectHash = loginRedirectHash || window.location.hash;
  window.location.href = `/login?redirect_uri=${encodeURIComponent(
    window.location.pathname + window.location.search + redirectHash
  )}`;
}

function closeFloatingPanels() {
  isAiPanelOpen = false;
  showSelectionTools = false;
}

async function handleSubmit(event: SubmitEvent) {
  event.preventDefault();
  if (isBusy()) return;
  if (showInlineAccountFields && !composerElement?.reportValidity()) return;
  await submitComposer({
    allowCaptchaDialog: true,
    promptForIdentity: useIdentityDialog,
  });
}

async function submitComposer({
  allowCaptchaDialog,
  captchaCodeOverride,
  promptForIdentity = false,
}: {
  allowCaptchaDialog: boolean;
  captchaCodeOverride?: string;
  promptForIdentity?: boolean;
}) {
  if (isSubmitting) {
    return;
  }

  submitMessage = '';

  if (!targetReady) {
    showSubmitMessage(targetMissingMessage);
    return;
  }

  if (!isLoggedIn && !allowAnonymous) {
    if (promptForIdentity) openIdentityDialog('submit');
    else handleLogin();
    return;
  }

  if (privateComment && !canSubmitPrivate) {
    showSubmitMessage('当前无法提交私密评论。');
    return;
  }

  const rawContent = editorRef?.getHtml() || editorHtml;

  if (!hasContent(rawContent)) {
    showSubmitMessage(
      variant === 'reply' ? '请先输入回复内容。' : '请先输入评论内容。'
    );
    editorRef?.focus();
    return;
  }

  const mediaLimitMessage = validateLimitedEmoteDimensions();
  if (mediaLimitMessage) {
    showSubmitMessage(mediaLimitMessage);
    editorRef?.focus();
    return;
  }

  if (!isLoggedIn && promptForIdentity) {
    const account = loadAnonymousAccount();
    if (!account || !isValidAnonymousAccount(account)) {
      openIdentityDialog('submit');
      return;
    }
    restoreAnonymousAccount(account);
  }

  if (anonymousMissingRequired) {
    showSubmitMessage('请先填写昵称和邮箱。');
    return;
  }

  const resolvedCaptchaCode = captchaCodeOverride ?? captchaCode;
  const captchaMissing = captchaRequired && !resolvedCaptchaCode.trim();

  if (captchaMissing && allowCaptchaDialog) {
    openCaptchaDialog();
    return;
  }

  if (captchaMissing) {
    showSubmitMessage(`${captchaMissingMessage}。`);
    return;
  }

  const quote = snippetQuote;
  const anchor = snippetAnchor;
  const hidden = privateComment;
  // Preparing images may be asynchronous. Keep the original submission target.
  const submit = onSubmit;
  const submittedEventName = createdEventName;

  try {
    localSubmitting = true;
    const editorContent = await prepareContentForSubmit(rawContent);

    if (!hasContent(editorContent)) {
      showSubmitMessage(
        variant === 'reply' ? '请先输入回复内容。' : '请先输入评论内容。'
      );
      editorRef?.focus();
      return;
    }

    const payload: CommentNextComposerSubmitPayload = {
      snippet: quote ? anchor : undefined,
      content: quote
        ? `${createSnippetQuoteHtml(quote)}${editorContent}`
        : editorContent,
      hidden,
      captchaCode: resolvedCaptchaCode,
      owner: isLoggedIn
        ? undefined
        : {
            displayName: anonymousDisplayName.trim(),
            email: anonymousEmail.trim(),
            website: anonymousWebsite.trim(),
          },
    };

    const result = await submit(payload);

    storeAnonymousAccount();
    reset();
    captchaCode = '';
    captchaImage = '';
    captchaDialogError = '';
    captchaDialogOpen = false;
    externalCaptchaStarting = false;
    captchaRefreshKey += 1;
    showSubmitMessage(resolveSubmitSuccessMessage(result), 'success');

    if (submittedEventName) {
      window.dispatchEvent(new CustomEvent(submittedEventName));
    }

    onCreated(result);
  } catch (error) {
    if (error instanceof CommentNextCommentError) {
      if (error.captchaRequired) {
        const message = resolveCaptchaSubmitMessage(error);
        captchaCode = '';
        captchaImage = error.captchaImage ?? '';
        captchaRefreshKey += error.captchaImage ? 0 : 1;
        captchaDialogError = message;
        externalCaptchaStarting = false;
        if (!isLocalImageCaptcha(captchaType)) {
          captchaDialogOpen = false;
          showSubmitMessage(message);
          return;
        }
        notifyCommentNextModalOpen('captcha');
        captchaDialogOpen = true;
        return;
      }

      captchaDialogError = '';
      captchaDialogOpen = false;
      externalCaptchaStarting = false;
      showSubmitMessage(getCommentSubmitErrorMessage(error));
      return;
    }

    if (error instanceof CommentNextUploadError) {
      captchaDialogError = '';
      captchaDialogOpen = false;
      externalCaptchaStarting = false;
      showSubmitMessage(getImageUploadErrorMessage(error));
      return;
    }

    if (error instanceof Error && error.message) {
      captchaDialogError = '';
      captchaDialogOpen = false;
      externalCaptchaStarting = false;
      showSubmitMessage(error.message);
      return;
    }

    console.error(error);
    captchaDialogError = '';
    captchaDialogOpen = false;
    externalCaptchaStarting = false;
    showSubmitMessage(genericErrorMessage);
  } finally {
    localSubmitting = false;
  }
}

function openCaptchaDialog() {
  if (isLocalImageCaptcha(captchaType) && !captchaImage) {
    captchaRefreshKey += 1;
  }

  notifyCommentNextModalOpen('captcha');
  submitMessage = '';
  captchaDialogError = '';
  externalCaptchaStarting = externalCaptchaRequired;
  captchaDialogOpen = true;
}

function handleCaptchaConfirm() {
  if (!captchaCode.trim() || isSubmitting) {
    return;
  }

  captchaDialogError = '';
  void submitComposer({ allowCaptchaDialog: false });
}

function handleCaptchaVerified(value: string) {
  const token = value.trim();
  if (!token || isSubmitting) {
    return;
  }

  captchaCode = token;
  captchaDialogError = '';
  captchaDialogOpen = false;
  externalCaptchaStarting = false;
  void submitComposer({
    allowCaptchaDialog: false,
    captchaCodeOverride: token,
  });
}

function resolveCaptchaSubmitMessage(error: CommentNextCommentError): string {
  if (
    !isLocalImageCaptcha(captchaType) &&
    error.type?.includes('captcha-invalid')
  ) {
    return '验证未通过，请重新完成安全验证。';
  }
  return getCommentSubmitErrorMessage(error);
}

function showSubmitMessage(
  message: string,
  type: 'error' | 'success' = 'error'
) {
  externalCaptchaStarting = false;
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
      template.content.querySelector('img[src], halo-lottie[src]')
  );
}

function validateLimitedEmoteDimensions(): string | undefined {
  const maxWidth = resolveEmoteMediaLimit(emoteConfig?.maxWidth);
  const maxHeight = resolveEmoteMediaLimit(emoteConfig?.maxHeight);

  for (const media of editorRef?.getLimitedMediaDimensions() ?? []) {
    if (media.type === 'lottie') {
      continue;
    }

    const label = '表情图片';
    if (media.width > maxWidth) {
      return `${label}宽度不能超过 ${maxWidth}px。`;
    }
    if (media.height > maxHeight) {
      return `${label}高度不能超过 ${maxHeight}px。`;
    }
  }

  return undefined;
}

function resolveEmoteMediaLimit(value: number | undefined): number {
  const numericValue = Number(value);
  if (!Number.isFinite(numericValue)) {
    return DEFAULT_EMOTE_MEDIA_LIMIT;
  }

  return Math.min(
    MAX_EMOTE_MEDIA_LIMIT,
    Math.max(MIN_EMOTE_MEDIA_LIMIT, Math.round(numericValue))
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

function restoreAnonymousAccount(account = loadAnonymousAccount()) {
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

async function handleAnonymousEmailBlur(email: string) {
  const expectedEmail = email.trim().toLowerCase();
  if (isLoggedIn || anonymousDisplayName.trim() || !isQqEmail(expectedEmail)) {
    return;
  }

  const requestId = ++qqProfileLookupRequestId;
  try {
    const profile = await fetchQqProfile(baseUrl, expectedEmail);
    if (
      requestId !== qqProfileLookupRequestId ||
      isLoggedIn ||
      anonymousDisplayName.trim() ||
      anonymousEmail.trim().toLowerCase() !== expectedEmail
    ) {
      return;
    }

    if (profile?.nickname) {
      anonymousDisplayName = profile.nickname;
    }
  } catch {
    // QQ nickname lookup is optional; keep manual input available on failure.
  }
}

function handleEmoteSelect(item: CommentNextEmoteItem) {
  if (item.type === 'lottie') {
    editorRef?.insertLottie(item);
    return;
  }

  if (item.type === 'image') {
    if (!allowImages) {
      return;
    }

    editorRef?.insertImage(item.src || item.value, item.label, 'emote');
    return;
  }

  editorRef?.insertText(item.value);
}

async function handleImageUpload(file: File) {
  submitMessage = '';

  if (!imageUploadEnabled) {
    showSubmitMessage(
      isLoggedIn ? '当前站点未开启图片上传。' : '请先登录后再上传图片。'
    );
    return;
  }

  const localValidationMessage = validateImageFile(file);
  if (localValidationMessage) {
    showSubmitMessage(localValidationMessage);
    return;
  }

  const src = URL.createObjectURL(file);
  pendingImageUploads.set(src, {
    src,
    file,
  });
  editorRef?.insertImage(src, file.name || '图片');
  showSubmitMessage('图片已添加，将在提交评论时上传。', 'success');
}

async function handleImagePaste(files: File[]) {
  if (!files.length) {
    return;
  }

  if (isSubmitting) {
    showSubmitMessage('正在提交中，请稍后再试。');
    return;
  }

  submitMessage = '';

  for (const file of files) {
    await handleImageUpload(file);
  }
}

async function prepareContentForSubmit(rawContent: string): Promise<string> {
  await uploadPendingImagesForSubmit(rawContent);
  return sanitizeCommentSubmitHtml(
    editorRef?.getHtml() || editorHtml,
    emoteConfig?.allowedHosts
  );
}

async function uploadPendingImagesForSubmit(rawContent: string): Promise<void> {
  const referencedUploads = getReferencedPendingImageUploads(rawContent);

  if (!referencedUploads.length) {
    pruneUnusedPendingImages(rawContent);
    return;
  }

  imageUploading = true;
  const uploadBaseUrl = baseUrl;

  try {
    for (const pendingUpload of referencedUploads) {
      const result = await uploadCommentImage({
        baseUrl: uploadBaseUrl,
        file: pendingUpload.file,
      });
      const uploadedUrl = result.url;

      editorRef?.replaceImageSrc(
        pendingUpload.src,
        uploadedUrl,
        result.filename || pendingUpload.file.name || '图片'
      );
      revokePendingImageUpload(pendingUpload.src);
    }
  } finally {
    imageUploading = false;
  }
}

function getReferencedPendingImageUploads(html: string): PendingImageUpload[] {
  if (!pendingImageUploads.size) {
    return [];
  }

  const referencedSrcs = getImageSrcs(html);

  return Array.from(pendingImageUploads.values()).filter((pendingUpload) =>
    referencedSrcs.has(pendingUpload.src)
  );
}

function pruneUnusedPendingImages(html: string) {
  if (!pendingImageUploads.size) {
    return;
  }

  const referencedSrcs = getImageSrcs(html);

  for (const pendingUpload of Array.from(pendingImageUploads.values())) {
    if (!referencedSrcs.has(pendingUpload.src)) {
      revokePendingImageUpload(pendingUpload.src);
    }
  }
}

function getImageSrcs(html: string): Set<string> {
  const srcs = new Set<string>();

  if (!html.trim()) {
    return srcs;
  }

  const template = document.createElement('template');
  template.innerHTML = html;

  for (const image of Array.from(
    template.content.querySelectorAll('img[src]')
  )) {
    const src = image.getAttribute('src');
    if (src) {
      srcs.add(src);
    }
  }

  return srcs;
}

function revokePendingImageUpload(src: string) {
  const pendingUpload = pendingImageUploads.get(src);

  if (!pendingUpload) {
    return;
  }

  URL.revokeObjectURL(pendingUpload.src);
  pendingImageUploads.delete(src);
}

function clearPendingImageUploads(uploads = pendingImageUploads) {
  for (const pendingUpload of uploads.values()) {
    URL.revokeObjectURL(pendingUpload.src);
  }

  uploads.clear();
}

async function handleAiModeSelect(
  mode: CommentNextAiMode | string,
  tags: string[] = []
) {
  const nextMode = normalizeAiMode(mode);

  if (aiGenerating) {
    return;
  }

  editorRef?.consumeCommandTrigger();
  submitMessage = '';
  aiMode = nextMode;
  aiSuggestionMode = nextMode;
  aiSuggestionTags = [...tags];
  aiSuggestionText = '';
  isAiPanelOpen = false;
  showSelectionTools = false;

  if (!aiWritingEnabled) {
    showInlineSuggestion = false;
    showSubmitMessage(
      isLoggedIn ? '当前站点未开启 AI 写作。' : '请先登录后再使用 AI 写作。'
    );
    return;
  }

  const content = editorRef?.getText() ?? textFromHtml(editorHtml);
  const localValidationMessage = validateAiInput(nextMode, content, tags);

  if (localValidationMessage) {
    showInlineSuggestion = false;
    showSubmitMessage(localValidationMessage);
    editorRef?.focus();
    return;
  }

  try {
    showInlineSuggestion = true;
    aiGenerating = true;
    const result = await generateCommentAiSuggestion({
      baseUrl,
      mode: nextMode,
      tags,
      content,
      variant,
      subject,
      replyToName,
    });
    aiSuggestionText = result.text;
  } catch (error) {
    showInlineSuggestion = false;
    showSubmitMessage(getAiSuggestionErrorMessage(error));
  } finally {
    aiGenerating = false;
  }
}

function handleAcceptAiSuggestion() {
  if (!aiSuggestionText || aiGenerating) {
    return;
  }

  editorRef?.replaceText(aiSuggestionText);
  closeAiSuggestion();
}

function handleInsertAiSuggestion() {
  if (!aiSuggestionText || aiGenerating) {
    return;
  }

  editorRef?.insertText(aiSuggestionText);
  closeAiSuggestion();
}

function handleRewriteAiSuggestion() {
  if (aiGenerating) {
    return;
  }

  void handleAiModeSelect(aiSuggestionMode, aiSuggestionTags);
}

function closeAiSuggestion() {
  showInlineSuggestion = false;
  aiSuggestionText = '';
}

function normalizeAiMode(mode: CommentNextAiMode | string): CommentNextAiMode {
  if (mode === 'reply' && variant !== 'reply') {
    return 'polish';
  }

  if (
    mode === 'generate' ||
    mode === 'polish' ||
    mode === 'expand' ||
    mode === 'question' ||
    mode === 'reply' ||
    mode === 'summary'
  ) {
    return mode;
  }

  return variant === 'reply' ? 'reply' : 'polish';
}

function validateAiInput(
  mode: CommentNextAiMode,
  content: string,
  tags: string[]
): string {
  const normalizedContent = content.trim();
  const maxInputLength = Number(aiConfig?.maxInputLength);

  if (
    Number.isFinite(maxInputLength) &&
    maxInputLength > 0 &&
    normalizedContent.length > maxInputLength
  ) {
    return `AI 输入内容不能超过 ${maxInputLength} 个字符。`;
  }

  if (mode === 'generate' && !tags.length) {
    return '请至少选择 1 个评论标签。';
  }

  if (
    mode !== 'generate' &&
    mode !== 'reply' &&
    mode !== 'summary' &&
    !normalizedContent
  ) {
    return '请先输入需要 AI 处理的内容。';
  }

  if (mode === 'summary' && !subject.trim()) {
    return '缺少文章信息，无法总结文章评论。';
  }

  if (mode === 'reply' && !normalizedContent && !replyToName.trim()) {
    return '请先输入回复内容或选择回复对象。';
  }

  return '';
}

function resolveAiWritingEnabled(): boolean {
  if (!aiConfig?.enabled) {
    return false;
  }

  if (!aiConfig.foundationAvailable) {
    return false;
  }

  return isLoggedIn || Boolean(aiConfig.allowAnonymous);
}

function resolveImageUploadEnabled(): boolean {
  if (!allowImages) {
    return false;
  }

  if (!uploadConfig?.enabled) {
    return false;
  }

  if (isLoggedIn) {
    return uploadConfig.authenticatedProvider !== 'DISABLED';
  }

  return Boolean(
    uploadConfig.allowAnonymousUpload &&
      uploadConfig.anonymousProvider !== 'DISABLED'
  );
}

function validateImageFile(file: File): string {
  const contentType = inferImageContentType(file);

  if (!contentType || !isImageContentType(contentType)) {
    return '仅支持上传图片文件。';
  }

  const maxSizeKb = isLoggedIn
    ? uploadConfig?.authenticatedMaxSizeKb
    : uploadConfig?.anonymousMaxSizeKb;
  const normalizedMaxSizeKb = Number(maxSizeKb);

  if (
    Number.isFinite(normalizedMaxSizeKb) &&
    normalizedMaxSizeKb > 0 &&
    file.size > normalizedMaxSizeKb * 1024
  ) {
    return `图片大小不能超过 ${formatMaxImageSize(normalizedMaxSizeKb)}。`;
  }

  return '';
}

function formatMaxImageSize(maxSizeKb: number): string {
  if (maxSizeKb >= 1024) {
    return `${Number((maxSizeKb / 1024).toFixed(1))} MB`;
  }

  return `${maxSizeKb} KB`;
}

function textFromHtml(html: string): string {
  if (!html.trim()) {
    return '';
  }

  const template = document.createElement('template');
  template.innerHTML = html;
  return template.content.textContent?.trim() ?? '';
}

function resolveTextOption(
  configuredValue: string | undefined,
  propValue: string,
  fallback: string
): string {
  return configuredValue?.trim() || propValue.trim() || fallback;
}

function resolveMentionName(
  configuredValue: string | undefined,
  displayName: string
): string {
  const value = configuredValue?.trim() || displayName.trim() || '评论助手';
  return value.startsWith('@') ? value : `@${value}`;
}
</script>

{#snippet identityAvatar()}
  <span class="comment-next-composer-avatar" aria-hidden="true">
    {#if composerAvatarUrl || (isLoggedIn && currentUser?.name)}
      <CommentNextAvatar
        src={composerAvatarUrl}
        seed={isLoggedIn ? currentUser?.name : undefined}
        config={avatarConfig}
        alt={composerIdentity}
        size={24}
      />
    {:else}
      <span class="comment-next-composer-avatar-placeholder">
        <CommentNextIcon name="user" size={14} />
      </span>
    {/if}
  </span>
{/snippet}

{#snippet identityAction()}
  <button
    class="comment-next-composer-identity"
    type="button"
    aria-label={isLoggedIn ? '查看评论身份' : '选择评论身份'}
    aria-haspopup="dialog"
    aria-controls={identityPrompt ? identityDialogId : undefined}
    title={isLoggedIn ? composerIdentity : '选择评论身份'}
    disabled={isSubmitting || captchaDialogOpen || externalCaptchaStarting}
    onclick={(event) => openIdentityDialog('profile', event.currentTarget)}
  >
    {@render identityAvatar()}
  </button>
{/snippet}

{#snippet guestFields()}
  <CommentNextAccountFields
    displayName={anonymousDisplayName}
    email={anonymousEmail}
    website={anonymousWebsite}
    disabled={isSubmitting}
    onChange={(values) => {
      anonymousDisplayName = values.displayName;
      anonymousEmail = values.email;
      anonymousWebsite = values.website;
    }}
    onEmailBlur={handleAnonymousEmailBlur}
  />
{/snippet}

<div class="comment-next-composer-frame">
  <form
    bind:this={composerElement}
    use:isolateTypingShortcuts
    class:comment-next-box={variant === "comment"}
    class:comment-next-reply-composer={variant === "reply"}
    class:comment-next-composer-compact={compact}
    class:comment-next-composer-embedded={!showSubmitArea}
    class="comment-next-composer"
    data-base-url={baseUrl}
    data-subject={subject}
    aria-label={showAccountFields || showHeader ? (isReply ? '回复评论' : '写评论') : undefined}
    novalidate
    onsubmit={handleSubmit}
  >
    <div class="comment-next-composer-surface">
      {#if loading}
        <CommentNextSkeleton {showFooter} {showSubmitArea} showIdentity={useIdentityDialog} />
      {:else}
        {#if showHeader}
          <CommentNextComposerHeader {replyToName} busy={isSubmitting} {onCancel} />
        {/if}

        <div class="comment-next-composer-writing">
          {#if snippetQuote}
            <div class="comment-next-snippet-quote" role="note" aria-label="引用的文章片段">
              <CommentNextIcon name="quote" size={15} />
              <blockquote>{snippetQuote}</blockquote>
              <button
                type="button"
                aria-label="移除文章片段引用"
                disabled={isSubmitting}
                onclick={() => {
                  snippetAnchor = undefined;
                  persistDraft();
                }}
              ><CommentNextIcon name="x" size={15} /></button>
            </div>
          {/if}
          <CommentNextEditor
            bind:this={editorRef}
            {placeholder}
            label={isReply && showHeader ? '回复内容' : '评论内容'}
            aiOpen={isAiPanelOpen}
            inlineSuggestion={showInlineSuggestion}
            selectionTools={showSelectionTools}
            {aiMode}
            suggestionText={aiSuggestionText}
            suggestionLoading={aiGenerating}
            aiAssistantName={resolvedAiAssistantName}
            aiAssistantMentionName={resolvedAiAssistantMentionName}
            aiAssistantAvatar={aiConfig?.assistantAvatar}
            aiMentionEnabled={aiMentionEnabled && !aiGenerating}
            {allowImages}
            readOnly={isSubmitting}
            onChange={handleEditorChange}
            onImagePaste={handleImagePaste}
            onCommandMenuRequest={() => {
              if (!aiWritingEnabled || aiGenerating) {
                return;
              }

              isAiPanelOpen = true;
              showInlineSuggestion = false;
              showSelectionTools = false;
            }}
            onCloseAiPanel={() => (isAiPanelOpen = false)}
            onAcceptSuggestion={handleAcceptAiSuggestion}
            onInsertSuggestion={handleInsertAiSuggestion}
            onRewriteSuggestion={handleRewriteAiSuggestion}
            onRejectSuggestion={closeAiSuggestion}
          />
        </div>

        {#if showInlineAccountFields}
          <div class="comment-next-guest-details">
            {@render guestFields()}
          </div>
        {/if}
        {#if externalCaptchaStarting || submitMessage || restoredDraft}
          <CommentNextNotice
            quiet={restoredDraft && !externalCaptchaStarting && !submitMessage}
            message={externalCaptchaStarting ? '正在启动安全验证…' : submitMessage || '已恢复本地草稿'}
            variant={externalCaptchaStarting ? 'info' : submitMessage ? submitMessageType : 'success'}
          />
        {/if}
        {#if showFooter}
          <CommentNextFooter
            {compact}
            identity={useIdentityDialog ? identityAction : undefined}
            commandMenuOpen={isAiPanelOpen}
            loggedIn={isLoggedIn}
            {allowAnonymous}
            {enablePrivate}
            {privateComment}
            onPrivateChange={(checked) => {
              privateComment = checked;
              persistDraft();
            }}
            submitting={isSubmitting}
            submitDisabled={externalCaptchaStarting}
            submitLabel={resolvedSubmitLabel}
            {loginLabel}
            aiLabel={resolvedAiLabel}
            aiAssistantName={resolvedAiAssistantName}
            aiAssistantMentionName={resolvedAiAssistantMentionName}
            aiAssistantAvatar={aiConfig?.assistantAvatar}
            {aiMode}
            aiLoading={aiGenerating}
            {variant}
            showAi={aiWritingEnabled}
            showMention={aiMentionEnabled}
            aiHasContent={Boolean(textFromHtml(editorHtml).trim())}
            aiHasSubject={Boolean(subject.trim())}
            aiHasReplyTarget={Boolean(replyToName.trim())}
            onMentionAssistant={() => editorRef?.mentionAssistant()}
            {showInsertTools}
            {showSubmitArea}
            {imageUploadEnabled}
            {imageUploading}
            {imageAccept}
            {emotePacks}
            emotePageSize={emoteConfig?.pageSize}
            onEmoteSelect={handleEmoteSelect}
            onImageUpload={handleImageUpload}
            onLogin={useIdentityDialog ? () => openIdentityDialog('submit') : handleLogin}
            onCloseCommandMenu={() => {
              isAiPanelOpen = false;
            }}
            onCommandModeSelect={handleAiModeSelect}
            onToggleCommandMenu={() => {
              if (!aiWritingEnabled || aiGenerating) {
                return;
              }
              isAiPanelOpen = !isAiPanelOpen;
              showInlineSuggestion = false;
              showSelectionTools = false;
            }}
          />
        {/if}
      {/if}
    </div>

    {#if !loading}
      <CommentNextCaptchaDialog
        open={captchaDialogOpen}
        {baseUrl}
        type={captchaType}
        {captchaConfig}
        image={captchaImage}
        value={captchaCode}
        refreshKey={captchaRefreshKey}
        error={captchaDialogError}
        submitting={isSubmitting}
        onChange={(value) => {
          captchaCode = value;
          if (value.trim()) {
            captchaDialogError = '';
          }
        }}
        onConfirm={handleCaptchaConfirm}
        onVerified={handleCaptchaVerified}
        onError={(message) => {
          externalCaptchaStarting = false;
          showSubmitMessage(message);
        }}
        onClose={() => {
          if (!isSubmitting) {
            captchaDialogError = '';
            externalCaptchaStarting = false;
            captchaDialogOpen = false;
          }
        }}
      />
    {/if}
  </form>

  {#if identityPrompt}
    <CommentNextIdentityDialog
      id={identityDialogId}
      loggedIn={isLoggedIn}
      {allowAnonymous}
      displayName={composerIdentity}
      avatar={identityAvatar}
      {guestFields}
      guestSubmitLabel={identityPrompt.intent === 'submit' ? submitLabel : '保存资料'}
      {loginLabel}
      onLogin={handleLogin}
      onGuestConfirm={confirmGuestIdentity}
      onClose={closeIdentityDialog}
    />
  {/if}
</div>

<style>
  .comment-next-composer-frame {
    --comment-next-composer-theme-accent: var(--halo-cw-primary-1-color, var(--halo-comment-widget-component-form-button-submit-bg-color, var(--comment-next-text-color, #202a39)));
    --comment-next-composer-accent: var(--comment-next-composer-theme-accent);
    --comment-next-composer-submit-bg: var(--comment-next-composer-accent);
    --comment-next-composer-tint: var(--comment-next-pill-bg-color, #eef2f6);
    --comment-next-composer-action-ink: var(--comment-next-text-color, #172033);
    --comment-next-composer-action-hover: var(--comment-next-control-hover-bg-color, #eef2f4);
    --comment-next-composer-background: var(
      --comment-next-box-bg,
      var(--halo-cw-muted-3-color, var(--halo-comment-widget-component-form-input-bg-color, var(--comment-next-bg-color, Canvas)))
    );
    --comment-next-composer-rail-bg: var(--comment-next-composer-background);
    --comment-next-composer-rule: var(--comment-next-border-subtle-color, #e7ecf2);
    --comment-next-composer-radius: var(--comment-next-style-radius, var(--halo-cw-base-rounded, var(--halo-comment-widget-base-border-radius, 0.875rem)));
    --comment-next-composer-control-radius: min(0.5625rem, var(--comment-next-composer-radius));
    --comment-next-composer-avatar-size: clamp(1.125rem, var(--comment-next-theme-avatar-size, 1.5rem), 1.625rem);
    box-sizing: border-box;
    width: 100%;
    max-width: 100%;
    min-width: 0;
    color: var(--comment-next-text-color, #172033);
    font-family: inherit;
  }

  @supports (color: color-mix(in srgb, white, black)) {
    .comment-next-composer-frame {
      --comment-next-composer-rail-bg: color-mix(
        in srgb,
        var(--comment-next-composer-background) 96%,
        var(--comment-next-text-color, #172033)
      );
      --comment-next-composer-tint: color-mix(
        in srgb,
        var(--comment-next-composer-background) 88%,
        var(--comment-next-composer-accent)
      );
      --comment-next-composer-action-ink: color-mix(
        in srgb,
        var(--comment-next-text-color, #172033) 74%,
        var(--comment-next-composer-accent)
      );
      --comment-next-composer-action-hover: color-mix(
        in srgb,
        var(--comment-next-composer-background) 80%,
        var(--comment-next-composer-accent)
      );
      --comment-next-composer-submit-bg: color-mix(
        in srgb,
        var(--comment-next-composer-accent) 36%,
        var(--comment-next-text-color, #172033)
      );
    }
  }

  @supports (color: contrast-color(white)) {
    .comment-next-composer-frame {
      --comment-next-composer-submit-bg: var(--comment-next-composer-accent);
      --comment-next-composer-submit-text: var(
        --halo-comment-widget-component-form-button-submit-color,
        contrast-color(var(--comment-next-composer-submit-bg))
      );
    }
  }

  @supports (color: light-dark(white, black)) {
    .comment-next-composer-frame {
      --comment-next-composer-accent: light-dark(
        var(--comment-next-style-light-accent, var(--comment-next-composer-theme-accent)),
        var(--comment-next-style-dark-accent, var(--comment-next-composer-theme-accent))
      );
    }
  }

  .comment-next-composer {
    --comment-next-composer-gutter: 1.125rem;
    --comment-next-composer-writing-inset: var(--comment-next-composer-gutter);
    --comment-next-composer-default-height: 9.25rem;
    --comment-next-composer-default-mobile-height: 8rem;
    --comment-next-composer-control-height: 2.125rem;
    --comment-next-composer-action-padding: 0.375rem;
    --comment-next-composer-action-inset: 0.75rem;
    container: comment-next-composer / inline-size;
    box-sizing: border-box;
    width: 100%;
    max-width: 100%;
    min-width: 0;
    margin: 0;
  }

  .comment-next-reply-composer {
    --comment-next-composer-default-height: 6rem;
    --comment-next-composer-default-mobile-height: 5.5rem;
  }

  .comment-next-composer-embedded {
    --comment-next-composer-default-height: 4.25rem;
    --comment-next-composer-default-mobile-height: 4.125rem;
  }

  .comment-next-composer-compact {
    --comment-next-composer-gutter: 0.8125rem;
    --comment-next-composer-action-padding: 0.3125rem;
    --comment-next-composer-action-inset: 0.5rem;
  }

  .comment-next-composer-avatar {
    --comment-next-avatar-layout-size: var(--comment-next-composer-avatar-size);
    display: inline-grid;
    flex: none;
    place-items: center;
    line-height: 0;
  }

  .comment-next-composer-avatar-placeholder {
    display: grid;
    place-items: center;
    width: var(--comment-next-composer-avatar-size);
    height: var(--comment-next-composer-avatar-size);
    border-radius: var(--comment-next-avatar-rounded, 50%);
    background: var(--comment-next-composer-tint);
    color: var(--comment-next-muted-color, #667085);
  }

  .comment-next-composer-surface {
    display: flow-root;
    position: relative;
    box-sizing: border-box;
    width: 100%;
    max-width: 100%;
    min-width: 0;
    border: 1px solid var(--comment-next-field-border-color, var(--comment-next-border-color, #d5dde7));
    border-radius: var(--comment-next-composer-radius);
    background: var(--comment-next-composer-background);
    transition: border-color 150ms ease, box-shadow 150ms ease;
  }

  .comment-next-composer-surface:focus-within {
    border-color: var(--comment-next-focus-border-color, var(--comment-next-composer-accent));
    box-shadow: 0 0 0 2px color-mix(in srgb, var(--comment-next-composer-accent) 8%, transparent);
  }

  .comment-next-composer-writing {
    display: flow-root;
    min-width: 0;
    border-radius: inherit;
  }

  .comment-next-composer-surface :global(.comment-next-composer-action-row) {
    padding: var(--comment-next-composer-action-padding) var(--comment-next-composer-action-inset);
    background: linear-gradient(var(--comment-next-composer-rule), var(--comment-next-composer-rule))
      center top / calc(100% - 2 * var(--comment-next-composer-action-inset)) 1px no-repeat;
  }

  .comment-next-guest-details {
    padding: 0.75rem var(--comment-next-composer-gutter) 0.5rem;
    border-block-start: 1px solid var(--comment-next-composer-rule);
  }

  .comment-next-composer-identity {
    display: inline-grid;
    flex: none;
    place-items: center;
    box-sizing: border-box;
    width: var(--comment-next-composer-tool-width, var(--comment-next-composer-control-height));
    height: var(--comment-next-composer-control-height);
    padding: 0;
    border: 1px solid transparent;
    border-radius: var(--comment-next-composer-control-radius);
    background: transparent;
    color: var(--comment-next-text-color, #172033);
    cursor: pointer;
  }

  .comment-next-composer-identity:hover:not(:disabled) {
    background: var(--comment-next-control-hover-bg-color, #eef2f4);
  }

  .comment-next-composer-identity:focus-visible {
    outline: 2px solid var(--comment-next-focus-border-color, var(--comment-next-composer-accent));
    outline-offset: 1px;
  }

  .comment-next-composer-identity:disabled {
    cursor: not-allowed;
    opacity: 0.55;
  }

  .comment-next-snippet-quote {
    display: flex;
    align-items: flex-start;
    gap: 0.625rem;
    min-width: 0;
    margin: 0.625rem var(--comment-next-composer-gutter) 0 var(--comment-next-composer-writing-inset);
    padding: 0.625rem 0.5rem 0.625rem 0.75rem;
    border: 1px solid var(--comment-next-border-subtle-color, #e7ecf2);
    border-radius: 0.5rem;
    background: var(--comment-next-composer-tint);
    color: var(--comment-next-composer-accent);
  }

  .comment-next-snippet-quote > :global(.comment-next-icon) {
    margin-top: 0.25rem;
  }

  .comment-next-snippet-quote blockquote {
    flex: 1;
    min-width: 0;
    max-height: 6em;
    margin: 0;
    overflow: auto;
    overflow-wrap: anywhere;
    color: var(--comment-next-text-color, #172033);
    font-size: 0.8125rem;
    line-height: 1.65;
  }

  .comment-next-snippet-quote button {
    display: grid;
    place-items: center;
    flex: none;
    width: 2rem;
    height: 2rem;
    margin: -0.25rem 0 -0.25rem auto;
    padding: 0;
    border: 0;
    border-radius: 0.375rem;
    background: transparent;
    color: var(--comment-next-muted-color, #667085);
    cursor: pointer;
  }

  .comment-next-snippet-quote button:hover:not(:disabled) {
    background: var(--comment-next-control-hover-bg-color, #eef2f4);
    color: var(--comment-next-text-color, #172033);
  }

  .comment-next-snippet-quote button:focus-visible {
    outline: 2px solid var(--comment-next-composer-accent);
    outline-offset: 2px;
  }

  .comment-next-snippet-quote button:disabled {
    cursor: not-allowed;
    opacity: 0.5;
  }

  @container comment-next-composer (max-width: 36rem) {
    .comment-next-composer-surface {
      --comment-next-composer-control-height: 2.5rem;
      --comment-next-composer-action-inset: 0.625rem;
      --comment-next-composer-gutter: 0.75rem;
      --comment-next-composer-writing-inset: var(--comment-next-composer-gutter);
    }
  }

  @container comment-next-composer (max-width: 24rem) {
    .comment-next-composer-surface {
      --comment-next-composer-action-inset: 0.5rem;
      --comment-next-composer-tool-width: 2.25rem;
    }
  }

  @media (prefers-reduced-motion: reduce) {
    .comment-next-composer-surface {
      transition: none;
    }
  }
</style>
