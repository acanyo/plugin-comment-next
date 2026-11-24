import type { CommentNextCommentPage } from '../types/comment';
import type { CommentAnchorTarget } from '../utils/comment-anchor';
import { resolveApiUrl } from './api';
import {
  COMMENTS_ENDPOINT,
  type FetchCommentsOptions,
  fetchCommentPage,
  fetchReplyPage,
} from './comments';

interface CommentLocation {
  commentName: string;
  page: number;
  replyPage: number | null;
}

export interface LinkedReplyPage {
  commentName: string;
  data: CommentNextCommentPage;
}

export async function fetchLinkedCommentPage(
  options: FetchCommentsOptions,
  target: CommentAnchorTarget
): Promise<
  { data: CommentNextCommentPage; replies?: LinkedReplyPage } | undefined
> {
  // Moderation or new comments can move a target between the location and page requests.
  for (let attempt = 0; attempt < 2; attempt += 1) {
    const location = await fetchLocation(options, target);
    if (!location) return undefined;

    const data = await fetchCommentPage({ ...options, page: location.page });
    const parent = [...(data.pinnedItems ?? []), ...data.items].find(
      (comment) => comment.id === location.commentName
    );
    if (!parent) continue;
    if (
      target.kind === 'comment' ||
      parent.replies?.some((reply) => reply.id === target.id)
    ) {
      return { data };
    }

    const replies = await fetchReplyPage({
      baseUrl: options.baseUrl,
      commentName: location.commentName,
      page: location.replyPage ?? 1,
      size: options.replySize,
      signal: options.signal,
    });
    if (replies.items.some((reply) => reply.id === target.id)) {
      return {
        data,
        replies: { commentName: location.commentName, data: replies },
      };
    }
  }
}

async function fetchLocation(
  options: FetchCommentsOptions,
  target: CommentAnchorTarget
) {
  const url = new URL(
    resolveApiUrl(options.baseUrl ?? '', `${COMMENTS_ENDPOINT}/locate`),
    window.location.origin
  );
  url.search = new URLSearchParams({
    group: options.group,
    kind: options.kind,
    name: options.name,
    version: options.version,
    size: String(options.size ?? 20),
    replySize: String(options.replySize ?? 10),
    sort: options.sort ?? 'latest',
    target: target.kind,
    targetName: target.id,
  }).toString();
  if (options.separatePinned === false) {
    url.searchParams.set('separatePinned', 'false');
  }

  const response = await fetch(url, {
    credentials: 'include',
    cache: 'no-store',
    signal: options.signal,
    headers: { Accept: 'application/json' },
  });
  if (response.status === 404) return undefined;
  if (!response.ok)
    throw new Error(`Failed to locate comment: ${response.status}`);

  const location = (await response.json()) as CommentLocation;
  if (
    typeof location?.commentName !== 'string' ||
    !location.commentName ||
    !isPageNumber(location.page) ||
    (target.kind === 'reply' && !isPageNumber(location.replyPage))
  ) {
    throw new Error('Invalid comment location');
  }
  return location;
}

function isPageNumber(page: number | null): page is number {
  return page !== null && Number.isSafeInteger(page) && page >= 1;
}
