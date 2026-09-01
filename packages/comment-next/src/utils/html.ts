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
  img: new Set([
    'src',
    'alt',
    'class',
    'loading',
    'decoding',
    'width',
    'height',
  ]),
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
  // Halo's comment safelist accepts the standard image attributes, but not
  // arbitrary CSS classes or Lottie-specific data attributes.
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

const LOTTIE_MARKER_PARAM = 'comment-next-lottie';
const LOTTIE_MARKER_VALUE = '1';
const LOTTIE_METADATA_PREFIX = 'comment-next-lottie:';
const REGULAR_IMAGE_METADATA_PREFIX = 'comment-next-image:';
const LOTTIE_MARKER_PARAMS = new Set([
  LOTTIE_MARKER_PARAM,
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
]);

interface SanitizeOptions {
  mode: 'display' | 'submit';
  allowImages?: boolean;
  allowedLottieHosts?: readonly unknown[];
  allowExternalLottie?: boolean;
}

export function sanitizeCommentHtml(
  value: string,
  allowedLottieHosts: readonly unknown[] = []
): string {
  return sanitizeHtml(value, {
    mode: 'display',
    allowImages: true,
    allowedLottieHosts,
    allowExternalLottie: true,
  });
}

export function sanitizeCommentSubmitHtml(
  value: string,
  allowedLottieHosts: readonly unknown[] = []
): string {
  return sanitizeHtml(value, {
    mode: 'submit',
    allowImages: true,
    allowedLottieHosts,
    allowExternalLottie: false,
  });
}

export function sanitizeConsoleCommentHtml(
  value: string,
  allowedLottieHosts: readonly unknown[] = []
): string {
  return sanitizeHtml(value, {
    mode: 'display',
    allowImages: false,
    allowedLottieHosts,
    allowExternalLottie: true,
  });
}

/**
 * Upgrade Lottie images rendered outside the comment widget, such as Halo
 * theme sidebar widgets. Those widgets render the stored, Halo-safe `<img>`
 * representation directly instead of going through the comment sanitizer.
 */
export function upgradeLottieImages(
  root: ParentNode,
  allowedLottieHosts: readonly unknown[] = [],
  options: LottieImageUpgradeOptions = {}
): boolean {
  if (typeof document === 'undefined') {
    return false;
  }

  const images: HTMLImageElement[] = [];
  if (root instanceof Element && root.matches('img[src]')) {
    images.push(root as HTMLImageElement);
  }
  images.push(...root.querySelectorAll<HTMLImageElement>('img[src]'));

  let upgraded = false;
  for (const image of images) {
    const data = decodeLottieImage(
      image,
      allowedLottieHosts,
      options.allowExternalLottie ?? true
    );
    if (!data || !image.isConnected) {
      continue;
    }

    replaceWithLottie(image, limitLottieDimensions(data, options));
    upgraded = true;
  }

  return upgraded;
}

export interface LottieImageUpgradeOptions {
  maxWidth?: number;
  maxHeight?: number;
  allowExternalLottie?: boolean;
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
  const imageClassName = tagName === 'img' ? element.getAttribute('class') : null;

  if (tagName === 'img' && options.mode === 'display') {
    const lottie = decodeLottieImage(
      element,
      options.allowedLottieHosts,
      options.allowExternalLottie ?? false
    );
    if (lottie) {
      replaceWithLottie(element, lottie);
      return;
    }
  }

  if (tagName === 'img' && !options.allowImages) {
    element.remove();
    return;
  }

  if (tagName === 'halo-lottie') {
    sanitizeLottie(element, options);
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
    sanitizeImage(element as HTMLImageElement, options, imageClassName);
  }

  sanitizeChildren(element, options);
}

type LottieImageData = {
  src: string;
  format: string;
  width: number;
  height: number;
  autoplay: string;
  loop: string;
  speed: string;
  fit: string;
  align: string;
  controls: string;
  hoverPlay: string;
  freezeOnOffscreen: string;
  ariaLabel: string;
};

