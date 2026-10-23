import type { CommentNextAvatarConfig } from '../services/config';

/** Stable pseudo-random assignment without retaining or publishing the identity. */
function hashIdentity(identity: string): number {
  let hash = 2166136261;
  for (const char of identity.trim().toLowerCase()) {
    hash = Math.imul(hash ^ (char.codePointAt(0) ?? 0), 16777619);
  }
  return hash >>> 0;
}

export function generatedAvatar(identity: string): string {
  const hash = hashIdentity(identity || 'anonymous');
  const hue = hash % 360;
  let bits = hash;
  const cells: string[] = [];

  for (let row = 0; row < 5; row++) {
    for (let column = 0; column < 3; column++) {
      // Xorshift produces a repeatable, varied pattern from this identity.
      bits ^= bits << 13;
      bits ^= bits >>> 17;
      bits ^= bits << 5;
      if ((bits & 1) === 0 && !(row === 2 && column === 2)) continue;
      cells.push(
        `<rect x="${column + 1}" y="${row + 1}" width="1" height="1"/>`
      );
      if (column < 2) {
        cells.push(
          `<rect x="${5 - column}" y="${row + 1}" width="1" height="1"/>`
        );
      }
    }
  }

  const svg = `<svg xmlns="http://www.w3.org/2000/svg" viewBox="0 0 7 7"><path fill="hsl(${hue} 40% 94%)" d="M0 0h7v7H0z"/><g fill="hsl(${hue} 55% 43%)">${cells.join('')}</g></svg>`;
  return `data:image/svg+xml,${encodeURIComponent(svg)}`;
}

export function avatarCandidates(
  src: string,
  identity: string,
  config?: CommentNextAvatarConfig
): string[] {
  const primary = primaryAvatar(src);
  // The email hash is already public in the avatar URL; never embed raw email in SVG.
  const emailHash = /^https?:\/\/weavatar\.com\/avatar\/([^?]+)/i.exec(
    primary
  )?.[1];
  const seed = emailHash || identity.trim() || primary || 'anonymous';
  const pool = (config?.fallbackAvatars ?? [])
    .map((item) => imageSource(item?.url ?? ''))
    .filter(Boolean);
  const custom = pool.length ? pool[hashIdentity(seed) % pool.length] : '';
  return [...new Set([primary, custom, generatedAvatar(seed)].filter(Boolean))];
}

function imageSource(value: string): string {
  const source = value.trim();
  return /^(?:https?:\/\/|\/(?!\/)|data:image\/)/i.test(source) ? source : '';
}

function primaryAvatar(value: string): string {
  const source = imageSource(value);
  if (!/^https?:\/\/weavatar\.com\/avatar\//i.test(source)) return source;
  const url = new URL(source);
  if (url.pathname === '/avatar/') return '';
  // Also handle avatar annotations and cached responses created before this change.
  url.searchParams.set('d', '404');
  return url.href;
}
