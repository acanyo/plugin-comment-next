<svelte:options
  customElement={{
    tag: "comment-next-content",
    props: {
      content: { reflect: true, type: "String", attribute: "content" },
      allowImages: { reflect: true, type: "Boolean", attribute: "allow-images" },
    },
  }}
/>

<script lang="ts">
import CommentNextContent from './CommentNextContent.svelte';

const {
  content = '',
  allowImages = true,
}: {
  content?: string;
  allowImages?: boolean;
} = $props();
</script>

<CommentNextContent {content} {allowImages} />

<style>
  :host {
    display: block;
    width: 100%;
  }
</style>
