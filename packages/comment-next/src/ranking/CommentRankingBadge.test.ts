import { render } from 'svelte/server';
import { describe, expect, it } from 'vitest';
import CommentRankingPodium from './CommentRankingPodium.svelte';
import CommentRankingTable from './CommentRankingTable.svelte';

describe('ranking level badges', () => {
  it.each([CommentRankingPodium, CommentRankingTable])(
    'renders the configured level badge with its icon, color and title',
    (component) => {
      const { body } = render(component, {
        props: {
          items: [
            {
              displayName: '读者',
              commentCount: 2,
              levelBadge: {
                id: 'reader-level',
                label: '资深评论家',
                tone: 'level',
                icon: 'mdi:star-outline',
                color: '#31536f',
                title: '累计评论 100 条',
              },
            },
          ],
        },
      });
      expect(body).toContain('资深评论家');
      expect(body).toContain('累计评论 100 条');
      expect(body).toContain('data:image/svg+xml,');
      expect(body).toContain('#31536f');
      expect(body).not.toContain('超活跃读者');
    }
  );

  it('omits a title when the reader has no level badge', () => {
    const { body } = render(CommentRankingPodium, {
      props: { items: [{ displayName: '读者', commentCount: 2 }] },
    });
    expect(body).not.toContain('reader-badge');
    expect(body).not.toContain('超活跃读者');
  });
});
