<script lang="ts" setup>
import '@xhhao/comment-next';
import '@xhhao/comment-next/comment-next.css';

defineProps<{
  content: string;
}>();
</script>

<template>
  <comment-next-content :content="content" :allow-images="false"></comment-next-content>
</template>
