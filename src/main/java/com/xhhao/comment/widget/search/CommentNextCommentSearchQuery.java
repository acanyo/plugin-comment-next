package com.xhhao.comment.widget.search;

import org.springframework.util.StringUtils;
import org.springframework.web.reactive.function.server.ServerRequest;
import org.springframework.web.server.ServerWebInputException;

final class CommentNextCommentSearchQuery {

    private static final int DEFAULT_PAGE_SIZE = 20;

    private static final int MAX_PAGE_SIZE = 100;

    private final ServerRequest request;

    CommentNextCommentSearchQuery(ServerRequest request) {
        this.request = request;
    }

    int page() {
        return positiveInt("page", 1, Integer.MAX_VALUE);
    }

    int size() {
        return positiveInt("size", DEFAULT_PAGE_SIZE, MAX_PAGE_SIZE);
    }

    CommentNextCommentSearchCriteria criteria() {
        return new CommentNextCommentSearchCriteria(
            CommentNextCommentSearchCriteria.Target.from(queryParam("target")),
            CommentNextCommentSearchCriteria.Status.from(queryParam("status")),
            queryParam("keyword"),
            queryParam("email"),
            queryParam("ipAddress"),
            queryParam("author"),
            queryParam("content"),
            queryParam("subject")
        );
    }

    private int positiveInt(String name, int defaultValue, int maxValue) {
        var value = queryParam(name);
        if (!StringUtils.hasText(value)) {
            return defaultValue;
        }

        try {
            return Math.min(Math.max(Integer.parseInt(value), 1), maxValue);
        } catch (NumberFormatException error) {
            throw new ServerWebInputException(
                "The query parameter '" + name + "' must be a number."
            );
        }
    }

    private String queryParam(String name) {
        return request.queryParam(name).orElse("");
    }
}
