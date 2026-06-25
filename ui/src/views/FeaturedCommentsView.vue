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
  type FeaturedCommentItem,
  type FeaturedCommentTarget,
  listFeaturedComments,
} from '../api/featured-comments';
import { updateCommentModeration } from '../api/comment-moderation';
import FeaturedCommentListItem from '../components/FeaturedCommentListItem.vue';

const page = ref(1);
const pageSize = ref(20);
const total = ref(0);
const items = ref<FeaturedCommentItem[]>([]);
const loading = ref(false);
const fetching = ref(false);
const keyword = ref('');
const target = ref<FeaturedCommentTarget>('all');
const managePermissions = ['plugin:comment-next:comments:moderate'];

const canManage = computed(() => utils.permission.has(managePermissions));
const hasFilters = computed(
  () => target.value !== 'all' || Boolean(keyword.value.trim())
);

const targetOptions: Array<{ label: string; value: FeaturedCommentTarget }> = [
  { label: '全部', value: 'all' },
  { label: '评论', value: 'comment' },
  { label: '回复', value: 'reply' },
];

onMounted(() => {
  loadItems({ initial: true });
});

watch(target, () => {
  page.value = 1;
  loadItems();
});

let keywordTimer: number | undefined;
watch(keyword, () => {
  window.clearTimeout(keywordTimer);
  keywordTimer = window.setTimeout(() => {
    page.value = 1;
    loadItems();
  }, 280);
});

async function loadItems(options: { initial?: boolean } = {}) {
  loading.value = Boolean(options.initial);
  fetching.value = true;

  try {
    const result = await listFeaturedComments({
      page: page.value,
      size: pageSize.value,
      target: target.value,
      keyword: keyword.value.trim(),
    });
    items.value = result.items ?? [];
    total.value = result.total ?? items.value.length;
  } catch (error) {
    console.error(error);
    Toast.error('精选评论加载失败');
  } finally {
    loading.value = false;
    fetching.value = false;
  }
}

function clearFilters() {
  target.value = 'all';
  keyword.value = '';
  page.value = 1;
  loadItems();
}

function handlePageChange() {
  loadItems();
}

function unfeatureItem(item: FeaturedCommentItem) {
  Dialog.warning({
    title: '取消精选',
    description: '确认取消这条内容的精选状态吗？取消后前台精选组件不会再展示它。',
    confirmText: '取消精选',
    cancelText: '保留',
    onConfirm: async () => {
      try {
        await updateCommentModeration(item.targetType, item.metadata.name, {
          featured: false,
        });
        Toast.success('已取消精选');
        await loadItems();
      } catch (error) {
        console.error(error);
        Toast.error('取消精选失败');
      }
    },
  });
}

function untopItem(item: FeaturedCommentItem) {
  Dialog.warning({
    title: '取消置顶',
    description: '确认取消这条内容的置顶状态吗？精选状态会保留。',
    confirmText: '取消置顶',
    cancelText: '保留',
    onConfirm: async () => {
      try {
        const state = await updateCommentModeration(item.targetType, item.metadata.name, {
          top: false,
          priority: 0,
        });
        item.top = state.top;
        item.priority = state.priority;
        Toast.success('已取消置顶');
      } catch (error) {
        console.error(error);
        Toast.error('取消置顶失败');
      }
    },
  });
}
</script>

<template>
  <VPageHeader title="精选评论" />

  <div class=":uno: m-0 md:m-4">
    <VCard :body-class="[':uno: !p-0']">
      <template #header>
        <div class=":uno: block w-full bg-gray-50 px-4 py-3">
          <div
            class=":uno: relative flex flex-col flex-wrap items-start gap-4 sm:flex-row sm:items-center"
          >
            <div class=":uno: flex w-full flex-1 items-center sm:w-auto">
              <SearchInput v-model="keyword" placeholder="搜索内容、评论者、来源" />
            </div>

            <VSpace spacing="lg" class=":uno: flex-wrap">
              <FilterCleanButton v-if="hasFilters" @click="clearFilters" />
              <FilterDropdown v-model="target" label="类型" :items="targetOptions" />
              <div class=":uno: flex flex-row gap-2">
                <div
                  v-tooltip="'刷新'"
                  class=":uno: group cursor-pointer rounded p-1 hover:bg-gray-200"
                  @click="loadItems()"
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

      <Transition v-else-if="!items.length" appear name="fade">
        <VEmpty
          title="暂无精选评论"
          message="在评论列表中将评论或回复设为精选后，会出现在这里。"
        />
      </Transition>

      <Transition v-else appear name="fade">
        <VEntityContainer>
          <FeaturedCommentListItem
            v-for="item in items"
            :key="`${item.targetType}-${item.metadata.name}`"
            :item="item"
            :can-manage="canManage"
            @unfeature="unfeatureItem"
            @untop="untopItem"
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
