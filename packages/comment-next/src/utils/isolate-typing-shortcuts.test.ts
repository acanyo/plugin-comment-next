// @vitest-environment jsdom

import { afterEach, describe, expect, it, vi } from 'vitest';
import { isolateTypingShortcuts } from './isolate-typing-shortcuts';

afterEach(() => {
  document.body.replaceChildren();
});

describe('comment input and theme slash shortcuts', () => {
  it.each(['keydown', 'keypress', 'keyup'])(
    'keeps %s inside website, email, nickname and editor fields without cancelling typing',
    (type) => {
      const { form, root, cleanup } = composer();
      const search = vi.fn();
      document.addEventListener(type, search);
      try {
        for (const field of form.querySelectorAll(
          'input, textarea, [contenteditable]'
        )) {
          const event = new KeyboardEvent(type, {
            key: '/',
            bubbles: true,
            composed: true,
            cancelable: true,
          });
          field.dispatchEvent(event);
          expect(event.defaultPrevented).toBe(false);
        }
        expect(search).not.toHaveBeenCalled();

        // A theme still receives its shortcut outside the composer.
        root.host.dispatchEvent(
          new KeyboardEvent(type, { key: '/', bubbles: true, composed: true })
        );
        expect(search).toHaveBeenCalledOnce();
      } finally {
        document.removeEventListener(type, search);
        cleanup();
      }
    }
  );

  it('preserves local slash commands and unrelated keyboard events', () => {
    const { form, cleanup } = composer();
    const input = form.querySelector('input');
    if (!input) {
      throw new Error('Missing composer input fixture');
    }
    const localCommand = vi.fn();
    const pageKey = vi.fn();
    input.addEventListener('keydown', localCommand);
    document.addEventListener('keydown', pageKey);
    try {
      input.dispatchEvent(
        new KeyboardEvent('keydown', {
          key: '/',
          bubbles: true,
          composed: true,
        })
      );
      expect(localCommand).toHaveBeenCalledOnce();
      expect(pageKey).not.toHaveBeenCalled();

      input.dispatchEvent(
        new KeyboardEvent('keydown', {
          key: 'Tab',
          bubbles: true,
          composed: true,
        })
      );
      expect(pageKey).toHaveBeenCalledOnce();

      cleanup();
      input.dispatchEvent(
        new KeyboardEvent('keydown', {
          key: '/',
          bubbles: true,
          composed: true,
        })
      );
      expect(pageKey).toHaveBeenCalledTimes(2);
    } finally {
      document.removeEventListener('keydown', pageKey);
      cleanup();
    }
  });
});

function composer() {
  const host = document.createElement('comment-widget');
  document.body.append(host);
  const root = host.attachShadow({ mode: 'open' });
  const form = document.createElement('form');
  form.innerHTML =
    '<input name="website" type="url"><input name="email">' +
    '<input name="displayName"><textarea></textarea><div contenteditable="true"></div>';
  root.append(form);
  const action = isolateTypingShortcuts(form);
  return { root, form, cleanup: () => action.destroy() };
}
