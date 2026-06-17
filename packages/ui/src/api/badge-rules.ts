import { axiosInstance, consoleApiClient } from '@halo-dev/api-client';
import type { ResourceMetadata } from './metadata';

export const BADGE_RULES_ENDPOINT =
  '/apis/api.commentnext.xhhao.com/v1alpha1/badgerules';
export const BADGE_PROFILES_ENDPOINT =
  '/apis/api.commentnext.xhhao.com/v1alpha1/badgeprofiles';
export const BADGE_ASSIGNMENTS_ENDPOINT =
  '/apis/api.commentnext.xhhao.com/v1alpha1/badgeassignments';
export const BADGE_STATS_SYNC_ENDPOINT =
  '/apis/api.commentnext.xhhao.com/v1alpha1/badges/sync-profiles';

export type BadgeRuleType = 'USER' | 'LEVEL';
export type BadgeRuleFilter = BadgeRuleType | 'ALL';
export type BadgeProfileIdentityType = 'USER' | 'EMAIL';
export type BadgeProfileFilter = BadgeProfileIdentityType | 'ALL';

export interface BadgeRule {
  apiVersion: 'api.commentnext.xhhao.com/v1alpha1';
  kind: 'BadgeRule';
  metadata: ResourceMetadata;
  spec: BadgeRuleSpec;
}

export interface BadgeRuleSpec {
  type: BadgeRuleType;
  enabled: boolean;
  label: string;
  icon?: string;
  color?: string;
  title?: string;
  minComments?: number;
}

export interface CommentNextBadge {
  id?: string;
  label?: string;
  tone?: string;
  icon?: string;
  color?: string;
  title?: string;
}

export interface BadgeProfile {
  apiVersion: 'api.commentnext.xhhao.com/v1alpha1';
  kind: 'BadgeProfile';
  metadata: ResourceMetadata;
  spec: BadgeProfileSpec;
}

export interface BadgeProfileSpec {
  identityType: BadgeProfileIdentityType;
  identity: string;
  activeCommentCount: number;
  badges: CommentNextBadge[];
}

export interface BadgeAssignment {
  apiVersion: 'api.commentnext.xhhao.com/v1alpha1';
  kind: 'BadgeAssignment';
  metadata: ResourceMetadata;
  spec: BadgeAssignmentSpec;
}

export interface BadgeAssignmentList {
  page: number;
  size: number;
  total: number;
  totalPages: number;
  hasNext: boolean;
  hasPrevious: boolean;
  items: BadgeAssignment[];
}

export interface BadgeAssignmentSpec {
  badgeName: string;
  identityType: BadgeProfileIdentityType;
  identity: string;
  enabled: boolean;
}

export interface BadgeProfileUserInfo {
  name: string;
  displayName: string;
  avatar?: string;
}

export interface BadgeRuleList {
  page: number;
  size: number;
  total: number;
  totalPages: number;
  hasNext: boolean;
  hasPrevious: boolean;
  items: BadgeRule[];
}

export interface BadgeProfileList {
  page: number;
  size: number;
  total: number;
  totalPages: number;
  hasNext: boolean;
  hasPrevious: boolean;
  items: BadgeProfile[];
}

export interface ListBadgeRulesOptions {
  page: number;
  size: number;
  type?: BadgeRuleFilter;
}

export interface ListBadgeProfilesOptions {
  page: number;
  size: number;
  identityType?: BadgeProfileFilter;
}

export interface ListBadgeAssignmentsOptions {
  page?: number;
  size?: number;
  badgeName?: string;
  identityType?: BadgeProfileIdentityType;
  identity?: string;
}

export interface DeleteBadgeAssignmentsOptions {
  badgeName: string;
  identityType: BadgeProfileIdentityType;
  identity: string;
}

export interface BadgeProfileSyncResult {
  activeProfiles: number;
  updatedProfiles: number;
  activeComments: number;
}

const API_VERSION = 'api.commentnext.xhhao.com/v1alpha1' as const;
const KIND = 'BadgeRule' as const;
const ASSIGNMENT_KIND = 'BadgeAssignment' as const;

export async function listBadgeRules(
  options: ListBadgeRulesOptions
): Promise<BadgeRuleList> {
  const params: Record<string, string | number> = {
    page: options.page,
    size: options.size,
    sort: 'metadata.creationTimestamp,desc',
  };

  if (options.type && options.type !== 'ALL') {
    params.fieldSelector = `spec.type=${options.type}`;
  }

  const { data } = await axiosInstance.get<BadgeRuleList>(
    BADGE_RULES_ENDPOINT,
    { params }
  );
  return data;
}

