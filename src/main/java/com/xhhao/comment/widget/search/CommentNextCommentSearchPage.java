package com.xhhao.comment.widget.search;

import java.util.List;

public record CommentNextCommentSearchPage(
    int page,
    int size,
    int total,
    int totalPages,
    boolean hasNext,
    boolean hasPrevious,
    List<CommentNextCommentSearchItem> items
) {
    static CommentNextCommentSearchPage from(
        List<CommentNextCommentSearchItem> matches,
        int page,
        int size
    ) {
        var total = matches.size();
        var requestedFromIndex = (long) (page - 1) * size;
        var fromIndex = (int) Math.min(requestedFromIndex, total);
        var toIndex = Math.min(fromIndex + size, total);
        var totalPages = total == 0 ? 0 : (int) Math.ceil((double) total / size);

        return new CommentNextCommentSearchPage(
            page,
            size,
            total,
            totalPages,
            page < totalPages,
            page > 1 && total > 0,
            List.copyOf(matches.subList(fromIndex, toIndex))
        );
    }
}
