<script lang="ts" setup>
import {
  Dialog,
  IconRefreshLine,
  Toast,
  VAlert,
  VButton,
} from '@halo-dev/components';

interface PromptFieldNode {
  input(value: string): Promise<void>;
}

interface PromptDefaultsNode {
  at(address: string): PromptFieldNode | undefined;
}

interface PromptDefaultsContext {
  promptFormNode?: PromptDefaultsNode;
  latestSystemPrompt?: string;
  latestAutoReplyRolePrompt?: string;
  latestReviewRolePrompt?: string;
}

const props = defineProps<{
  context: PromptDefaultsContext;
}>();

interface PromptDefaultTarget {
  name: string;
  value: string | undefined;
}

function requestLatestPromptDefaults() {
  Dialog.warning({
    title: '获取最新角色设置',
    description:
      '将使用当前插件版本内置的最新内容覆盖三项提示词。修改只会写入当前表单，检查无误后仍需点击保存。',
    showCancel: true,
    confirmText: '获取并覆盖',
    cancelText: '取消',
    onConfirm: applyLatestPromptDefaults,
  });
}

async function applyLatestPromptDefaults() {
  const targets: PromptDefaultTarget[] = [
    {
      name: 'systemPrompt',
      value: props.context.latestSystemPrompt,
    },
    {
      name: 'autoReplyRolePrompt',
      value: props.context.latestAutoReplyRolePrompt,
    },
    {
      name: 'reviewRolePrompt',
      value: props.context.latestReviewRolePrompt,
    },
  ];

  const promptFormNode = props.context.promptFormNode;
  const updates = targets.map((target) => {
    const node = promptFormNode?.at(target.name);
    if (!node || !target.value?.trim()) {
      return undefined;
    }
    return node.input(target.value);
  });

  if (updates.some((update) => !update)) {
    Toast.error('无法读取当前版本的最新角色设置');
    return;
  }

  try {
    await Promise.all(updates);
    Toast.success('已获取最新角色设置，请检查后保存');
  } catch {
    Toast.error('获取最新角色设置失败，请稍后重试');
  }
}
</script>

<template>
  <VAlert
    type="info"
    title="更新当前版本的默认提示词"
    description="插件升级不会自动覆盖你已经保存的提示词；需要时可手动获取最新版。"
  >
    <template #actions>
      <VButton size="sm" type="secondary" @click="requestLatestPromptDefaults">
        <template #icon>
          <IconRefreshLine />
        </template>
        获取最新角色设置
      </VButton>
    </template>
  </VAlert>
</template>
