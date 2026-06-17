<script lang="ts">
import CommentNextIcon from './CommentNextIcon.svelte';

const {
  activeMode = 'polish',
  onModeSelect = () => {},
  onClose = () => {},
}: {
  activeMode?: string;
  onModeSelect?: (mode: string) => void;
  onClose?: () => void;
} = $props();

const modes = [
  { key: 'polish', icon: 'wand', label: '润色', hint: '让表达更自然' },
  { key: 'expand', icon: 'plusCircle', label: '补充', hint: '延展一个观点' },
  { key: 'question', icon: 'circleHelp', label: '提问', hint: '换成提问角度' },
  { key: 'reply', icon: 'message', label: '回复', hint: '整理为回复语气' },
  { key: 'summary', icon: 'listChecks', label: '总结', hint: '压缩成短评' },
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
    <button class="comment-next-ai-panel-close" type="button" aria-label="关闭 AI 写作助手" onclick={onClose}>
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
    --at-apply: absolute right-4 bottom-3.5 left-4 z-4 box-border max-w-168 rounded-[var(--comment-next-radius-md,0.75rem)] border border-solid [border-color:var(--comment-next-ai-border-color,rgb(191_219_254))] [background:var(--comment-next-ai-panel-surface-bg,linear-gradient(180deg,rgb(255_255_255_/_0.96),rgb(248_252_251_/_0.96)),var(--comment-next-ai-panel-bg-color,#f8fcfb))] p-3 text-[var(--comment-next-text-color,#172033)] shadow-[0_18px_44px_rgb(15_23_42_/_0.12),0_1px_0_rgb(255_255_255_/_0.8)_inset];
    animation: comment-next-panel-in 180ms cubic-bezier(0.2, 0.8, 0.2, 1);
  }

  .comment-next-ai-panel::before {
    --at-apply: absolute top-3 bottom-3 left-0 w-[0.1875rem] rounded-full bg-[var(--comment-next-ai-color,rgb(59,130,246))];
    content: "";
  }

  .comment-next-ai-panel-head,
  .comment-next-ai-panel-title,
  .comment-next-ai-panel-grid,
  .comment-next-ai-panel-item {
    --at-apply: flex items-center;
  }

  .comment-next-ai-panel-head {
    --at-apply: justify-between gap-3 pl-1.5;
  }

  .comment-next-ai-panel-title {
    --at-apply: min-w-0 gap-2.5;
  }

  .comment-next-ai-panel-heading {
    --at-apply: block text-sm text-[var(--comment-next-text-color,#172033)] font-[760];
  }

  .comment-next-ai-panel-description {
    --at-apply: mt-0.5 block text-xs text-[var(--comment-next-muted-color,#667085)] font-medium;
  }

  .comment-next-ai-panel-emblem {
    --at-apply: inline-flex h-7 w-7 items-center justify-center rounded-full border border-solid [border-color:var(--comment-next-ai-border-color,rgb(191_219_254))] bg-[var(--comment-next-ai-bg-color,rgb(239_246_255))] text-[var(--comment-next-ai-color,rgb(59,130,246))];
  }

  .comment-next-ai-panel-close {
    --at-apply: inline-flex h-7 w-7 cursor-pointer items-center justify-center rounded-lg border-0 bg-transparent p-0 text-[var(--comment-next-muted-color,#667085)] font-inherit transition-[background-color,color,transform] duration-150 ease-in-out;
  }

  .comment-next-ai-panel-close:hover {
    --at-apply: bg-[var(--comment-next-control-hover-bg-color,#eef2f4)] text-[var(--comment-next-text-color,#172033)];
  }

  .comment-next-ai-panel-close:active {
    --at-apply: scale-94;
  }

  .comment-next-ai-panel-grid {
    --at-apply: mt-3 gap-2 overflow-x-auto px-[0.0625rem] pb-0.5 pt-[0.0625rem];
  }

  .comment-next-ai-panel-item {
    --at-apply: relative flex-[1_0_7.25rem] min-h-18 min-w-0 box-border cursor-pointer flex-col items-start justify-center gap-1 rounded-[0.625rem] border border-solid [border-color:var(--comment-next-ai-control-border-color,#d8e8e5)] bg-[var(--comment-next-ai-control-bg-color,#ffffff)] px-3 py-[0.6875rem] text-left text-[var(--comment-next-text-color,#172033)] font-inherit transition-[border-color,background-color,box-shadow,color,transform] duration-150 ease-in-out;
  }

  .comment-next-ai-panel-item-icon {
    --at-apply: mb-0.5 inline-flex items-center justify-center text-[var(--comment-next-ai-color,rgb(59,130,246))];
  }

  .comment-next-ai-panel-item-label {
    --at-apply: text-sm font-[730];
  }

  .comment-next-ai-panel-item-hint {
    --at-apply: text-xs text-[var(--comment-next-muted-color,#667085)] font-medium leading-[1.35];
  }

  .comment-next-ai-panel-item:hover,
  .comment-next-ai-panel-item-active {
    --at-apply: -translate-y-px [border-color:var(--comment-next-ai-border-color,rgb(191_219_254))] bg-[var(--comment-next-ai-bg-color,#eaf8f5)] text-[var(--comment-next-ai-color,rgb(59,130,246))] shadow-[0_8px_18px_rgb(15_118_110_/_0.1)];
  }

  .comment-next-ai-panel-item:focus-visible,
  .comment-next-ai-panel-close:focus-visible {
    --at-apply: outline-2 outline-solid outline-offset-2 [outline-color:var(--comment-next-focus-ring-color,#80cbc0)];
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
      --at-apply: right-3 bottom-3 left-3 p-2.5;
    }

    .comment-next-ai-panel-grid {
      --at-apply: grid grid-cols-2 overflow-visible;
    }

    .comment-next-ai-panel-item {
      --at-apply: basis-auto min-h-17;
    }
  }

  @media (prefers-reduced-motion: reduce) {
    .comment-next-ai-panel,
    .comment-next-ai-panel-item,
    .comment-next-ai-panel-close {
      --at-apply: animate-none transition-none;
    }
  }
</style>
