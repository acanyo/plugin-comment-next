import { axiosInstance } from '@halo-dev/api-client';
import type { ResourceMetadata } from './metadata';

const AI_REPLY_RECORDS_ENDPOINT =
  '/apis/api.commentnext.xhhao.com/v1alpha1/ai/reply-records';

export type AiReplyRecordTarget = 'all' | 'comment' | 'reply';
export type AiReplyRecordTrigger = 'all' | 'auto' | 'manual' | 'mention';
export type AiReplyRecordStatus =
  | 'all'
  | 'pending'
  | 'published'
  | 'rejected'
  | 'failed';

export interface AiReplyRecord {
  apiVersion: 'api.commentnext.xhhao.com/v1alpha1';
  kind: 'AiReplyRecord';
  metadata: ResourceMetadata;
  spec: AiReplyRecordSpec;
}

export interface AiReplyRecordSpec {
  targetType: 'COMMENT' | 'REPLY';
  triggerType: 'AUTO' | 'MANUAL' | 'MENTION';
  targetName: string;
  commentName: string;
  quoteReplyName?: string;
  subject?: string;
  authorName?: string;
  sourceContent?: string;
  replyContent: string;
  replyCandidates?: AiReplyCandidate[];
  replyStyle?: string;
  selectedCandidateIndex?: number;
  candidateCount?: number;
  assistantName?: string;
  assistantUserName?: string;
  status: 'PENDING_REVIEW' | 'PUBLISHED' | 'REJECTED' | 'FAILED';
  publishMode: 'REVIEW' | 'AUTO';
  replyName?: string;
  error?: string;
  creationTime?: string;
  generatedAt?: string;
  reviewedAt?: string;
  publishedAt?: string;
}

export interface AiReplyCandidate {
  index: number;
  style?: string;
  content: string;
}

export interface AiReplyRecordPage {
  page: number;
  size: number;
  total: number;
  totalPages: number;
  hasNext: boolean;
  hasPrevious: boolean;
  items: AiReplyRecord[];
}

export interface ListAiReplyRecordsOptions {
  page?: number;
  size?: number;
  target?: AiReplyRecordTarget;
  trigger?: AiReplyRecordTrigger;
  status?: AiReplyRecordStatus;
  keyword?: string;
}

export async function listAiReplyRecords(
  options: ListAiReplyRecordsOptions
): Promise<AiReplyRecordPage> {
  const { data } = await axiosInstance.get<AiReplyRecordPage>(
    AI_REPLY_RECORDS_ENDPOINT,
    {
      params: {
        page: options.page ?? 1,
        size: options.size ?? 20,
        target: options.target ?? 'all',
        trigger: options.trigger ?? 'all',
        status: options.status ?? 'pending',
        keyword: options.keyword || undefined,
      },
    }
  );
  return data;
}

export async function publishAiReplyRecord(
  name: string,
  index?: number
): Promise<AiReplyRecord> {
  const { data } = await axiosInstance.post<AiReplyRecord>(
    `${AI_REPLY_RECORDS_ENDPOINT}/${name}/publish`,
    index ? { index } : undefined
  );
  return data;
}

export async function generateAiReplyForComment(
  name: string,
  options?: GenerateAiReplyOptions
): Promise<AiReplyRecord> {
  const { data } = await axiosInstance.post<AiReplyRecord>(
    `${AI_REPLY_RECORDS_ENDPOINT}/comments/${name}/generate`,
    options
  );
  return data;
}

export async function generateAiReplyForReply(
  name: string,
  options?: GenerateAiReplyOptions
): Promise<AiReplyRecord> {
  const { data } = await axiosInstance.post<AiReplyRecord>(
    `${AI_REPLY_RECORDS_ENDPOINT}/replies/${name}/generate`,
    options
  );
  return data;
}

export interface GenerateAiReplyOptions {
  style?: string;
  candidateCount?: number;
}

export async function selectAiReplyCandidate(
  name: string,
  index: number
): Promise<AiReplyRecord> {
  const { data } = await axiosInstance.post<AiReplyRecord>(
    `${AI_REPLY_RECORDS_ENDPOINT}/${name}/select`,
    { index }
  );
  return data;
}

export async function rejectAiReplyRecord(name: string): Promise<AiReplyRecord> {
  const { data } = await axiosInstance.post<AiReplyRecord>(
    `${AI_REPLY_RECORDS_ENDPOINT}/${name}/reject`
  );
  return data;
}
