<script setup lang="ts">
import type { CommentNextStyle } from './style-api';
import { densityOptions, listStyleOptions } from './style-fields';

defineProps<{ disabled: boolean }>();
const draft = defineModel<CommentNextStyle>({ required: true });
const colors = [
  { key: 'lightAccent', label: '浅色主色', initial: '#2563eb' },
  { key: 'darkAccent', label: '深色主色', initial: '#93c5fd' },
] as const;

function setField<Key extends keyof CommentNextStyle>(
  field: Key,
  value: CommentNextStyle[Key]
) {
  draft.value = { ...draft.value, [field]: value };
}

function fieldValue(event: Event): string {
  return (event.target as HTMLInputElement).value;
}
</script>

<template>
  <fieldset class="cn-style-settings" :disabled="disabled">
    <legend class="cn-style-panel-heading">外观</legend>
    <div class="cn-style-setting">
      <div class="cn-style-setting-title">主色</div>
      <div v-for="color in colors" :key="color.key" class="cn-style-color">
        <label :for="`cn-style-${color.key}`">{{ color.label }}</label>
        <input
          :id="`cn-style-${color.key}`"
          type="color"
          :value="draft[color.key] ?? color.initial"
          @input="setField(color.key, fieldValue($event))"
        />
        <code>{{ draft[color.key] ?? '跟随主题' }}</code>
        <button
          type="button"
          class="cn-style-text-button"
          :aria-label="`${color.label}跟随主题`"
          :disabled="!draft[color.key]"
          @click="setField(color.key, null)"
        >重置</button>
      </div>
    </div>
    <div class="cn-style-setting">
      <div class="cn-style-setting-row">
        <label for="cn-style-radius">圆角</label>
        <output for="cn-style-radius">{{ draft.radius == null ? '跟随主题' : `${draft.radius} px` }}</output>
      </div>
      <input
        id="cn-style-radius"
        type="range"
        min="0"
        max="24"
        step="1"
        :value="draft.radius ?? 14"
        @input="setField('radius', Number(fieldValue($event)))"
      />
      <button type="button" class="cn-style-text-button" :disabled="draft.radius == null" @click="setField('radius', null)">
        恢复主题圆角
      </button>
    </div>
    <div class="cn-style-setting">
      <div class="cn-style-setting-title">评论间距</div>
      <div class="cn-style-segment" role="group" aria-label="评论间距">
        <button v-for="option in densityOptions" :key="option.label" type="button"
          :aria-pressed="(draft.density ?? null) === option.value" @click="setField('density', option.value)">
          {{ option.label }}
        </button>
      </div>
    </div>
    <div class="cn-style-setting">
      <div class="cn-style-setting-title">列表样式</div>
      <div class="cn-style-segment" role="group" aria-label="列表样式">
        <button v-for="option in listStyleOptions" :key="option.value" type="button"
          :aria-pressed="(draft.listStyle ?? 'flat') === option.value" @click="setField('listStyle', option.value)">
          {{ option.label }}
        </button>
      </div>
    </div>
  </fieldset>
</template>
