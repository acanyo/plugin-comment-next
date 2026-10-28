const MAX_SNIPPET_LENGTH = 1200;

export function normalizeSnippetText(value: string): string {
  const normalized = value.replaceAll(/\s+/g, ' ').trim();
  return Array.from(normalized).slice(0, MAX_SNIPPET_LENGTH).join('').trimEnd();
}

export function createSnippetQuoteHtml(text: string): string {
  return `<blockquote class="comment-next-snippet-quote"><p>${escapeHtml(normalizeSnippetText(text))}</p></blockquote>`;
}

export function snippetCommentContent(content: string, exact: string): string {
  const template = document.createElement('template');
  template.innerHTML = content;
  const quote = template.content.querySelector('blockquote');
  if (quote && normalizeSnippetText(quote.textContent ?? '') === exact)
    quote.remove();
  return template.innerHTML;
}

function escapeHtml(value: string): string {
  return value
    .replaceAll('&', '&amp;')
    .replaceAll('<', '&lt;')
    .replaceAll('>', '&gt;')
    .replaceAll('"', '&quot;')
    .replaceAll("'", '&#039;');
}
