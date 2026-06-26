import { axiosInstance } from '@halo-dev/api-client';
import type { ResourceMetadata } from './metadata.ts';

export const EMOTE_GROUPS_ENDPOINT =
  '/apis/api.commentnext.xhhao.com/v1alpha1/emotegroups';
export const DEFAULT_EMOTE_SOURCE_URL =
  'https://raw.githubusercontent.com/SwaggyMacro/OwO_Stickers/main/OwO.min.json';

export type EmoteGroupType = 'emoticon' | 'image';
export type EmoteSourceType = 'DEFAULT' | 'CUSTOM';
export type EmoteGroupFilter = EmoteGroupType | 'ALL';

export interface EmoteGroup {
  apiVersion: 'api.commentnext.xhhao.com/v1alpha1';
  kind: 'EmoteGroup';
  metadata: ResourceMetadata;
  spec: EmoteGroupSpec;
}

export interface EmoteGroupSpec {
  enabled: boolean;
  displayName: string;
  type: EmoteGroupType;
  sourceType: EmoteSourceType;
  sourceUrl?: string;
  priority?: number;
  items: EmoteItem[];
}

export interface EmoteItem {
  icon: string;
  text?: string;
}

export interface EmoteGroupList {
  page: number;
  size: number;
  total: number;
  totalPages: number;
  hasNext: boolean;
  hasPrevious: boolean;
  items: EmoteGroup[];
}

export interface ListEmoteGroupsOptions {
  page: number;
  size: number;
  type?: EmoteGroupFilter;
  sort?: string;
}

export interface ListAllEmoteGroupsOptions {
  type?: EmoteGroupFilter;
}

export type RawEmotePacks = Record<
  string,
  {
    type?: string;
    container?: unknown[];
  }
>;

const API_VERSION = 'api.commentnext.xhhao.com/v1alpha1' as const;
const KIND = 'EmoteGroup' as const;

export async function listEmoteGroups(
  options: ListEmoteGroupsOptions
): Promise<EmoteGroupList> {
  const params: Record<string, string | number> = {
    page: options.page,
    size: options.size,
    sort: options.sort || 'spec.priority,asc',
  };

  if (options.type && options.type !== 'ALL') {
    params.fieldSelector = `spec.type=${options.type}`;
  }

  const { data } = await axiosInstance.get<EmoteGroupList>(
    EMOTE_GROUPS_ENDPOINT,
    { params }
  );
  return data;
}

export async function listAllEmoteGroups(
  options: ListAllEmoteGroupsOptions = {}
): Promise<EmoteGroup[]> {
  const size = 100;
  const groups: EmoteGroup[] = [];
  let page = 1;
  let hasNext = true;

  while (hasNext) {
    const result = await listEmoteGroups({
      page,
      size,
      type: options.type,
    });
    groups.push(...(result.items ?? []));
    hasNext = Boolean(result.hasNext);
    page += 1;
  }

  return sortEmoteGroupsByPriority(groups);
}

export async function createEmoteGroup(group: EmoteGroup): Promise<EmoteGroup> {
  const { data } = await axiosInstance.post<EmoteGroup>(
    EMOTE_GROUPS_ENDPOINT,
    normalizeEmoteGroup(group)
  );
  return data;
}

export async function updateEmoteGroup(group: EmoteGroup): Promise<EmoteGroup> {
  const { data } = await axiosInstance.put<EmoteGroup>(
    `${EMOTE_GROUPS_ENDPOINT}/${group.metadata.name}`,
    normalizeEmoteGroup(group)
  );
  return data;
}

export async function deleteEmoteGroup(name: string): Promise<void> {
  await axiosInstance.delete(`${EMOTE_GROUPS_ENDPOINT}/${name}`);
}

export async function fetchEmotePacksFromUrl(
  sourceUrl = DEFAULT_EMOTE_SOURCE_URL
): Promise<RawEmotePacks> {
  const response = await fetch(sourceUrl, {
    headers: {
      Accept: 'application/json',
    },
  });

  if (!response.ok) {
    throw new Error(`Failed to fetch emotes from source: ${response.status}`);
  }

  return parseRawEmotePacks(await response.text());
}

export async function fetchDefaultEmotePacks(): Promise<RawEmotePacks> {
  return fetchEmotePacksFromUrl(DEFAULT_EMOTE_SOURCE_URL);
}

export function parseRawEmotePacks(value: string): RawEmotePacks {
  const parsed = JSON.parse(value) as unknown;

  if (!parsed || Array.isArray(parsed) || typeof parsed !== 'object') {
    throw new Error('表情 JSON 必须是对象格式');
  }

  return parsed as RawEmotePacks;
}

