// @vitest-environment jsdom
import { describe, expect, it } from 'vitest';
import { createMentionChip, serializeMentionChips } from './mention-chip';

describe('assistant mention chips', () => {
  it('shows the configured avatar and keeps the mention as one editing unit', () => {
    const chip = createMentionChip('@拾光 bot', '/upload/assistant.png');
    expect(chip.contentEditable).toBe('false');
    expect(chip.querySelector('img')?.getAttribute('src')).toBe(
      '/upload/assistant.png'
    );
    expect(chip.textContent).toBe('@拾光 bot');
  });

  it('serializes as the exact mention text without sending avatar markup', () => {
    const editor = document.createElement('div');
    editor.append(
      '请教一下 ',
      createMentionChip('@拾光 bot', '/upload/assistant.png'),
      ' 这个问题'
    );
    const clone = editor.cloneNode(true) as HTMLElement;
    serializeMentionChips(clone);
    expect(clone.innerHTML).toBe('请教一下 @拾光 bot 这个问题');
    expect(editor.querySelector('img')).not.toBeNull();
    expect(clone.querySelector('img')).toBeNull();
  });

  it('treats assistant names as text and falls back to a local avatar', () => {
    const chip = createMentionChip(
      '@<img onerror=alert(1)>',
      'javascript:alert(1)'
    );
    expect(chip.querySelectorAll('img')).toHaveLength(1);
    expect(chip.querySelector('img')?.getAttribute('src')).toMatch(
      /^data:image/
    );
    expect(chip.textContent).toBe('@<img onerror=alert(1)>');
    expect(chip.querySelector('[onerror]')).toBeNull();
  });
});
