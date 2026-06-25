import { axiosInstance } from '@halo-dev/api-client';
import type { ResourceMetadata } from './metadata';

const FEATURED_COMMENTS_ENDPOINT =
  '/apis/api.commentnext.xhhao.com/v1alpha1/comments/featured';

export type FeaturedCommentTarget = 'all' | 'comment' | 'reply';

export interface FeaturedCommentOwner {
  displayName?: string;
  avatar?: string;
  kind?: string;
  name?: string;
  role?: string;
}

export interface FeaturedCommentSpecOwner {
  displayName?: string;
  name?: string;
  kind?: string;
}

export interface FeaturedCommentSpec {
  content?: string;
  creationTime?: string;
  approved?: boolean;
  hidden?: boolean;
  quoteReply?: string;
  top?: boolean;
  priority?: number;
  owner?: FeaturedCommentSpecOwner;
}

export interface FeaturedCommentItem {
  targetType: 'comment' | 'reply';
  parentName?: string;
  subject?: string;
  featuredAt?: string;
  metadata: ResourceMetadata;
  spec?: FeaturedCommentSpec;
  owner?: FeaturedCommentOwner;
  top?: boolean;
  featured?: boolean;
  priority?: number;
  stats?: {
    upvote?: number;
  };
}

export interface FeaturedCommentPage {
  page: number;
  size: number;
  total: number;
  totalPages: number;
  hasNext: boolean;
  hasPrevious: boolean;
  items: FeaturedCommentItem[];
}

export interface ListFeaturedCommentsOptions {
  page?: number;
  size?: number;
  target?: FeaturedCommentTarget;
  keyword?: string;
}

export async function listFeaturedComments(
  options: ListFeaturedCommentsOptions
): Promise<FeaturedCommentPage> {
  const { data } = await axiosInstance.get<FeaturedCommentPage>(
    FEATURED_COMMENTS_ENDPOINT,
    {
      params: {
        page: options.page ?? 1,
        size: options.size ?? 20,
        target: options.target ?? 'all',
        keyword: options.keyword || undefined,
      },
    }
  );
  return data;
}
