<script lang="ts" setup>
import {
  VButton,
  VModal,
  VSpace,
  VStatusDot,
} from '@halo-dev/components';
import { utils } from '@halo-dev/ui-shared';
import { computed, nextTick, onBeforeUnmount, onMounted, ref, watch } from 'vue';
import type { AiReplyCandidate, AiReplyRecord } from '../api/ai-reply-records';

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
  close: [];
  publish: [record: AiReplyRecord, candidateIndex?: number];
  reject: [record: AiReplyRecord];
}>();

const visible = ref(false);
const selectedCandidateIndex = ref<number>();
let closeTimer: ReturnType<typeof setTimeout> | undefined;

const statusTextMap: Record<AiReplyRecord['spec']['status'], string> = {
  PENDING_REVIEW: '待审核',
  PUBLISHED: '已发布',
  REJECTED: '已驳回',
  FAILED: '失败',
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

const targetTextMap: Record<AiReplyRecord['spec']['targetType'], string> = {
  COMMENT: '评论',
  REPLY: '回复',
};

const triggerTextMap: Record<AiReplyRecord['spec']['triggerType'], string> = {
  AUTO: '自动回复',
  MANUAL: '手动生成',
  MENTION: '@ 提及',
};

const candidates = computed<AiReplyCandidate[]>(() => {
  if (props.record.spec.replyCandidates?.length) {
    return props.record.spec.replyCandidates;
  }

  const content = props.record.spec.replyContent?.trim();
  return content
    ? [
        {
          index: props.record.spec.selectedCandidateIndex || 1,
          style: props.record.spec.replyStyle,
          content,
        },
      ]
    : [];
});

const selectedCandidate = computed(() =>
  candidates.value.find((candidate) => candidate.index === selectedCandidateIndex.value)
);

const sourceContent = computed(() =>
  props.record.spec.sourceContent?.trim() || '无源内容'
);

const canPublish = computed(
  () =>
    props.canManage &&
    props.record.spec.status === 'PENDING_REVIEW' &&
    Boolean(selectedCandidate.value)
);

const canReject = computed(
  () => props.canManage && props.record.spec.status === 'PENDING_REVIEW'
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

watch(
  () => props.record,
  () => {
    selectedCandidateIndex.value =
      props.record.spec.selectedCandidateIndex || candidates.value[0]?.index;
  },
  { immediate: true }
);

onMounted(() => {
  nextTick(() => {
    visible.value = true;
  });
});

onBeforeUnmount(() => {
  if (closeTimer) {
    clearTimeout(closeTimer);
  }
});

function requestClose() {
  if (!visible.value) {
    return;
  }
  visible.value = false;
  closeTimer = setTimeout(() => emit('close'), 220);
}

function handleModalClosed() {
  if (closeTimer) {
    clearTimeout(closeTimer);
    closeTimer = undefined;
  }
  emit('close');
}

function publishSelected() {
  if (!canPublish.value) {
    return;
  }
  emit('publish', props.record, selectedCandidateIndex.value);
}

function rejectRecord() {
  if (!canReject.value) {
    return;
  }
  emit('reject', props.record);
}
</script>

<template>
  <VModal
    v-model:visible="visible"
    title="查看 AI 回复"
    :width="760"
    @close="handleModalClosed"
  >
    <div class=":uno: flex flex-col gap-5">
      <div class=":uno: flex flex-wrap items-center gap-x-4 gap-y-2 text-xs text-gray-500">
        <VStatusDot
          :state="statusStateMap[record.spec.status]"
          :text="statusTextMap[record.spec.status]"
        />
        <span>{{ record.spec.assistantName || record.spec.assistantUserName || "评论助手" }}</span>
        <span>{{ targetTextMap[record.spec.targetType] }}</span>
        <span>{{ triggerTextMap[record.spec.triggerType] }}</span>
        <span>生成于 {{ generatedAtText }}</span>
        <span v-if="record.spec.publishedAt">发布于 {{ publishedAtText }}</span>
      </div>

      <div class=":uno: rounded border border-solid border-gray-200 bg-gray-50 px-3 py-2">
        <div class=":uno: mb-1 text-xs font-medium text-gray-500">
          源内容
        </div>
        <p class=":uno: m-0 max-h-28 overflow-auto whitespace-pre-wrap text-sm leading-6 text-gray-800">
          {{ sourceContent }}
        </p>
      </div>

      <div class=":uno: flex flex-col gap-2">
        <div class=":uno: flex items-center justify-between gap-3">
          <div class=":uno: text-sm font-medium text-gray-900">
            AI 回复候选
          </div>
          <div class=":uno: text-xs text-gray-500">
            {{ candidates.length }} 条
          </div>
        </div>

        <div v-if="!candidates.length" class=":uno: rounded border border-solid border-gray-200 px-3 py-6 text-center text-sm text-gray-500">
          暂无可用回复内容
        </div>

        <button
          v-for="candidate in candidates"
          :key="candidate.index"
          type="button"
          class=":uno: w-full cursor-pointer rounded border border-solid bg-white p-3 text-left transition hover:border-gray-300"
          :class="
            selectedCandidateIndex === candidate.index
              ? ':uno: border-primary ring-1 ring-primary'
              : ':uno: border-gray-200'
          "
          @click="selectedCandidateIndex = candidate.index"
        >
          <div class=":uno: mb-2 flex items-center justify-between gap-3">
            <span class=":uno: text-xs font-medium text-gray-500">
              候选 {{ candidate.index }}
              <span v-if="candidate.style" class=":uno: font-normal">
                · {{ candidate.style }}
              </span>
            </span>
            <span
              v-if="selectedCandidateIndex === candidate.index"
              class=":uno: text-xs font-medium text-primary"
            >
              已选中
            </span>
          </div>
          <p class=":uno: m-0 whitespace-pre-wrap text-sm leading-6 text-gray-900">
            {{ candidate.content }}
          </p>
        </button>
      </div>

      <div v-if="record.spec.error" class=":uno: text-xs leading-5 text-red-600">
        {{ record.spec.error }}
      </div>
    </div>

    <template #footer>
      <VSpace>
        <VButton type="secondary" @click="requestClose">
          关闭
        </VButton>
        <VButton
          v-if="canReject"
          type="danger"
          @click="rejectRecord"
        >
          驳回
        </VButton>
        <VButton
          v-if="canPublish"
          type="primary"
          @click="publishSelected"
        >
          发布选中回复
        </VButton>
      </VSpace>
    </template>
  </VModal>
</template>
