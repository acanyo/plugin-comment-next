<script lang="ts">
import type { CommentNextComment } from './types/comment';
import { formatCommentDate, formatRelativeTime } from './utils/date';
import { getCommentEnvironmentTags } from './utils/user-agent';

const {
  entry,
  showCommenterDevice,
}: {
  entry: Pick<CommentNextComment, 'creationTime' | 'ipLocation' | 'userAgent'>;
  showCommenterDevice: boolean;
} = $props();

const time = $derived(formatRelativeTime(entry.creationTime));
const location = $derived(entry.ipLocation?.trim());
const devices = $derived(
  showCommenterDevice
    ? getCommentEnvironmentTags({ userAgent: entry.userAgent })
    : []
);
</script>

{#if time || location || devices.length}
  <div class="comment-next-comment-submeta">
    {#if time}
      <time datetime={entry.creationTime} title={formatCommentDate(entry.creationTime)}>{time}</time>
    {/if}
    {#if location}
      <span class="comment-next-ip-location" title={`IP 属地：${location}`}>
        <i class="comment-next-ip-location-glyph i-mdi:map-marker" aria-hidden="true"></i>
        <span class="comment-next-ip-location-text">{location}</span>
      </span>
    {/if}
    {#each devices as device (device.type)}
      <span class="comment-next-env-tag" title={`${device.title}：${device.label}`}>
        <i class={`comment-next-env-icon ${device.iconClass}`} style:color={device.color} aria-hidden="true"></i>
        <span>{device.label.replace(/\s+\d[\d.]*$/u, '')}</span>
      </span>
    {/each}
  </div>
{/if}

<style uno-safelist>
  .comment-next-comment-submeta {
    display: flex;
    flex-wrap: wrap;
    align-items: center;
    gap: 0.125rem 0.5625rem;
    margin-block-end: 0.3125rem;
    color: var(--comment-next-muted-color, #6b7687);
    font-size: 0.75rem;
    line-height: 1.4;
  }

  .comment-next-comment-submeta time {
    font-variant-numeric: tabular-nums;
    white-space: nowrap;
  }

  .comment-next-ip-location,
  .comment-next-env-tag {
    display: inline-flex;
    align-items: center;
    gap: 0.1875rem;
    min-inline-size: 0;
    max-inline-size: 100%;
  }

  .comment-next-ip-location {
    font-weight: 550;
  }

  .comment-next-ip-location-text,
  .comment-next-env-tag span {
    min-inline-size: 0;
    overflow-wrap: anywhere;
  }

  .comment-next-ip-location-glyph,
  .comment-next-env-icon {
    display: inline-block;
    flex: none;
    width: 0.75rem;
    height: 0.75rem;
  }
</style>
