<script lang="ts" setup>
import { VEntity, VEntityField, VStatusDot } from '@halo-dev/components';
import { utils } from '@halo-dev/ui-shared';
import { Icon } from '@iconify/vue';
import { computed } from 'vue';
import type {
  BadgeProfile,
  BadgeProfileUserInfo,
  CommentNextBadge,
} from '../api/badge-rules';
import { isDeletingResource } from '../utils/deletion';

const props = withDefaults(
  defineProps<{
    profile: BadgeProfile;
    userInfo?: BadgeProfileUserInfo;
    canManage?: boolean;
  }>(),
  {
    canManage: false,
  }
);

const emit = defineEmits<{
  revoke: [profile: BadgeProfile, badge: CommentNextBadge];
}>();

const badges = computed(() => props.profile.spec.badges ?? []);
const isDeleting = computed(() => isDeletingResource(props.profile));
const createdAtText = computed(() => {
  const timestamp = props.profile.metadata.creationTimestamp;
  return timestamp ? utils.date.format(timestamp) : '--';
});
const displayName = computed(() => {
  if (props.profile.spec.identityType === 'USER') {
    return (
      props.userInfo?.displayName || props.profile.spec.identity || '未知用户'
    );
  }
  return props.profile.spec.identity || '未知邮箱用户';
});
const identityDescription = computed(() => {
  if (props.profile.spec.identityType === 'USER') {
    return `用户名：${props.userInfo?.name || props.profile.spec.identity}`;
  }
  return props.profile.spec.identity;
});

function identityTypeText(type: string) {
  return type === 'EMAIL' ? '邮箱用户' : 'Halo 用户';
}

function badgeStyle(badge: CommentNextBadge) {
  return {
    '--comment-next-console-badge-color': badge.color || '#3b82f6',
  } as Record<string, string>;
}

function canRevokeBadge(badge: CommentNextBadge) {
  return (
    props.canManage &&
    !isDeleting.value &&
    badge.tone === 'custom' &&
    Boolean(badge.id)
  );
}

function handleRevokeBadge(badge: CommentNextBadge) {
  if (isDeleting.value) {
    return;
  }

  emit('revoke', props.profile, badge);
}
</script>

<template>
  <VEntity>
    <template #start>
      <VEntityField :title="displayName" width="22rem">
        <template #description>
          <div class=":uno: flex min-w-0 flex-wrap gap-x-2 gap-y-1 text-xs text-gray-500">
            <span>{{ identityTypeText(profile.spec.identityType) }}</span>
            <span class=":uno: max-w-80 truncate">{{ identityDescription }}</span>
            <span class=":uno: max-w-80 truncate">{{ profile.metadata.name }}</span>
          </div>
        </template>
      </VEntityField>
    </template>

    <template #end>
      <VEntityField title="活跃评论" width="8rem">
        <template #description>
          <span class=":uno: text-sm tabular-nums text-gray-600">
            {{ profile.spec.activeCommentCount || 0 }} 条
          </span>
        </template>
      </VEntityField>
      <VEntityField title="用户徽章" width="22rem">
        <template #description>
          <div v-if="badges.length" class=":uno: flex min-w-0 flex-wrap gap-1.5">
            <span
              v-for="badge in badges"
              :key="badge.id || badge.label"
              class="comment-next-console-profile-badge :uno: inline-flex max-w-36 items-center gap-[0.35rem] rounded-full border border-solid [border-color:color-mix(in_srgb,var(--comment-next-console-badge-color),white_70%)] [background:color-mix(in_srgb,var(--comment-next-console-badge-color),white_92%)] px-2 py-[0.1875rem] text-xs text-[var(--comment-next-console-badge-color)] font-[650]"
              :style="badgeStyle(badge)"
              :title="badge.title || badge.label"
            >
              <Icon
                v-if="badge.icon"
                :icon="badge.icon"
                class=":uno: h-3.5 w-3.5 shrink-0"
              />
              <span class="comment-next-console-profile-badge-label :uno: truncate">
                {{ badge.label || badge.id || "未命名" }}
              </span>
              <button
                v-if="canRevokeBadge(badge)"
                type="button"
                class="comment-next-console-profile-badge-revoke :uno: -mr-1 inline-flex h-4 w-4 cursor-pointer items-center justify-center rounded-full border-0 bg-transparent p-0 text-current opacity-62 hover:[background:color-mix(in_srgb,var(--comment-next-console-badge-color),white_82%)] hover:opacity-100"
                title="撤销徽章"
                aria-label="撤销徽章"
                @click.stop="handleRevokeBadge(badge)"
              >
                <Icon icon="mdi:close" class=":uno: h-3 w-3" />
              </button>
            </span>
          </div>
          <VStatusDot v-else state="default" text="暂无徽章" />
        </template>
      </VEntityField>
      <VEntityField v-if="isDeleting" width="7rem">
        <template #description>
          <VStatusDot
            v-tooltip="'删除中'"
            animate
            state="warning"
            text="删除中"
          />
        </template>
      </VEntityField>
      <VEntityField width="8rem">
        <template #description>
          <span class=":uno: truncate text-xs tabular-nums text-gray-500">
            {{ createdAtText }}
          </span>
        </template>
      </VEntityField>
    </template>
  </VEntity>
</template>
