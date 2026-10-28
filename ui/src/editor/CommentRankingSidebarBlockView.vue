<script lang="ts" setup>
import '@xhhao/comment-next';
import '@xhhao/comment-next/comment-next.css';
import { type NodeViewProps, NodeViewWrapper } from '@halo-dev/richtext-editor';
import { computed } from 'vue';
import CommentRankingColorFields from './CommentRankingColorFields.vue';
import {
  type CommentRankingSidebarBlockAttrs,
  DEFAULT_COMMENT_RANKING_SIDEBAR_ATTRS,
  normalizeRankingColor,
  normalizeRankingSidebarLimit,
  normalizeRankingSidebarPeriod,
} from './comment-ranking-config';

const props = defineProps<NodeViewProps>();
const attrs = computed<CommentRankingSidebarBlockAttrs>(() => {
  const value = props.node.attrs;
  return {
    period: normalizeRankingSidebarPeriod(value.period),
    limit: normalizeRankingSidebarLimit(value.limit),
    title: String(value.title || DEFAULT_COMMENT_RANKING_SIDEBAR_ATTRS.title),
    rankingUrl: String(value.rankingUrl || ''),
    linkText: String(
      value.linkText ?? DEFAULT_COMMENT_RANKING_SIDEBAR_ATTRS.linkText
    ),
    showHeader: value.showHeader !== false,
    accentColor: normalizeRankingColor(
      value.accentColor,
      DEFAULT_COMMENT_RANKING_SIDEBAR_ATTRS.accentColor
    ),
    surfaceColor: normalizeRankingColor(value.surfaceColor, ''),
    textColor: normalizeRankingColor(value.textColor, ''),
  };
});

const periodOptions = [
  { value: 'month', label: '本月' },
  { value: 'year', label: '本年' },
];

function updateText(key: 'title' | 'rankingUrl' | 'linkText', event: Event) {
  const value = (event.target as HTMLInputElement).value.trim();
  const fallback = DEFAULT_COMMENT_RANKING_SIDEBAR_ATTRS[key];
  props.updateAttributes({
    [key]: key === 'title' ? value || fallback : value,
  });
}

function updatePeriod(event: Event) {
  props.updateAttributes({
    period: normalizeRankingSidebarPeriod(
      (event.target as HTMLSelectElement).value
    ),
  });
}

function updateLimit(event: Event) {
  props.updateAttributes({
    limit: normalizeRankingSidebarLimit(
      (event.target as HTMLInputElement).value
    ),
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
    :class="['comment-ranking-sidebar-block', { 'comment-ranking-sidebar-block--selected': selected }]"
    contenteditable="false"
  >
    <div class="comment-ranking-sidebar-block__label">评论读者榜 · 侧边栏</div>
    <div class="comment-ranking-sidebar-block__preview">
      <comment-ranking-sidebar
        :period="attrs.period"
        :limit="attrs.limit"
        :title="attrs.title"
        :ranking-url="attrs.rankingUrl"
        :link-text="attrs.linkText"
        :show-header="String(attrs.showHeader)"
        :accent-color="attrs.accentColor"
        :surface-color="attrs.surfaceColor"
        :text-color="attrs.textColor"
      ></comment-ranking-sidebar>
    </div>

    <details class="comment-ranking-sidebar-block__settings" @mousedown.stop>
      <summary>显示与链接</summary>
      <div class="comment-ranking-sidebar-block__fields">
        <label>
          <span>统计周期</span>
          <select :value="attrs.period" @change="updatePeriod">
            <option v-for="option in periodOptions" :key="option.value" :value="option.value">{{ option.label }}</option>
          </select>
        </label>
        <label>
          <span>显示人数</span>
          <input :value="attrs.limit" type="number" min="1" max="10" @change="updateLimit" />
        </label>
        <label class="comment-ranking-sidebar-block__wide">
          <span>完整榜单地址</span>
          <input :value="attrs.rankingUrl" type="text" inputmode="url" placeholder="可选，例如 /readers" @change="updateText('rankingUrl', $event)" />
        </label>
        <label class="comment-ranking-sidebar-block__wide">
          <span>按钮文字</span>
          <input :value="attrs.linkText" type="text" maxlength="30" @change="updateText('linkText', $event)" />
        </label>
        <label class="comment-ranking-sidebar-block__wide">
          <span>标题</span>
          <input :value="attrs.title" type="text" maxlength="40" @change="updateText('title', $event)" />
        </label>
        <label class="comment-ranking-sidebar-block__wide comment-ranking-sidebar-block__toggle">
          <input :checked="attrs.showHeader" type="checkbox" @change="updateShowHeader" />
          <span>显示标题</span>
        </label>
      </div>
      <p class="comment-ranking-sidebar-block__hint">填写完整榜单地址后显示按钮。</p>
      <CommentRankingColorFields :colors="attrs" @change="updateAttributes" />
    </details>
  </node-view-wrapper>
</template>

<style scoped>
.comment-ranking-sidebar-block {
  margin: 0.75rem 0;
  overflow: hidden;
  border: 1px solid #e2e8f0;
  border-radius: 12px;
  background: #fff;
}

.comment-ranking-sidebar-block--selected {
  border-color: #94a3b8;
  box-shadow: 0 0 0 2px rgb(148 163 184 / 15%);
}

.comment-ranking-sidebar-block__label {
  padding: 0.6rem 1rem;
  border-bottom: 1px solid #f1f5f9;
  color: #64748b;
  font-size: 0.7rem;
  font-weight: 600;
}

.comment-ranking-sidebar-block__preview {
  max-width: 24rem;
  padding: 0.75rem;
}

.comment-ranking-sidebar-block__settings {
  padding: 0 1rem;
  border-top: 1px solid #f1f5f9;
  background: #f8fafc;
  color: #475569;
  font-size: 0.8rem;
}

.comment-ranking-sidebar-block__settings summary {
  padding: 0.85rem 0;
  cursor: pointer;
  font-weight: 600;
}

.comment-ranking-sidebar-block__fields {
  display: grid;
  grid-template-columns: repeat(2, minmax(0, 1fr));
  gap: 0.75rem;
  padding-bottom: 1rem;
}

.comment-ranking-sidebar-block__fields label {
  display: grid;
  min-width: 0;
  gap: 0.35rem;
}

.comment-ranking-sidebar-block__wide {
  grid-column: 1 / -1;
}

.comment-ranking-sidebar-block__fields input,
.comment-ranking-sidebar-block__fields select {
  min-width: 0;
  width: 100%;
  border: 1px solid #dbe3eb;
  border-radius: 6px;
  padding: 0.42rem 0.55rem;
  background: #fff;
  color: #334155;
  font: inherit;
}

label.comment-ranking-sidebar-block__toggle {
  display: flex;
  align-items: center;
  gap: 0.5rem;
}

.comment-ranking-sidebar-block__toggle input {
  width: auto;
}

.comment-ranking-sidebar-block__hint {
  margin: 0 0 0.75rem;
  color: #64748b;
  font-size: 0.7rem;
}

input:focus-visible,
select:focus-visible,
summary:focus-visible {
  outline: 2px solid #64748b;
  outline-offset: 2px;
}
</style>
