import { hashAvatarIdentity } from './email-hash';

const WEAVATAR_BASE_URL = 'https://weavatar.com';
const DEFAULT_AVATAR = '404';
const DEFAULT_SIZE = 96;

export function getDefaultAnonymousAvatarUrl(): string {
  return '';
}

export async function getAnonymousAvatarUrl(
  email: string,
  size = DEFAULT_SIZE
): Promise<string> {
  const hash = await hashAvatarIdentity(email);

  return buildWeAvatarUrl({ hash, size });
}

export function getAnonymousAvatarUrlFromHash(
  hash: string,
  size = DEFAULT_SIZE
): string {
  return buildWeAvatarUrl({ hash: hash.trim(), size });
}

function buildWeAvatarUrl({
  hash = '',
  size = DEFAULT_SIZE,
  defaultAvatar = DEFAULT_AVATAR,
}: {
  hash?: string;
  size?: number;
  defaultAvatar?: string;
}): string {
  if (!hash.trim()) {
    return getDefaultAnonymousAvatarUrl();
  }

  const normalizedSize = Math.min(Math.max(Math.round(size), 10), 2000);
  const avatarPath = `/avatar/${encodeURIComponent(hash)}`;
  const params = new URLSearchParams({
    d: defaultAvatar,
    f: 'webp',
    s: String(normalizedSize),
  });

  return `${WEAVATAR_BASE_URL}${avatarPath}?${params.toString()}`;
}
