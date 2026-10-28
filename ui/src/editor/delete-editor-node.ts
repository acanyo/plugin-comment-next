import type { Editor } from '@halo-dev/richtext-editor';

/** Deletes the selected editor node even when Halo uses a fake node selection. */
export function deleteEditorNode(nodeType: string, editor: Editor): boolean {
  const { state } = editor;
  const $position = state.selection.$anchor;

  for (let depth = $position.depth; depth > 0; depth -= 1) {
    if ($position.node(depth).type.name !== nodeType) {
      continue;
    }
    editor.view.dispatch(
      state.tr
        .delete($position.before(depth), $position.after(depth))
        .scrollIntoView()
    );
    return true;
  }

  const selectedNode = (
    state.selection as typeof state.selection & {
      node?: { type: { name: string } };
    }
  ).node;
  if (selectedNode?.type.name === nodeType) {
    return editor.chain().focus().deleteSelection().run();
  }

  const position = $position.pos;
  const node = state.doc.nodeAt(position);
  if (node?.type.name !== nodeType) {
    return false;
  }
  editor.view.dispatch(
    state.tr.delete(position, position + node.nodeSize).scrollIntoView()
  );
  return true;
}
