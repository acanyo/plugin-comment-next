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
import CommentRankingSidebarBlockView from './CommentRankingSidebarBlockView.vue';
import {
  type CommentRankingSidebarBlockAttrs,
  DEFAULT_COMMENT_RANKING_SIDEBAR_ATTRS,
  normalizeRankingColor,
  normalizeRankingSidebarLimit,
  normalizeRankingSidebarPeriod,
  parseShowHeader,
} from './comment-ranking-config';
import { deleteEditorNode } from './delete-editor-node';

declare module '@halo-dev/richtext-editor' {
  interface Commands<ReturnType> {
    'comment-ranking-sidebar-block': {
      setCommentRankingSidebarBlock: (
        options?: Partial<CommentRankingSidebarBlockAttrs>
      ) => ReturnType;
    };
  }
}

const CommentRankingSidebarBlockExtension = Node.create({
  name: 'comment-ranking-sidebar-block',
  group: 'block',
  atom: true,
  selectable: true,
  draggable: true,
  fakeSelection: true,

  addAttributes() {
    return {
      period: {
        default: DEFAULT_COMMENT_RANKING_SIDEBAR_ATTRS.period,
        parseHTML: (element: HTMLElement) =>
          normalizeRankingSidebarPeriod(element.getAttribute('period')),
      },
      limit: {
        default: DEFAULT_COMMENT_RANKING_SIDEBAR_ATTRS.limit,
        parseHTML: (element: HTMLElement) =>
          normalizeRankingSidebarLimit(element.getAttribute('limit')),
      },
      title: {
        default: DEFAULT_COMMENT_RANKING_SIDEBAR_ATTRS.title,
        parseHTML: (element: HTMLElement) =>
          element.getAttribute('title') ||
          DEFAULT_COMMENT_RANKING_SIDEBAR_ATTRS.title,
      },
      rankingUrl: {
        default: DEFAULT_COMMENT_RANKING_SIDEBAR_ATTRS.rankingUrl,
        parseHTML: (element: HTMLElement) =>
          element.getAttribute('ranking-url') || '',
      },
      linkText: {
        default: DEFAULT_COMMENT_RANKING_SIDEBAR_ATTRS.linkText,
        parseHTML: (element: HTMLElement) =>
          element.getAttribute('link-text') ??
          DEFAULT_COMMENT_RANKING_SIDEBAR_ATTRS.linkText,
      },
      showHeader: {
        default: DEFAULT_COMMENT_RANKING_SIDEBAR_ATTRS.showHeader,
        parseHTML: (element: HTMLElement) =>
          parseShowHeader(element.getAttribute('show-header')),
      },
      accentColor: {
        default: DEFAULT_COMMENT_RANKING_SIDEBAR_ATTRS.accentColor,
        parseHTML: (element: HTMLElement) =>
          normalizeRankingColor(element.getAttribute('accent-color'), ''),
      },
      surfaceColor: {
        default: DEFAULT_COMMENT_RANKING_SIDEBAR_ATTRS.surfaceColor,
        parseHTML: (element: HTMLElement) =>
          normalizeRankingColor(element.getAttribute('surface-color'), ''),
      },
      textColor: {
        default: DEFAULT_COMMENT_RANKING_SIDEBAR_ATTRS.textColor,
        parseHTML: (element: HTMLElement) =>
          normalizeRankingColor(element.getAttribute('text-color'), ''),
      },
    };
  },

  parseHTML() {
    return [{ tag: 'comment-ranking-sidebar' }];
  },

  renderHTML({ node }) {
    const attrs = node.attrs as CommentRankingSidebarBlockAttrs;
    const htmlAttrs: Record<string, string | number> = {
      period: attrs.period,
      limit: attrs.limit,
      title: attrs.title,
      'link-text': attrs.linkText,
    };
    if (attrs.rankingUrl) htmlAttrs['ranking-url'] = attrs.rankingUrl;
    if (!attrs.showHeader) htmlAttrs['show-header'] = 'false';
    if (
      attrs.accentColor !== DEFAULT_COMMENT_RANKING_SIDEBAR_ATTRS.accentColor
    ) {
      htmlAttrs['accent-color'] = normalizeRankingColor(attrs.accentColor, '');
    }
    if (attrs.surfaceColor) {
      htmlAttrs['surface-color'] = normalizeRankingColor(
        attrs.surfaceColor,
        ''
      );
    }
    if (attrs.textColor) {
      htmlAttrs['text-color'] = normalizeRankingColor(attrs.textColor, '');
    }
    return ['comment-ranking-sidebar', htmlAttrs];
  },

  addCommands() {
    return {
      setCommentRankingSidebarBlock:
        (options = {}) =>
        ({ commands }) =>
          commands.insertContent({
            type: this.name,
            attrs: {
              period: normalizeRankingSidebarPeriod(options.period),
              limit: normalizeRankingSidebarLimit(options.limit),
              title:
                options.title || DEFAULT_COMMENT_RANKING_SIDEBAR_ATTRS.title,
              rankingUrl: options.rankingUrl || '',
              linkText:
                options.linkText ??
                DEFAULT_COMMENT_RANKING_SIDEBAR_ATTRS.linkText,
              showHeader: options.showHeader ?? true,
              accentColor: normalizeRankingColor(
                options.accentColor,
                DEFAULT_COMMENT_RANKING_SIDEBAR_ATTRS.accentColor
              ),
              surfaceColor: normalizeRankingColor(options.surfaceColor, ''),
              textColor: normalizeRankingColor(options.textColor, ''),
            },
          }),
    };
  },

  addNodeView() {
    return VueNodeViewRenderer(CommentRankingSidebarBlockView);
  },

  addOptions() {
    return {
      getCommandMenuItems() {
        return {
          priority: 207,
          icon: markRaw(IconList),
          title: '评论读者榜（侧边栏）',
          keywords: ['comment-ranking-sidebar', 'sidebar', '侧边栏', '读者榜'],
          command: ({ editor, range }: { editor: Editor; range: Range }) => {
            editor
              .chain()
              .focus()
              .setCommentRankingSidebarBlock()
              .deleteRange(range)
              .run();
          },
        };
      },
      getToolboxItems({ editor }: { editor: Editor }) {
        return {
          priority: 63,
          component: markRaw(ToolboxItem),
          props: {
            editor,
            icon: markRaw(IconList),
            title: '评论读者榜（侧边栏）',
            action: () =>
              editor.chain().focus().setCommentRankingSidebarBlock().run(),
          },
        };
      },
      getBubbleMenu({ editor }: { editor: Editor }) {
        return {
          pluginKey: 'comment-ranking-sidebar-block-bubble-menu',
          shouldShow: ({ state }: { state: EditorState }) =>
            isActive(state, CommentRankingSidebarBlockExtension.name),
          items: [
            {
              priority: 10,
              props: {
                icon: markRaw(IconDeleteBin),
                title: '删除',
                action: () =>
                  deleteEditorNode(
                    CommentRankingSidebarBlockExtension.name,
                    editor
                  ),
              },
            },
          ],
        };
      },
    };
  },
});

export default CommentRankingSidebarBlockExtension;
