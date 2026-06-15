import type { CommentNextComment, CommentNextCommentPage } from "../types/comment";

interface FetchCommentsOptions {
  baseUrl?: string;
  group: string;
  kind: string;
  name: string;
  version: string;
  page?: number;
  size?: number;
  withReplies?: boolean;
  replySize?: number;
}

interface HaloCommentPage {
  page?: number;
  size?: number;
  total?: number;
  totalPages?: number;
  hasNext?: boolean;
  hasPrevious?: boolean;
  items?: HaloComment[];
}

interface HaloComment {
  metadata?: {
    name?: string;
    creationTimestamp?: string;
  };
  owner?: {
    displayName?: string;
    avatar?: string;
    name?: string;
  };
  spec?: {
    content?: string;
    creationTime?: string;
    approved?: boolean;
    hidden?: boolean;
    owner?: {
      name?: string;
      displayName?: string;
      email?: string;
      annotations?: {
        website?: string;
      };
    };
  };
  stats?: {
    upvote?: number;
  };
  status?: {
    visibleReplyCount?: number;
  };
  replies?: HaloCommentPage;
}

export async function fetchCommentPage(options: FetchCommentsOptions): Promise<CommentNextCommentPage> {
  const page = options.page ?? 1;
  const size = options.size ?? 20;
  const url = new URL(`${options.baseUrl ?? ""}/apis/api.halo.run/v1alpha1/comments`, window.location.origin);

  url.searchParams.set("group", options.group);
  url.searchParams.set("kind", options.kind);
  url.searchParams.set("name", options.name);
  url.searchParams.set("version", options.version);
  url.searchParams.set("page", String(page));
  url.searchParams.set("size", String(size));
  url.searchParams.set("withReplies", String(options.withReplies ?? true));
  url.searchParams.set("replySize", String(options.replySize ?? 10));

  const response = await fetch(url, {
    credentials: "include",
    headers: {
      Accept: "application/json",
    },
  });

  if (!response.ok) {
    throw new Error(`Failed to fetch comments: ${response.status}`);
  }

  return adaptHaloCommentPage((await response.json()) as HaloCommentPage, page, size);
}

function adaptHaloCommentPage(data: HaloCommentPage, fallbackPage: number, fallbackSize: number): CommentNextCommentPage {
  return {
    page: data.page ?? fallbackPage,
    size: data.size ?? fallbackSize,
    total: data.total ?? data.items?.length ?? 0,
    totalPages: data.totalPages ?? 1,
    hasNext: Boolean(data.hasNext),
    hasPrevious: Boolean(data.hasPrevious),
    items: (data.items ?? []).map(adaptHaloComment),
  };
}

function adaptHaloComment(comment: HaloComment): CommentNextComment {
  const owner = comment.owner ?? {};
  const specOwner = comment.spec?.owner ?? {};
  const displayName = owner.displayName || specOwner.displayName || specOwner.name || owner.name || "匿名用户";

  return {
    id: comment.metadata?.name || crypto.randomUUID(),
    content: comment.spec?.content ?? "",
    creationTime: comment.spec?.creationTime ?? comment.metadata?.creationTimestamp,
    approved: comment.spec?.approved,
    private: comment.spec?.hidden,
    author: {
      displayName,
      avatar: owner.avatar,
      website: specOwner.annotations?.website,
      username: owner.name || specOwner.name,
      email: specOwner.email,
      role: specOwner.name || owner.name ? "member" : "anonymous",
    },
    stats: {
      upvotes: comment.stats?.upvote ?? 0,
      replies: comment.status?.visibleReplyCount ?? comment.replies?.total ?? 0,
    },
    replies: (comment.replies?.items ?? []).map(adaptHaloComment),
  };
}
