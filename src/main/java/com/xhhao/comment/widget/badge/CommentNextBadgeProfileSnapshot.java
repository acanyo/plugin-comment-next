package com.xhhao.comment.widget.badge;

import com.xhhao.comment.widget.comment.CommentNextBadge;
import java.util.List;

public record CommentNextBadgeProfileSnapshot(
    long activeCommentCount,
    List<CommentNextBadge> badges
) {

    public CommentNextBadgeProfileSnapshot {
        badges = badges == null ? List.of() : List.copyOf(badges);
    }

    public static CommentNextBadgeProfileSnapshot empty() {
        return new CommentNextBadgeProfileSnapshot(0, List.of());
    }
}
