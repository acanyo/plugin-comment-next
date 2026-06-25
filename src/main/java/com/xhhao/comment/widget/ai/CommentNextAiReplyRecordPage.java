package com.xhhao.comment.widget.ai;

import java.util.List;

public record CommentNextAiReplyRecordPage(
    int page,
    int size,
    int total,
    int totalPages,
    boolean hasNext,
    boolean hasPrevious,
    List<CommentNextAiReplyRecord> items
) {

    static CommentNextAiReplyRecordPage of(int page,
                                           int size,
                                           List<CommentNextAiReplyRecord> records) {
        var safePage = Math.max(1, page);
        var safeSize = Math.max(1, size);
        var total = records.size();
        var totalPages = total == 0 ? 0 : (int) Math.ceil((double) total / safeSize);
        var from = Math.min((safePage - 1) * safeSize, total);
        var to = Math.min(from + safeSize, total);
        return new CommentNextAiReplyRecordPage(
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
