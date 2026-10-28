<script lang="ts">
import { onMount } from 'svelte';
import CommentNextIcon from './CommentNextIcon.svelte';
import CommentNextSnippetPopover from './CommentNextSnippetPopover.svelte';
import { fetchSnippetThreads, type SnippetSubject } from './services/snippets';
import {
  dismissSnippetTarget,
  pinSnippetTarget,
  refreshSnippetTargets,
  registerSnippetTarget,
} from './snippets/controller';
import { snippetTransition } from './snippets/motion';
import { resolveSnippetRanges, type SnippetRange } from './snippets/ranges';
import type {
  CommentNextSnippetAnchor,
  CommentNextSnippetSelection,
  CommentNextSnippetThread,
} from './types/snippet';
import { portalToBody } from './utils/portal';

const {
  subject,
  onQuote,
}: {
  subject: SnippetSubject;
  onQuote: (anchor: CommentNextSnippetAnchor) => boolean;
} = $props();
let anchor: HTMLSpanElement;
let candidate = $state<CommentNextSnippetSelection | undefined>();
let threads = $state<CommentNextSnippetThread[]>([]);
let ranges: SnippetRange[] = [];
let host = $state<HTMLElement>();
const open = $derived(Boolean(candidate?.preview));

onMount(() => {
  const root = anchor.getRootNode();
  const element = root instanceof ShadowRoot ? root.host : anchor.parentElement;
  if (!(element instanceof HTMLElement)) return;
  host = element;
  const unregister = registerSnippetTarget({
    host: element,
    ranges: () => ranges,
    onChange: (value) => {
      candidate = value;
    },
  });
  let controller: AbortController | undefined;
  const refresh = async () => {
    controller?.abort();
    controller = new AbortController();
    try {
      threads = await fetchSnippetThreads(subject, controller.signal);
    } catch (error) {
      if (!(error instanceof DOMException && error.name === 'AbortError'))
        console.warn('片段评论暂时不可用');
    }
  };
  void refresh();
  window.addEventListener('halo:comment:created', refresh);
  return () => {
    unregister();
    controller?.abort();
    window.removeEventListener('halo:comment:created', refresh);
  };
});

$effect(() => {
  if (!host) return;
  ranges = resolveSnippetRanges(host, threads);
  refreshSnippetTargets();
});

function quote() {
  if (candidate && onQuote(candidate.anchor)) {
    dismissSnippetTarget();
    candidate = undefined;
    window.getSelection()?.removeAllRanges();
  }
}
</script>

<span bind:this={anchor} hidden></span>
{#if candidate}
  <button
    use:portalToBody={{ style: `left:${candidate.left}px;top:${candidate.top}px` }}
    transition:snippetTransition
    class="comment-next-snippet-trigger"
    type="button"
    aria-label={candidate.thread ? `${candidate.thread.total} 条片段评论` : '评论此片段'}
    aria-expanded={candidate.thread ? open : undefined}
    title={candidate.thread ? `点击查看 ${candidate.thread.total} 条片段评论` : '评论此片段'}
    onpointerdown={(event) => event.preventDefault()}
    onclick={() => {
      if (candidate?.thread) pinSnippetTarget();
      else quote();
    }}
  >
    <CommentNextIcon name="message" size={20} />
    {#if candidate.thread}<span>{candidate.thread.total}</span>{/if}
  </button>
  {#if candidate.thread && open}
    <CommentNextSnippetPopover
      {subject}
      thread={candidate.thread}
      left={candidate.left}
      top={candidate.top}
      onQuote={quote}
      onClose={dismissSnippetTarget}
    />
  {/if}
{/if}
