<script lang="ts" setup>
import { Toast, VButton, VLoading, VModal, VSpace } from '@halo-dev/components';
import { computed, nextTick, onBeforeUnmount, onMounted, ref } from 'vue';
import {
  type AiReplyCandidate,
  type AiReplyRecord,
  generateAiReplyForComment,
  generateAiReplyForReply,
  publishAiReplyRecord,
} from '../api/ai-reply-records';

const props = defineProps<{
  targetType: 'comment' | 'reply';
  targetName: string;
  targetLabel?: string;
}>();

const emit = defineEmits<{
  close: [];
  published: [record: AiReplyRecord];
}>();

const visible = ref(false);
const generating = ref(false);
const publishing = ref(false);
const style = ref('智能推荐');
const candidateCount = ref(4);
const selectedIndex = ref<number>();
const record = ref<AiReplyRecord>();

const styleOptions = [
  {
    label: '智能推荐',
    value: '智能推荐',
    description: '根据上下文自动决定语气和角度',
  },
  {
    label: '自然亲和',
    value: '自然亲和',
    description: '像真实评论区互动，语气轻松克制',
  },
  {
    label: '专业补充',
    value: '专业补充',
    description: '偏技术、方法、边界和实践建议',
  },
  {
    label: '简洁回应',
    value: '简洁回应',
    description: '短句回复，不展开太多背景',
  },
  {
    label: '提问引导',
    value: '提问引导',
    description: '用问题延续讨论，适合开放话题',
  },
];

let closeTimer: ReturnType<typeof setTimeout> | undefined;

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

const candidates = computed<AiReplyCandidate[]>(() => {
  const items = record.value?.spec.replyCandidates;
  if (items?.length) {
    return items;
  }
  const content = record.value?.spec.replyContent?.trim();
  return content ? [{ index: 1, style: style.value, content }] : [];
});

const canPublish = computed(
  () => Boolean(record.value?.metadata.name) && Boolean(selectedIndex.value)
);

async function generateCandidates() {
  generating.value = true;
  try {
    record.value =
      props.targetType === 'comment'
        ? await generateAiReplyForComment(props.targetName, {
            style: style.value,
            candidateCount: candidateCount.value,
          })
        : await generateAiReplyForReply(props.targetName, {
            style: style.value,
            candidateCount: candidateCount.value,
          });
    selectedIndex.value =
      record.value.spec.selectedCandidateIndex ||
      candidates.value[0]?.index ||
      undefined;
    if (record.value.spec.status === 'FAILED') {
      Toast.error(record.value.spec.error || 'AI 回复生成失败');
      return;
    }
    Toast.success('AI 回复候选已生成');
  } catch (error) {
    console.error(error);
    Toast.error('AI 回复生成失败');
  } finally {
    generating.value = false;
  }
}

async function publishSelected() {
  if (!record.value?.metadata.name || !selectedIndex.value) {
    Toast.error('请选择一条 AI 回复候选');
    return;
  }
  publishing.value = true;
  try {
    const publishedRecord = await publishAiReplyRecord(
      record.value.metadata.name,
      selectedIndex.value
    );
    Toast.success('AI 回复已发布');
    emit('published', publishedRecord);
    requestClose();
  } catch (error) {
    console.error(error);
    Toast.error('AI 回复发布失败');
  } finally {
    publishing.value = false;
  }
}

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
</script>

<template>
  <VModal
    v-model:visible="visible"
    title="AI 回复"
    :width="720"
    @close="handleModalClosed"
  >
    <div class=":uno: flex flex-col gap-5">
      <div class=":uno: rounded border border-solid border-gray-200 bg-gray-50 px-3 py-2 text-xs leading-5 text-gray-600">
        {{ targetLabel || "为当前评论生成 AI 回复候选" }}
      </div>

      <div class=":uno: grid gap-3 sm:grid-cols-2">
        <label
          v-for="option in styleOptions"
          :key="option.value"
          class=":uno: cursor-pointer rounded border border-solid p-3 transition hover:border-gray-300"
          :class="
            style === option.value
              ? ':uno: border-primary bg-primary/5'
              : ':uno: border-gray-200 bg-white'
          "
        >
          <div class=":uno: flex items-start gap-2">
            <input
              v-model="style"
              class=":uno: mt-1"
              type="radio"
              :value="option.value"
              :disabled="generating || publishing"
            />
            <div class=":uno: min-w-0">
              <div class=":uno: text-sm font-medium text-gray-900">{{ option.label }}</div>
              <div class=":uno: mt-1 text-xs leading-5 text-gray-500">
                {{ option.description }}
              </div>
            </div>
          </div>
        </label>
      </div>

      <div class=":uno: flex flex-wrap items-center justify-between gap-3">
        <label class=":uno: flex items-center gap-2 text-sm text-gray-700">
          候选数量
          <select
            v-model.number="candidateCount"
            class=":uno: rounded border border-solid border-gray-300 bg-white px-2 py-1 text-sm"
            :disabled="generating || publishing"
          >
            <option :value="3">3 条</option>
            <option :value="4">4 条</option>
            <option :value="5">5 条</option>
          </select>
        </label>
        <VButton type="secondary" :loading="generating" @click="generateCandidates">
          {{ record ? "重新生成" : "生成候选" }}
        </VButton>
      </div>

      <VLoading v-if="generating" />

      <div v-if="candidates.length" class=":uno: flex flex-col gap-2">
        <button
          v-for="candidate in candidates"
          :key="candidate.index"
          type="button"
          class=":uno: w-full cursor-pointer rounded border border-solid bg-white p-3 text-left transition hover:border-gray-300"
          :class="
            selectedIndex === candidate.index
              ? ':uno: border-primary ring-1 ring-primary'
              : ':uno: border-gray-200'
          "
          @click="selectedIndex = candidate.index"
        >
          <div class=":uno: mb-2 flex items-center justify-between gap-3">
            <span class=":uno: text-xs font-medium text-gray-500">
              候选 {{ candidate.index }}
            </span>
            <span
              v-if="selectedIndex === candidate.index"
              class=":uno: text-xs font-medium text-primary"
            >
              当前选中
            </span>
          </div>
          <p class=":uno: m-0 whitespace-pre-wrap text-sm leading-6 text-gray-900">
            {{ candidate.content }}
          </p>
        </button>
      </div>
    </div>

    <template #footer>
      <VSpace>
        <VButton type="secondary" :disabled="publishing" @click="requestClose">
          取消
        </VButton>
        <VButton
          type="primary"
          :disabled="!canPublish || generating"
          :loading="publishing"
          @click="publishSelected"
        >
          发布选中回复
        </VButton>
      </VSpace>
    </template>
  </VModal>
</template>
