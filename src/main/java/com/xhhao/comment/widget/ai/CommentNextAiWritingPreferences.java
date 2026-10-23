package com.xhhao.comment.widget.ai;

import java.util.List;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

final class CommentNextAiWritingPreferences {
    private static final int MAX_TAGS = 8;

    private static final int MAX_TAG_LENGTH = 24;

    private CommentNextAiWritingPreferences() {
    }

    static List<String> normalize(List<String> tags) {
        if (tags == null || tags.isEmpty()) {
            return List.of();
        }
        if (tags.size() > MAX_TAGS) {
            throw invalidTags();
        }
        for (var tag : tags) {
            if (tag == null || tag.isBlank() || tag.strip().length() > MAX_TAG_LENGTH) {
                throw invalidTags();
            }
        }
        return tags.stream().map(String::strip).distinct().toList();
    }

    static String prompt(List<String> tags) {
        var normalized = normalize(tags);
        if (normalized.isEmpty()) {
            return "";
        }
        return """

            用户选择的表达标签：%s
            请结合写作模式、用户原文和已提供的文章上下文体现这些表达意向，不要机械拼接标签。
            标签只表示用户希望的方向，不是事实证据。不要据此编造已经解决问题、亲自实践、收藏、推荐或其他未提供的具体经历。
            如果没有文章正文上下文，不要声称读过文章，也不要编造文章的观点或细节。
            """.formatted(String.join("、", normalized));
    }

    private static ResponseStatusException invalidTags() {
        return new ResponseStatusException(HttpStatus.BAD_REQUEST,
            "评论标签最多选择 8 个，每个标签须为 1 至 24 个字符");
    }
}
