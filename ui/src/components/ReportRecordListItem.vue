<script lang="ts" setup>
import { VButton, VEntity, VEntityField, VStatusDot } from '@halo-dev/components';
import { utils } from '@halo-dev/ui-shared';
import { computed } from 'vue';
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

const props = defineProps<{
  record: ReportRecord;
}>();

const emit = defineEmits<{
  view: [record: ReportRecord];
}>();

const targetText = computed(() => getReportTargetText(props.record));
const targetState = computed(() => getReportTargetState(props.record));
const reasonText = computed(() => getReportReasonText(props.record.reason));
const identityText = computed(() => getReportIdentityText(props.record.identityType));
const contentPreview = computed(() => getReportContentPreview(props.record));
const statusText = computed(() => getReportTargetStatusText(props.record));
const statusState = computed(() => getReportTargetStatusState(props.record));
const descriptionPreview = computed(() => props.record.description || '未填写');
const reportedAtText = computed(() =>
  props.record.creationTime ? utils.date.format(props.record.creationTime) : '--'
);
const targetCreationTimeText = computed(() =>
  props.record.targetCreationTime
    ? utils.date.format(props.record.targetCreationTime)
    : '--'
);
const targetTooltip = computed(() =>
  [
    `目标 ID：${props.record.targetName}`,
    props.record.parentName ? `所属评论：${props.record.parentName}` : '',
    props.record.subject ? `来源：${props.record.subject}` : '',
    `举报记录：${props.record.name}`,
    `举报人类型：${identityText.value}`,
  ]
    .filter(Boolean)
    .join('\n')
);

function openDetail() {
  emit('view', props.record);
}
</script>

<template>
  <VEntity>
    <template #start>
      <VEntityField width="34rem" max-width="34rem">
        <template #description>
          <div class=":uno: flex min-w-0 flex-col gap-1.5 whitespace-normal">
            <p
              class=":uno: line-clamp-2 whitespace-normal break-words text-sm font-medium leading-5"
              :class="record.targetExists ? 'text-gray-900' : 'text-gray-400'"
            >
              {{ contentPreview }}
            </p>
            <p class=":uno: line-clamp-1 whitespace-normal break-words text-xs leading-5 text-gray-500">
              <span class=":uno: font-medium text-gray-700">举报说明：</span>
              {{ descriptionPreview }}
            </p>
            <div
              class=":uno: flex min-w-0 flex-wrap items-center gap-x-2 gap-y-1 text-xs text-gray-500"
            >
              <VStatusDot :state="targetState" :text="targetText" />
              <span class=":uno: max-w-36 truncate">
                评论者：{{ record.authorName || '匿名用户' }}
              </span>
              <span v-tooltip="record.subject || '未知来源'" class=":uno: max-w-52 truncate">
                {{ record.subject || '未知来源' }}
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
      <VEntityField title="举报类型" width="8rem">
        <template #description>
          <span
            v-tooltip="reasonText"
            class=":uno: block truncate text-xs text-gray-500"
          >
            {{ reasonText }}
          </span>
        </template>
      </VEntityField>
      <VEntityField title="累计" width="5rem">
        <template #description>
          <span class=":uno: text-xs tabular-nums text-gray-500">
            {{ record.targetReportCount }} 次
          </span>
        </template>
      </VEntityField>
      <VEntityField title="状态" width="6rem">
        <template #description>
          <VStatusDot :state="statusState" :text="statusText" />
        </template>
      </VEntityField>
      <VEntityField title="举报时间" width="10rem">
        <template #description>
          <span
            v-tooltip="`目标创建：${targetCreationTimeText}`"
            class=":uno: truncate text-xs tabular-nums text-gray-500"
          >
            {{ reportedAtText }}
          </span>
        </template>
      </VEntityField>
      <VEntityField title="操作" width="5rem">
        <template #description>
          <VButton size="sm" type="secondary" @click="openDetail">
            详情
          </VButton>
        </template>
      </VEntityField>
    </template>
  </VEntity>
</template>
