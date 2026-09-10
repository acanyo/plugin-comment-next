import { axiosInstance } from '@halo-dev/api-client';

const COMMENT_SEARCH_ENDPOINT =
  '/apis/console.api.commentnext.xhhao.com/v1alpha1/comment-search';

export type CommentSearchTarget = 'all' | 'comment' | 'reply';

export type CommentSearchStatus = 'all' | 'visible' | 'pending' | 'hidden';

export interface CommentSearchItem {
  targetType: 'comment' | 'reply';
  name: string;
  parentName?: string;
  subject?: string;
  authorName: string;
  ownerKind?: string;
  email?: string;
  username?: string;
  ipAddress?: string;
  content?: string;
  userAgent?: string;
  approved: boolean;
  hidden: boolean;
  creationTime?: string;
}

export interface CommentSearchPage {
  page: number;
  size: number;
  total: number;
  totalPages: number;
  hasNext: boolean;
  hasPrevious: boolean;
  items: CommentSearchItem[];
}

export interface ListCommentSearchOptions {
  page?: number;
  size?: number;
  target?: CommentSearchTarget;
  status?: CommentSearchStatus;
  keyword?: string;
  email?: string;
  ipAddress?: string;
  author?: string;
  content?: string;
  subject?: string;
}

export async function listCommentSearch(
  options: ListCommentSearchOptions
): Promise<CommentSearchPage> {
  const { data } = await axiosInstance.get<CommentSearchPage>(
    COMMENT_SEARCH_ENDPOINT,
    {
      params: {
        page: options.page ?? 1,
        size: options.size ?? 20,
        target: options.target ?? 'all',
        status: options.status ?? 'all',
        keyword: options.keyword || undefined,
        email: options.email || undefined,
        ipAddress: options.ipAddress || undefined,
        author: options.author || undefined,
        content: options.content || undefined,
        subject: options.subject || undefined,
      },
    }
  );
  return data;
}
