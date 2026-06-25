package com.xhhao.comment.widget.ai;

import java.time.Instant;
import java.util.List;

public record CommentNextAiModerationRecord(
    String targetType,
    String name,
    String parentName,
    String authorName,
    String subject,
    String content,
    boolean approved,
    boolean hidden,
    Instant creationTime,
    boolean intercepted,
    boolean rejected,
    String action,
    List<String> categories,
    List<String> labels,
    double confidence,
    String reason,
    Instant reviewedAt
) {
}
