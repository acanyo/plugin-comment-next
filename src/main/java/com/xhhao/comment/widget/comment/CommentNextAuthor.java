package com.xhhao.comment.widget.comment;

import java.util.List;

record CommentNextAuthor(
    String name,
    String displayName,
    String avatar,
    String kind,
    String role,
    long activeCommentCount,
    List<CommentNextBadge> badges
) {
}
