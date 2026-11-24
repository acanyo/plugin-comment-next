import { describe, expect, it } from 'vitest';
import { resolveCommentStyle } from './comment-style';

describe('comment style rendering', () => {
  it('renders validated settings and computes readable button text', () => {
    const style = resolveCommentStyle({
      lightAccent: '#244c3c',
      darkAccent: '#9ed4bb',
      radius: 0,
      density: 'compact',
      listStyle: 'cards',
    });

    expect(style.variables).toContain('--comment-next-style-light-ink:#fff');
    expect(style.variables).toContain('--comment-next-style-dark-ink:#000');
    expect(style.variables).toContain('--comment-next-style-radius:0px');
    expect(style).toMatchObject({
      customAccent: true,
      density: 'compact',
      listStyle: 'cards',
    });
  });

  it('ignores invalid CSS and out-of-range radii from a theme or an API', () => {
    expect(
      resolveCommentStyle({ lightAccent: '#000;display:none', radius: 25 })
    ).toEqual(resolveCommentStyle());
  });
});
