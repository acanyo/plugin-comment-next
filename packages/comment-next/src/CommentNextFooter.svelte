<script lang="ts">
import { onMount, tick } from 'svelte';
import CommentNextAiPanel from './CommentNextAiPanel.svelte';
import CommentNextEmotePanel from './CommentNextEmotePanel.svelte';
import CommentNextEmotePreview from './CommentNextEmotePreview.svelte';
import CommentNextIcon from './CommentNextIcon.svelte';
import CommentNextMentionOption from './CommentNextMentionOption.svelte';
import CommentNextTooltip from './CommentNextTooltip.svelte';
import type { CommentNextAiMode } from './services/ai';
import type { CommentNextEmoteItem, CommentNextEmotePack } from './types/emote';
import { COMMENT_NEXT_MODAL_OPEN_EVENT } from './utils/overlays';

type CommentNextComposerVariant = 'comment' | 'reply';
type CommentNextEmotePreviewState = {
  item: CommentNextEmoteItem;
  packName: string;
  style: string;
};

const {
  commandMenuOpen = false,
  loggedIn = false,
  allowAnonymous = true,
  enablePrivate = true,
  submitting = false,
  submitDisabled = false,
  submitLabel = '提交',
  loginLabel = '登录后评论',
  aiLabel = 'AI 评论',
  aiAssistantName = '评论助手',
  aiAssistantMentionName = '',
  aiAssistantAvatar = '',
  aiMode = 'generate',
  aiLoading = false,
  variant = 'comment',
  compact = false,
  showAi = true,
  showMention = false,
  aiHasContent = false,
  aiHasSubject = false,
  aiHasReplyTarget = false,
  showInsertTools = true,
  showSubmitArea = true,
  imageUploadEnabled = false,
  imageUploading = false,
  imageAccept = 'image/*',
  emotePacks = [],
  emotePageSize,
  onToggleCommandMenu = () => {},
  onCloseCommandMenu = () => {},
  onCommandModeSelect = () => {},
  onMentionAssistant = () => {},
  onEmoteSelect = () => {},
  onImageUpload = () => {},
  onLogin = () => {},
}: {
  commandMenuOpen?: boolean;
  loggedIn?: boolean;
  allowAnonymous?: boolean;
  enablePrivate?: boolean;
  submitting?: boolean;
  submitDisabled?: boolean;
  submitLabel?: string;
  loginLabel?: string;
  aiLabel?: string;
  aiAssistantName?: string;
  aiAssistantMentionName?: string;
  aiAssistantAvatar?: string;
  aiMode?: CommentNextAiMode;
  aiLoading?: boolean;
  variant?: CommentNextComposerVariant;
  compact?: boolean;
  showAi?: boolean;
  showMention?: boolean;
  aiHasContent?: boolean;
  aiHasSubject?: boolean;
  aiHasReplyTarget?: boolean;
  showInsertTools?: boolean;
  showSubmitArea?: boolean;
  imageUploadEnabled?: boolean;
  imageUploading?: boolean;
  imageAccept?: string;
  emotePacks?: CommentNextEmotePack[];
  emotePageSize?: number;
  onToggleCommandMenu?: () => void;
  onCloseCommandMenu?: () => void;
  onCommandModeSelect?: (mode: CommentNextAiMode, tags: string[]) => void;
  onMentionAssistant?: () => void;
  onEmoteSelect?: (item: CommentNextEmoteItem) => void;
  onImageUpload?: (file: File) => Promise<void> | void;
  onLogin?: () => void;
} = $props();

const insertTools = [
  { key: 'smile', icon: 'smile', title: '表情' },
  { key: 'at', icon: 'at', title: '@ 提及' },
  { key: 'image', icon: 'image', title: '图片' },
];

