<script lang="ts">
import CommentNextBaseComposer from './CommentNextBaseComposer.svelte';
import { normalizeReplyTarget } from './replies/target';
import { createComment, createReply } from './services/comments';
import type {
  CommentNextAiConfig,
  CommentNextAvatarConfig,
  CommentNextEmoteConfig,
  CommentNextSecurityConfig,
  CommentNextUploadConfig,
} from './services/config';
import type {
  CommentNextComposerSubmitPayload,
  CommentNextCreatedEventDetail,
  CommentNextReplyTarget,
} from './types/composer';
import type { CommentNextEmotePack } from './types/emote';
import type { CommentNextSnippetAnchor } from './types/snippet';

let {
  baseUrl = '',
  group = '',
  kind = '',
  version = 'v1alpha1',
  name = '',
  loggedIn = false,
  allowAnonymous = true,
  showCaptcha = false,
  captchaType = 'ALPHANUMERIC',
  captchaConfig,
  enablePrivate = false,
  loading = false,
  submitting = false,
  commandMenuOpen = false,
  inlineSuggestion = false,
  selectionTools = false,
  placeholder = '写下你的评论...',
  allowImages = true,
  showAccountFields = true,
  showFooter = true,
  showSubmitArea = true,
  replyTarget = $bindable<CommentNextReplyTarget | undefined>(),
  onCreated = () => {},
  aiConfig,
  uploadConfig,
  emoteConfig,
  avatarConfig,
  emotePacks = [],
}: {
  baseUrl?: string;
  group?: string;
  kind?: string;
  version?: string;
  name?: string;
  loggedIn?: boolean;
  allowAnonymous?: boolean;
  showCaptcha?: boolean;
  captchaType?: NonNullable<CommentNextSecurityConfig['captcha']>['type'];
  captchaConfig?: CommentNextSecurityConfig['captcha'];
  enablePrivate?: boolean;
  loading?: boolean;
  submitting?: boolean;
  commandMenuOpen?: boolean;
  inlineSuggestion?: boolean;
  selectionTools?: boolean;
  placeholder?: string;
  allowImages?: boolean;
  showAccountFields?: boolean;
  showFooter?: boolean;
  showSubmitArea?: boolean;
  replyTarget?: CommentNextReplyTarget;
  onCreated?: (detail: CommentNextCreatedEventDetail) => void;
  aiConfig?: CommentNextAiConfig;
  uploadConfig?: CommentNextUploadConfig;
  emoteConfig?: CommentNextEmoteConfig;
  avatarConfig?: CommentNextAvatarConfig;
  emotePacks?: CommentNextEmotePack[];
} = $props();

const subject = $derived(
  [group, version, kind, name].filter(Boolean).join('/')
);
const normalizedReplyTarget = $derived(normalizeReplyTarget(replyTarget));
const isReply = $derived(replyTarget != null);
const targetReady = $derived(
  Boolean(group && kind && name) && (!isReply || Boolean(normalizedReplyTarget))
);
const loginRedirectHash = $derived(getSubjectDomId());
let composerRef:
  | {
      quoteSnippet: (anchor: CommentNextSnippetAnchor) => boolean;
      focus: () => void;
      reset: () => void;
      isBusy: () => boolean;
    }
  | undefined;
let lastSubmission: CommentNextCreatedEventDetail | undefined;
let lastSubmittedReplyTarget: CommentNextReplyTarget | undefined;

export function focus() {
  composerRef?.focus();
}

export function reset() {
  composerRef?.reset();
}

export function replyTo(target: CommentNextReplyTarget): boolean {
  const normalized = normalizeReplyTarget(target);
  if (!normalized || !composerRef || composerRef.isBusy()) return false;
  replyTarget = normalized;
  composerRef.focus();
  return true;
}

export function cancelReply(): boolean {
  if (!composerRef || composerRef.isBusy()) return false;
  replyTarget = undefined;
  return true;
}

export function quoteSnippet(anchor: CommentNextSnippetAnchor): boolean {
  return composerRef?.quoteSnippet(anchor) ?? false;
}

// Each handler captures its target before the base composer prepares image uploads.
const handleSubmit = $derived.by(() => {
  const target = normalizedReplyTarget;
  const currentReplyTarget = replyTarget;
  const subjectRef = { group, kind, name, version };
  const currentBaseUrl = baseUrl;
  const allowedLottieHosts = emoteConfig?.allowedHosts;
  return async (payload: CommentNextComposerSubmitPayload) => {
    if (currentReplyTarget != null && !target) {
      throw new Error('回复目标缺少主评论标识，无法提交。');
    }
    const common = {
      baseUrl: currentBaseUrl,
      content: payload.content,
      allowedLottieHosts,
      captchaCode: payload.captchaCode,
      owner: payload.owner,
    };
    const result = target
      ? await createReply({
          ...common,
          commentName: target.commentName,
          quoteReply: target.replyName,
        })
      : await createComment({
          ...common,
          ...subjectRef,
          hidden: payload.hidden,
          snippet: payload.snippet,
        });
    lastSubmission = {
      type: target ? 'reply' : 'comment',
      subject: subjectRef,
      commentName: target?.commentName ?? result.id,
      replyTarget: target,
      result,
    };
    lastSubmittedReplyTarget = currentReplyTarget;
    return result;
  };
});

function handleCreated(_result: unknown) {
  if (!lastSubmission) return;
  // Do not clear a new target supplied while the request was running.
  if (replyTarget === lastSubmittedReplyTarget) replyTarget = undefined;
  onCreated(lastSubmission);
}

function getSubjectDomId() {
  const subjectDomId = [group.replaceAll('.', '-'), kind, name]
    .filter(Boolean)
    .join('-')
    .replaceAll(/-+/g, '-');

  return subjectDomId ? `#comment-${subjectDomId}` : window.location.hash;
}
</script>

<CommentNextBaseComposer
  bind:this={composerRef}
  {baseUrl}
  {loggedIn}
  {allowAnonymous}
  {showCaptcha}
  {captchaType}
  {captchaConfig}
  enablePrivate={!isReply && enablePrivate}
  {loading}
  {submitting}
  {commandMenuOpen}
  {inlineSuggestion}
  {selectionTools}
  placeholder={isReply ? (normalizedReplyTarget?.displayName ? `回复 @${normalizedReplyTarget.displayName}` : '写下你的回复...') : placeholder}
  {allowImages}
  {showAccountFields}
  {showFooter}
  {showSubmitArea}
  {aiConfig}
  {uploadConfig}
  {emoteConfig}
  {avatarConfig}
  {emotePacks}
  {subject}
  {targetReady}
  {loginRedirectHash}
  targetMissingMessage={isReply ? '回复目标缺少主评论标识或内容归属，无法提交。' : '评论目标缺少 group、kind 或 name，无法提交。'}
  successMessage={isReply ? '回复提交成功。' : '评论提交成功。'}
  genericErrorMessage={isReply ? '回复提交失败，请稍后再试。' : '评论提交失败，请稍后再试。'}
  createdEventName={isReply ? 'halo:comment-reply:created' : 'halo:comment:created'}
  variant={isReply ? 'reply' : 'comment'}
  showHeader={isReply}
  replyToName={normalizedReplyTarget?.displayName ?? ''}
  submitLabel={isReply ? '回复' : '提交'}
  loginLabel={isReply ? '登录后回复' : '登录后评论'}
  onCancel={cancelReply}
  onSubmit={handleSubmit}
  onCreated={handleCreated}
/>
