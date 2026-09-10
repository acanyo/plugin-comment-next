package com.xhhao.comment.widget.search;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import org.junit.jupiter.api.Test;

class CommentNextCommentSearchPageTest {

    @Test
    void paginatesMatchesWithoutOverflowingPastTheLastPage() {
        var items = List.of(item("one"), item("two"), item("three"));

        var secondPage = CommentNextCommentSearchPage.from(items, 2, 2);
        var pagePastEnd = CommentNextCommentSearchPage.from(items, 99, 2);

        assertThat(secondPage.total()).isEqualTo(3);
        assertThat(secondPage.totalPages()).isEqualTo(2);
        assertThat(secondPage.hasNext()).isFalse();
        assertThat(secondPage.hasPrevious()).isTrue();
        assertThat(secondPage.items()).extracting(CommentNextCommentSearchItem::name)
            .containsExactly("three");
        assertThat(pagePastEnd.items()).isEmpty();
    }

    private CommentNextCommentSearchItem item(String name) {
        return new CommentNextCommentSearchItem(
            "comment",
            name,
            "",
            "",
            "User",
            "User",
            "",
            "user",
            "",
            "content",
            "",
            true,
            false,
            null
        );
    }
}
