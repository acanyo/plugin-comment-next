<script lang="ts">
  import CommentNextIcon from "./CommentNextIcon.svelte";

  let {
    activeMode = "polish",
    onModeSelect = () => {},
    onClose = () => {},
  }: {
    activeMode?: string;
    onModeSelect?: (mode: string) => void;
    onClose?: () => void;
  } = $props();

  const modes = [
    { key: "polish", icon: "wand", label: "润色", hint: "让表达更自然" },
    { key: "expand", icon: "plusCircle", label: "补充", hint: "延展一个观点" },
    { key: "question", icon: "circleHelp", label: "提问", hint: "换成提问角度" },
    { key: "reply", icon: "message", label: "回复", hint: "整理为回复语气" },
    { key: "summary", icon: "listChecks", label: "总结", hint: "压缩成短评" },
  ];
</script>

<div class="comment-next-ai-panel" role="dialog" aria-label="AI 写作助手">
  <div class="comment-next-ai-panel-head">
    <div class="comment-next-ai-panel-title">
      <span class="comment-next-ai-panel-emblem" aria-hidden="true">
        <CommentNextIcon name="sparkle" size={15} />
      </span>
      <div>
        <span class="comment-next-ai-panel-heading">AI 写作助手</span>
        <small class="comment-next-ai-panel-description">选择一个处理方式，建议会直接进入正文层</small>
      </div>
    </div>
    <button class="comment-next-ai-panel-close" type="button" title="关闭" aria-label="关闭 AI 写作助手" onclick={onClose}>
      <CommentNextIcon name="x" size={15} />
    </button>
  </div>

  <div class="comment-next-ai-panel-grid" role="menu" aria-label="AI 写作操作">
    {#each modes as mode}
      <button
        class:comment-next-ai-panel-item-active={activeMode === mode.key}
        class="comment-next-ai-panel-item"
        type="button"
        role="menuitem"
        onclick={() => onModeSelect(mode.key)}
      >
        <span class="comment-next-ai-panel-item-icon">
          <CommentNextIcon name={mode.icon} size={16} />
        </span>
        <span class="comment-next-ai-panel-item-label">{mode.label}</span>
        <small class="comment-next-ai-panel-item-hint">{mode.hint}</small>
      </button>
    {/each}
  </div>
</div>

<style>
  .comment-next-ai-panel {
    position: absolute;
    right: 1rem;
    bottom: 0.875rem;
    left: 1rem;
    z-index: 4;
    box-sizing: border-box;
    max-width: 42rem;
    padding: 0.75rem;
    border: 1px solid var(--comment-next-ai-border-color, rgb(191 219 254));
    border-radius: var(--comment-next-radius-md, 0.75rem);
    background: var(
      --comment-next-ai-panel-surface-bg,
      linear-gradient(180deg, rgb(255 255 255 / 0.96), rgb(248 252 251 / 0.96)),
      var(--comment-next-ai-panel-bg-color, #f8fcfb)
    );
    box-shadow:
      0 18px 44px rgb(15 23 42 / 0.12),
      0 1px 0 rgb(255 255 255 / 0.8) inset;
    color: var(--comment-next-text-color, #172033);
    animation: comment-next-panel-in 180ms cubic-bezier(0.2, 0.8, 0.2, 1);
  }

  .comment-next-ai-panel::before {
    position: absolute;
    top: 0.75rem;
    bottom: 0.75rem;
    left: 0;
    width: 0.1875rem;
    border-radius: 999px;
    background: var(--comment-next-ai-color, rgb(59, 130, 246));
    content: "";
  }

  .comment-next-ai-panel-head,
  .comment-next-ai-panel-title,
  .comment-next-ai-panel-grid,
  .comment-next-ai-panel-item {
    display: flex;
    align-items: center;
  }

  .comment-next-ai-panel-head {
    justify-content: space-between;
    gap: 0.75rem;
    padding-left: 0.375rem;
  }

  .comment-next-ai-panel-title {
    min-width: 0;
    gap: 0.625rem;
  }

  .comment-next-ai-panel-heading {
    display: block;
    color: var(--comment-next-text-color, #172033);
    font-size: 0.875rem;
    font-weight: 760;
  }

  .comment-next-ai-panel-description {
    display: block;
    margin-top: 0.125rem;
    color: var(--comment-next-muted-color, #667085);
    font-size: 0.75rem;
    font-weight: 500;
  }

  .comment-next-ai-panel-emblem {
    display: inline-flex;
    align-items: center;
    justify-content: center;
    width: 1.75rem;
    height: 1.75rem;
    border: 1px solid var(--comment-next-ai-border-color, rgb(191 219 254));
    border-radius: 999px;
    background: var(--comment-next-ai-bg-color, rgb(239 246 255));
    color: var(--comment-next-ai-color, rgb(59, 130, 246));
  }

  .comment-next-ai-panel-close {
    display: inline-flex;
    align-items: center;
    justify-content: center;
    width: 1.75rem;
    height: 1.75rem;
    padding: 0;
    border: 0;
    border-radius: 0.5rem;
    background: transparent;
    color: var(--comment-next-muted-color, #667085);
    cursor: pointer;
    font: inherit;
    transition:
      background-color 150ms ease,
      color 150ms ease,
      transform 150ms ease;
  }

  .comment-next-ai-panel-close:hover {
    background: var(--comment-next-control-hover-bg-color, #eef2f4);
    color: var(--comment-next-text-color, #172033);
  }

  .comment-next-ai-panel-close:active {
    transform: scale(0.94);
  }

  .comment-next-ai-panel-grid {
    gap: 0.5rem;
    margin-top: 0.75rem;
    overflow-x: auto;
    padding: 0.0625rem 0.0625rem 0.125rem;
  }

  .comment-next-ai-panel-item {
    position: relative;
    flex: 1 0 7.25rem;
    min-width: 0;
    min-height: 4.5rem;
    box-sizing: border-box;
    align-items: flex-start;
    flex-direction: column;
    justify-content: center;
    gap: 0.25rem;
    padding: 0.6875rem 0.75rem;
    border: 1px solid var(--comment-next-ai-control-border-color, #d8e8e5);
    border-radius: 0.625rem;
    background: var(--comment-next-ai-control-bg-color, #ffffff);
    color: var(--comment-next-text-color, #172033);
    cursor: pointer;
    font: inherit;
    text-align: left;
    transition:
      border-color 150ms ease,
      background-color 150ms ease,
      box-shadow 150ms ease,
      color 150ms ease,
      transform 150ms ease;
  }

  .comment-next-ai-panel-item-icon {
    display: inline-flex;
    align-items: center;
    justify-content: center;
    margin-bottom: 0.125rem;
    color: var(--comment-next-ai-color, rgb(59, 130, 246));
  }

  .comment-next-ai-panel-item-label {
    font-size: 0.875rem;
    font-weight: 730;
  }

  .comment-next-ai-panel-item-hint {
    color: var(--comment-next-muted-color, #667085);
    font-size: 0.75rem;
    font-weight: 500;
    line-height: 1.35;
  }

  .comment-next-ai-panel-item:hover,
  .comment-next-ai-panel-item-active {
    border-color: var(--comment-next-ai-border-color, rgb(191 219 254));
    background: var(--comment-next-ai-bg-color, #eaf8f5);
    box-shadow: 0 8px 18px rgb(15 118 110 / 0.1);
    color: var(--comment-next-ai-color, rgb(59, 130, 246));
    transform: translateY(-1px);
  }

  .comment-next-ai-panel-item:focus-visible,
  .comment-next-ai-panel-close:focus-visible {
    outline: 2px solid var(--comment-next-focus-ring-color, #80cbc0);
    outline-offset: 2px;
  }

  @keyframes comment-next-panel-in {
    from {
      opacity: 0;
      transform: translateY(0.5rem) scale(0.985);
    }

    to {
      opacity: 1;
      transform: translateY(0) scale(1);
    }
  }

  @media (max-width: 640px) {
    .comment-next-ai-panel {
      right: 0.75rem;
      bottom: 0.75rem;
      left: 0.75rem;
      padding: 0.625rem;
    }

    .comment-next-ai-panel-grid {
      display: grid;
      grid-template-columns: repeat(2, minmax(0, 1fr));
      overflow: visible;
    }

    .comment-next-ai-panel-item {
      flex-basis: auto;
      min-height: 4.25rem;
    }
  }

  @media (prefers-reduced-motion: reduce) {
    .comment-next-ai-panel,
    .comment-next-ai-panel-item,
    .comment-next-ai-panel-close {
      animation: none;
      transition: none;
    }
  }
</style>
