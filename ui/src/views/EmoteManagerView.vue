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
import { computed, onBeforeUnmount, onMounted, ref, watch } from 'vue';
import {
  cancelEmoteLocalization,
  createEmoteGroup,
  DEFAULT_EMOTE_SOURCE_URL,
  deleteEmoteGroup,
  EMOTE_SOURCE_PRESETS,
  type EmoteGroup,
  type EmoteGroupFilter,
  type EmoteLocalizationOptions,
  type EmoteLocalizationProgress,
  fetchEmotePacksFromUrl,
  getEmoteLocalizationOptions,
  getEmoteLocalizationStatus,
  isPresetEmoteSourceUrl,
  listAllEmoteGroups,
  listEmoteGroups,
  normalizeEmoteGroup,
  parseRawEmotePacks,
  type RawEmotePacks,
  rawPacksToGroups,
  sortEmoteGroupsByPriority,
  startEmoteLocalization,
  summarizeGroupLocalization,
  updateEmoteGroup,
} from '../api/emotes';
import {
  buildLottieEmoteGroups,
  fetchLottieCatalog,
  LottiePluginUnavailableError,
} from '../api/lottie';
import EmoteGroupListItem from '../components/EmoteGroupListItem.vue';
import EmoteGroupSortModal from '../components/EmoteGroupSortModal.vue';
import EmoteLocalizationFields from '../components/EmoteLocalizationFields.vue';
import EmoteLocalizationProgressBanner from '../components/EmoteLocalizationProgressBanner.vue';
import EmoteLocalizeModal from '../components/EmoteLocalizeModal.vue';
import { useDeletionRefresh } from '../composables/use-deletion-refresh';
import { hasDeletingResources, isDeletingResource } from '../utils/deletion';

const page = ref(1);
const pageSize = ref(20);
const total = ref(0);
const groups = ref<EmoteGroup[]>([]);
const loading = ref(false);
const fetching = ref(false);
const lottieSyncing = ref(false);
const importing = ref(false);
const keyword = ref('');
const checkAll = ref(false);
const selectedGroupNames = ref<string[]>([]);
const importKnownGroups = ref<EmoteGroup[]>([]);
const defaultSourceVisible = ref(false);
const defaultSourceLoading = ref(false);
const defaultSourceUrl = ref(DEFAULT_EMOTE_SOURCE_URL);
const defaultSourcePacks = ref<RawEmotePacks>({});
const selectedDefaultNames = ref<string[]>([]);
const customVisible = ref(false);
const customJson = ref('');
const customFileInput = ref<HTMLInputElement>();
const customJsonFileName = ref('');
const customJsonParsing = ref(false);
const customPacks = ref<RawEmotePacks>({});
const selectedCustomNames = ref<string[]>([]);
const sortVisible = ref(false);
const sortLoading = ref(false);
const sortSaving = ref(false);
const sortGroups = ref<EmoteGroup[]>([]);
const filter = ref<EmoteGroupFilter>('ALL');
const managePermissions = ['plugin:comment-next:emotes:manage'];

const localizationOptions = ref<EmoteLocalizationOptions>();
const localizationOptionsLoading = ref(false);
const localizationProgress = ref<EmoteLocalizationProgress>();
const localizationBannerDismissed = ref(false);
const localizationStarting = ref(false);
const localizationCancelling = ref(false);
const scanningExternalGroups = ref(false);
const localizeModalVisible = ref(false);
const localizeModalGroups = ref<EmoteGroup[]>([]);
const localizeOnDefaultImport = ref(false);
const localizeOnCustomImport = ref(false);
const selectedPolicyName = ref('');
const selectedAttachmentGroupName = ref('');
const useAbsoluteLocalizedUrl = ref(true);
let localizationPollTimer: number | undefined;
let localizationPollSeq = 0;

const filterItems: Array<{ label: string; value: EmoteGroupFilter }> = [
  { label: '全部', value: 'ALL' },
  { label: '颜文字', value: 'emoticon' },
  { label: '图片表情', value: 'image' },
  { label: 'Lottie 动画', value: 'lottie' },
];

const canManage = computed(() => utils.permission.has(managePermissions));
const hasFilters = computed(
  () => filter.value !== 'ALL' || Boolean(keyword.value.trim())
);
const visibleSelectableGroupNames = computed(() =>
  groups.value
    .filter((group) => !isDeletingResource(group))
    .map((group) => group.metadata.name)
    .filter((name): name is string => Boolean(name))
);
const selectedVisibleGroups = computed(() => {
  const selectedNames = new Set(selectedGroupNames.value);
  return groups.value.filter(
    (group) =>
      !isDeletingResource(group) && selectedNames.has(group.metadata.name)
  );
});
const importedDisplayNames = computed(
  () =>
    new Set(
      importKnownGroups.value
        .filter((group) => group.spec.provider !== 'LOTTIE')
        .map((group) => group.spec.displayName)
    )
);
const defaultPackSummaries = computed(() =>
  summarizePacks(defaultSourcePacks.value)
);
const normalizedDefaultSourceUrl = computed(
  () => defaultSourceUrl.value.trim() || DEFAULT_EMOTE_SOURCE_URL
);
const defaultImportSourceType = computed(() =>
  isPresetEmoteSourceUrl(normalizedDefaultSourceUrl.value)
    ? 'DEFAULT'
    : 'CUSTOM'
);
const customPackSummaries = computed(() => summarizePacks(customPacks.value));
const hasDeletingGroups = computed(() => hasDeletingResources(groups.value));
const localizationSiteOrigin = computed(
  () => localizationOptions.value?.externalBaseUrl || ''
);
const localizationRunning = computed(
  () => localizationProgress.value?.state === 'RUNNING'
);
const showLocalizationBanner = computed(
  () =>
    Boolean(localizationProgress.value) &&
    localizationProgress.value?.state !== 'IDLE' &&
    (localizationRunning.value || !localizationBannerDismissed.value)
);
const selectedLocalizableGroups = computed(() =>
  selectedVisibleGroups.value.filter(
    (group) =>
      summarizeGroupLocalization(group, localizationSiteOrigin.value)
        .externalImages > 0
  )
);
const selectedDefaultHasImages = computed(() =>
  selectedDefaultNames.value.some(
    (name) => defaultSourcePacks.value[name]?.type === 'image'
  )
);
const selectedCustomHasImages = computed(() =>
  selectedCustomNames.value.some(
    (name) => customPacks.value[name]?.type === 'image'
  )
);

