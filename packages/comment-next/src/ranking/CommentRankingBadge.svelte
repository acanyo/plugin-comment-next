<script lang="ts">
import CommentNextBadge from '../CommentNextBadge.svelte';
import type { CommentNextBadge as Badge } from '../types/comment';

const { badge }: { badge?: Badge | null } = $props();
</script>

{#if badge?.label?.trim()}
  <span class="reader-badge"><CommentNextBadge {badge} /></span>
{/if}

<style>
  .reader-badge {
    display: inline-block;
    max-width: 100%;
  }
</style>
