export interface CommentNextAuthorIdentity {
  userName?: string;
  displayName?: string;
  avatar?: string;
  identityMarks?: unknown[];
  decorations?: {
    avatarFrame?: unknown;
    title?: unknown;
    primaryBadge?: unknown;
    badgeShowcase?: unknown[];
    cardBackground?: unknown;
    nameStyle?: unknown;
  };
  [key: string]: unknown;
}

export interface CommentNextInteractionPlusConfig {
  enabled?: boolean;
  runtimeAvailable?: boolean;
  runtimeScript?: string;
}
