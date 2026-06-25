<script lang="ts" setup>
import {
  Dialog,
  IconRefreshLine,
  Toast,
  VButton,
  VCard,
  VEmpty,
  VEntityContainer,
  VLoading,
  VModal,
  VPageHeader,
  VPagination,
  VSpace,
} from '@halo-dev/components';
import { utils } from '@halo-dev/ui-shared';
import { computed, onMounted, ref, watch } from 'vue';
import {
  type SecurityRule,
  type SecurityRuleField,
  type SecurityRuleFieldFilter,
  type SecurityRuleListType,
  type SecurityRuleListTypeFilter,
  type SecurityRuleMatchType,
  cloneSecurityRule,
  createSecurityRule,
  createSecurityRuleDraft,
  deleteSecurityRule,
  listAllSecurityRules,
  listSecurityRules,
  normalizeSecurityRule,
  updateSecurityRule,
} from '../api/security-rules';
import SecurityRuleListItem from '../components/SecurityRuleListItem.vue';
import { useDeletionRefresh } from '../composables/use-deletion-refresh';
import { hasDeletingResources, isDeletingResource } from '../utils/deletion';

const page = ref(1);
const pageSize = ref(20);
const total = ref(0);
const rules = ref<SecurityRule[]>([]);
const loading = ref(false);
const fetching = ref(false);
const saving = ref(false);
const editorVisible = ref(false);
const editing = ref(false);
const form = ref<SecurityRule>(createSecurityRuleDraft());
const keyword = ref('');
const listType = ref<SecurityRuleListTypeFilter>('ALL');
const field = ref<SecurityRuleFieldFilter>('ALL');
const managePermissions = ['plugin:comment-next:security-rules:manage'];

const listTypeOptions: Array<{ label: string; value: SecurityRuleListType }> = [
  { label: '灰名单', value: 'GRAY' },
  { label: '黑名单', value: 'BLACK' },
];

const listTypeFilterItems: Array<{
  label: string;
  value: SecurityRuleListTypeFilter;
}> = [
  { label: '全部', value: 'ALL' },
  { label: '黑名单', value: 'BLACK' },
  { label: '灰名单', value: 'GRAY' },
];

const fieldOptions: Array<{ label: string; value: SecurityRuleField }> = [
  { label: '评论内容', value: 'KEYWORD' },
  { label: 'IP 地址', value: 'IP' },
  { label: '邮箱', value: 'EMAIL' },
  { label: '用户名 / 昵称', value: 'USERNAME' },
  { label: '域名', value: 'DOMAIN' },
  { label: 'User-Agent', value: 'UA' },
];

const fieldFilterItems: Array<{ label: string; value: SecurityRuleFieldFilter }> =
  [
    { label: '全部', value: 'ALL' },
    ...fieldOptions,
  ];

const matchTypeOptions: Array<{ label: string; value: SecurityRuleMatchType }> =
  [
    { label: '包含', value: 'CONTAINS' },
    { label: '精确匹配', value: 'EXACT' },
    { label: '正则', value: 'REGEX' },
  ];

const canManage = computed(() => utils.permission.has(managePermissions));
const hasFilters = computed(
  () =>
    listType.value !== 'ALL' ||
    field.value !== 'ALL' ||
    Boolean(keyword.value.trim())
);
const hasDeletingRules = computed(() => hasDeletingResources(rules.value));
const editorTitle = computed(() => (editing.value ? '编辑规则' : '新增规则'));

onMounted(() => {
  loadRules({ initial: true });
});

watch([listType, field], () => {
  page.value = 1;
  loadRules();
});

let keywordTimer: number | undefined;
watch(keyword, () => {
  window.clearTimeout(keywordTimer);
  keywordTimer = window.setTimeout(() => {
    page.value = 1;
    loadRules();
  }, 280);
});

useDeletionRefresh({
  hasDeletingItems: () => hasDeletingRules.value,
  refresh: () => loadRules(),
});

