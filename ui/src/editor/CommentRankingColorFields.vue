<script setup lang="ts">
import { normalizeRankingColor } from './comment-ranking-config';

interface RankingColors {
  accentColor: string;
  surfaceColor: string;
  textColor: string;
}

defineProps<{ colors: RankingColors }>();
const emit = defineEmits<{ change: [value: Partial<RankingColors>] }>();
const options = [
  { key: 'accentColor', label: '主色', fallback: '#31536f' },
  { key: 'surfaceColor', label: '背景', fallback: '#ffffff' },
  { key: 'textColor', label: '文字', fallback: '#172033' },
] as const;

function changeColor(key: keyof RankingColors, event: Event) {
  emit('change', {
    [key]: normalizeRankingColor((event.target as HTMLInputElement).value, ''),
  });
}
</script>

<template>
  <div class="ranking-colors">
    <label v-for="option in options" :key="option.key">
      <span>{{ option.label }}</span>
      <input
        :value="colors[option.key] || option.fallback"
        :aria-label="`${option.label}颜色`"
        type="color"
        @input="changeColor(option.key, $event)"
      />
      <input
        :value="colors[option.key]"
        :aria-label="`${option.label}色值`"
        type="text"
        placeholder="跟随主题"
        maxlength="9"
        @change="changeColor(option.key, $event)"
      />
    </label>
  </div>
  <div class="ranking-colors__footer">
    <span>配色会保存到内容；留空时跟随主题。</span>
    <button type="button" @click="emit('change', { accentColor: '', surfaceColor: '', textColor: '' })">
      恢复默认配色
    </button>
  </div>
</template>

<style scoped>
.ranking-colors {
  display: grid;
  grid-template-columns: repeat(3, minmax(0, 1fr));
  gap: 0.75rem;
}

label {
  display: grid;
  grid-template-columns: 1.8rem minmax(0, 1fr);
  align-items: center;
  gap: 0.4rem;
}

label span {
  grid-column: 1 / -1;
}

input[type='color'] {
  width: 1.8rem;
  height: 1.8rem;
  border: 1px solid #dbe3eb;
  border-radius: 6px;
  padding: 0.15rem;
  background: #fff;
  cursor: pointer;
}

input[type='text'] {
  min-width: 0;
  width: 100%;
  border: 1px solid #dbe3eb;
  border-radius: 6px;
  padding: 0.45rem 0.6rem;
  background: #fff;
  color: #334155;
  font: inherit;
}

.ranking-colors__footer {
  display: flex;
  align-items: center;
  justify-content: space-between;
  flex-wrap: wrap;
  gap: 0.5rem;
  padding: 0.9rem 0 1rem;
  color: #64748b;
  font-size: 0.7rem;
}

button {
  border: 0;
  padding: 0.2rem 0;
  background: transparent;
  color: #475569;
  cursor: pointer;
  font: inherit;
  text-decoration: underline;
  text-underline-offset: 0.2rem;
}

input:focus-visible,
button:focus-visible {
  outline: 2px solid #64748b;
  outline-offset: 2px;
}

@media (max-width: 640px) {
  .ranking-colors {
    grid-template-columns: 1fr;
  }
}
</style>
