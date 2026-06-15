import type { CommentNextBadgeConfig } from "../types/comment";

export interface CommentNextPluginConfig {
  basic?: CommentNextBasicConfig;
  security?: CommentNextSecurityConfig;
  editor?: CommentNextEditorConfig;
  badge?: CommentNextBadgeConfig;
}

export interface CommentNextBasicConfig {
  size?: number;
  replySize?: number;
  withReplies?: boolean;
  withReplySize?: number;
  enablePrivateComment?: boolean;
}

export interface CommentNextSecurityConfig {
  captcha?: {
    anonymousCommentCaptcha?: boolean;
  };
}

export interface CommentNextEditorConfig {
  placeholder?: string;
}

const CONFIG_ENDPOINT = "/apis/api.commentnext.xhhao.com/v1alpha1/config";

export async function fetchPluginConfig(baseUrl = ""): Promise<CommentNextPluginConfig> {
  const response = await fetch(resolveApiUrl(baseUrl, CONFIG_ENDPOINT), {
    credentials: "include",
    headers: {
      Accept: "application/json",
    },
  });

  if (!response.ok) {
    throw new Error(`Failed to fetch comment next config: ${response.status}`);
  }

  return response.json() as Promise<CommentNextPluginConfig>;
}

function resolveApiUrl(baseUrl: string, endpoint: string): string {
  if (!baseUrl) {
    return endpoint;
  }

  return `${baseUrl.replace(/\/$/, "")}${endpoint}`;
}
