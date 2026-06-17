<script lang="ts">
import CommentNextAiPanel from './CommentNextAiPanel.svelte';
import CommentNextIcon from './CommentNextIcon.svelte';
import {
  autolinkUrls,
  getTextSelectionOffset,
  restoreTextSelectionOffset,
} from './utils/autolink';

const {
  placeholder = '写下你的评论...',
  aiOpen = false,
  inlineSuggestion = false,
  selectionTools = false,
  aiMode = 'polish',
  topRounded = false,
  onChange = () => {},
  onModeSelect = () => {},
  onCloseAiPanel = () => {},
}: {
  placeholder?: string;
  aiOpen?: boolean;
  inlineSuggestion?: boolean;
  selectionTools?: boolean;
  aiMode?: string;
  topRounded?: boolean;
  onChange?: (html: string) => void;
  onModeSelect?: (mode: string) => void;
  onCloseAiPanel?: () => void;
} = $props();

const modeLabels: Record<string, string> = {
  polish: '润色建议',
  expand: '补充观点',
  question: '提问角度',
  reply: '生成回复',
  summary: '总结观点',
};

let editorElement: HTMLDivElement | undefined;
let autolinkTimer: number | undefined;

export function getHtml(): string {
  return editorElement?.innerHTML ?? '';
}

export function getText(): string {
  return editorElement?.textContent ?? '';
}

export function reset() {
  if (!editorElement) {
    return;
  }

  editorElement.innerHTML = '';
  onChange('');
}

export function focus() {
  editorElement?.focus();
}

export function insertText(value: string) {
  if (!value || !editorElement) {
    return;
  }

  insertNodeAtCaret(document.createTextNode(value));
}

export function insertImage(src: string, alt = '') {
  if (!src || !editorElement) {
    return;
  }

  const image = document.createElement('img');
  image.src = src;
  image.alt = alt;
  image.className = 'comment-next-emote-image';
  image.loading = 'lazy';
  image.decoding = 'async';
  insertNodeAtCaret(image, document.createTextNode(' '));
}

function handleEditorInput() {
  onChange(getHtml());

  if (inlineSuggestion || selectionTools) {
    return;
  }

  scheduleAutolink();
}

function handleEditorPaste() {
  if (inlineSuggestion || selectionTools) {
    return;
  }

  scheduleAutolink(0);
}

function scheduleAutolink(delay = 260) {
  if (!editorElement) {
    return;
  }

  if (autolinkTimer) {
    window.clearTimeout(autolinkTimer);
  }

  autolinkTimer = window.setTimeout(() => {
    runAutolink();
  }, delay);
}

function runAutolink() {
  if (!editorElement) {
    return;
  }

  const wasFocused = document.activeElement === editorElement;
  const selectionOffset = wasFocused
    ? getTextSelectionOffset(editorElement)
    : undefined;
  const changed = autolinkUrls(editorElement);

  if (changed && wasFocused && selectionOffset !== undefined) {
    restoreTextSelectionOffset(editorElement, selectionOffset);
  }

  onChange(getHtml());
}

function insertNodeAtCaret(node: Node, trailingNode?: Node) {
  if (!editorElement) {
    return;
  }

  editorElement.focus();

  const selection = window.getSelection();
  const range = resolveEditorRange(selection);

  range.deleteContents();
  range.insertNode(node);

  if (trailingNode) {
    range.setStartAfter(node);
    range.insertNode(trailingNode);
    range.setStartAfter(trailingNode);
  } else {
    range.setStartAfter(node);
  }

  range.collapse(true);
  selection?.removeAllRanges();
  selection?.addRange(range);
  onChange(getHtml());
}

function resolveEditorRange(selection: Selection | null): Range {
  if (
    selection?.rangeCount &&
    editorElement &&
    isSelectionInsideEditor(selection)
  ) {
    return selection.getRangeAt(0);
  }

  const range = document.createRange();
  range.selectNodeContents(editorElement as HTMLDivElement);
  range.collapse(false);

  return range;
}

