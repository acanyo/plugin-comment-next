import type { CommentNextAuthor } from '../types/comment';
import type {
  CommentNextAuthorIdentity,
  CommentNextInteractionPlusConfig,
} from '../types/interaction-plus';

const RUNTIME_SCRIPT_ID = 'interaction-plus-runtime';
const RUNTIME_SRC_MARKER = 'interaction-plus.runtime.js';
const HIP_AVATAR_TAG = 'hip-user-avatar';

export function hasAuthorIdentity(
  author?: Pick<CommentNextAuthor, 'identity'> | null
): author is CommentNextAuthor & { identity: CommentNextAuthorIdentity } {
  return Boolean(author?.identity?.userName?.trim());
}

export function hasAuthorDecorations(
  author?: Pick<CommentNextAuthor, 'identity'> | null
): boolean {
  const identity = author?.identity;
  if (!identity?.userName?.trim()) {
    return false;
  }

  const marks = Array.isArray(identity.identityMarks)
    ? identity.identityMarks
    : [];
  if (marks.length > 0) {
    return true;
  }

  const decorations = identity.decorations;
  if (!decorations || typeof decorations !== 'object') {
    return false;
  }

  return Boolean(
    decorations.avatarFrame ||
      decorations.title ||
      decorations.primaryBadge ||
      decorations.cardBackground ||
      decorations.nameStyle ||
      (Array.isArray(decorations.badgeShowcase) &&
        decorations.badgeShowcase.length > 0)
  );
}

export function toIdentityData(
  identity?: CommentNextAuthorIdentity | null
): string {
  if (!identity?.userName?.trim()) {
    return '';
  }

  try {
    return JSON.stringify(identity);
  } catch {
    return '';
  }
}

export function bindHipData(node: HTMLElement, value: string) {
  syncHipData(node, value);

  return {
    update(next: string) {
      syncHipData(node, next);
    },
  };
}

export function isInteractionPlusRuntimeReady(): boolean {
  return (
    typeof customElements !== 'undefined' &&
    Boolean(customElements.get(HIP_AVATAR_TAG))
  );
}

export function whenInteractionPlusRuntimeReady(): Promise<void> {
  if (isInteractionPlusRuntimeReady()) {
    return Promise.resolve();
  }

  if (typeof customElements === 'undefined') {
    return Promise.resolve();
  }

  return customElements.whenDefined(HIP_AVATAR_TAG).then(() => undefined);
}

export function shouldLoadInteractionPlusRuntime(
  config?: CommentNextInteractionPlusConfig
): boolean {
  return Boolean(
    config?.enabled !== false &&
      config?.runtimeAvailable &&
      config.runtimeScript
  );
}

export function ensureInteractionPlusRuntime(src?: string) {
  if (typeof document === 'undefined' || !src) {
    return;
  }

  if (isInteractionPlusRuntimeReady()) {
    return;
  }

  if (
    document.getElementById(RUNTIME_SCRIPT_ID) ||
    document.querySelector(`script[src*="${RUNTIME_SRC_MARKER}"]`)
  ) {
    return;
  }

  const script = document.createElement('script');
  script.id = RUNTIME_SCRIPT_ID;
  script.src = src;
  script.defer = true;
  document.head.appendChild(script);
}

function syncHipData(node: HTMLElement, value: string) {
  if (value) {
    node.setAttribute('data', value);
    return;
  }

  node.removeAttribute('data');
}