onMounted(() => {
  loadGroups({ initial: true });
  if (canManage.value) {
    void loadLocalizationOptions();
    void pollLocalizationStatus({ silent: true });
  }
});

onBeforeUnmount(() => {
  clearLocalizationPollTimer();
  localizationPollSeq += 1;
});

watch([filter, keyword], () => {
  page.value = 1;
  loadGroups();
});

watch(
  selectedGroupNames,
  () => {
    syncCheckAll();
  },
  { deep: true }
);

watch(
  () => visibleSelectableGroupNames.value.join(','),
  () => {
    const visibleNames = new Set(visibleSelectableGroupNames.value);
    selectedGroupNames.value = selectedGroupNames.value.filter((name) =>
      visibleNames.has(name)
    );
    syncCheckAll();
  }
);

watch(defaultSourceUrl, () => {
  defaultSourcePacks.value = {};
  selectedDefaultNames.value = [];
});

useDeletionRefresh({
  hasDeletingItems: () => hasDeletingGroups.value,
  refresh: () => loadGroups(),
});

async function loadGroups(options: { initial?: boolean } = {}) {
  loading.value = Boolean(options.initial);
  fetching.value = true;
  try {
    const normalizedKeyword = keyword.value.trim().toLowerCase();

    if (normalizedKeyword) {
      const allGroups = await listAllEmoteGroups({ type: filter.value });
      const filteredGroups = filterEmoteGroups(
        sortEmoteGroupsByPriority(allGroups),
        normalizedKeyword
      );
      total.value = filteredGroups.length;
      groups.value = filteredGroups.slice(
        (page.value - 1) * pageSize.value,
        page.value * pageSize.value
      );
    } else {
      const result = await listEmoteGroups({
        page: page.value,
        size: pageSize.value,
        type: filter.value,
      });
      groups.value = result.items ?? [];
      total.value = result.total ?? groups.value.length;
    }
  } catch (error) {
    console.error(error);
    Toast.error('表情分类加载失败');
  } finally {
    loading.value = false;
    fetching.value = false;
  }
}

function clearFilters() {
  filter.value = 'ALL';
  keyword.value = '';
}

function handleCheckAllChange(event: Event) {
  const checked = (event.target as HTMLInputElement).checked;
  selectedGroupNames.value = checked
    ? [...visibleSelectableGroupNames.value]
    : [];
  syncCheckAll();
}

function syncCheckAll() {
  checkAll.value =
    visibleSelectableGroupNames.value.length > 0 &&
    visibleSelectableGroupNames.value.every((name) =>
      selectedGroupNames.value.includes(name)
    );
}

function openDefaultSource() {
  defaultSourceVisible.value = true;
  refreshKnownGroupsForImport();
  void loadLocalizationOptions();
  if (!Object.keys(defaultSourcePacks.value).length) {
    loadDefaultSource();
  }
}

async function loadDefaultSource() {
  if (!isValidHttpUrl(normalizedDefaultSourceUrl.value)) {
    Toast.error('请输入正确的在线表情源地址');
    return;
  }

  defaultSourceLoading.value = true;
  try {
    defaultSourcePacks.value = await fetchEmotePacksFromUrl(
      normalizedDefaultSourceUrl.value
    );
    selectedDefaultNames.value = [];
    Toast.success('在线表情源已加载');
  } catch (error) {
    console.error(error);
    Toast.error('在线表情源加载失败');
  } finally {
    defaultSourceLoading.value = false;
  }
}

function selectPresetSourceUrl(url: string) {
  if (
    defaultSourceUrl.value === url &&
    Object.keys(defaultSourcePacks.value).length
  ) {
    return;
  }
  defaultSourceUrl.value = url;
  defaultSourcePacks.value = {};
  selectedDefaultNames.value = [];
  void loadDefaultSource();
}

function openCustomImport() {
  customVisible.value = true;
  refreshKnownGroupsForImport();
  void loadLocalizationOptions();
}

function parseCustomJson() {
  if (!customJson.value.trim()) {
    Toast.error('请先粘贴表情 JSON，或选择 JSON 文件导入');
    return;
  }

  try {
    applyCustomPacks(parseRawEmotePacks(customJson.value));
  } catch (error) {
    console.error(error);
    Toast.error('表情 JSON 格式不正确');
  }
}

function triggerCustomJsonFileSelect() {
  customFileInput.value?.click();
}

async function handleCustomJsonFileChange(event: Event) {
  const input = event.target as HTMLInputElement;
  const file = input.files?.[0];

  if (!file) {
    return;
  }

  customJsonParsing.value = true;

  try {
    await nextFrame();
    const content = await file.text();
    await nextFrame();
    applyCustomPacks(parseRawEmotePacks(content), file.name);
    customJson.value = '';
  } catch (error) {
    console.error(error);
    Toast.error('JSON 文件格式不正确');
  } finally {
    customJsonParsing.value = false;
    input.value = '';
  }
}

