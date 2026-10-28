import type { SnippetCandidate } from './targets';

const SHOW_DELAY = 240;
const LEAVE_DELAY = 320;

// Own the dwell timers separately from document events and widget routing.
export function createSnippetIntent(
  publish: (candidate?: SnippetCandidate, preview?: boolean) => void
) {
  let active: SnippetCandidate | undefined;
  let pending: SnippetCandidate | undefined;
  let dismissed: SnippetCandidate | undefined;
  let pinned = false;
  let showTimer: number | undefined;
  let hideTimer: number | undefined;

  function cancelPending() {
    window.clearTimeout(showTimer);
    showTimer = undefined;
    pending = undefined;
  }

  function cancelLeave() {
    window.clearTimeout(hideTimer);
    hideTimer = undefined;
  }

  function reset() {
    cancelPending();
    cancelLeave();
    active = undefined;
    pinned = false;
    publish();
  }

  return {
    hover(next?: SnippetCandidate, moved = false) {
      if (pinned) return;
      if (sameCandidate(dismissed, next)) return;
      dismissed = undefined;
      if (sameCandidate(active, next)) {
        cancelLeave();
        cancelPending();
        return;
      }
      if (sameCandidate(pending, next) && !moved) return;
      cancelPending();
      if (active && hideTimer === undefined) {
        hideTimer = window.setTimeout(() => {
          hideTimer = undefined;
          active = undefined;
          publish();
        }, LEAVE_DELAY);
      }
      if (!next) return;
      pending = next;
      showTimer = window.setTimeout(() => {
        cancelLeave();
        active = pending;
        pending = undefined;
        showTimer = undefined;
        publish(active, false);
      }, SHOW_DELAY);
    },
    hold() {
      cancelPending();
      cancelLeave();
    },
    select(next: SnippetCandidate) {
      reset();
      dismissed = undefined;
      active = next;
      publish(active, false);
    },
    pin() {
      if (!active?.thread) return;
      cancelPending();
      cancelLeave();
      pinned = true;
      publish(active, true);
    },
    dismiss() {
      dismissed = active ?? pending;
      reset();
    },
    reset() {
      dismissed = undefined;
      reset();
    },
    destroy() {
      cancelPending();
      cancelLeave();
    },
  };
}

function sameCandidate(a?: SnippetCandidate, b?: SnippetCandidate): boolean {
  return Boolean(
    a &&
      b &&
      a.source === b.source &&
      a.anchor.exact === b.anchor.exact &&
      a.anchor.prefix === b.anchor.prefix &&
      a.anchor.suffix === b.anchor.suffix &&
      a.thread?.key === b.thread?.key
  );
}
