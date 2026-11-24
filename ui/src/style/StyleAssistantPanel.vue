<script setup lang="ts">
import { IconMagic, IconSendPlaneFill } from '@halo-dev/components';
import { isAxiosError, isCancel } from 'axios';
import { computed, nextTick, onBeforeUnmount, reactive, ref } from 'vue';
import {
  type CommentNextStyle,
  type StyleAssistantAvailability,
  suggestStyle,
} from './style-api';
import { changedStyleFields } from './style-fields';

const props = defineProps<{
  availability: StyleAssistantAvailability;
  checkingModel: boolean;
  disabled: boolean;
}>();
const draft = defineModel<CommentNextStyle>({ required: true });
const modelName = defineModel<string>('modelName', { required: true });
const emit = defineEmits<{ busy: [value: boolean] }>();

const prompts = [
  {
    label: '墨绿紧凑',
    text: '换成墨绿主色，深色模式用明亮一点的松绿。评论间距紧凑，圆角 12 像素。',
  },
  {
    label: '暖棕留白',
    text: '做成暖棕色，浅色和深色搭配适合各自背景的颜色。列表改用卡片，间距留宽一点。',
  },
  { label: '回到主题风格', text: '把配色、圆角、间距和列表样式都恢复为默认。' },
];
type Turn = {
  instruction: string;
  summary?: string;
  changes?: string[];
  error?: string;
};

const turns = ref<Turn[]>([]);
const instruction = ref('');
const log = ref<HTMLElement>();
const textbox = ref<HTMLTextAreaElement>();
const generating = ref(false);
const composerLocked = computed(
  () => !props.availability.canChooseModel || props.disabled || generating.value
);
const ready = computed(
  () =>
    props.availability.available &&
    !props.checkingModel &&
    !composerLocked.value
);
let request: AbortController | undefined;

function pick(text: string) {
  instruction.value = text;
  textbox.value?.focus();
}

async function send() {
  const text = instruction.value.trim();
  if (!text || !ready.value) return;
  const selectedModel = modelName.value;
  const initial = { ...draft.value };
  const controller = new AbortController();
  request = controller;
  const turn = reactive<Turn>({ instruction: text });
  turns.value.push(turn);
  instruction.value = '';
  generating.value = true;
  emit('busy', true);
  await scrollToLatest();
  try {
    const result = await suggestStyle(
      initial,
      text,
      selectedModel,
      controller.signal
    );
    if (controller.signal.aborted) return;
    const changes = changedStyleFields(initial, result.draft);
    draft.value = result.draft;
    turn.summary = changes.length
      ? result.summary || '已更新样式草稿。'
      : '草稿已经符合这个要求。';
    turn.changes = changes.map((change) => change.label);
  } catch (error) {
    if (controller.signal.aborted || isCancel(error)) return;
    const detail = isAxiosError<{ detail?: string }>(error)
      ? error.response?.data?.detail
      : undefined;
    turn.error = detail || '生成样式失败，请稍后重试。';
    instruction.value = text;
  } finally {
    if (!controller.signal.aborted) {
      request = undefined;
      generating.value = false;
      emit('busy', false);
      await scrollToLatest();
    }
  }
}

async function scrollToLatest() {
  await nextTick();
  log.value?.scrollTo({ top: log.value.scrollHeight });
}

onBeforeUnmount(() => request?.abort());
</script>

<template>
  <div class="cn-style-assistant">
    <header class="cn-style-panel-heading">
      <strong><IconMagic aria-hidden="true" /> 样式助手</strong>
      <span v-if="checkingModel" class="cn-style-muted" role="status">正在检查模型…</span>
      <span v-else-if="availability.available" class="cn-style-ready">AI Foundation</span>
    </header>
    <div ref="log" class="cn-style-chat" role="log" aria-label="样式对话">
      <div v-if="!turns.length" class="cn-style-welcome">
        <IconMagic aria-hidden="true" />
        <h2>想怎么改评论区？</h2>
        <p>用一句话描述配色、圆角、间距或列表样式。</p>
        <div class="cn-style-prompts">
          <button
            v-for="example in prompts"
            :key="example.label"
            type="button"
            class="cn-style-button"
            :disabled="composerLocked"
            @click="pick(example.text)"
          >{{ example.label }}</button>
        </div>
      </div>
      <div v-for="(turn, index) in turns" :key="index" class="cn-style-turn">
        <p class="cn-style-question">{{ turn.instruction }}</p>
        <p v-if="turn.error" class="cn-style-error">{{ turn.error }}</p>
        <template v-else-if="turn.summary">
          <p>{{ turn.summary }}</p>
          <ul v-if="turn.changes?.length" class="cn-style-tags" aria-label="本轮改动">
            <li v-for="label in turn.changes" :key="label">{{ label }}</li>
          </ul>
        </template>
        <p v-else class="cn-style-muted">正在调整草稿…</p>
      </div>
    </div>
    <div class="cn-style-input-area">
      <p v-if="!checkingModel && !availability.available" class="cn-style-notice" role="status">
        {{ availability.reason }} 也可以在「外观」里手动调整。
      </p>
      <form class="cn-style-prompt" @submit.prevent="send">
        <textarea
          ref="textbox"
          v-model="instruction"
          aria-label="描述样式修改"
          rows="3"
          maxlength="1200"
          placeholder="例如 把评论列表改成卡片，间距宽一点…"
          :disabled="composerLocked"
          @keydown.ctrl.enter.prevent="send"
          @keydown.meta.enter.prevent="send"
        />
        <div class="cn-style-prompt-actions">
          <div v-if="availability.canChooseModel" class="cn-style-model-picker" role="group" aria-label="生成模型"
            title="选择样式工作台使用的语言模型，清空后使用默认模型。">
            <FormKit
              type="aiModelSelector"
              name="comment-next-style-model"
              model-type="language"
              :available="true"
              :clearable="true"
              :delay="0"
              :model-value="modelName || undefined"
              :disabled="composerLocked"
              placeholder="默认模型"
              @update:model-value="modelName = typeof $event === 'string' ? $event : ''"
            />
          </div>
          <button type="submit" class="cn-style-button is-primary" :disabled="!ready || !instruction.trim()">
            <IconSendPlaneFill aria-hidden="true" /> 发送
          </button>
        </div>
      </form>
    </div>
  </div>
</template>
