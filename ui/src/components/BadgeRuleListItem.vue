<script lang="ts" setup>
import {
  VDropdownItem,
  VEntity,
  VEntityField,
  VStatusDot,
} from '@halo-dev/components';
import { utils } from '@halo-dev/ui-shared';
import { Icon } from '@iconify/vue';
import { computed } from 'vue';
import type { BadgeRule, BadgeRuleType } from '../api/badge-rules';
import { isDeletingResource } from '../utils/deletion';

const props = withDefaults(
  defineProps<{
    rule: BadgeRule;
    canManage?: boolean;
  }>(),
  {
    canManage: false,
  }
);

const emit = defineEmits<{
  assign: [rule: BadgeRule];
  toggle: [rule: BadgeRule];
  edit: [rule: BadgeRule];
  remove: [rule: BadgeRule];
}>();

const previewStyle = computed<Record<string, string>>(() => ({
  '--comment-next-console-badge-color': props.rule.spec.color || '#3b82f6',
}));
const isDeleting = computed(() => isDeletingResource(props.rule));

const createdAtText = computed(() => {
  const timestamp = props.rule.metadata.creationTimestamp;
  return timestamp ? utils.date.format(timestamp) : '--';
});

function typeLabel(type: BadgeRuleType) {
  return type === 'LEVEL' ? '活跃等级' : '用户徽章';
}

function targetText(rule: BadgeRule) {
  if (rule.spec.type === 'LEVEL') {
    return `累计评论 >= ${rule.spec.minComments ?? 0} 条`;
  }
  return '可分配';
}

function handleAssign() {
  if (isDeleting.value) {
    return;
  }

  emit('assign', props.rule);
}

function handleToggle() {
  if (isDeleting.value) {
    return;
  }

  emit('toggle', props.rule);
}

function handleEdit() {
  if (isDeleting.value) {
    return;
  }

  emit('edit', props.rule);
}

function handleRemove() {
  if (isDeleting.value) {
    return;
  }

  emit('remove', props.rule);
}
</script>

<template>
  <VEntity>
    <template #start>
      <VEntityField :title="rule.spec.label || '未命名徽章'" width="22rem">
        <template #description>
          <div class=":uno: flex min-w-0 flex-wrap gap-x-2 gap-y-1 text-xs text-gray-500">
            <span class=":uno: max-w-80 truncate">{{ rule.metadata.name }}</span>
            <span v-if="rule.spec.title" class=":uno: max-w-80 truncate">
              {{ rule.spec.title }}
            </span>
          </div>
        </template>
      </VEntityField>
    </template>

    <template #end>
      <VEntityField
        :title="typeLabel(rule.spec.type)"
        :description="targetText(rule)"
        width="12rem"
      />
      <VEntityField width="10rem">
        <template #description>
          <div
            class="comment-next-console-badge-preview :uno: inline-flex min-w-0 max-w-40 items-center gap-[0.35rem] rounded-full border border-solid [border-color:color-mix(in_srgb,var(--comment-next-console-badge-color),white_68%)] [background:color-mix(in_srgb,var(--comment-next-console-badge-color),white_90%)] px-2 py-1 text-xs text-[var(--comment-next-console-badge-color)] font-bold"
            :style="previewStyle"
          >
            <Icon
              v-if="rule.spec.icon"
              :icon="rule.spec.icon"
              class=":uno: h-3.5 w-3.5 shrink-0"
            />
            <span class="comment-next-console-badge-preview-label :uno: truncate">
              {{ rule.spec.label || "未命名" }}
            </span>
          </div>
        </template>
      </VEntityField>
      <VEntityField width="7rem">
        <template #description>
          <VStatusDot
            :animate="isDeleting"
            :state="isDeleting ? 'warning' : rule.spec.enabled ? 'success' : 'warning'"
            :text="isDeleting ? '删除中' : rule.spec.enabled ? '已启用' : '已停用'"
          />
        </template>
      </VEntityField>
      <VEntityField width="8rem">
        <template #description>
          <span class=":uno: truncate text-xs tabular-nums text-gray-500">
            {{ createdAtText }}
          </span>
        </template>
      </VEntityField>
    </template>

    <template v-if="canManage && !isDeleting" #dropdownItems>
      <VDropdownItem
        v-if="rule.spec.type === 'USER'"
        @click="handleAssign"
      >
        分配徽章
      </VDropdownItem>
      <VDropdownItem @click="handleToggle">
        {{ rule.spec.enabled ? "停用" : "启用" }}
      </VDropdownItem>
      <VDropdownItem @click="handleEdit">
        编辑
      </VDropdownItem>
      <VDropdownItem type="danger" @click="handleRemove">
        删除
      </VDropdownItem>
    </template>
  </VEntity>
</template>
