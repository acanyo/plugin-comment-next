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
  VTabbar,
} from '@halo-dev/components';
import { utils } from '@halo-dev/ui-shared';
import { computed, onMounted, ref, watch } from 'vue';
import {
  type BadgeProfile,
  type BadgeProfileFilter,
  type BadgeProfileIdentityType,
  type BadgeProfileUserInfo,
  type BadgeRule,
  type BadgeRuleFilter,
  type BadgeRuleType,
  type CommentNextBadge,
  cloneBadgeRule,
  createBadgeAssignment,
  createBadgeAssignmentDraft,
  createBadgeRule,
  createBadgeRuleDraft,
  deleteBadgeAssignmentsForIdentity,
  deleteBadgeRule,
  listBadgeProfiles,
  listBadgeRules,
  listHaloUserInfos,
  normalizeBadgeRule,
  syncBadgeProfiles,
  updateBadgeRule,
} from '../api/badge-rules';
import BadgeProfileListItem from '../components/BadgeProfileListItem.vue';
import BadgeRuleListItem from '../components/BadgeRuleListItem.vue';
import { useDeletionRefresh } from '../composables/use-deletion-refresh';
import { hasDeletingResources, isDeletingResource } from '../utils/deletion';

const page = ref(1);
const pageSize = ref(20);
const total = ref(0);
const rules = ref<BadgeRule[]>([]);
const profilePage = ref(1);
const profilePageSize = ref(20);
const profileTotal = ref(0);
const profiles = ref<BadgeProfile[]>([]);
const profileUserInfos = ref<Record<string, BadgeProfileUserInfo>>({});
const loading = ref(false);
const fetching = ref(false);
const profileLoading = ref(false);
const profileFetching = ref(false);
const saving = ref(false);
const syncing = ref(false);
const filter = ref<BadgeRuleFilter>('ALL');
const profileFilter = ref<BadgeProfileFilter>('ALL');
const activeTab = ref('rules');
const editorVisible = ref(false);
const editing = ref(false);
const form = ref<BadgeRule>(createBadgeRuleDraft('USER'));
const assignmentVisible = ref(false);
const assignmentSaving = ref(false);
const assignmentForm = ref(createBadgeAssignmentDraft());
const assignmentUserIdentities = ref<string[]>([]);
const assigningRule = ref<BadgeRule>();
const managePermissions = ['plugin:comment-next:badge-rules:manage'];

const tabItems = [
  { id: 'rules', label: '分配徽章' },
  { id: 'profiles', label: '用户徽章' },
];

const badgeTypeOptions: Array<{ label: string; value: BadgeRuleType }> = [
  { label: '用户徽章', value: 'USER' },
  { label: '活跃等级', value: 'LEVEL' },
];

const assignmentIdentityTypeOptions: Array<{
  label: string;
  value: BadgeProfileIdentityType;
}> = [
  { label: 'Halo 用户', value: 'USER' },
  { label: '邮箱用户', value: 'EMAIL' },
];

const filterItems: Array<{ label: string; value: BadgeRuleFilter }> = [
  { label: '全部', value: 'ALL' },
  { label: '用户徽章', value: 'USER' },
  { label: '活跃等级', value: 'LEVEL' },
];

const profileFilterItems: Array<{ label: string; value: BadgeProfileFilter }> =
  [
    { label: '全部', value: 'ALL' },
    { label: 'Halo 用户', value: 'USER' },
    { label: '邮箱用户', value: 'EMAIL' },
  ];

const hasFilters = computed(() => filter.value !== 'ALL');
const hasProfileFilters = computed(() => profileFilter.value !== 'ALL');
const canManage = computed(() => utils.permission.has(managePermissions));
const hasDeletingRules = computed(() => hasDeletingResources(rules.value));
const hasDeletingProfiles = computed(() =>
  hasDeletingResources(profiles.value)
);
const editorTitle = computed(() => {
  if (editing.value) {
    return form.value.spec.type === 'LEVEL'
      ? '编辑活跃等级徽章'
      : '编辑用户徽章';
  }
  return form.value.spec.type === 'LEVEL' ? '新增活跃等级徽章' : '新增用户徽章';
});
const assignmentTitle = computed(() => {
  const label = assigningRule.value?.spec.label || '用户徽章';
  return `分配徽章：${label}`;
});

onMounted(() => {
  loadRules({ initial: true });
  loadProfiles({ initial: true });
});

watch(filter, () => {
  page.value = 1;
  loadRules();
});

