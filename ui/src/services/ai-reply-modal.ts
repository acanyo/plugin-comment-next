import { type AppContext, h, render } from 'vue';
import type { AiReplyRecord } from '../api/ai-reply-records';
import AiReplyGenerateModal from '../components/AiReplyGenerateModal.vue';

export interface OpenAiReplyModalOptions {
  targetType: 'comment' | 'reply';
  targetName: string;
  targetLabel?: string;
  onPublished?: (record: AiReplyRecord) => void;
}

export function openAiReplyGenerateModal(
  options: OpenAiReplyModalOptions,
  appContext?: AppContext
) {
  const container = document.createElement('div');
  document.body.append(container);
  let cleaned = false;

  const cleanup = () => {
    if (cleaned) {
      return;
    }
    cleaned = true;
    render(null, container);
    container.remove();
  };

  const vnode = h(AiReplyGenerateModal, {
    targetType: options.targetType,
    targetName: options.targetName,
    targetLabel: options.targetLabel,
    onClose: cleanup,
    onPublished: options.onPublished,
  });

  if (appContext) {
    vnode.appContext = appContext;
  }

  render(vnode, container);
}
