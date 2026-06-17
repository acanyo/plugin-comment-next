import { IconNotificationBadgeLine, VLoading } from '@halo-dev/components';
import { definePlugin } from '@halo-dev/ui-shared';
import { defineAsyncComponent, markRaw } from 'vue';
import 'uno.css';

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
          permissions: ['plugin:comment-next:badge-rules:view'],
          menu: {
            name: '评论徽章设置',
            icon: markRaw(IconNotificationBadgeLine),
            priority: 50,
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
          permissions: ['plugin:comment-next:emotes:view'],
          menu: {
            name: '评论表情管理',
            icon: markRaw(IconNotificationBadgeLine),
            priority: 55,
          },
        },
      },
    },
  ],
});
