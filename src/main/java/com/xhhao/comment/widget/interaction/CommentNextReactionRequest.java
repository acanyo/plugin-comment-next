package com.xhhao.comment.widget.interaction;

public record CommentNextReactionRequest(
    String targetType,
    String group,
    String kind,
    String version,
    String name,
    String reaction
) {
}
