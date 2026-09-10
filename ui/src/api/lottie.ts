import { axiosInstance } from '@halo-dev/api-client';
import {
  type EmoteGroup,
  type EmoteItem,
  normalizeLottieDefaults,
} from './emotes';

export const LOTTIE_API_BASE = '/apis/console.api.lottie.halo.run/v1alpha1';
export const LOTTIE_PUBLIC_ANIMATION_BASE =
  '/apis/api.lottie.halo.run/v1alpha1/animations';
export const LOTTIE_UNGROUPED_REF = '__comment_next_lottie_ungrouped__';

export interface LottieAnimation {
  metadata: { name: string };
  spec: {
    displayName?: string | null;
    groupName?: string | null;
    format?: string | null;
    enabled?: boolean | null;
    tags?: string[] | null;
    defaults?: unknown;
    attachmentUrl?: string | null;
  };
}

export interface LottieGroup {
  metadata: { name: string };
  spec: {
    displayName?: string | null;
    sort?: number | null;
  };
}

export interface LottieCatalog {
  animations: LottieAnimation[];
  groups: LottieGroup[];
}

export class LottiePluginUnavailableError extends Error {
  constructor() {
    super('plugin-lottie 未安装或未启动');
    this.name = 'LottiePluginUnavailableError';
  }
}

export async function fetchLottieCatalog(): Promise<LottieCatalog> {
  try {
    const [animationsResponse, groupsResponse] = await Promise.all([
      axiosInstance.get<LottieAnimation[]>(`${LOTTIE_API_BASE}/animations`),
      axiosInstance.get<LottieGroup[]>(`${LOTTIE_API_BASE}/groups`),
    ]);

    return {
      animations: animationsResponse.data ?? [],
      groups: groupsResponse.data ?? [],
    };
  } catch (error) {
    const status = (error as { response?: { status?: number } })?.response
      ?.status;
    if (status === 404) {
      throw new LottiePluginUnavailableError();
    }
    throw error;
  }
}

export function buildLottieEmoteGroups(
  catalog: LottieCatalog,
  existingGroups: EmoteGroup[]
): { upserts: EmoteGroup[]; stale: EmoteGroup[] } {
  const sourceGroups = new Map(
    catalog.groups
      .filter((group) => Boolean(group?.metadata?.name))
      .map((group) => [group.metadata.name, group])
  );
  const buckets = new Map<
    string,
    { sourceRef: string; displayName: string; items: EmoteItem[]; sort: number }
  >();

  for (const animation of catalog.animations) {
    if (!animation?.metadata?.name || animation.spec?.enabled === false) {
      continue;
    }

    const groupRef = animation.spec?.groupName?.trim() || LOTTIE_UNGROUPED_REF;
    const sourceGroup = sourceGroups.get(groupRef);
    const displayName = normalizeDisplayName(
      sourceGroup?.spec?.displayName ||
        (groupRef === LOTTIE_UNGROUPED_REF ? '未分组' : groupRef)
    );
    const bucket = buckets.get(groupRef) ?? {
      sourceRef: groupRef,
      displayName,
      items: [],
      sort: Number(sourceGroup?.spec?.sort) || 0,
    };

    bucket.items.push({
      icon: '',
      text: animation.spec?.displayName?.trim() || animation.metadata.name,
      animationName: animation.metadata.name,
      // Keep stored comments on plugin-lottie's stable, same-origin endpoint.
      // The endpoint resolves the backing attachment without exposing its host
      // as part of the persisted comment HTML.
      contentUrl: `${LOTTIE_PUBLIC_ANIMATION_BASE}/${encodeURIComponent(animation.metadata.name)}/content`,
      format: normalizeFormat(animation.spec?.format),
      defaults: normalizeLottieDefaults(animation.spec?.defaults),
    });
    buckets.set(groupRef, bucket);
  }

  const existingLottie = existingGroups.filter(
    (group) => group.spec?.provider === 'LOTTIE'
  );
  const existingBySourceRef = new Map(
    existingLottie
      .filter((group) => Boolean(group.spec?.sourceRef))
      .map((group) => [group.spec.sourceRef as string, group])
  );
  const nextPriority = resolveNextPriority(existingGroups);
  const orderedBuckets = Array.from(buckets.values()).sort(
    (left, right) =>
      left.sort - right.sort ||
      left.displayName.localeCompare(right.displayName, 'zh-Hans-CN')
  );
  const upserts = orderedBuckets.map((bucket, index) => {
    const existing = existingBySourceRef.get(bucket.sourceRef);
    return {
      apiVersion: 'api.commentnext.xhhao.com/v1alpha1' as const,
      kind: 'EmoteGroup' as const,
      metadata: existing?.metadata ?? {
        name: createLottieGroupName(bucket.sourceRef),
      },
      spec: {
        enabled: existing?.spec.enabled ?? true,
        displayName: bucket.displayName,
        type: 'lottie' as const,
        provider: 'LOTTIE' as const,
        sourceType: 'PLUGIN' as const,
        sourceRef: bucket.sourceRef,
        priority: existing?.spec.priority ?? nextPriority + index,
        items: bucket.items,
      },
    } satisfies EmoteGroup;
  });
  const desiredRefs = new Set(buckets.keys());
  const stale = existingLottie.filter(
    (group) => !group.spec?.sourceRef || !desiredRefs.has(group.spec.sourceRef)
  );

  return { upserts, stale };
}

function normalizeFormat(value: unknown): string {
  const format = typeof value === 'string' ? value.trim().toLowerCase() : '';
  return ['json', 'tgs', 'lottie'].includes(format) ? format : 'json';
}

function normalizeDisplayName(value: unknown): string {
  const displayName = typeof value === 'string' ? value.trim() : '';
  let result = (displayName || 'Lottie 分组').slice(0, 64);
  const lastCodeUnit = result.charCodeAt(result.length - 1);
  if (lastCodeUnit >= 0xd800 && lastCodeUnit <= 0xdbff) {
    result = result.slice(0, -1);
  }
  return result;
}

function resolveNextPriority(groups: EmoteGroup[]): number {
  if (!groups.length) {
    return 0;
  }
  const priorities = groups.map((group) => Number(group.spec?.priority));
  const max = Math.max(...priorities.filter(Number.isFinite));
  return Number.isFinite(max) ? max + 1 : groups.length;
}

function createLottieGroupName(sourceRef: string): string {
  const slug = sourceRef
    .toLowerCase()
    .replace(/[^a-z0-9]+/g, '-')
    .replace(/^-|-$/g, '')
    .slice(0, 24);
  return `comment-next-lottie-${slug || 'group'}-${hashString(sourceRef)}`;
}

function hashString(value: string): string {
  let hash = 2166136261;
  for (let index = 0; index < value.length; index += 1) {
    hash ^= value.charCodeAt(index);
    hash = Math.imul(hash, 16777619);
  }
  return (hash >>> 0).toString(36);
}
