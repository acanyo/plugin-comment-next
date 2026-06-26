package com.xhhao.comment.widget.report;

import java.time.Instant;

public record CommentNextReportRecord(
    String name,
    String targetType,
    String targetName,
    String parentName,
    String authorName,
    String subject,
    String content,
    boolean targetExists,
    boolean approved,
    boolean hidden,
    Instant targetCreationTime,
    String reason,
    String description,
    String identityType,
    Instant creationTime,
    int targetReportCount,
    boolean autoPending
) {
}
