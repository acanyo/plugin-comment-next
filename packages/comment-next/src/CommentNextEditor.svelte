<script lang="ts">
import { tick } from 'svelte';
import CommentNextAiSuggestion from './CommentNextAiSuggestion.svelte';
import CommentNextIcon from './CommentNextIcon.svelte';
import {
  autolinkUrls,
  getTextSelectionOffset,
  restoreTextSelectionOffset,
} from './utils/autolink';
import { isImageFile } from './utils/image-files';

type PendingCommandTrigger = {
  startOffset: number;
  endOffset: number;
};

type PendingMentionTrigger = PendingCommandTrigger & {
  query: string;
};

const COMMAND_TRIGGER_PATTERN = /(?:^|\s)(\/[^\s/@]*)$/;
const MENTION_TRIGGER_PATTERN = /(?:^|\s)(@([^\s@/]*)?)$/;

const {
  placeholder = '写下你的评论...',
  aiOpen = false,
  inlineSuggestion = false,
  selectionTools = false,
  aiMode = 'polish',
  suggestionText = '',
  suggestionLoading = false,
  aiAssistantName = '评论助手',
  aiAssistantMentionName = '',
  aiMentionEnabled = true,
  allowImages = true,
  topRounded = false,
  onChange = () => {},
  onImagePaste = () => {},
  onCommandMenuRequest = () => {},
  onCloseAiPanel = () => {},
  onAcceptSuggestion = () => {},
  onInsertSuggestion = () => {},
  onRewriteSuggestion = () => {},
  onRejectSuggestion = () => {},
}: {
  placeholder?: string;
  aiOpen?: boolean;
  inlineSuggestion?: boolean;
  selectionTools?: boolean;
  aiMode?: string;
  suggestionText?: string;
  suggestionLoading?: boolean;
  aiAssistantName?: string;
  aiAssistantMentionName?: string;
  aiMentionEnabled?: boolean;
  allowImages?: boolean;
  topRounded?: boolean;
  onChange?: (html: string) => void;
  onImagePaste?: (files: File[]) => Promise<void> | void;
  onCommandMenuRequest?: () => void;
  onCloseAiPanel?: () => void;
  onAcceptSuggestion?: () => void;
  onInsertSuggestion?: () => void;
  onRewriteSuggestion?: () => void;
  onRejectSuggestion?: () => void;
} = $props();

let editorWrapElement: HTMLDivElement | undefined;
let editorElement: HTMLDivElement | undefined;
let autolinkTimer: number | undefined;
let pendingCommandTrigger = $state<PendingCommandTrigger | undefined>();
let pendingMentionTrigger = $state<PendingMentionTrigger | undefined>();
let mentionPanelStyle = $state('');

const resolvedMentionName = $derived(
  normalizeMentionName(aiAssistantMentionName || aiAssistantName)
);
const mentionSuggestionVisible = $derived(
  Boolean(
    aiMentionEnabled &&
      pendingMentionTrigger &&
      assistantMentionMatchesQuery(pendingMentionTrigger.query)
  )
);

export function getHtml(): string {
  return getSerializableEditorHtml();
}

