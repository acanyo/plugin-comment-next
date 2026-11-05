import type { CommentNextReplyTarget } from '../types/composer';

export function normalizeReplyTarget(
  value: unknown
): CommentNextReplyTarget | undefined {
  if (!value || typeof value !== 'object') return undefined;
  const target = value as Partial<CommentNextReplyTarget>;
  if (typeof target.commentName !== 'string' || !target.commentName.trim()) {
    return undefined;
  }
  return {
    commentName: target.commentName.trim(),
    replyName:
      typeof target.replyName === 'string'
        ? target.replyName.trim() || undefined
        : undefined,
    displayName:
      typeof target.displayName === 'string'
        ? target.displayName.trim() || undefined
        : undefined,
  };
}
