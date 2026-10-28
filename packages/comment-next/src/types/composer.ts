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

import type { CommentNextSnippetAnchor } from './snippet';
