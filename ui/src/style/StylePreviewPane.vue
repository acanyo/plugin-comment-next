<script setup lang="ts">
import { IconComputer, IconPhone } from '@halo-dev/components';
import { computed, ref } from 'vue';
import '@xhhao/comment-next';
import '@xhhao/comment-next/comment-next.css';
import type { CommentNextStyle } from './style-api';

const props = defineProps<{
  draft: CommentNextStyle;
  published: CommentNextStyle;
}>();
const version = ref<'draft' | 'published'>('draft');
const scheme = ref<'light' | 'dark'>('light');
const device = ref<'desktop' | 'mobile'>('desktop');
const style = computed(() => props[version.value]);

defineExpose({
  showDraft: () => {
    version.value = 'draft';
  },
});
</script>

<template>
  <section class="cn-style-preview" aria-label="实时预览">
    <header class="cn-style-preview-bar">
      <div class="cn-style-segment" role="group" aria-label="预览版本">
        <button type="button" :aria-pressed="version === 'draft'" @click="version = 'draft'">草稿</button>
        <button type="button" :aria-pressed="version === 'published'" @click="version = 'published'">已发布</button>
      </div>
      <div class="cn-style-preview-tools">
        <div class="cn-style-segment" role="group" aria-label="预览设备">
          <button type="button" aria-label="桌面预览" :aria-pressed="device === 'desktop'" @click="device = 'desktop'">
            <IconComputer aria-hidden="true" />
          </button>
          <button type="button" aria-label="手机预览" :aria-pressed="device === 'mobile'" @click="device = 'mobile'">
            <IconPhone aria-hidden="true" />
          </button>
        </div>
        <div class="cn-style-segment" role="group" aria-label="预览颜色模式">
          <button type="button" :aria-pressed="scheme === 'light'" @click="scheme = 'light'">浅色</button>
          <button type="button" :aria-pressed="scheme === 'dark'" @click="scheme = 'dark'">深色</button>
        </div>
      </div>
    </header>
    <div class="cn-style-stage" :data-device="device">
      <div class="cn-style-preview-page" :data-color-scheme="scheme">
        <div class="cn-style-preview-title">
          <strong>文章评论区</strong>
          <span>示例内容</span>
        </div>
        <comment-next inert demo-data placeholder="写下你的评论…" :previewStyle.prop="style" />
      </div>
    </div>
  </section>
</template>
