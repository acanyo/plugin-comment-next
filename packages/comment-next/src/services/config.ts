import type { CommentNextBadgeConfig } from '../types/comment';
import { resolveApiUrl } from './api';

export interface CommentNextPluginConfig {
  basic?: CommentNextBasicConfig;
  security?: CommentNextSecurityConfig;
  ai?: CommentNextAiConfig;
  upload?: CommentNextUploadConfig;
  editor?: CommentNextEditorConfig;
  badge?: CommentNextBadgeConfig;
}

export interface CommentNextGlobalInfo {
  allowAnonymousComments?: boolean;
}

export interface CommentNextBasicConfig {
  size?: number;
  replySize?: number;
  withReplies?: boolean;
  withReplySize?: number;
  enablePrivateComment?: boolean;
  showCommenterDevice?: boolean;
}

export interface CommentNextSecurityConfig {
  captcha?: {
    anonymousCommentCaptcha?: boolean;
  };
}

export interface CommentNextAiConfig {
  enabled?: boolean;
  allowAnonymous?: boolean;
  maxInputLength?: number;
  foundationAvailable?: boolean;
}

export type CommentNextImageUploadProvider =
  | 'DISABLED'
  | 'HALO_ATTACHMENT'
  | 'IMGBB';

export interface CommentNextUploadConfig {
  enabled?: boolean;
  allowAnonymousUpload?: boolean;
  anonymousProvider?: CommentNextImageUploadProvider;
  authenticatedProvider?: CommentNextImageUploadProvider;
  anonymousMaxSizeKb?: number;
  authenticatedMaxSizeKb?: number;
}

export interface CommentNextEditorConfig {
  placeholder?: string;
}

const CONFIG_ENDPOINT = '/apis/api.commentnext.xhhao.com/v1alpha1/config';
const GLOBAL_INFO_ENDPOINT = '/actuator/globalinfo';

export async function fetchPluginConfig(
  baseUrl = ''
): Promise<CommentNextPluginConfig> {
  const response = await fetch(resolveApiUrl(baseUrl, CONFIG_ENDPOINT), {
    credentials: 'include',
    headers: {
      Accept: 'application/json',
    },
  });

  if (!response.ok) {
    throw new Error(`Failed to fetch comment next config: ${response.status}`);
  }

  return response.json() as Promise<CommentNextPluginConfig>;
}

export async function fetchGlobalInfo(
  baseUrl = ''
): Promise<CommentNextGlobalInfo> {
  const response = await fetch(resolveApiUrl(baseUrl, GLOBAL_INFO_ENDPOINT), {
    credentials: 'include',
    headers: {
      Accept: 'application/json',
    },
  });

  if (!response.ok) {
    throw new Error(`Failed to fetch global info: ${response.status}`);
  }

  return response.json() as Promise<CommentNextGlobalInfo>;
}
