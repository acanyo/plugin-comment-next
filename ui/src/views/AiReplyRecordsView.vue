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
  type AiReplyRecord,
  type AiReplyRecordStatus,
  type AiReplyRecordTarget,
  type AiReplyRecordTrigger,
  listAiReplyRecords,
  publishAiReplyRecord,
  rejectAiReplyRecord,
} from '../api/ai-reply-records';
import AiReplyRecordListItem from '../components/AiReplyRecordListItem.vue';
import AiReplyRecordReviewModal from '../components/AiReplyRecordReviewModal.vue';

const page = ref(1);
const pageSize = ref(20);
const total = ref(0);
const records = ref<AiReplyRecord[]>([]);
const reviewingRecord = ref<AiReplyRecord>();
const loading = ref(false);
const fetching = ref(false);
const keyword = ref('');
const target = ref<AiReplyRecordTarget>('all');
const trigger = ref<AiReplyRecordTrigger>('all');
const status = ref<AiReplyRecordStatus>('pending');
const managePermissions = ['plugin:comment-next:comments:moderate'];

const canManage = computed(() => utils.permission.has(managePermissions));
const hasFilters = computed(
  () =>
    target.value !== 'all' ||
    trigger.value !== 'all' ||
    status.value !== 'pending' ||
    Boolean(keyword.value.trim())
);

const targetOptions: Array<{ label: string; value: AiReplyRecordTarget }> = [
  { label: '全部', value: 'all' },
  { label: '评论', value: 'comment' },
  { label: '回复', value: 'reply' },
];

const triggerOptions: Array<{ label: string; value: AiReplyRecordTrigger }> = [
  { label: '全部', value: 'all' },
  { label: '自动回复', value: 'auto' },
  { label: '手动生成', value: 'manual' },
  { label: '@ 提及', value: 'mention' },
];

const statusOptions: Array<{ label: string; value: AiReplyRecordStatus }> = [
  { label: '待审核', value: 'pending' },
  { label: '已发布', value: 'published' },
  { label: '已驳回', value: 'rejected' },
  { label: '失败', value: 'failed' },
  { label: '全部', value: 'all' },
];

onMounted(() => {
  loadRecords({ initial: true });
});

watch([target, trigger, status], () => {
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
    const result = await listAiReplyRecords({
      page: page.value,
      size: pageSize.value,
      target: target.value,
      trigger: trigger.value,
      status: status.value,
      keyword: keyword.value.trim(),
    });
    records.value = result.items ?? [];
    total.value = result.total ?? records.value.length;
  } catch (error) {
    console.error(error);
    Toast.error('AI 回复记录加载失败');
  } finally {
    loading.value = false;
    fetching.value = false;
  }
}

function clearFilters() {
  target.value = 'all';
  trigger.value = 'all';
  status.value = 'pending';
  keyword.value = '';
  page.value = 1;
  loadRecords();
}

function handlePageChange() {
  loadRecords();
}

function openReviewModal(record: AiReplyRecord) {
  reviewingRecord.value = record;
}

function publishRecord(record: AiReplyRecord, candidateIndex?: number) {
  Dialog.warning({
    title: '发布 AI 回复',
    description: candidateIndex
      ? `确认发布候选 ${candidateIndex} 为评论回复吗？`
      : '确认将这条 AI 生成内容发布为评论回复吗？',
    confirmText: '发布',
    cancelText: '取消',
    onConfirm: async () => {
      try {
        await publishAiReplyRecord(record.metadata.name, candidateIndex);
        Toast.success('AI 回复已发布');
        reviewingRecord.value = undefined;
        await loadRecords();
      } catch (error) {
        console.error(error);
        Toast.error('AI 回复发布失败');
      }
    },
  });
}

function rejectRecord(record: AiReplyRecord) {
  Dialog.warning({
    title: '驳回 AI 回复',
    description: '确认驳回这条 AI 生成内容吗？驳回后不会发布到评论区。',
    confirmType: 'danger',
    confirmText: '驳回',
    cancelText: '取消',
    onConfirm: async () => {
      try {
        await rejectAiReplyRecord(record.metadata.name);
        Toast.success('AI 回复已驳回');
        reviewingRecord.value = undefined;
        await loadRecords();
      } catch (error) {
        console.error(error);
        Toast.error('AI 回复驳回失败');
      }
    },
  });
}
</script>

<template>
  <VPageHeader title="AI 回复管理" />

  <div class=":uno: m-0 md:m-4">
    <VCard :body-class="[':uno: !p-0']">
      <template #header>
        <div class=":uno: block w-full bg-gray-50 px-4 py-3">
          <div class=":uno: relative flex flex-col flex-wrap items-start gap-4 sm:flex-row sm:items-center">
            <div class=":uno: flex w-full flex-1 items-center sm:w-auto">
              <SearchInput
                v-model="keyword"
                placeholder="搜索生成内容、源内容、用户"
              />
            </div>

            <VSpace spacing="lg" class=":uno: flex-wrap">
              <FilterCleanButton v-if="hasFilters" @click="clearFilters" />
              <FilterDropdown v-model="target" label="目标" :items="targetOptions" />
              <FilterDropdown v-model="trigger" label="触发" :items="triggerOptions" />
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
          title="暂无 AI 回复记录"
          message="开启 AI 自动回复后，生成的回复会先出现在这里。"
        />
      </Transition>

      <Transition v-else appear name="fade">
        <VEntityContainer>
          <AiReplyRecordListItem
            v-for="record in records"
            :key="record.metadata.name"
            :record="record"
            :can-manage="canManage"
            @view="openReviewModal"
            @reject="rejectRecord"
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

  <AiReplyRecordReviewModal
    v-if="reviewingRecord"
    :record="reviewingRecord"
    :can-manage="canManage"
    @close="reviewingRecord = undefined"
    @publish="publishRecord"
    @reject="rejectRecord"
  />
</template>
