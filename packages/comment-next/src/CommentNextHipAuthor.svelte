<script lang="ts">
import { onMount } from 'svelte';
import CommentNextAvatar from './CommentNextAvatar.svelte';
import {
  bindHipData,
  isInteractionPlusRuntimeReady,
  toIdentityData,
  whenInteractionPlusRuntimeReady,
} from './services/interaction-plus';
import type { CommentNextAuthorIdentity } from './types/interaction-plus';

const {
  identity,
  variant,
  size = 36,
  displayName = '',
  avatar = '',
}: {
  identity: CommentNextAuthorIdentity;
  variant: 'avatar' | 'identity';
  size?: number;
  displayName?: string;
  avatar?: string;
} = $props();

const payload = $derived(toIdentityData(identity));
const resolvedDisplayName = $derived(
  identity.displayName?.trim() || displayName.trim()
);
const resolvedAvatar = $derived(identity.avatar?.trim() || avatar);

let runtimeReady = $state(isInteractionPlusRuntimeReady());

onMount(() => {
  if (runtimeReady) {
    return;
  }

  let cancelled = false;
  whenInteractionPlusRuntimeReady().then(() => {
    if (!cancelled) {
      runtimeReady = true;
    }
  });

  return () => {
    cancelled = true;
  };
});
</script>

{#if payload && runtimeReady}
  {#if variant === 'avatar'}
    <span class="comment-next-hip-avatar" style={`--hip-avatar-size: ${size}px`}>
      <hip-user-card data={payload} scene="comment" use:bindHipData={payload}>
        <hip-user-avatar
          data={payload}
          scene="comment"
          use:bindHipData={payload}
        ></hip-user-avatar>
      </hip-user-card>
    </span>
  {:else}
    <span class="comment-next-hip-identity">
      <hip-user-identity
        data={payload}
        scene="comment"
        use:bindHipData={payload}
      ></hip-user-identity>
    </span>
  {/if}
{:else if variant === 'avatar'}
  <CommentNextAvatar src={resolvedAvatar} alt={resolvedDisplayName} {size} />
{:else}
  <span>{resolvedDisplayName}</span>
{/if}

<style>
  .comment-next-hip-avatar {
    --at-apply: inline-flex overflow-visible leading-none;
  }

  .comment-next-hip-avatar :global(hip-user-card),
  .comment-next-hip-avatar :global(hip-user-avatar) {
    display: inline-flex;
    overflow: visible;
    line-height: 0;
    vertical-align: top;
  }

  .comment-next-hip-identity {
    --at-apply: inline-flex max-w-full min-w-0 items-center overflow-visible;
  }

  .comment-next-hip-identity :global(hip-user-identity) {
    max-width: 100%;
  }
</style>
