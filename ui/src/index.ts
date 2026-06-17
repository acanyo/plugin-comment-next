import { IconNotificationBadgeLine, VLoading } from '@halo-dev/components';
import { definePlugin } from '@halo-dev/ui-shared';
import { defineAsyncComponent, markRaw } from 'vue';
import 'uno.css';

const ConsoleCommentEditor = defineAsyncComponent({
  loader: () => import('./components/ConsoleCommentEditor.vue'),
  loadingComponent: VLoading,
});

const ConsoleCommentContent = defineAsyncComponent({
  loader: () => import('./components/ConsoleCommentContent.vue'),
  loadingComponent: VLoading,
});

export default definePlugin({
  components: {},
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
            name: '评论徽章设置',
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
            name: '评论表情管理',
            icon: markRaw(IconNotificationBadgeLine),
            priority: 2.2,
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
  },
});