function applyCustomPacks(rawPacks: RawEmotePacks, fileName = '') {
  customPacks.value = rawPacks;
  customJsonFileName.value = fileName;
  selectedCustomNames.value = [];
  Toast.success(`已解析 ${Object.keys(rawPacks).length} 个表情分类`);
}

function clearCustomParsedPacks() {
  customPacks.value = {};
  selectedCustomNames.value = [];
  customJsonFileName.value = '';
}

function nextFrame() {
  return new Promise<void>((resolve) => {
    window.requestAnimationFrame(() => resolve());
  });
}

async function importDefaultGroups() {
  const shouldLocalize =
    localizeOnDefaultImport.value && selectedDefaultHasImages.value;
  if (shouldLocalize && !ensureLocalizationPolicyReady()) {
    return;
  }

  const imported = await importGroups({
    rawPacks: defaultSourcePacks.value,
    names: selectedDefaultNames.value,
    sourceType: defaultImportSourceType.value,
    sourceUrl: normalizedDefaultSourceUrl.value,
  });
  if (!imported) {
    return;
  }
  defaultSourceVisible.value = false;
  if (shouldLocalize) {
    const imageGroupNames = imported
      .filter((group) => group.spec.type === 'image')
      .map((group) => group.metadata.name);
    if (imageGroupNames.length) {
      await triggerLocalization(imageGroupNames);
    }
  }
}

async function importCustomGroups() {
  const shouldLocalize =
    localizeOnCustomImport.value && selectedCustomHasImages.value;
  if (shouldLocalize && !ensureLocalizationPolicyReady()) {
    return;
  }

  const imported = await importGroups({
    rawPacks: customPacks.value,
    names: selectedCustomNames.value,
    sourceType: 'CUSTOM',
  });
  if (!imported) {
    return;
  }
  customVisible.value = false;
  if (shouldLocalize) {
    const imageGroupNames = imported
      .filter((group) => group.spec.type === 'image')
      .map((group) => group.metadata.name);
    if (imageGroupNames.length) {
      await triggerLocalization(imageGroupNames);
    }
  }
}

async function importGroups({
  rawPacks,
  names,
  sourceType,
  sourceUrl,
}: {
  rawPacks: RawEmotePacks;
  names: string[];
  sourceType: 'DEFAULT' | 'CUSTOM';
  sourceUrl?: string;
}): Promise<EmoteGroup[] | null> {
  if (!names.length) {
    Toast.error('请先选择要导入的表情分类');
    return null;
  }

  importing.value = true;
  try {
    const existingGroups = await listAllEmoteGroups();
    importKnownGroups.value = existingGroups;
    const payloads = rawPacksToGroups({
      rawPacks,
      names,
      sourceType,
      sourceUrl,
      existingGroups,
    });
    const existingNames = new Set(
      existingGroups
        .filter((group) => group.spec.provider !== 'LOTTIE')
        .map((group) => group.spec.displayName)
    );

    const savedGroups = await Promise.all(
      payloads.map((group) =>
        existingNames.has(group.spec.displayName)
          ? updateEmoteGroup(group)
          : createEmoteGroup(group)
      )
    );
    Toast.success(`已导入 ${payloads.length} 个表情分类`);
    await loadGroups();
    await refreshKnownGroupsForImport();
    return savedGroups;
  } catch (error) {
    console.error(error);
    Toast.error('表情分类导入失败');
    return null;
  } finally {
    importing.value = false;
  }
}

async function loadLocalizationOptions(force = false) {
  if (!canManage.value) {
    return;
  }
  if (
    !force &&
    (localizationOptions.value || localizationOptionsLoading.value)
  ) {
    return;
  }

  localizationOptionsLoading.value = true;
  try {
    const options = await getEmoteLocalizationOptions();
    localizationOptions.value = options;
    if (!selectedPolicyName.value && options.defaultPolicyName) {
      selectedPolicyName.value = options.defaultPolicyName;
    }
    if (
      !selectedAttachmentGroupName.value &&
      options.defaultAttachmentGroupName
    ) {
      selectedAttachmentGroupName.value = options.defaultAttachmentGroupName;
    }
  } catch (error) {
    console.error(error);
  } finally {
    localizationOptionsLoading.value = false;
  }
}

function ensureLocalizationPolicyReady(): boolean {
  if (localizationRunning.value) {
    Toast.warning('当前已有表情本地化任务正在运行，请等待完成或先停止');
    return false;
  }
  if (!selectedPolicyName.value.trim()) {
    Toast.error('请先选择 Halo 附件存储策略');
    return false;
  }
  return true;
}

function openLocalizeModal(targetGroups: EmoteGroup[]) {
  const imageGroups = targetGroups.filter(
    (group) =>
      group.spec.provider !== 'LOTTIE' &&
      group.spec.type === 'image' &&
      summarizeGroupLocalization(group, localizationSiteOrigin.value)
        .externalImages > 0
  );
  if (!imageGroups.length) {
    Toast.info('所选分类没有需要本地化的外链图片');
    return;
  }
  if (localizationRunning.value) {
    Toast.warning('当前已有表情本地化任务正在运行');
    return;
  }

  localizeModalGroups.value = imageGroups;
  localizeModalVisible.value = true;
  void loadLocalizationOptions();
}

async function openLocalizeAllExternalModal() {
  if (scanningExternalGroups.value || localizationRunning.value) {
    return;
  }

  scanningExternalGroups.value = true;
  try {
    const imageGroups = await listAllEmoteGroups({ type: 'image' });
    openLocalizeModal(imageGroups);
  } catch (error) {
    console.error(error);
    Toast.error('扫描外链表情分类失败');
  } finally {
    scanningExternalGroups.value = false;
  }
}

