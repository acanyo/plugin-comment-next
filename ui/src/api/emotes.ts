import { axiosInstance } from '@halo-dev/api-client';
import type { ResourceMetadata } from './metadata.ts';

export const EMOTE_GROUPS_ENDPOINT =
  '/apis/api.commentnext.xhhao.com/v1alpha1/emotegroups';
export const EMOTE_CONSOLE_ENDPOINT =
  '/apis/console.api.commentnext.xhhao.com/v1alpha1/emotes';
export const DEFAULT_CHINA_EMOTE_SOURCE_URL =
  'https://owo.twouu.com/OwO.min.json';
export const DEFAULT_GITHUB_EMOTE_SOURCE_URL =
  'https://raw.githubusercontent.com/SwaggyMacro/OwO_Stickers/main/OwO.min.json';
export const DEFAULT_EMOTE_SOURCE_URL = DEFAULT_GITHUB_EMOTE_SOURCE_URL;

export interface EmoteSourcePreset {
  id: 'china' | 'github';
  label: string;
  description: string;
  url: string;
}

export const EMOTE_SOURCE_PRESETS: readonly EmoteSourcePreset[] = [
  {
    id: 'github',
    label: '官方源（GitHub）',
    description: 'SwaggyMacro/OwO_Stickers 默认上游源',
    url: DEFAULT_GITHUB_EMOTE_SOURCE_URL,
  },
  {
    id: 'china',
    label: '国内源（EdgeOne Pages）',
    description: '国内加速镜像源，可按需一键切换',
    url: DEFAULT_CHINA_EMOTE_SOURCE_URL,
  },
];

export function isPresetEmoteSourceUrl(url: string): boolean {
  const normalized = url.trim();
  return EMOTE_SOURCE_PRESETS.some((preset) => preset.url === normalized);
}

export type EmoteLocalizationState =
  | 'IDLE'
  | 'RUNNING'
  | 'COMPLETED'
  | 'CANCELLED'
  | 'FAILED';

export interface EmotePolicyOption {
  name: string;
  displayName: string;
}

export interface EmoteAttachmentGroupOption {
  name: string;
  displayName: string;
}

export interface EmoteLocalizationOptions {
  policies: EmotePolicyOption[];
  attachmentGroups: EmoteAttachmentGroupOption[];
  defaultPolicyName: string;
  defaultAttachmentGroupName: string;
  externalBaseUrl: string;
  externalUrlConfigured: boolean;
}

export interface EmoteLocalizationFailure {
  groupName: string;
  groupDisplayName: string;
  itemText: string;
  sourceUrl: string;
  reason: string;
}

export interface EmoteLocalizationProgress {
  state: EmoteLocalizationState;
  targetGroupNames: string[];
  policyName: string;
  attachmentGroupName: string;
  useAbsoluteUrl: boolean;
  totalGroups: number;
  completedGroups: number;
  currentGroupName: string;
  currentGroupDisplayName: string;
  totalItems: number;
  processedItems: number;
  localizedItems: number;
  reusedItems: number;
  skippedItems: number;
  failedItems: number;
  failures: EmoteLocalizationFailure[];
  startedAt?: string | null;
  finishedAt?: string | null;
  errorMessage?: string | null;
}

export interface StartEmoteLocalizationRequest {
  groupNames: string[];
  policyName: string;
  attachmentGroupName?: string;
  useAbsoluteUrl: boolean;
}

export type EmoteGroupLocalizationStatus =
  | 'NONE'
  | 'LOCALIZED'
  | 'PARTIAL'
  | 'EXTERNAL';

export interface EmoteGroupLocalizationSummary {
  totalImages: number;
  externalImages: number;
  localImages: number;
  status: EmoteGroupLocalizationStatus;
}

export type EmoteProvider = 'OWO' | 'LOTTIE';
export type EmoteGroupType = 'emoticon' | 'image' | 'lottie';
export type EmoteSourceType = 'DEFAULT' | 'CUSTOM' | 'PLUGIN';
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
  provider: EmoteProvider;
  sourceType: EmoteSourceType;
  sourceUrl?: string;
  sourceRef?: string;
  priority?: number;
  items: EmoteItem[];
}

export interface EmoteItem {
  icon?: string;
  text?: string;
  animationName?: string;
  contentUrl?: string;
  format?: string;
  defaults?: EmoteLottieDefaults;
}

