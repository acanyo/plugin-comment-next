<svelte:options
  customElement={{
    tag: "comment-next-content",
    props: {
      content: { reflect: true, type: "String", attribute: "content" },
      allowImages: { reflect: true, type: "Boolean", attribute: "allow-images" },
      enableImageLightbox: { reflect: true, type: "Boolean", attribute: "enable-image-lightbox" },
    },
  }}
/>

<script lang="ts">
import CommentNextContent from './CommentNextContent.svelte';

const {
  content = '',
  allowImages = true,
  enableImageLightbox = true,
}: {
  content?: string;
  allowImages?: boolean;
  enableImageLightbox?: boolean;
} = $props();
</script>

<CommentNextContent {content} {allowImages} {enableImageLightbox} />

<style>
  :host {
    display: block;
    min-width: 0;
    max-width: 100%;
    width: 100%;
    overflow-wrap: anywhere;
    word-break: break-word;
  }
</style>
