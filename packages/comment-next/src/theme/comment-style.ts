export interface CommentNextStyle {
  lightAccent?: string | null;
  darkAccent?: string | null;
  radius?: number | null;
  density?: 'compact' | 'spacious' | null;
  listStyle?: 'flat' | 'cards' | null;
}

export function resolveCommentStyle(style?: CommentNextStyle) {
  const variables: string[] = [];
  appendAccent(variables, 'light', style?.lightAccent);
  appendAccent(variables, 'dark', style?.darkAccent);
  const customAccent = variables.length > 0;

  const radius = style?.radius;
  if (
    typeof radius === 'number' &&
    Number.isInteger(radius) &&
    radius >= 0 &&
    radius <= 24
  ) {
    variables.push(`--comment-next-style-radius:${radius}px`);
  }
  return {
    variables: variables.join(';') || undefined,
    customAccent,
    density:
      style?.density === 'compact' || style?.density === 'spacious'
        ? style.density
        : undefined,
    listStyle: style?.listStyle === 'cards' ? 'cards' : undefined,
  };
}

function appendAccent(
  variables: string[],
  scheme: 'light' | 'dark',
  accent?: string | null
) {
  if (typeof accent !== 'string' || !/^#[0-9a-f]{6}$/i.test(accent)) return;
  const luminance =
    0.2126 * linearChannel(accent.slice(1, 3)) +
    0.7152 * linearChannel(accent.slice(3, 5)) +
    0.0722 * linearChannel(accent.slice(5, 7));
  variables.push(
    `--comment-next-style-${scheme}-accent:${accent}`,
    `--comment-next-style-${scheme}-accent-tint:color-mix(in srgb,${accent} 12%,transparent)`,
    `--comment-next-style-${scheme}-ink:${luminance > 0.179 ? '#000' : '#fff'}`
  );
}

function linearChannel(hex: string): number {
  const sRgb = Number.parseInt(hex, 16) / 255;
  return sRgb <= 0.04045 ? sRgb / 12.92 : ((sRgb + 0.055) / 1.055) ** 2.4;
}