export interface EmoteLottieDefaults {
  width: number;
  height: number;
  autoplay: boolean;
  loop: boolean;
  speed: number;
  fit: string;
  align: string;
  controls: boolean;
  hoverPlay: boolean;
  freezeOnOffscreen: boolean;
  ariaLabel?: string;
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
  try {
    const { data } = await axiosInstance.post<RawEmotePacks | string>(
      `${EMOTE_CONSOLE_ENDPOINT}/source-fetch`,
      { url: sourceUrl }
    );
    if (typeof data === 'string') {
      return parseRawEmotePacks(data);
    }
    if (!data || Array.isArray(data) || typeof data !== 'object') {
      throw new Error('表情 JSON 必须是对象格式');
    }
    return data;
  } catch (error) {
    const status = (error as { response?: { status?: number } })?.response
      ?.status;
    if (status && status !== 404 && status !== 405) {
      throw error;
    }
  }

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

export async function getEmoteLocalizationOptions(): Promise<EmoteLocalizationOptions> {
  const { data } = await axiosInstance.get<EmoteLocalizationOptions>(
    `${EMOTE_CONSOLE_ENDPOINT}/localization-options`
  );
  return data;
}

export async function getEmoteLocalizationStatus(): Promise<EmoteLocalizationProgress> {
  const { data } = await axiosInstance.get<EmoteLocalizationProgress>(
    `${EMOTE_CONSOLE_ENDPOINT}/localization`
  );
  return data;
}

export async function startEmoteLocalization(
  payload: StartEmoteLocalizationRequest
): Promise<EmoteLocalizationProgress> {
  const { data } = await axiosInstance.post<EmoteLocalizationProgress>(
    `${EMOTE_CONSOLE_ENDPOINT}/localization`,
    payload
  );
  return data;
}

export async function cancelEmoteLocalization(): Promise<EmoteLocalizationProgress> {
  const { data } = await axiosInstance.delete<EmoteLocalizationProgress>(
    `${EMOTE_CONSOLE_ENDPOINT}/localization`
  );
  return data;
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
    existingGroups
      .filter((group) => group.spec?.provider !== 'LOTTIE')
      .map((group) => [group.spec.displayName, group])
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
        provider: 'OWO',
        sourceType,
        sourceUrl,
        sourceRef: undefined,
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
  const provider = group.spec?.provider === 'LOTTIE' ? 'LOTTIE' : 'OWO';
  const type =
    provider === 'LOTTIE'
      ? 'lottie'
      : normalizeEmoteGroupType(
          group.spec?.type === 'lottie' ? 'emoticon' : group.spec?.type
        );

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
      provider,
      sourceType:
        group.spec?.sourceType === 'DEFAULT'
          ? 'DEFAULT'
          : group.spec?.sourceType === 'PLUGIN'
            ? 'PLUGIN'
            : 'CUSTOM',
      sourceUrl: group.spec?.sourceUrl?.trim() || undefined,
      sourceRef: group.spec?.sourceRef?.trim() || undefined,
      priority: Number.isFinite(Number(group.spec?.priority))
        ? Number(group.spec.priority)
        : 0,
      items:
        provider === 'LOTTIE'
          ? normalizeLottieItems(group.spec?.items)
          : normalizeEmoteItems(group.spec?.items),
    },
  };
}

