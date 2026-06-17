<script lang="ts" setup>
import { VButton, VModal, VSpace } from '@halo-dev/components';
import { Icon } from '@iconify/vue';
import { computed, ref, watch } from 'vue';
import { VueDraggable } from 'vue-draggable-plus';
import {
  type EmoteGroup,
  emoteItemPreviewUrl,
  sortEmoteGroupsByPriority,
} from '../api/emotes';

const props = withDefaults(
  defineProps<{
    visible: boolean;
    groups: EmoteGroup[];
    saving?: boolean;
  }>(),
  {
    saving: false,
  }
);

const emit = defineEmits<{
  'update:visible': [visible: boolean];
  save: [groups: EmoteGroup[]];
}>();

const visibleProxy = computed({
  get: () => props.visible,
  set: (value: boolean) => emit('update:visible', value),
});

const sortedGroups = ref<EmoteGroup[]>([]);

watch(
  () => [props.visible, props.groups] as const,
  ([visible]) => {
    if (visible) {
      sortedGroups.value = sortEmoteGroupsByPriority(props.groups).map((group) => ({
        ...group,
        metadata: { ...group.metadata },
        spec: {
          ...group.spec,
          items: [...group.spec.items],
        },
      }));
    }
  },
  { immediate: true }
);

function close() {
  emit('update:visible', false);
}

function save() {
  emit('save', sortedGroups.value);
}

function sourceText(type: string) {
  return type === 'DEFAULT' ? '默认源' : '自定义';
}

function typeText(type: string) {
  return type === 'image' ? '图片表情' : '颜文字';
}
</script>

<template>
  <VModal v-model:visible="visibleProxy" title="手动排序" :width="760">
    <div class=":uno: space-y-4">
      <div class=":uno: rounded-lg bg-gray-50 px-3 py-2 text-xs text-gray-500">
        拖动表情分类调整前台表情面板的展示顺序，保存后立即生效。
      </div>

      <VueDraggable
        v-model="sortedGroups"
        handle=".comment-next-console-emote-sort-handle"
        class=":uno: grid max-h-[28rem] grid-cols-1 gap-2 overflow-y-auto pr-1"
      >
        <div
          v-for="group in sortedGroups"
          :key="group.metadata.name"
          class=":uno: flex items-center gap-3 rounded-lg border border-solid border-gray-200 bg-white px-3 py-2 transition-colors hover:border-blue-200 hover:bg-blue-50/30"
        >
          <button
            class="comment-next-console-emote-sort-handle :uno: flex h-8 w-8 shrink-0 cursor-grab items-center justify-center rounded-md border border-solid border-gray-200 bg-gray-50 text-gray-400 transition-colors hover:border-blue-200 hover:bg-blue-50 hover:text-blue-500 active:cursor-grabbing"
            type="button"
            aria-label="拖动排序"
          >
            <Icon icon="ri:drag-move-2-line" class=":uno: h-4 w-4" />
          </button>

          <div class=":uno: min-w-0 flex-1">
            <div class=":uno: truncate text-sm font-semibold text-gray-900">
              {{ group.spec.displayName || "未命名表情" }}
            </div>
            <div class=":uno: mt-0.5 flex min-w-0 items-center gap-1.5 text-xs text-gray-500">
              <span>{{ sourceText(group.spec.sourceType) }}</span>
              <span class=":uno: text-gray-300">·</span>
              <span>{{ typeText(group.spec.type) }}</span>
              <span class=":uno: text-gray-300">·</span>
              <span class=":uno: tabular-nums">{{ group.spec.items.length }} 个</span>
            </div>
          </div>

          <div class=":uno: hidden min-w-0 items-center gap-1 sm:flex">
            <span
              v-for="(item, itemIndex) in group.spec.items.slice(0, 5)"
              :key="`${item.icon}-${itemIndex}`"
              class=":uno: inline-flex h-7 min-w-7 items-center justify-center overflow-hidden rounded-md bg-gray-100 px-1 text-xs font-semibold text-gray-700"
            >
              <img
                v-if="group.spec.type === 'image' && emoteItemPreviewUrl(item)"
                :src="emoteItemPreviewUrl(item)"
                :alt="item.text || group.spec.displayName"
                class=":uno: block max-h-6 max-w-6 object-contain"
                loading="lazy"
              />
              <span v-else class=":uno: max-w-14 truncate">{{ item.icon }}</span>
            </span>
          </div>
        </div>
      </VueDraggable>
    </div>

    <template #footer>
      <VSpace>
        <VButton type="secondary" :disabled="saving" @click="close">取消</VButton>
        <VButton type="primary" :loading="saving" @click="save">保存排序</VButton>
      </VSpace>
    </template>
  </VModal>
</template>
