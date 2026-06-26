import { axiosInstance } from '@halo-dev/api-client';

const REPORT_RECORDS_ENDPOINT =
  '/apis/api.commentnext.xhhao.com/v1alpha1/report-records';

export type ReportRecordTarget = 'all' | 'comment' | 'reply';

export type ReportRecordReason =
  | 'all'
  | 'SPAM'
  | 'AD'
  | 'ABUSE'
  | 'PORN'
  | 'ILLEGAL'
  | 'OTHER';

export interface ReportRecord {
  name: string;
  targetType: 'comment' | 'reply';
  targetName: string;
  parentName?: string;
  authorName?: string;
  subject?: string;
  content?: string;
  targetExists: boolean;
  approved: boolean;
  hidden: boolean;
  targetCreationTime?: string;
  reason?: string;
  description?: string;
  identityType?: string;
  creationTime?: string;
  targetReportCount: number;
  autoPending: boolean;
}

export interface ReportRecordPage {
  page: number;
  size: number;
  total: number;
  totalPages: number;
  hasNext: boolean;
  hasPrevious: boolean;
  items: ReportRecord[];
}

export interface ListReportRecordsOptions {
  page?: number;
  size?: number;
  target?: ReportRecordTarget;
  reason?: ReportRecordReason;
  keyword?: string;
}

export async function listReportRecords(
  options: ListReportRecordsOptions
): Promise<ReportRecordPage> {
  const { data } = await axiosInstance.get<ReportRecordPage>(
    REPORT_RECORDS_ENDPOINT,
    {
      params: {
        page: options.page ?? 1,
        size: options.size ?? 20,
        target: options.target ?? 'all',
        reason: options.reason && options.reason !== 'all'
          ? options.reason
          : undefined,
        keyword: options.keyword || undefined,
      },
    }
  );
  return data;
}
