import { describe, expect, it } from 'vitest';
import {
  DEFAULT_CHINA_EMOTE_SOURCE_URL,
  DEFAULT_EMOTE_SOURCE_URL,
  DEFAULT_GITHUB_EMOTE_SOURCE_URL,
  EMOTE_SOURCE_PRESETS,
  type EmoteGroup,
  emoteItemPreferredUrl,
  isExternalEmoteImageUrl,
  isPresetEmoteSourceUrl,
  summarizeGroupLocalization,
} from '../emotes';

function createImageGroup(icons: string[]): EmoteGroup {
  return {
    apiVersion: 'api.commentnext.xhhao.com/v1alpha1',
    kind: 'EmoteGroup',
    metadata: { name: 'comment-next-emote-test' },
    spec: {
      enabled: true,
      displayName: '测试图片表情',
      type: 'image',
      provider: 'OWO',
      sourceType: 'DEFAULT',
      items: icons.map((icon, index) => ({ icon, text: `item-${index}` })),
    },
  };
}

describe('emote localization helpers', () => {
  it('exposes GitHub source as default preset alongside domestic EdgeOne Pages source', () => {
    expect(DEFAULT_EMOTE_SOURCE_URL).toBe(DEFAULT_GITHUB_EMOTE_SOURCE_URL);
    expect(DEFAULT_CHINA_EMOTE_SOURCE_URL).toBe(
      'https://owo.twouu.com/OwO.min.json'
    );
    expect(EMOTE_SOURCE_PRESETS.map((preset) => preset.url)).toEqual([
      DEFAULT_GITHUB_EMOTE_SOURCE_URL,
      DEFAULT_CHINA_EMOTE_SOURCE_URL,
    ]);
    expect(isPresetEmoteSourceUrl(DEFAULT_CHINA_EMOTE_SOURCE_URL)).toBe(true);
    expect(isPresetEmoteSourceUrl(DEFAULT_GITHUB_EMOTE_SOURCE_URL)).toBe(true);
    expect(isPresetEmoteSourceUrl('https://custom.example.com/OwO.json')).toBe(
      false
    );
  });

  it('prefers origin attribute over src and normalizes protocol-relative URLs', () => {
    expect(
      emoteItemPreferredUrl({
        icon: '<img src="//thumb.example.com/a.png" origin="//origin.example.com/a.png" alt="a">',
        text: 'a',
      })
    ).toBe('https://origin.example.com/a.png');

    expect(
      emoteItemPreferredUrl({
        icon: '<img src="/upload/local.png" alt="local">',
        text: 'local',
      })
    ).toBe('/upload/local.png');
  });

  it('distinguishes external URLs from relative and same-origin URLs', () => {
    const siteOrigin = 'https://blog.example.com';

    expect(isExternalEmoteImageUrl('/upload/a.png', siteOrigin)).toBe(false);
    expect(
      isExternalEmoteImageUrl(
        'https://blog.example.com/upload/a.png',
        siteOrigin
      )
    ).toBe(false);
    expect(
      isExternalEmoteImageUrl(
        'https://raw.githubusercontent.com/a/b.png',
        siteOrigin
      )
    ).toBe(true);
    expect(
      isExternalEmoteImageUrl('//raw.githubusercontent.com/a/b.png', siteOrigin)
    ).toBe(true);
  });

  it('summarizes group localization status accurately', () => {
    const siteOrigin = 'https://blog.example.com';

    expect(
      summarizeGroupLocalization(
        createImageGroup([
          '<img src="https://blog.example.com/upload/1.png">',
          '<img src="/upload/2.png">',
        ]),
        siteOrigin
      )
    ).toEqual({
      totalImages: 2,
      externalImages: 0,
      localImages: 2,
      status: 'LOCALIZED',
    });

    expect(
      summarizeGroupLocalization(
        createImageGroup([
          '<img src="https://blog.example.com/upload/1.png">',
          '<img src="https://raw.githubusercontent.com/a/2.png">',
        ]),
        siteOrigin
      )
    ).toEqual({
      totalImages: 2,
      externalImages: 1,
      localImages: 1,
      status: 'PARTIAL',
    });

    expect(
      summarizeGroupLocalization(
        createImageGroup(['<img src="//raw.githubusercontent.com/a/1.png">']),
        siteOrigin
      )
    ).toEqual({
      totalImages: 1,
      externalImages: 1,
      localImages: 0,
      status: 'EXTERNAL',
    });
  });
});
