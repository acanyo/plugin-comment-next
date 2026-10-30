package com.xhhao.comment.widget.ranking;

import com.xhhao.comment.widget.comment.CommentNextBadge;
import java.time.Instant;

/** A public, privacy-safe row in the comment ranking. */
public record CommentNextCommentRankingItem(
    String displayName,
    String avatar,
    String kind,
    long commentCount,
    long upvoteCount,
    String latestComment,
    Instant latestCommentAt,
    CommentNextBadge levelBadge
) {
    CommentNextCommentRankingItem withLevelBadge(CommentNextBadge badge) {
        return new CommentNextCommentRankingItem(displayName, avatar, kind, commentCount,
            upvoteCount, latestComment, latestCommentAt, badge);
    }
}
