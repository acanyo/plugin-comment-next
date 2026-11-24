<script lang="ts" setup>
import { VButton } from '@halo-dev/components';
import { events, utils } from '@halo-dev/ui-shared';
import { computed, onBeforeUnmount, onMounted, ref } from 'vue';
import {
  getIpLocationBackfillStatus,
  type IpLocationBackfillState,
  type IpLocationBackfillStatus,
  startIpLocationBackfill,
} from '../api/ip-location-backfill';

const canBackfill = utils.permission.has(['*']);
const status = ref<IpLocationBackfillStatus>();
const starting = ref(false);
const statusUnavailable = ref(false);
const running = computed(() => status.value?.progress.state === 'RUNNING');
const canStart = computed(
  () => status.value?.lookupEnabled && !running.value && !starting.value
);

const stateLabels: Record<IpLocationBackfillState, string> = {
  IDLE: '',
  RUNNING: '正在后台补查…',
  COMPLETED: '补查完成',
  STOPPED: '属地查询已关闭，补查已停止',
  FAILED: '补查中断，可重新运行以处理剩余记录',
};

const counts = computed(() => {
  const progress = status.value?.progress;
  if (!progress || progress.state === 'IDLE') return [];
  const { saved, skipped, unresolved, failed } = progress;
  return [
    { label: '已检查', value: saved + skipped + unresolved + failed },
    { label: '已补齐', value: saved },
    { label: '已跳过', value: skipped },
    { label: '未查到', value: unresolved },
    { label: '处理失败', value: failed },
  ];
});

let pollTimer: number | undefined;
let latestRequest = 0;

function handleConfigSaved({
  group,
  pluginName,
}: {
  group: string;
  pluginName: string;
}) {
  if (
    group === 'ipLocation' &&
    pluginName === 'PluginCommentNext' &&
    !starting.value
  ) {
    // The server's plugin setting cache updates after Halo saves the form.
    void syncStatus(false, 4);
  }
}

onMounted(() => {
  if (!canBackfill) return;
  events.on('core:plugin:configMap:updated', handleConfigSaved);
  void syncStatus();
});

onBeforeUnmount(() => {
  events.off('core:plugin:configMap:updated', handleConfigSaved);
  window.clearTimeout(pollTimer);
  latestRequest += 1;
});

async function syncStatus(start = false, checksAfterSave = 0) {
  if (start && !canStart.value) return;
  const request = ++latestRequest;
  window.clearTimeout(pollTimer);
  if (start) starting.value = true;

  try {
    const result = await (start
      ? startIpLocationBackfill()
      : getIpLocationBackfillStatus());
    if (request !== latestRequest) return;
    const lookupChanged = result.lookupEnabled !== status.value?.lookupEnabled;
    status.value = result;
    statusUnavailable.value = false;
    if (checksAfterSave > 0 && !lookupChanged) {
      pollTimer = window.setTimeout(
        () => void syncStatus(false, checksAfterSave - 1),
        750
      );
    } else if (result.progress.state === 'RUNNING') {
      pollTimer = window.setTimeout(() => void syncStatus(), 2500);
    }
  } catch {
    if (request !== latestRequest) return;
    if (start) void syncStatus();
    else statusUnavailable.value = true;
  } finally {
    if (start) starting.value = false;
  }
}
</script>

<template>
  <section
    v-if="canBackfill"
    aria-label="历史评论属地补查"
    class=":uno: mb-4 rounded border border-gray-200 bg-gray-50 p-4"
  >
    <div class=":uno: flex flex-wrap items-start justify-between gap-x-6 gap-y-3">
      <div class=":uno: min-w-0 flex-1 basis-64">
        <div class=":uno: text-sm text-gray-900 font-medium">历史评论属地</div>
        <p class=":uno: mt-1 text-xs text-gray-600 leading-5">
          将缺失属地的评论和回复所记录的公网 IP 发给已配置的查询服务。关闭页面后任务会继续。
        </p>
      </div>
      <VButton
        v-if="statusUnavailable"
        size="sm"
        type="secondary"
        @click="syncStatus()"
      >
        重新读取进度
      </VButton>
      <VButton
        v-else
        size="sm"
        type="secondary"
        :disabled="!canStart"
        :loading="starting || running"
        @click="syncStatus(true)"
      >
        {{ running ? '补查中…' : '补查历史 IP 属地' }}
      </VButton>
    </div>
    <p v-if="statusUnavailable" class=":uno: mt-2 text-xs text-gray-600" role="status">
      暂时无法读取补查进度。
    </p>
    <p v-else-if="status && !status.lookupEnabled && !running" class=":uno: mt-2 text-xs text-gray-600">
      请开启「显示评论 IP 属地」和「启用在线属地查询」，然后保存设置。
    </p>
    <div
      v-if="status && counts.length"
      class=":uno: mt-3 border-t border-gray-200 pt-3"
      role="status"
    >
      <p class=":uno: text-xs text-gray-700 font-medium">{{ stateLabels[status.progress.state] }}</p>
      <dl class=":uno: mt-2 flex flex-wrap gap-x-5 gap-y-2 text-xs">
        <div v-for="count in counts" :key="count.label" class=":uno: flex items-baseline gap-1.5">
          <dt class=":uno: text-gray-600">{{ count.label }}</dt>
          <dd class=":uno: m-0 text-gray-900 font-medium tabular-nums">{{ count.value }}</dd>
        </div>
      </dl>
    </div>
  </section>
</template>
