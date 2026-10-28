<script lang="ts">
import { getDefaultAnonymousAvatarUrl } from '../avatar/weavatar';

const {
  src = '',
  name = '匿名读者',
  loading = 'lazy',
}: {
  src?: string;
  name?: string;
  loading?: 'eager' | 'lazy';
} = $props();

let failed = $state(false);
const resolvedSrc = $derived(src.trim() || getDefaultAnonymousAvatarUrl());
const initial = $derived(Array.from(name.trim() || '匿名')[0]);

$effect(() => {
  resolvedSrc;
  failed = false;
});
</script>

<span class="ranking-avatar" aria-hidden="true">
  <span class="ranking-avatar__fallback">{initial}</span>
  {#if resolvedSrc && !failed}
    <img src={resolvedSrc} alt="" {loading} onerror={() => (failed = true)} />
  {/if}
</span>

<style>
  .ranking-avatar {
    position: relative;
    display: grid;
    width: 100%;
    height: 100%;
    overflow: hidden;
    place-items: center;
    border-radius: inherit;
    background: var(--ranking-avatar-fallback, #eef2f6);
  }

  .ranking-avatar__fallback {
    position: absolute;
    display: grid;
    width: 100%;
    height: 100%;
    place-items: center;
    color: var(--ranking-text, #172033);
    font-size: 0.95em;
    font-weight: 700;
  }

  img {
    position: relative;
    z-index: 1;
    width: 100%;
    height: 100%;
    object-fit: cover;
  }
</style>
