import { createInput } from '@formkit/vue';
import type { ListedComment, ListedReply } from '@halo-dev/api-client';
import {
  IconForbidLine,
  IconMessage,
  IconMotionLine,
  IconNotificationBadgeLine,
  IconReplyLine,
  IconShieldUser,
  Toast,
  VDropdownItem,
  VLoading,
} from '@halo-dev/components';
import { definePlugin, type OperationItem } from '@halo-dev/ui-shared';
import { defineAsyncComponent, markRaw } from 'vue';
import {
  type CommentModerationState,
  updateCommentModeration,
} from './api/comment-moderation';
import { openAiReplyGenerateModal } from './services/ai-reply-modal';
import 'uno.css';

const FEATURED_ANNOTATION = 'commentnext.xhhao.com/featured';
const FEATURED_AT_ANNOTATION = 'commentnext.xhhao.com/featured-at';

const ConsoleCommentEditor = defineAsyncComponent({
  loader: () => import('./components/ConsoleCommentEditor.vue'),
  loadingComponent: VLoading,
});

const ConsoleCommentContent = defineAsyncComponent({
  loader: () => import('./components/ConsoleCommentContent.vue'),
  loadingComponent: VLoading,
});

const AiPromptDefaultsAction = defineAsyncComponent(
  () => import('./components/AiPromptDefaultsAction.vue')
);

export default definePlugin({
  components: {},
  formkit: {
    inputs: {
      commentNextPromptDefaults: createInput(AiPromptDefaultsAction, {
        features: [
          (node) => {
            // Retain sibling access, but keep this action-only input out of saved settings.
            node.props.promptFormNode = node.parent;
            node.props.ignore = true;
            node.parent = null;
          },
        ],
        props: [
          'latestSystemPrompt',
          'latestAutoReplyRolePrompt',
          'latestReviewRolePrompt',
          'promptFormNode',
        ],
      }),
    },
  },
  routes: [
    {
      parentName: 'CommentsRoot',
      route: {
        path: 'comment-next-badges',
        name: 'CommentNextBadgeRules',
        component: defineAsyncComponent({
          loader: () => import('./views/BadgeRulesView.vue'),
          loadingComponent: VLoading,
        }),
        meta: {
          title: '评论徽章设置',
          permissions: ['*'],
          menu: {
            name: '评论徽章',
            icon: markRaw(IconNotificationBadgeLine),
            priority: 2.1,
          },
        },
      },
    },
    {
      parentName: 'CommentsRoot',
      route: {
        path: 'comment-next-emotes',
        name: 'CommentNextEmotes',
        component: defineAsyncComponent({
          loader: () => import('./views/EmoteManagerView.vue'),
          loadingComponent: VLoading,
        }),
        meta: {
          title: '评论表情管理',
          permissions: ['*'],
          menu: {
            name: '评论表情',
            icon: markRaw(IconMotionLine),
            priority: 2.2,
          },
        },
      },
    },
    {
      parentName: 'CommentsRoot',
      route: {
        path: 'comment-next-featured-records',
        name: 'CommentNextFeaturedRecords',
        component: defineAsyncComponent({
          loader: () => import('./views/FeaturedCommentsView.vue'),
          loadingComponent: VLoading,
        }),
        meta: {
          title: '精选评论',
          permissions: ['*'],
          menu: {
            name: '精选评论',
            icon: markRaw(IconMessage),
            priority: 2.25,
          },
        },
      },
    },
    {
      parentName: 'CommentsRoot',
      route: {
        path: 'comment-next-ai-moderation-records',
        name: 'CommentNextAiModerationRecords',
        component: defineAsyncComponent({
          loader: () => import('./views/AiModerationRecordsView.vue'),
          loadingComponent: VLoading,
        }),
        meta: {
          title: 'AI 拦截记录',
          permissions: ['*'],
          menu: {
            name: 'AI 拦截记录',
            icon: markRaw(IconShieldUser),
            priority: 2.3,
          },
        },
      },
    },
    {
      parentName: 'CommentsRoot',
      route: {
        path: 'comment-next-report-records',
        name: 'CommentNextReportRecords',
        component: defineAsyncComponent({
          loader: () => import('./views/ReportRecordsView.vue'),
          loadingComponent: VLoading,
        }),
        meta: {
          title: '举报记录',
          permissions: ['*'],
          menu: {
            name: '举报记录',
            icon: markRaw(IconShieldUser),
            priority: 2.32,
          },
        },
      },
    },
    {
      parentName: 'CommentsRoot',
      route: {
        path: 'comment-next-ai-reply-records',
        name: 'CommentNextAiReplyRecords',
        component: defineAsyncComponent({
          loader: () => import('./views/AiReplyRecordsView.vue'),
          loadingComponent: VLoading,
        }),
        meta: {
          title: 'AI 回复管理',
          permissions: ['*'],
          menu: {
            name: 'AI 回复管理',
            icon: markRaw(IconReplyLine),
            priority: 2.35,
          },
        },
      },
    },
    {
      parentName: 'CommentsRoot',
      route: {
        path: 'comment-next-security-rules',
        name: 'CommentNextSecurityRules',
        component: defineAsyncComponent({
          loader: () => import('./views/SecurityRulesView.vue'),
          loadingComponent: VLoading,
        }),
        meta: {
          title: '黑灰名单',
          permissions: ['*'],
          menu: {
            name: '黑灰名单',
            icon: markRaw(IconForbidLine),
            priority: 2.4,
          },
        },
      },
    },
  ],
  extensionPoints: {
    'comment:editor:replace': () => ({
      component: markRaw(ConsoleCommentEditor),
    }),
    'comment:list-item:content:replace': () => ({
      component: markRaw(ConsoleCommentContent),
    }),
    'comment:list-item:operation:create': (comment) =>
      createCommentModerationOperations(comment.value),
    'reply:list-item:operation:create': (reply) =>
      createReplyModerationOperations(reply.value),
  },
});

