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
import type { AiModerationRecord } from '../api/ai-moderation-records';

const props = withDefaults(
  defineProps<{
    record: AiModerationRecord;
    canManage?: boolean;
    approving?: boolean;
  }>(),
  {
    canManage: false,
    approving: false,
  }
);

const emit = defineEmits<{
  approve: [record: AiModerationRecord];
}>();

const categoryLabels: Record<string, string> = {
  spam: '垃圾',
  ads: '广告',
  abuse: '辱骂',
  provocation: '引战',
  porn: '色情',
  flood: '灌水',
  illegal: '违法',
  malicious_link: '恶意链接',
  other: '其他',
};

const actionLabels: Record<string, string> = {
  PENDING_REVIEW: '进审核',
  REJECT: '自动驳回',
  TAG: '打标签',
  NOTICE: '仅提醒',
};

const targetLabel = computed(() =>
  props.record.targetType === 'reply' ? '回复' : '评论'
);
const reviewedAtText = computed(() =>
  props.record.reviewedAt ? utils.date.format(props.record.reviewedAt) : '--'
);
const creationTimeText = computed(() =>
  props.record.creationTime ? utils.date.format(props.record.creationTime) : '--'
);
const confidenceText = computed(() =>
  `${Math.round((props.record.confidence || 0) * 100)}%`
);
const actionText = computed(() =>
  props.record.intercepted
    ? actionLabels[props.record.action || ''] || props.record.action || '未处理'
    : '放行'
);
const resultText = computed(() => {
  if (props.record.rejected) {
    return '已驳回';
  }
  if (props.record.intercepted) {
    return '已拦截';
  }
  return '未命中';
});
const resultState = computed(() => {
  if (props.record.rejected) {
    return 'error';
  }
  if (props.record.intercepted) {
    return 'warning';
  }
  return 'success';
});
const categories = computed(() =>
  (props.record.categories || []).map((category) => ({
    value: category,
    label: categoryLabels[category] || category,
  }))
);
const categoryText = computed(() =>
  categories.value.length
    ? categories.value.map((category) => category.label).join('、')
    : '--'
);
const riskText = computed(() =>
  categories.value.length
    ? `${categoryText.value} · ${confidenceText.value}`
    : confidenceText.value
);
const metaItems = computed(() =>
  [
    `评论者：${props.record.authorName || '匿名用户'}`,
    targetLabel.value,
  ].filter(Boolean)
);
const targetTooltip = computed(() =>
  [
    `ID：${props.record.name}`,
    props.record.parentName ? `所属评论：${props.record.parentName}` : '',
    props.record.subject ? `来源：${props.record.subject}` : '',
  ].filter(Boolean).join('\n')
);
const contentPreview = computed(() => props.record.content || '无内容');
const canApprove = computed(() => props.canManage && props.record.intercepted);

function approveRecord() {
  if (!canApprove.value || props.approving) {
    return;
  }
  emit('approve', props.record);
}
</script>

<template>
  <VEntity>
    <template #start>
      <VEntityField width="100%" max-width="100%">
        <template #description>
          <div class=":uno: flex min-w-0 flex-col gap-1.5">
            <p class=":uno: line-clamp-2 text-sm font-medium leading-5 text-gray-900">
              {{ contentPreview }}
            </p>
            <div
              class=":uno: flex min-w-0 flex-wrap items-center gap-x-2 gap-y-1 text-xs text-gray-500"
            >
              <span
                v-for="item in metaItems"
                :key="item"
                class=":uno: max-w-48 truncate"
              >
                {{ item }}
              </span>
              <span
                v-tooltip="targetTooltip"
                class=":uno: cursor-help text-gray-400"
              >
                对象信息
              </span>
            </div>
          </div>
        </template>
      </VEntityField>
    </template>

    <template #end>
      <VEntityField title="识别结果" width="7rem">
        <template #description>
          <VStatusDot :state="resultState" :text="resultText" />
        </template>
      </VEntityField>
      <VEntityField title="风险" width="10rem">
        <template #description>
          <span
            v-tooltip="riskText"
            class=":uno: block truncate text-xs text-gray-500"
          >
            {{ riskText }}
          </span>
        </template>
      </VEntityField>
      <VEntityField title="处理策略" width="8rem">
        <template #description>
          <span class=":uno: text-xs text-gray-500">{{ actionText }}</span>
        </template>
      </VEntityField>
      <VEntityField title="审核时间" width="10rem">
        <template #description>
          <span
            v-tooltip="`创建时间：${creationTimeText}`"
            class=":uno: truncate text-xs tabular-nums text-gray-500"
          >
            {{ reviewedAtText }}
          </span>
        </template>
      </VEntityField>
      <VEntityField v-if="canApprove" title="操作" width="6rem">
        <template #description>
          <VButton
            size="sm"
            type="secondary"
            :loading="approving"
            @click="approveRecord"
          >
            通过
          </VButton>
        </template>
      </VEntityField>
    </template>

    <template v-if="record.reason" #footer>
      <div class=":uno: px-4 pb-3 text-xs leading-5 text-gray-600">
        <span class=":uno: font-medium text-gray-700">拦截原因：</span>
        <span>{{ record.reason }}</span>
      </div>
    </template>

    <template v-if="canApprove" #dropdownItems>
      <VDropdownItem @click="approveRecord">
        复核通过
      </VDropdownItem>
    </template>
  </VEntity>
</template>
