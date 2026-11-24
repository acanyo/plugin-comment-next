<script lang="ts" setup>
import {
  VButton,
  VDropdownItem,
  VEntity,
  VEntityField,
  VStatusDot,
} from '@halo-dev/components';
import { utils } from '@halo-dev/ui-shared';
import { computed } from 'vue';
import type {
  CommentSearchItem,
  CommentSearchReplyQueue,
} from '../api/comment-search';
import ConsoleCommentContent from './ConsoleCommentContent.vue';

const props = defineProps<{
  item: CommentSearchItem;
  replyQueue: CommentSearchReplyQueue;
  canReply: boolean;
  updating: boolean;
}>();

const emit = defineEmits<{ reply: []; mark: [] }>();

const targetText = computed(() =>
  props.item.targetType === 'reply' ? '回复' : '评论'
);
const targetState = computed(() =>
  props.item.targetType === 'reply' ? 'default' : 'success'
);
const identityText = computed(
  () => props.item.email || props.item.username || '未记录'
);
const statusText = computed(() => {
  if (props.item.hidden) {
    return '私密';
  }
  if (!props.item.approved) {
    return '待审核';
  }
  return '可见';
});
const statusState = computed(() => {
  if (props.item.hidden) {
    return 'default';
  }
  if (!props.item.approved) {
    return 'warning';
  }
  return 'success';
});
const creationTimeText = computed(() =>
  props.item.creationTime ? utils.date.format(props.item.creationTime) : '--'
);
const waitingTimeText = computed(
  () => utils.date.timeAgo(props.item.creationTime) || '--'
);
const objectTooltip = computed(() =>
  [
    `ID：${props.item.name}`,
    props.item.parentName ? `所属评论：${props.item.parentName}` : '',
    props.item.subject ? `来源：${props.item.subject}` : '',
    props.item.userAgent ? `User-Agent：${props.item.userAgent}` : '',
  ]
    .filter(Boolean)
    .join('\n')
);
</script>

<template>
  <VEntity>
    <template #start>
      <VEntityField width="100%" max-width="100%">
        <template #description>
          <div class=":uno: flex min-w-0 flex-col gap-2">
            <ConsoleCommentContent :content="item.content || '无内容'" />

            <div
              class=":uno: flex min-w-0 flex-wrap items-center gap-x-2 gap-y-1 text-xs text-gray-500"
            >
              <span class=":uno: max-w-42 truncate">
                评论者：{{ item.authorName || '匿名用户' }}
              </span>
              <span v-tooltip="identityText" class=":uno: max-w-64 truncate">
                {{ item.email ? `邮箱：${item.email}` : `用户：${identityText}` }}
              </span>
              <span v-tooltip="item.ipAddress || '未记录'" class=":uno: max-w-44 truncate">
                IP：{{ item.ipAddress || '未记录' }}
              </span>
              <span v-tooltip="objectTooltip" class=":uno: cursor-help text-gray-400">
                对象信息
              </span>
            </div>
          </div>
        </template>
      </VEntityField>
    </template>

    <template #end>
      <VEntityField title="类型" width="6rem">
        <template #description>
          <VStatusDot :state="targetState" :text="targetText" />
        </template>
      </VEntityField>
      <VEntityField title="状态" width="6rem">
        <template #description>
          <VStatusDot :state="statusState" :text="statusText" />
        </template>
      </VEntityField>
      <VEntityField :title="replyQueue === 'waiting' ? '等待时长' : '创建时间'" width="10rem">
        <template #description>
          <span v-tooltip="creationTimeText" class=":uno: truncate text-xs tabular-nums text-gray-500">
            {{ replyQueue === 'waiting' ? waitingTimeText : creationTimeText }}
          </span>
        </template>
      </VEntityField>
      <VEntityField v-if="replyQueue === 'waiting' && canReply" title="操作" width="5rem">
        <template #description>
          <VButton size="sm" type="secondary" :disabled="updating" @click="emit('reply')">
            回复
          </VButton>
        </template>
      </VEntityField>
    </template>

    <template v-if="replyQueue !== 'all'" #dropdownItems>
      <VDropdownItem v-if="replyQueue === 'waiting' && canReply" :disabled="updating" @click="emit('reply')">
        回复
      </VDropdownItem>
      <VDropdownItem :disabled="updating" @click="emit('mark')">
        {{ replyQueue === 'waiting' ? '标记无需回复' : '取消无需回复' }}
      </VDropdownItem>
    </template>
  </VEntity>
</template>