async function loadRules(options: { initial?: boolean } = {}) {
  loading.value = Boolean(options.initial);
  fetching.value = true;

  try {
    const normalizedKeyword = keyword.value.trim().toLowerCase();
    if (normalizedKeyword) {
      const allRules = await listAllSecurityRules({
        listType: listType.value,
        field: field.value,
      });
      const filteredRules = allRules.filter((rule) =>
        ruleMatchesKeyword(rule, normalizedKeyword)
      );
      total.value = filteredRules.length;
      rules.value = filteredRules.slice(
        (page.value - 1) * pageSize.value,
        page.value * pageSize.value
      );
    } else {
      const result = await listSecurityRules({
        page: page.value,
        size: pageSize.value,
        listType: listType.value,
        field: field.value,
      });
      rules.value = result.items ?? [];
      total.value = result.total ?? rules.value.length;
    }
  } catch (error) {
    console.error(error);
    Toast.error('黑灰名单加载失败');
  } finally {
    loading.value = false;
    fetching.value = false;
  }
}

function clearFilters() {
  listType.value = 'ALL';
  field.value = 'ALL';
  keyword.value = '';
  page.value = 1;
  loadRules();
}

function openCreate() {
  editing.value = false;
  form.value = createSecurityRuleDraft();
  editorVisible.value = true;
}

function openEdit(rule: SecurityRule) {
  if (isDeletingResource(rule)) {
    return;
  }

  editing.value = true;
  form.value = cloneSecurityRule(rule);
  editorVisible.value = true;
}

function closeEditor() {
  if (saving.value) {
    return;
  }
  editorVisible.value = false;
}

async function submitRule() {
  const payload = normalizeSecurityRule(form.value);
  const message = validateRule(payload);
  if (message) {
    Toast.error(message);
    return;
  }

  saving.value = true;
  try {
    if (editing.value) {
      await updateSecurityRule(payload);
      Toast.success('规则已更新');
    } else {
      await createSecurityRule(payload);
      Toast.success('规则已创建');
    }
    editorVisible.value = false;
    await loadRules();
  } catch (error) {
    console.error(error);
    Toast.error('保存规则失败');
  } finally {
    saving.value = false;
  }
}

async function toggleRule(rule: SecurityRule) {
  if (isDeletingResource(rule)) {
    return;
  }

  const payload = cloneSecurityRule(rule);
  payload.spec.enabled = !payload.spec.enabled;

  try {
    await updateSecurityRule(payload);
    Toast.success(payload.spec.enabled ? '规则已启用' : '规则已停用');
    await loadRules();
  } catch (error) {
    console.error(error);
    Toast.error('切换规则状态失败');
  }
}

function removeRule(rule: SecurityRule) {
  if (isDeletingResource(rule)) {
    return;
  }

  Dialog.warning({
    title: '删除规则',
    description: `确认删除「${rule.spec.value || rule.metadata.name}」吗？`,
    confirmType: 'danger',
    confirmText: '删除',
    cancelText: '取消',
    onConfirm: async () => {
      try {
        await deleteSecurityRule(rule.metadata.name);
        Toast.success('规则已删除');
        if (rules.value.length === 1 && page.value > 1) {
          page.value -= 1;
        }
        await loadRules();
      } catch (error) {
        console.error(error);
        Toast.error('删除规则失败');
      }
    },
  });
}

function validateRule(rule: SecurityRule): string {
  if (!rule.spec.value) {
    return '请填写匹配值';
  }
  if (rule.spec.matchType === 'REGEX') {
    try {
      new RegExp(rule.spec.value);
    } catch {
      return '正则表达式格式不正确';
    }
  }
  return '';
}

function ruleMatchesKeyword(rule: SecurityRule, keywordValue: string) {
  return [
    rule.spec.value,
    rule.spec.reason,
    rule.metadata.name,
    rule.spec.listType,
    rule.spec.field,
    rule.spec.matchType,
  ]
    .filter(Boolean)
    .some((value) => String(value).toLowerCase().includes(keywordValue));
}

function handlePageChange() {
  loadRules();
}
</script>

