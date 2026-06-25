<script lang="ts">
import CommentNextIcon from './CommentNextIcon.svelte';

type CommentNextComposerVariant = 'comment' | 'reply';

const {
  activeMode = 'polish',
  loading = false,
  variant = 'comment',
  assistantName = '评论助手',
  onModeSelect = () => {},
  onClose = () => {},
}: {
  activeMode?: string;
  loading?: boolean;
  variant?: CommentNextComposerVariant;
  assistantName?: string;
  onModeSelect?: (mode: string) => void;
  onClose?: () => void;
} = $props();

const commands = [
  {
    key: 'polish',
    icon: 'wand',
    label: '润色',
    hint: '优化当前评论表达',
  },
  {
    key: 'expand',
    icon: 'plusCircle',
    label: '补充观点',
    hint: '延展当前内容',
  },
  {
    key: 'question',
    icon: 'circleHelp',
    label: '提问角度',
    hint: '转换为问题',
  },
  {
    key: 'reply',
    icon: 'message',
    label: '生成回复',
    hint: '整理回复语气',
    replyOnly: true,
  },
  {
    key: 'summary',
    icon: 'listChecks',
    label: '总结文章评论',
    hint: '提炼文章内容',
    commentOnly: true,
  },
];

const visibleCommands = $derived(
  commands.filter(
    (command) =>
      (!command.replyOnly || variant === 'reply') &&
      (!command.commentOnly || variant === 'comment')
  )
);
</script>

