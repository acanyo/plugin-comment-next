const ALLOWED_TAGS = new Set([
  'a',
  'b',
  'blockquote',
  'br',
  'code',
  'div',
  'em',
  'i',
  'img',
  'li',
  'ol',
  'p',
  'pre',
  's',
  'span',
  'strong',
  'u',
  'ul',
]);

const GLOBAL_ALLOWED_ATTRIBUTES = new Set(['title']);
const DISPLAY_TAG_ALLOWED_ATTRIBUTES: Record<string, Set<string>> = {
  a: new Set(['href', 'title']),
  img: new Set(['src', 'alt', 'class', 'loading', 'decoding']),
};

const SUBMIT_TAG_ALLOWED_ATTRIBUTES: Record<string, Set<string>> = {
  a: new Set(['href', 'target', 'title']),
  code: new Set(['class']),
  img: new Set(['align', 'alt', 'height', 'src', 'title', 'width']),
};

interface SanitizeOptions {
  mode: 'display' | 'submit';
  allowImages?: boolean;
}

export function sanitizeCommentHtml(value: string): string {
  return sanitizeHtml(value, { mode: 'display', allowImages: true });
}

export function sanitizeCommentSubmitHtml(value: string): string {
  return sanitizeHtml(value, { mode: 'submit', allowImages: true });
}

export function sanitizeConsoleCommentHtml(value: string): string {
  return sanitizeHtml(value, { mode: 'display', allowImages: false });
}

export function highlightAssistantMentionHtml(
  value: string,
  mentionName = ''
): string {
  const normalizedMention = normalizeMentionName(mentionName);

  if (!value || !normalizedMention || typeof document === 'undefined') {
    return value;
  }

  const template = document.createElement('template');
  template.innerHTML = value;
  highlightMentionTextNodes(template.content, normalizedMention);

  return template.innerHTML;
}

function sanitizeHtml(value: string, options: SanitizeOptions): string {
  if (!value.trim()) {
    return '';
  }

  if (typeof document === 'undefined') {
    return escapeHtml(value);
  }

  const template = document.createElement('template');
  template.innerHTML = value;
  sanitizeChildren(template.content, options);

  return template.innerHTML;
}

function sanitizeChildren(parent: ParentNode, options: SanitizeOptions): void {
  for (const node of Array.from(parent.childNodes)) {
    if (node.nodeType === Node.ELEMENT_NODE) {
      sanitizeElement(node as Element, options);
      continue;
    }

    if (node.nodeType !== Node.TEXT_NODE) {
      node.remove();
    }
  }
}

function highlightMentionTextNodes(
  parent: ParentNode,
  mentionName: string
): void {
  const textNodes: Text[] = [];
  const mentionNameLower = mentionName.toLowerCase();
  const walker = document.createTreeWalker(parent, NodeFilter.SHOW_TEXT, {
    acceptNode(node) {
      const text = node.textContent ?? '';
      const parentElement = node.parentElement;

      if (
        !text.toLowerCase().includes(mentionNameLower) ||
        parentElement?.closest('a, code, pre, .comment-next-ai-mention')
      ) {
        return NodeFilter.FILTER_REJECT;
      }

      return NodeFilter.FILTER_ACCEPT;
    },
  });

  let currentNode = walker.nextNode();

  while (currentNode) {
    textNodes.push(currentNode as Text);
    currentNode = walker.nextNode();
  }

  textNodes.forEach((textNode) => {
    const fragment = createMentionHighlightedFragment(
      textNode.textContent ?? '',
      mentionName,
      mentionNameLower
    );

    if (fragment) {
      textNode.replaceWith(fragment);
    }
  });
}

