import { axiosInstance } from '@halo-dev/api-client';

const AI_MODERATION_RECORDS_ENDPOINT =
  '/apis/api.commentnext.xhhao.com/v1alpha1/ai/moderation-records';

export type AiModerationTarget = 'all' | 'comment' | 'reply';

export interface AiModerationRecord {
  targetType: 'comment' | 'reply';
  name: string;
  parentName?: string;
  authorName?: string;
  subject?: string;
  content?: string;
  approved: boolean;
  hidden: boolean;
  creationTime?: string;
  intercepted: boolean;
  rejected: boolean;
  action?: string;
  categories: string[];
  labels: string[];
  confidence: number;
  reason?: string;
  reviewedAt?: string;
}

export interface AiModerationRecordPage {
  page: number;
  size: number;
  total: number;
  totalPages: number;
  hasNext: boolean;
  hasPrevious: boolean;
  items: AiModerationRecord[];
}

export interface ListAiModerationRecordsOptions {
  page?: number;
  size?: number;
  target?: AiModerationTarget;
  intercepted?: boolean;
  keyword?: string;
}

export async function listAiModerationRecords(
  options: ListAiModerationRecordsOptions
): Promise<AiModerationRecordPage> {
  const { data } = await axiosInstance.get<AiModerationRecordPage>(
    AI_MODERATION_RECORDS_ENDPOINT,
    {
      params: {
        page: options.page ?? 1,
        size: options.size ?? 20,
        target: options.target ?? 'all',
        intercepted: options.intercepted ?? true,
        keyword: options.keyword || undefined,
      },
    }
  );
  return data;
}

export async function approveAiModerationRecord(
  targetType: AiModerationRecord['targetType'],
  name: string
): Promise<void> {
  await axiosInstance.put(
    `${AI_MODERATION_RECORDS_ENDPOINT}/${targetType}/${name}/approve`
  );
}