export async function listBadgeProfiles(
  options: ListBadgeProfilesOptions
): Promise<BadgeProfileList> {
  const params: Record<string, string | number> = {
    page: options.page,
    size: options.size,
    sort: 'metadata.creationTimestamp,desc',
  };

  if (options.identityType && options.identityType !== 'ALL') {
    params.fieldSelector = `spec.identityType=${options.identityType}`;
  }

  const { data } = await axiosInstance.get<BadgeProfileList>(
    BADGE_PROFILES_ENDPOINT,
    { params }
  );
  return data;
}

export async function listHaloUserInfos(
  names: string[]
): Promise<Record<string, BadgeProfileUserInfo>> {
  const uniqueNames = Array.from(
    new Set(names.map((name) => name.trim()).filter(Boolean))
  );
  if (!uniqueNames.length) {
    return {};
  }

  const { data } = await consoleApiClient.user.listUsers({
    page: 1,
    size: uniqueNames.length,
    fieldSelector: [`metadata.name=(${uniqueNames.join(',')})`],
  });

  return Object.fromEntries(
    (data.items ?? [])
      .map((item) => item.user)
      .filter((user) => user?.metadata?.name)
      .map((user) => [
        user.metadata.name,
        {
          name: user.metadata.name,
          displayName: user.spec?.displayName || user.metadata.name,
          avatar: user.spec?.avatar,
        },
      ])
  );
}

export async function createBadgeAssignment(
  assignment: BadgeAssignment
): Promise<BadgeAssignment> {
  const { data } = await axiosInstance.post<BadgeAssignment>(
    BADGE_ASSIGNMENTS_ENDPOINT,
    normalizeBadgeAssignment(assignment)
  );
  return data;
}

export async function listBadgeAssignments(
  options: ListBadgeAssignmentsOptions = {}
): Promise<BadgeAssignmentList> {
  const params = new URLSearchParams();
  params.set('page', String(options.page ?? 1));
  params.set('size', String(options.size ?? 100));
  params.append('sort', 'metadata.creationTimestamp,desc');

  if (options.badgeName) {
    params.append('fieldSelector', `spec.badgeName=${options.badgeName}`);
  }
  if (options.identityType) {
    params.append('fieldSelector', `spec.identityType=${options.identityType}`);
  }
  if (options.identity) {
    params.append(
      'fieldSelector',
      `spec.identity=${normalizeIdentity(options.identityType, options.identity)}`
    );
  }

  const { data } = await axiosInstance.get<BadgeAssignmentList>(
    BADGE_ASSIGNMENTS_ENDPOINT,
    { params }
  );
  return data;
}

export async function deleteBadgeAssignment(name: string): Promise<void> {
  await axiosInstance.delete(`${BADGE_ASSIGNMENTS_ENDPOINT}/${name}`);
}

export async function deleteBadgeAssignmentsForIdentity(
  options: DeleteBadgeAssignmentsOptions
): Promise<number> {
  const assignments: BadgeAssignment[] = [];
  let page = 1;
  let hasNext = true;

  while (hasNext) {
    const result = await listBadgeAssignments({
      ...options,
      page,
      size: 100,
    });
    assignments.push(...(result.items ?? []));
    hasNext = Boolean(result.hasNext);
    page += 1;
  }

  const normalizedIdentity = normalizeIdentity(
    options.identityType,
    options.identity
  );
  const matchedAssignments = assignments.filter((assignment) => {
    const spec = assignment.spec;
    return (
      spec.badgeName === options.badgeName &&
      spec.identityType === options.identityType &&
      normalizeIdentity(spec.identityType, spec.identity) === normalizedIdentity
    );
  });

  await Promise.all(
    matchedAssignments.map((assignment) =>
      deleteBadgeAssignment(assignment.metadata.name)
    )
  );

  return matchedAssignments.length;
}

export async function createBadgeRule(rule: BadgeRule): Promise<BadgeRule> {
  const { data } = await axiosInstance.post<BadgeRule>(
    BADGE_RULES_ENDPOINT,
    normalizeBadgeRule(rule)
  );
  return data;
}

