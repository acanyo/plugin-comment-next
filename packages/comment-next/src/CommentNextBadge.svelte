<script lang="ts">
import { badgeIconImage } from '../../../shared/badge-icon-image';
import CommentNextIcon from './CommentNextIcon.svelte';
import CommentNextTooltip from './CommentNextTooltip.svelte';
import type { CommentNextBadge } from './types/comment';

const {
  badge,
}: {
  badge: CommentNextBadge;
} = $props();

const badgeStyle = $derived(resolveBadgeStyle(badge.color));
const iconImage = $derived(badgeIconImage(badge.icon, badge.color));
const tooltipText = $derived(badge.title?.trim() ?? '');
// biome-ignore lint/style/useConst: Updated by the image error handler in the template.
let failedIcon = $state<string>();

const showImage = $derived(Boolean(iconImage && failedIcon !== iconImage));

function resolveBadgeStyle(color?: string): string | undefined {
  const badgeColor = normalizeColor(color);
  if (!badgeColor) {
    return undefined;
  }

  return [
    `--comment-next-badge-color: ${badgeColor}`,
    `--comment-next-badge-bg-color: color-mix(in srgb, ${badgeColor} 10%, transparent)`,
    `--comment-next-badge-border-color: color-mix(in srgb, ${badgeColor} 26%, transparent)`,
  ].join(';');
}

function normalizeColor(color?: string): string | undefined {
  const value = color?.trim();
  if (!value) {
    return undefined;
  }

  if (/^#[0-9a-f]{3,8}$/i.test(value)) {
    return value;
  }

  if (/^rgba?\([0-9\s,./%]+\)$/i.test(value)) {
    return value;
  }

  return undefined;
}
</script>

<CommentNextTooltip text={tooltipText} align="start">
  <span
    class:comment-next-badge-first={badge.tone === "first"}
    class:comment-next-badge-admin={badge.tone === "admin"}
    class:comment-next-badge-level={badge.tone === "level"}
    class:comment-next-badge-custom={badge.tone === "custom"}
    class:comment-next-badge-muted={badge.tone === "muted"}
    class="comment-next-badge inline-flex items-center box-border h-5 max-w-32 gap-[0.2rem] whitespace-nowrap rounded-md border border-solid [border-color:var(--comment-next-badge-border-color,rgb(15_23_42_/_0.08))] bg-[var(--comment-next-badge-bg-color,#f3f6f8)] px-[0.4rem] text-[0.6875rem] text-[var(--comment-next-badge-color,#4b5870)] font-[760] leading-none"
    style={badgeStyle}
  >
    {#if showImage}
      <img
        class="comment-next-badge-icon block h-3 w-3 flex-none object-contain"
        src={iconImage}
        alt=""
        aria-hidden="true"
        onerror={() => { failedIcon = iconImage; }}
      />
    {:else if badge.icon?.trim()}
      <CommentNextIcon name={iconImage ? 'medal' : badge.icon.trim()} size={12} />
    {/if}
    <span class="min-w-0 overflow-hidden text-ellipsis">{badge.label}</span>
  </span>
</CommentNextTooltip>

<style>
  .comment-next-badge-first {
    --comment-next-badge-bg-color: var(--comment-next-badge-first-bg-color, #fff7d7);
    --comment-next-badge-border-color: var(--comment-next-badge-first-border-color, #ead27a);
    --comment-next-badge-color: var(--comment-next-badge-first-color, #7a5700);
  }

  .comment-next-badge-admin {
    --comment-next-badge-bg-color: var(--comment-next-badge-admin-bg-color, #e9f7f4);
    --comment-next-badge-border-color: var(--comment-next-badge-admin-border-color, rgb(191 219 254));
    --comment-next-badge-color: var(--comment-next-badge-admin-color, #0f665f);
  }

  .comment-next-badge-level {
    --comment-next-badge-bg-color: var(--comment-next-badge-level-bg-color, #edf2ff);
    --comment-next-badge-border-color: var(--comment-next-badge-level-border-color, #bcc8ee);
    --comment-next-badge-color: var(--comment-next-badge-level-color, #364b92);
  }

  .comment-next-badge-custom {
    --comment-next-badge-bg-color: var(--comment-next-badge-custom-bg-color, #f6eefb);
    --comment-next-badge-border-color: var(--comment-next-badge-custom-border-color, #d8c1e8);
    --comment-next-badge-color: var(--comment-next-badge-custom-color, #6a3d86);
  }
</style>
