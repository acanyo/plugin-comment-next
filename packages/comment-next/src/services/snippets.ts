import type { CommentNextSnippetThread } from '../types/snippet';
import { adaptHaloComment } from './comments';

export interface SnippetSubject {
  baseUrl: string;
  group: string;
  kind: string;
  name: string;
  version: string;
}

async function request(
  subject: SnippetSubject,
  key = '',
  page = 1,
  signal?: AbortSignal
) {
  const url = new URL(
    `${subject.baseUrl.replace(/\/$/, '')}/apis/api.commentnext.xhhao.com/v1alpha1/comments/snippets`,
    window.location.origin
  );
  for (const field of ['group', 'kind', 'name', 'version'] as const)
    url.searchParams.set(field, subject[field]);
  if (key) {
    url.searchParams.set('key', key);
    url.searchParams.set('page', String(page));
  }
  const response = await fetch(url, {
    credentials: 'include',
    headers: { Accept: 'application/json' },
    signal,
  });
  if (!response.ok) throw new Error('片段评论加载失败，请重试');
  return response.json();
}

export async function fetchSnippetThreads(
  subject: SnippetSubject,
  signal?: AbortSignal
): Promise<CommentNextSnippetThread[]> {
  return (await request(subject, '', 1, signal)).threads ?? [];
}

export async function fetchSnippetComments(
  subject: SnippetSubject,
  key: string,
  page: number,
  signal?: AbortSignal
) {
  const data = await request(subject, key, page, signal);
  return {
    items: (data.items ?? []).map(adaptHaloComment),
    total: Number(data.total) || 0,
    hasNext: Boolean(data.hasNext),
  };
}
