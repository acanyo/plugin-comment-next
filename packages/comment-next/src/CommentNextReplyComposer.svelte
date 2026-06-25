<script lang="ts">
import CommentNextBaseComposer from './CommentNextBaseComposer.svelte';
import { createReply } from './services/comments';
import type {
  CommentNextAiConfig,
  CommentNextSecurityConfig,
  CommentNextUploadConfig,
} from './services/config';
import type { CommentNextComment } from './types/comment';
import type { CommentNextComposerSubmitPayload } from './types/composer';
import type { CommentNextEmotePack } from './types/emote';

const {
  baseUrl = '',
  commentId = '',
  loggedIn = false,
  allowAnonymous = true,
  showCaptcha = false,
  captchaType = 'ALPHANUMERIC',
  captchaConfig,
  replyToName = '',
  aiConfig,
  uploadConfig,
  emotePacks = [],
  quoteReply,
  onCancel = () => {},
  onCreated = () => {},
}: {
  baseUrl?: string;
  commentId?: string;
  loggedIn?: boolean;
  allowAnonymous?: boolean;
  showCaptcha?: boolean;
  captchaType?: NonNullable<CommentNextSecurityConfig['captcha']>['type'];
  captchaConfig?: CommentNextSecurityConfig['captcha'];
  replyToName?: string;
  aiConfig?: CommentNextAiConfig;
  uploadConfig?: CommentNextUploadConfig;
  emotePacks?: CommentNextEmotePack[];
  quoteReply?: CommentNextComment;
  onCancel?: () => void;
  onCreated?: (reply: CommentNextComment) => void;
} = $props();

const placeholder = $derived(
  replyToName ? `回复 @${replyToName}` : '写下你的回复...'
);
const targetReady = $derived(Boolean(commentId));

async function handleSubmit(payload: CommentNextComposerSubmitPayload) {
  return createReply({
    baseUrl,
    commentName: commentId,
    content: payload.content,
    captchaCode: payload.captchaCode,
    quoteReply: quoteReply?.id,
    owner: payload.owner,
  });
}
</script>

<CommentNextBaseComposer
  {baseUrl}
  {loggedIn}
  {allowAnonymous}
  {showCaptcha}
  {captchaType}
  {captchaConfig}
  {placeholder}
  {targetReady}
  {replyToName}
  {aiConfig}
  {uploadConfig}
  {emotePacks}
  {onCancel}
  targetMissingMessage="回复目标缺失，无法提交。"
  successMessage="回复提交成功。"
  genericErrorMessage="回复提交失败，请稍后再试。"
  createdEventName="halo:comment-reply:created"
  variant="reply"
  compact
  showHeader
  enablePrivate={false}
  submitLabel="回复"
  loginLabel="登录后回复"
  onSubmit={handleSubmit}
  onCreated={(reply) => onCreated(reply as CommentNextComment)}
/>
