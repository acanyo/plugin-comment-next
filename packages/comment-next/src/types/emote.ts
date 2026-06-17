export type CommentNextEmoteType = 'emoticon' | 'image';

export interface CommentNextEmoteItem {
  id: string;
  type: CommentNextEmoteType;
  label: string;
  value: string;
  description?: string;
  previewSrc?: string;
  originSrc?: string;
  src?: string;
}

export interface CommentNextEmotePack {
  id: string;
  name: string;
  type: CommentNextEmoteType;
  items: CommentNextEmoteItem[];
}

export interface CommentNextRawEmotePack {
  type?: string;
  container?: unknown[];
}

export type CommentNextRawEmotePacks = Record<
  string,
  CommentNextRawEmotePack
>;
