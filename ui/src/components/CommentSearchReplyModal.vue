<script lang="ts" setup>
import { consoleApiClient } from '@halo-dev/api-client';
import { Toast, VButton, VModal, VSpace } from '@halo-dev/components';
import { nextTick, onMounted, ref } from 'vue';
import type { CommentSearchItem } from '../api/comment-search';
import ConsoleCommentContent from './ConsoleCommentContent.vue';
import ConsoleCommentEditor from './ConsoleCommentEditor.vue';

const props = defineProps<{ item: CommentSearchItem }>();
const emit = defineEmits<{ close: []; replied: [] }>();

const visible = ref(false);
const content = ref('');
const characterCount = ref(0);
const submitting = ref(false);

onMounted(async () => {
  await nextTick();
  visible.value = true;
});

function updateEditor(value: { content: string; characterCount: number }) {
  content.value = value.content;
  characterCount.value = value.characterCount;
}

async function submit() {
  if (submitting.value || !characterCount.value) return;

  submitting.value = true;
  const reply = content.value;
  try {
    await consoleApiClient.content.comment.createReply({
      name: props.item.name,
      replyRequest: { raw: reply, content: reply, allowNotification: true },
    });
    Toast.success('回复已发布');
    emit('replied');
  } catch (error) {
    console.error('Failed to reply to comment', error);
    Toast.error('回复发布失败');
  } finally {
    submitting.value = false;
  }
}
</script>

<template>
  <VModal
    v-model:visible="visible"
    :title="`回复 ${item.authorName}`"
    :width="620"
    :closable="!submitting"
    @close="emit('close')"
  >
    <div class=":uno: flex flex-col gap-3">
      <div class=":uno: max-h-32 overflow-auto rounded bg-gray-50 p-3 text-sm">
        <ConsoleCommentContent :content="item.content || '无内容'" />
      </div>
      <ConsoleCommentEditor auto-focus :allow-images="false" @update="updateEditor" />
    </div>

    <template #footer>
      <VSpace>
        <VButton :disabled="submitting" @click="emit('close')">取消</VButton>
        <VButton
          type="primary"
          :loading="submitting"
          :disabled="submitting || !characterCount"
          @click="submit"
        >
          发表回复
        </VButton>
      </VSpace>
    </template>
  </VModal>
</template>