export async function updateBadgeRule(rule: BadgeRule): Promise<BadgeRule> {
  const name = rule.metadata.name;
  const { data } = await axiosInstance.put<BadgeRule>(
    `${BADGE_RULES_ENDPOINT}/${name}`,
    normalizeBadgeRule(rule)
  );
  return data;
}

export async function deleteBadgeRule(name: string): Promise<void> {
  await axiosInstance.delete(`${BADGE_RULES_ENDPOINT}/${name}`);
}

export async function syncBadgeProfiles(): Promise<BadgeProfileSyncResult> {
  const { data } = await axiosInstance.post<BadgeProfileSyncResult>(
    BADGE_STATS_SYNC_ENDPOINT
  );
  return data;
}

export function createBadgeRuleDraft(type: BadgeRuleType): BadgeRule {
  return {
    apiVersion: API_VERSION,
    kind: KIND,
    metadata: {
      name: createBadgeRuleName(type),
    },
    spec: {
      type,
      enabled: true,
      label: '',
      icon:
        type === 'LEVEL' ? 'mdi:star-four-points-outline' : 'mdi:star-outline',
      color: type === 'LEVEL' ? '#3b82f6' : '#8b5cf6',
      title: '',
      minComments: type === 'LEVEL' ? 10 : undefined,
    },
  };
}

export function createBadgeAssignmentDraft(badgeName = ''): BadgeAssignment {
  return {
    apiVersion: API_VERSION,
    kind: ASSIGNMENT_KIND,
    metadata: {
      name: createBadgeAssignmentName(),
    },
    spec: {
      badgeName,
      identityType: 'USER',
      identity: '',
      enabled: true,
    },
  };
}

export function cloneBadgeRule(rule: BadgeRule): BadgeRule {
  return {
    apiVersion: API_VERSION,
    kind: KIND,
    metadata: {
      ...rule.metadata,
      labels: rule.metadata.labels ? { ...rule.metadata.labels } : undefined,
      annotations: rule.metadata.annotations
        ? { ...rule.metadata.annotations }
        : undefined,
    },
    spec: {
      ...rule.spec,
    },
  };
}

export function normalizeBadgeRule(rule: BadgeRule): BadgeRule {
  const spec = rule.spec ?? createBadgeRuleDraft('USER').spec;
  const type = spec.type === 'LEVEL' ? 'LEVEL' : 'USER';

  return {
    apiVersion: API_VERSION,
    kind: KIND,
    metadata: {
      ...rule.metadata,
      name: rule.metadata?.name || createBadgeRuleName(type),
    },
    spec: {
      type,
      enabled: spec.enabled !== false,
      label: spec.label?.trim() ?? '',
      icon: spec.icon?.trim() || undefined,
      color: spec.color?.trim() || undefined,
      title: spec.title?.trim() || undefined,
      minComments:
        type === 'LEVEL'
          ? Math.max(0, Number(spec.minComments || 0))
          : undefined,
    },
  };
}

export function normalizeBadgeAssignment(
  assignment: BadgeAssignment
): BadgeAssignment {
  const identityType =
    assignment.spec?.identityType === 'EMAIL' ? 'EMAIL' : 'USER';

  return {
    apiVersion: API_VERSION,
    kind: ASSIGNMENT_KIND,
    metadata: {
      ...assignment.metadata,
      name: assignment.metadata?.name || createBadgeAssignmentName(),
    },
    spec: {
      badgeName: assignment.spec?.badgeName?.trim() ?? '',
      identityType,
      identity:
        identityType === 'EMAIL'
          ? (assignment.spec?.identity?.trim().toLowerCase() ?? '')
          : (assignment.spec?.identity?.trim() ?? ''),
      enabled: assignment.spec?.enabled !== false,
    },
  };
}

function createBadgeRuleName(type: BadgeRuleType): string {
  const random = Math.random().toString(36).slice(2, 8);
  return `comment-next-${type.toLowerCase()}-${Date.now().toString(36)}-${random}`;
}

function createBadgeAssignmentName(): string {
  const random = Math.random().toString(36).slice(2, 8);
  return `comment-next-assignment-${Date.now().toString(36)}-${random}`;
}

function normalizeIdentity(
  identityType: BadgeProfileIdentityType | undefined,
  identity: string
): string {
  return identityType === 'EMAIL'
    ? identity.trim().toLowerCase()
    : identity.trim();
}
