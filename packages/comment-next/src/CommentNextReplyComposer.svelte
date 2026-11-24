<script lang="ts">
import CommentNextBaseComposer from './CommentNextBaseComposer.svelte';
import { commentDraftTarget } from './services/comment-drafts';
import { createReply } from './services/comments';
import type {
  CommentNextAiConfig,
  CommentNextAvatarConfig,
  CommentNextEmoteConfig,
  CommentNextSecurityConfig,
  CommentNextUploadConfig,
} from './services/config';
import type { CommentNextComment } from './types/comment';
import type { CommentNextComposerSubmitPayload } from './types/composer';
import type { CommentNextEmotePack } from './types/emote';
import { getCommentAnchorHref } from './utils/comment-anchor';

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
  emoteConfig,
  avatarConfig,
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
  emoteConfig?: CommentNextEmoteConfig;
  avatarConfig?: CommentNextAvatarConfig;
  emotePacks?: CommentNextEmotePack[];
  quoteReply?: CommentNextComment;
  onCancel?: () => void;
  onCreated?: (reply: CommentNextComment) => void;
} = $props();

const targetReady = $derived(Boolean(commentId));
const draftTarget = $derived(
  targetReady
    ? commentDraftTarget(baseUrl, {
        commentName: commentId,
        replyName: quoteReply?.id,
      })
    : undefined
);
const loginRedirectHash = $derived(
  quoteReply?.id
    ? getCommentAnchorHref('reply', quoteReply.id)
    : commentId
      ? getCommentAnchorHref('comment', commentId)
      : ''
);

async function handleSubmit(payload: CommentNextComposerSubmitPayload) {
  return createReply({
    baseUrl,
    commentName: commentId,
    content: payload.content,
    allowedLottieHosts: emoteConfig?.allowedHosts,
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
  placeholder="写下你的回复…"
  {targetReady}
  {draftTarget}
  {loginRedirectHash}
  {replyToName}
  {aiConfig}
  {uploadConfig}
  {emoteConfig}
  {avatarConfig}
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
  submitLabel="发布回复"
  loginLabel="登录后回复"
  onSubmit={handleSubmit}
  onCreated={(reply) => onCreated(reply as CommentNextComment)}
/>