export function normalizeEmoteGroupType(value: unknown): EmoteGroupType {
  if (value === 'lottie') return 'lottie';
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

export function normalizeLottieItems(value: unknown): EmoteItem[] {
  if (!Array.isArray(value)) {
    return [];
  }

  return value
    .map((item) => normalizeLottieItem(item))
    .filter((item): item is EmoteItem => Boolean(item));
}

export function emoteItemPreviewUrl(item: EmoteItem): string {
  const source = extractImageAttribute(item.icon || '', 'src');
  return normalizeUrl(source);
}

export function emoteItemOriginUrl(item: EmoteItem): string {
  const source = extractImageAttribute(item.icon || '', 'origin');
  return normalizeUrl(source);
}

export function emoteItemPreferredUrl(item: EmoteItem): string {
  const origin = emoteItemOriginUrl(item);
  if (origin) {
    return origin;
  }
  const preview = emoteItemPreviewUrl(item);
  if (preview) {
    return preview;
  }
  const raw = (item.icon || '').trim();
  if (/^(https?:)?\/\//i.test(raw) || raw.startsWith('/')) {
    return normalizeUrl(raw);
  }
  return '';
}

export function isExternalEmoteImageUrl(
  rawUrl: string,
  siteOrigin?: string
): boolean {
  const normalized = normalizeUrl((rawUrl || '').trim());
  if (!normalized || !/^https?:\/\//i.test(normalized)) {
    return false;
  }

  const effectiveOrigin =
    resolveOrigin(siteOrigin) ||
    (typeof window !== 'undefined'
      ? resolveOrigin(window.location.origin)
      : '');
  if (!effectiveOrigin) {
    return true;
  }

  try {
    const candidate = new URL(normalized);
    const site = new URL(effectiveOrigin);
    return candidate.host.toLowerCase() !== site.host.toLowerCase();
  } catch {
    return true;
  }
}

export function summarizeGroupLocalization(
  group: EmoteGroup,
  siteOrigin?: string
): EmoteGroupLocalizationSummary {
  if (group.spec?.provider === 'LOTTIE' || group.spec?.type !== 'image') {
    return {
      totalImages: 0,
      externalImages: 0,
      localImages: 0,
      status: 'NONE',
    };
  }

  const items = group.spec.items ?? [];
  let totalImages = 0;
  let externalImages = 0;

  for (const item of items) {
    const url = emoteItemPreferredUrl(item);
    if (!url) {
      continue;
    }
    totalImages += 1;
    if (isExternalEmoteImageUrl(url, siteOrigin)) {
      externalImages += 1;
    }
  }

  const localImages = Math.max(0, totalImages - externalImages);
  const status: EmoteGroupLocalizationStatus =
    totalImages === 0
      ? 'NONE'
      : externalImages === 0
        ? 'LOCALIZED'
        : localImages === 0
          ? 'EXTERNAL'
          : 'PARTIAL';

  return {
    totalImages,
    externalImages,
    localImages,
    status,
  };
}

function resolveOrigin(value?: string): string {
  if (!value) {
    return '';
  }
  try {
    return new URL(value).origin;
  } catch {
    return '';
  }
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

function normalizeLottieItem(value: unknown): EmoteItem | undefined {
  if (!value || typeof value !== 'object') {
    return undefined;
  }

  const item = value as Partial<EmoteItem>;
  const animationName =
    typeof item.animationName === 'string' ? item.animationName.trim() : '';
  const contentUrl =
    typeof item.contentUrl === 'string' ? item.contentUrl.trim() : '';

  if (!animationName || !contentUrl) {
    return undefined;
  }

  return {
    icon: '',
    text: typeof item.text === 'string' ? item.text.trim() : '',
    animationName,
    contentUrl,
    format:
      typeof item.format === 'string' && item.format.trim()
        ? item.format.trim().toLowerCase()
        : 'json',
    defaults: normalizeLottieDefaults(item.defaults),
  };
}

export function normalizeLottieDefaults(value: unknown): EmoteLottieDefaults {
  const defaults =
    value && typeof value === 'object'
      ? (value as Partial<EmoteLottieDefaults>)
      : {};
  const positiveNumber = (candidate: unknown, fallback: number) => {
    const number = Number(candidate);
    return Number.isFinite(number) && number > 0 ? number : fallback;
  };
  const booleanValue = (candidate: unknown, fallback: boolean) =>
    typeof candidate === 'boolean' ? candidate : fallback;

  return {
    width: positiveNumber(defaults.width, 160),
    height: positiveNumber(defaults.height, 160),
    autoplay: booleanValue(defaults.autoplay, true),
    loop: booleanValue(defaults.loop, true),
    speed: Math.min(10, positiveNumber(defaults.speed, 1)),
    fit:
      typeof defaults.fit === 'string' && defaults.fit.trim()
        ? defaults.fit.trim()
        : 'contain',
    align:
      typeof defaults.align === 'string' && defaults.align.trim()
        ? defaults.align.trim()
        : 'center',
    controls: booleanValue(defaults.controls, false),
    hoverPlay: booleanValue(defaults.hoverPlay, false),
    freezeOnOffscreen: booleanValue(defaults.freezeOnOffscreen, true),
    ariaLabel:
      typeof defaults.ariaLabel === 'string' ? defaults.ariaLabel.trim() : '',
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
