<script lang="ts" setup>
import { VButton, VSpace, VStatusDot } from '@halo-dev/components';
import { computed, ref } from 'vue';
import type {
  EmoteLocalizationProgress,
  EmoteLocalizationState,
} from '../api/emotes';

const props = withDefaults(
  defineProps<{
    progress: EmoteLocalizationProgress;
    cancelling?: boolean;
    canManage?: boolean;
  }>(),
  {
    cancelling: false,
    canManage: false,
  }
);

const emit = defineEmits<{
  cancel: [];
  dismiss: [];
  retryFailed: [groupNames: string[]];
}>();

const failuresExpanded = ref(false);

const isRunning = computed(() => props.progress.state === 'RUNNING');

const stateDot = computed<'success' | 'warning' | 'error' | 'default'>(() => {
  const map: Record<
    EmoteLocalizationState,
    'success' | 'warning' | 'error' | 'default'
  > = {
    IDLE: 'default',
    RUNNING: 'warning',
    COMPLETED: props.progress.failedItems > 0 ? 'warning' : 'success',
    CANCELLED: 'warning',
    FAILED: 'error',
  };
  return map[props.progress.state];
});

const stateTitle = computed(() => {
  const {
    state,
    currentGroupDisplayName,
    completedGroups,
    totalGroups,
    failedItems,
  } = props.progress;
  if (state === 'RUNNING') {
    if (currentGroupDisplayName) {
      return `正在本地化「${currentGroupDisplayName}」（${Math.min(completedGroups + 1, Math.max(totalGroups, 1))}/${Math.max(totalGroups, 1)}）`;
    }
    return '正在准备后台本地化任务…';
  }
  if (state === 'COMPLETED') {
    return failedItems > 0
      ? `本地化完成，${failedItems} 张图片失败（已保留原外链，可重试）`
      : '表情图片本地化完成';
  }
  if (state === 'CANCELLED') {
    return '本地化任务已停止（已完成分类的替换已保留）';
  }
  if (state === 'FAILED') {
    return props.progress.errorMessage
      ? `本地化任务异常中断：${props.progress.errorMessage}`
      : '本地化任务异常中断';
  }
  return '';
});

const progressPercent = computed(() => {
  const { totalItems, processedItems, state } = props.progress;
  if (totalItems <= 0) {
    return state === 'COMPLETED' ? 100 : 0;
  }
  return Math.min(100, Math.round((processedItems / totalItems) * 100));
});

const failedGroupNames = computed(() => {
  const names = new Set<string>();
  for (const failure of props.progress.failures ?? []) {
    if (failure.groupName) {
      names.add(failure.groupName);
    }
  }
  if (!names.size && props.progress.failedItems > 0) {
    for (const name of props.progress.targetGroupNames ?? []) {
      names.add(name);
    }
  }
  return Array.from(names);
});
</script>

<template>
  <section
    v-if="props.progress.state !== 'IDLE'"
    aria-label="表情图片本地化进度"
    class=":uno: border-b border-solid border-gray-200 bg-blue-50/40 px-4 py-3"
  >
    <div class=":uno: flex flex-wrap items-center justify-between gap-3">
      <div class=":uno: flex min-w-0 items-center gap-2">
        <VStatusDot
          :animate="isRunning"
          :state="stateDot"
        />
        <span class=":uno: truncate text-sm font-medium text-gray-900">
          {{ stateTitle }}
        </span>
      </div>

      <VSpace>
        <VButton
          v-if="isRunning && props.canManage"
          size="sm"
          type="secondary"
          :loading="props.cancelling"
          @click="emit('cancel')"
        >
          停止任务
        </VButton>
        <template v-else>
          <VButton
            v-if="props.canManage && failedGroupNames.length"
            size="sm"
            type="secondary"
            @click="emit('retryFailed', failedGroupNames)"
          >
            重试失败分类
          </VButton>
          <VButton
            size="sm"
            type="default"
            @click="emit('dismiss')"
          >
            关闭
          </VButton>
        </template>
      </VSpace>
    </div>

    <div class=":uno: mt-2.5">
      <div class=":uno: h-1.5 w-full overflow-hidden rounded-full bg-gray-200">
        <div
          class=":uno: h-full rounded-full transition-all duration-300"
          :class="
            props.progress.state === 'FAILED'
              ? ':uno: bg-red-500'
              : props.progress.failedItems > 0
                ? ':uno: bg-amber-500'
                : ':uno: bg-blue-600'
          "
          :style="{ width: `${progressPercent}%` }"
        />
      </div>

      <div class=":uno: mt-2 flex flex-wrap items-center justify-between gap-x-6 gap-y-1 text-xs text-gray-600">
        <div class=":uno: flex flex-wrap items-center gap-x-4 gap-y-1 tabular-nums">
          <span>
            进度 <strong class=":uno: text-gray-900">{{ props.progress.processedItems }}/{{ props.progress.totalItems }}</strong>
            （{{ progressPercent }}%）
          </span>
          <span>
            已上传 <strong class=":uno: text-gray-900">{{ props.progress.localizedItems }}</strong>
          </span>
          <span v-if="props.progress.reusedItems > 0">
            复用附件 <strong class=":uno: text-gray-900">{{ props.progress.reusedItems }}</strong>
          </span>
          <span v-if="props.progress.skippedItems > 0">
            已跳过 <strong class=":uno: text-gray-900">{{ props.progress.skippedItems }}</strong>
          </span>
          <span v-if="props.progress.failedItems > 0" class=":uno: text-amber-700">
            失败 <strong>{{ props.progress.failedItems }}</strong>
          </span>
        </div>

        <button
          v-if="props.progress.failures.length"
          type="button"
          class=":uno: cursor-pointer border-0 bg-transparent p-0 text-xs font-medium text-blue-600 hover:text-blue-700"
          @click="failuresExpanded = !failuresExpanded"
        >
          {{ failuresExpanded ? '收起失败明细' : `查看失败明细（${props.progress.failures.length}）` }}
        </button>
      </div>
    </div>

    <div
      v-if="failuresExpanded && props.progress.failures.length"
      class=":uno: mt-2.5 max-h-40 overflow-y-auto rounded-md border border-solid border-amber-200 bg-white p-2.5 text-xs"
    >
      <div
        v-for="(item, index) in props.progress.failures"
        :key="`${item.sourceUrl}-${index}`"
        class=":uno: flex flex-col gap-0.5 border-b border-solid border-gray-100 py-1.5 last:border-b-0"
      >
        <div class=":uno: flex flex-wrap items-center justify-between gap-2">
          <span class=":uno: font-medium text-gray-800">
            {{ item.groupDisplayName }}<template v-if="item.itemText"> · {{ item.itemText }}</template>
          </span>
          <span class=":uno: text-amber-700">{{ item.reason }}</span>
        </div>
        <span class=":uno: truncate font-mono text-[11px] text-gray-400">
          {{ item.sourceUrl }}
        </span>
      </div>
    </div>
  </section>
</template>
