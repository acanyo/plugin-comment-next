<script setup lang="ts">
import {
  IconArrowLeft,
  IconHistoryLine,
  IconMagic,
  IconPalette,
  VLoading,
} from '@halo-dev/components';
import { isAxiosError } from 'axios';
import { computed, onBeforeUnmount, onMounted, ref, watch } from 'vue';
import StyleAppearancePanel from './StyleAppearancePanel.vue';
import StyleAssistantPanel from './StyleAssistantPanel.vue';
import StylePreviewPane from './StylePreviewPane.vue';
import {
  type CommentNextStyle,
  getStyleWorkbench,
  type StyleAssistantAvailability,
  type StyleOperation,
  type StyleState,
  saveStyleState,
} from './style-api';
import { changedStyleFields } from './style-fields';
import './style-workbench.css';

const MODEL_PREFERENCE_KEY = 'comment-next:style-model';
const state = ref<StyleState>();
const draft = ref<CommentNextStyle>({});
const availability = ref<StyleAssistantAvailability>({
  available: false,
  canChooseModel: false,
});
const modelName = ref(readModelName());
const checkingModel = ref(false);
const preview = ref<InstanceType<typeof StylePreviewPane>>();
const panel = ref<'ai' | 'appearance'>('ai');
const loading = ref(true);
const saving = ref<StyleOperation>();
const generating = ref(false);
const errorMessage = ref('');
const notice = ref('');
const conflict = ref(false);

const locked = computed(() =>
  Boolean(loading.value || saving.value || generating.value)
);
const dirty = computed(() =>
  Boolean(
    state.value &&
      changedStyleFields(state.value.design.draft, draft.value).length
  )
);
const changes = computed(() =>
  state.value
    ? changedStyleFields(state.value.design.published, draft.value)
    : []
);

async function load() {
  loading.value = true;
  errorMessage.value = '';
  try {
    const result = await getStyleWorkbench(modelName.value);
    state.value = result.state;
    availability.value = result.assistant;
    draft.value = { ...result.state.design.draft };
    conflict.value = false;
    notice.value = '';
  } catch {
    errorMessage.value = '读取样式失败，请重试。';
  } finally {
    loading.value = false;
  }
}

function readModelName() {
  try {
    return window.localStorage.getItem(MODEL_PREFERENCE_KEY) ?? '';
  } catch {
    return '';
  }
}

watch(modelName, async (name, _, onCleanup) => {
  try {
    if (name) window.localStorage.setItem(MODEL_PREFERENCE_KEY, name);
    else window.localStorage.removeItem(MODEL_PREFERENCE_KEY);
  } catch {
    // Selecting a model still works if the browser blocks local storage.
  }

  const request = new AbortController();
  onCleanup(() => request.abort());
  checkingModel.value = true;
  try {
    const result = await getStyleWorkbench(name, request.signal);
    if (!request.signal.aborted) availability.value = result.assistant;
  } catch {
    if (request.signal.aborted) return;
    availability.value = {
      ...availability.value,
      available: false,
      reason: '暂时无法检查模型，请重新选择或刷新工作台。',
    };
  } finally {
    if (!request.signal.aborted) checkingModel.value = false;
  }
});

function edit(next: CommentNextStyle) {
  draft.value = next;
  notice.value = '';
  preview.value?.showDraft();
}

async function save(operation: StyleOperation) {
  if (!state.value || locked.value || conflict.value) return;
  saving.value = operation;
  errorMessage.value = '';
  notice.value = '';
  try {
    state.value = await saveStyleState(
      state.value.revision,
      operation,
      draft.value
    );
    if (operation !== 'rollback') draft.value = { ...state.value.design.draft };
    notice.value = {
      save: '草稿已保存',
      publish: '已发布到评论区',
      rollback: '已恢复上一版',
    }[operation];
  } catch (error) {
    conflict.value = isAxiosError(error) && error.response?.status === 409;
    errorMessage.value = conflict.value
      ? '样式已在其他窗口更新。重新读取后可继续编辑。'
      : '保存样式失败，请重试。';
  } finally {
    saving.value = undefined;
  }
}

function confirmUnsaved(event: BeforeUnloadEvent) {
  if (dirty.value) event.preventDefault();
}

onMounted(() => {
  void load();
  window.addEventListener('beforeunload', confirmUnsaved);
});
onBeforeUnmount(() =>
  window.removeEventListener('beforeunload', confirmUnsaved)
);
</script>

<template>
  <div class="cn-style-workbench">
    <header class="cn-style-header">
      <RouterLink :to="{ name: 'Comments' }" class="cn-style-text-button cn-style-back" aria-label="返回评论管理">
        <IconArrowLeft aria-hidden="true" /><span>评论管理</span>
      </RouterLink>
      <h1>评论样式工作台</h1>
      <div v-if="state" class="cn-style-header-actions">
        <span class="cn-style-revision" role="status">
          {{ notice || (dirty ? '草稿未保存' : changes.length ? '草稿未发布' : '已应用') }}
        </span>
        <button v-if="state.design.previous" type="button" class="cn-style-button"
          :disabled="locked || conflict" @click="save('rollback')">
          <IconHistoryLine aria-hidden="true" />恢复上一版
        </button>
        <button type="button" class="cn-style-button" :disabled="!dirty || locked || conflict" @click="save('save')">
          {{ saving === 'save' ? '保存中…' : '保存草稿' }}
        </button>
        <button type="button" class="cn-style-button is-primary" :disabled="!changes.length || locked || conflict" @click="save('publish')">
          {{ saving === 'publish' ? '发布中…' : '发布样式' }}
        </button>
      </div>
    </header>

    <VLoading v-if="loading" />
    <div v-if="errorMessage" class="cn-style-page-error">
      <span role="alert">{{ errorMessage }}</span>
      <button v-if="conflict || !state" type="button" class="cn-style-button" :disabled="loading" @click="load">重新读取</button>
    </div>

    <div v-if="state" class="cn-style-body">
      <aside class="cn-style-sidebar" aria-label="样式编辑">
        <nav class="cn-style-rail" aria-label="样式编辑方式">
          <button type="button" :aria-pressed="panel === 'ai'" @click="panel = 'ai'">
            <IconMagic aria-hidden="true" /><span>AI</span>
          </button>
          <button type="button" :aria-pressed="panel === 'appearance'" @click="panel = 'appearance'">
            <IconPalette aria-hidden="true" /><span>外观</span>
          </button>
        </nav>
        <div class="cn-style-panel">
          <StyleAssistantPanel v-show="panel === 'ai'" :model-value="draft"
            v-model:model-name="modelName" :availability="availability" :checking-model="checkingModel"
            :disabled="loading || Boolean(saving)"
            @update:model-value="edit" @busy="generating = $event" />
          <StyleAppearancePanel v-if="panel === 'appearance'" :model-value="draft" :disabled="locked"
            @update:model-value="edit" />
          <section v-if="changes.length" class="cn-style-changes" aria-label="相对已发布的改动">
            <h2>草稿改动</h2>
            <dl>
              <div v-for="change in changes" :key="change.key">
                <dt>{{ change.label }}</dt>
                <dd>{{ change.before }} → {{ change.after }}</dd>
              </div>
            </dl>
          </section>
        </div>
      </aside>
      <StylePreviewPane ref="preview" :draft="draft" :published="state.design.published" />
    </div>
  </div>
</template>
