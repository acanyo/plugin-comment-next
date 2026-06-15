const ALLOWED_TAGS = new Set([
  "a",
  "b",
  "blockquote",
  "br",
  "code",
  "em",
  "i",
  "li",
  "ol",
  "p",
  "pre",
  "s",
  "span",
  "strong",
  "u",
  "ul",
]);

const GLOBAL_ALLOWED_ATTRIBUTES = new Set(["title"]);
const TAG_ALLOWED_ATTRIBUTES: Record<string, Set<string>> = {
  a: new Set(["href", "title"]),
};

export function sanitizeCommentHtml(value: string): string {
  if (!value.trim()) {
    return "";
  }

  if (typeof document === "undefined") {
    return escapeHtml(value);
  }

  const template = document.createElement("template");
  template.innerHTML = value;
  sanitizeChildren(template.content);

  return template.innerHTML;
}

function sanitizeChildren(parent: ParentNode): void {
  for (const node of Array.from(parent.childNodes)) {
    if (node.nodeType === Node.ELEMENT_NODE) {
      sanitizeElement(node as Element);
      continue;
    }

    if (node.nodeType !== Node.TEXT_NODE) {
      node.remove();
    }
  }
}

function sanitizeElement(element: Element): void {
  const tagName = element.tagName.toLowerCase();

  if (!ALLOWED_TAGS.has(tagName)) {
    element.replaceWith(document.createTextNode(element.textContent ?? ""));
    return;
  }

  for (const attribute of Array.from(element.attributes)) {
    const attributeName = attribute.name.toLowerCase();
    const allowedAttributes = TAG_ALLOWED_ATTRIBUTES[tagName];
    const isAllowed =
      GLOBAL_ALLOWED_ATTRIBUTES.has(attributeName) || Boolean(allowedAttributes?.has(attributeName));

    if (!isAllowed || attributeName.startsWith("on")) {
      element.removeAttribute(attribute.name);
      continue;
    }

    if (attributeName === "href" && !isSafeHref(attribute.value)) {
      element.removeAttribute(attribute.name);
    }
  }

  if (tagName === "a") {
    element.setAttribute("target", "_blank");
    element.setAttribute("rel", "noopener noreferrer nofollow ugc");
  }

  sanitizeChildren(element);
}

function isSafeHref(value: string): boolean {
  try {
    const url = new URL(value, window.location.origin);

    return ["http:", "https:", "mailto:"].includes(url.protocol);
  } catch {
    return false;
  }
}

function escapeHtml(value: string): string {
  return value
    .replaceAll("&", "&amp;")
    .replaceAll("<", "&lt;")
    .replaceAll(">", "&gt;")
    .replaceAll('"', "&quot;")
    .replaceAll("'", "&#039;");
}
