(() => {
  const entry = document.currentScript;
  if (!(entry instanceof HTMLScriptElement) || !entry.src) return;

  // Include Lottie images in theme sidebars and older stored comment HTML.
  const neededBy = [
    'comment-widget',
    'comment-next',
    'comment-next-content',
    'comment-ranking',
    'comment-ranking-sidebar',
    'img[src][title^="comment-next-lottie:"]',
    'img[src*="comment-next-lottie"]',
    'img[src*=".json" i]',
    'img[src*=".tgs" i]',
    'img[src*=".lottie" i]',
    'img[src*="/apis/api.lottie.halo.run/"]',
  ].join(',');

  const entryUrl = new URL(entry.src);
  const nonce = entry.nonce;
  let loading = false;

  /** @param {string} file */
  function assetUrl(file) {
    const url = new URL(file, entryUrl);
    url.search = entryUrl.search;
    return url.href;
  }

  /**
   * @param {string} first
   * @param {string} second
   */
  function sameAsset(first, second) {
    return first.split(/[?#]/, 1)[0] === second.split(/[?#]/, 1)[0];
  }

  const observer =
    typeof MutationObserver === 'undefined'
      ? undefined
      : new MutationObserver((records) => {
          if (loading) return;
          for (const { addedNodes } of records) {
            for (const node of addedNodes) {
              if (
                node instanceof Element &&
                (node.matches(neededBy) || node.querySelector(neededBy))
              ) {
                loadAssets();
                return;
              }
            }
          }
        });

  function loadAssets() {
    const styleUrl = assetUrl('comment-next.css');
    const scriptUrl = assetUrl('comment-next.iife.js');

    const hasStyle = Array.from(document.querySelectorAll('link')).some(
      (link) => link.rel === 'stylesheet' && sameAsset(link.href, styleUrl)
    );
    if (!hasStyle) {
      const style = document.createElement('link');
      style.rel = 'stylesheet';
      style.href = styleUrl;
      if (nonce) style.nonce = nonce;
      style.addEventListener('error', () => style.remove(), { once: true });
      document.head.append(style);
    }

    const hasScript = Array.from(document.scripts).some((script) =>
      sameAsset(script.src, scriptUrl)
    );
    if (window.customElements?.get('comment-next') || hasScript) {
      observer?.disconnect();
      return;
    }

    loading = true;
    const script = document.createElement('script');
    script.src = scriptUrl;
    if (nonce) script.nonce = nonce;
    script.addEventListener('load', () => observer?.disconnect(), {
      once: true,
    });
    script.addEventListener(
      'error',
      () => {
        script.remove();
        loading = false;
      },
      { once: true }
    );
    document.head.append(script);
  }

  observer?.observe(document.documentElement, {
    childList: true,
    subtree: true,
  });
  if (document.querySelector(neededBy)) loadAssets();
})();
