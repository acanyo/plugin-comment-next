package com.xhhao.comment.widget.ai;

import org.springframework.util.StringUtils;
import org.springframework.web.reactive.function.server.ServerRequest;

class CommentNextAiReplyRecordQuery {

    private final ServerRequest request;

    CommentNextAiReplyRecordQuery(ServerRequest request) {
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

    Trigger trigger() {
        return Trigger.from(queryParam("trigger", "all"));
    }

    Status status() {
        return Status.from(queryParam("status", "pending"));
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

    enum Trigger {
        ALL,
        AUTO,
        MANUAL,
        MENTION;

        static Trigger from(String value) {
            if ("auto".equalsIgnoreCase(value)) {
                return AUTO;
            }
            if ("manual".equalsIgnoreCase(value)) {
                return MANUAL;
            }
            if ("mention".equalsIgnoreCase(value)) {
                return MENTION;
            }
            return ALL;
        }
    }

    enum Status {
        ALL,
        PENDING_REVIEW,
        PUBLISHED,
        REJECTED,
        FAILED;

        static Status from(String value) {
            if ("all".equalsIgnoreCase(value)) {
                return ALL;
            }
            if ("published".equalsIgnoreCase(value)) {
                return PUBLISHED;
            }
            if ("rejected".equalsIgnoreCase(value)) {
                return REJECTED;
            }
            if ("failed".equalsIgnoreCase(value)) {
                return FAILED;
            }
            return PENDING_REVIEW;
        }
    }
}
