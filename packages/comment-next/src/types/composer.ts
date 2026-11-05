import type { CommentNextComment } from './comment';
import type { CommentNextSnippetAnchor } from './snippet';

export interface CommentNextComposerOwner {
  displayName: string;
  email: string;
  website?: string;
}

export interface CommentNextComposerSubmitPayload {
  snippet?: CommentNextSnippetAnchor;
  content: string;
  hidden: boolean;
  captchaCode: string;
  owner?: CommentNextComposerOwner;
}

export interface CommentNextReplyTarget {
  /** Name of the root comment, including when replying to another reply. */
  commentName: string;
  replyName?: string;
  displayName?: string;
}

export interface CommentNextCreatedEventDetail {
  type: 'comment' | 'reply';
  subject: { group: string; kind: string; version: string; name: string };
  commentName: string;
  replyTarget?: CommentNextReplyTarget;
  result: CommentNextComment;
}