<template>
  <VPageHeader title="黑灰名单">
    <template #actions>
      <VButton v-if="canManage" type="primary" @click="openCreate">
        新增规则
      </VButton>
    </template>
  </VPageHeader>

  <div class=":uno: m-0 md:m-4">
    <VCard :body-class="[':uno: !p-0']">
      <template #header>
        <div class=":uno: block w-full bg-gray-50 px-4 py-3">
          <div
            class=":uno: mb-3 rounded-md border border-solid border-gray-200 bg-white px-3 py-2 text-xs leading-5 text-gray-600"
          >
            灰名单用于处理可疑评论：命中后不直接驳回，只会进入待审核；黑名单用于明确违规内容：命中后进入待审核，并标记为已驳回。
          </div>
          <div
            class=":uno: relative flex flex-col flex-wrap items-start gap-4 sm:flex-row sm:items-center"
          >
            <div class=":uno: flex w-full flex-1 items-center sm:w-auto">
              <SearchInput
                v-model="keyword"
                placeholder="搜索匹配值、原因、规则名"
              />
            </div>

            <VSpace spacing="lg" class=":uno: flex-wrap">
              <FilterCleanButton v-if="hasFilters" @click="clearFilters" />
              <FilterDropdown
                v-model="listType"
                label="名单"
                :items="listTypeFilterItems"
              />
              <FilterDropdown
                v-model="field"
                label="字段"
                :items="fieldFilterItems"
              />
              <div class=":uno: flex flex-row gap-2">
                <div
                  v-tooltip="'刷新'"
                  class=":uno: group cursor-pointer rounded p-1 hover:bg-gray-200"
                  @click="loadRules()"
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

      <Transition v-else-if="!rules.length" appear name="fade">
        <VEmpty
          title="暂无黑灰名单规则"
          message="添加规则后，命中的评论或回复会自动进入审核。"
        >
          <template #actions>
            <VButton v-if="canManage" type="primary" @click="openCreate">
              新增规则
            </VButton>
          </template>
        </VEmpty>
      </Transition>

      <Transition v-else appear name="fade">
        <VEntityContainer>
          <SecurityRuleListItem
            v-for="rule in rules"
            :key="rule.metadata.name"
            :rule="rule"
            :can-manage="canManage"
            @toggle="toggleRule"
            @edit="openEdit"
            @remove="removeRule"
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

  <VModal
    v-model:visible="editorVisible"
    :title="editorTitle"
    :width="640"
    @close="closeEditor"
  >
    <FormKit type="form" :actions="false" :config="{ validationVisibility: 'submit' }">
      <FormKit
        v-model="form.spec.listType"
        type="radio"
        label="名单类型"
        name="listType"
        help="灰名单适合可疑内容，命中后等待人工审核；黑名单适合明确违规内容，会额外标记为已驳回。"
        :options="listTypeOptions"
      />
      <FormKit
        v-model="form.spec.field"
        type="select"
        label="匹配字段"
        name="field"
        :options="fieldOptions"
      />
      <FormKit
        v-model="form.spec.matchType"
        type="radio"
        label="匹配方式"
        name="matchType"
        :options="matchTypeOptions"
      />
      <FormKit
        v-model="form.spec.value"
        type="textarea"
        label="匹配值"
        name="value"
        rows="3"
        validation="required"
      />
      <FormKit
        v-model="form.spec.reason"
        type="textarea"
        label="命中原因"
        name="reason"
        rows="2"
      />
      <FormKit
        v-model.number="form.spec.priority"
        type="number"
        label="优先级"
        name="priority"
        validation="number"
      />
      <FormKit
        v-model="form.spec.enabled"
        type="switch"
        label="启用规则"
        name="enabled"
      />
    </FormKit>

    <template #footer>
      <VSpace>
        <VButton type="secondary" :disabled="saving" @click="closeEditor">
          取消
        </VButton>
        <VButton type="primary" :disabled="saving" @click="submitRule">
          {{ saving ? "保存中" : "保存" }}
        </VButton>
      </VSpace>
    </template>
  </VModal>
</template>