<div class="comment-next-ai-panel-layer">
  <div
    class="comment-next-ai-panel-backdrop"
    aria-hidden="true"
  ></div>

  <div class="comment-next-ai-panel" role="dialog" aria-label={`${assistantName}命令`}>
    <button
      class="comment-next-ai-panel-close"
      type="button"
      aria-label={`关闭${assistantName}命令`}
      onclick={onClose}
    >
      <CommentNextIcon name="x" size={15} />
    </button>

    <div class="comment-next-ai-panel-list" role="menu" aria-label={`${assistantName}命令列表`}>
      {#each visibleCommands as command}
        <button
          class:comment-next-ai-panel-command-active={activeMode === command.key}
          class="comment-next-ai-panel-command"
          type="button"
          role="menuitem"
          disabled={loading}
          onclick={() => onModeSelect(command.key)}
        >
          <span class="comment-next-ai-panel-command-icon" aria-hidden="true">
            <CommentNextIcon name={command.icon} size={16} />
          </span>
          <span class="comment-next-ai-panel-command-copy">
            <span class="comment-next-ai-panel-command-label">{command.label}</span>
            <small class="comment-next-ai-panel-command-hint">{command.hint}</small>
          </span>
        </button>
      {/each}
    </div>
  </div>
</div>

<style>
  .comment-next-ai-panel-layer {
    --at-apply: absolute bottom-[calc(100%+0.5rem)] left-0 z-60 w-[min(18.75rem,calc(100vw-2rem))];
  }

  .comment-next-ai-panel-backdrop {
    --at-apply: hidden;
  }

  .comment-next-ai-panel {
    --at-apply: relative box-border w-full overflow-hidden rounded-[var(--comment-next-radius-lg,0.875rem)] border border-solid [border-color:var(--comment-next-menu-border-color,#d5dde7)] bg-[var(--comment-next-menu-bg-color,#ffffff)] p-1.5 text-[var(--comment-next-text-color,#172033)] shadow-[0_18px_42px_rgb(15_23_42_/_0.14),0_1px_0_rgb(255_255_255_/_0.82)_inset];
    animation: comment-next-ai-menu-in 150ms cubic-bezier(0.2, 0.8, 0.2, 1);
  }

  .comment-next-ai-panel-list,
  .comment-next-ai-panel-command {
    --at-apply: flex items-center;
  }

  .comment-next-ai-panel-close {
    --at-apply: absolute right-2 top-2 z-1 inline-flex h-7 w-7 cursor-pointer items-center justify-center rounded-lg border-0 bg-transparent p-0 text-[var(--comment-next-muted-color,#667085)] font-inherit transition-[background-color,color,transform] duration-140 ease-in-out;
  }

  .comment-next-ai-panel-close:hover {
    --at-apply: bg-[var(--comment-next-control-hover-bg-color,#eef2f4)] text-[var(--comment-next-text-color,#172033)];
  }

  .comment-next-ai-panel-close:active {
    --at-apply: scale-94;
  }

  .comment-next-ai-panel-list {
    --at-apply: flex-col items-stretch gap-0.5;
  }

  .comment-next-ai-panel-command {
    --at-apply: min-h-11 w-full cursor-pointer gap-2.5 rounded-[0.6875rem] border-0 bg-transparent px-2.5 py-2 text-left text-[var(--comment-next-text-color,#172033)] font-inherit transition-[background-color,color,transform] duration-140 ease-in-out;
  }

  .comment-next-ai-panel-command:first-child {
    --at-apply: pr-10;
  }

  .comment-next-ai-panel-command-icon {
    --at-apply: inline-flex h-8 w-8 flex-none items-center justify-center rounded-lg text-[var(--comment-next-ai-color,rgb(59,130,246))];
  }

  .comment-next-ai-panel-command-copy {
    --at-apply: min-w-0 flex-1;
  }

  .comment-next-ai-panel-command-label {
    --at-apply: block truncate text-[0.875rem] font-[720] leading-tight;
  }

  .comment-next-ai-panel-command-hint {
    --at-apply: mt-0.5 block truncate text-[0.75rem] text-[var(--comment-next-muted-color,#667085)] font-medium leading-tight;
  }

  .comment-next-ai-panel-command:hover,
  .comment-next-ai-panel-command-active {
    --at-apply: bg-[var(--comment-next-control-hover-bg-color,#eef2f4)] text-[var(--comment-next-ai-color,rgb(59,130,246))];
  }

  .comment-next-ai-panel-command:hover .comment-next-ai-panel-command-icon,
  .comment-next-ai-panel-command-active .comment-next-ai-panel-command-icon {
    --at-apply: bg-[var(--comment-next-ai-bg-color,rgb(239_246_255))];
  }

  .comment-next-ai-panel-command:active {
    --at-apply: translate-y-px;
  }

  .comment-next-ai-panel-command:disabled {
    --at-apply: cursor-wait opacity-64;
  }

  .comment-next-ai-panel-command:focus-visible,
  .comment-next-ai-panel-close:focus-visible {
    --at-apply: outline-2 outline-solid outline-offset-2 [outline-color:var(--comment-next-focus-ring-color,#80cbc0)];
  }

  @keyframes comment-next-ai-menu-in {
    from {
      opacity: 0;
      transform: translateY(0.375rem) scale(0.985);
    }

    to {
      opacity: 1;
      transform: translateY(0) scale(1);
    }
  }

  @keyframes comment-next-ai-sheet-in {
    from {
      opacity: 0;
      transform: translateY(1rem);
    }

    to {
      opacity: 1;
      transform: translateY(0);
    }
  }

  @media (max-width: 640px) {
    .comment-next-ai-panel-layer {
      --at-apply: fixed inset-0 z-[9999] w-auto pointer-events-none;
    }

    .comment-next-ai-panel-backdrop {
      --at-apply: absolute inset-0 block cursor-default border-0 bg-[rgb(15_23_42_/_0.28)] p-0 pointer-events-auto;
    }

    .comment-next-ai-panel {
      --at-apply: absolute left-3 right-3 bottom-3 w-auto rounded-[1.125rem] p-2 pointer-events-auto shadow-[0_24px_64px_rgb(15_23_42_/_0.28),0_1px_0_rgb(255_255_255_/_0.82)_inset];
      bottom: calc(0.75rem + env(safe-area-inset-bottom));
      animation: comment-next-ai-sheet-in 180ms cubic-bezier(0.2, 0.8, 0.2, 1);
    }

    .comment-next-ai-panel-list {
      --at-apply: gap-1;
    }

    .comment-next-ai-panel-command {
      --at-apply: min-h-14 px-3 py-2.5;
    }

    .comment-next-ai-panel-command-icon {
      --at-apply: h-9 w-9;
    }

  }

  @media (prefers-reduced-motion: reduce) {
    .comment-next-ai-panel,
    .comment-next-ai-panel-command,
    .comment-next-ai-panel-close {
      --at-apply: animate-none transition-none;
    }
  }
</style>
