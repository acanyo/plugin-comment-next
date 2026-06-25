package com.xhhao.comment.widget.ai;

import lombok.Data;
import org.springframework.util.StringUtils;

@Data
class CommentNextAiReplyGenerateRequest {

    private static final int DEFAULT_CANDIDATE_COUNT = 4;

    private String style;

    private Integer candidateCount;

    String normalizedStyle() {
        return StringUtils.hasText(style) ? style.strip() : "智能推荐";
    }

    int normalizedCandidateCount() {
        if (candidateCount == null || candidateCount <= 0) {
            return DEFAULT_CANDIDATE_COUNT;
        }
        return Math.min(5, Math.max(3, candidateCount));
    }
}
