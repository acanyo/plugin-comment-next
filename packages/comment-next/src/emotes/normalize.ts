import { DEFAULT_COMMENT_NEXT_EMOTE_PACKS } from './default-packs';
import type {
  CommentNextEmoteItem,
  CommentNextEmotePack,
  CommentNextEmoteType,
  CommentNextRawEmotePacks,
} from '../types/emote';

export function normalizeCommentNextEmotePacks(
  value: unknown
): CommentNextEmotePack[] {
  const rawPacks = normalizeRawPacks(value) ?? DEFAULT_COMMENT_NEXT_EMOTE_PACKS;

  return Object.entries(rawPacks)
    .map(([name, pack]) => normalizePack(name, pack))
    .filter((pack): pack is CommentNextEmotePack => Boolean(pack));
}

function normalizeRawPacks(
  value: unknown
): CommentNextRawEmotePacks | undefined {
  if (!value) {
    return undefined;
  }

  if (typeof value === 'string') {
    try {
      return normalizeRawPacks(JSON.parse(value));
    } catch {
      return undefined;
    }
  }

  if (Array.isArray(value) || typeof value !== 'object') {
    return undefined;
  }

  return value as CommentNextRawEmotePacks;
}

function normalizePack(
  name: string,
  pack: unknown
): CommentNextEmotePack | undefined {
  if (!pack || typeof pack !== 'object') {
    return undefined;
  }

  const rawPack = pack as { type?: unknown; container?: unknown };
  const type = normalizeType(rawPack.type);
  const container = Array.isArray(rawPack.container) ? rawPack.container : [];
  const id = createStableId(name);
  const items = container
    .map((item, index) => normalizeItem(id, type, item, index))
    .filter((item): item is CommentNextEmoteItem => Boolean(item));

  if (!items.length) {
    return undefined;
  }

  return {
    id,
    name,
    type,
    items,
  };
}

function normalizeType(value: unknown): CommentNextEmoteType {
  return value === 'image' ? 'image' : 'emoticon';
}

function normalizeItem(
  packId: string,
  type: CommentNextEmoteType,
  value: unknown,
  index: number
): CommentNextEmoteItem | undefined {
  const rawItem = normalizeRawItem(value);

  if (!rawItem) {
    return undefined;
  }

  const itemValue = type === 'image' ? rawItem.previewSrc : rawItem.icon;

  if (!itemValue) {
    return undefined;
  }

  return {
    id: `${packId}-${index}-${createStableId(itemValue)}`,
    type,
    label: rawItem.text || itemValue,
    value: itemValue,
    description: rawItem.text,
    previewSrc: type === 'image' ? rawItem.previewSrc : undefined,
    originSrc: type === 'image' ? rawItem.originSrc : undefined,
    src: type === 'image' ? (rawItem.originSrc || rawItem.previewSrc) : undefined,
  };
}

function normalizeRawItem(
  value: unknown
): {
  icon: string;
  previewSrc: string;
  originSrc: string;
  text: string;
} | undefined {
  if (typeof value === 'string') {
    const icon = value.trim();
    return {
      icon,
      previewSrc: normalizeUrl(extractImageAttribute(icon, 'src') || icon),
      originSrc: normalizeUrl(extractImageAttribute(icon, 'origin')),
      text: '',
    };
  }

  if (!value || typeof value !== 'object') {
    return undefined;
  }

  const item = value as {
    icon?: unknown;
    src?: unknown;
    url?: unknown;
    text?: unknown;
    name?: unknown;
  };
  const icon = normalizeString(item.icon);
  const rawSrc =
    normalizeString(item.src) ||
    normalizeString(item.url) ||
    extractImageAttribute(icon, 'src') ||
    icon;
  const rawOrigin = extractImageAttribute(icon, 'origin');
  const text = normalizeString(item.text) || normalizeString(item.name);

  return {
    icon,
    previewSrc: normalizeUrl(rawSrc),
    originSrc: normalizeUrl(rawOrigin),
    text,
  };
}

function normalizeString(value: unknown): string {
  return typeof value === 'string' ? value.trim() : '';
}

function createStableId(value: string): string {
  return value
    .trim()
    .toLowerCase()
    .replaceAll(/[^a-z0-9\u3400-\u9fff]+/g, '-')
    .replaceAll(/^-|-$/g, '');
}

function extractImageAttribute(value: string, attribute: string): string {
  const pattern = new RegExp(`${attribute}=['"]([^'"]+)['"]`, 'i');
  return value.match(pattern)?.[1] ?? '';
}

function normalizeUrl(value: string): string {
  if (value.startsWith('//')) {
    return `https:${value}`;
  }

  return value;
}