function createCommentModerationOperations(
  item: ListedComment
): OperationItem<ListedComment>[] {
  const top = isCommentTop(item);
  const featured = isCommentFeatured(item);

  return [
    {
      priority: 23,
      component: markRaw(VDropdownItem),
      label: 'AI 回复',
      permissions: ['plugin:comment-next:comments:moderate'],
      hidden: !item.comment?.metadata?.name,
      action: () =>
        openAiReplyModal(
          'comment',
          item.comment?.metadata?.name,
          '为当前评论生成 AI 回复候选'
        ),
    },
    {
      priority: 22,
      component: markRaw(VDropdownItem),
      label: top ? '取消置顶' : '置顶评论',
      props: {
        selected: top,
      },
      permissions: ['plugin:comment-next:comments:moderate'],
      action: () => toggleCommentTop(item),
    },
    {
      priority: 21,
      component: markRaw(VDropdownItem),
      label: featured ? '取消精选' : '设为精选',
      props: {
        selected: featured,
      },
      permissions: ['plugin:comment-next:comments:moderate'],
      action: () => toggleCommentFeatured(item),
    },
  ];
}

function createReplyModerationOperations(
  item: ListedReply
): OperationItem<ListedReply>[] {
  const top = isReplyTop(item);
  const featured = isReplyFeatured(item);

  return [
    {
      priority: 23,
      component: markRaw(VDropdownItem),
      label: 'AI 回复',
      permissions: ['plugin:comment-next:comments:moderate'],
      hidden: !item.reply?.metadata?.name,
      action: () =>
        openAiReplyModal(
          'reply',
          item.reply?.metadata?.name,
          '为当前回复生成 AI 回复候选'
        ),
    },
    {
      priority: 22,
      component: markRaw(VDropdownItem),
      label: top ? '取消置顶' : '置顶回复',
      props: {
        selected: top,
      },
      permissions: ['plugin:comment-next:comments:moderate'],
      action: () => toggleReplyTop(item),
    },
    {
      priority: 21,
      component: markRaw(VDropdownItem),
      label: featured ? '取消精选' : '设为精选',
      props: {
        selected: featured,
      },
      permissions: ['plugin:comment-next:comments:moderate'],
      action: () => toggleReplyFeatured(item),
    },
  ];
}

function openAiReplyModal(
  targetType: 'comment' | 'reply',
  targetName: string | undefined,
  targetLabel: string
) {
  if (!targetName) {
    Toast.error(
      targetType === 'comment'
        ? '评论缺少 ID，无法生成 AI 回复'
        : '回复缺少 ID，无法生成 AI 回复'
    );
    return;
  }

  openAiReplyGenerateModal({
    targetType,
    targetName,
    targetLabel,
    onPublished: () => Toast.success('AI 回复已发布'),
  });
}