function sanitizeLottie(element: Element, options: SanitizeOptions): void {
  const mode = options.mode;
  const allowedAttributes = DISPLAY_TAG_ALLOWED_ATTRIBUTES['halo-lottie'];
  for (const attribute of Array.from(element.attributes)) {
    const attributeName = attribute.name.toLowerCase();
    if (!allowedAttributes.has(attributeName) || attributeName.startsWith('on')) {
      element.removeAttribute(attribute.name);
    }
  }

  const src = normalizeLottieSrc(
    element.getAttribute('src') ?? '',
    options.allowedLottieHosts,
    options.allowExternalLottie ?? false
  );
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
  // Comments are playback content: always start them automatically, while
  // the runtime pauses them when they leave the viewport.
  element.setAttribute('autoplay', mode === 'display'
    ? 'true'
    : normalizeBooleanAttribute(element.getAttribute('autoplay'), true));
  element.setAttribute('loop', normalizeBooleanAttribute(element.getAttribute('loop'), true));
  element.setAttribute('controls', normalizeBooleanAttribute(element.getAttribute('controls'), false));
  element.setAttribute('hover-play', mode === 'display'
    ? 'false'
    : normalizeBooleanAttribute(element.getAttribute('hover-play'), false));
  element.setAttribute(
    'freeze-on-offscreen',
    mode === 'display'
      ? 'true'
      : normalizeBooleanAttribute(element.getAttribute('freeze-on-offscreen'), true)
  );

  element.setAttribute('speed', normalizeLottieSpeed(element.getAttribute('speed')));
  element.setAttribute('fit', normalizeLottieFit(element.getAttribute('fit')));
  element.setAttribute('align', normalizeLottieAlign(element.getAttribute('align')));

  const ariaLabel = (element.getAttribute('aria-label') ?? '').trim().slice(0, 120);
  if (ariaLabel) {
    element.setAttribute('aria-label', ariaLabel);
  } else {
    element.removeAttribute('aria-label');
  }

  if (mode === 'submit') {
    const image = document.createElement('img');
    // Keep the submitted representation within Halo's native comment
    // safelist. The frontend recognizes the .lottie source and restores the
    // custom element when rendering the comment.
    image.setAttribute('src', src);
    image.setAttribute('alt', ariaLabel || 'Lottie 动画');
    image.setAttribute('width', String(dimensions.width));
    image.setAttribute('height', String(dimensions.height));
    image.setAttribute('title', encodeLottieMetadata({
      format: element.getAttribute('format') ?? 'json',
      autoplay: element.getAttribute('autoplay') ?? 'true',
      loop: element.getAttribute('loop') ?? 'true',
      speed: element.getAttribute('speed') ?? '1',
      fit: element.getAttribute('fit') ?? 'contain',
      align: element.getAttribute('align') ?? 'center',
      controls: element.getAttribute('controls') ?? 'false',
      hoverPlay: element.getAttribute('hover-play') ?? 'false',
      freezeOnOffscreen: element.getAttribute('freeze-on-offscreen') ?? 'true',
      ariaLabel,
    }));
    element.replaceWith(image);
    return;
  }

  // The runtime owns the element's canvas and controls. Never preserve
  // attacker-provided children inside the custom element.
  element.replaceChildren();
}

