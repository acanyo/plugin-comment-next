<script lang="ts" setup>
import '@xhhao/comment-next';
import '@xhhao/comment-next/comment-next.css';
import { type NodeViewProps, NodeViewWrapper } from '@halo-dev/richtext-editor';
import { computed } from 'vue';
import CommentRankingColorFields from './CommentRankingColorFields.vue';
import {
  type CommentRankingBlockAttrs,
  DEFAULT_COMMENT_RANKING_ATTRS,
  normalizeRankingColor,
  normalizeRankingLimit,
  normalizeRankingPeriod,
} from './comment-ranking-config';

const props = defineProps<NodeViewProps>();
const periodOptions = [
  { value: 'all', label: '全部时间' },
  { value: 'week', label: '本周' },
  { value: 'month', label: '本月' },
  { value: 'quarter', label: '本季度' },
  { value: 'year', label: '今年' },
];

const attrs = computed<CommentRankingBlockAttrs>(() => {
  const value = props.node.attrs;
  return {
    period: normalizeRankingPeriod(value.period),
    limit: normalizeRankingLimit(value.limit),
    title: String(value.title || DEFAULT_COMMENT_RANKING_ATTRS.title),
    subtitle: String(value.subtitle ?? DEFAULT_COMMENT_RANKING_ATTRS.subtitle),
    showHeader: value.showHeader !== false,
    accentColor: normalizeRankingColor(
      value.accentColor,
      DEFAULT_COMMENT_RANKING_ATTRS.accentColor
    ),
    surfaceColor: normalizeRankingColor(value.surfaceColor, ''),
    textColor: normalizeRankingColor(value.textColor, ''),
  };
});

function updateText(key: 'title' | 'subtitle', event: Event) {
  const value = (event.target as HTMLInputElement).value.trim();
  props.updateAttributes({
    [key]:
      key === 'subtitle' ? value : value || DEFAULT_COMMENT_RANKING_ATTRS[key],
  });
}

function updatePeriod(event: Event) {
  props.updateAttributes({
    period: normalizeRankingPeriod((event.target as HTMLSelectElement).value),
  });
}

function updateLimit(event: Event) {
  props.updateAttributes({
    limit: normalizeRankingLimit((event.target as HTMLInputElement).value),
  });
}

function updateShowHeader(event: Event) {
  props.updateAttributes({
    showHeader: (event.target as HTMLInputElement).checked,
  });
}
</script>

<template>
  <node-view-wrapper
    as="div"
    :class="['comment-ranking-block', { 'comment-ranking-block--selected': selected }]"
    contenteditable="false"
  >
    <div class="comment-ranking-block__label">评论读者榜 · 页面</div>
    <div class="comment-ranking-block__preview">
      <comment-ranking
        :period="attrs.period"
        :limit="attrs.limit"
        :title="attrs.title"
        :subtitle="attrs.subtitle"
        :show-header="String(attrs.showHeader)"
        :accent-color="attrs.accentColor"
        :surface-color="attrs.surfaceColor"
        :text-color="attrs.textColor"
      ></comment-ranking>
    </div>

    <details class="comment-ranking-block__settings" @mousedown.stop>
      <summary>显示与配色</summary>
      <div class="comment-ranking-block__fields">
        <label class="comment-ranking-block__field">
          <span>统计周期</span>
          <select :value="attrs.period" @change="updatePeriod">
            <option v-for="option in periodOptions" :key="option.value" :value="option.value">
              {{ option.label }}
            </option>
          </select>
        </label>
        <label class="comment-ranking-block__field">
          <span>显示人数</span>
          <input :value="attrs.limit" type="number" min="1" max="50" @change="updateLimit" />
        </label>
        <label class="comment-ranking-block__field comment-ranking-block__field--wide">
          <span>标题</span>
          <input :value="attrs.title" type="text" maxlength="40" @change="updateText('title', $event)" />
        </label>
        <label class="comment-ranking-block__field comment-ranking-block__field--wide">
          <span>副标题</span>
          <input :value="attrs.subtitle" type="text" maxlength="80" @change="updateText('subtitle', $event)" />
        </label>
        <label class="comment-ranking-block__toggle">
          <input :checked="attrs.showHeader" type="checkbox" @change="updateShowHeader" />
          <span>显示标题和副标题</span>
        </label>
      </div>

      <CommentRankingColorFields :colors="attrs" @change="updateAttributes" />
    </details>
  </node-view-wrapper>
</template>

<style scoped>
.comment-ranking-block {
  margin: 0.75rem 0;
  overflow: hidden;
  border: 1px solid #e2e8f0;
  border-radius: 12px;
  background: #fff;
}

.comment-ranking-block--selected {
  border-color: #94a3b8;
  box-shadow: 0 0 0 2px rgb(148 163 184 / 15%);
}

.comment-ranking-block__label {
  padding: 0.6rem 1rem;
  border-bottom: 1px solid #f1f5f9;
  color: #64748b;
  font-size: 0.7rem;
  font-weight: 600;
}

.comment-ranking-block__preview {
  padding: 0.5rem;
}

.comment-ranking-block__settings {
  padding: 0 1rem;
  border-top: 1px solid #f1f5f9;
  background: #f8fafc;
  color: #475569;
  font-size: 0.8rem;
}

.comment-ranking-block__settings summary {
  padding: 0.85rem 0;
  cursor: pointer;
  font-weight: 600;
}

.comment-ranking-block__fields {
  display: grid;
  grid-template-columns: repeat(2, minmax(0, 1fr));
  gap: 0.85rem;
  padding: 0.25rem 0 1rem;
}

.comment-ranking-block__field {
  display: grid;
  min-width: 0;
  gap: 0.4rem;
}

.comment-ranking-block__field--wide {
  grid-column: 1 / -1;
}

.comment-ranking-block__field input,
.comment-ranking-block__field select {
  min-width: 0;
  width: 100%;
  border: 1px solid #dbe3eb;
  border-radius: 6px;
  padding: 0.45rem 0.6rem;
  background: #fff;
  color: #334155;
  font: inherit;
}

.comment-ranking-block__toggle {
  display: flex;
  grid-column: 1 / -1;
  align-items: center;
  gap: 0.5rem;
}


input:focus-visible,
select:focus-visible,
button:focus-visible,
summary:focus-visible {
  outline: 2px solid #64748b;
  outline-offset: 2px;
}

</style>
