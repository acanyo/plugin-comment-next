import { render } from 'svelte/server';
import { describe, expect, it } from 'vitest';
import { badgeIconImage } from '../../../shared/badge-icon-image';
import CommentNextBadge from './CommentNextBadge.svelte';

const svg =
  '<svg xmlns="http://www.w3.org/2000/svg" viewBox="0 0 24 24"><path fill="currentColor" d="M0 0h8v24H0z"/><path fill="#ff0000" d="M8 0h8v24H8z"/><path fill="#00ff00" d="M16 0h8v24h-8z"/></svg>';

describe('badge icon images', () => {
  it('renders a saved icon without an Iconify request or a mask', () => {
    const { body } = render(CommentNextBadge, {
      props: {
        badge: {
          id: 'custom',
          label: '共建者',
          tone: 'custom',
          icon: svg,
          color: '#31536f',
        },
      },
    });

    expect(body).toContain('<img');
    expect(body).toContain('data:image/svg+xml,');
    expect(body).not.toContain('api.iconify.design');
    expect(body).not.toContain('mask');
    expect(body).toContain('共建者');
  });

  it('resolves currentColor while retaining explicit colors', () => {
    const image = badgeIconImage(svg, '#31536f') ?? '';
    const decoded = decodeURIComponent(image.slice(image.indexOf(',') + 1));
    expect(decoded).toContain('fill="#31536f"');
    expect(decoded).toContain('fill="#ff0000"');
    expect(decoded).toContain('fill="#00ff00"');
    expect(decoded).not.toContain('currentColor');
  });

  it.each([
    'mdi:medal-outline',
    'mdi:shield-star-outline',
    'mdi:star-four-points-outline',
    'mdi:star-outline',
    'mdi:crown-outline',
  ])('bundles the old default %s', (icon) => {
    expect(badgeIconImage(icon, '#123456')).toMatch(/^data:image\/svg\+xml,/);
  });

  it('accepts existing base64 icons and uploaded images', () => {
    const base64 = 'data:image/png;base64,iVBORw0KGgo=';
    expect(badgeIconImage(base64)).toBe(base64);
    expect(badgeIconImage('/upload/badge.png')).toBe('/upload/badge.png');
  });

  it('applies the badge color to base64 SVG icons as well', () => {
    const image =
      badgeIconImage(`data:image/svg+xml;base64,${btoa(svg)}`, '#31536f') ?? '';
    expect(decodeURIComponent(image)).toContain('fill="#31536f"');
    expect(decodeURIComponent(image)).toContain('fill="#ff0000"');
  });

  it('keeps legacy names readable until saved again', () => {
    expect(
      badgeIconImage('game-icons:boxing-glove-surprise', '#31536f')
    ).toContain('color=%2331536f');
  });

  it('renders custom SVG as an image instead of inserting its markup', () => {
    const image = badgeIconImage(
      '<svg xmlns="http://www.w3.org/2000/svg" onload="alert(1)"></svg>'
    );
    expect(image).toMatch(/^data:image\/svg\+xml,/);
    expect(badgeIconImage('javascript:alert(1)')).toBeUndefined();
  });
});
