package com.xhhao.comment.widget.ai;

import com.xhhao.comment.widget.ai.website.CommentNextWebsiteMetadata;

record CommentNextAiModerationSubject(
    String sourceType,
    String name,
    String authorName,
    String authorKind,
    String authorIdentifier,
    String authorWebsite,
    String authorWebsiteTitle,
    String authorWebsiteDescription,
    String authorWebsiteMetadataSource,
    String subject,
    String content
) {
    CommentNextAiModerationSubject withWebsiteMetadata(CommentNextWebsiteMetadata metadata) {
        return new CommentNextAiModerationSubject(
            sourceType,
            name,
            authorName,
            authorKind,
            authorIdentifier,
            authorWebsite,
            metadata.title(),
            metadata.description(),
            metadata.source(),
            subject,
            content
        );
    }

    String fingerprint() {
        return String.join("\n",
            safe(sourceType),
            safe(name),
            safe(authorName),
            safe(authorKind),
            safe(authorIdentifier),
            safe(authorWebsite),
            safe(authorWebsiteTitle),
            safe(authorWebsiteDescription),
            safe(authorWebsiteMetadataSource),
            safe(subject),
            safe(content)
        );
    }

    private static String safe(String value) {
        return value == null ? "" : value;
    }
}