watch(profileFilter, () => {
  profilePage.value = 1;
  loadProfiles();
});

watch(
  () => assignmentForm.value.spec.identityType,
  () => {
    assignmentForm.value.spec.identity = '';
    assignmentUserIdentities.value = [];
  }
);

useDeletionRefresh({
  hasDeletingItems: () => hasDeletingRules.value,
  refresh: () => loadRules(),
});

useDeletionRefresh({
  hasDeletingItems: () => hasDeletingProfiles.value,
  refresh: () => loadProfiles(),
});

async function loadRules(options: { initial?: boolean } = {}) {
  loading.value = Boolean(options.initial);
  fetching.value = true;
  try {
    const result = await listBadgeRules({
      page: page.value,
      size: pageSize.value,
      type: filter.value,
    });
    rules.value = result.items ?? [];
    total.value = result.total ?? rules.value.length;
  } catch (error) {
    console.error(error);
    Toast.error('徽章规则加载失败');
  } finally {
    loading.value = false;
    fetching.value = false;
  }
}

async function loadProfiles(options: { initial?: boolean } = {}) {
  profileLoading.value = Boolean(options.initial);
  profileFetching.value = true;
  try {
    const result = await listBadgeProfiles({
      page: profilePage.value,
      size: profilePageSize.value,
      identityType: profileFilter.value,
    });
    profiles.value = result.items ?? [];
    profileTotal.value = result.total ?? profiles.value.length;
    await loadProfileUserInfos(profiles.value);
  } catch (error) {
    console.error(error);
    Toast.error('用户徽章加载失败');
  } finally {
    profileLoading.value = false;
    profileFetching.value = false;
  }
}

async function loadProfileUserInfos(items: BadgeProfile[]) {
  const names = items
    .filter((profile) => profile.spec.identityType === 'USER')
    .map((profile) => profile.spec.identity);
  profileUserInfos.value = await listHaloUserInfos(names);
}

function clearFilters() {
  filter.value = 'ALL';
}

function clearProfileFilters() {
  profileFilter.value = 'ALL';
}

function openCreate(type: BadgeRuleType) {
  editing.value = false;
  form.value = createBadgeRuleDraft(type);
  editorVisible.value = true;
}

function openEdit(rule: BadgeRule) {
  if (isDeletingResource(rule)) {
    return;
  }

  editing.value = true;
  form.value = cloneBadgeRule(rule);
  editorVisible.value = true;
}

function closeEditor() {
  if (saving.value) {
    return;
  }
  editorVisible.value = false;
}

function openAssign(rule: BadgeRule) {
  if (isDeletingResource(rule)) {
    return;
  }

  assigningRule.value = rule;
  assignmentForm.value = createBadgeAssignmentDraft(rule.metadata.name);
  assignmentUserIdentities.value = [];
  assignmentVisible.value = true;
}

function closeAssignment() {
  if (assignmentSaving.value) {
    return;
  }
  assignmentVisible.value = false;
}

async function submitRule() {
  const payload = normalizeBadgeRule(form.value);
  const message = validateRule(payload);
  if (message) {
    Toast.error(message);
    return;
  }

  saving.value = true;
  try {
    if (editing.value) {
      await updateBadgeRule(payload);
      Toast.success('徽章规则已更新');
    } else {
      await createBadgeRule(payload);
      Toast.success('徽章规则已创建');
    }
    await syncBadgeMetadataSilently();
    editorVisible.value = false;
    await loadRules();
  } catch (error) {
    console.error(error);
    Toast.error('保存徽章规则失败');
  } finally {
    saving.value = false;
  }
}

async function toggleRule(rule: BadgeRule) {
  if (isDeletingResource(rule)) {
    return;
  }

  const payload = cloneBadgeRule(rule);
  payload.spec.enabled = !payload.spec.enabled;
  try {
    await updateBadgeRule(payload);
    Toast.success(payload.spec.enabled ? '徽章规则已启用' : '徽章规则已停用');
    await syncBadgeMetadataSilently();
    await loadRules();
  } catch (error) {
    console.error(error);
    Toast.error('切换状态失败');
  }
}

