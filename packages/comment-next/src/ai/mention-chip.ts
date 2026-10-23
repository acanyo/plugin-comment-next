import { avatarCandidates } from '../avatar/fallback';

const CHIP_SELECTOR = '[data-comment-next-mention]';

export function createMentionChip(
  mentionName: string,
  avatar: string
): HTMLElement {
  const chip = document.createElement('span');
  chip.className = 'comment-next-editor-mention';
  chip.contentEditable = 'false';
  chip.setAttribute('data-comment-next-mention', mentionName);

  const sources = avatarCandidates(avatar, mentionName);
  const image = document.createElement('img');
  image.src = sources[0];
  image.alt = '';
  image.draggable = false;
  let sourceIndex = 0;
  image.onerror = () => {
    sourceIndex += 1;
    if (sources[sourceIndex]) image.src = sources[sourceIndex];
    else image.remove();
  };
  const name = document.createElement('span');
  name.textContent = mentionName;
  chip.append(image, name);
  return chip;
}

/** Only the mention text is persisted, never editor markup or the avatar image. */
export function serializeMentionChips(root: HTMLElement) {
  root.querySelectorAll(CHIP_SELECTOR).forEach((chip) => {
    chip.replaceWith(
      document.createTextNode(
        chip.getAttribute('data-comment-next-mention') ?? ''
      )
    );
  });
}
