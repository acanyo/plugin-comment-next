package com.xhhao.comment.widget.report;

public record CommentNextReportRequest(
    String targetType,
    String name,
    String reason,
    String description
) {
}