async function submitAssignment() {
  const payload = assignmentForm.value;
  const userIdentities = normalizeIdentityList(assignmentUserIdentities.value);
  const message = validateAssignment(payload.spec, userIdentities);
  if (message) {
    Toast.error(message);
    return;
  }

  assignmentSaving.value = true;
  try {
    const assignments =
      payload.spec.identityType === 'USER'
        ? userIdentities.map((identity) =>
            createAssignmentPayload(payload.spec.badgeName, 'USER', identity)
          )
        : [
            createAssignmentPayload(
              payload.spec.badgeName,
              'EMAIL',
              payload.spec.identity
            ),
          ];

    await Promise.all(
      assignments.map((assignment) => createBadgeAssignment(assignment))
    );
    Toast.success(
      assignments.length > 1
        ? `徽章已分配给 ${assignments.length} 个用户`
        : '徽章已分配'
    );
    assignmentVisible.value = false;
    await syncBadgeMetadataSilently();
  } catch (error) {
    console.error(error);
    Toast.error('分配徽章失败');
  } finally {
    assignmentSaving.value = false;
  }
}

async function handleSyncBadgeProfiles() {
  await syncBadgeMetadata(false);
}

async function syncBadgeMetadataSilently() {
  await syncBadgeMetadata(true);
}

async function syncBadgeMetadata(silent: boolean) {
  syncing.value = true;
  try {
    const syncResult = await syncBadgeProfiles();
    await loadProfiles();
    if (!silent) {
      Toast.success(
        `徽章档案已同步，更新 ${syncResult.updatedProfiles} 条档案`
      );
    }
  } catch (error) {
    console.error(error);
    Toast.error('徽章档案同步失败');
  } finally {
    syncing.value = false;
  }
}

function removeRule(rule: BadgeRule) {
  if (isDeletingResource(rule)) {
    return;
  }

  Dialog.warning({
    title: '删除徽章规则',
    description: `确认删除徽章「${rule.spec.label || rule.metadata.name}」吗？`,
    confirmType: 'danger',
    confirmText: '删除',
    cancelText: '取消',
    onConfirm: async () => {
      try {
        await deleteBadgeRule(rule.metadata.name);
        Toast.success('徽章规则已删除');
        await syncBadgeMetadataSilently();
        if (rules.value.length === 1 && page.value > 1) {
          page.value -= 1;
        }
        await loadRules();
      } catch (error) {
        console.error(error);
        Toast.error('删除徽章规则失败');
      }
    },
  });
}

function revokeProfileBadge(profile: BadgeProfile, badge: CommentNextBadge) {
  if (isDeletingResource(profile)) {
    return;
  }

  const badgeName = badge.id?.trim();
  if (!badgeName) {
    Toast.error('徽章缺少规则 ID，无法撤销');
    return;
  }

  Dialog.warning({
    title: '撤销用户徽章',
    description: `确认从「${profileIdentityText(profile)}」撤销徽章「${badge.label || badgeName}」吗？`,
    confirmType: 'danger',
    confirmText: '撤销',
    cancelText: '取消',
    onConfirm: async () => {
      try {
        const deletedCount = await deleteBadgeAssignmentsForIdentity({
          badgeName,
          identityType: profile.spec.identityType,
          identity: profile.spec.identity,
        });

        if (!deletedCount) {
          Toast.error('没有找到对应的分配记录');
          return;
        }

        Toast.success('用户徽章已撤销');
        await syncBadgeMetadataSilently();
      } catch (error) {
        console.error(error);
        Toast.error('撤销用户徽章失败');
      }
    },
  });
}

function profileIdentityText(profile: BadgeProfile) {
  if (profile.spec.identityType === 'USER') {
    return (
      profileUserInfos.value[profile.spec.identity]?.displayName ||
      profile.spec.identity
    );
  }
  return profile.spec.identity;
}

async function handlePageChange(value: { page: number; size: number }) {
  page.value = value.page;
  pageSize.value = value.size;
  await loadRules();
}

async function handleProfilePageChange(value: { page: number; size: number }) {
  profilePage.value = value.page;
  profilePageSize.value = value.size;
  await loadProfiles();
}

function validateRule(rule: BadgeRule): string {
  if (!rule.spec.label) {
    return '请填写徽章文案';
  }
  if (rule.spec.type === 'LEVEL' && Number(rule.spec.minComments) < 1) {
    return '活跃等级至少需要 1 条评论';
  }
  return '';
}

function validateAssignment(
  spec: {
    badgeName?: string;
    identityType?: BadgeProfileIdentityType;
    identity?: string;
  },
  userIdentities: string[] = []
): string {
  const badgeName = spec.badgeName?.trim();
  const email = spec.identity?.trim() ?? '';

  if (!badgeName) {
    return '请选择要分配的徽章';
  }
  if (spec.identityType === 'USER') {
    return userIdentities.length ? '' : '请选择 Halo 用户';
  }
  if (!email) {
    return '请填写邮箱';
  }
  if (
    spec.identityType === 'EMAIL' &&
    !/^[^\s@]+@[^\s@]+\.[^\s@]+$/.test(email)
  ) {
    return '请填写正确的邮箱';
  }
  return '';
}

