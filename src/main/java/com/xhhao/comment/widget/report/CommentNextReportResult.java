package com.xhhao.comment.widget.report;

public record CommentNextReportResult(
    String targetType,
    String targetName,
    int count,
    boolean reported,
    boolean duplicate,
    boolean autoPending
) {
}
