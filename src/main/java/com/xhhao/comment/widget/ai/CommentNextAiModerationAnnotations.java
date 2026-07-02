package com.xhhao.comment.widget.ai;

import java.util.Map;

final class CommentNextAiModerationAnnotations {

    private static final String PREFIX = "commentnext.xhhao.com/ai-review-";

    static final String REVIEWED = "commentnext.xhhao.com/ai-review-reviewed";

    static final String REVIEWED_AT = "commentnext.xhhao.com/ai-review-reviewed-at";

    static final String CONTENT_HASH = "commentnext.xhhao.com/ai-review-content-hash";

    static final String INTERCEPTED = "commentnext.xhhao.com/ai-review-intercepted";

    static final String ACTION = "commentnext.xhhao.com/ai-review-action";

    static final String CATEGORIES = "commentnext.xhhao.com/ai-review-categories";

    static final String LABELS = "commentnext.xhhao.com/ai-review-labels";

    static final String CONFIDENCE = "commentnext.xhhao.com/ai-review-confidence";

    static final String REASON = "commentnext.xhhao.com/ai-review-reason";

    static final String REJECTED = "commentnext.xhhao.com/ai-review-rejected";

    static boolean hasReviewAnnotations(Map<String, String> annotations) {
        return annotations != null
            && annotations.keySet().stream()
                .anyMatch(key -> key.startsWith(PREFIX));
    }

    static void clear(Map<String, String> annotations) {
        if (annotations == null) {
            return;
        }

        annotations.remove(REVIEWED);
        annotations.remove(REVIEWED_AT);
        annotations.remove(CONTENT_HASH);
        annotations.remove(INTERCEPTED);
        annotations.remove(ACTION);
        annotations.remove(CATEGORIES);
        annotations.remove(LABELS);
        annotations.remove(CONFIDENCE);
        annotations.remove(REASON);
        annotations.remove(REJECTED);
    }

    private CommentNextAiModerationAnnotations() {
    }
}
