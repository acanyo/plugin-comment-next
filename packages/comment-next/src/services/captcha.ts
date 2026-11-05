export interface CaptchaRequiredResponse {
  type: string;
  title: string;
  status: number;
  detail: string;
  captcha?: string;
  captchaType?: CommentNextCaptchaType;
}

export type CommentNextCaptchaType =
  | 'ALPHANUMERIC'
  | 'ARITHMETIC'
  | 'GEETEST'
  | 'ALTCHA'
  | 'CAP'
  | 'TURNSTILE'
  | 'RECAPTCHA';

export type CommentNextRecaptchaVersion = 'V2' | 'V3';

export interface CommentNextGeeTestCaptchaConfig {
  captchaId?: string;
  apiServer?: string;
}

export interface CommentNextAltchaCaptchaConfig {
  algorithm?: string;
  cost?: number;
  expiresInSeconds?: number;
}

export interface CommentNextCapCaptchaConfig {
  apiEndpoint?: string;
}

export interface CommentNextTurnstileCaptchaConfig {
  siteKey?: string;
}

export interface CommentNextRecaptchaCaptchaConfig {
  siteKey?: string;
  version?: CommentNextRecaptchaVersion;
  scoreThreshold?: number;
  action?: string;
  apiServer?: string;
}

/**
 * Google's official alternative domain for reCAPTCHA. It exposes the same
 * endpoints as www.google.com and is reachable from mainland China.
 */
export const RECAPTCHA_DEFAULT_API_SERVER = 'https://www.recaptcha.net';

export interface CommentNextCaptchaConfig {
  anonymousCommentCaptcha?: boolean;
  type?: CommentNextCaptchaType;
  geeTest?: CommentNextGeeTestCaptchaConfig;
  altcha?: CommentNextAltchaCaptchaConfig;
  cap?: CommentNextCapCaptchaConfig;
  turnstile?: CommentNextTurnstileCaptchaConfig;
  recaptcha?: CommentNextRecaptchaCaptchaConfig;
}

const CAPTCHA_ENDPOINT =
  '/apis/api.commentnext.xhhao.com/v1alpha1/captcha/-/generate';
const ALTCHA_CHALLENGE_ENDPOINT =
  '/apis/api.commentnext.xhhao.com/v1alpha1/captcha/-/altcha-challenge';
const CAPTCHA_CODE_HEADER = 'X-Captcha-Code';
const CAPTCHA_REQUIRED_HEADER = 'X-Require-Captcha';

export function getCaptchaCodeHeader(code: string): Record<string, string> {
  const normalizedCode = code.trim();

  if (!normalizedCode) {
    return {};
  }

  return {
    [CAPTCHA_CODE_HEADER]: normalizedCode,
  };
}

export function isCaptchaRequired(response: Response): boolean {
  return (
    response.status === 403 && response.headers.has(CAPTCHA_REQUIRED_HEADER)
  );
}

export function isLocalImageCaptcha(type: CommentNextCaptchaType): boolean {
  return type === 'ALPHANUMERIC' || type === 'ARITHMETIC';
}

export async function fetchCaptchaImage(baseUrl = ''): Promise<string> {
  const response = await fetch(resolveApiUrl(baseUrl, CAPTCHA_ENDPOINT), {
    credentials: 'include',
    headers: {
      Accept: 'text/plain',
    },
  });

  if (!response.ok) {
    throw new Error(`Failed to fetch captcha: ${response.status}`);
  }

  return response.text();
}

export function getAltchaChallengeUrl(baseUrl = ''): string {
  return resolveApiUrl(baseUrl, ALTCHA_CHALLENGE_ENDPOINT);
}

/**
 * Resolves the configured reCAPTCHA service origin. Both official domains and
 * self-hosted proxies are accepted; anything unparsable falls back to the
 * default so the widget never silently points at a broken host.
 */
export function resolveRecaptchaApiServer(apiServer?: string): string {
  const value = apiServer?.trim();
  if (!value) {
    return RECAPTCHA_DEFAULT_API_SERVER;
  }

  try {
    const url = new URL(value);
    if (url.protocol !== 'https:' && url.protocol !== 'http:') {
      return RECAPTCHA_DEFAULT_API_SERVER;
    }
    if (!url.hostname) {
      return RECAPTCHA_DEFAULT_API_SERVER;
    }
    return url.origin;
  } catch {
    return RECAPTCHA_DEFAULT_API_SERVER;
  }
}

export function getRecaptchaScriptUrl(
  apiServer?: string,
  language = 'zh-CN'
): string {
  const base = resolveRecaptchaApiServer(apiServer);
  return `${base}/recaptcha/api.js?render=explicit&hl=${encodeURIComponent(
    language
  )}`;
}

export function resolveApiUrl(baseUrl: string, endpoint: string): string {
  if (!baseUrl) {
    return endpoint;
  }

  return `${baseUrl.replace(/\/$/, '')}${endpoint}`;
}
