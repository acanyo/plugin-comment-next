package com.xhhao.comment.widget.report;

import java.util.List;

public record CommentNextReportRecordPage(
    int page,
    int size,
    int total,
    int totalPages,
    boolean hasNext,
    boolean hasPrevious,
    List<CommentNextReportRecord> items
) {

    static CommentNextReportRecordPage of(int page,
                                          int size,
                                          List<CommentNextReportRecord> records) {
        var safePage = Math.max(1, page);
        var safeSize = Math.max(1, size);
        var total = records.size();
        var totalPages = total == 0 ? 0 : (int) Math.ceil((double) total / safeSize);
        var from = Math.min((safePage - 1) * safeSize, total);
        var to = Math.min(from + safeSize, total);
        return new CommentNextReportRecordPage(
            safePage,
            safeSize,
            total,
            totalPages,
            safePage < totalPages,
            safePage > 1 && totalPages > 0,
            records.subList(from, to)
        );
    }
}
