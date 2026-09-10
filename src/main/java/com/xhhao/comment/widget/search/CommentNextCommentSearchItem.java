package com.xhhao.comment.widget.search;

import java.time.Instant;

/**
 * Console-only view of a comment target used by the advanced search page.
 */
public record CommentNextCommentSearchItem(
    String targetType,
    String name,
    String parentName,
    String subject,
    String authorName,
    String ownerKind,
    String email,
    String username,
    String ipAddress,
    String content,
    String userAgent,
    boolean approved,
    boolean hidden,
    Instant creationTime
) {
}
