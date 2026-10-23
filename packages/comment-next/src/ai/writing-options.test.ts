import { afterEach, describe, expect, it, vi } from 'vitest';
import { generateCommentAiSuggestion } from '../services/ai';
import {
  AI_WRITING_MODES,
  writingInputHint,
  writingModes,
} from './writing-options';

const mode = (key: string) => {
  const item = AI_WRITING_MODES.find((value) => value.key === key);
  if (!item) throw new Error(`Missing mode ${key}`);
  return item;
};

afterEach(() => vi.unstubAllGlobals());

describe('AI writing controls', () => {
  it('requires a tag for blank draft generation and original text for editing', () => {
    expect(writingInputHint(mode('generate'), false, true, false, 0)).not.toBe(
      ''
    );
    expect(writingInputHint(mode('generate'), false, true, false, 1)).toBe('');
    for (const key of ['polish', 'expand', 'question']) {
      expect(writingInputHint(mode(key), false, true, false, 2)).not.toBe('');
      expect(writingInputHint(mode(key), true, true, false, 0)).toBe('');
    }
  });

  it('requires article context for reading and a target or content for replies', () => {
    expect(writingInputHint(mode('summary'), false, false, false, 1)).not.toBe(
      ''
    );
    expect(writingInputHint(mode('summary'), false, true, false, 0)).toBe('');
    expect(writingInputHint(mode('reply'), false, false, false, 1)).not.toBe(
      ''
    );
    expect(writingInputHint(mode('reply'), false, false, true, 0)).toBe('');
    expect(writingModes('comment').some((value) => value.key === 'reply')).toBe(
      false
    );
    expect(writingModes('reply').some((value) => value.key === 'summary')).toBe(
      false
    );
  });

  it('sends selected tags separately from the original draft and article reference', async () => {
    const fetchMock = vi
      .fn()
      .mockResolvedValue(
        new Response(JSON.stringify({ text: '生成的草稿' }), { status: 200 })
      );
    vi.stubGlobal('fetch', fetchMock);
    const result = await generateCommentAiSuggestion({
      mode: 'expand',
      content: '我的原始观点',
      variant: 'comment',
      subject: 'content.halo.run/v1alpha1/Post/test',
      tags: ['补充观点', '说明原因'],
    });
    const request = JSON.parse(fetchMock.mock.calls[0][1].body);
    expect(request).toMatchObject({
      mode: 'expand',
      content: '我的原始观点',
      tags: ['补充观点', '说明原因'],
      subject: 'content.halo.run/v1alpha1/Post/test',
    });
    expect(result.text).toBe('生成的草稿');
  });
});
