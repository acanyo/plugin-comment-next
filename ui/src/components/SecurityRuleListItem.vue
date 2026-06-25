<script lang="ts" setup>
import {
  VDropdownItem,
  VEntity,
  VEntityField,
  VStatusDot,
} from '@halo-dev/components';
import { computed } from 'vue';
import type {
  SecurityRule,
  SecurityRuleField,
  SecurityRuleListType,
  SecurityRuleMatchType,
} from '../api/security-rules';
import { isDeletingResource } from '../utils/deletion';

const props = withDefaults(
  defineProps<{
    rule: SecurityRule;
    canManage?: boolean;
  }>(),
  {
    canManage: false,
  }
);

const emit = defineEmits<{
  toggle: [rule: SecurityRule];
  edit: [rule: SecurityRule];
  remove: [rule: SecurityRule];
}>();

const isDeleting = computed(() => isDeletingResource(props.rule));
const valueText = computed(() => props.rule.spec.value || '未填写匹配值');
const priorityText = computed(() => String(props.rule.spec.priority ?? 0));
const ruleReason = computed(() => props.rule.spec.reason || props.rule.metadata.name);

function listTypeText(type: SecurityRuleListType) {
  return type === 'BLACK' ? '黑名单' : '灰名单';
}

function listTypeState(type: SecurityRuleListType) {
  return type === 'BLACK' ? 'error' : 'warning';
}

function fieldText(field: SecurityRuleField) {
  const labels: Record<SecurityRuleField, string> = {
    IP: 'IP',
    EMAIL: '邮箱',
    USERNAME: '用户名',
    KEYWORD: '内容',
    DOMAIN: '域名',
    UA: 'UA',
  };
  return labels[field] || field;
}

function matchTypeText(matchType: SecurityRuleMatchType) {
  const labels: Record<SecurityRuleMatchType, string> = {
    EXACT: '精确',
    CONTAINS: '包含',
    REGEX: '正则',
  };
  return labels[matchType] || matchType;
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
      <VEntityField :title="valueText" width="28rem">
        <template #description>
          <div class=":uno: flex min-w-0 flex-wrap gap-x-2 gap-y-1 text-xs text-gray-500">
            <span v-tooltip="ruleReason" class=":uno: max-w-96 truncate">
              {{ ruleReason }}
            </span>
          </div>
        </template>
      </VEntityField>
    </template>

    <template #end>
      <VEntityField title="名单" width="7rem">
        <template #description>
          <VStatusDot
            :state="listTypeState(rule.spec.listType)"
            :text="listTypeText(rule.spec.listType)"
          />
        </template>
      </VEntityField>
      <VEntityField
        :title="fieldText(rule.spec.field)"
        :description="matchTypeText(rule.spec.matchType)"
        width="7rem"
      />
      <VEntityField title="优先级" width="5rem">
        <template #description>
          <span class=":uno: truncate text-xs tabular-nums text-gray-500">
            {{ priorityText }}
          </span>
        </template>
      </VEntityField>
      <VEntityField title="状态" width="7rem">
        <template #description>
          <VStatusDot
            :animate="isDeleting"
            :state="isDeleting ? 'warning' : rule.spec.enabled ? 'success' : 'default'"
            :text="isDeleting ? '删除中' : rule.spec.enabled ? '已启用' : '已停用'"
          />
        </template>
      </VEntityField>
    </template>

    <template v-if="canManage && !isDeleting" #dropdownItems>
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
