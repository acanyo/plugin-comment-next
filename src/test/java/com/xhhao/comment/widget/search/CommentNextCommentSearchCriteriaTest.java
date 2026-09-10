package com.xhhao.comment.widget.search;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Instant;
import org.junit.jupiter.api.Test;

class CommentNextCommentSearchCriteriaTest {

    private static final CommentNextCommentSearchItem ITEM =
        new CommentNextCommentSearchItem(
            "reply",
            "reply-1",
            "comment-1",
            "content.halo.run/Post/post-1",
            "Halo User",
            "Email",
            "user@example.com",
            "",
            "203.0.113.42",
            "<p>Hello <strong>Halo</strong></p>",
            "Mozilla/5.0",
            true,
            false,
            Instant.parse("2026-09-10T00:00:00Z")
        );

    @Test
    void matchesEmailAndIpAddressTogether() {
        var matching = criteria("example.com", "203.0.113", "", "", "");
        var wrongIp = criteria("example.com", "198.51.100", "", "", "");

        assertThat(matching.matches(ITEM)).isTrue();
        assertThat(wrongIp.matches(ITEM)).isFalse();
    }

    @Test
    void matchesKeywordAcrossSensitiveAndVisibleFields() {
        assertThat(criteria("", "", "203.0.113.42", "", "").matches(ITEM)).isTrue();
        assertThat(criteria("", "", "user@example.com", "", "").matches(ITEM)).isTrue();
        assertThat(criteria("", "", "hello halo", "", "").matches(ITEM)).isTrue();
        assertThat(criteria("", "", "post-1", "", "").matches(ITEM)).isTrue();
    }

    @Test
    void combinesAuthorContentAndSubjectFilters() {
        var matching = criteria("", "", "", "halo user", "hello", "post-1");
        var wrongContent = criteria("", "", "", "halo user", "goodbye", "post-1");

        assertThat(matching.matches(ITEM)).isTrue();
        assertThat(wrongContent.matches(ITEM)).isFalse();
    }

    @Test
    void filtersTargetAndModerationStatus() {
        var visibleReplies = new CommentNextCommentSearchCriteria(
            CommentNextCommentSearchCriteria.Target.REPLY,
            CommentNextCommentSearchCriteria.Status.VISIBLE,
            "",
            "",
            "",
            "",
            "",
            ""
        );
        var pendingReplies = new CommentNextCommentSearchCriteria(
            CommentNextCommentSearchCriteria.Target.REPLY,
            CommentNextCommentSearchCriteria.Status.PENDING,
            "",
            "",
            "",
            "",
            "",
            ""
        );

        assertThat(visibleReplies.matches(ITEM)).isTrue();
        assertThat(pendingReplies.matches(ITEM)).isFalse();
    }

    private CommentNextCommentSearchCriteria criteria(
        String email,
        String ipAddress,
        String keyword,
        String author,
        String content
    ) {
        return criteria(email, ipAddress, keyword, author, content, "");
    }

    private CommentNextCommentSearchCriteria criteria(
        String email,
        String ipAddress,
        String keyword,
        String author,
        String content,
        String subject
    ) {
        return new CommentNextCommentSearchCriteria(
            CommentNextCommentSearchCriteria.Target.ALL,
            CommentNextCommentSearchCriteria.Status.ALL,
            keyword,
            email,
            ipAddress,
            author,
            content,
            subject
        );
    }
}
