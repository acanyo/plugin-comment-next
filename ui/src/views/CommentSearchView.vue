<script lang="ts" setup>
import {
  IconRefreshLine,
  IconSearch,
  VButton,
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
  type CommentSearchItem,
  type CommentSearchStatus,
  type CommentSearchTarget,
  listCommentSearch,
} from '../api/comment-search';
import CommentSearchListItem from '../components/CommentSearchListItem.vue';

const page = ref(1);
const pageSize = ref(20);
const total = ref(0);
const items = ref<CommentSearchItem[]>([]);
const loading = ref(false);
const fetching = ref(false);
const advancedVisible = ref(false);

const target = ref<CommentSearchTarget>('all');
const status = ref<CommentSearchStatus>('all');
const keyword = ref('');
const email = ref('');
const ipAddress = ref('');
const author = ref('');
const content = ref('');
const subject = ref('');

const targetOptions: Array<{ label: string; value: CommentSearchTarget }> = [
  { label: '全部', value: 'all' },
  { label: '评论', value: 'comment' },
  { label: '回复', value: 'reply' },
];

const statusOptions: Array<{ label: string; value: CommentSearchStatus }> = [
  { label: '全部', value: 'all' },
  { label: '可见', value: 'visible' },
  { label: '待审核', value: 'pending' },
  { label: '私密', value: 'hidden' },
];

const advancedFilters = [email, ipAddress, author, content, subject];
const hasAdvancedFilters = computed(() =>
  advancedFilters.some((filter) => Boolean(filter.value.trim()))
);
const hasFilters = computed(
  () =>
    target.value !== 'all' ||
    status.value !== 'all' ||
    Boolean(keyword.value.trim()) ||
    hasAdvancedFilters.value
);

onMounted(() => {
  loadItems({ initial: true });
});

watch([target, status], () => {
  page.value = 1;
  loadItems();
});

let filterTimer: number | undefined;
let latestRequestId = 0;
watch([keyword, ...advancedFilters], () => {
  window.clearTimeout(filterTimer);
  filterTimer = window.setTimeout(() => {
    page.value = 1;
    loadItems();
  }, 280);
});

async function loadItems(options: { initial?: boolean } = {}) {
  const requestId = ++latestRequestId;
  loading.value = Boolean(options.initial);
  fetching.value = true;

  try {
    const result = await listCommentSearch({
      page: page.value,
      size: pageSize.value,
      target: target.value,
      status: status.value,
      keyword: keyword.value.trim(),
      email: email.value.trim(),
      ipAddress: ipAddress.value.trim(),
      author: author.value.trim(),
      content: content.value.trim(),
      subject: subject.value.trim(),
    });
    if (requestId === latestRequestId) {
      items.value = result.items ?? [];
      total.value = result.total ?? items.value.length;
    }
  } catch (error) {
    console.error('Failed to search comments', error);
  } finally {
    if (requestId === latestRequestId) {
      loading.value = false;
      fetching.value = false;
    }
  }
}

function clearFilters() {
  target.value = 'all';
  status.value = 'all';
  keyword.value = '';
  email.value = '';
  ipAddress.value = '';
  author.value = '';
  content.value = '';
  subject.value = '';
  page.value = 1;
}

function handlePageChange() {
  loadItems();
}
</script>

<template>
  <VPageHeader title="评论查询">
    <template #icon>
      <IconSearch class=":uno: h-6 w-6" />
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
                placeholder="搜索内容、评论者、邮箱、IP、来源"
              />
            </div>

            <VSpace spacing="lg" class=":uno: flex-wrap">
              <FilterCleanButton v-if="hasFilters" @click="clearFilters" />
              <FilterDropdown v-model="target" label="类型" :items="targetOptions" />
              <FilterDropdown v-model="status" label="状态" :items="statusOptions" />
              <VButton
                size="sm"
                type="secondary"
                :class="{ ':uno: ring-1 ring-primary': hasAdvancedFilters }"
                @click="advancedVisible = !advancedVisible"
              >
                {{ advancedVisible ? '收起条件' : '更多条件' }}
              </VButton>
              <button
                v-tooltip="'刷新'"
                type="button"
                class=":uno: group cursor-pointer rounded border-0 bg-transparent p-1 hover:bg-gray-200"
                @click="loadItems()"
              >
                <IconRefreshLine
                  :class="{ ':uno: animate-spin text-gray-900': fetching }"
                  class=":uno: h-4 w-4 text-gray-600 group-hover:text-gray-900"
                />
              </button>
            </VSpace>
          </div>

          <div
            v-if="advancedVisible"
            class=":uno: mt-4 grid grid-cols-1 gap-3 border-t border-gray-200 pt-4 sm:grid-cols-2 xl:grid-cols-5"
          >
            <label class=":uno: flex min-w-0 flex-col gap-1 text-xs text-gray-600">
              邮箱
              <input
                v-model="email"
                type="search"
                placeholder="例如 example.com"
                class=":uno: h-9 rounded border border-gray-300 bg-white px-3 text-sm text-gray-900 outline-none focus:border-primary"
              />
            </label>
            <label class=":uno: flex min-w-0 flex-col gap-1 text-xs text-gray-600">
              IP 地址
              <input
                v-model="ipAddress"
                type="search"
                placeholder="例如 192.168"
                class=":uno: h-9 rounded border border-gray-300 bg-white px-3 text-sm text-gray-900 outline-none focus:border-primary"
              />
            </label>
            <label class=":uno: flex min-w-0 flex-col gap-1 text-xs text-gray-600">
              评论者
              <input
                v-model="author"
                type="search"
                placeholder="昵称、用户名或邮箱"
                class=":uno: h-9 rounded border border-gray-300 bg-white px-3 text-sm text-gray-900 outline-none focus:border-primary"
              />
            </label>
            <label class=":uno: flex min-w-0 flex-col gap-1 text-xs text-gray-600">
              评论内容
              <input
                v-model="content"
                type="search"
                placeholder="正文关键词"
                class=":uno: h-9 rounded border border-gray-300 bg-white px-3 text-sm text-gray-900 outline-none focus:border-primary"
              />
            </label>
            <label class=":uno: flex min-w-0 flex-col gap-1 text-xs text-gray-600">
              来源
              <input
                v-model="subject"
                type="search"
                placeholder="文章或页面标识"
                class=":uno: h-9 rounded border border-gray-300 bg-white px-3 text-sm text-gray-900 outline-none focus:border-primary"
              />
            </label>
          </div>
        </div>
      </template>

      <VLoading v-if="loading" />

      <Transition v-else-if="!items.length" appear name="fade">
        <VEmpty
          title="没有匹配的评论"
          message="可按邮箱、IP、评论者、内容和来源组合查询评论与回复。"
        />
      </Transition>

      <Transition v-else appear name="fade">
        <VEntityContainer>
          <CommentSearchListItem
            v-for="item in items"
            :key="`${item.targetType}-${item.name}`"
            :item="item"
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
