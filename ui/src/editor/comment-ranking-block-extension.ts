import { IconDeleteBin, IconList } from '@halo-dev/components';
import {
  type Editor,
  type EditorState,
  isActive,
  Node,
  type Range,
  ToolboxItem,
  VueNodeViewRenderer,
} from '@halo-dev/richtext-editor';
import { markRaw } from 'vue';
import CommentRankingBlockView from './CommentRankingBlockView.vue';
import {
  type CommentRankingBlockAttrs,
  DEFAULT_COMMENT_RANKING_ATTRS,
  normalizeRankingColor,
  normalizeRankingLimit,
  normalizeRankingPeriod,
  parseShowHeader,
} from './comment-ranking-config';
import { deleteEditorNode } from './delete-editor-node';

declare module '@halo-dev/richtext-editor' {
  interface Commands<ReturnType> {
    'comment-ranking-block': {
      setCommentRankingBlock: (
        options?: Partial<CommentRankingBlockAttrs>
      ) => ReturnType;
    };
  }
}

const CommentRankingBlockExtension = Node.create({
  name: 'comment-ranking-block',
  group: 'block',
  atom: true,
  selectable: true,
  draggable: true,
  fakeSelection: true,

  addAttributes() {
    return {
      period: {
        default: DEFAULT_COMMENT_RANKING_ATTRS.period,
        parseHTML: (element: HTMLElement) =>
          normalizeRankingPeriod(element.getAttribute('period')),
      },
      limit: {
        default: DEFAULT_COMMENT_RANKING_ATTRS.limit,
        parseHTML: (element: HTMLElement) =>
          normalizeRankingLimit(element.getAttribute('limit')),
      },
      title: {
        default: DEFAULT_COMMENT_RANKING_ATTRS.title,
        parseHTML: (element: HTMLElement) =>
          element.getAttribute('title') || DEFAULT_COMMENT_RANKING_ATTRS.title,
      },
      subtitle: {
        default: DEFAULT_COMMENT_RANKING_ATTRS.subtitle,
        parseHTML: (element: HTMLElement) =>
          element.getAttribute('subtitle') ??
          DEFAULT_COMMENT_RANKING_ATTRS.subtitle,
      },
      showHeader: {
        default: DEFAULT_COMMENT_RANKING_ATTRS.showHeader,
        parseHTML: (element: HTMLElement) =>
          parseShowHeader(element.getAttribute('show-header')),
      },
      accentColor: {
        default: DEFAULT_COMMENT_RANKING_ATTRS.accentColor,
        parseHTML: (element: HTMLElement) =>
          normalizeRankingColor(
            element.getAttribute('accent-color'),
            DEFAULT_COMMENT_RANKING_ATTRS.accentColor
          ),
      },
      surfaceColor: {
        default: DEFAULT_COMMENT_RANKING_ATTRS.surfaceColor,
        parseHTML: (element: HTMLElement) =>
          normalizeRankingColor(
            element.getAttribute('surface-color'),
            DEFAULT_COMMENT_RANKING_ATTRS.surfaceColor
          ),
      },
      textColor: {
        default: DEFAULT_COMMENT_RANKING_ATTRS.textColor,
        parseHTML: (element: HTMLElement) =>
          normalizeRankingColor(
            element.getAttribute('text-color'),
            DEFAULT_COMMENT_RANKING_ATTRS.textColor
          ),
      },
    };
  },

  parseHTML() {
    return [{ tag: 'comment-ranking' }];
  },

  renderHTML({ node }) {
    const attrs = node.attrs as CommentRankingBlockAttrs;
    const htmlAttrs: Record<string, string | number> = {};
    if (attrs.period && attrs.period !== DEFAULT_COMMENT_RANKING_ATTRS.period) {
      htmlAttrs.period = attrs.period;
    }
    if (attrs.limit && attrs.limit !== DEFAULT_COMMENT_RANKING_ATTRS.limit) {
      htmlAttrs.limit = attrs.limit;
    }
    if (attrs.title && attrs.title !== DEFAULT_COMMENT_RANKING_ATTRS.title) {
      htmlAttrs.title = attrs.title;
    }
    if (attrs.subtitle !== DEFAULT_COMMENT_RANKING_ATTRS.subtitle) {
      htmlAttrs.subtitle = attrs.subtitle;
    }
    if (!attrs.showHeader) {
      htmlAttrs['show-header'] = 'false';
    }
    const accentColor = normalizeRankingColor(
      attrs.accentColor,
      DEFAULT_COMMENT_RANKING_ATTRS.accentColor
    );
    const surfaceColor = normalizeRankingColor(attrs.surfaceColor, '');
    const textColor = normalizeRankingColor(attrs.textColor, '');
    if (accentColor !== DEFAULT_COMMENT_RANKING_ATTRS.accentColor) {
      htmlAttrs['accent-color'] = accentColor;
    }
    if (surfaceColor) {
      htmlAttrs['surface-color'] = surfaceColor;
    }
    if (textColor) {
      htmlAttrs['text-color'] = textColor;
    }
    return ['comment-ranking', htmlAttrs];
  },

  addCommands() {
    return {
      setCommentRankingBlock:
        (options = {}) =>
        ({ commands }) =>
          commands.insertContent({
            type: this.name,
            attrs: {
              period: normalizeRankingPeriod(options.period),
              limit: normalizeRankingLimit(options.limit),
              title: options.title || DEFAULT_COMMENT_RANKING_ATTRS.title,
              subtitle:
                options.subtitle ?? DEFAULT_COMMENT_RANKING_ATTRS.subtitle,
              showHeader:
                options.showHeader ?? DEFAULT_COMMENT_RANKING_ATTRS.showHeader,
              accentColor: normalizeRankingColor(
                options.accentColor,
                DEFAULT_COMMENT_RANKING_ATTRS.accentColor
              ),
              surfaceColor: normalizeRankingColor(
                options.surfaceColor,
                DEFAULT_COMMENT_RANKING_ATTRS.surfaceColor
              ),
              textColor: normalizeRankingColor(
                options.textColor,
                DEFAULT_COMMENT_RANKING_ATTRS.textColor
              ),
            },
          }),
    };
  },

  addNodeView() {
    return VueNodeViewRenderer(CommentRankingBlockView);
  },

  addOptions() {
    return {
      getCommandMenuItems() {
        return {
          priority: 206,
          icon: markRaw(IconList),
          title: '评论读者榜（页面）',
          keywords: [
            'comment-ranking',
            'comment',
            'reader',
            '排行',
            '评论榜',
            '读者榜',
          ],
          command: ({ editor, range }: { editor: Editor; range: Range }) => {
            editor
              .chain()
              .focus()
              .setCommentRankingBlock()
              .deleteRange(range)
              .run();
          },
        };
      },
      getToolboxItems({ editor }: { editor: Editor }) {
        return {
          priority: 62,
          component: markRaw(ToolboxItem),
          props: {
            editor,
            icon: markRaw(IconList),
            title: '评论读者榜（页面）',
            action: () => editor.chain().focus().setCommentRankingBlock().run(),
          },
        };
      },
      getBubbleMenu({ editor }: { editor: Editor }) {
        return {
          pluginKey: 'comment-ranking-block-bubble-menu',
          shouldShow: ({ state }: { state: EditorState }) =>
            isActive(state, CommentRankingBlockExtension.name),
          items: [
            {
              priority: 10,
              props: {
                icon: markRaw(IconDeleteBin),
                title: '删除',
                action: () =>
                  deleteEditorNode(CommentRankingBlockExtension.name, editor),
              },
            },
          ],
        };
      },
    };
  },
});

export default CommentRankingBlockExtension;