export function getText(): string {
  return getSerializableEditorText();
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

export function replaceText(value: string) {
  if (!editorElement) {
    return;
  }

  editorElement.textContent = value;
  onChange(getHtml());
  editorElement.focus();
}

export function insertImage(src: string, alt = '') {
  if (!allowImages || !src || !editorElement) {
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

export function insertHtml(value: string) {
  if (!value || !editorElement) {
    return;
  }

  editorElement.focus();
  document.execCommand('insertHTML', false, value);
  if (!allowImages) {
    removeEditorImages();
  }
  onChange(getHtml());
}

export function runCommand(command: string, value?: string) {
  if (!command || !editorElement) {
    return;
  }

  editorElement.focus();
  document.execCommand(command, false, value);
  if (!allowImages) {
    removeEditorImages();
  }
  onChange(getHtml());
}

export function consumeCommandTrigger() {
  consumePendingCommandTrigger();
}

function handleEditorInput() {
  if (!allowImages) {
    removeEditorImages();
  }

  onChange(getHtml());

  if (inlineSuggestion || selectionTools) {
    return;
  }

  updateCommandTrigger();
  updateMentionTrigger();
  scheduleAutolink();
}

function handleEditorKeyDown(event: KeyboardEvent) {
  if (
    mentionSuggestionVisible &&
    (event.key === 'Enter' || event.key === 'Tab')
  ) {
    event.preventDefault();
    insertAssistantMention();
    return;
  }

  if (event.key === 'Escape' && mentionSuggestionVisible) {
    event.preventDefault();
    closeMentionPanel();
    return;
  }

  if (event.key !== 'Escape' || !aiOpen || inlineSuggestion || selectionTools) {
    return;
  }

  pendingCommandTrigger = undefined;
  onCloseAiPanel();
}

function handleEditorPaste(event: ClipboardEvent) {
  const imageFiles = getClipboardImageFiles(event.clipboardData);
  if (imageFiles.length) {
    event.preventDefault();
    if (!allowImages) {
      return;
    }
    void onImagePaste(imageFiles);
    return;
  }

  if (inlineSuggestion || selectionTools) {
    return;
  }

  window.setTimeout(() => {
    if (allowImages) {
      normalizeEditorImages();
    } else {
      removeEditorImages();
      onChange(getHtml());
    }
    runAutolink();
  }, 0);
}

function handleEditorBlur() {
  window.setTimeout(() => {
    if (
      editorWrapElement?.contains(document.activeElement) ||
      editorElement === document.activeElement
    ) {
      return;
    }

    closeMentionPanel();
  }, 0);
}

function updateCommandTrigger() {
  const trigger = getActiveCommandTrigger();

  if (trigger) {
    closeMentionPanel();
    pendingCommandTrigger = trigger;
    onCommandMenuRequest();
    return;
  }

  if (pendingCommandTrigger) {
    pendingCommandTrigger = undefined;

    if (aiOpen) {
      onCloseAiPanel();
    }
  }
}

function updateMentionTrigger() {
  if (!aiMentionEnabled) {
    closeMentionPanel();
    return;
  }

  const trigger = getActiveMentionTrigger();

  if (trigger && assistantMentionMatchesQuery(trigger.query)) {
    pendingMentionTrigger = trigger;

    if (aiOpen) {
      onCloseAiPanel();
    }

    void updateMentionPanelPosition(trigger);
    return;
  }

  closeMentionPanel();
}

function getActiveCommandTrigger(): PendingCommandTrigger | undefined {
  if (!editorElement) {
    return undefined;
  }

  const endOffset = getTextSelectionOffset(editorElement);

  if (endOffset === undefined) {
    return undefined;
  }

  const beforeCaret = getSerializableEditorText().slice(0, endOffset);
  const match = beforeCaret.match(COMMAND_TRIGGER_PATTERN);

  if (!match) {
    return undefined;
  }

  const matchedText = match[0];
  const markerOffset = matchedText.lastIndexOf('/');
  const startOffset = beforeCaret.length - matchedText.length + markerOffset;

  return {
    startOffset,
    endOffset,
  };
}

function getActiveMentionTrigger(): PendingMentionTrigger | undefined {
  if (!editorElement) {
    return undefined;
  }

  const endOffset = getTextSelectionOffset(editorElement);

  if (endOffset === undefined) {
    return undefined;
  }

  const beforeCaret = getSerializableEditorText().slice(0, endOffset);
  const match = beforeCaret.match(MENTION_TRIGGER_PATTERN);

  if (!match) {
    return undefined;
  }

  const matchedText = match[0];
  const markerOffset = matchedText.lastIndexOf('@');
  const startOffset = beforeCaret.length - matchedText.length + markerOffset;

  return {
    startOffset,
    endOffset,
    query: match[2] ?? '',
  };
}

function consumePendingCommandTrigger() {
  if (!editorElement) {
    pendingCommandTrigger = undefined;
    return;
  }

  const trigger = getActiveCommandTrigger() ?? pendingCommandTrigger;
  pendingCommandTrigger = undefined;

  if (!trigger || trigger.endOffset <= trigger.startOffset) {
    return;
  }

  const range = createTextOffsetRange(
    editorElement,
    trigger.startOffset,
    trigger.endOffset
  );

  if (!range) {
    return;
  }

  range.deleteContents();
  editorElement.normalize();
  restoreTextSelectionOffset(editorElement, trigger.startOffset);
  onChange(getHtml());
}

function insertAssistantMention() {
  if (!editorElement) {
    return;
  }

  const trigger = getActiveMentionTrigger() ?? pendingMentionTrigger;

  if (!trigger || !assistantMentionMatchesQuery(trigger.query)) {
    closeMentionPanel();
    return;
  }

  const mentionText = `${resolvedMentionName} `;

  if (trigger.endOffset <= trigger.startOffset) {
    closeMentionPanel();
    insertText(mentionText);
    return;
  }

  const fallbackText = replaceTextRange(
    getSerializableEditorText(),
    trigger.startOffset,
    trigger.endOffset,
    mentionText
  );
  const selectionOffset = trigger.startOffset + mentionText.length;
  const inserted = replaceEditorRangeWithText(trigger, mentionText);

  if (!inserted || !hasTextAtOffset(mentionText, trigger.startOffset)) {
    replaceEditorText(fallbackText, selectionOffset);
  }

  closeMentionPanel();
  onChange(getHtml());
}

function replaceEditorRangeWithText(
  trigger: PendingMentionTrigger,
  text: string
): boolean {
  if (!editorElement) {
    return false;
  }

  const editor = editorElement;
  const range = createTextOffsetRange(
    editor,
    trigger.startOffset,
    trigger.endOffset
  );

  if (!range) {
    return false;
  }

  editor.focus();
  range.deleteContents();
  range.insertNode(document.createTextNode(text));
  editor.normalize();
  restoreTextSelectionOffset(editor, trigger.startOffset + text.length);

  return true;
}

function replaceTextRange(
  value: string,
  startOffset: number,
  endOffset: number,
  replacement: string
): string {
  return `${value.slice(0, startOffset)}${replacement}${value.slice(endOffset)}`;
}

function hasTextAtOffset(text: string, offset: number): boolean {
  return getSerializableEditorText().slice(offset, offset + text.length) === text;
}

function replaceEditorText(text: string, selectionOffset: number) {
  if (!editorElement) {
    return;
  }

  editorElement.textContent = text;
  editorElement.focus();
  restoreTextSelectionOffset(editorElement, selectionOffset);
}

function closeMentionPanel() {
  pendingMentionTrigger = undefined;
  mentionPanelStyle = '';
}

async function updateMentionPanelPosition(trigger: PendingMentionTrigger) {
  if (!editorElement || !editorWrapElement) {
    return;
  }

  await tick();

  if (!editorElement || !editorWrapElement || !pendingMentionTrigger) {
    return;
  }

  const range = createTextOffsetRange(
    editorElement,
    trigger.startOffset,
    trigger.endOffset
  );
  const wrapRect = editorWrapElement.getBoundingClientRect();
  const editorRect = editorElement.getBoundingClientRect();
  const triggerRect = range?.getBoundingClientRect();
  const rect = triggerRect && triggerRect.width + triggerRect.height > 0
    ? triggerRect
    : editorRect;
  const panelWidth = Math.min(288, Math.max(220, wrapRect.width - 24));
  const left = Math.min(
    Math.max(12, rect.left - wrapRect.left),
    Math.max(12, wrapRect.width - panelWidth - 12)
  );
  const top = Math.max(12, rect.bottom - wrapRect.top + 8);

  mentionPanelStyle = [
    `--comment-next-mention-left:${left}px`,
    `--comment-next-mention-top:${top}px`,
    `--comment-next-mention-width:${panelWidth}px`,
  ].join(';');
}

function normalizeMentionName(value: string): string {
  const normalizedValue = value.trim() || '评论助手';
  return normalizedValue.startsWith('@')
    ? normalizedValue
    : `@${normalizedValue}`;
}

function assistantMentionMatchesQuery(query: string): boolean {
  const normalizedQuery = query.trim().toLocaleLowerCase();

  if (!normalizedQuery) {
    return true;
  }

  const displayName = aiAssistantName.trim().toLocaleLowerCase();
  const mentionName = resolvedMentionName
    .replace(/^@/, '')
    .trim()
    .toLocaleLowerCase();

  return (
    displayName.includes(normalizedQuery) ||
    mentionName.includes(normalizedQuery)
  );
}

function createTextOffsetRange(
  root: HTMLElement,
  startOffset: number,
  endOffset: number
): Range | undefined {
  const range = document.createRange();
  const start = Math.max(0, Math.min(startOffset, endOffset));
  const end = Math.max(start, endOffset);
  const walker = document.createTreeWalker(root, NodeFilter.SHOW_TEXT);
  let currentOffset = 0;
  let currentNode = walker.nextNode();
  let startSet = false;

  while (currentNode) {
    const textLength = currentNode.textContent?.length ?? 0;
    const nextOffset = currentOffset + textLength;

    if (!startSet && start <= nextOffset) {
      range.setStart(currentNode, Math.max(0, start - currentOffset));
      startSet = true;
    }

    if (startSet && end <= nextOffset) {
      range.setEnd(currentNode, Math.max(0, end - currentOffset));
      return range;
    }

    currentOffset = nextOffset;
    currentNode = walker.nextNode();
  }

  if (!startSet) {
    return undefined;
  }

  range.setEnd(root, root.childNodes.length);

  return range;
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

function getSerializableEditorHtml(): string {
  if (!editorElement) {
    return '';
  }

  const clone = editorElement.cloneNode(true) as HTMLElement;
  removeTransientEditorNodes(clone);

  return clone.innerHTML;
}

function getSerializableEditorText(): string {
  if (!editorElement) {
    return '';
  }

  const clone = editorElement.cloneNode(true) as HTMLElement;
  removeTransientEditorNodes(clone);

  return clone.textContent ?? '';
}

function removeTransientEditorNodes(root: HTMLElement) {
  root
    .querySelectorAll('[data-comment-next-transient="true"]')
    .forEach((node) => node.remove());
}

function getClipboardImageFiles(clipboardData: DataTransfer | null): File[] {
  if (!clipboardData) {
    return [];
  }

  const imageFiles = new Map<string, File>();

  for (const item of Array.from(clipboardData.items)) {
    if (item.kind !== 'file') {
      continue;
    }

    const file = item.getAsFile();
    if (file && isImageFile(file)) {
      imageFiles.set(fileKey(file), file);
    }
  }

  for (const file of Array.from(clipboardData.files)) {
    if (isImageFile(file)) {
      imageFiles.set(fileKey(file), file);
    }
  }

  return Array.from(imageFiles.values());
}

function fileKey(file: File): string {
  return `${file.name}:${file.size}:${file.lastModified}`;
}

function normalizeEditorImages() {
  if (!editorElement) {
    return;
  }

  for (const image of Array.from(editorElement.querySelectorAll('img'))) {
    image.classList.add('comment-next-emote-image');
    image.loading = 'lazy';
    image.decoding = 'async';
    image.alt ||= '图片';
  }

  onChange(getHtml());
}

function removeEditorImages() {
  if (!editorElement) {
    return;
  }

  for (const image of Array.from(editorElement.querySelectorAll('img'))) {
    image.remove();
  }
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

  if (
    isNodeInsideTransient(selection.anchorNode) ||
    isNodeInsideTransient(selection.focusNode)
  ) {
    return false;
  }

  return (
    editorElement === selection.anchorNode ||
    editorElement.contains(selection.anchorNode) ||
    editorElement === selection.focusNode ||
    editorElement.contains(selection.focusNode)
  );
}

function isNodeInsideTransient(node: Node): boolean {
  if (node.nodeType === Node.ELEMENT_NODE) {
    return Boolean((node as Element).closest('[data-comment-next-transient="true"]'));
  }

  return Boolean(node.parentElement?.closest('[data-comment-next-transient="true"]'));
}
</script>

<div
  bind:this={editorWrapElement}
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
    tabindex="0"
    aria-multiline="true"
    data-placeholder={placeholder}
    aria-label="评论内容"
    oninput={handleEditorInput}
    onkeydown={handleEditorKeyDown}
    onblur={handleEditorBlur}
    onpaste={handleEditorPaste}
  >
    {#if inlineSuggestion}
      <CommentNextAiSuggestion
        mode={aiMode}
        text={suggestionText}
        loading={suggestionLoading}
        {aiAssistantName}
        onAccept={onAcceptSuggestion}
        onInsert={onInsertSuggestion}
        onRewrite={onRewriteSuggestion}
        onReject={onRejectSuggestion}
      />
    {:else if selectionTools}
      <p class="comment-next-editor-paragraph">
        这篇文章给了我很多启发，尤其是关于
        <mark class="comment-next-selection-mark">长期主义的部分</mark>
        ，让我对产品设计有了新的思考。
      </p>
    {/if}
  </div>

  {#if mentionSuggestionVisible}
    <div
      class="comment-next-mention-panel"
      data-comment-next-transient="true"
      role="listbox"
      aria-label="AI 助手候选"
      style={mentionPanelStyle}
    >
      <button
        class="comment-next-mention-option"
        type="button"
        role="option"
        aria-selected="true"
        onmousedown={(event) => {
          event.preventDefault();
          insertAssistantMention();
        }}
        onpointerdown={(event) => {
          event.preventDefault();
          insertAssistantMention();
        }}
        onclick={(event) => {
          event.preventDefault();
          insertAssistantMention();
        }}
      >
        <span class="comment-next-mention-option-icon" aria-hidden="true">
          <CommentNextIcon name="sparkle" size={15} />
        </span>
        <span class="comment-next-mention-option-copy">
          <span class="comment-next-mention-option-name">{resolvedMentionName}</span>
          <small>AI 助手</small>
        </span>
      </button>
    </div>
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
    --at-apply: relative min-h-[var(--comment-next-editor-min-height,12.5rem)] overflow-visible [background:var(--comment-next-editor-surface-bg,transparent,var(--comment-next-editor-bg-color,#ffffff))];
    box-sizing: border-box;
    max-width: 100%;
    min-width: 0;
  }

  .comment-next-editor-wrap-top-rounded {
    --at-apply: rounded-t-[var(--comment-next-radius-lg,0.875rem)];
  }

  .comment-next-editor-wrap::after {
    --at-apply: absolute right-4 bottom-0 left-4 h-0 border-t [border-top-style:var(--comment-next-divider-style,dashed)] [border-top-color:var(--comment-next-divider-color,#d4dde8)] bg-transparent;
    content: "";
  }

  .comment-next-editor-wrap-inline,
  .comment-next-editor-wrap-selection {
    --at-apply: [background:var(--comment-next-editor-ai-surface-bg,radial-gradient(circle_at_1.5rem_1.25rem,rgb(59_130_246_/_0.14),transparent_8rem),linear-gradient(180deg,rgb(255_255_255_/_0.98),rgb(247_252_251_/_0.98)),var(--comment-next-editor-bg-color,#ffffff))];
  }

  .comment-next-editor {
    --at-apply: box-border min-h-[var(--comment-next-editor-min-height,12.5rem)] text-[0.9375rem] text-[var(--comment-next-text-color,#172033)] leading-[1.7] outline-none caret-[var(--comment-next-primary-color,rgb(59,130,246))];
    max-width: 100%;
    min-width: 0;
    overflow-wrap: anywhere;
    padding: var(--comment-next-editor-padding, 1.125rem 1.25rem 1.25rem);
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
    --at-apply: mx-0.5 inline-block max-h-[16rem] max-w-full align-middle object-contain;
  }

  .comment-next-editor :global(img) {
    --at-apply: inline-block max-h-[16rem] max-w-full align-middle object-contain;
  }

  .comment-next-editor-paragraph {
    --at-apply: m-0;
  }

  .comment-next-selection-mark {
    --at-apply: rounded px-[0.1875rem] py-[0.0625rem] bg-[var(--comment-next-ai-mark-bg-color,rgb(191_219_254))] text-inherit shadow-[0_0_0_1px_rgb(59_130_246_/_0.16)_inset];
  }

  .comment-next-selection-bar {
    --at-apply: flex items-center;
  }

  .comment-next-mention-panel {
    --at-apply: absolute z-50 box-border rounded-[0.875rem] border border-solid [border-color:var(--comment-next-menu-border-color,#d5dde7)] bg-[var(--comment-next-menu-bg-color,#ffffff)] p-1.5 text-[var(--comment-next-text-color,#172033)] shadow-[0_18px_42px_rgb(15_23_42_/_0.16),0_1px_0_rgb(255_255_255_/_0.82)_inset];
    left: var(--comment-next-mention-left, 1rem);
    top: var(--comment-next-mention-top, 3rem);
    width: var(--comment-next-mention-width, 18rem);
    animation: comment-next-mention-in 150ms cubic-bezier(0.2, 0.8, 0.2, 1);
  }

  .comment-next-mention-option,
  .comment-next-mention-option-icon {
    --at-apply: flex items-center;
  }

  .comment-next-mention-option {
    --at-apply: min-h-12 w-full cursor-pointer gap-2.5 rounded-[0.6875rem] border-0 bg-transparent px-2.5 py-2 text-left text-[var(--comment-next-text-color,#172033)] font-inherit transition-[background-color,color,transform] duration-140 ease-in-out;
  }

  .comment-next-mention-option:hover,
  .comment-next-mention-option:focus-visible {
    --at-apply: bg-[var(--comment-next-control-hover-bg-color,#eef2f4)] text-[var(--comment-next-ai-color,rgb(59,130,246))] outline-none;
  }

  .comment-next-mention-option:active {
    --at-apply: translate-y-px;
  }

  .comment-next-mention-option-icon {
    --at-apply: h-8 w-8 flex-none justify-center rounded-lg bg-[var(--comment-next-ai-bg-color,rgb(239_246_255))] text-[var(--comment-next-ai-color,rgb(59,130,246))];
  }

  .comment-next-mention-option-copy {
    --at-apply: min-w-0 flex-1;
  }

  .comment-next-mention-option-name {
    --at-apply: block truncate text-[0.875rem] font-[760] leading-tight;
  }

  .comment-next-mention-option-copy small {
    --at-apply: mt-0.5 block truncate text-[0.75rem] text-[var(--comment-next-muted-color,#667085)] font-medium leading-tight;
  }

  .comment-next-selection-bar button {
    --at-apply: inline-flex h-7 cursor-pointer items-center justify-center gap-[0.3125rem] rounded-lg border border-solid border-transparent bg-transparent px-2.5 py-0 text-[0.8125rem] text-[var(--comment-next-muted-color,#667085)] font-[620] font-inherit transition-[background-color,border-color,color,transform] duration-150 ease-in-out;
  }

  .comment-next-selection-bar button:hover,
  .comment-next-selection-action-active {
    --at-apply: [border-color:var(--comment-next-ai-border-color,rgb(191_219_254))] bg-[var(--comment-next-ai-control-hover-bg-color,rgb(239_246_255))] text-[var(--comment-next-ai-color,rgb(59,130,246))];
  }

  .comment-next-selection-bar button:active {
    --at-apply: translate-y-px;
  }

  .comment-next-selection-bar {
    --at-apply: absolute top-14 left-5 z-3 gap-1.5 rounded-xl border border-solid [border-color:var(--comment-next-menu-border-color,#d5dde7)] bg-[var(--comment-next-menu-bg-color,#ffffff)] p-1.5 shadow-[0_14px_34px_rgb(15_23_42_/_0.14),0_1px_0_rgb(255_255_255_/_0.8)_inset];
    animation: comment-next-selection-in 160ms cubic-bezier(0.2, 0.8, 0.2, 1);
  }

  .comment-next-selection-bar::after {
    --at-apply: absolute bottom-[-0.3125rem] left-8 h-2.5 w-2.5 border-r border-b border-solid [border-color:var(--comment-next-menu-border-color,#d5dde7)] bg-[var(--comment-next-menu-bg-color,#ffffff)] rotate-45;
    content: "";
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

  @keyframes comment-next-mention-in {
    from {
      opacity: 0;
      transform: translateY(0.375rem) scale(0.985);
    }

    to {
      opacity: 1;
      transform: translateY(0) scale(1);
    }
  }

  @media (max-width: 640px) {
    .comment-next-editor-wrap {
      --at-apply: min-h-[var(--comment-next-editor-mobile-min-height,9rem)];
    }

    .comment-next-editor {
      --at-apply: min-h-[var(--comment-next-editor-mobile-min-height,9rem)];
      padding: var(
        --comment-next-editor-mobile-padding,
        var(--comment-next-editor-padding, 1rem)
      );
    }

    .comment-next-selection-bar {
      --at-apply: right-3 left-3 overflow-x-auto;
    }

    .comment-next-mention-panel {
      --at-apply: right-3 w-auto;
      left: max(0.75rem, var(--comment-next-mention-left, 0.75rem));
      max-width: calc(100% - 1.5rem);
    }
  }

  @media (prefers-reduced-motion: reduce) {
    .comment-next-mention-panel,
    .comment-next-mention-option,
    .comment-next-selection-bar,
    .comment-next-selection-bar button {
      --at-apply: animate-none transition-none;
    }
  }
</style>