// biome-ignore lint/style/useConst: Svelte assigns this through bind:this.
let footerElement = $state<HTMLDivElement | undefined>();
// biome-ignore lint/style/useConst: Svelte assigns this through bind:this.
let aiButtonElement = $state<HTMLButtonElement | undefined>();
// biome-ignore lint/style/useConst: Svelte assigns this through bind:this.
let imageInputElement = $state<HTMLInputElement | undefined>();
let emotePanelOpen = $state(false);
let mentionPanelOpen = $state(false);
let mentionPanelStyle = $state('');
let emotePanelStyle = $state('');
// biome-ignore lint/style/useConst: Updated by the preview callback in the template.
let emotePreview = $state<CommentNextEmotePreviewState | undefined>();
let aiPanelStyle = $state('');
let aiPanelHeight = $state(340);
let isMobileViewport = $state(false);

const hasEmotePacks = $derived(emotePacks.some((pack) => pack.items.length));
const visibleInsertTools = $derived(
  insertTools.filter((tool) => {
    if (tool.key === 'smile') {
      return showInsertTools && hasEmotePacks;
    }

    if (tool.key === 'at') {
      return showMention;
    }

    if (tool.key === 'image') {
      return showInsertTools && (imageUploadEnabled || imageUploading);
    }

    return true;
  })
);
const hasVisibleInsertTools = $derived(visibleInsertTools.length > 0);
const floatingEmotePanel = $derived(
  compact || !showSubmitArea || isMobileViewport
);

onMount(() => {
  const mobileMedia = window.matchMedia('(max-width: 780px)');
  const syncMobileViewport = () => {
    isMobileViewport = mobileMedia.matches;
  };
  const handleViewportChange = () => {
    syncMobileViewport();

    if (emotePanelOpen && floatingEmotePanel) {
      void updateEmotePanelPosition();
    }

    if (commandMenuOpen) {
      void updateAiPanelPosition();
    }
    if (mentionPanelOpen) void updateMentionPickerPosition();
  };

  const handlePointerDown = (event: PointerEvent) => {
    const path = event.composedPath();

    if (footerElement && path.includes(footerElement)) {
      return;
    }

    mentionPanelOpen = false;
    if (emotePanelOpen) {
      emotePanelOpen = false;
    }
    if (
      commandMenuOpen &&
      !path.some(
        (node) =>
          node instanceof Element &&
          node.classList.contains('comment-next-ai-panel-layer')
      )
    ) {
      onCloseCommandMenu();
    }
  };

  const handleKeyDown = (event: KeyboardEvent) => {
    if (event.key === 'Escape') {
      emotePanelOpen = false;
      mentionPanelOpen = false;
      onCloseCommandMenu();
    }
  };
  const handleModalOpen = () => {
    emotePanelOpen = false;
    mentionPanelOpen = false;
    onCloseCommandMenu();
  };

  syncMobileViewport();
  mobileMedia.addEventListener('change', syncMobileViewport);
  document.addEventListener('pointerdown', handlePointerDown, true);
  document.addEventListener('keydown', handleKeyDown);
  window.addEventListener(COMMENT_NEXT_MODAL_OPEN_EVENT, handleModalOpen);
  window.addEventListener('resize', handleViewportChange);
  window.addEventListener('scroll', handleViewportChange, true);

  return () => {
    mobileMedia.removeEventListener('change', syncMobileViewport);
    document.removeEventListener('pointerdown', handlePointerDown, true);
    document.removeEventListener('keydown', handleKeyDown);
    window.removeEventListener(COMMENT_NEXT_MODAL_OPEN_EVENT, handleModalOpen);
    window.removeEventListener('resize', handleViewportChange);
    window.removeEventListener('scroll', handleViewportChange, true);
  };
});

$effect(() => {
  if (!hasEmotePacks) {
    emotePanelOpen = false;
  }

  if (emotePanelOpen && floatingEmotePanel) {
    void updateEmotePanelPosition();
    return;
  }

  emotePanelStyle = '';
});

$effect(() => {
  if (commandMenuOpen) {
    void updateAiPanelPosition(aiPanelHeight);
    return;
  }

  aiPanelStyle = '';
});

function handleQuickActionClick() {
  emotePanelOpen = false;
  mentionPanelOpen = false;
  aiPanelHeight = 340;
  void updateAiPanelPosition();
  onToggleCommandMenu();
}

