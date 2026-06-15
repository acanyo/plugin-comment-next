export type CommentNextAuthorRole = "admin" | "member" | "anonymous";

export type CommentNextBadgeTone = "first" | "admin" | "level" | "custom" | "muted";

export interface CommentNextBadge {
  id: string;
  label: string;
  tone: CommentNextBadgeTone;
  icon?: string;
  title?: string;
}

export interface CommentNextBadgeIdentifier {
  username?: string;
  email?: string;
  displayName?: string;
}

export interface CommentNextBadgeLevelRule {
  id: string;
  label: string;
  minComments: number;
  tone?: CommentNextBadgeTone;
  icon?: string;
  title?: string;
}

export interface CommentNextBadgeIdentityRule {
  id: string;
  label: string;
  match: CommentNextBadgeIdentifier;
  tone?: CommentNextBadgeTone;
  icon?: string;
  title?: string;
}

export interface CommentNextBadgeConfig {
  enableFirstCommentBadge?: boolean;
  firstCommentBadge?: Partial<CommentNextBadge>;
  adminIdentifiers?: CommentNextBadgeIdentifier[];
  adminBadge?: Partial<CommentNextBadge>;
  levelRules?: CommentNextBadgeLevelRule[];
  customRules?: CommentNextBadgeIdentityRule[];
}

export interface CommentNextAuthor {
  displayName: string;
  avatar?: string;
  website?: string;
  username?: string;
  email?: string;
  role?: CommentNextAuthorRole;
  activeCommentCount?: number;
  badges?: CommentNextBadge[];
}

export interface CommentNextCommentStats {
  upvotes?: number;
  replies?: number;
}

export interface CommentNextComment {
  id: string;
  content: string;
  creationTime?: string;
  approved?: boolean;
  private?: boolean;
  author: CommentNextAuthor;
  stats?: CommentNextCommentStats;
  replies?: CommentNextComment[];
}

export interface CommentNextCommentPage {
  page: number;
  size: number;
  total: number;
  totalPages: number;
  hasNext: boolean;
  hasPrevious: boolean;
  items: CommentNextComment[];
}
