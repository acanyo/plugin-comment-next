package com.xhhao.comment.widget.comment;

import org.springframework.util.StringUtils;
import org.springframework.web.reactive.function.server.ServerRequest;
import org.springframework.web.server.ServerWebInputException;
import run.halo.app.extension.Ref;

class CommentNextCommentQuery {

    private final ServerRequest request;

    CommentNextCommentQuery(ServerRequest request) {
        this.request = request;
    }

    Ref toRef() {
        var ref = new Ref();
        ref.setGroup(queryParam("group"));
        ref.setKind(requiredQueryParam("kind"));
        ref.setName(requiredQueryParam("name"));
        ref.setVersion(requiredQueryParam("version"));
        return ref;
    }

    int getPage() {
        return intQueryParam("page", 1);
    }

    int getSize() {
        return intQueryParam("size", 20);
    }

    boolean isWithReplies() {
        return Boolean.parseBoolean(queryParam("withReplies", "true"));
    }

    int getReplySize() {
        return intQueryParam("replySize", 10);
    }

    SortMode getSortMode() {
        return SortMode.from(queryParam("sort", SortMode.LATEST.value));
    }

    private int intQueryParam(String name, int defaultValue) {
        var value = queryParam(name);
        return StringUtils.hasText(value) ? Integer.parseInt(value) : defaultValue;
    }

    private String requiredQueryParam(String name) {
        var value = queryParam(name);
        if (!StringUtils.hasText(value)) {
            throw new ServerWebInputException("The query parameter '" + name + "' is required.");
        }
        return value;
    }

    private String queryParam(String name) {
        return request.queryParam(name).orElse(null);
    }

    private String queryParam(String name, String defaultValue) {
        return request.queryParam(name).orElse(defaultValue);
    }

    enum SortMode {
        HOT("hot"),
        LATEST("latest"),
        EARLIEST("earliest");

        private final String value;

        SortMode(String value) {
            this.value = value;
        }

        static SortMode from(String value) {
            if (LATEST.value.equalsIgnoreCase(value)) {
                return LATEST;
            }
            if (EARLIEST.value.equalsIgnoreCase(value)) {
                return EARLIEST;
            }
            if (HOT.value.equalsIgnoreCase(value)) {
                return HOT;
            }
            return LATEST;
        }
    }
}
