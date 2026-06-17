<script lang="ts" setup>
import '@xhhao/comment-next';
import '@xhhao/comment-next/comment-next.css';
import { nextTick, onBeforeUnmount, onMounted, ref } from 'vue';

const props = defineProps<{
  autoFocus?: boolean;
}>();

const emit =
  defineEmits<
    (event: 'update', content: { content: string; characterCount: number }) => void
  >();

type ConsoleEditorElement = HTMLElement & {
  autoFocus?: boolean;
  editorOnly?: boolean;
  allowImages?: boolean;
  showAccountFields?: boolean;
  showFooter?: boolean;
  showSubmitArea?: boolean;
  allowAnonymous?: boolean;
  showCaptcha?: boolean;
  enablePrivate?: boolean;
  setFocus?: () => void;
  reset?: () => void;
};

const editorRef = ref<ConsoleEditorElement>();

onMounted(() => {
  nextTick(() => {
    applyEditorOptions();
    editorRef.value?.addEventListener('update', handleUpdate);

    if (props.autoFocus) {
      editorRef.value?.setFocus?.();
    }
  });
});

onBeforeUnmount(() => {
  editorRef.value?.removeEventListener('update', handleUpdate);
});

function handleUpdate(event: Event) {
  const detail = (event as CustomEvent).detail;
  emit('update', {
    content: detail.content,
    characterCount: detail.characterCount ?? 0,
  });
}

function applyEditorOptions() {
  if (!editorRef.value) {
    return;
  }

  editorRef.value.autoFocus = Boolean(props.autoFocus);
  editorRef.value.editorOnly = true;
  editorRef.value.allowImages = true;
  editorRef.value.showAccountFields = false;
  editorRef.value.showFooter = true;
  editorRef.value.showSubmitArea = false;
  editorRef.value.allowAnonymous = false;
  editorRef.value.showCaptcha = false;
  editorRef.value.enablePrivate = false;
}
</script>

<template>
  <comment-next
    ref="editorRef"
    placeholder="编写评论"
  ></comment-next>
</template>
