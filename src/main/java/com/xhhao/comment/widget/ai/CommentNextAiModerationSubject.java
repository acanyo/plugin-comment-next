package com.xhhao.comment.widget.ai;

record CommentNextAiModerationSubject(
    String sourceType,
    String name,
    String authorName,
    String authorKind,
    String authorIdentifier,
    String authorWebsite,
    String subject,
    String content
) {
    String fingerprint() {
        return String.join("\n",
            safe(sourceType),
            safe(name),
            safe(authorName),
            safe(authorKind),
            safe(authorIdentifier),
            safe(authorWebsite),
            safe(subject),
            safe(content)
        );
    }

    private static String safe(String value) {
        return value == null ? "" : value;
    }
}