function handleInsertToolClick(key: string) {
  if (commandMenuOpen) {
    onCloseCommandMenu();
  }

  if (key === 'at') {
    emotePanelOpen = false;
    mentionPanelOpen = !mentionPanelOpen;
    if (mentionPanelOpen) void updateMentionPickerPosition();
    return;
  }

  mentionPanelOpen = false;

  if (key === 'smile') {
    if (!hasEmotePacks) {
      return;
    }

    emotePanelOpen = !emotePanelOpen;

    if (emotePanelOpen && floatingEmotePanel) {
      void updateEmotePanelPosition();
    }

    return;
  }

  if (key === 'image') {
    if (!imageUploadEnabled || imageUploading) {
      return;
    }

    emotePanelOpen = false;
    imageInputElement?.click();
  }
}

function handleEmoteSelect(item: CommentNextEmoteItem) {
  onEmoteSelect(item);
  emotePanelOpen = false;
}

function handleCommandModeSelect(mode: CommentNextAiMode, tags: string[]) {
  onCloseCommandMenu();
  onCommandModeSelect(mode, tags);
}

function handleImageFileChange(event: Event) {
  const input = event.currentTarget as HTMLInputElement;
  const file = input.files?.[0];
  input.value = '';

  if (file) {
    void onImageUpload(file);
  }
}

function isInsertToolDisabled(key: string): boolean {
  if (submitting || aiLoading) {
    return true;
  }

  if (key === 'smile') {
    return !hasEmotePacks;
  }

  if (key === 'image') {
    return !imageUploadEnabled || imageUploading;
  }

  return false;
}

async function updateEmotePanelPosition() {
  if (typeof window === 'undefined' || !footerElement) {
    return;
  }

  await tick();

  const footer = footerElement;

  if (!footer?.isConnected) {
    return;
  }

  const trigger = footer.querySelector<HTMLButtonElement>(
    '[data-comment-next-tool="smile"]'
  );

  if (!trigger) {
    return;
  }

  const rect = trigger.getBoundingClientRect();
  const viewportPadding = 16;
  const gap = 8;
  const preferredPanelHeight = 360;
  const minimumUsefulPanelHeight = 280;
  const panelWidth = Math.min(480, window.innerWidth - viewportPadding * 2);
  const availableAbove = rect.top - viewportPadding - gap;
  const availableBelow =
    window.innerHeight - rect.bottom - viewportPadding - gap;
  const shouldPreferAbove = variant === 'reply' || compact || !showSubmitArea;
  const openBelow =
    !shouldPreferAbove &&
    availableAbove < minimumUsefulPanelHeight &&
    availableBelow > availableAbove;
  const availableAboveHeight = shouldPreferAbove
    ? Math.max(minimumUsefulPanelHeight, availableAbove)
    : availableAbove;
  const availableHeight = Math.max(
    180,
    openBelow ? availableBelow : availableAboveHeight
  );
  const panelHeight = Math.min(
    preferredPanelHeight,
    window.innerHeight - viewportPadding * 2,
    availableHeight
  );
  const maxLeft = window.innerWidth - panelWidth - viewportPadding;
  const left = Math.min(Math.max(viewportPadding, rect.left), maxLeft);
  const top = openBelow ? rect.bottom + gap : rect.top - gap - panelHeight;

  emotePanelStyle = [
    `--comment-next-emote-fixed-left:${Math.max(viewportPadding, left)}px`,
    `--comment-next-emote-fixed-top:${Math.max(viewportPadding, top)}px`,
    `--comment-next-emote-fixed-width:${panelWidth}px`,
    `--comment-next-emote-fixed-height:${panelHeight}px`,
    `--comment-next-emote-fixed-max-height:${panelHeight}px`,
  ].join(';');
}

