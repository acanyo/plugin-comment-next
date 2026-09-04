package com.xhhao.comment.widget.comment;

import com.fasterxml.jackson.databind.node.ObjectNode;
import java.util.List;

record CommentNextAuthor(
    String name,
    String displayName,
    String avatar,
    String kind,
    String role,
    long activeCommentCount,
    List<CommentNextBadge> badges,
    ObjectNode identity
) {
}
