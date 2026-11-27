<script lang="ts" setup>
import { VButton, VModal, VSpace } from '@halo-dev/components';
import { computed } from 'vue';
import {
  type EmoteGroup,
  type EmoteLocalizationOptions,
  summarizeGroupLocalization,
} from '../api/emotes';
import EmoteLocalizationFields from './EmoteLocalizationFields.vue';

const MAX_VISIBLE_BADGES = 8;

const props = withDefaults(
  defineProps<{
    visible: boolean;
    groups: EmoteGroup[];
    options?: EmoteLocalizationOptions;
    optionsLoading?: boolean;
    submitting?: boolean;
    policyName: string;
    attachmentGroupName: string;
    useAbsoluteUrl: boolean;
    siteOrigin?: string;
  }>(),
  {
    options: undefined,
    optionsLoading: false,
    submitting: false,
    siteOrigin: '',
  }
);

const emit = defineEmits<{
  'update:visible': [value: boolean];
  'update:policyName': [value: string];
  'update:attachmentGroupName': [value: string];
  'update:useAbsoluteUrl': [value: boolean];
  refreshOptions: [];
  confirm: [];
}>();

const modalVisible = computed({
  get: () => props.visible,
  set: (value: boolean) => emit('update:visible', value),
});

const groupSummaries = computed(() =>
  props.groups
    .filter(
      (group) => group.spec.provider !== 'LOTTIE' && group.spec.type === 'image'
    )
    .map((group) => ({
      group,
      summary: summarizeGroupLocalization(group, props.siteOrigin),
    }))
);

const visibleGroupSummaries = computed(() =>
  groupSummaries.value.slice(0, MAX_VISIBLE_BADGES)
);

const hiddenGroupCount = computed(() =>
  Math.max(0, groupSummaries.value.length - visibleGroupSummaries.value.length)
);

const totalExternalImages = computed(() =>
  groupSummaries.value.reduce(
    (sum, entry) => sum + entry.summary.externalImages,
    0
  )
);

const canConfirm = computed(
  () =>
    !props.submitting &&
    !props.optionsLoading &&
    groupSummaries.value.length > 0 &&
    Boolean(props.policyName.trim())
);
</script>

<template>
  <VModal
    v-model:visible="modalVisible"
    title="本地化表情图片到 Halo 附件库"
    :width="640"
  >
    <div class=":uno: space-y-4">
      <div class=":uno: rounded-lg border border-solid border-blue-100 bg-blue-50/50 p-3 text-xs leading-5 text-gray-700">
        <div>
          将处理 <strong class=":uno: text-gray-900">{{ groupSummaries.length }}</strong> 个图片表情分类，
          预计需要下载并转存 <strong class=":uno: text-gray-900">{{ totalExternalImages }}</strong> 张外链图片。
        </div>
        <div v-if="visibleGroupSummaries.length" class=":uno: mt-2 flex flex-wrap gap-1.5">
          <span
            v-for="entry in visibleGroupSummaries"
            :key="entry.group.metadata.name"
            class=":uno: inline-flex items-center gap-1 rounded-full bg-white px-2 py-0.5 text-[11px] text-gray-700 ring-1 ring-inset ring-blue-100"
          >
            <span>{{ entry.group.spec.displayName }}</span>
            <span class=":uno: text-gray-400">({{ entry.summary.externalImages }})</span>
          </span>
          <span
            v-if="hiddenGroupCount > 0"
            class=":uno: inline-flex items-center rounded-full bg-blue-100/70 px-2 py-0.5 text-[11px] font-medium text-blue-700"
          >
            +{{ hiddenGroupCount }} 个分类
          </span>
        </div>
      </div>

      <EmoteLocalizationFields
        :options="props.options"
        :loading="props.optionsLoading"
        :disabled="props.submitting"
        :policy-name="props.policyName"
        :attachment-group-name="props.attachmentGroupName"
        :use-absolute-url="props.useAbsoluteUrl"
        @update:policy-name="emit('update:policyName', $event)"
        @update:attachment-group-name="emit('update:attachmentGroupName', $event)"
        @update:use-absolute-url="emit('update:useAbsoluteUrl', $event)"
        @refresh-options="emit('refreshOptions')"
      />
    </div>

    <template #footer>
      <VSpace>
        <VButton
          type="default"
          :disabled="props.submitting"
          @click="modalVisible = false"
        >
          取消
        </VButton>
        <VButton
          type="primary"
          :loading="props.submitting"
          :disabled="!canConfirm"
          @click="emit('confirm')"
        >
          开始后台本地化
        </VButton>
      </VSpace>
    </template>
  </VModal>
</template>