function createMentionHighlightedFragment(
  text: string,
  mentionName: string,
  mentionNameLower: string
): DocumentFragment | undefined {
  const textLower = text.toLowerCase();
  const fragment = document.createDocumentFragment();
  let cursor = 0;
  let index = textLower.indexOf(mentionNameLower);
  let matched = false;

  while (index >= 0) {
    fragment.append(text.slice(cursor, index));
    fragment.append(
      createMentionElement(text.slice(index, index + mentionName.length))
    );
    cursor = index + mentionName.length;
    index = textLower.indexOf(mentionNameLower, cursor);
    matched = true;
  }

  if (!matched) {
    return undefined;
  }

  fragment.append(text.slice(cursor));
  return fragment;
}

function createMentionElement(text: string): HTMLSpanElement {
  const element = document.createElement('span');
  element.className = 'comment-next-ai-mention';
  element.setAttribute('data-comment-next-ai-mention', 'true');
  element.textContent = text;
  return element;
}

function sanitizeElement(element: Element, options: SanitizeOptions): void {
  const tagName = element.tagName.toLowerCase();

  if (tagName === 'img' && !options.allowImages) {
    element.remove();
    return;
  }

  if (!ALLOWED_TAGS.has(tagName)) {
    element.replaceWith(document.createTextNode(element.textContent ?? ''));
    return;
  }

  for (const attribute of Array.from(element.attributes)) {
    const attributeName = attribute.name.toLowerCase();
    const allowedAttributes =
      options.mode === 'submit'
        ? SUBMIT_TAG_ALLOWED_ATTRIBUTES[tagName]
        : DISPLAY_TAG_ALLOWED_ATTRIBUTES[tagName];
    const isAllowed =
      GLOBAL_ALLOWED_ATTRIBUTES.has(attributeName) ||
      Boolean(allowedAttributes?.has(attributeName));

    if (!isAllowed || attributeName.startsWith('on')) {
      element.removeAttribute(attribute.name);
      continue;
    }

    if (attributeName === 'href' && !isSafeHref(attribute.value)) {
      element.removeAttribute(attribute.name);
    }
  }

  if (tagName === 'a' && options.mode === 'display') {
    element.setAttribute('target', '_blank');
    element.setAttribute('rel', 'noopener noreferrer nofollow ugc');
  }

  if (tagName === 'img') {
    sanitizeImage(element as HTMLImageElement, options);
  }

  sanitizeChildren(element, options);
}

function sanitizeImage(
  image: HTMLImageElement,
  options: SanitizeOptions
): void {
  const src = normalizeImageSrc(image.getAttribute('src') ?? '');

  if (!src) {
    image.replaceWith(document.createTextNode(image.getAttribute('alt') ?? ''));
    return;
  }

  image.setAttribute('src', src);
  image.setAttribute('alt', image.getAttribute('alt') ?? '表情');

  if (options.mode === 'display') {
    image.setAttribute('class', 'comment-next-emote-image');
    image.setAttribute('loading', 'lazy');
    image.setAttribute('decoding', 'async');
  }
}

function isSafeHref(value: string): boolean {
  try {
    const url = new URL(value, window.location.origin);

    return ['http:', 'https:', 'mailto:'].includes(url.protocol);
  } catch {
    return false;
  }
}

function normalizeImageSrc(value: string): string {
  const normalizedValue = value.startsWith('//') ? `https:${value}` : value;

  try {
    const url = new URL(normalizedValue, window.location.origin);

    return ['http:', 'https:'].includes(url.protocol) ? url.href : '';
  } catch {
    return '';
  }
}

function normalizeMentionName(value: string): string {
  const normalizedValue = value.trim();

  if (!normalizedValue) {
    return '';
  }

  return normalizedValue.startsWith('@')
    ? normalizedValue
    : `@${normalizedValue}`;
}

function escapeHtml(value: string): string {
  return value
    .replaceAll('&', '&amp;')
    .replaceAll('<', '&lt;')
    .replaceAll('>', '&gt;')
    .replaceAll('"', '&quot;')
    .replaceAll("'", '&#039;');
}
