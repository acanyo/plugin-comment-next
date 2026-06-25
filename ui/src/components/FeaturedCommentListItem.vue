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
import type { FeaturedCommentItem } from '../api/featured-comments';
import ConsoleCommentContent from './ConsoleCommentContent.vue';

const props = withDefaults(
  defineProps<{
    item: FeaturedCommentItem;
    canManage?: boolean;
  }>(),
  {
    canManage: false,
  }
);

const emit = defineEmits<{
  unfeature: [item: FeaturedCommentItem];
  untop: [item: FeaturedCommentItem];
}>();

const targetText = computed(() =>
  props.item.targetType === 'reply' ? '回复' : '评论'
);
const targetState = computed(() =>
  props.item.targetType === 'reply' ? 'default' : 'success'
);
const authorName = computed(
  () =>
    props.item.owner?.displayName ||
    props.item.spec?.owner?.displayName ||
    props.item.spec?.owner?.name ||
    '匿名用户'
);
const content = computed(() => props.item.spec?.content || '无内容');
const featuredAtText = computed(() =>
  props.item.featuredAt ? utils.date.format(props.item.featuredAt) : '--'
);
const creationTimeText = computed(() =>
  props.item.spec?.creationTime
    ? utils.date.format(props.item.spec.creationTime)
    : props.item.metadata.creationTimestamp
      ? utils.date.format(props.item.metadata.creationTimestamp)
      : '--'
);
const statusText = computed(() => {
  if (props.item.spec?.approved === false) {
    return '待审核';
  }
  if (props.item.spec?.hidden) {
    return '私密';
  }
  return '可见';
});
const statusState = computed(() => {
  if (props.item.spec?.approved === false) {
    return 'warning';
  }
  if (props.item.spec?.hidden) {
    return 'default';
  }
  return 'success';
});
const sourceText = computed(() => props.item.subject || '未知来源');
const targetTooltip = computed(() =>
  [
    `ID：${props.item.metadata.name}`,
    props.item.parentName ? `所属评论：${props.item.parentName}` : '',
    props.item.subject ? `来源：${props.item.subject}` : '',
    props.item.spec?.quoteReply ? `引用回复：${props.item.spec.quoteReply}` : '',
  ]
    .filter(Boolean)
    .join('\n')
);
const canCancelTop = computed(() => props.canManage && Boolean(props.item.top));
</script>

<template>
  <VEntity>
    <template #start>
      <VEntityField width="100%" max-width="100%">
        <template #description>
          <div class=":uno: flex min-w-0 flex-col gap-2">
            <ConsoleCommentContent :content="content" />

            <div
              class=":uno: flex min-w-0 flex-wrap items-center gap-x-2 gap-y-1 text-xs text-gray-500"
            >
              <span class=":uno: max-w-42 truncate">评论者：{{ authorName }}</span>
              <span v-tooltip="sourceText" class=":uno: max-w-64 truncate">
                {{ sourceText }}
              </span>
              <span v-tooltip="targetTooltip" class=":uno: cursor-help text-gray-400">
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
      <VEntityField title="排序" width="7rem">
        <template #description>
          <span class=":uno: text-xs text-gray-500">
            {{ item.top ? `置顶 · ${item.priority ?? 0}` : `普通 · ${item.priority ?? 0}` }}
          </span>
        </template>
      </VEntityField>
      <VEntityField title="精选时间" width="10rem">
        <template #description>
          <span
            v-tooltip="`创建时间：${creationTimeText}`"
            class=":uno: truncate text-xs tabular-nums text-gray-500"
          >
            {{ featuredAtText }}
          </span>
        </template>
      </VEntityField>
      <VEntityField v-if="canManage" title="操作" width="7rem">
        <template #description>
          <VButton size="sm" type="secondary" @click="emit('unfeature', item)">
            取消精选
          </VButton>
        </template>
      </VEntityField>
    </template>

    <template v-if="canManage" #dropdownItems>
      <VDropdownItem @click="emit('unfeature', item)">
        取消精选
      </VDropdownItem>
      <VDropdownItem v-if="canCancelTop" @click="emit('untop', item)">
        取消置顶
      </VDropdownItem>
    </template>
  </VEntity>
</template>
