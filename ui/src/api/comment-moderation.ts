import { axiosInstance } from '@halo-dev/api-client';

const COMMENT_MODERATION_ENDPOINT =
  '/apis/api.commentnext.xhhao.com/v1alpha1/comments';
const REPLY_MODERATION_ENDPOINT =
  '/apis/api.commentnext.xhhao.com/v1alpha1/replies';

export type CommentModerationTarget = 'comment' | 'reply';

export interface CommentModerationRequest {
  top?: boolean;
  featured?: boolean;
  priority?: number;
}

export interface CommentModerationState {
  name: string;
  top: boolean;
  featured: boolean;
  priority: number;
}

export async function updateCommentModeration(
  target: CommentModerationTarget,
  name: string,
  request: CommentModerationRequest
): Promise<CommentModerationState> {
  const endpoint =
    target === 'comment' ? COMMENT_MODERATION_ENDPOINT : REPLY_MODERATION_ENDPOINT;

  const { data } = await axiosInstance.put<CommentModerationState>(
    `${endpoint}/${name}/moderation`,
    request
  );
  return data;
}
