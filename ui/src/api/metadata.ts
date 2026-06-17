export interface ResourceMetadata {
  name: string;
  creationTimestamp?: string;
  deletionTimestamp?: string | null;
  version?: number;
  labels?: Record<string, string>;
  annotations?: Record<string, string>;
}