function isSelectionInsideEditor(selection: Selection): boolean {
  if (!editorElement || !selection.anchorNode || !selection.focusNode) {
    return false;
  }

  return (
    editorElement === selection.anchorNode ||
    editorElement.contains(selection.anchorNode) ||
    editorElement === selection.focusNode ||
    editorElement.contains(selection.focusNode)
  );
}
</script>

<div
  class:comment-next-editor-wrap-ai-open={aiOpen}
  class:comment-next-editor-wrap-inline={inlineSuggestion}
  class:comment-next-editor-wrap-selection={selectionTools}
  class:comment-next-editor-wrap-top-rounded={topRounded}
  class="comment-next-editor-wrap"
>
  <div
    bind:this={editorElement}
    class="comment-next-editor"
    contenteditable="true"
    role="textbox"
    aria-multiline="true"
    data-placeholder={placeholder}
    aria-label="评论内容"
    oninput={handleEditorInput}
    onpaste={handleEditorPaste}
  >
    {#if inlineSuggestion}
      <p class="comment-next-editor-paragraph">我认为这篇文章对我帮助很大，尤其是关于时间管理的方法。</p>
      <div class="comment-next-ai-suggestion" contenteditable="false">
        <div class="comment-next-ai-suggestion-head">
          <div class="comment-next-ai-suggestion-title">
            <span class="comment-next-ai-suggestion-emblem" aria-hidden="true">
              <CommentNextIcon name="sparkle" size={14} />
            </span>
            <div>
              <span class="comment-next-ai-suggestion-kicker">AI 建议</span>
              <span class="comment-next-ai-suggestion-mode">{modeLabels[aiMode] ?? "写作建议"}</span>
            </div>
          </div>
          <span class="comment-next-ai-suggestion-status">可插入到光标处</span>
        </div>
        <p class="comment-next-ai-suggestion-copy">
          这篇文章让我重新审视了日常安排的优先级，也让我意识到减少任务切换，比单纯追求效率更重要。
        </p>
        <div class="comment-next-ai-suggestion-actions">
          <button class="comment-next-ai-suggestion-primary" type="button">
            <CommentNextIcon name="check" size={14} />
            接受
          </button>
          <button type="button">插入</button>
          <button type="button">
            <CommentNextIcon name="refresh" size={13} />
            重写
          </button>
        </div>
      </div>
    {:else if selectionTools}
      <p class="comment-next-editor-paragraph">
        这篇文章给了我很多启发，尤其是关于
        <mark class="comment-next-selection-mark">长期主义的部分</mark>
        ，让我对产品设计有了新的思考。
      </p>
    {/if}
  </div>

  {#if aiOpen && !inlineSuggestion && !selectionTools}
    <CommentNextAiPanel activeMode={aiMode} onModeSelect={onModeSelect} onClose={onCloseAiPanel} />
  {/if}

  {#if selectionTools}
    <div class="comment-next-selection-bar" contenteditable="false">
      <button class="comment-next-selection-action-active" type="button">
        <CommentNextIcon name="wand" size={14} />
        改写
      </button>
      <button type="button">更清楚</button>
      <button type="button">更友好</button>
      <button type="button">更专业</button>
      <button type="button">
        <CommentNextIcon name="refresh" size={13} />
      </button>
    </div>
  {/if}
</div>

<style>
  .comment-next-editor-wrap {
    --at-apply: relative min-h-[var(--comment-next-editor-min-height,12.5rem)] overflow-hidden [background:var(--comment-next-editor-surface-bg,transparent,var(--comment-next-editor-bg-color,#ffffff))];
  }

  .comment-next-editor-wrap-top-rounded {
    --at-apply: rounded-t-[var(--comment-next-radius-lg,0.875rem)];
  }

  .comment-next-editor-wrap::after {
    --at-apply: absolute right-4 bottom-0 left-4 h-0 border-t [border-top-style:var(--comment-next-divider-style,dashed)] [border-top-color:var(--comment-next-divider-color,#d4dde8)] bg-transparent;
    content: "";
  }

  .comment-next-editor-wrap-ai-open,
  .comment-next-editor-wrap-inline,
  .comment-next-editor-wrap-selection {
    --at-apply: [background:var(--comment-next-editor-ai-surface-bg,radial-gradient(circle_at_1.5rem_1.25rem,rgb(59_130_246_/_0.14),transparent_8rem),linear-gradient(180deg,rgb(255_255_255_/_0.98),rgb(247_252_251_/_0.98)),var(--comment-next-editor-bg-color,#ffffff))];
  }

  .comment-next-editor {
    --at-apply: box-border min-h-[var(--comment-next-editor-min-height,12.5rem)] px-5 pb-5 pt-[1.125rem] text-[0.9375rem] text-[var(--comment-next-text-color,#172033)] leading-[1.7] outline-none caret-[var(--comment-next-primary-color,rgb(59,130,246))];
  }

  .comment-next-editor:empty::before {
    content: attr(data-placeholder);
    --at-apply: pointer-events-none text-[var(--comment-next-placeholder-color,#8b96a7)];
  }

  .comment-next-editor :global(.comment-next-auto-link) {
    --at-apply: text-[var(--comment-next-link-color,rgb(59,130,246))] font-[620] decoration-[var(--comment-next-link-underline-color,rgb(59_130_246_/_0.35))] decoration-[0.08em] underline-offset-[0.18em] transition-[color,text-decoration-color] duration-140 ease-in-out;
  }

  .comment-next-editor :global(.comment-next-auto-link:hover) {
    --at-apply: text-[var(--comment-next-link-hover-color,rgb(37_99_235))] decoration-current;
  }

  .comment-next-editor :global(.comment-next-emote-image) {
    --at-apply: mx-0.5 inline-block max-h-15 max-w-30 align-middle object-contain;
  }

  .comment-next-editor-paragraph {
    --at-apply: m-0;
  }

  .comment-next-selection-mark {
    --at-apply: rounded px-[0.1875rem] py-[0.0625rem] bg-[var(--comment-next-ai-mark-bg-color,rgb(191_219_254))] text-inherit shadow-[0_0_0_1px_rgb(59_130_246_/_0.16)_inset];
  }

  .comment-next-ai-suggestion {
    --at-apply: relative mt-4 max-w-180 rounded-[var(--comment-next-radius-md,0.75rem)] border border-solid [border-color:var(--comment-next-ai-border-color,rgb(191_219_254))] [background:var(--comment-next-ai-suggestion-surface-bg,linear-gradient(180deg,rgb(255_255_255_/_0.94),rgb(243_252_249_/_0.94)),var(--comment-next-ai-suggestion-bg-color,rgb(248_251_255)))] px-4 pb-4 pt-3.5 text-[var(--comment-next-text-color,#172033)] shadow-[0_12px_28px_rgb(15_23_42_/_0.08),0_1px_0_rgb(255_255_255_/_0.86)_inset];
    animation: comment-next-suggestion-in 180ms cubic-bezier(0.2, 0.8, 0.2, 1);
  }

  .comment-next-ai-suggestion::before {
    --at-apply: absolute top-3 bottom-3 left-0 w-[0.1875rem] rounded-full bg-[var(--comment-next-ai-color,rgb(59,130,246))];
    content: "";
  }

  .comment-next-ai-suggestion-head,
  .comment-next-ai-suggestion-title,
  .comment-next-ai-suggestion-emblem,
  .comment-next-ai-suggestion-actions,
  .comment-next-selection-bar {
    --at-apply: flex items-center;
  }

  .comment-next-ai-suggestion-head {
    --at-apply: justify-between gap-3 text-[0.8125rem] text-[var(--comment-next-muted-color,#667085)];
  }

  .comment-next-ai-suggestion-title {
    --at-apply: min-w-0 gap-2.5;
  }

  .comment-next-ai-suggestion-emblem {
    --at-apply: h-7 w-7 justify-center rounded-full border border-solid [border-color:var(--comment-next-ai-border-color,rgb(191_219_254))] bg-[var(--comment-next-ai-bg-color,rgb(239_246_255))] text-[var(--comment-next-ai-color,rgb(59,130,246))];
  }

  .comment-next-ai-suggestion-kicker {
    --at-apply: block text-xs text-[var(--comment-next-ai-color,rgb(59,130,246))] font-bold;
  }

  .comment-next-ai-suggestion-mode {
    --at-apply: mt-[0.0625rem] block text-sm text-[var(--comment-next-text-color,#172033)] font-[760];
  }

  .comment-next-ai-suggestion-status {
    --at-apply: flex-none text-xs text-[var(--comment-next-muted-color,#667085)];
  }

  .comment-next-ai-suggestion-copy {
    --at-apply: mt-3 mb-0 ml-0 mr-0 pl-[2.375rem] text-[var(--comment-next-ai-text-color,rgb(30_64_175))] leading-[1.72];
  }

  .comment-next-ai-suggestion-actions {
    --at-apply: mt-3.5 gap-1.5 pl-[2.375rem];
  }

  .comment-next-ai-suggestion-actions button,
  .comment-next-selection-bar button {
    --at-apply: inline-flex h-7 cursor-pointer items-center justify-center gap-[0.3125rem] rounded-lg border border-solid border-transparent bg-transparent px-2.5 py-0 text-[0.8125rem] text-[var(--comment-next-muted-color,#667085)] font-[620] font-inherit transition-[background-color,border-color,color,transform] duration-150 ease-in-out;
  }

  .comment-next-ai-suggestion-actions button:hover,
  .comment-next-selection-bar button:hover,
  .comment-next-selection-action-active {
    --at-apply: [border-color:var(--comment-next-ai-border-color,rgb(191_219_254))] bg-[var(--comment-next-ai-control-hover-bg-color,rgb(239_246_255))] text-[var(--comment-next-ai-color,rgb(59,130,246))];
  }

  .comment-next-ai-suggestion-actions button:active,
  .comment-next-selection-bar button:active {
    --at-apply: translate-y-px;
  }

  .comment-next-ai-suggestion-actions .comment-next-ai-suggestion-primary {
    --at-apply: [border-color:var(--comment-next-ai-color,rgb(59,130,246))] bg-[var(--comment-next-ai-color,rgb(59,130,246))] text-white;
  }

  .comment-next-selection-bar {
    --at-apply: absolute top-14 left-5 z-3 gap-1.5 rounded-xl border border-solid [border-color:var(--comment-next-menu-border-color,#d5dde7)] bg-[var(--comment-next-menu-bg-color,#ffffff)] p-1.5 shadow-[0_14px_34px_rgb(15_23_42_/_0.14),0_1px_0_rgb(255_255_255_/_0.8)_inset];
    animation: comment-next-selection-in 160ms cubic-bezier(0.2, 0.8, 0.2, 1);
  }

  .comment-next-selection-bar::after {
    --at-apply: absolute bottom-[-0.3125rem] left-8 h-2.5 w-2.5 border-r border-b border-solid [border-color:var(--comment-next-menu-border-color,#d5dde7)] bg-[var(--comment-next-menu-bg-color,#ffffff)] rotate-45;
    content: "";
  }

  @keyframes comment-next-suggestion-in {
    from {
      opacity: 0;
      transform: translateY(0.375rem);
    }

    to {
      opacity: 1;
      transform: translateY(0);
    }
  }

  @keyframes comment-next-selection-in {
    from {
      opacity: 0;
      transform: translateY(0.375rem) scale(0.98);
    }

    to {
      opacity: 1;
      transform: translateY(0) scale(1);
    }
  }

  @media (max-width: 640px) {
    .comment-next-editor-wrap,
    .comment-next-editor {
      --at-apply: min-h-[var(--comment-next-editor-mobile-min-height,9rem)] p-4;
    }

    .comment-next-ai-suggestion-copy,
    .comment-next-ai-suggestion-actions {
      --at-apply: pl-0;
    }

    .comment-next-ai-suggestion-head {
      --at-apply: flex-col items-start;
    }

    .comment-next-selection-bar {
      --at-apply: right-3 left-3 overflow-x-auto;
    }
  }

  @media (prefers-reduced-motion: reduce) {
    .comment-next-ai-suggestion,
    .comment-next-selection-bar,
    .comment-next-ai-suggestion-actions button,
    .comment-next-selection-bar button {
      --at-apply: animate-none transition-none;
    }
  }
</style>
