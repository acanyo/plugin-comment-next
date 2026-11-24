export function openTopLayer(node: HTMLElement) {
  if (!node.isConnected || typeof node.showPopover !== 'function') return;

  node.setAttribute('popover', 'manual');
  node.showPopover();
}

export function anchoredPanelStyle(
  anchor: DOMRect,
  maxWidth: number,
  height: number
) {
  const padding = 12;
  const gap = 8;
  const width = Math.max(
    0,
    Math.min(maxWidth, window.innerWidth - padding * 2)
  );
  const left = Math.max(
    padding,
    Math.min(anchor.left, window.innerWidth - width - padding)
  );
  const below = anchor.bottom + gap;
  const above = anchor.top - gap - height;
  const maxTop = window.innerHeight - height - padding;
  const top = Math.max(
    padding,
    below <= maxTop ? below : Math.min(above, maxTop)
  );
  return `left:${left}px;top:${top}px;width:${width}px`;
}