async function updateMentionPickerPosition() {
  await tick();
  const button = footerElement?.querySelector('[data-comment-next-tool="at"]');
  if (!button) return;
  const rect = button.getBoundingClientRect();
  const width = Math.min(344, window.innerWidth - 24);
  const left = Math.min(
    Math.max(12, rect.left),
    window.innerWidth - width - 12
  );
  const top =
    window.innerHeight - rect.bottom > 100 ? rect.bottom + 8 : rect.top - 92;
  mentionPanelStyle = `left:${left}px;top:${Math.max(12, top)}px;width:${width}px`;
}

async function updateAiPanelPosition(height = aiPanelHeight) {
  if (typeof window === 'undefined' || !aiButtonElement) {
    return;
  }

  await tick();

  const trigger = aiButtonElement;

  if (!trigger?.isConnected) {
    return;
  }

  const rect = trigger.getBoundingClientRect();
  const viewportPadding = 16;
  const gap = 8;
  const panelWidth = Math.min(400, window.innerWidth - viewportPadding * 2);
  const availableAbove = rect.top - viewportPadding - gap;
  const availableBelow =
    window.innerHeight - rect.bottom - viewportPadding - gap;
  const openBelow = availableBelow >= 280 || availableBelow >= availableAbove;
  const maxHeight = Math.max(220, openBelow ? availableBelow : availableAbove);
  const panelHeight = Math.min(height, maxHeight);
  const maxLeft = window.innerWidth - panelWidth - viewportPadding;
  const left = Math.min(Math.max(viewportPadding, rect.left), maxLeft);
  const top = openBelow ? rect.bottom + gap : rect.top - gap - panelHeight;

  aiPanelStyle = [
    `--comment-next-ai-panel-left:${Math.max(viewportPadding, left)}px`,
    `--comment-next-ai-panel-top:${Math.max(viewportPadding, top)}px`,
    `--comment-next-ai-panel-bottom:auto`,
    `--comment-next-ai-panel-max-height:${maxHeight}px`,
  ].join(';');
}
</script>

