<script lang="ts">
import type { Snippet } from 'svelte';
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
import { anchoredPanelStyle, openTopLayer } from './utils/top-layer';

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
  privateComment = false,
  submitting = false,
  submitDisabled = false,
  submitLabel = '发布评论',
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
  identity,
  onToggleCommandMenu = () => {},
  onCloseCommandMenu = () => {},
  onCommandModeSelect = () => {},
  onMentionAssistant = () => {},
  onEmoteSelect = () => {},
  onImageUpload = () => {},
  onLogin = () => {},
  onPrivateChange = () => {},
}: {
  commandMenuOpen?: boolean;
  loggedIn?: boolean;
  allowAnonymous?: boolean;
  enablePrivate?: boolean;
  privateComment?: boolean;
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
  identity?: Snippet;
  onToggleCommandMenu?: () => void;
  onCloseCommandMenu?: () => void;
  onCommandModeSelect?: (mode: CommentNextAiMode, tags: string[]) => void;
  onMentionAssistant?: () => void;
  onEmoteSelect?: (item: CommentNextEmoteItem) => void;
  onImageUpload?: (file: File) => Promise<void> | void;
  onLogin?: () => void;
  onPrivateChange?: (checked: boolean) => void;
} = $props();

const insertTools = [
  { key: 'smile', icon: 'smile', title: '表情' },
  { key: 'image', icon: 'image', title: '图片' },
  { key: 'at', icon: 'at', title: '@ 提及' },
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
let selectedEmoteId = $state('');
let emotePanelStyle = $state('');
// biome-ignore lint/style/useConst: Updated by the preview callback in the template.
let emotePreview = $state<CommentNextEmotePreviewState | undefined>();
let aiPanelStyle = $state('');
let aiPanelHeight = $state(340);

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
const hasTools = $derived(hasVisibleInsertTools || showAi);
onMount(() => {
  const handleViewportChange = () => {
    if (emotePanelOpen) {
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

  const composer = footerElement?.closest('form');
  const observer =
    composer && typeof ResizeObserver === 'function'
      ? new ResizeObserver(handleViewportChange)
      : undefined;
  if (composer) observer?.observe(composer);
  document.addEventListener('pointerdown', handlePointerDown, true);
  document.addEventListener('keydown', handleKeyDown);
  window.addEventListener(COMMENT_NEXT_MODAL_OPEN_EVENT, handleModalOpen);
  window.addEventListener('resize', handleViewportChange);
  window.addEventListener('scroll', handleViewportChange, true);

  return () => {
    observer?.disconnect();
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

  if (emotePanelOpen) {
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
  selectedEmoteId = item.id;
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
  const anchorTop = openBelow
    ? rect.bottom + gap
    : rect.top - gap - panelHeight;
  const top = Math.min(
    anchorTop,
    window.innerHeight - panelHeight - viewportPadding
  );

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
  mentionPanelStyle = anchoredPanelStyle(
    button.getBoundingClientRect(),
    344,
    84
  );
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

{#if identity || hasTools || showSubmitArea}
  <div bind:this={footerElement} class="comment-next-footer comment-next-composer-action-row">
    {#if identity || hasTools}
      <div class="comment-next-footer-left">
        {#if identity}
          <div class="comment-next-footer-identity">{@render identity()}</div>
        {/if}
        {#if hasTools}
          <div class="comment-next-footer-tools">
            {#if hasVisibleInsertTools}
              <div class="comment-next-insert-tools-region">
                {#if emotePanelOpen && hasEmotePacks}
                  <CommentNextEmotePanel
                    panelStyle={emotePanelStyle}
                    packs={emotePacks}
                    pageSize={emotePageSize}
                    selectedItemId={selectedEmoteId}
                    onSelect={handleEmoteSelect}
                    onPreviewChange={(entry) => (emotePreview = entry)}
                  />
                {/if}

                <div class="comment-next-insert-tools" role="group" aria-label="插入工具栏">
                  {#if imageUploadEnabled || imageUploading}
                    <input
                      bind:this={imageInputElement}
                      type="file"
                      accept={imageAccept}
                      hidden
                      disabled={submitting || aiLoading}
                      onchange={handleImageFileChange}
                    />
                  {/if}
                  {#each visibleInsertTools as tool (tool.key)}
                    <CommentNextTooltip text={tool.title} disabled={tool.key === 'at' && mentionPanelOpen}>
                      <button
                        class:comment-next-tool-button-active={(tool.key === 'smile' && emotePanelOpen) || (tool.key === 'image' && imageUploading)}
                        class="comment-next-tool-button"
                        type="button"
                        data-comment-next-tool={tool.key}
                        aria-label={tool.key === 'at' ? `@${aiAssistantName}，邀请助手回复` : tool.title}
                        aria-expanded={tool.key === 'smile' ? emotePanelOpen : tool.key === 'at' ? mentionPanelOpen : undefined}
                        aria-haspopup={tool.key === 'at' ? 'listbox' : undefined}
                        aria-busy={tool.key === 'image' && imageUploading ? true : undefined}
                        disabled={isInsertToolDisabled(tool.key)}
                        onpointerdown={(event) => {
                          if (tool.key === 'at') event.preventDefault();
                        }}
                        onclick={() => handleInsertToolClick(tool.key)}
                      >
                        <CommentNextIcon name={tool.key === 'image' && imageUploading ? 'loader' : tool.icon} size={17} />
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
              <div use:openTopLayer class="comment-next-mention-picker" style={mentionPanelStyle} role="listbox" aria-label="AI 助手候选">
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
                  aria-label={aiLabel}
                  title={aiLabel}
                  aria-haspopup="dialog"
                  aria-expanded={commandMenuOpen}
                  disabled={submitting || aiLoading}
                  onclick={handleQuickActionClick}
                >
                  <CommentNextIcon name="sparkle" size={16} />
                  <span>{aiLabel}</span>
                </button>
              </div>
            {/if}
          </div>
        {/if}
      </div>
    {/if}

    {#if showSubmitArea}
      <div class="comment-next-submit-area">
        {#if enablePrivate && loggedIn}
          <CommentNextTooltip text="仅自己和管理员可见" align="end">
            <label class="comment-next-private-option">
              <input
                class="comment-next-private-input"
                type="checkbox"
                name="hidden"
                aria-label="私密评论"
                checked={privateComment}
                disabled={submitting}
                onchange={(event) => onPrivateChange(event.currentTarget.checked)}
              />
              <span class="comment-next-private-chip">
                <CommentNextIcon name={privateComment ? 'check' : 'lock'} size={15} />
                <span class="comment-next-private-label">私密</span>
              </span>
            </label>
          </CommentNextTooltip>
        {/if}

        <button
          class="comment-next-submit-button"
          type={!loggedIn && !allowAnonymous ? 'button' : 'submit'}
          disabled={submitting || submitDisabled}
          aria-busy={submitting || submitDisabled || undefined}
          onclick={!loggedIn && !allowAnonymous ? onLogin : undefined}
        >
          <span>{!loggedIn && !allowAnonymous ? loginLabel : submitLabel}</span>
          <span class="comment-next-submit-icon" class:comment-next-loading-icon={submitting || submitDisabled}>
            <CommentNextIcon
              name={submitting || submitDisabled ? 'loader' : !loggedIn && !allowAnonymous ? 'login' : 'send'}
              size={14}
            />
          </span>
        </button>
      </div>
    {/if}
  </div>
{/if}

<style>
  .comment-next-footer {
    display: flex;
    flex-wrap: wrap;
    align-items: center;
    gap: 0.3125rem 0.625rem;
    box-sizing: border-box;
    min-width: 0;
  }

  .comment-next-footer-left,
  .comment-next-footer-identity,
  .comment-next-footer-tools,
  .comment-next-quick-actions,
  .comment-next-insert-tools,
  .comment-next-submit-area {
    display: flex;
    align-items: center;
    min-width: 0;
  }

  .comment-next-footer-left,
  .comment-next-footer-tools,
  .comment-next-submit-area {
    flex-wrap: wrap;
    max-width: 100%;
  }

  .comment-next-footer-left,
  .comment-next-footer-tools {
    gap: 0.25rem 0.1875rem;
  }

  .comment-next-submit-area {
    justify-content: flex-end;
    gap: 0.3125rem;
    margin-inline-start: auto;
  }

  .comment-next-insert-tools-region,
  .comment-next-quick-actions {
    position: relative;
    min-width: 0;
  }

  .comment-next-mention-picker {
    --at-apply: fixed z-[2147482991] box-border rounded-[0.875rem] border border-solid [border-color:var(--comment-next-menu-border-color,#d5dde7)] bg-[var(--comment-next-menu-bg-color,#ffffff)] p-2 shadow-[0_12px_36px_rgb(15_23_42_/_0.14)];
    inset: auto;
    margin: 0;
  }

  .comment-next-tool-button,
  .comment-next-quick-button,
  .comment-next-private-chip,
  .comment-next-submit-button {
    display: inline-flex;
    align-items: center;
    justify-content: center;
    box-sizing: border-box;
    min-height: var(--comment-next-composer-control-height);
    gap: 0.375rem;
    padding: 0 0.5rem;
    border: 1px solid transparent;
    border-radius: var(--comment-next-composer-control-radius);
    background: transparent;
    color: var(--comment-next-muted-color, #667085);
    font: inherit;
    font-size: 0.8125rem;
    font-weight: 600;
    line-height: 1.2;
    cursor: pointer;
    transition: background-color 140ms ease, border-color 140ms ease, color 140ms ease;
  }

  .comment-next-tool-button {
    min-width: var(--comment-next-composer-tool-width, var(--comment-next-composer-control-height));
    padding-inline: 0;
  }

  .comment-next-tool-button:where(:hover:not(:disabled)),
  .comment-next-quick-button:where(:hover:not(:disabled)),
  .comment-next-private-input:where(:not(:disabled)) + .comment-next-private-chip:where(:hover) {
    background: var(--comment-next-control-hover-bg-color, #eef2f4);
    color: var(--comment-next-text-color, #172033);
  }

  .comment-next-quick-button {
    min-width: 0;
    max-width: 10rem;
  }

  .comment-next-quick-button span {
    overflow: hidden;
    white-space: nowrap;
    text-overflow: ellipsis;
  }

  .comment-next-quick-button-active,
  .comment-next-tool-button-active,
  .comment-next-private-input:checked + .comment-next-private-chip {
    background: var(--comment-next-composer-tint);
    color: var(--comment-next-composer-action-ink);
  }

  .comment-next-private-input {
    --at-apply: sr-only;
  }

  .comment-next-private-option {
    display: inline-flex;
  }

  .comment-next-private-chip {
    white-space: nowrap;
  }

  .comment-next-submit-button {
    flex: none;
    gap: 0.4375rem;
    max-width: 100%;
    padding-inline: 0.6875rem;
    background: var(--comment-next-composer-tint);
    color: var(--comment-next-composer-action-ink);
    font-weight: 650;
  }

  .comment-next-submit-icon {
    display: inline-flex;
    flex: none;
  }

  .comment-next-submit-button:hover:not(:disabled) {
    background: var(--comment-next-composer-action-hover);
  }

  .comment-next-submit-button:active:not(:disabled) {
    background: var(--comment-next-composer-submit-bg);
    color: var(--comment-next-composer-submit-text, var(--comment-next-composer-on-accent, #fff));
  }

  @supports (color: light-dark(white, black)) {
    .comment-next-submit-button {
      background: light-dark(
        var(--comment-next-style-light-accent, var(--comment-next-composer-tint)),
        var(--comment-next-style-dark-accent, var(--comment-next-composer-tint))
      );
      color: light-dark(
        var(--comment-next-style-light-ink, var(--comment-next-composer-action-ink)),
        var(--comment-next-style-dark-ink, var(--comment-next-composer-action-ink))
      );
    }

    .comment-next-submit-button:hover:not(:disabled) {
      background: light-dark(
        var(--comment-next-style-light-accent, var(--comment-next-composer-action-hover)),
        var(--comment-next-style-dark-accent, var(--comment-next-composer-action-hover))
      );
    }

    .comment-next-submit-button:active:not(:disabled) {
      background: light-dark(
        var(--comment-next-style-light-accent, var(--comment-next-composer-submit-bg)),
        var(--comment-next-style-dark-accent, var(--comment-next-composer-submit-bg))
      );
      color: light-dark(
        var(--comment-next-style-light-ink, var(--comment-next-composer-submit-text, var(--comment-next-composer-on-accent, #fff))),
        var(--comment-next-style-dark-ink, var(--comment-next-composer-submit-text, var(--comment-next-composer-on-accent, #fff)))
      );
    }
  }

  .comment-next-submit-button:focus-visible,
  .comment-next-quick-button:focus-visible,
  .comment-next-tool-button:focus-visible,
  .comment-next-private-input:focus-visible + .comment-next-private-chip {
    outline: 2px solid var(--comment-next-composer-accent);
    outline-offset: 2px;
  }

  .comment-next-submit-button:disabled,
  .comment-next-quick-button:disabled,
  .comment-next-tool-button:disabled,
  .comment-next-private-input:disabled + .comment-next-private-chip {
    cursor: not-allowed;
    opacity: 0.55;
  }

  .comment-next-tool-button[aria-busy="true"] :global(.comment-next-icon),
  .comment-next-loading-icon {
    --at-apply: animate-spin;
  }

  @container comment-next-composer (max-width: 28rem) {
    .comment-next-quick-button {
      min-width: var(--comment-next-composer-tool-width, var(--comment-next-composer-control-height));
      padding: 0;
    }

    .comment-next-quick-button span {
      display: none;
    }

    .comment-next-footer {
      column-gap: 0.3125rem;
    }
  }

  @container comment-next-composer (max-width: 24rem) {
    .comment-next-footer,
    .comment-next-submit-area {
      column-gap: 0.1875rem;
    }

    .comment-next-footer-left,
    .comment-next-footer-tools {
      column-gap: 0.125rem;
    }

    .comment-next-submit-button {
      padding-inline: 0.625rem;
    }

    .comment-next-private-chip {
      min-width: var(--comment-next-composer-tool-width);
      padding-inline: 0;
    }

    .comment-next-private-label,
    .comment-next-submit-icon {
      display: none;
    }
  }

  @media (prefers-reduced-motion: reduce) {
    .comment-next-tool-button,
    .comment-next-quick-button,
    .comment-next-private-chip,
    .comment-next-submit-button {
      transition: none;
    }

    .comment-next-tool-button[aria-busy="true"] :global(.comment-next-icon),
    .comment-next-loading-icon {
      animation: none;
    }
  }
</style>
