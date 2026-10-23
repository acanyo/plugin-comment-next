package com.xhhao.comment.widget.ai;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.web.server.ResponseStatusException;

class CommentNextAiWritingPreferencesTest {

    @Test
    void preservesLegacyRequestsAndNormalizesUserSelections() {
        assertThat(CommentNextAiWritingPreferences.normalize(null)).isEmpty();
        assertThat(CommentNextAiWritingPreferences.normalize(List.of(" 更自然 ", "更简洁", "更自然")))
            .containsExactly("更自然", "更简洁");
        assertThat(CommentNextAiMode.from("generate").contentRequired()).isFalse();
    }

    @Test
    void rejectsOversizedOrInvalidSelections() {
        for (var tags : List.of(Collections.nCopies(9, "标签"), List.of(" "),
            Arrays.asList("更自然", null), List.of("a".repeat(25)))) {
            assertThatThrownBy(() -> CommentNextAiWritingPreferences.normalize(tags))
                .isInstanceOf(ResponseStatusException.class);
        }
    }

    @Test
    void usesTagsAsIntentWithoutInventingExperience() {
        assertThat(CommentNextAiWritingPreferences.prompt(List.of("已收藏", "解决了我的问题")))
            .contains("已收藏、解决了我的问题", "不是事实证据", "不要据此编造", "不要声称读过文章");
        assertThat(CommentNextAiWritingPreferences.prompt(List.of())).isEmpty();
    }
}