function decodeLottieImage(
  element: Element,
  allowedLottieHosts: readonly unknown[] = [],
  allowExternalLottie = false
): LottieImageData | undefined {
  const value = element.getAttribute('src') ?? '';
  try {
    const url = new URL(unwrapMarkdownLink(value), window.location.origin);
    const metadata = decodeLottieMetadata(element.getAttribute('title'));
    const isMarked =
      url.searchParams.get(LOTTIE_MARKER_PARAM) === LOTTIE_MARKER_VALUE ||
      Boolean(metadata);
    // Displayed comments may contain a direct HTTPS `.lottie` URL from a CDN.
    // Submitted content still relies on the configured host allowlist.
    const src = normalizeLottieSrc(
      url.href,
      allowedLottieHosts,
      allowExternalLottie || isMarked
    );
    if (!src) {
      return undefined;
    }

    const sourceUrl = new URL(src);
    const isLottiePath =
      isLottieAttachmentPath(sourceUrl.pathname) ||
      isPublicLottieContentPath(sourceUrl.pathname);
    if (!isMarked && !isLottiePath) {
      return undefined;
    }

    const dimensions = normalizeLottieDimensions(
      url.searchParams.get('width') ?? element.getAttribute('width'),
      url.searchParams.get('height') ?? element.getAttribute('height')
    );
    const speedValue = metadata?.speed ?? url.searchParams.get('speed');
    return {
      src: isMarked ? stripLottieMarkerParams(src) : src,
      format: normalizeLottieFormat(
        metadata?.format ?? url.searchParams.get('format') ?? (isLottiePath ? 'lottie' : null)
      ),
      width: dimensions.width,
      height: dimensions.height,
      autoplay: normalizeBooleanAttribute(metadata?.autoplay ?? url.searchParams.get('autoplay'), true),
      loop: normalizeBooleanAttribute(metadata?.loop ?? url.searchParams.get('loop'), true),
      speed: normalizeLottieSpeed(speedValue),
      fit: normalizeLottieFit(metadata?.fit ?? url.searchParams.get('fit')),
      align: normalizeLottieAlign(metadata?.align ?? url.searchParams.get('align')),
      controls: normalizeBooleanAttribute(metadata?.controls ?? url.searchParams.get('controls'), false),
      hoverPlay: normalizeBooleanAttribute(metadata?.hoverPlay ?? url.searchParams.get('hover-play'), false),
      freezeOnOffscreen: normalizeBooleanAttribute(
        metadata?.freezeOnOffscreen ?? url.searchParams.get('freeze-on-offscreen'),
        true
      ),
      ariaLabel: (metadata?.ariaLabel ?? url.searchParams.get('aria-label') ?? '').trim().slice(0, 120),
    };
  } catch {
    return undefined;
  }
}

function replaceWithLottie(element: Element, data: LottieImageData): void {
  const lottie = document.createElement('halo-lottie');
  lottie.setAttribute('src', data.src);
  lottie.setAttribute('format', data.format);
  lottie.setAttribute('width', String(data.width));
  lottie.setAttribute('height', String(data.height));
  lottie.setAttribute('autoplay', 'true');
  lottie.setAttribute('loop', data.loop);
  lottie.setAttribute('speed', data.speed);
  lottie.setAttribute('fit', data.fit);
  lottie.setAttribute('align', data.align);
  lottie.setAttribute('controls', data.controls);
  lottie.setAttribute('hover-play', 'false');
  lottie.setAttribute('freeze-on-offscreen', 'true');
  if (data.ariaLabel) {
    lottie.setAttribute('aria-label', data.ariaLabel);
  }
  element.replaceWith(lottie);
}

function normalizeLottieSrc(
  value: string,
  allowedLottieHosts: readonly unknown[] = [],
  allowExternalLottie = false
): string {
  const unwrapped = unwrapMarkdownLink(value);
  const normalized = unwrapped.startsWith('//') ? `https:${unwrapped}` : unwrapped.trim();
  try {
    const url = new URL(normalized, window.location.origin);
    if (!['http:', 'https:'].includes(url.protocol)) {
      return '';
    }

    const isLottieAttachment = isLottieAttachmentPath(url.pathname);
    const isAllowedExternalHost =
      isLottieAttachment &&
      url.protocol === 'https:' &&
      isAllowedLottieHost(url.host, allowedLottieHosts);
    const isExternalLottieAllowed =
      allowExternalLottie &&
      isLottieAttachment &&
      url.protocol === 'https:';

    // Plugin API content stays on this site. Displayed comments may also use
    // direct HTTPS `.lottie` attachments; submission keeps the Host allowlist.
    if (
      url.origin !== window.location.origin &&
      !isAllowedExternalHost &&
      !isExternalLottieAllowed
    ) {
      return '';
    }

    const isPublicLottieContent = isPublicLottieContentPath(url.pathname);
    return isPublicLottieContent || isLottieAttachment
      ? url.href
      : '';
  } catch {
    return '';
  }
}

function isAllowedLottieHost(
  host: string,
  allowedLottieHosts: readonly unknown[]
): boolean {
  const normalizedHost = host.trim().toLowerCase();
  if (!normalizedHost) {
    return false;
  }

  return allowedLottieHosts.some((value) => {
    const rawHost =
      typeof value === 'string'
        ? value
        : value && typeof value === 'object' && 'host' in value
          ? (value as { host?: unknown }).host
          : undefined;
    return normalizeAllowedLottieHost(rawHost) === normalizedHost;
  });
}

