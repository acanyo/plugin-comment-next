export interface CaptchaRequiredResponse {
  type: string;
  title: string;
  status: number;
  detail: string;
  captcha?: string;
}

const CAPTCHA_ENDPOINT = "/apis/api.commentnext.xhhao.com/v1alpha1/captcha/-/generate";
const CAPTCHA_CODE_HEADER = "X-Captcha-Code";
const CAPTCHA_REQUIRED_HEADER = "X-Require-Captcha";

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
  return response.status === 403 && response.headers.has(CAPTCHA_REQUIRED_HEADER);
}

export async function fetchCaptchaImage(baseUrl = ""): Promise<string> {
  const response = await fetch(resolveApiUrl(baseUrl, CAPTCHA_ENDPOINT), {
    credentials: "include",
    headers: {
      Accept: "text/plain",
    },
  });

  if (!response.ok) {
    throw new Error(`Failed to fetch captcha: ${response.status}`);
  }

  return response.text();
}

function resolveApiUrl(baseUrl: string, endpoint: string): string {
  if (!baseUrl) {
    return endpoint;
  }

  return `${baseUrl.replace(/\/$/, "")}${endpoint}`;
}