export function rawPacksToGroups({
  rawPacks,
  names,
  sourceType,
  sourceUrl,
  existingGroups = [],
}: {
  rawPacks: RawEmotePacks;
  names: string[];
  sourceType: EmoteSourceType;
  sourceUrl?: string;
  existingGroups?: EmoteGroup[];
}): EmoteGroup[] {
  const existingByName = new Map(
    existingGroups.map((group) => [group.spec.displayName, group])
  );
  const nextPriority = resolveNextPriority(existingGroups);
  const groups: EmoteGroup[] = [];

  names.forEach((name, index) => {
    const rawPack = rawPacks[name];
    const type = normalizeEmoteGroupType(rawPack?.type);
    const items = normalizeEmoteItems(rawPack?.container);

    if (!items.length) {
      return;
    }

    const existing = existingByName.get(name);
    groups.push({
      apiVersion: API_VERSION,
      kind: KIND,
      metadata: existing?.metadata ?? {
        name: createEmoteGroupName(name),
      },
      spec: {
        enabled: existing?.spec.enabled ?? true,
        displayName: name,
        type,
        sourceType,
        sourceUrl,
        priority: existing?.spec.priority ?? nextPriority + index,
        items,
      },
    });
  });

  return groups;
}

export function sortEmoteGroupsByPriority(groups: EmoteGroup[]): EmoteGroup[] {
  return [...groups].sort((left, right) => {
    const priorityDelta = resolvePriority(left) - resolvePriority(right);

    if (priorityDelta !== 0) {
      return priorityDelta;
    }

    return left.spec.displayName.localeCompare(
      right.spec.displayName,
      'zh-Hans-CN'
    );
  });
}

export function normalizeEmoteGroup(group: EmoteGroup): EmoteGroup {
  const type = normalizeEmoteGroupType(group.spec?.type);

  return {
    apiVersion: API_VERSION,
    kind: KIND,
    metadata: {
      ...group.metadata,
      name:
        group.metadata?.name || createEmoteGroupName(group.spec?.displayName),
    },
    spec: {
      enabled: group.spec?.enabled !== false,
      displayName: group.spec?.displayName?.trim() || '未命名表情',
      type,
      sourceType: group.spec?.sourceType === 'DEFAULT' ? 'DEFAULT' : 'CUSTOM',
      sourceUrl: group.spec?.sourceUrl?.trim() || undefined,
      priority: Number.isFinite(Number(group.spec?.priority))
        ? Number(group.spec.priority)
        : 0,
      items: normalizeEmoteItems(group.spec?.items),
    },
  };
}

export function normalizeEmoteGroupType(value: unknown): EmoteGroupType {
  return value === 'image' ? 'image' : 'emoticon';
}

export function normalizeEmoteItems(value: unknown): EmoteItem[] {
  if (!Array.isArray(value)) {
    return [];
  }

  return value
    .map((item) => normalizeEmoteItem(item))
    .filter((item): item is EmoteItem => Boolean(item));
}

export function emoteItemPreviewUrl(item: EmoteItem): string {
  const source = extractImageAttribute(item.icon, 'src');
  return normalizeUrl(source);
}

export function emoteItemOriginUrl(item: EmoteItem): string {
  const source = extractImageAttribute(item.icon, 'origin');
  return normalizeUrl(source);
}

function normalizeEmoteItem(value: unknown): EmoteItem | undefined {
  if (typeof value === 'string') {
    const icon = value.trim();
    return icon ? { icon, text: '' } : undefined;
  }

  if (!value || typeof value !== 'object') {
    return undefined;
  }

  const item = value as Partial<EmoteItem>;
  const icon = typeof item.icon === 'string' ? item.icon.trim() : '';

  if (!icon) {
    return undefined;
  }

  return {
    icon,
    text: typeof item.text === 'string' ? item.text.trim() : '',
  };
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

function resolvePriority(group: EmoteGroup): number {
  const priority = Number(group.spec?.priority);
  return Number.isFinite(priority) ? priority : 0;
}

function resolveNextPriority(groups: EmoteGroup[]): number {
  if (!groups.length) {
    return 0;
  }

  const maxPriority = Math.max(
    ...groups.map((group) => resolvePriority(group))
  );
  return Number.isFinite(maxPriority) ? maxPriority + 1 : groups.length;
}

function createEmoteGroupName(displayName?: string): string {
  const source = displayName?.trim() || Math.random().toString(36);
  const slug = source
    .toLowerCase()
    .replace(/[^a-z0-9]+/g, '-')
    .replace(/^-|-$/g, '')
    .slice(0, 24);
  const hash = hashString(source);

  return `comment-next-emote-${slug ? `${slug}-` : ''}${hash}`;
}

function hashString(value: string): string {
  let hash = 2166136261;

  for (let index = 0; index < value.length; index += 1) {
    hash ^= value.charCodeAt(index);
    hash = Math.imul(hash, 16777619);
  }

  return (hash >>> 0).toString(36);
}