async function confirmLocalizeModal() {
  if (!ensureLocalizationPolicyReady()) {
    return;
  }
  const groupNames = localizeModalGroups.value
    .map((group) => group.metadata.name)
    .filter(Boolean);
  if (!groupNames.length) {
    return;
  }

  const started = await triggerLocalization(groupNames);
  if (started) {
    localizeModalVisible.value = false;
    selectedGroupNames.value = [];
  }
}

async function triggerLocalization(groupNames: string[]): Promise<boolean> {
  if (!groupNames.length || !ensureLocalizationPolicyReady()) {
    return false;
  }

  localizationStarting.value = true;
  try {
    const progress = await startEmoteLocalization({
      groupNames,
      policyName: selectedPolicyName.value.trim(),
      attachmentGroupName:
        selectedAttachmentGroupName.value.trim() || undefined,
      useAbsoluteUrl: useAbsoluteLocalizedUrl.value,
    });
    localizationProgress.value = progress;
    localizationBannerDismissed.value = false;
    Toast.success('已启动后台本地化任务');
    scheduleLocalizationPoll(1200);
    return true;
  } catch (error) {
    console.error(error);
    const message =
      (error as { response?: { data?: { detail?: string; message?: string } } })
        ?.response?.data?.detail ||
      (error as { response?: { data?: { detail?: string; message?: string } } })
        ?.response?.data?.message ||
      '启动表情本地化任务失败';
    Toast.error(message);
    return false;
  } finally {
    localizationStarting.value = false;
  }
}

async function handleCancelLocalization() {
  if (localizationCancelling.value) {
    return;
  }
  localizationCancelling.value = true;
  try {
    const progress = await cancelEmoteLocalization();
    clearLocalizationPollTimer();
    localizationProgress.value = progress;
    Toast.info('已停止本地化任务');
    await loadGroups();
  } catch (error) {
    console.error(error);
    Toast.error('停止本地化任务失败');
  } finally {
    localizationCancelling.value = false;
  }
}

async function handleRetryFailedGroups(groupNames: string[]) {
  if (!groupNames.length) {
    return;
  }
  try {
    const allGroups = await listAllEmoteGroups({ type: 'image' });
    const targetSet = new Set(groupNames);
    const matched = allGroups.filter((group) =>
      targetSet.has(group.metadata.name)
    );
    if (!matched.length) {
      Toast.info('未找到可重试的表情分类');
      return;
    }
    openLocalizeModal(matched);
  } catch (error) {
    console.error(error);
    Toast.error('加载重试分类失败');
  }
}

function dismissLocalizationBanner() {
  localizationBannerDismissed.value = true;
}

function clearLocalizationPollTimer() {
  if (localizationPollTimer !== undefined) {
    window.clearTimeout(localizationPollTimer);
    localizationPollTimer = undefined;
  }
}

function scheduleLocalizationPoll(delayMs = 1500) {
  clearLocalizationPollTimer();
  localizationPollTimer = window.setTimeout(() => {
    void pollLocalizationStatus({ silent: true });
  }, delayMs);
}

async function pollLocalizationStatus(options: { silent?: boolean } = {}) {
  const seq = ++localizationPollSeq;
  try {
    const previousState = localizationProgress.value?.state;
    const current = await getEmoteLocalizationStatus();
    if (seq !== localizationPollSeq) {
      return;
    }
    localizationProgress.value = current;

    if (current.state === 'RUNNING') {
      localizationBannerDismissed.value = false;
      scheduleLocalizationPoll(1500);
      return;
    }

    clearLocalizationPollTimer();
    if (previousState === 'RUNNING') {
      await loadGroups();
      if (current.state === 'COMPLETED') {
        if (current.failedItems > 0) {
          Toast.warning(
            `表情本地化完成，成功 ${current.localizedItems + current.reusedItems} 张，失败 ${current.failedItems} 张`
          );
        } else {
          Toast.success('表情图片已全部本地化到 Halo 附件库');
        }
      } else if (current.state === 'FAILED') {
        Toast.error(current.errorMessage || '表情本地化任务异常中断');
      }
    }
  } catch (error) {
    if (!options.silent) {
      console.error(error);
    }
    clearLocalizationPollTimer();
  }
}

async function refreshKnownGroupsForImport() {
  try {
    importKnownGroups.value = await listAllEmoteGroups();
  } catch (error) {
    console.error(error);
    Toast.error('已有表情分类加载失败');
  }
}

function selectAllDefaultPacks() {
  selectedDefaultNames.value = defaultPackSummaries.value.map(
    (pack) => pack.name
  );
}

function selectNewDefaultPacks() {
  selectedDefaultNames.value = defaultPackSummaries.value
    .filter((pack) => !importedDisplayNames.value.has(pack.name))
    .map((pack) => pack.name);
}

function clearDefaultSelection() {
  selectedDefaultNames.value = [];
}

function selectAllCustomPacks() {
  selectedCustomNames.value = customPackSummaries.value.map(
    (pack) => pack.name
  );
}

function selectNewCustomPacks() {
  selectedCustomNames.value = customPackSummaries.value
    .filter((pack) => !importedDisplayNames.value.has(pack.name))
    .map((pack) => pack.name);
}

function clearCustomSelection() {
  selectedCustomNames.value = [];
}

async function toggleGroup(group: EmoteGroup) {
  if (isDeletingResource(group)) {
    return;
  }

  const payload = normalizeEmoteGroup({
    ...group,
    metadata: { ...group.metadata },
    spec: {
      ...group.spec,
      enabled: !group.spec.enabled,
      items: [...group.spec.items],
    },
  });

  try {
    await updateEmoteGroup(payload);
    Toast.success(payload.spec.enabled ? '表情分类已启用' : '表情分类已停用');
    await loadGroups();
  } catch (error) {
    console.error(error);
    Toast.error('切换表情分类状态失败');
  }
}

