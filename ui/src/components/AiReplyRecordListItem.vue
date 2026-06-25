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
import type { AiReplyRecord } from '../api/ai-reply-records';

const props = withDefaults(
  defineProps<{
    record: AiReplyRecord;
    canManage?: boolean;
  }>(),
  {
    canManage: false,
  }
);

const emit = defineEmits<{
  view: [record: AiReplyRecord];
  reject: [record: AiReplyRecord];
}>();

const statusTextMap: Record<AiReplyRecord['spec']['status'], string> = {
  PENDING_REVIEW: '待审核',
  PUBLISHED: '已发布',
  REJECTED: '已驳回',
  FAILED: '失败',
};

const targetTextMap: Record<AiReplyRecord['spec']['targetType'], string> = {
  COMMENT: '评论',
  REPLY: '回复',
};

const triggerTextMap: Record<AiReplyRecord['spec']['triggerType'], string> = {
  AUTO: '自动回复',
  MANUAL: '手动生成',
  MENTION: '@ 提及',
};

const statusStateMap: Record<
  AiReplyRecord['spec']['status'],
  'default' | 'success' | 'warning' | 'error'
> = {
  PENDING_REVIEW: 'warning',
  PUBLISHED: 'success',
  REJECTED: 'default',
  FAILED: 'error',
};

const canPublish = computed(() =>
  props.canManage &&
  props.record.spec.status === 'PENDING_REVIEW' &&
  (Boolean(props.record.spec.replyContent?.trim()) ||
    Boolean(props.record.spec.replyCandidates?.length))
);

const canReject = computed(() =>
  props.canManage && props.record.spec.status === 'PENDING_REVIEW'
);

const candidateList = computed(() => props.record.spec.replyCandidates ?? []);

const replyContent = computed(() =>
  props.record.spec.replyContent?.trim() ||
  candidateList.value[0]?.content?.trim() ||
  '无回复内容'
);

const sourceContent = computed(() =>
  props.record.spec.sourceContent?.trim() || '无源内容'
);

const assistantText = computed(() =>
  props.record.spec.assistantName || props.record.spec.assistantUserName || '评论助手'
);

const generatedAtText = computed(() =>
  props.record.spec.generatedAt
    ? utils.date.format(props.record.spec.generatedAt)
    : '--'
);

const publishedAtText = computed(() =>
  props.record.spec.publishedAt
    ? utils.date.format(props.record.spec.publishedAt)
    : '--'
);

const candidateCountText = computed(() => {
  const count = candidateList.value.length || (props.record.spec.replyContent ? 1 : 0);
  return count ? `${count} 条候选` : '无候选';
});

const targetTooltip = computed(() =>
  [
    `记录：${props.record.metadata.name}`,
    `目标：${props.record.spec.targetName}`,
    `所属评论：${props.record.spec.commentName}`,
    props.record.spec.quoteReplyName
      ? `引用回复：${props.record.spec.quoteReplyName}`
      : '',
    props.record.spec.replyName ? `已发布回复：${props.record.spec.replyName}` : '',
    props.record.spec.subject ? `来源：${props.record.spec.subject}` : '',
  ]
    .filter(Boolean)
    .join('\n')
);

function handleReject() {
  if (!canReject.value) {
    return;
  }
  emit('reject', props.record);
}
</script>

<template>
  <VEntity>
    <template #start>
      <VEntityField width="100%" max-width="100%">
        <template #description>
          <div class=":uno: flex min-w-0 flex-col gap-2">
            <p class=":uno: line-clamp-2 text-sm font-medium leading-5 text-gray-900">
              {{ replyContent }}
            </p>
            <p class=":uno: line-clamp-1 text-xs leading-5 text-gray-500">
              源内容：{{ sourceContent }}
            </p>
            <div class=":uno: flex min-w-0 flex-wrap items-center gap-x-2 gap-y-1 text-xs text-gray-500">
              <span>{{ assistantText }}</span>
              <span>{{ targetTextMap[record.spec.targetType] }}</span>
              <span>{{ triggerTextMap[record.spec.triggerType] }}</span>
              <span>{{ candidateCountText }}</span>
              <span v-tooltip="targetTooltip" class=":uno: cursor-help text-gray-400">
                对象信息
              </span>
            </div>
          </div>
        </template>
      </VEntityField>
    </template>

    <template #end>
      <VEntityField title="状态" width="7rem">
        <template #description>
          <VStatusDot
            :state="statusStateMap[record.spec.status]"
            :text="statusTextMap[record.spec.status]"
          />
        </template>
      </VEntityField>
      <VEntityField title="生成时间" width="10rem">
        <template #description>
          <span
            v-tooltip="record.spec.status === 'PUBLISHED' ? `发布时间：${publishedAtText}` : ''"
            class=":uno: truncate text-xs tabular-nums text-gray-500"
          >
            {{ generatedAtText }}
          </span>
        </template>
      </VEntityField>
      <VEntityField title="操作" width="7rem">
        <template #description>
          <VButton size="sm" type="secondary" @click="emit('view', record)">
            查看回复
          </VButton>
        </template>
      </VEntityField>
    </template>

    <template v-if="record.spec.error" #footer>
      <div class=":uno: px-4 pb-3">
        <div v-if="record.spec.error" class=":uno: text-xs leading-5 text-red-600">
          {{ record.spec.error }}
        </div>
      </div>
    </template>

    <template v-if="canManage || canReject" #dropdownItems>
      <VDropdownItem @click="emit('view', record)">
        {{ canPublish ? "查看并发布" : "查看回复" }}
      </VDropdownItem>
      <VDropdownItem v-if="canReject" type="danger" @click="handleReject">
        驳回
      </VDropdownItem>
    </template>
  </VEntity>
</template>
