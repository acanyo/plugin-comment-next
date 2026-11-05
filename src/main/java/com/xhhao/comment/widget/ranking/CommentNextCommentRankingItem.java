package com.xhhao.comment.widget.ranking;

import com.xhhao.comment.widget.comment.CommentNextBadge;
import java.time.Instant;

/**
 * A public row in the comment ranking, without raw email or internal identity.
 *
 * @param commentCount total authored comments and replies in the selected period
 * @param repliesCount authored replies included in commentCount
 */
public record CommentNextCommentRankingItem(
    String displayName,
    String avatar,
    String kind,
    long commentCount,
    long upvoteCount,
    String latestComment,
    Instant latestCommentAt,
    CommentNextBadge levelBadge,
    String website,
    String emailHash,
    long repliesCount
) {
    CommentNextCommentRankingItem withLevelBadge(CommentNextBadge badge) {
        return new CommentNextCommentRankingItem(displayName, avatar, kind, commentCount,
            upvoteCount, latestComment, latestCommentAt, badge, website, emailHash, repliesCount);
    }
}
