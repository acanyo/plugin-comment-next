package com.xhhao.comment.widget.ranking;

import java.time.Instant;

/** A public, privacy-safe row in the comment ranking. */
public record CommentNextCommentRankingItem(
    String displayName,
    String avatar,
    String kind,
    long commentCount,
    long upvoteCount,
    String latestComment,
    Instant latestCommentAt
) {
}
