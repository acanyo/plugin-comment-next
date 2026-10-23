<script lang="ts">
import { onMount, untrack } from 'svelte';
import {
  MAX_WRITING_TAGS,
  writingInputHint,
  writingModes,
} from './ai/writing-options';
import CommentNextIcon from './CommentNextIcon.svelte';
import type { CommentNextAiMode } from './services/ai';
import { portalToBody } from './utils/portal';

const {
  activeMode = 'generate',
  loading = false,
  variant = 'comment',
  assistantName = '评论助手',
  panelStyle = '',
  hasContent = false,
  hasSubject = false,
  hasReplyTarget = false,
  onGenerate = () => {},
  onClose = () => {},
  onHeightChange = () => {},
}: {
  activeMode?: CommentNextAiMode;
  loading?: boolean;
  variant?: 'comment' | 'reply';
  assistantName?: string;
  panelStyle?: string;
  hasContent?: boolean;
  hasSubject?: boolean;
  hasReplyTarget?: boolean;
  onGenerate?: (mode: CommentNextAiMode, tags: string[]) => void;
  onClose?: () => void;
  onHeightChange?: (height: number) => void;
} = $props();

const modes = $derived(writingModes(variant));
let selectedMode = $state(untrack(() => activeMode));
let selectedTags = $state<string[]>([]);
let showAllTags = $state(false);
// biome-ignore lint/style/useConst: Svelte assigns this through bind:this.
let panelElement = $state<HTMLDivElement>();
const currentMode = $derived(
  modes.find((mode) => mode.key === selectedMode) ?? modes[0]
);
const visibleTags = $derived(
  showAllTags ? currentMode.tags : currentMode.tags.slice(0, 8)
);
const inputHint = $derived(
  writingInputHint(
    currentMode,
    hasContent,
    hasSubject,
    hasReplyTarget,
    selectedTags.length
  )
);
const description = $derived(
  inputHint ||
    (selectedTags.length >= MAX_WRITING_TAGS
      ? `最多选择 ${MAX_WRITING_TAGS} 个标签`
      : currentMode.hint)
);

onMount(() => {
  const trigger = document.activeElement;
  const observer = new ResizeObserver(() => {
    if (panelElement)
      onHeightChange(panelElement.getBoundingClientRect().height);
  });
  if (panelElement) observer.observe(panelElement);
  panelElement
    ?.querySelector<HTMLButtonElement>('[aria-pressed="true"]')
    ?.focus({ preventScroll: true });
  const handleKeyDown = (event: KeyboardEvent) => {
    if (event.key === 'Escape') {
      event.preventDefault();
      onClose();
      if (trigger instanceof HTMLElement)
        trigger.focus({ preventScroll: true });
    }
  };
  document.addEventListener('keydown', handleKeyDown);
  return () => {
    observer.disconnect();
    document.removeEventListener('keydown', handleKeyDown);
  };
});

function selectMode(mode: CommentNextAiMode) {
  selectedMode = mode;
  selectedTags = [];
  showAllTags = false;
}

function toggleTag(tag: string) {
  if (selectedTags.includes(tag)) {
    selectedTags = selectedTags.filter((value) => value !== tag);
  } else if (selectedTags.length < MAX_WRITING_TAGS) {
    selectedTags = [...selectedTags, tag];
  }
}
</script>

<div use:portalToBody={{ style: panelStyle }} class="comment-next-ai-panel-layer">
  <div
    class="comment-next-ai-panel-backdrop"
    aria-hidden="true"
    onclick={onClose}
  ></div>
  <div
    bind:this={panelElement}
    class="comment-next-ai-panel"
    role="dialog"
    aria-label={`${assistantName} · AI 评论`}
  >
    <header class="comment-next-ai-panel-head">
      <div>
        <h3>AI 帮你写</h3>
        <p aria-live="polite">{description}</p>
      </div>
      <button class="comment-next-ai-panel-close" type="button" aria-label="关闭 AI 评论" onclick={onClose}>
        <CommentNextIcon name="x" size={16} />
      </button>
    </header>
    <div class="comment-next-ai-modes" role="group" aria-label="写作方式">
      {#each modes as mode}
        <button
          type="button"
          class:comment-next-ai-mode-selected={currentMode.key === mode.key}
          aria-pressed={currentMode.key === mode.key}
          disabled={loading}
          onclick={() => selectMode(mode.key)}
        >{mode.label}</button>
      {/each}
    </div>
    <div class="comment-next-ai-tags" role="group" aria-label="评论标签">
      {#each visibleTags as tag}
        <button
          type="button"
          class:comment-next-ai-tag-selected={selectedTags.includes(tag)}
          aria-pressed={selectedTags.includes(tag)}
          disabled={loading || (!selectedTags.includes(tag) && selectedTags.length >= MAX_WRITING_TAGS)}
          onclick={() => toggleTag(tag)}
        >
          {tag}
        </button>
      {/each}
      {#if currentMode.tags.length > 8}
        <button class="comment-next-ai-tags-more" type="button" aria-expanded={showAllTags} onclick={() => (showAllTags = !showAllTags)}>
          {showAllTags ? '收起标签' : '更多标签'}
          <CommentNextIcon name={showAllTags ? 'chevronUp' : 'chevronDown'} size={13} />
        </button>
      {/if}
    </div>
    <footer class="comment-next-ai-panel-actions">
      <span class="comment-next-ai-selection-count" aria-live="polite">已选 {selectedTags.length}</span>
      <button class="comment-next-ai-cancel" type="button" onclick={onClose}>取消</button>
      <button
        class="comment-next-ai-generate"
        type="button"
        disabled={loading || Boolean(inputHint)}
        onclick={() => onGenerate(currentMode.key, [...selectedTags])}
      >
        {#if loading}<CommentNextIcon name="loader" size={14} />{/if}
        {loading ? '生成中' : '生成'}
      </button>
    </footer>
  </div>
</div>
