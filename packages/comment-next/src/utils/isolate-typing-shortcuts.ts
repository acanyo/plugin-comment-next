/** Keep a theme's global slash shortcut from intercepting typing inside the shadow DOM. */
export function isolateTypingShortcuts(node: HTMLElement) {
  const handleKey = (event: KeyboardEvent) => {
    if (event.key !== '/' || !(event.target instanceof HTMLElement)) {
      return;
    }
    if (event.target.closest('input, textarea, [contenteditable="true"]')) {
      event.stopPropagation();
    }
  };
  const events = ['keydown', 'keypress', 'keyup'] as const;
  for (const event of events) {
    node.addEventListener(event, handleKey);
  }
  return {
    destroy() {
      for (const event of events) {
        node.removeEventListener(event, handleKey);
      }
    },
  };
}
