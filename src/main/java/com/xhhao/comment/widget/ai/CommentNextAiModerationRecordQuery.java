package com.xhhao.comment.widget.ai;

import org.springframework.util.StringUtils;
import org.springframework.web.reactive.function.server.ServerRequest;

class CommentNextAiModerationRecordQuery {

    private final ServerRequest request;

    CommentNextAiModerationRecordQuery(ServerRequest request) {
        this.request = request;
    }

    int page() {
        return intQueryParam("page", 1);
    }

    int size() {
        return intQueryParam("size", 20);
    }

    Target target() {
        return Target.from(queryParam("target", "all"));
    }

    boolean interceptedOnly() {
        return Boolean.parseBoolean(queryParam("intercepted", "true"));
    }

    String keyword() {
        return queryParam("keyword", "");
    }

    private int intQueryParam(String name, int defaultValue) {
        var value = queryParam(name, "");
        return StringUtils.hasText(value) ? Integer.parseInt(value) : defaultValue;
    }

    private String queryParam(String name, String defaultValue) {
        return request.queryParam(name).orElse(defaultValue);
    }

    enum Target {
        ALL,
        COMMENT,
        REPLY;

        static Target from(String value) {
            if ("comment".equalsIgnoreCase(value)) {
                return COMMENT;
            }
            if ("reply".equalsIgnoreCase(value)) {
                return REPLY;
            }
            return ALL;
        }
    }
}
