import type { ReportRecord } from '../api/report-records';

export type ReportRecordStatusState = 'default' | 'success' | 'warning' | 'error';

const reasonLabels: Record<string, string> = {
  SPAM: '垃圾灌水',
  AD: '广告推广',
  ABUSE: '辱骂骚扰',
  PORN: '色情低俗',
  ILLEGAL: '违法违规',
  OTHER: '其他问题',
};

const identityLabels: Record<string, string> = {
  USER: '登录用户',
  ANONYMOUS: '匿名访客',
};

export function getReportReasonText(reason?: string) {
  return reasonLabels[reason || ''] || reason || '其他问题';
}

export function getReportIdentityText(identityType?: string) {
  return identityLabels[identityType || ''] || identityType || '--';
}

export function getReportTargetText(record: ReportRecord) {
  return record.targetType === 'reply' ? '回复' : '评论';
}

export function getReportTargetState(record: ReportRecord): ReportRecordStatusState {
  return record.targetType === 'reply' ? 'default' : 'success';
}

export function getReportContentPreview(record: ReportRecord) {
  if (!record.targetExists) {
    return '目标内容已删除或不可用';
  }
  return record.content || '无内容';
}

export function getReportTargetStatusText(record: ReportRecord) {
  if (!record.targetExists) {
    return '目标缺失';
  }
  if (record.autoPending || !record.approved) {
    return '待审核';
  }
  if (record.hidden) {
    return '私密';
  }
  return '可见';
}

export function getReportTargetStatusState(
  record: ReportRecord
): ReportRecordStatusState {
  if (!record.targetExists) {
    return 'default';
  }
  if (record.autoPending || !record.approved) {
    return 'warning';
  }
  if (record.hidden) {
    return 'default';
  }
  return 'success';
}
