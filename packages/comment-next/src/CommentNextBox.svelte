<script lang="ts">
import CommentNextBaseComposer from './CommentNextBaseComposer.svelte';
import { createComment } from './services/comments';
import type {
  CommentNextAiConfig,
  CommentNextUploadConfig,
} from './services/config';
import type { CommentNextComposerSubmitPayload } from './types/composer';
import type { CommentNextEmotePack } from './types/emote';

const {
  baseUrl = '',
  group = '',
  kind = '',
  version = 'v1alpha1',
  name = '',
  loggedIn = false,
  allowAnonymous = true,
  showCaptcha = false,
  enablePrivate = false,
  loading = false,
  submitting = false,
  commandMenuOpen = false,
  inlineSuggestion = false,
  selectionTools = false,
  placeholder = '写下你的评论...',
  aiConfig,
  uploadConfig,
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
  enablePrivate?: boolean;
  loading?: boolean;
  submitting?: boolean;
  commandMenuOpen?: boolean;
  inlineSuggestion?: boolean;
  selectionTools?: boolean;
  placeholder?: string;
  aiConfig?: CommentNextAiConfig;
  uploadConfig?: CommentNextUploadConfig;
  emotePacks?: CommentNextEmotePack[];
} = $props();

const subject = $derived(
  [group, version, kind, name].filter(Boolean).join('/')
);
const targetReady = $derived(Boolean(group && kind && name));
const loginRedirectHash = $derived(getSubjectDomId());

async function handleSubmit(payload: CommentNextComposerSubmitPayload) {
  return createComment({
    baseUrl,
    group,
    kind,
    name,
    version,
    content: payload.content,
    hidden: payload.hidden,
    captchaCode: payload.captchaCode,
    owner: payload.owner,
  });
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
  {baseUrl}
  {loggedIn}
  {allowAnonymous}
  {showCaptcha}
  {enablePrivate}
  {loading}
  {submitting}
  {commandMenuOpen}
  {inlineSuggestion}
  {selectionTools}
  {placeholder}
  {aiConfig}
  {uploadConfig}
  {emotePacks}
  {subject}
  {targetReady}
  {loginRedirectHash}
  targetMissingMessage="评论目标缺少 group、kind 或 name，无法提交。"
  successMessage="评论提交成功。"
  genericErrorMessage="评论提交失败，请稍后再试。"
  createdEventName="halo:comment:created"
  variant="comment"
  onSubmit={handleSubmit}
/>
