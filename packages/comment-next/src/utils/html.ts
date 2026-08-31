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
  'halo-lottie',
  'u',
  'ul',
]);

const GLOBAL_ALLOWED_ATTRIBUTES = new Set(['title']);
const DISPLAY_TAG_ALLOWED_ATTRIBUTES: Record<string, Set<string>> = {
  a: new Set(['href', 'title']),
  img: new Set(['src', 'alt', 'class', 'loading', 'decoding']),
  'halo-lottie': new Set([
    'src',
    'format',
    'width',
    'height',
    'autoplay',
    'loop',
    'speed',
    'fit',
    'align',
    'controls',
    'hover-play',
    'freeze-on-offscreen',
    'aria-label',
  ]),
};

const SUBMIT_TAG_ALLOWED_ATTRIBUTES: Record<string, Set<string>> = {
  a: new Set(['href', 'target', 'title']),
  code: new Set(['class']),
  img: new Set(['align', 'alt', 'height', 'src', 'title', 'width']),
  'halo-lottie': new Set([
    'src',
    'format',
    'width',
    'height',
    'autoplay',
    'loop',
    'speed',
    'fit',
    'align',
    'controls',
    'hover-play',
    'freeze-on-offscreen',
    'aria-label',
  ]),
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

  if (tagName === 'halo-lottie') {
    sanitizeLottie(element);
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

function sanitizeLottie(element: Element): void {
  const allowedAttributes = DISPLAY_TAG_ALLOWED_ATTRIBUTES['halo-lottie'];
  for (const attribute of Array.from(element.attributes)) {
    const attributeName = attribute.name.toLowerCase();
    if (!allowedAttributes.has(attributeName) || attributeName.startsWith('on')) {
      element.removeAttribute(attribute.name);
    }
  }

  const src = normalizeLottieSrc(element.getAttribute('src') ?? '');
  if (!src) {
    element.remove();
    return;
  }

  element.setAttribute('src', src);
  element.setAttribute('format', normalizeLottieFormat(element.getAttribute('format')));

  const dimensions = normalizeLottieDimensions(
    element.getAttribute('width'),
    element.getAttribute('height')
  );
  element.setAttribute('width', String(dimensions.width));
  element.setAttribute('height', String(dimensions.height));
  element.setAttribute('autoplay', normalizeBooleanAttribute(element.getAttribute('autoplay'), true));
  element.setAttribute('loop', normalizeBooleanAttribute(element.getAttribute('loop'), true));
  element.setAttribute('controls', normalizeBooleanAttribute(element.getAttribute('controls'), false));
  element.setAttribute('hover-play', normalizeBooleanAttribute(element.getAttribute('hover-play'), false));
  element.setAttribute(
    'freeze-on-offscreen',
    normalizeBooleanAttribute(element.getAttribute('freeze-on-offscreen'), true)
  );

  const speed = Number(element.getAttribute('speed'));
  element.setAttribute(
    'speed',
    String(Number.isFinite(speed) ? Math.min(10, Math.max(0.1, speed)) : 1)
  );
  element.setAttribute('fit', normalizeLottieFit(element.getAttribute('fit')));
  element.setAttribute('align', normalizeLottieAlign(element.getAttribute('align')));

  const ariaLabel = (element.getAttribute('aria-label') ?? '').trim().slice(0, 120);
  if (ariaLabel) {
    element.setAttribute('aria-label', ariaLabel);
  } else {
    element.removeAttribute('aria-label');
  }

  // The runtime owns the element's canvas and controls. Never preserve
  // attacker-provided children inside the custom element.
  element.replaceChildren();
}

function normalizeLottieSrc(value: string): string {
  const normalized = value.startsWith('//') ? `https:${value}` : value.trim();
  try {
    const url = new URL(normalized, window.location.origin);
    if (!['http:', 'https:'].includes(url.protocol)) {
      return '';
    }

    // Lottie content is served by the installed plugin on this site. A
    // matching path on another origin must not be allowed to load arbitrary
    // attacker-controlled animation data.
    if (url.origin !== window.location.origin) {
      return '';
    }

    const isPublicLottieContent =
      url.pathname.startsWith(
        '/apis/api.lottie.halo.run/v1alpha1/animations/'
      ) && url.pathname.endsWith('/content');
    return isPublicLottieContent ? url.href : '';
  } catch {
    return '';
  }
}

function normalizeLottieFormat(value: string | null): string {
  const format = value?.trim().toLowerCase();
  return format && ['json', 'tgs', 'lottie'].includes(format) ? format : 'json';
}

function normalizeLottieDimensions(widthValue: string | null, heightValue: string | null) {
  const width = parsePositiveDimension(widthValue, 160);
  const height = parsePositiveDimension(heightValue, 160);
  const scale = Math.min(1, 144 / width, 72 / height);
  return {
    width: Math.max(1, Math.round(width * scale)),
    height: Math.max(1, Math.round(height * scale)),
  };
}

function parsePositiveDimension(value: string | null, fallback: number): number {
  const number = Number(value);
  return Number.isFinite(number) && number > 0 ? Math.min(number, 4096) : fallback;
}

function normalizeBooleanAttribute(value: string | null, fallback: boolean): string {
  if (value === null) return fallback ? 'true' : 'false';
  return value.trim().toLowerCase() === 'false' ? 'false' : 'true';
}

function normalizeLottieFit(value: string | null): string {
  const fit = value?.trim();
  return fit && ['contain', 'cover', 'fill', 'none', 'fit-width', 'fit-height'].includes(fit)
    ? fit
    : 'contain';
}

function normalizeLottieAlign(value: string | null): string {
  const align = value?.trim();
  return align && ['center', 'top', 'bottom', 'left', 'right'].includes(align)
    ? align
    : 'center';
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
