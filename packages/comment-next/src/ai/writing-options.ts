import type { CommentNextAiMode } from '../services/ai';

export interface AiWritingMode {
  key: CommentNextAiMode;
  label: string;
  hint: string;
  tags: string[];
  contentRequired?: boolean;
  variant?: 'comment' | 'reply';
}

export const MAX_WRITING_TAGS = 8;

export const AI_WRITING_MODES: AiWritingMode[] = [
  {
    key: 'generate',
    label: '生成评论',
    hint: '至少选择 1 个标签，再生成评论',
    tags: [
      '内容实用',
      '解决了我的问题',
      '感谢分享',
      '学到了',
      '讲得很清楚',
      '步骤详细',
      '新手友好',
      '引发思考',
      '说到心坎里了',
      '补充一点经验',
      '有不同看法',
      '想了解更多',
      '期待后续更新',
      '已收藏',
      '会推荐给朋友',
      '观点新颖',
      '值得讨论',
      '请教一个问题',
    ],
  },
  {
    key: 'polish',
    label: '润色',
    hint: '保留原意，按选中的方向优化表达',
    contentRequired: true,
    tags: [
      '更自然',
      '更简洁',
      '更有条理',
      '更礼貌',
      '轻松一点',
      '保留我的语气',
      '修正错别字',
      '重点更突出',
    ],
  },
  {
    key: 'expand',
    label: '扩展',
    hint: '围绕已有想法，补充表达和讨论角度',
    contentRequired: true,
    tags: [
      '补充观点',
      '说明原因',
      '展开细节',
      '联系实际',
      '加入思考',
      '换个角度',
      '提出建议',
      '保留原意',
    ],
  },
  {
    key: 'question',
    label: '提问',
    hint: '把已有想法整理成清晰、具体的问题',
    contentRequired: true,
    tags: [
      '询问细节',
      '请教方法',
      '讨论原因',
      '了解适用场景',
      '比较不同方案',
      '进一步追问',
    ],
  },
  {
    key: 'reply',
    label: '生成回复',
    hint: '结合回复对象，整理你想表达的意思',
    variant: 'reply',
    tags: [
      '表示认同',
      '感谢解答',
      '补充说明',
      '礼貌讨论',
      '继续追问',
      '分享想法',
    ],
  },
  {
    key: 'summary',
    label: '读文评论',
    hint: '结合文章内容，写一条有重点的评论',
    variant: 'comment',
    tags: [
      '主要收获',
      '核心观点',
      '值得讨论',
      '提出问题',
      '实践启发',
      '简短感受',
    ],
  },
];

export function writingModes(variant: 'comment' | 'reply'): AiWritingMode[] {
  return AI_WRITING_MODES.filter(
    (mode) => !mode.variant || mode.variant === variant
  );
}

export function writingInputHint(
  mode: AiWritingMode,
  hasContent: boolean,
  hasSubject: boolean,
  hasReplyTarget: boolean,
  selectedCount: number
): string {
  if (mode.contentRequired && !hasContent)
    return '请先在评论框中写下想法，再使用这个功能。';
  if (mode.key === 'summary' && !hasSubject)
    return '当前页面没有可用的文章信息，请选择其他方式。';
  if (mode.key === 'reply' && !hasContent && !hasReplyTarget)
    return '请先选择回复对象，或在评论框中写下想法。';
  if (mode.key === 'generate' && !selectedCount) return mode.hint;
  return '';
}
