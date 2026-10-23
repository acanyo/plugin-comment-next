import { describe, expect, it } from 'vitest';
import { avatarCandidates, generatedAvatar } from './fallback';
import {
  getAnonymousAvatarUrl,
  getDefaultAnonymousAvatarUrl,
} from './weavatar';

const remote = 'https://weavatar.com/avatar/abc?d=mp&f=webp&s=96';

describe('avatar fallbacks', () => {
  it('generates distinct, repeatable avatars without a network URL or raw identity', () => {
    const avatar = generatedAvatar('alice@example.com');
    expect(avatar).toBe(generatedAvatar(' ALICE@EXAMPLE.COM '));
    expect(avatar).not.toBe(generatedAvatar('bob@example.com'));
    expect(avatar).toMatch(/^data:image\/svg\+xml,/);
    expect(decodeURIComponent(avatar)).not.toContain('alice@example.com');
  });

  it('requests a missing-avatar error for old and new WeAvatar URLs', async () => {
    const oldCandidates = avatarCandidates(remote, 'old name');
    const newCandidates = avatarCandidates(
      remote.replace('d=mp', 'd=404'),
      'new name'
    );
    expect(oldCandidates[0]).toContain('d=404');
    expect(oldCandidates.at(-1)).toBe(newCandidates.at(-1));
    expect(await getAnonymousAvatarUrl('alice@example.com')).toContain('d=404');
    expect(getDefaultAnonymousAvatarUrl()).toBe('');
    expect(await getAnonymousAvatarUrl(' ')).toBe('');
  });

  it('uses a local avatar immediately when there is no email hash', () => {
    const candidates = avatarCandidates(
      'https://weavatar.com/avatar/?d=mp',
      ''
    );
    expect(candidates).toHaveLength(1);
    expect(candidates[0]).toMatch(/^data:/);
  });

  it('keeps an existing avatar ahead of the assigned custom and generated fallbacks', () => {
    const config = {
      fallbackAvatars: [{ url: '/upload/a.png' }, { url: '/upload/b.png' }],
    };
    const candidates = avatarCandidates('/upload/user.png', 'alice', config);
    expect(candidates).toHaveLength(3);
    expect(candidates[0]).toBe('/upload/user.png');
    expect(['/upload/a.png', '/upload/b.png']).toContain(candidates[1]);
    expect(candidates[2]).toMatch(/^data:/);
    expect(avatarCandidates('/upload/user.png', 'alice', config)).toEqual(
      candidates
    );
  });

  it('assigns varied custom avatars and retains the local fallback if they fail', () => {
    const config = {
      fallbackAvatars: [{ url: '/upload/a.png' }, { url: '/upload/b.png' }],
    };
    const assigned = new Set(
      Array.from(
        { length: 20 },
        (_, i) => avatarCandidates('', `user-${i}`, config)[0]
      )
    );
    expect(assigned.size).toBe(2);
    expect(avatarCandidates('', 'alice', config).at(-1)).toMatch(/^data:/);
  });

  it('skips invalid sources and de-duplicates identical candidates', () => {
    const config = {
      fallbackAvatars: [
        { url: 'javascript:alert(1)' },
        { url: '/upload/a.png' },
      ],
    };
    expect(avatarCandidates('/upload/a.png', 'alice', config)).toHaveLength(2);
    expect(avatarCandidates('javascript:alert(1)', 'alice')).toHaveLength(1);
  });
});