async function updateSelectedGroupsEnabled(enabled: boolean) {
  if (!selectedVisibleGroups.value.length) {
    return;
  }

  try {
    await Promise.all(
      selectedVisibleGroups.value.map((group) =>
        updateEmoteGroup(
          normalizeEmoteGroup({
            ...group,
            metadata: { ...group.metadata },
            spec: {
              ...group.spec,
              enabled,
              items: [...group.spec.items],
            },
          })
        )
      )
    );
    Toast.success(enabled ? '已启用所选表情分类' : '已停用所选表情分类');
    selectedGroupNames.value = [];
    await loadGroups();
  } catch (error) {
    console.error(error);
    Toast.error(enabled ? '批量启用失败' : '批量停用失败');
  }
}

async function syncLottieGroups() {
  if (lottieSyncing.value) {
    return;
  }

  lottieSyncing.value = true;
  try {
    const [catalog, existingGroups] = await Promise.all([
      fetchLottieCatalog(),
      listAllEmoteGroups(),
    ]);
    const { upserts, stale } = buildLottieEmoteGroups(catalog, existingGroups);
    const existingNames = new Set(
      existingGroups.map((group) => group.metadata.name)
    );

    await Promise.all(
      upserts.map((group) =>
        existingNames.has(group.metadata.name)
          ? updateEmoteGroup(group)
          : createEmoteGroup(group)
      )
    );
    await Promise.all(
      stale.map((group) => deleteEmoteGroup(group.metadata.name))
    );
    Toast.success(`已同步 ${upserts.length} 个 Lottie 分组`);
    selectedGroupNames.value = [];
    page.value = 1;
    await loadGroups();
  } catch (error) {
    console.error(error);
    if (error instanceof LottiePluginUnavailableError) {
      Toast.error('未检测到已启用的 plugin-lottie');
    } else {
      Toast.error('Lottie 动画库同步失败，未清理现有镜像');
    }
  } finally {
    lottieSyncing.value = false;
  }
}

function removeGroup(group: EmoteGroup) {
  if (isDeletingResource(group)) {
    return;
  }

  Dialog.warning({
    title: '删除表情分类',
    description: `确认删除「${group.spec.displayName}」吗？评论中已经插入的表情不会被删除。`,
    confirmText: '删除',
    cancelText: '取消',
    onConfirm: async () => {
      try {
        await deleteEmoteGroup(group.metadata.name);
        Toast.success('表情分类已删除');
        await loadGroups();
      } catch (error) {
        console.error(error);
        Toast.error('删除表情分类失败');
      }
    },
  });
}

function removeSelectedGroups() {
  const selectedGroups = selectedVisibleGroups.value;

  if (!selectedGroups.length) {
    return;
  }

  Dialog.warning({
    title: '删除所选表情分类',
    description: `确认删除已选择的 ${selectedGroups.length} 个表情分类吗？评论中已经插入的表情不会被删除。`,
    confirmType: 'danger',
    confirmText: '删除',
    cancelText: '取消',
    onConfirm: async () => {
      try {
        await Promise.all(
          selectedGroups.map((group) => deleteEmoteGroup(group.metadata.name))
        );
        Toast.success('所选表情分类已删除');
        selectedGroupNames.value = [];
        await loadGroups();
      } catch (error) {
        console.error(error);
        Toast.error('批量删除表情分类失败');
      }
    },
  });
}

function handlePageChange() {
  loadGroups();
}

function summarizePacks(rawPacks: RawEmotePacks) {
  return Object.entries(rawPacks).map(([name, pack]) => ({
    name,
    type: pack.type === 'image' ? 'image' : 'emoticon',
    count: Array.isArray(pack.container) ? pack.container.length : 0,
  }));
}

function filterEmoteGroups(groups: EmoteGroup[], normalizedKeyword: string) {
  return groups.filter((group) =>
    [
      group.spec.displayName,
      group.metadata.name,
      group.spec.provider,
      group.spec.sourceType,
      group.spec.sourceUrl,
      group.spec.sourceRef,
    ]
      .filter(Boolean)
      .some((value) => String(value).toLowerCase().includes(normalizedKeyword))
  );
}

function typeText(type: string) {
  if (type === 'lottie') return 'Lottie 动画';
  return type === 'image' ? '图片表情' : '颜文字';
}

function isValidHttpUrl(value: string) {
  try {
    const url = new URL(value);
    return url.protocol === 'http:' || url.protocol === 'https:';
  } catch {
    return false;
  }
}

async function openSortModal() {
  sortVisible.value = true;
  sortLoading.value = true;

  try {
    sortGroups.value = sortEmoteGroupsByPriority(await listAllEmoteGroups());
  } catch (error) {
    console.error(error);
    Toast.error('表情排序数据加载失败');
    sortVisible.value = false;
  } finally {
    sortLoading.value = false;
  }
}

async function saveEmoteGroupOrder(sortedGroups: EmoteGroup[]) {
  sortSaving.value = true;

  try {
    await Promise.all(
      sortedGroups.map((group, index) =>
        updateEmoteGroup(
          normalizeEmoteGroup({
            ...group,
            metadata: { ...group.metadata },
            spec: {
              ...group.spec,
              priority: index,
              items: [...group.spec.items],
            },
          })
        )
      )
    );
    Toast.success('前台表情排序已保存');
    sortVisible.value = false;
    page.value = 1;
    await loadGroups();
  } catch (error) {
    console.error(error);
    Toast.error('前台表情排序保存失败');
  } finally {
    sortSaving.value = false;
  }
}
</script>

