package com.xhhao.comment.widget.search;

/**
 * Request used to replace the email identity attached to historical comments
 * and replies.
 */
public record CommentNextCommentEmailReplacementRequest(
    String oldEmail,
    String newEmail
) {
}