function normalizeAllowedLottieHost(value: unknown): string {
  if (typeof value !== 'string') {
    return '';
  }

  const input = value.trim();
  if (!input) {
    return '';
  }

  try {
    return new URL(input.includes('://') ? input : `https://${input}`).host
      .trim()
      .toLowerCase();
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
  return {
    width: Math.max(1, Math.round(width)),
    height: Math.max(1, Math.round(height)),
  };
}

function limitLottieDimensions(
  data: LottieImageData,
  options: LottieImageUpgradeOptions
): LottieImageData {
  const maxWidth = normalizeLottieDimensionLimit(options.maxWidth);
  const maxHeight = normalizeLottieDimensionLimit(options.maxHeight);
  if (!maxWidth && !maxHeight) {
    return data;
  }

  const scale = Math.min(
    1,
    maxWidth ? maxWidth / data.width : 1,
    maxHeight ? maxHeight / data.height : 1
  );
  return {
    ...data,
    width: Math.max(1, Math.round(data.width * scale)),
    height: Math.max(1, Math.round(data.height * scale)),
  };
}

function normalizeLottieDimensionLimit(value: number | undefined): number | undefined {
  return Number.isFinite(value) && value > 0 ? Math.floor(value) : undefined;
}

function parsePositiveDimension(value: string | null, fallback: number): number {
  const number = Number(value);
  return Number.isFinite(number) && number > 0 ? Math.min(number, 4096) : fallback;
}

function normalizeLottieSpeed(value: string | null | undefined): string {
  if (typeof value !== 'string' || !value.trim()) {
    return '1';
  }

  const speed = Number(value);
  return Number.isFinite(speed) && speed > 0
    ? String(Math.min(10, Math.max(0.1, speed)))
    : '1';
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
  options: SanitizeOptions,
  originalClassName: string | null
): void {
  const src = normalizeImageSrc(image.getAttribute('src') ?? '');

  if (!src) {
    image.replaceWith(document.createTextNode(image.getAttribute('alt') ?? ''));
    return;
  }

  image.setAttribute('src', src);
  image.setAttribute('alt', image.getAttribute('alt') ?? '表情');

  const dimensions = normalizeImageDimensions(
    image.getAttribute('width'),
    image.getAttribute('height')
  );
  if (dimensions.width) {
    image.setAttribute('width', String(dimensions.width));
  } else {
    image.removeAttribute('width');
  }
  if (dimensions.height) {
    image.setAttribute('height', String(dimensions.height));
  } else {
    image.removeAttribute('height');
  }

  const metadata = decodeRegularImageMetadata(image.getAttribute('title'));
  const imageClass = normalizeImageClass(originalClassName, Boolean(metadata));

  if (options.mode === 'submit' && imageClass === 'comment-next-image') {
    image.setAttribute(
      'title',
      encodeRegularImageMetadata(metadata?.title ?? image.getAttribute('title') ?? '')
    );
  }

  if (options.mode === 'display') {
    if (metadata) {
      if (metadata.title) {
        image.setAttribute('title', metadata.title);
      } else {
        image.removeAttribute('title');
      }
    }
    image.setAttribute('class', imageClass);
    image.setAttribute('loading', 'lazy');
    image.setAttribute('decoding', 'async');
  }
}

function normalizeImageClass(
  value: string | null,
  hasRegularImageMetadata: boolean
): 'comment-next-emote-image' | 'comment-next-image' {
  const classes = new Set((value ?? '').split(/\s+/).filter(Boolean));
  if (
    hasRegularImageMetadata ||
    classes.has('comment-next-editor-image') ||
    classes.has('comment-next-image')
  ) {
    return 'comment-next-image';
  }
  if (
    classes.has('comment-next-editor-emote-image') ||
    classes.has('comment-next-emote-image')
  ) {
    return 'comment-next-emote-image';
  }

  // Legacy comments did not carry a type marker and were all rendered as
  // emotes. Preserve that behaviour while still rejecting arbitrary classes.
  return 'comment-next-emote-image';
}

type RegularImageMetadata = {
  title: string;
};

function encodeRegularImageMetadata(title: string): string {
  return `${REGULAR_IMAGE_METADATA_PREFIX}${encodeURIComponent(title)}`;
}

function decodeRegularImageMetadata(value: string | null): RegularImageMetadata | undefined {
  if (!value?.startsWith(REGULAR_IMAGE_METADATA_PREFIX)) {
    return undefined;
  }

  try {
    return {
      title: decodeURIComponent(value.slice(REGULAR_IMAGE_METADATA_PREFIX.length)),
    };
  } catch {
    return { title: '' };
  }
}

function normalizeImageDimensions(
  widthValue: string | null,
  heightValue: string | null
): { width?: number; height?: number } {
  const width = parseOptionalPositiveDimension(widthValue);
  const height = parseOptionalPositiveDimension(heightValue);
  return { width, height };
}

function parseOptionalPositiveDimension(value: string | null): number | undefined {
  if (value === null || !value.trim()) {
    return undefined;
  }
  const number = Number(value);
  return Number.isFinite(number) && number > 0
    ? Math.min(4096, Math.round(number))
    : undefined;
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
  const unwrapped = unwrapMarkdownLink(value);
  const normalizedValue = unwrapped.startsWith('//') ? `https:${unwrapped}` : unwrapped;

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

function unwrapMarkdownLink(value: string): string {
  const trimmed = value.trim();
  if (!trimmed.startsWith('[')) {
    return trimmed;
  }

  const separator = trimmed.indexOf('](');
  if (separator < 0) {
    return trimmed;
  }

  const closeLength = trimmed.endsWith('\\)') ? 2 : 1;
  if (!trimmed.endsWith(')')) {
    return trimmed;
  }

  return trimmed
    .slice(separator + 2, trimmed.length - closeLength)
    .trim()
    .replace(/^<|>$/g, '')
    .replace(/\\([)\\]])/g, '$1');
}