async function toggleCommentTop(item: ListedComment) {
  const comment = item.comment;
  const name = comment?.metadata?.name;
  if (!name) {
    Toast.error('评论缺少 ID，无法更新');
    return;
  }

  const nextTop = !isCommentTop(item);
  try {
    const state = await updateCommentModeration('comment', name, {
      top: nextTop,
      priority: nextTop ? normalizePriority(comment.spec?.priority) : 0,
    });
    applyCommentModerationState(comment, state);
    Toast.success(nextTop ? '评论已置顶' : '评论已取消置顶');
  } catch (error) {
    console.error(error);
    Toast.error('更新评论置顶状态失败');
  }
}

async function toggleCommentFeatured(item: ListedComment) {
  const comment = item.comment;
  const name = comment?.metadata?.name;
  if (!name) {
    Toast.error('评论缺少 ID，无法更新');
    return;
  }

  const nextFeatured = !isCommentFeatured(item);
  try {
    const state = await updateCommentModeration('comment', name, {
      featured: nextFeatured,
    });
    applyCommentModerationState(comment, state);
    Toast.success(nextFeatured ? '评论已精选' : '评论已取消精选');
  } catch (error) {
    console.error(error);
    Toast.error('更新评论精选状态失败');
  }
}

async function toggleReplyTop(item: ListedReply) {
  const reply = item.reply;
  const name = reply?.metadata?.name;
  if (!name) {
    Toast.error('回复缺少 ID，无法更新');
    return;
  }

  const nextTop = !isReplyTop(item);
  try {
    const state = await updateCommentModeration('reply', name, {
      top: nextTop,
      priority: nextTop ? normalizePriority(reply.spec?.priority) : 0,
    });
    applyCommentModerationState(reply, state);
    Toast.success(nextTop ? '回复已置顶' : '回复已取消置顶');
  } catch (error) {
    console.error(error);
    Toast.error('更新回复置顶状态失败');
  }
}

async function toggleReplyFeatured(item: ListedReply) {
  const reply = item.reply;
  const name = reply?.metadata?.name;
  if (!name) {
    Toast.error('回复缺少 ID，无法更新');
    return;
  }

  const nextFeatured = !isReplyFeatured(item);
  try {
    const state = await updateCommentModeration('reply', name, {
      featured: nextFeatured,
    });
    applyCommentModerationState(reply, state);
    Toast.success(nextFeatured ? '回复已精选' : '回复已取消精选');
  } catch (error) {
    console.error(error);
    Toast.error('更新回复精选状态失败');
  }
}

function isCommentTop(item: ListedComment): boolean {
  return Boolean(item.comment?.spec?.top);
}

function isReplyTop(item: ListedReply): boolean {
  return Boolean(item.reply?.spec?.top);
}

function isCommentFeatured(item: ListedComment): boolean {
  return isFeatured(item.comment);
}

function isReplyFeatured(item: ListedReply): boolean {
  return isFeatured(item.reply);
}

function isFeatured(resource?: {
  metadata?: { annotations?: Record<string, string> };
}): boolean {
  return resource?.metadata?.annotations?.[FEATURED_ANNOTATION] === 'true';
}

function applyCommentModerationState(
  resource: {
    metadata?: { annotations?: Record<string, string> };
    spec?: { top?: boolean; priority?: number };
  },
  state: CommentModerationState
) {
  resource.spec ??= {};
  resource.spec.top = state.top;
  resource.spec.priority = state.priority;

  resource.metadata ??= {};
  resource.metadata.annotations ??= {};
  if (state.featured) {
    resource.metadata.annotations[FEATURED_ANNOTATION] = 'true';
    resource.metadata.annotations[FEATURED_AT_ANNOTATION] =
      new Date().toISOString();
    return;
  }

  delete resource.metadata.annotations[FEATURED_ANNOTATION];
  delete resource.metadata.annotations[FEATURED_AT_ANNOTATION];
}

function normalizePriority(value: unknown): number {
  const priority = Number(value);
  return Number.isFinite(priority) ? priority : 0;
}