<div bind:this={footerElement} class:comment-next-footer-compact={compact} class="comment-next-footer">
  <div class:comment-next-toolbar-visible={hasVisibleInsertTools || showAi} class="comment-next-footer-left">
    {#if hasVisibleInsertTools}
    <div class="comment-next-insert-tools-region">
      {#if emotePanelOpen && hasEmotePacks}
        <CommentNextEmotePanel
          fixed={floatingEmotePanel}
          panelStyle={emotePanelStyle}
          packs={emotePacks}
          pageSize={emotePageSize}
          onSelect={handleEmoteSelect}
          onPreviewChange={(entry) => (emotePreview = entry)}
        />
      {/if}

      <div class="comment-next-insert-tools" aria-label="插入工具栏">
        {#if imageUploadEnabled || imageUploading}
        <input
          bind:this={imageInputElement}
          class="comment-next-upload-input"
          type="file"
          accept={imageAccept}
          disabled={submitting || aiLoading}
          onchange={handleImageFileChange}
        />
        {/if}
        {#each visibleInsertTools as tool}
          <CommentNextTooltip text={tool.title} disabled={tool.key === "at" && mentionPanelOpen}>
            <button
              class:comment-next-tool-button-active={(tool.key === "smile" && emotePanelOpen) || (tool.key === "image" && imageUploading)}
              class="comment-next-tool-button"
              type="button"
              data-comment-next-tool={tool.key}
              aria-label={tool.key === "at" ? `@${aiAssistantName}，邀请助手回复` : tool.title}
              aria-expanded={tool.key === "smile" ? emotePanelOpen : tool.key === "at" ? mentionPanelOpen : undefined}
              aria-haspopup={tool.key === "at" ? "listbox" : undefined}
              aria-busy={tool.key === "image" && imageUploading ? "true" : undefined}
              disabled={isInsertToolDisabled(tool.key)}
              onpointerdown={(event) => {
                if (tool.key === "at") event.preventDefault();
              }}
              onclick={() => handleInsertToolClick(tool.key)}
            >
              <CommentNextIcon name={tool.key === "image" && imageUploading ? "loader" : tool.icon} size={20} />
            </button>
          </CommentNextTooltip>
        {/each}
      </div>
    </div>
    {#if emotePreview}
      <CommentNextEmotePreview
        item={emotePreview.item}
        packName={emotePreview.packName}
        style={emotePreview.style}
      />
    {/if}
    {/if}

    {#if mentionPanelOpen && showMention}
      <div class="comment-next-mention-picker" style={mentionPanelStyle} role="listbox" aria-label="AI 助手候选">
        <CommentNextMentionOption
          mentionName={aiAssistantMentionName || `@${aiAssistantName}`}
          avatar={aiAssistantAvatar}
          onSelect={() => {
            mentionPanelOpen = false;
            onMentionAssistant();
          }}
        />
      </div>
    {/if}

    {#if showAi}
    <div class="comment-next-quick-actions">
      {#if commandMenuOpen}
        <CommentNextAiPanel
          activeMode={aiMode}
          loading={aiLoading}
          {variant}
          assistantName={aiAssistantName}
          panelStyle={aiPanelStyle}
          hasContent={aiHasContent}
          hasSubject={aiHasSubject}
          hasReplyTarget={aiHasReplyTarget}
          onHeightChange={(height) => (aiPanelHeight = height)}
          onGenerate={handleCommandModeSelect}
          onClose={() => {
            onCloseCommandMenu();
            aiButtonElement?.focus({ preventScroll: true });
          }}
        />
      {/if}

      <button
        bind:this={aiButtonElement}
        class:comment-next-quick-button-active={commandMenuOpen}
        class="comment-next-quick-button"
        type="button"
        aria-label="AI 评论"
        aria-haspopup="dialog"
        aria-expanded={commandMenuOpen}
        disabled={submitting || aiLoading}
        onclick={handleQuickActionClick}
      >
        <CommentNextIcon name="messageSparkle" size={21} />
        <span>{aiLabel}</span>
      </button>
    </div>
    {/if}


  </div>

  {#if showSubmitArea}
  <div class="comment-next-submit-area">
    {#if enablePrivate && loggedIn}
      <div class="comment-next-private-control">
        <CommentNextTooltip text="仅自己和管理员可见" align="end" mobileAlign="start">
          <label class="comment-next-private-option">
            <input class="comment-next-private-input" type="checkbox" name="hidden" aria-label="私密评论" />
            <span class="comment-next-private-chip">
              <CommentNextIcon name="lock" size={14} />
              <span>私密</span>
            </span>
          </label>
        </CommentNextTooltip>
      </div>
    {/if}

    <button
      class="comment-next-submit-button"
      type={!loggedIn && !allowAnonymous ? "button" : "submit"}
      disabled={submitting || submitDisabled}
      onclick={!loggedIn && !allowAnonymous ? onLogin : undefined}
    >
      {#if submitting}
        <span class="comment-next-loading-icon">
          <CommentNextIcon name="loader" size={17} />
        </span>
      {:else if !loggedIn && !allowAnonymous}
        <CommentNextIcon name="login" size={17} />
      {:else}
        <CommentNextIcon name="send" size={17} />
      {/if}
      {!loggedIn && !allowAnonymous ? loginLabel : submitLabel}
    </button>
  </div>
  {/if}
</div>

<style>
  .comment-next-footer {
    --at-apply: flex min-h-14 box-border items-center justify-between gap-3 border-t [border-top-style:var(--comment-next-divider-style,dashed)] [border-top-color:var(--comment-next-divider-color,#d4dde8)] rounded-b-[var(--comment-next-radius-lg,0.875rem)] [background:var(--comment-next-footer-surface-bg,transparent,var(--comment-next-footer-bg-color,#fbfcfc))] px-3.5 py-0;
    max-width: 100%;
    min-width: 0;
  }

  .comment-next-footer-left,
  .comment-next-quick-actions,
  .comment-next-insert-tools-region,
  .comment-next-insert-tools,
  .comment-next-submit-area,
  .comment-next-private-control,
  .comment-next-private-option {
    --at-apply: flex items-center;
  }

  .comment-next-quick-actions,
  .comment-next-insert-tools,
  .comment-next-submit-area {
    --at-apply: gap-2;
  }

  .comment-next-quick-actions {
    --at-apply: relative;
  }

  .comment-next-mention-picker {
    --at-apply: fixed z-[2147482991] box-border rounded-[0.875rem] border border-solid [border-color:var(--comment-next-menu-border-color,#d5dde7)] bg-[var(--comment-next-menu-bg-color,#ffffff)] p-2 shadow-[0_12px_36px_rgb(15_23_42_/_0.14)];
  }

  .comment-next-footer-left {
    --at-apply: min-w-0 gap-0.5;
    max-width: 100%;
  }

  .comment-next-toolbar-visible {
    --at-apply: rounded-full border border-solid [border-color:var(--comment-next-border-subtle-color,#dfe5ec)] bg-[var(--comment-next-toolbar-bg-color,#ffffff)] p-[0.1875rem];
    max-width: 100%;
  }

  .comment-next-submit-area {
    max-width: 100%;
    min-width: 0;
  }

  .comment-next-insert-tools-region {
    --at-apply: relative min-w-0;
  }

  .comment-next-footer-compact {
    --at-apply: min-h-12 px-2.5 py-0;
  }

  .comment-next-footer-compact .comment-next-quick-button {
    --at-apply: h-[1.9375rem] min-w-0 px-2.5 py-0 text-[0.8125rem];
  }

  .comment-next-footer-compact .comment-next-submit-button {
    --at-apply: h-8 min-w-18 px-3.5 py-0 text-sm;
  }

  .comment-next-quick-button,
  .comment-next-tool-button,
  .comment-next-submit-button {
    --at-apply: font-inherit;
  }

  .comment-next-quick-button {
    --at-apply: inline-flex h-8 w-auto max-w-36 min-w-0 cursor-pointer items-center justify-center gap-[0.3rem] rounded-full border-0 bg-transparent px-3 py-0 text-sm text-[var(--comment-next-icon-color,#39445d)] font-[650] transition-[background-color,color,border-color,box-shadow,transform] duration-140 ease-in-out;
  }

  .comment-next-quick-button span {
    --at-apply: min-w-0 truncate;
  }

  .comment-next-quick-button:hover,
  .comment-next-quick-button-active {
    --at-apply: [border-color:var(--comment-next-primary-color,rgb(59,130,246))] bg-[var(--comment-next-pill-active-bg-color,rgb(239_246_255))] text-[var(--comment-next-primary-color,rgb(59,130,246))];
  }

  .comment-next-quick-button:active {
    --at-apply: translate-y-px;
  }

  .comment-next-quick-button:disabled {
    --at-apply: cursor-not-allowed opacity-45;
  }

  .comment-next-insert-tools {
    --at-apply: min-w-0 overflow-visible gap-0.5;
  }

  .comment-next-tool-button {
    --at-apply: inline-flex h-8 w-8 cursor-pointer items-center justify-center rounded-[0.4375rem] border-0 bg-transparent p-0 text-[var(--comment-next-icon-color,#39445d)] transition-[background-color,color,transform] duration-140 ease-in-out;
  }

  .comment-next-tool-button:hover {
    --at-apply: bg-[var(--comment-next-control-hover-bg-color,#eef2f4)] text-[var(--comment-next-text-color,#172033)];
  }

  .comment-next-tool-button:disabled {
    --at-apply: cursor-not-allowed opacity-45;
  }

  .comment-next-tool-button-active {
    --at-apply: bg-[var(--comment-next-pill-active-bg-color,rgb(239_246_255))] text-[var(--comment-next-primary-color,rgb(59,130,246))];
  }

  .comment-next-tool-button:active {
    --at-apply: translate-y-px;
  }

  .comment-next-tool-button[aria-busy="true"] :global(.comment-next-icon) {
    --at-apply: animate-spin;
  }

  .comment-next-upload-input {
    --at-apply: sr-only;
  }

  .comment-next-private-control {
    --at-apply: gap-1;
  }

  .comment-next-private-option {
    --at-apply: h-8 cursor-pointer whitespace-nowrap;
  }

  .comment-next-private-input {
    --at-apply: sr-only;
  }

  .comment-next-private-chip {
    --at-apply: inline-flex h-8 box-border items-center justify-center gap-1.5 rounded-[0.5625rem] border border-solid border-transparent bg-transparent px-2.5 py-0 text-sm text-[var(--comment-next-muted-color,#6b7687)] font-[650] transition-[background-color,border-color,color,box-shadow,transform] duration-140 ease-in-out;
  }

  .comment-next-private-option:hover .comment-next-private-chip {
    --at-apply: bg-[var(--comment-next-control-hover-bg-color,#eef2f4)] text-[var(--comment-next-text-color,#172033)];
  }

  .comment-next-private-input:checked + .comment-next-private-chip {
    --at-apply: [border-color:rgb(147_197_253_/_0.55)] bg-[var(--comment-next-pill-active-bg-color,rgb(239_246_255))] text-[var(--comment-next-primary-color,rgb(59,130,246))] shadow-[0_5px_14px_rgb(59_130_246_/_0.10)];
  }

  .comment-next-private-input:focus-visible + .comment-next-private-chip {
    --at-apply: outline-2 outline-solid outline-offset-2 [outline-color:var(--comment-next-focus-ring-color,rgb(147_197_253))];
  }

  .comment-next-private-input:active + .comment-next-private-chip {
    --at-apply: translate-y-px;
  }

  .comment-next-submit-button {
    --at-apply: inline-flex h-9 min-w-[5.75rem] cursor-pointer items-center justify-center gap-[0.45rem] rounded-[0.5625rem] border-0 bg-[var(--comment-next-primary-color,#172033)] px-4 py-0 text-[0.9375rem] text-white font-[650] shadow-[0_9px_18px_rgb(23_32_51_/_0.14)] transition-[background-color,box-shadow,transform] duration-140 ease-in-out;
  }

  .comment-next-submit-button:hover {
    --at-apply: bg-[var(--comment-next-primary-hover-color,rgb(59,130,246))] shadow-[0_10px_20px_rgb(59_130_246_/_0.18)];
  }

  .comment-next-submit-button:active {
    --at-apply: translate-y-px;
  }

  .comment-next-submit-button:focus-visible,
  .comment-next-quick-button:focus-visible,
  .comment-next-tool-button:focus-visible {
    --at-apply: outline-2 outline-solid outline-offset-2 [outline-color:var(--comment-next-focus-ring-color,rgb(147_197_253))];
  }

  .comment-next-submit-button:disabled {
    --at-apply: cursor-not-allowed opacity-72;
  }

  .comment-next-loading-icon {
    --at-apply: animate-spin;
  }

  @media (max-width: 780px) {
    .comment-next-footer {
      --at-apply: grid min-h-0 grid-cols-[minmax(0,1fr)_auto] items-center gap-x-3 gap-y-2 px-3 py-2.5;
    }

    .comment-next-footer-left {
      --at-apply: min-w-0 flex-wrap gap-2;
    }

    .comment-next-submit-area {
      --at-apply: min-w-0 justify-end gap-2;
    }

    .comment-next-insert-tools-region {
      --at-apply: w-auto;
    }

    .comment-next-insert-tools {
      --at-apply: overflow-visible;
    }

    .comment-next-quick-button {
      --at-apply: h-8 min-w-0 px-2.5 text-[0.8125rem];
    }

    .comment-next-submit-button {
      --at-apply: h-8 min-w-18 px-3.5 text-sm;
    }

    .comment-next-private-chip {
      --at-apply: h-8 px-2 text-[0.8125rem];
    }
  }

  @media (max-width: 420px) {
    .comment-next-footer {
      --at-apply: grid-cols-1;
    }

    .comment-next-submit-area {
      --at-apply: w-full justify-between;
    }
  }

  @media (prefers-reduced-motion: reduce) {
    .comment-next-quick-button,
    .comment-next-tool-button,
    .comment-next-submit-button {
      --at-apply: transition-none;
    }

    .comment-next-loading-icon {
      --at-apply: animate-none;
    }
  }
</style>
