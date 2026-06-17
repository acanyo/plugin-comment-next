<script lang="ts" setup>
import {
  VDropdownItem,
  VEntity,
  VEntityField,
  VStatusDot,
} from '@halo-dev/components';
import { computed } from 'vue';
import { type EmoteGroup, emoteItemPreviewUrl } from '../api/emotes';
import { isDeletingResource } from '../utils/deletion';

const props = withDefaults(
  defineProps<{
    group: EmoteGroup;
    canManage?: boolean;
    isSelected?: boolean;
  }>(),
  {
    canManage: false,
    isSelected: false,
  }
);

const emit = defineEmits<{
  toggle: [group: EmoteGroup];
  remove: [group: EmoteGroup];
}>();

const createdAtText = computed(() => {
  const timestamp = props.group.metadata.creationTimestamp;

  if (!timestamp) {
    return '--';
  }

  const date = new Date(timestamp);

  if (Number.isNaN(date.getTime())) {
    return timestamp;
  }

  return `${padDateValue(date.getMonth() + 1)}-${padDateValue(date.getDate())} ${padDateValue(date.getHours())}:${padDateValue(date.getMinutes())}`;
});

const previewItems = computed(() => props.group.spec.items.slice(0, 6));
const isDeleting = computed(() => isDeletingResource(props.group));

function typeText(type: string) {
  return type === 'image' ? '图片表情' : '颜文字';
}

function sourceText(type: string) {
  return type === 'DEFAULT' ? '默认源' : '自定义';
}

function padDateValue(value: number): string {
  return value.toString().padStart(2, '0');
}
</script>

<template>
  <VEntity :is-selected="isSelected">
    <template v-if="canManage" #checkbox>
      <slot name="checkbox" />
    </template>

    <template #start>
      <VEntityField :title="group.spec.displayName || '未命名表情'" width="16rem">
        <template #description>
          <div class=":uno: flex min-w-0 items-center gap-1.5 text-xs text-gray-500">
            <span>{{ sourceText(group.spec.sourceType) }}</span>
            <span class=":uno: text-gray-300">·</span>
            <span>{{ typeText(group.spec.type) }}</span>
            <span class=":uno: text-gray-300">·</span>
            <span class=":uno: tabular-nums">{{ group.spec.items.length }} 个</span>
          </div>
        </template>
      </VEntityField>
    </template>

    <template #end>
      <VEntityField width="14rem">
        <template #description>
          <div class=":uno: flex min-w-0 items-center gap-1 overflow-hidden">
            <span
              v-for="(item, index) in previewItems"
              :key="`${item.icon}-${index}`"
              class=":uno: inline-flex h-7 min-w-7 items-center justify-center overflow-hidden rounded-md bg-gray-100 px-1 text-xs font-semibold text-gray-700"
            >
              <img
                v-if="group.spec.type === 'image' && emoteItemPreviewUrl(item)"
                :src="emoteItemPreviewUrl(item)"
                :alt="item.text || group.spec.displayName"
                class=":uno: block max-h-6 max-w-6 object-contain"
                loading="lazy"
              />
              <span v-else class=":uno: max-w-16 truncate">{{ item.icon }}</span>
            </span>
          </div>
        </template>
      </VEntityField>
      <VEntityField width="5.5rem">
        <template #description>
          <VStatusDot
            :animate="isDeleting"
            :state="isDeleting ? 'warning' : group.spec.enabled ? 'success' : 'warning'"
            :text="isDeleting ? '删除中' : group.spec.enabled ? '已启用' : '已停用'"
          />
        </template>
      </VEntityField>
      <VEntityField width="5.5rem">
        <template #description>
          <span class=":uno: truncate text-xs tabular-nums text-gray-500">
            {{ createdAtText }}
          </span>
        </template>
      </VEntityField>
    </template>

    <template v-if="canManage && !isDeleting" #dropdownItems>
      <VDropdownItem @click="emit('toggle', group)">
        {{ group.spec.enabled ? "停用" : "启用" }}
      </VDropdownItem>
      <VDropdownItem type="danger" @click="emit('remove', group)">
        删除
      </VDropdownItem>
    </template>
  </VEntity>
</template>
