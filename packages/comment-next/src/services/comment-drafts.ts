import type { CommentNextReplyTarget } from '../types/composer';
import type { CommentNextSnippetAnchor } from '../types/snippet';
import { sanitizeCommentHtml, sanitizeConsoleCommentHtml } from '../utils/html';
import { resolveApiUrl } from './api';

export interface CommentDraft {
  html: string;
  snippet?: CommentNextSnippetAnchor;
  privateComment: boolean;
}

const DRAFT_PREFIX = 'halo-comment-next:draft:v1:';
const LOGIN_DRAFT_KEY = `${DRAFT_PREFIX}after-login`;

export function commentDraftTarget(
  baseUrl: string,
  subjectOrReply: string | CommentNextReplyTarget
): string | undefined {
  try {
    const site = new URL(resolveApiUrl(baseUrl, '/'), window.location.href)
      .href;
    const target =
      typeof subjectOrReply === 'string'
        ? ['comment', subjectOrReply]
        : ['reply', subjectOrReply.commentName, subjectOrReply.replyName ?? ''];
    return JSON.stringify([site, ...target]);
  } catch {
    return undefined;
  }
}

export function commentDraftKey(target: string, username?: string): string {
  return DRAFT_PREFIX + JSON.stringify([target, username ?? null]);
}

export function readCommentDraft(key: string) {
  try {
    const serialized = localStorage.getItem(key);
    if (!serialized) return undefined;
    const draft = normalizeDraft(JSON.parse(serialized));
    return draft ? { serialized, draft } : undefined;
  } catch {
    return undefined;
  }
}

export function rememberCommentDraftForLogin(
  target: string | undefined,
  draft?: CommentDraft
) {
  try {
    if (!target || !draft) {
      sessionStorage.removeItem(LOGIN_DRAFT_KEY);
      return;
    }
    sessionStorage.setItem(LOGIN_DRAFT_KEY, JSON.stringify({ target, draft }));
  } catch {
    // The original draft remains available in local storage.
  }
}

export function takeCommentDraftAfterLogin(target: string) {
  try {
    const saved = sessionStorage.getItem(LOGIN_DRAFT_KEY);
    if (!saved) return undefined;
    const handoff = JSON.parse(saved) as {
      target?: string;
      draft?: Partial<CommentDraft>;
    } | null;
    if (handoff?.target !== target) return undefined;
    sessionStorage.removeItem(LOGIN_DRAFT_KEY);
    return normalizeDraft(handoff.draft);
  } catch {
    return undefined;
  }
}

function normalizeDraft(
  draft?: Partial<CommentDraft> | null
): CommentDraft | undefined {
  if (typeof draft?.html !== 'string') return undefined;
  return {
    html: draft.html,
    snippet: readSnippet(draft.snippet),
    privateComment: draft.privateComment === true,
  };
}

export function saveCommentDraft(key: string, serialized: string): boolean {
  try {
    localStorage.setItem(key, serialized);
    return true;
  } catch {
    return false;
  }
}

export function clearCommentDraft(key: string, serialized: string): void {
  try {
    if (localStorage.getItem(key) === serialized) localStorage.removeItem(key);
  } catch {
    // The current editor remains usable when browser storage is unavailable.
  }
}

export function cleanDraftHtml(
  html: string,
  allowImages: boolean,
  allowedLottieHosts?: readonly unknown[]
): string {
  if (!html.trim()) return '';
  const template = document.createElement('template');
  template.innerHTML = html;
  // Pending files use object URLs which expire when the page closes.
  for (const image of template.content.querySelectorAll('img[src]')) {
    if (/^(blob|data):/i.test(image.getAttribute('src')?.trim() ?? '')) {
      image.remove();
    }
  }
  const sanitize = allowImages
    ? sanitizeCommentHtml
    : sanitizeConsoleCommentHtml;
  return sanitize(template.innerHTML, allowedLottieHosts);
}

function readSnippet(value?: CommentNextSnippetAnchor) {
  return value?.version === 1 &&
    typeof value.exact === 'string' &&
    typeof value.prefix === 'string' &&
    typeof value.suffix === 'string' &&
    Number.isSafeInteger(value.start) &&
    value.start >= 0
    ? value
    : undefined;
}
