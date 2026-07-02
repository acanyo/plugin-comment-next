<script lang="ts" setup>
import {
  Dialog,
  IconRefreshLine,
  Toast,
  VCard,
  VEmpty,
  VEntityContainer,
  VLoading,
  VPageHeader,
  VPagination,
  VSpace,
} from '@halo-dev/components';
import { utils } from '@halo-dev/ui-shared';
import { computed, onMounted, ref, watch } from 'vue';
import {
  approveAiModerationRecord,
  type AiModerationRecord,
  type AiModerationTarget,
  listAiModerationRecords,
} from '../api/ai-moderation-records';
import AiModerationRecordListItem from '../components/AiModerationRecordListItem.vue';

type AiModerationStatusFilter = 'intercepted' | 'all';

const page = ref(1);
const pageSize = ref(20);
const total = ref(0);
const records = ref<AiModerationRecord[]>([]);
const loading = ref(false);
const fetching = ref(false);
const target = ref<AiModerationTarget>('all');
const status = ref<AiModerationStatusFilter>('intercepted');
const keyword = ref('');
const approvingRecordKey = ref('');
const managePermissions = ['plugin:comment-next:comments:moderate'];

const canManage = computed(() => utils.permission.has(managePermissions));
const hasFilters = computed(
  () =>
    target.value !== 'all' ||
    status.value !== 'intercepted' ||
    Boolean(keyword.value.trim())
);

const targetOptions: Array<{ label: string; value: AiModerationTarget }> = [
  { label: '全部', value: 'all' },
  { label: '评论', value: 'comment' },
  { label: '回复', value: 'reply' },
];

const statusOptions: Array<{
  label: string;
  value: AiModerationStatusFilter;
}> = [
  { label: '仅看拦截', value: 'intercepted' },
  { label: '全部记录', value: 'all' },
];

onMounted(() => {
  loadRecords({ initial: true });
});

watch([target, status], () => {
  page.value = 1;
  loadRecords();
});

let keywordTimer: number | undefined;
watch(keyword, () => {
  window.clearTimeout(keywordTimer);
  keywordTimer = window.setTimeout(() => {
    page.value = 1;
    loadRecords();
  }, 280);
});

async function loadRecords(options: { initial?: boolean } = {}) {
  loading.value = Boolean(options.initial);
  fetching.value = true;
  try {
    const result = await listAiModerationRecords({
      page: page.value,
      size: pageSize.value,
      target: target.value,
      intercepted: status.value === 'intercepted',
      keyword: keyword.value.trim(),
    });
    records.value = result.items ?? [];
    total.value = result.total ?? records.value.length;
  } catch (error) {
    console.error(error);
    Toast.error('AI 拦截记录加载失败');
  } finally {
    loading.value = false;
    fetching.value = false;
  }
}

function clearFilters() {
  target.value = 'all';
  status.value = 'intercepted';
  keyword.value = '';
  page.value = 1;
  loadRecords();
}

function handlePageChange() {
  loadRecords();
}

function recordKey(record: AiModerationRecord) {
  return `${record.targetType}-${record.name}`;
}

function approveRecord(record: AiModerationRecord) {
  Dialog.warning({
    title: '复核通过',
    description: '通过后会将这条评论或回复标记为已审核，并清除对应的 AI 拦截记录。',
    confirmText: '通过',
    cancelText: '取消',
    onConfirm: async () => {
      approvingRecordKey.value = recordKey(record);
      try {
        await approveAiModerationRecord(record.targetType, record.name);
        Toast.success('已复核通过');
        await loadRecords();
      } catch (error) {
        console.error(error);
        Toast.error('复核通过失败');
      } finally {
        approvingRecordKey.value = '';
      }
    },
  });
}
</script>

<template>
  <VPageHeader title="AI 拦截记录" />

  <div class=":uno: m-0 md:m-4">
    <VCard :body-class="[':uno: !p-0']">
      <template #header>
        <div class=":uno: block w-full bg-gray-50 px-4 py-3">
          <div
            class=":uno: relative flex flex-col flex-wrap items-start gap-4 sm:flex-row sm:items-center"
          >
            <div class=":uno: flex w-full flex-1 items-center sm:w-auto">
              <SearchInput
                v-model="keyword"
                placeholder="搜索内容、原因、分类"
              />
            </div>

            <VSpace spacing="lg" class=":uno: flex-wrap">
              <FilterCleanButton v-if="hasFilters" @click="clearFilters" />
              <FilterDropdown v-model="target" label="类型" :items="targetOptions" />
              <FilterDropdown v-model="status" label="状态" :items="statusOptions" />
              <div class=":uno: flex flex-row gap-2">
                <div
                  v-tooltip="'刷新'"
                  class=":uno: group cursor-pointer rounded p-1 hover:bg-gray-200"
                  @click="loadRecords()"
                >
                  <IconRefreshLine
                    :class="{ ':uno: animate-spin text-gray-900': fetching }"
                    class=":uno: h-4 w-4 text-gray-600 group-hover:text-gray-900"
                  />
                </div>
              </div>
            </VSpace>
          </div>
        </div>
      </template>

      <VLoading v-if="loading" />

      <Transition v-else-if="!records.length" appear name="fade">
        <VEmpty
          title="暂无 AI 拦截记录"
          message="开启 AI 自动审核后，命中的评论和回复会出现在这里。"
        />
      </Transition>

      <Transition v-else appear name="fade">
        <VEntityContainer>
          <AiModerationRecordListItem
            v-for="record in records"
            :key="recordKey(record)"
            :record="record"
            :can-manage="canManage"
            :approving="approvingRecordKey === recordKey(record)"
            @approve="approveRecord"
          />
        </VEntityContainer>
      </Transition>

      <template #footer>
        <VPagination
          v-model:page="page"
          v-model:size="pageSize"
          page-label="页"
          size-label="条 / 页"
          :total-label="`共 ${total} 项数据`"
          :total="total"
          :size-options="[20, 30, 50, 100]"
          @change="handlePageChange"
        />
      </template>
    </VCard>
  </div>
</template>
