export interface CommentNextSnippetSelection {
  text: string;
  left: number;
  top: number;
  anchor: CommentNextSnippetAnchor;
  thread?: CommentNextSnippetThread;
  preview?: boolean;
}

export interface CommentNextSnippetAnchor {
  version: 1;
  exact: string;
  prefix: string;
  suffix: string;
  start: number;
}

export interface CommentNextSnippetThread {
  key: string;
  anchor: CommentNextSnippetAnchor;
  total: number;
}
