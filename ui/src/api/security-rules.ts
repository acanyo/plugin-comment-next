import { axiosInstance } from '@halo-dev/api-client';
import type { ResourceMetadata } from './metadata';

export const SECURITY_RULES_ENDPOINT =
  '/apis/api.commentnext.xhhao.com/v1alpha1/securityrules';

export type SecurityRuleListType = 'BLACK' | 'GRAY';
export type SecurityRuleListTypeFilter = SecurityRuleListType | 'ALL';
export type SecurityRuleField =
  | 'IP'
  | 'EMAIL'
  | 'USERNAME'
  | 'KEYWORD'
  | 'DOMAIN'
  | 'UA';
export type SecurityRuleFieldFilter = SecurityRuleField | 'ALL';
export type SecurityRuleMatchType = 'EXACT' | 'CONTAINS' | 'REGEX';

export interface SecurityRule {
  apiVersion: 'api.commentnext.xhhao.com/v1alpha1';
  kind: 'SecurityRule';
  metadata: ResourceMetadata;
  spec: SecurityRuleSpec;
}

export interface SecurityRuleSpec {
  enabled: boolean;
  listType: SecurityRuleListType;
  field: SecurityRuleField;
  matchType: SecurityRuleMatchType;
  value: string;
  reason?: string;
  priority?: number;
}

export interface SecurityRuleList {
  page: number;
  size: number;
  total: number;
  totalPages: number;
  hasNext: boolean;
  hasPrevious: boolean;
  items: SecurityRule[];
}

export interface ListSecurityRulesOptions {
  page?: number;
  size?: number;
  listType?: SecurityRuleListTypeFilter;
  field?: SecurityRuleFieldFilter;
}

const API_VERSION = 'api.commentnext.xhhao.com/v1alpha1' as const;
const KIND = 'SecurityRule' as const;

export async function listSecurityRules(
  options: ListSecurityRulesOptions = {}
): Promise<SecurityRuleList> {
  const params = buildListParams(options);
  const { data } = await axiosInstance.get<SecurityRuleList>(
    SECURITY_RULES_ENDPOINT,
    { params }
  );
  return data;
}

export async function listAllSecurityRules(
  options: Omit<ListSecurityRulesOptions, 'page' | 'size'> = {}
): Promise<SecurityRule[]> {
  const rules: SecurityRule[] = [];
  let page = 1;
  let hasNext = true;

  while (hasNext) {
    const result = await listSecurityRules({
      ...options,
      page,
      size: 100,
    });
    rules.push(...(result.items ?? []));
    hasNext = Boolean(result.hasNext);
    page += 1;
  }

  return rules;
}

export async function createSecurityRule(
  rule: SecurityRule
): Promise<SecurityRule> {
  const { data } = await axiosInstance.post<SecurityRule>(
    SECURITY_RULES_ENDPOINT,
    normalizeSecurityRule(rule)
  );
  return data;
}

export async function updateSecurityRule(
  rule: SecurityRule
): Promise<SecurityRule> {
  const name = rule.metadata.name;
  const { data } = await axiosInstance.put<SecurityRule>(
    `${SECURITY_RULES_ENDPOINT}/${name}`,
    normalizeSecurityRule(rule)
  );
  return data;
}

export async function deleteSecurityRule(name: string): Promise<void> {
  await axiosInstance.delete(`${SECURITY_RULES_ENDPOINT}/${name}`);
}

export function createSecurityRuleDraft(
  listType: SecurityRuleListType = 'GRAY'
): SecurityRule {
  return {
    apiVersion: API_VERSION,
    kind: KIND,
    metadata: {
      name: createSecurityRuleName(listType),
    },
    spec: {
      enabled: true,
      listType,
      field: 'KEYWORD',
      matchType: 'CONTAINS',
      value: '',
      reason: '',
      priority: 0,
    },
  };
}

export function cloneSecurityRule(rule: SecurityRule): SecurityRule {
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

export function normalizeSecurityRule(rule: SecurityRule): SecurityRule {
  const draft = createSecurityRuleDraft();
  const spec = rule.spec ?? draft.spec;
  const listType = spec.listType === 'BLACK' ? 'BLACK' : 'GRAY';
  const field = normalizeField(spec.field);
  const matchType = normalizeMatchType(spec.matchType);

  return {
    apiVersion: API_VERSION,
    kind: KIND,
    metadata: {
      ...rule.metadata,
      name: rule.metadata?.name || createSecurityRuleName(listType),
    },
    spec: {
      enabled: spec.enabled !== false,
      listType,
      field,
      matchType,
      value: spec.value?.trim() ?? '',
      reason: spec.reason?.trim() || undefined,
      priority: Number.isFinite(Number(spec.priority))
        ? Math.trunc(Number(spec.priority))
        : 0,
    },
  };
}

function buildListParams(options: ListSecurityRulesOptions) {
  const params = new URLSearchParams();
  params.set('page', String(options.page ?? 1));
  params.set('size', String(options.size ?? 20));
  params.append('sort', 'spec.priority,asc');
  params.append('sort', 'metadata.creationTimestamp,desc');

  if (options.listType && options.listType !== 'ALL') {
    params.append('fieldSelector', `spec.listType=${options.listType}`);
  }

  if (options.field && options.field !== 'ALL') {
    params.append('fieldSelector', `spec.field=${options.field}`);
  }

  return params;
}

function normalizeField(value: string | undefined): SecurityRuleField {
  const fields: SecurityRuleField[] = [
    'IP',
    'EMAIL',
    'USERNAME',
    'KEYWORD',
    'DOMAIN',
    'UA',
  ];
  return fields.includes(value as SecurityRuleField)
    ? (value as SecurityRuleField)
    : 'KEYWORD';
}

function normalizeMatchType(value: string | undefined): SecurityRuleMatchType {
  const matchTypes: SecurityRuleMatchType[] = ['EXACT', 'CONTAINS', 'REGEX'];
  return matchTypes.includes(value as SecurityRuleMatchType)
    ? (value as SecurityRuleMatchType)
    : 'CONTAINS';
}

function createSecurityRuleName(listType: SecurityRuleListType): string {
  const random = Math.random().toString(36).slice(2, 8);
  return `comment-next-${listType.toLowerCase()}-rule-${Date.now().toString(36)}-${random}`;
}