function isLottieAttachmentPath(pathname: string): boolean {
  return /\.lottie$/i.test(pathname);
}

function isPublicLottieContentPath(pathname: string): boolean {
  return (
    pathname.startsWith('/apis/api.lottie.halo.run/v1alpha1/animations/') &&
    pathname.endsWith('/content')
  );
}

function stripLottieMarkerParams(value: string): string {
  try {
    const url = new URL(value, window.location.origin);
    for (const parameter of LOTTIE_MARKER_PARAMS) {
      url.searchParams.delete(parameter);
    }
    return url.href;
  } catch {
    return value;
  }
}

type LottieMetadata = Pick<
  LottieImageData,
  'format' | 'autoplay' | 'loop' | 'speed' | 'fit' | 'align' | 'controls' | 'hoverPlay' | 'freezeOnOffscreen' | 'ariaLabel'
>;

function encodeLottieMetadata(metadata: LottieMetadata): string {
  const params = new URLSearchParams({
    format: metadata.format,
    autoplay: metadata.autoplay,
    loop: metadata.loop,
    speed: metadata.speed,
    fit: metadata.fit,
    align: metadata.align,
    controls: metadata.controls,
    hoverPlay: metadata.hoverPlay,
    freezeOnOffscreen: metadata.freezeOnOffscreen,
    ariaLabel: metadata.ariaLabel,
  });
  return `${LOTTIE_METADATA_PREFIX}${params.toString()}`;
}

function decodeLottieMetadata(value: string | null): LottieMetadata | undefined {
  if (!value?.startsWith(LOTTIE_METADATA_PREFIX)) {
    return undefined;
  }
  try {
    const params = new URLSearchParams(value.slice(LOTTIE_METADATA_PREFIX.length));
    return {
      format: params.get('format') ?? 'json',
      autoplay: params.get('autoplay') ?? 'true',
      loop: params.get('loop') ?? 'true',
      speed: params.get('speed') ?? '1',
      fit: params.get('fit') ?? 'contain',
      align: params.get('align') ?? 'center',
      controls: params.get('controls') ?? 'false',
      hoverPlay: params.get('hoverPlay') ?? 'false',
      freezeOnOffscreen: params.get('freezeOnOffscreen') ?? 'true',
      ariaLabel: params.get('ariaLabel') ?? '',
    };
  } catch {
    return undefined;
  }
}
