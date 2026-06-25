package com.xhhao.comment.widget.ai;

import org.springframework.http.HttpStatus;
import org.springframework.util.StringUtils;
import org.springframework.web.server.ResponseStatusException;

record CommentNextAiSubjectRef(String group, String version, String kind, String name) {
    private static final String CONTENT_GROUP = "content.halo.run";

    static CommentNextAiSubjectRef parse(String subject) {
        if (!StringUtils.hasText(subject)) {
            throw new ResponseStatusException(
                HttpStatus.BAD_REQUEST,
                "缺少文章信息，无法总结文章评论"
            );
        }

        var parts = subject.trim().split("/", 4);
        if (parts.length != 4
            || !StringUtils.hasText(parts[0])
            || !StringUtils.hasText(parts[1])
            || !StringUtils.hasText(parts[2])
            || !StringUtils.hasText(parts[3])) {
            throw new ResponseStatusException(
                HttpStatus.BAD_REQUEST,
                "文章信息格式不正确，无法总结文章评论"
            );
        }

        return new CommentNextAiSubjectRef(
            parts[0].trim(),
            parts[1].trim(),
            parts[2].trim(),
            parts[3].trim()
        );
    }

    boolean isPost() {
        return CONTENT_GROUP.equals(group) && "Post".equalsIgnoreCase(kind);
    }

    boolean isSinglePage() {
        return CONTENT_GROUP.equals(group) && "SinglePage".equalsIgnoreCase(kind);
    }

    boolean isSupportedContent() {
        return isPost() || isSinglePage();
    }

    String displayType() {
        return isSinglePage() ? "页面" : "文章";
    }
}
