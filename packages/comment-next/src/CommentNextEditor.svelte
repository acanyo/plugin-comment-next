<script lang="ts">
  import CommentNextAiPanel from "./CommentNextAiPanel.svelte";
  import CommentNextIcon from "./CommentNextIcon.svelte";
  import { autolinkUrls, getTextSelectionOffset, restoreTextSelectionOffset } from "./utils/autolink";

  let {
    placeholder = "写下你的评论...",
    aiOpen = false,
    inlineSuggestion = false,
    selectionTools = false,
    aiMode = "polish",
    topRounded = false,
    onModeSelect = () => {},
    onCloseAiPanel = () => {},
  }: {
    placeholder?: string;
    aiOpen?: boolean;
    inlineSuggestion?: boolean;
    selectionTools?: boolean;
    aiMode?: string;
    topRounded?: boolean;
    onModeSelect?: (mode: string) => void;
    onCloseAiPanel?: () => void;
  } = $props();

  const modeLabels: Record<string, string> = {
    polish: "润色建议",
    expand: "补充观点",
    question: "提问角度",
    reply: "生成回复",
    summary: "总结观点",
  };

  let editorElement: HTMLDivElement | undefined;
  let autolinkTimer: number | undefined;

  function handleEditorInput() {
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
    const selectionOffset = wasFocused ? getTextSelectionOffset(editorElement) : undefined;
    const changed = autolinkUrls(editorElement);

    if (changed && wasFocused && selectionOffset !== undefined) {
      restoreTextSelectionOffset(editorElement, selectionOffset);
    }
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
    position: relative;
    min-height: var(--comment-next-editor-min-height, 12.5rem);
    overflow: hidden;
    background: var(
      --comment-next-editor-surface-bg,
      linear-gradient(180deg, rgb(255 255 255 / 0.96), rgb(250 253 252 / 0.96)),
      var(--comment-next-editor-bg-color, #ffffff)
    );
  }

  .comment-next-editor-wrap-top-rounded {
    border-radius: var(--comment-next-radius-lg, 0.875rem) var(--comment-next-radius-lg, 0.875rem) 0 0;
  }

  .comment-next-editor-wrap::after {
    position: absolute;
    right: 1rem;
    bottom: 0;
    left: 1rem;
    height: 1px;
    background: var(--comment-next-border-subtle-color, #e2e8ef);
    content: "";
  }

  .comment-next-editor-wrap-ai-open,
  .comment-next-editor-wrap-inline,
  .comment-next-editor-wrap-selection {
    background: var(
      --comment-next-editor-ai-surface-bg,
      radial-gradient(circle at 1.5rem 1.25rem, rgb(59 130 246 / 0.14), transparent 8rem),
      linear-gradient(180deg, rgb(255 255 255 / 0.98), rgb(247 252 251 / 0.98)),
      var(--comment-next-editor-bg-color, #ffffff)
    );
  }

  .comment-next-editor {
    min-height: var(--comment-next-editor-min-height, 12.5rem);
    box-sizing: border-box;
    padding: 1.125rem 1.25rem 1.25rem;
    outline: none;
    color: var(--comment-next-text-color, #172033);
    font-size: 0.9375rem;
    line-height: 1.7;
    caret-color: var(--comment-next-primary-color, rgb(59, 130, 246));
  }

  .comment-next-editor:empty::before {
    content: attr(data-placeholder);
    color: var(--comment-next-placeholder-color, #8b96a7);
    pointer-events: none;
  }

  .comment-next-editor :global(.comment-next-auto-link) {
    color: var(--comment-next-link-color, rgb(59, 130, 246));
    font-weight: 620;
    text-decoration-color: var(--comment-next-link-underline-color, rgb(59 130 246 / 0.35));
    text-decoration-thickness: 0.08em;
    text-underline-offset: 0.18em;
    transition:
      color 140ms ease,
      text-decoration-color 140ms ease;
  }

  .comment-next-editor :global(.comment-next-auto-link:hover) {
    color: var(--comment-next-link-hover-color, rgb(37 99 235));
    text-decoration-color: currentColor;
  }

  .comment-next-editor-paragraph {
    margin: 0;
  }

  .comment-next-selection-mark {
    padding: 0.0625rem 0.1875rem;
    border-radius: 0.25rem;
    background: var(--comment-next-ai-mark-bg-color, rgb(191 219 254));
    box-shadow: 0 0 0 1px rgb(59 130 246 / 0.16) inset;
    color: inherit;
  }

  .comment-next-ai-suggestion {
    position: relative;
    margin-top: 1rem;
    max-width: 45rem;
    padding: 0.875rem 1rem 1rem;
    border: 1px solid var(--comment-next-ai-border-color, rgb(191 219 254));
    border-radius: var(--comment-next-radius-md, 0.75rem);
    background: var(
      --comment-next-ai-suggestion-surface-bg,
      linear-gradient(180deg, rgb(255 255 255 / 0.94), rgb(243 252 249 / 0.94)),
      var(--comment-next-ai-suggestion-bg-color, rgb(248 251 255))
    );
    box-shadow:
      0 12px 28px rgb(15 23 42 / 0.08),
      0 1px 0 rgb(255 255 255 / 0.86) inset;
    color: var(--comment-next-text-color, #172033);
    animation: comment-next-suggestion-in 180ms cubic-bezier(0.2, 0.8, 0.2, 1);
  }

  .comment-next-ai-suggestion::before {
    position: absolute;
    top: 0.75rem;
    bottom: 0.75rem;
    left: 0;
    width: 0.1875rem;
    border-radius: 999px;
    background: var(--comment-next-ai-color, rgb(59, 130, 246));
    content: "";
  }

  .comment-next-ai-suggestion-head,
  .comment-next-ai-suggestion-title,
  .comment-next-ai-suggestion-emblem,
  .comment-next-ai-suggestion-actions,
  .comment-next-selection-bar {
    display: flex;
    align-items: center;
  }

  .comment-next-ai-suggestion-head {
    justify-content: space-between;
    gap: 0.75rem;
    color: var(--comment-next-muted-color, #667085);
    font-size: 0.8125rem;
  }

  .comment-next-ai-suggestion-title {
    min-width: 0;
    gap: 0.625rem;
  }

  .comment-next-ai-suggestion-emblem {
    justify-content: center;
    width: 1.75rem;
    height: 1.75rem;
    border: 1px solid var(--comment-next-ai-border-color, rgb(191 219 254));
    border-radius: 999px;
    background: var(--comment-next-ai-bg-color, rgb(239 246 255));
    color: var(--comment-next-ai-color, rgb(59, 130, 246));
  }

  .comment-next-ai-suggestion-kicker {
    display: block;
    color: var(--comment-next-ai-color, rgb(59, 130, 246));
    font-size: 0.75rem;
    font-weight: 700;
  }

  .comment-next-ai-suggestion-mode {
    display: block;
    margin-top: 0.0625rem;
    color: var(--comment-next-text-color, #172033);
    font-size: 0.875rem;
    font-weight: 760;
  }

  .comment-next-ai-suggestion-status {
    flex: 0 0 auto;
    color: var(--comment-next-muted-color, #667085);
    font-size: 0.75rem;
  }

  .comment-next-ai-suggestion-copy {
    margin: 0.75rem 0 0;
    padding-left: 2.375rem;
    color: var(--comment-next-ai-text-color, rgb(30 64 175));
    line-height: 1.72;
  }

  .comment-next-ai-suggestion-actions {
    gap: 0.375rem;
    margin-top: 0.875rem;
    padding-left: 2.375rem;
  }

  .comment-next-ai-suggestion-actions button,
  .comment-next-selection-bar button {
    display: inline-flex;
    align-items: center;
    justify-content: center;
    height: 1.75rem;
    gap: 0.3125rem;
    padding: 0 0.625rem;
    border: 1px solid transparent;
    border-radius: 0.5rem;
    background: transparent;
    color: var(--comment-next-muted-color, #667085);
    cursor: pointer;
    font: inherit;
    font-size: 0.8125rem;
    font-weight: 620;
    transition:
      background-color 150ms ease,
      border-color 150ms ease,
      color 150ms ease,
      transform 150ms ease;
  }

  .comment-next-ai-suggestion-actions button:hover,
  .comment-next-selection-bar button:hover,
  .comment-next-selection-action-active {
    border-color: var(--comment-next-ai-border-color, rgb(191 219 254));
    background: var(--comment-next-ai-control-hover-bg-color, rgb(239 246 255));
    color: var(--comment-next-ai-color, rgb(59, 130, 246));
  }

  .comment-next-ai-suggestion-actions button:active,
  .comment-next-selection-bar button:active {
    transform: translateY(1px);
  }

  .comment-next-ai-suggestion-primary {
    border-color: var(--comment-next-ai-color, rgb(59, 130, 246)) !important;
    background: var(--comment-next-ai-color, rgb(59, 130, 246)) !important;
    color: #ffffff !important;
  }

  .comment-next-selection-bar {
    position: absolute;
    top: 3.5rem;
    left: 1.25rem;
    z-index: 3;
    gap: 0.25rem;
    padding: 0.375rem;
    border: 1px solid var(--comment-next-menu-border-color, #d5dde7);
    border-radius: 0.75rem;
    background: var(--comment-next-menu-bg-color, #ffffff);
    box-shadow:
      0 14px 34px rgb(15 23 42 / 0.14),
      0 1px 0 rgb(255 255 255 / 0.8) inset;
    animation: comment-next-selection-in 160ms cubic-bezier(0.2, 0.8, 0.2, 1);
  }

  .comment-next-selection-bar::after {
    position: absolute;
    bottom: -0.3125rem;
    left: 2rem;
    width: 0.625rem;
    height: 0.625rem;
    border-right: 1px solid var(--comment-next-menu-border-color, #d5dde7);
    border-bottom: 1px solid var(--comment-next-menu-border-color, #d5dde7);
    background: var(--comment-next-menu-bg-color, #ffffff);
    content: "";
    transform: rotate(45deg);
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
      min-height: var(--comment-next-editor-mobile-min-height, 9rem);
      padding: 1rem;
    }

    .comment-next-ai-suggestion-copy,
    .comment-next-ai-suggestion-actions {
      padding-left: 0;
    }

    .comment-next-ai-suggestion-head {
      align-items: flex-start;
      flex-direction: column;
    }

    .comment-next-selection-bar {
      right: 0.75rem;
      left: 0.75rem;
      overflow-x: auto;
    }
  }

  @media (prefers-reduced-motion: reduce) {
    .comment-next-ai-suggestion,
    .comment-next-selection-bar,
    .comment-next-ai-suggestion-actions button,
    .comment-next-selection-bar button {
      animation: none;
      transition: none;
    }
  }
</style>
