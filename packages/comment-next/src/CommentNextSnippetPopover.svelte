<script lang="ts">
import { onMount } from 'svelte';
import CommentNextAvatar from './CommentNextAvatar.svelte';
import CommentNextContent from './CommentNextContent.svelte';
import { fetchSnippetComments, type SnippetSubject } from './services/snippets';
import { snippetTransition } from './snippets/motion';
import { snippetCommentContent } from './snippets/text';
import type { CommentNextComment } from './types/comment';
import type { CommentNextSnippetThread } from './types/snippet';
import { portalToBody } from './utils/portal';

const {
  subject,
  thread,
  left,
  top,
  onQuote,
  onClose,
}: {
  subject: SnippetSubject;
  thread: CommentNextSnippetThread;
  left: number;
  top: number;
  onQuote: () => void;
  onClose: () => void;
} = $props();
let comments = $state<CommentNextComment[]>([]);
let loading = $state(false);
let error = $state('');
let page = $state(1);
let hasNext = $state(false);
// biome-ignore lint/style/useConst: Updated by the retry button in the template.
let retry = $state(0);
const position = $derived(
  `left:${Math.max(10, Math.min(left - 300, window.innerWidth - Math.min(360, window.innerWidth - 20) - 10))}px;top:${Math.max(10, Math.min(top + 42, window.innerHeight - Math.min(440, window.innerHeight - 20) - 10))}px`
);

onMount(() => {
  const closeOnEscape = (event: KeyboardEvent) => {
    if (event.key === 'Escape') onClose();
  };
  window.addEventListener('keydown', closeOnEscape);
  return () => window.removeEventListener('keydown', closeOnEscape);
});

$effect(() => {
  const key = thread.key;
  retry;
  page = 1;
  comments = [];
  const controller = new AbortController();
  void load(key, 1, controller.signal);
  return () => controller.abort();
});

async function load(key: string, nextPage: number, signal?: AbortSignal) {
  loading = true;
  error = '';
  try {
    const result = await fetchSnippetComments(subject, key, nextPage, signal);
    if (signal?.aborted || thread.key !== key) return;
    comments = nextPage === 1 ? result.items : [...comments, ...result.items];
    hasNext = result.hasNext;
    page = nextPage;
  } catch (cause) {
    if (!signal?.aborted && thread.key === key)
      error = cause instanceof Error ? cause.message : '片段评论加载失败';
  } finally {
    if (!signal?.aborted && thread.key === key) loading = false;
  }
}
</script>

<section transition:snippetTransition={{duration:180}} use:portalToBody={{style:position}} class="comment-next-snippet-popover" aria-label="片段评论">
  <header><strong>{thread.total} 条片段评论</strong><button type="button" aria-label="关闭片段评论" onclick={onClose}>×</button></header>
  <blockquote>{thread.anchor.exact}</blockquote>
  <div class="comment-next-snippet-discussion" aria-live="polite">
    {#each comments as comment (comment.id)}
      <article>
        <div class="comment-next-snippet-comment-author"><CommentNextAvatar src={comment.author.avatar} alt={comment.author.displayName} size={24}/><strong>{comment.author.displayName}</strong>{#if comment.private}<small>私密</small>{/if}{#if comment.approved === false}<small>待审核</small>{/if}</div>
        <CommentNextContent content={snippetCommentContent(comment.content, thread.anchor.exact)} allowImages={false} enableImageLightbox={false}/>
      </article>
    {/each}
    {#if loading}<p role="status">正在加载评论…</p>{/if}
    {#if error}<p role="alert">{error} <button type="button" onclick={() => (retry += 1)}>重试</button></p>{/if}
    {#if !loading && !error && !comments.length}<p>暂无可见评论</p>{/if}
    {#if hasNext && !loading}<button class="comment-next-snippet-more" type="button" onclick={() => load(thread.key, page + 1)}>加载更多评论</button>{/if}
  </div>
  <footer><button type="button" onclick={onQuote}>评论此片段</button></footer>
</section>
