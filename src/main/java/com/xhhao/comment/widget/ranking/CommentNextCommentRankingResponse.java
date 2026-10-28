package com.xhhao.comment.widget.ranking;

import java.util.List;

/** Response returned to the Svelte comment ranking web component. */
public record CommentNextCommentRankingResponse(
    String period,
    int limit,
    List<CommentNextCommentRankingItem> items
) {
}
