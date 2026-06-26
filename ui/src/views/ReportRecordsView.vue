<script lang="ts" setup>
import {
  IconMessage,
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
import { computed, onMounted, ref, watch } from 'vue';
import {
  type ReportRecord,
  type ReportRecordReason,
  type ReportRecordTarget,
  listReportRecords,
} from '../api/report-records';
import ReportRecordDetailModal from '../components/ReportRecordDetailModal.vue';
import ReportRecordListItem from '../components/ReportRecordListItem.vue';

const page = ref(1);
const pageSize = ref(20);
const total = ref(0);
const records = ref<ReportRecord[]>([]);
const loading = ref(false);
const fetching = ref(false);
const target = ref<ReportRecordTarget>('all');
const reason = ref<ReportRecordReason>('all');
const keyword = ref('');
const selectedRecord = ref<ReportRecord>();

const hasFilters = computed(
  () =>
    target.value !== 'all' ||
    reason.value !== 'all' ||
    Boolean(keyword.value.trim())
);

const targetOptions: Array<{ label: string; value: ReportRecordTarget }> = [
  { label: '全部', value: 'all' },
  { label: '评论', value: 'comment' },
  { label: '回复', value: 'reply' },
];

const reasonOptions: Array<{ label: string; value: ReportRecordReason }> = [
  { label: '全部', value: 'all' },
  { label: '垃圾灌水', value: 'SPAM' },
  { label: '广告推广', value: 'AD' },
  { label: '辱骂骚扰', value: 'ABUSE' },
  { label: '色情低俗', value: 'PORN' },
  { label: '违法违规', value: 'ILLEGAL' },
  { label: '其他问题', value: 'OTHER' },
];

onMounted(() => {
  loadRecords({ initial: true });
});

watch([target, reason], () => {
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
    const result = await listReportRecords({
      page: page.value,
      size: pageSize.value,
      target: target.value,
      reason: reason.value,
      keyword: keyword.value.trim(),
    });
    records.value = result.items ?? [];
    total.value = result.total ?? records.value.length;
  } catch (error) {
    console.error(error);
    Toast.error('举报记录加载失败');
  } finally {
    loading.value = false;
    fetching.value = false;
  }
}

function clearFilters() {
  target.value = 'all';
  reason.value = 'all';
  keyword.value = '';
  page.value = 1;
  loadRecords();
}

function handlePageChange() {
  loadRecords();
}

function openDetail(record: ReportRecord) {
  selectedRecord.value = record;
}

function closeDetail() {
  selectedRecord.value = undefined;
}
</script>

<template>
  <VPageHeader title="举报记录">
    <template #icon>
      <IconMessage class=":uno: h-6 w-6" />
    </template>
  </VPageHeader>

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
                placeholder="搜索内容、举报说明、评论者、来源"
              />
            </div>

            <VSpace spacing="lg" class=":uno: flex-wrap">
              <FilterCleanButton v-if="hasFilters" @click="clearFilters" />
              <FilterDropdown v-model="target" label="类型" :items="targetOptions" />
              <FilterDropdown v-model="reason" label="举报类型" :items="reasonOptions" />
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
          title="暂无举报记录"
          message="前台用户提交举报后，会在这里展示举报类型、说明和处理状态。"
        />
      </Transition>

      <Transition v-else appear name="fade">
        <VEntityContainer>
          <ReportRecordListItem
            v-for="record in records"
            :key="record.name"
            :record="record"
            @view="openDetail"
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

  <ReportRecordDetailModal
    v-if="selectedRecord"
    :record="selectedRecord"
    @close="closeDetail"
  />
</template>
