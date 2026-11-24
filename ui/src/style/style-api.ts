import { axiosInstance } from '@halo-dev/api-client';
import type { CommentNextStyle } from '../../../packages/comment-next/src/theme/comment-style';

export type { CommentNextStyle };

export interface StyleState {
  revision: number;
  design: {
    draft: CommentNextStyle;
    published: CommentNextStyle;
    previous: CommentNextStyle | null;
  };
}

export interface StyleAssistantAvailability {
  available: boolean;
  canChooseModel: boolean;
  reason?: string | null;
}

export type StyleOperation = 'save' | 'publish' | 'rollback';

const BASE = '/apis/console.api.commentnext.xhhao.com/v1alpha1';
const STATE = `${BASE}/comment-style/state`;

export async function getStyleWorkbench(modelName = '', signal?: AbortSignal) {
  const { data } = await axiosInstance.get<{
    state: StyleState;
    assistant: StyleAssistantAvailability;
  }>(STATE, { params: { modelName: modelName || undefined }, signal });
  return data;
}

export async function saveStyleState(
  revision: number,
  operation: StyleOperation,
  draft: CommentNextStyle
) {
  const { data } = await axiosInstance.put<StyleState>(STATE, {
    revision,
    operation,
    draft,
  });
  return data;
}

export async function suggestStyle(
  draft: CommentNextStyle,
  instruction: string,
  modelName: string,
  signal: AbortSignal
) {
  const { data } = await axiosInstance.post<{
    draft: CommentNextStyle;
    summary: string;
  }>(
    `${BASE}/comment-style-suggestions`,
    { draft, instruction, modelName },
    { signal }
  );
  return data;
}
