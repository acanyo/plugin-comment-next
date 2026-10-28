<script lang="ts">
import { rankingReaderBadge } from './presentation';

const { rank }: { rank: number } = $props();
const badge = $derived(rankingReaderBadge(rank));
</script>

<span class="reader-badge" data-tone={badge.tone}>{badge.label}</span>

<style>
  .reader-badge {
    --badge-color: #7a8798;
    display: inline-block;
    max-width: 100%;
    padding: 0.32rem 0.65rem;
    border-radius: 0.6rem;
    background: color-mix(in srgb, var(--badge-color) 14%, var(--ranking-surface));
    color: color-mix(in srgb, var(--badge-color) 85%, var(--ranking-text));
    font-size: 0.73rem;
    font-weight: 650;
    line-height: 1.2;
    white-space: nowrap;
  }

  [data-tone='gold'] {
    --badge-color: #db9b14;
  }
  [data-tone='violet'] {
    --badge-color: #8b5ada;
  }
  [data-tone='blue'] {
    --badge-color: #3987d9;
  }
  [data-tone='green'] {
    --badge-color: #279e72;
  }
</style>
