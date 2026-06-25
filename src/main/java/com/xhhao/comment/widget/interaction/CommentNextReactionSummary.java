package com.xhhao.comment.widget.interaction;

import java.util.List;

public record CommentNextReactionSummary(
    String targetType,
    String targetKey,
    String prompt,
    boolean enabled,
    boolean allowAnonymous,
    List<CommentNextReactionItem> items
) {

    public record CommentNextReactionItem(
        String name,
        String type,
        String value,
        String label,
        long count,
        boolean selected
    ) {
    }
}
