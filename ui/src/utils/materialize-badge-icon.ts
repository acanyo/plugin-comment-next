import { buildIcon, loadIcon } from '@iconify/vue';
import { bundledBadgeIcon } from '../../../shared/badge-icons';

export async function materializeBadgeIcon(
  icon?: string
): Promise<string | undefined> {
  const value = icon?.trim();
  if (!value) {
    return undefined;
  }
  const bundled = bundledBadgeIcon(value);
  if (bundled) {
    return bundled;
  }
  if (!/^[a-z0-9-]+:[a-z0-9-]+$/i.test(value)) {
    return value;
  }

  // Resolve legacy names once while saving for subsequent offline rendering.
  // A failed lookup rejects the save, preserving the existing rule.
  const data = await loadIcon(value);
  const { body, attributes } = buildIcon(data);
  return `<svg xmlns="http://www.w3.org/2000/svg" viewBox="${attributes.viewBox}">${body}</svg>`;
}
