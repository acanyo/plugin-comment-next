<script lang="ts" setup>
import '@xhhao/comment-next';
import '@xhhao/comment-next/comment-next.css';

defineProps<{
  content: string;
}>();
</script>

<template>
  <div class="comment-next-console-comment-content">
    <comment-next-content :content="content" :allow-images="false"></comment-next-content>
  </div>
</template>

<style scoped>
.comment-next-console-comment-content {
  display: block;
  min-width: 0;
  max-width: 100%;
}

.comment-next-console-comment-content comment-next-content {
  display: block;
  min-width: 0;
  max-width: 100%;
}
</style>
