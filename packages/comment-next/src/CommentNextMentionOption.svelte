<script lang="ts">
import CommentNextAvatar from './CommentNextAvatar.svelte';

const {
  mentionName = '@评论助手',
  avatar = '',
  onSelect = () => {},
}: {
  mentionName?: string;
  avatar?: string;
  onSelect?: () => void;
} = $props();
</script>

<button
  class="comment-next-mention-option"
  type="button"
  role="option"
  aria-selected="true"
  onpointerdown={(event) => event.preventDefault()}
  onclick={onSelect}
>
  <CommentNextAvatar src={avatar} seed={mentionName} alt="" size={34} />
  <span class="comment-next-mention-option-copy">
    <strong>{mentionName}</strong>
    <small>AI 助手，可以帮你解答问题</small>
  </span>
</button>

<style>
  .comment-next-mention-option {
    --at-apply: flex min-h-14 w-full box-border cursor-pointer items-center gap-3 rounded-[0.6875rem] border-0 bg-[var(--comment-next-field-bg-color,#f7f8fa)] px-3 py-2.5 text-left text-[var(--comment-next-text-color,#172033)] font-inherit transition-colors duration-120;
  }
  .comment-next-mention-option:hover,
  .comment-next-mention-option:focus-visible {
    --at-apply: bg-[var(--comment-next-ai-bg-color,#eff6ff)] outline-2 outline-offset-2 [outline-color:var(--comment-next-focus-ring-color,#93c5fd)];
  }
  .comment-next-mention-option-copy {
    --at-apply: flex min-w-0 flex-1 flex-col gap-0.5;
  }
  .comment-next-mention-option strong {
    --at-apply: truncate text-sm font-[700] leading-snug;
  }
  .comment-next-mention-option small {
    --at-apply: text-xs text-[var(--comment-next-muted-color,#76808f)] leading-snug;
  }
</style>
