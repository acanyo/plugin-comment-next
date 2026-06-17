import { onBeforeUnmount, watch } from 'vue';

interface UseDeletionRefreshOptions {
  hasDeletingItems: () => boolean;
  refresh: () => Promise<unknown> | unknown;
  interval?: number;
}

export function useDeletionRefresh({
  hasDeletingItems,
  refresh,
  interval = 1000,
}: UseDeletionRefreshOptions) {
  let timer: ReturnType<typeof window.setInterval> | undefined;
  let refreshing = false;

  const stopTimer = () => {
    if (!timer) {
      return;
    }

    window.clearInterval(timer);
    timer = undefined;
  };

  const tick = async () => {
    if (refreshing) {
      return;
    }

    refreshing = true;
    try {
      await refresh();
    } finally {
      refreshing = false;
      if (!hasDeletingItems()) {
        stopTimer();
      }
    }
  };

  const startTimer = () => {
    if (timer || !hasDeletingItems()) {
      return;
    }

    timer = window.setInterval(() => {
      void tick();
    }, interval);
  };

  const stopWatch = watch(
    hasDeletingItems,
    (hasDeleting) => {
      if (hasDeleting) {
        startTimer();
        return;
      }

      stopTimer();
    },
    { immediate: true }
  );

  onBeforeUnmount(() => {
    stopWatch();
    stopTimer();
  });
}