function normalizeIdentityList(value: string[] | string | undefined): string[] {
  const values = Array.isArray(value) ? value : [value];
  return Array.from(
    new Set(values.map((item) => item?.trim()).filter(Boolean) as string[])
  );
}

function createAssignmentPayload(
  badgeName: string,
  identityType: BadgeProfileIdentityType,
  identity: string
) {
  const assignment = createBadgeAssignmentDraft(badgeName);
  assignment.spec.identityType = identityType;
  assignment.spec.identity = identity;
  assignment.spec.enabled = assignmentForm.value.spec.enabled !== false;
  return assignment;
}
</script>

<template>
  <VPageHeader title="评论徽章设置">
    <template #actions>
      <VSpace v-if="canManage">
        <VButton type="secondary" :loading="syncing" @click="handleSyncBadgeProfiles">
          同步徽章数据
        </VButton>
        <VButton
          v-if="activeTab === 'rules'"
          type="secondary"
          @click="openCreate('LEVEL')"
        >
          新增等级徽章
        </VButton>
        <VButton
          v-if="activeTab === 'rules'"
          type="primary"
          @click="openCreate('USER')"
        >
          新增用户徽章
        </VButton>
      </VSpace>
    </template>
  </VPageHeader>

  <div class=":uno: m-0 md:m-4">
    <VCard :body-class="[':uno: !p-0']">
      <template #header>
        <div class=":uno: w-full">
          <div
            class=":uno: border-b border-gray-100 px-4 pt-4"
          >
            <VTabbar
              v-model:activeId="activeTab"
              :items="tabItems"
              type="outline"
            />
          </div>

          <div
            v-if="activeTab === 'rules'"
            class=":uno: block w-full bg-gray-50 px-4 py-3"
          >
            <div
              class=":uno: relative flex flex-col flex-wrap items-start gap-4 sm:flex-row sm:items-center"
            >
              <div class=":uno: flex w-full flex-1 items-center sm:w-auto">
                <span class=":uno: text-sm text-gray-500">
                  共 {{ total }} 项数据
                </span>
              </div>

              <VSpace spacing="lg" class=":uno: flex-wrap">
                <FilterCleanButton v-if="hasFilters" @click="clearFilters" />
                <FilterDropdown v-model="filter" label="类型" :items="filterItems" />
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

          <div
            v-else
            class=":uno: block w-full bg-gray-50 px-4 py-3"
          >
            <div
              class=":uno: relative flex flex-col flex-wrap items-start gap-4 sm:flex-row sm:items-center"
            >
              <div class=":uno: flex w-full flex-1 items-center sm:w-auto">
                <span class=":uno: text-sm text-gray-500">
                  共 {{ profileTotal }} 项数据
                </span>
              </div>

              <VSpace spacing="lg" class=":uno: flex-wrap">
                <FilterCleanButton
                  v-if="hasProfileFilters"
                  @click="clearProfileFilters"
                />
                <FilterDropdown
                  v-model="profileFilter"
                  label="身份"
                  :items="profileFilterItems"
                />
                <div class=":uno: flex flex-row gap-2">
                  <div
                    v-tooltip="'刷新'"
                    class=":uno: group cursor-pointer rounded p-1 hover:bg-gray-200"
                    @click="loadProfiles()"
                  >
                    <IconRefreshLine
                      :class="{ ':uno: animate-spin text-gray-900': profileFetching }"
                      class=":uno: h-4 w-4 text-gray-600 group-hover:text-gray-900"
                    />
                  </div>
                </div>
              </VSpace>
            </div>
          </div>
        </div>
      </template>

      <template v-if="activeTab === 'rules'">
        <VLoading v-if="loading" />

        <Transition v-else-if="!rules.length" appear name="fade">
          <VEmpty
            title="暂无徽章规则"
            message="当前没有徽章规则，你可以新增用户徽章或活跃等级徽章。"
          >
            <template #actions>
              <VSpace v-if="canManage">
                <VButton type="secondary" @click="openCreate('LEVEL')">
                  新增等级徽章
                </VButton>
                <VButton type="primary" @click="openCreate('USER')">
                  新增用户徽章
                </VButton>
              </VSpace>
            </template>
          </VEmpty>
        </Transition>

        <Transition v-else appear name="fade">
          <VEntityContainer>
            <BadgeRuleListItem
              v-for="rule in rules"
              :key="rule.metadata.name"
              :rule="rule"
              :can-manage="canManage"
              @assign="openAssign"
              @toggle="toggleRule"
              @edit="openEdit"
              @remove="removeRule"
            />
          </VEntityContainer>
        </Transition>
      </template>

      <template v-else>
        <VLoading v-if="profileLoading" />

        <Transition v-else-if="!profiles.length" appear name="fade">
          <VEmpty
            title="暂无用户徽章"
            message="当前没有同步出的用户徽章。可以先同步徽章数据，或等待定时同步。"
          >
            <template #actions>
              <VButton v-if="canManage" :loading="syncing" @click="handleSyncBadgeProfiles">
                同步徽章数据
              </VButton>
            </template>
          </VEmpty>
        </Transition>

        <Transition v-else appear name="fade">
          <VEntityContainer>
            <BadgeProfileListItem
              v-for="profile in profiles"
              :key="profile.metadata.name"
              :profile="profile"
              :user-info="profileUserInfos[profile.spec.identity]"
              :can-manage="canManage"
              @revoke="revokeProfileBadge"
            />
          </VEntityContainer>
        </Transition>
      </template>

      <template #footer>
        <VPagination
          v-if="activeTab === 'rules'"
          v-model:page="page"
          v-model:size="pageSize"
          page-label="页"
          size-label="条 / 页"
          :total-label="`共 ${total} 项数据`"
          :total="total"
          :size-options="[20, 30, 50, 100]"
          @change="handlePageChange"
        />
        <VPagination
          v-else
          v-model:page="profilePage"
          v-model:size="profilePageSize"
          page-label="页"
          size-label="条 / 页"
          :total-label="`共 ${profileTotal} 项数据`"
          :total="profileTotal"
          :size-options="[20, 30, 50, 100]"
          @change="handleProfilePageChange"
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
        v-model="form.spec.type"
        type="radio"
        label="徽章类型"
        name="type"
        :disabled="editing"
        :options="badgeTypeOptions"
      />
      <FormKit
        v-model="form.spec.label"
        type="text"
        label="徽章文案"
        name="label"
        placeholder="共建者"
        validation="required"
      />
      <FormKit
        v-model="form.spec.title"
        type="text"
        label="提示文案"
        name="title"
        placeholder="展示在徽章 hover 提示中"
      />
      <FormKit
        v-model="form.spec.icon"
        format="name"
        label="Iconify 图标"
        name="icon"
        type="iconify"
        value-only
      />
      <FormKit
        v-model="form.spec.color"
        label="徽章颜色"
        name="color"
        type="color"
      />
      <FormKit
        v-if="form.spec.type === 'LEVEL'"
        v-model.number="form.spec.minComments"
        type="number"
        label="最少活跃评论数"
        name="minComments"
        min="1"
        validation="required|min:1"
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
        <VButton type="secondary" :disabled="saving" @click="closeEditor">取消</VButton>
        <VButton type="primary" :disabled="saving" @click="submitRule">
          {{ saving ? "保存中" : "保存" }}
        </VButton>
      </VSpace>
    </template>
  </VModal>

  <VModal
    v-model:visible="assignmentVisible"
    :title="assignmentTitle"
    @close="closeAssignment"
  >
    <FormKit type="form" :actions="false" :config="{ validationVisibility: 'submit' }">
      <FormKit
        v-model="assignmentForm.spec.identityType"
        type="radio"
        label="分配对象"
        name="identityType"
        :options="assignmentIdentityTypeOptions"
      />
      <FormKit
        v-if="assignmentForm.spec.identityType === 'USER'"
        v-model="assignmentUserIdentities"
        clearable
        label="Halo 用户"
        multiple
        name="assignmentUser"
        placeholder="选择一个或多个 Halo 用户"
        type="userSelect"
        validation="required"
      />
      <FormKit
        v-else
        v-model="assignmentForm.spec.identity"
        type="email"
        label="邮箱"
        name="email"
        placeholder="user@example.com"
        validation="required|email"
      />
    </FormKit>

    <template #footer>
      <VSpace>
        <VButton type="secondary" :disabled="assignmentSaving" @click="closeAssignment">
          取消
        </VButton>
        <VButton type="primary" :disabled="assignmentSaving" @click="submitAssignment">
          {{ assignmentSaving ? "分配中" : "分配" }}
        </VButton>
      </VSpace>
    </template>
  </VModal>
</template>
