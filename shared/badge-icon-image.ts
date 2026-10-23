import { bundledBadgeIcon } from './badge-icons';

export function badgeIconImage(
  icon?: string,
  color?: string
): string | undefined {
  const value = icon?.trim();
  if (!value) {
    return undefined;
  }

  const svg = bundledBadgeIcon(value) ?? decodeSvgImage(value) ?? value;
  if (svg.startsWith('<svg')) {
    // SVG in an image cannot inherit the badge's currentColor. Resolve only that
    // keyword; explicit fills and gradients keep their original colors.
    const fill =
      color &&
      /^(#(?:[\da-f]{3,4}|[\da-f]{6}|[\da-f]{8})|rgba?\([\d\s,./%]+\))$/i.test(
        color
      )
        ? color
        : '#4b5870';
    return `data:image/svg+xml,${encodeURIComponent(svg.replace(/currentColor/g, fill))}`;
  }

  if (
    /^(data:image\/(svg\+xml|png|jpe?g|gif|webp|avif)[;,]|https?:\/\/|\/)/i.test(
      value
    )
  ) {
    return value;
  }

  // Compatibility for old rules until they are saved again in Console.
  if (/^[a-z0-9-]+:[a-z0-9-]+$/i.test(value)) {
    const params = new URLSearchParams();
    if (color) {
      params.set('color', color);
    }
    return `https://api.iconify.design/${value}.svg?${params}`;
  }

  return undefined;
}

function decodeSvgImage(value: string): string | undefined {
  const match =
    /^data:image\/svg\+xml(?:;charset=[^;,]+)?(;base64)?,([\s\S]*)$/i.exec(
      value
    );
  if (!match || match[2] === undefined) {
    return undefined;
  }
  try {
    return match[1]
      ? new TextDecoder().decode(
          Uint8Array.from(atob(match[2]), (char) => char.charCodeAt(0))
        )
      : decodeURIComponent(match[2]);
  } catch {
    return undefined;
  }
}
