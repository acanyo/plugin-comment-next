import { buildIcon, loadIcon } from '@iconify/vue';
import { describe, expect, it, vi } from 'vitest';
import { materializeBadgeIcon } from '../materialize-badge-icon';

vi.mock('@iconify/vue', () => ({ loadIcon: vi.fn(), buildIcon: vi.fn() }));

describe('materialize badge icons on save', () => {
  it('bundles the returned icon body instead of persisting its name', async () => {
    vi.mocked(loadIcon).mockResolvedValueOnce({
      body: '<path fill="currentColor"/>',
      width: 512,
      height: 512,
    } as Awaited<ReturnType<typeof loadIcon>>);
    vi.mocked(buildIcon).mockReturnValueOnce({
      body: '<path fill="currentColor"/>',
      attributes: { viewBox: '0 0 512 512' },
    } as ReturnType<typeof buildIcon>);
    const result = await materializeBadgeIcon(
      'game-icons:boxing-glove-surprise'
    );
    expect(result).toContain('<svg');
    expect(result).toContain('viewBox="0 0 512 512"');
    expect(result).not.toContain('api.iconify.design');
    expect(result).not.toContain('game-icons:boxing-glove-surprise');
  });

  it('keeps saved SVG and data images unchanged', async () => {
    const svg =
      '<svg xmlns="http://www.w3.org/2000/svg"><path fill="#ff0000"/></svg>';
    expect(await materializeBadgeIcon(svg)).toBe(svg);
    expect(await materializeBadgeIcon('data:image/png;base64,abc')).toBe(
      'data:image/png;base64,abc'
    );
  });

  it('does not require a lookup for bundled defaults', async () => {
    vi.mocked(loadIcon).mockClear();
    expect(await materializeBadgeIcon('mdi:star-outline')).toContain('<svg');
    expect(loadIcon).not.toHaveBeenCalled();
  });

  it('rejects a failed legacy lookup so saving cannot silently erase the icon', async () => {
    vi.mocked(loadIcon).mockRejectedValueOnce(new Error('offline'));
    await expect(
      materializeBadgeIcon('game-icons:boxing-glove-surprise')
    ).rejects.toThrow('offline');
  });
});
