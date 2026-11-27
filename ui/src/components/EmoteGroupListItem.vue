<script lang="ts" setup>
import {
  VDropdownItem,
  VEntity,
  VEntityField,
  VStatusDot,
} from '@halo-dev/components';
import { computed } from 'vue';
import {
  type EmoteGroup,
  emoteItemPreviewUrl,
  summarizeGroupLocalization,
} from '../api/emotes';
import { isDeletingResource } from '../utils/deletion';

const props = withDefaults(
  defineProps<{
    group: EmoteGroup;
    canManage?: boolean;
    isSelected?: boolean;
    siteOrigin?: string;
    localizationRunning?: boolean;
  }>(),
  {
    canManage: false,
    isSelected: false,
    siteOrigin: '',
    localizationRunning: false,
  }
);

const emit = defineEmits<{
  toggle: [group: EmoteGroup];
  localize: [group: EmoteGroup];
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

const previewItems = computed(() =>
  props.group.spec.items.slice(0, props.group.spec.type === 'image' ? 6 : 3)
);
const isDeleting = computed(() => isDeletingResource(props.group));
const localizationSummary = computed(() =>
  summarizeGroupLocalization(props.group, props.siteOrigin)
);
const canLocalize = computed(
  () =>
    props.canManage &&
    !isDeleting.value &&
    !props.localizationRunning &&
    props.group.spec.provider !== 'LOTTIE' &&
    props.group.spec.type === 'image' &&
    localizationSummary.value.externalImages > 0
);

function typeText(type: string) {
  if (type === 'lottie') return 'Lottie 动画';
  return type === 'image' ? '图片表情' : '颜文字';
}

function sourceText(type: string) {
  if (type === 'PLUGIN') return 'plugin-lottie';
  return type === 'DEFAULT' ? '默认源' : '自定义源';
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
            <template v-if="localizationSummary.status === 'LOCALIZED'">
              <span class=":uno: text-gray-300">·</span>
              <span class=":uno: rounded bg-green-50 px-1.5 py-0.5 text-[11px] font-medium text-green-700">
                已本地化
              </span>
            </template>
            <template v-else-if="localizationSummary.status === 'PARTIAL'">
              <span class=":uno: text-gray-300">·</span>
              <span class=":uno: rounded bg-amber-50 px-1.5 py-0.5 text-[11px] font-medium text-amber-700">
                {{ localizationSummary.externalImages }} 个外链
              </span>
            </template>
            <template v-else-if="localizationSummary.status === 'EXTERNAL'">
              <span class=":uno: text-gray-300">·</span>
              <span class=":uno: rounded bg-gray-100 px-1.5 py-0.5 text-[11px] text-gray-600">
                外链
              </span>
            </template>
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
              :class="
                group.spec.type === 'image'
                  ? ':uno: inline-flex h-7 min-w-7 items-center justify-center overflow-hidden rounded-md bg-gray-100 px-1 text-xs font-semibold text-gray-700'
                  : ':uno: inline-flex h-7 items-center justify-center overflow-hidden rounded-md bg-gray-100 px-2 text-xs font-medium text-gray-700'
              "
            >
              <img
                v-if="group.spec.type === 'image' && emoteItemPreviewUrl(item)"
                :src="emoteItemPreviewUrl(item)"
                :alt="item.text || group.spec.displayName"
                class=":uno: block max-h-6 max-w-6 object-contain"
                loading="lazy"
              />
              <span v-else class=":uno: max-w-28 truncate">
                {{ group.spec.provider === 'LOTTIE' ? (item.text || item.animationName) : item.icon }}
              </span>
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
      <VDropdownItem
        v-if="canLocalize"
        @click="emit('localize', group)"
      >
        本地化到附件库
      </VDropdownItem>
      <VDropdownItem type="danger" @click="emit('remove', group)">
        删除
      </VDropdownItem>
    </template>
  </VEntity>
</template>
