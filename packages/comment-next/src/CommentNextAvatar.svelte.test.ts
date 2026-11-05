import { flushSync, mount, unmount } from 'svelte';
import { expect, it, vi } from 'vitest';
import CommentNextAvatar from './CommentNextAvatar.svelte';

it('ignores a pending image error after its comment list has been removed', async () => {
  const component = mount(CommentNextAvatar, {
    target: document.body,
    props: { src: 'https://example.com/avatar.webp', alt: 'Reader' },
  });
  flushSync();
  const image = document.querySelector('img');
  expect(image).not.toBeNull();
  await unmount(component);
  const warn = vi.spyOn(console, 'warn').mockImplementation(() => {});
  try {
    image?.dispatchEvent(new Event('error'));
    flushSync();
    expect(warn).not.toHaveBeenCalled();
  } finally {
    warn.mockRestore();
    document.body.replaceChildren();
  }
});
