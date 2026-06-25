package com.xhhao.comment.widget.security;

import java.util.Locale;

public enum CommentNextSecurityReviewAction {
    PENDING_REVIEW,
    REJECT,
    TAG,
    NOTICE;

    public static CommentNextSecurityReviewAction from(String value) {
        if (value == null || value.isBlank()) {
            return PENDING_REVIEW;
        }
        try {
            return valueOf(value.strip().toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException ignored) {
            return PENDING_REVIEW;
        }
    }

    public boolean shouldMarkPending() {
        return this == PENDING_REVIEW || this == REJECT;
    }
}
