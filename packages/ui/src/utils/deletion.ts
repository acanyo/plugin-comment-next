import type { ResourceMetadata } from '../api/metadata';

export interface ResourceWithMetadata {
  metadata?: Pick<ResourceMetadata, 'deletionTimestamp'>;
}

export function isDeletingResource(resource?: ResourceWithMetadata): boolean {
  return Boolean(resource?.metadata?.deletionTimestamp);
}

export function hasDeletingResources(
  resources?: ResourceWithMetadata[]
): boolean {
  return Boolean(resources?.some(isDeletingResource));
}
