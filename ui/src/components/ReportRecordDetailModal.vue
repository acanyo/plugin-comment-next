<script lang="ts" setup>
import {
  VButton,
  VModal,
  VSpace,
  VStatusDot,
} from '@halo-dev/components';
import { utils } from '@halo-dev/ui-shared';
import { computed, nextTick, onBeforeUnmount, onMounted, ref } from 'vue';
import type { ReportRecord } from '../api/report-records';
import {
  getReportContentPreview,
  getReportIdentityText,
  getReportReasonText,
  getReportTargetState,
  getReportTargetStatusState,
  getReportTargetStatusText,
  getReportTargetText,
} from '../utils/report-records';
import ConsoleCommentContent from './ConsoleCommentContent.vue';

const props = defineProps<{
  record: ReportRecord;
}>();

const emit = defineEmits<{
  close: [];
}>();

const visible = ref(false);
let closeTimer: ReturnType<typeof setTimeout> | undefined;

const targetText = computed(() => getReportTargetText(props.record));
const targetState = computed(() => getReportTargetState(props.record));
const statusText = computed(() => getReportTargetStatusText(props.record));
const statusState = computed(() => getReportTargetStatusState(props.record));
const reasonText = computed(() => getReportReasonText(props.record.reason));
const identityText = computed(() => getReportIdentityText(props.record.identityType));
const contentText = computed(() => getReportContentPreview(props.record));
const descriptionText = computed(() => props.record.description || '未填写');

const reportedAtText = computed(() =>
  props.record.creationTime ? utils.date.format(props.record.creationTime) : '--'
);
const targetCreationTimeText = computed(() =>
  props.record.targetCreationTime
    ? utils.date.format(props.record.targetCreationTime)
    : '--'
);

const detailItems = computed(() =>
  [
    { label: '评论者', value: props.record.authorName || '匿名用户' },
    { label: '举报人类型', value: identityText.value },
    { label: '举报类型', value: reasonText.value },
    { label: '累计举报', value: `${props.record.targetReportCount} 次` },
    { label: '目标 ID', value: props.record.targetName },
    { label: '所属评论', value: props.record.parentName || '--' },
    { label: '来源', value: props.record.subject || '未知来源' },
    { label: '目标创建', value: targetCreationTimeText.value },
    { label: '举报时间', value: reportedAtText.value },
    { label: '举报记录', value: props.record.name },
  ]
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
</script>

<template>
  <VModal
    v-model:visible="visible"
    title="举报详情"
    :width="760"
    @close="handleModalClosed"
  >
    <div class=":uno: flex flex-col gap-5">
      <div class=":uno: flex flex-wrap items-center gap-x-4 gap-y-2 text-xs text-gray-500">
        <VStatusDot :state="targetState" :text="targetText" />
        <VStatusDot :state="statusState" :text="statusText" />
        <span>{{ reasonText }}</span>
        <span>{{ record.targetReportCount }} 次举报</span>
        <span>举报于 {{ reportedAtText }}</span>
      </div>

      <section class=":uno: flex flex-col gap-2">
        <div class=":uno: text-sm font-medium text-gray-900">被举报内容</div>
        <div
          class=":uno: max-h-72 overflow-auto rounded border border-solid border-gray-200 bg-white p-3 text-sm leading-6 text-gray-900"
          :class="record.targetExists ? '' : ':uno: text-gray-400'"
        >
          <ConsoleCommentContent
            v-if="record.targetExists && record.content"
            :content="record.content"
          />
          <p v-else class=":uno: m-0 whitespace-pre-wrap break-words">
            {{ contentText }}
          </p>
        </div>
      </section>

      <section class=":uno: flex flex-col gap-2">
        <div class=":uno: text-sm font-medium text-gray-900">举报说明</div>
        <p class=":uno: m-0 max-h-40 overflow-auto rounded border border-solid border-gray-200 bg-gray-50 p-3 whitespace-pre-wrap break-words text-sm leading-6 text-gray-700">
          {{ descriptionText }}
        </p>
      </section>

      <section class=":uno: grid gap-3 sm:grid-cols-2">
        <div
          v-for="item in detailItems"
          :key="item.label"
          class=":uno: min-w-0 border-0 border-b border-solid border-gray-100 pb-2"
        >
          <div class=":uno: mb-1 text-xs font-medium text-gray-500">
            {{ item.label }}
          </div>
          <div class=":uno: break-all text-sm leading-5 text-gray-900">
            {{ item.value }}
          </div>
        </div>
      </section>
    </div>

    <template #footer>
      <VSpace>
        <VButton type="secondary" @click="requestClose">
          关闭
        </VButton>
      </VSpace>
    </template>
  </VModal>
</template>