<template>
  <VPageHeader title="评论表情管理">
    <template #actions>
      <VSpace v-if="canManage">
        <VButton type="secondary" :loading="sortLoading" @click="openSortModal">
          手动排序
        </VButton>
        <VButton type="secondary" :loading="lottieSyncing" @click="syncLottieGroups">
          同步 Lottie
        </VButton>
        <VButton type="secondary" @click="openCustomImport">
          导入 JSON
        </VButton>
        <VButton type="primary" @click="openDefaultSource">
          在线表情源
        </VButton>
      </VSpace>
    </template>
  </VPageHeader>

  <div class=":uno: m-0 md:m-4">
    <VCard :body-class="[':uno: !p-0']">
      <template #header>
        <div class=":uno: block w-full bg-gray-50 px-4 py-3">
          <div class=":uno: relative flex flex-col flex-wrap items-start gap-4 sm:flex-row sm:items-center">
            <div
              v-if="canManage"
              class=":uno: hidden items-center sm:flex"
            >
              <input
                :checked="checkAll"
                :disabled="!visibleSelectableGroupNames.length"
                type="checkbox"
                @change="handleCheckAllChange"
              />
            </div>

            <div class=":uno: flex w-full flex-1 items-center sm:w-auto">
              <SearchInput
                v-if="!selectedGroupNames.length"
                v-model="keyword"
                placeholder="搜索表情分类"
              />
              <VSpace v-else>
                <span class=":uno: text-sm text-gray-500">
                  已选择 {{ selectedGroupNames.length }} 项
                </span>
                <VButton
                  type="secondary"
                  size="sm"
                  @click="updateSelectedGroupsEnabled(true)"
                >
                  启用
                </VButton>
                <VButton
                  type="secondary"
                  size="sm"
                  @click="updateSelectedGroupsEnabled(false)"
                >
                  停用
                </VButton>
                <VButton
                  v-if="selectedLocalizableGroups.length"
                  type="secondary"
                  size="sm"
                  :disabled="localizationRunning"
                  @click="openLocalizeModal(selectedLocalizableGroups)"
                >
                  本地化到附件库
                </VButton>
                <VButton type="danger" size="sm" @click="removeSelectedGroups">
                  删除
                </VButton>
              </VSpace>
            </div>

            <VSpace spacing="lg" class=":uno: flex-wrap">
              <span class=":uno: text-sm text-gray-500">
                共 {{ total }} 个分类
              </span>
              <VButton
                v-if="canManage && total > 0"
                size="sm"
                type="default"
                :loading="scanningExternalGroups"
                :disabled="localizationRunning"
                @click="openLocalizeAllExternalModal"
              >
                本地化外链
              </VButton>
              <FilterCleanButton v-if="hasFilters" @click="clearFilters" />
              <FilterDropdown v-model="filter" label="类型" :items="filterItems" />
              <div class=":uno: flex flex-row gap-2">
                <div
                  v-tooltip="'刷新'"
                  class=":uno: group cursor-pointer rounded p-1 hover:bg-gray-200"
                  @click="loadGroups()"
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

      <EmoteLocalizationProgressBanner
        v-if="showLocalizationBanner && localizationProgress"
        :progress="localizationProgress"
        :cancelling="localizationCancelling"
        :can-manage="canManage"
        @cancel="handleCancelLocalization"
        @dismiss="dismissLocalizationBanner"
        @retry-failed="handleRetryFailedGroups"
      />

      <VLoading v-if="loading" />

      <Transition v-else-if="!groups.length" appear name="fade">
        <VEmpty
          title="暂无表情分类"
          message="可以从在线表情源选择分类导入，也可以粘贴 OwO JSON 导入自定义表情。"
        >
          <template #actions>
            <VSpace v-if="canManage">
              <VButton type="secondary" :loading="lottieSyncing" @click="syncLottieGroups">
                同步 Lottie
              </VButton>
              <VButton type="secondary" @click="openCustomImport">导入 JSON</VButton>
              <VButton type="primary" @click="openDefaultSource">在线表情源</VButton>
            </VSpace>
          </template>
        </VEmpty>
      </Transition>

      <Transition v-else appear name="fade">
        <VEntityContainer>
          <EmoteGroupListItem
            v-for="group in groups"
            :key="group.metadata.name"
            :group="group"
            :can-manage="canManage"
            :is-selected="selectedGroupNames.includes(group.metadata.name)"
            :site-origin="localizationSiteOrigin"
            :localization-running="localizationRunning"
            @toggle="toggleGroup"
            @localize="openLocalizeModal([$event])"
            @remove="removeGroup"
          >
            <template #checkbox>
              <input
                v-model="selectedGroupNames"
                :value="group.metadata.name"
                :disabled="isDeletingResource(group)"
                name="comment-next-emote-group-checkbox"
                type="checkbox"
              />
            </template>
          </EmoteGroupListItem>
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
    v-model:visible="defaultSourceVisible"
    title="在线表情源"
    :width="820"
  >
    <div class=":uno: space-y-4">
      <div class=":uno: rounded-lg border border-solid border-gray-200 bg-gray-50 p-3">
        <div class=":uno: flex flex-wrap items-center justify-between gap-2">
          <label class=":uno: text-xs font-medium text-gray-700">
            表情源地址
          </label>
          <div class=":uno: flex flex-wrap items-center gap-1.5">
            <button
              v-for="preset in EMOTE_SOURCE_PRESETS"
              :key="preset.id"
              type="button"
              :title="preset.description"
              :disabled="defaultSourceLoading"
              class=":uno: cursor-pointer rounded-full border border-solid px-2.5 py-0.5 text-xs transition disabled:cursor-not-allowed disabled:opacity-60"
              :class="
                normalizedDefaultSourceUrl === preset.url
                  ? ':uno: border-blue-500 bg-blue-50 font-medium text-blue-700'
                  : ':uno: border-gray-200 bg-white text-gray-600 hover:border-blue-300 hover:text-blue-600'
              "
              @click="selectPresetSourceUrl(preset.url)"
            >
              {{ preset.label }}
            </button>
          </div>
        </div>
        <div class=":uno: mt-2 flex items-center gap-2">
          <input
            v-model="defaultSourceUrl"
            class=":uno: min-w-0 flex-1 rounded-lg border border-solid border-gray-200 bg-white px-3 py-1.5 text-sm text-gray-900 outline-none transition focus:border-blue-500 focus:ring-2 focus:ring-blue-100"
            placeholder="https://owo.twouu.com/OwO.min.json"
            type="url"
          />
          <VButton
            type="default"
            size="sm"
            class=":uno: shrink-0"
            :loading="defaultSourceLoading"
            @click="loadDefaultSource"
          >
            加载
          </VButton>
        </div>
        <div class=":uno: mt-2 text-xs leading-5 text-gray-500">
          支持 OwO JSON 格式；默认优先使用 EdgeOne Pages 国内源，也可一键切换官方 GitHub 源或填入自己的 CDN / 站内文件地址。
        </div>
      </div>

      <div class=":uno: flex flex-wrap items-center justify-between gap-2">
        <span class=":uno: text-sm text-gray-600">
          已加载 {{ defaultPackSummaries.length }} 个分类
          <template v-if="selectedDefaultNames.length">
            · 已选 <strong class=":uno: text-gray-900">{{ selectedDefaultNames.length }}</strong> 个
          </template>
        </span>
        <VSpace>
          <VButton type="default" size="sm" @click="selectAllDefaultPacks">
            全选
          </VButton>
          <VButton type="default" size="sm" @click="selectNewDefaultPacks">
            仅选未导入
          </VButton>
          <VButton type="default" size="sm" @click="clearDefaultSelection">
            清空
          </VButton>
          <VButton type="default" size="sm" :loading="defaultSourceLoading" @click="loadDefaultSource">
            重新加载
          </VButton>
        </VSpace>
      </div>

      <VLoading v-if="defaultSourceLoading" />
      <div v-else class=":uno: grid max-h-[26rem] grid-cols-1 gap-2 overflow-y-auto pr-1 sm:grid-cols-2">
        <label
          v-for="pack in defaultPackSummaries"
          :key="pack.name"
          class=":uno: flex cursor-pointer items-center gap-3 rounded-lg border border-solid border-gray-200 bg-white px-3 py-2 hover:border-blue-200 hover:bg-blue-50/40"
        >
          <input v-model="selectedDefaultNames" type="checkbox" :value="pack.name" />
          <span class=":uno: min-w-0 flex-1">
            <span class=":uno: block truncate text-sm font-medium text-gray-900">{{ pack.name }}</span>
            <span class=":uno: text-xs text-gray-500">
              {{ typeText(pack.type) }} · {{ pack.count }} 个
            </span>
          </span>
          <span
            v-if="importedDisplayNames.has(pack.name)"
            class=":uno: shrink-0 rounded-full bg-blue-50 px-2 py-0.5 text-xs font-medium text-blue-600"
          >
            已导入
          </span>
        </label>
      </div>

      <div
        v-if="selectedDefaultHasImages"
        class=":uno: space-y-2.5 rounded-lg border border-solid border-gray-200 bg-gray-50 p-3"
      >
        <label class=":uno: flex cursor-pointer items-center gap-2 text-sm font-medium text-gray-800">
          <input
            v-model="localizeOnDefaultImport"
            :disabled="importing || localizationRunning"
            type="checkbox"
          />
          <span>导入后自动将图片表情本地化到 Halo 附件库</span>
        </label>
        <EmoteLocalizationFields
          v-if="localizeOnDefaultImport"
          :options="localizationOptions"
          :loading="localizationOptionsLoading"
          :disabled="importing || localizationRunning"
          v-model:policy-name="selectedPolicyName"
          v-model:attachment-group-name="selectedAttachmentGroupName"
          v-model:use-absolute-url="useAbsoluteLocalizedUrl"
          @refresh-options="loadLocalizationOptions(true)"
        />
      </div>
    </div>

    <template #footer>
      <VSpace>
        <VButton type="default" :disabled="importing" @click="defaultSourceVisible = false">
          取消
        </VButton>
        <VButton type="primary" :disabled="importing || localizationStarting" @click="importDefaultGroups">
          {{ importing ? "导入中" : localizeOnDefaultImport && selectedDefaultHasImages ? "导入并本地化" : "导入选中分类" }}
        </VButton>
      </VSpace>
    </template>
  </VModal>

  <VModal
    v-model:visible="customVisible"
    title="导入自定义表情 JSON"
    :width="820"
  >
    <div class=":uno: space-y-4">
      <div
        class=":uno: rounded-lg border border-dashed border-blue-200 bg-blue-50/40 p-4"
      >
        <input
          ref="customFileInput"
          class=":uno: hidden"
          type="file"
          accept=".json,application/json,text/json"
          @change="handleCustomJsonFileChange"
        />
        <div class=":uno: flex flex-col gap-3 sm:flex-row sm:items-center sm:justify-between">
          <div class=":uno: min-w-0">
            <div class=":uno: text-sm font-medium text-gray-900">
              从 JSON 文件导入
            </div>
            <div class=":uno: mt-1 text-xs leading-5 text-gray-500">
              适合较大的 OwO JSON 文件，解析内容不会写入下方文本框。
            </div>
          </div>
          <VButton
            type="secondary"
            :loading="customJsonParsing"
            @click="triggerCustomJsonFileSelect"
          >
            选择 JSON 文件
          </VButton>
        </div>
        <div
          v-if="customJsonFileName"
          class=":uno: mt-3 flex flex-wrap items-center gap-2 text-xs text-blue-600"
        >
          <span class=":uno: min-w-0 truncate">
            已解析：{{ customJsonFileName }}
          </span>
          <span class=":uno: text-blue-300">·</span>
          <span>{{ customPackSummaries.length }} 个分类</span>
        </div>
      </div>

      <textarea
        v-model="customJson"
        class=":uno: h-36 w-full resize-y rounded-lg border border-solid border-gray-200 bg-white p-3 font-mono text-xs outline-none focus:border-blue-500 focus:ring-2 focus:ring-blue-100"
        placeholder='{"颜文字":{"type":"emoticon","container":[{"icon":"OωO","text":"Author"}]}}'
      ></textarea>
      <div class=":uno: flex flex-wrap items-center justify-between gap-2">
        <span class=":uno: text-sm text-gray-500">
          小片段可以粘贴后解析，格式与 OwO JSON 一致。
        </span>
        <VSpace>
          <VButton
            v-if="customPackSummaries.length"
            type="default"
            size="sm"
            @click="selectAllCustomPacks"
          >
            全选
          </VButton>
          <VButton
            v-if="customPackSummaries.length"
            type="default"
            size="sm"
            @click="selectNewCustomPacks"
          >
            仅选未导入
          </VButton>
          <VButton
            v-if="customPackSummaries.length"
            type="default"
            size="sm"
            @click="clearCustomSelection"
          >
            清空
          </VButton>
          <VButton
            v-if="customPackSummaries.length"
            type="default"
            size="sm"
            @click="clearCustomParsedPacks"
          >
            清空解析结果
          </VButton>
          <VButton
            type="default"
            size="sm"
            :disabled="customJsonParsing"
            @click="parseCustomJson"
          >
            解析 JSON
          </VButton>
        </VSpace>
      </div>
      <div
        v-if="customPackSummaries.length"
        class=":uno: grid max-h-72 grid-cols-1 gap-2 overflow-y-auto pr-1 sm:grid-cols-2"
      >
        <label
          v-for="pack in customPackSummaries"
          :key="pack.name"
          class=":uno: flex cursor-pointer items-center gap-3 rounded-lg border border-solid border-gray-200 bg-white px-3 py-2 hover:border-blue-200 hover:bg-blue-50/40"
        >
          <input v-model="selectedCustomNames" type="checkbox" :value="pack.name" />
          <span class=":uno: min-w-0 flex-1">
            <span class=":uno: block truncate text-sm font-medium text-gray-900">{{ pack.name }}</span>
            <span class=":uno: text-xs text-gray-500">
              {{ typeText(pack.type) }} · {{ pack.count }} 个
            </span>
          </span>
          <span
            v-if="importedDisplayNames.has(pack.name)"
            class=":uno: shrink-0 rounded-full bg-blue-50 px-2 py-0.5 text-xs font-medium text-blue-600"
          >
            已导入
          </span>
        </label>
      </div>

      <div
        v-if="selectedCustomHasImages"
        class=":uno: space-y-2.5 rounded-lg border border-solid border-gray-200 bg-gray-50 p-3"
      >
        <label class=":uno: flex cursor-pointer items-center gap-2 text-sm font-medium text-gray-800">
          <input
            v-model="localizeOnCustomImport"
            :disabled="importing || customJsonParsing || localizationRunning"
            type="checkbox"
          />
          <span>导入后自动将图片表情本地化到 Halo 附件库</span>
        </label>
        <EmoteLocalizationFields
          v-if="localizeOnCustomImport"
          :options="localizationOptions"
          :loading="localizationOptionsLoading"
          :disabled="importing || customJsonParsing || localizationRunning"
          v-model:policy-name="selectedPolicyName"
          v-model:attachment-group-name="selectedAttachmentGroupName"
          v-model:use-absolute-url="useAbsoluteLocalizedUrl"
          @refresh-options="loadLocalizationOptions(true)"
        />
      </div>
    </div>

    <template #footer>
      <VSpace>
        <VButton
          type="default"
          :disabled="importing || customJsonParsing"
          @click="customVisible = false"
        >
          取消
        </VButton>
        <VButton
          type="primary"
          :disabled="importing || customJsonParsing || localizationStarting"
          @click="importCustomGroups"
        >
          {{ importing ? "导入中" : localizeOnCustomImport && selectedCustomHasImages ? "导入并本地化" : "导入选中分类" }}
        </VButton>
      </VSpace>
    </template>
  </VModal>

  <EmoteLocalizeModal
    v-model:visible="localizeModalVisible"
    :groups="localizeModalGroups"
    :options="localizationOptions"
    :options-loading="localizationOptionsLoading"
    :submitting="localizationStarting"
    :site-origin="localizationSiteOrigin"
    v-model:policy-name="selectedPolicyName"
    v-model:attachment-group-name="selectedAttachmentGroupName"
    v-model:use-absolute-url="useAbsoluteLocalizedUrl"
    @refresh-options="loadLocalizationOptions(true)"
    @confirm="confirmLocalizeModal"
  />

  <EmoteGroupSortModal
    v-model:visible="sortVisible"
    :groups="sortGroups"
    :saving="sortSaving"
    @save="saveEmoteGroupOrder"
  />
</template>
