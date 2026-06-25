package com.xhhao.comment.widget.comment;

import org.springframework.util.StringUtils;
import org.springframework.web.reactive.function.server.ServerRequest;
import org.springframework.web.server.ServerWebInputException;
import run.halo.app.extension.Ref;

class CommentNextFeaturedCommentQuery {

    private final ServerRequest request;

    CommentNextFeaturedCommentQuery(ServerRequest request) {
        this.request = request;
    }

    int page() {
        return intQueryParam("page", 1);
    }

    int size() {
        return intQueryParam("size", 10);
    }

    Target target() {
        return Target.from(queryParam("target", "all"));
    }

    String keyword() {
        return queryParam("keyword", "");
    }

    boolean hasSubject() {
        return StringUtils.hasText(queryParam("kind", ""))
            || StringUtils.hasText(queryParam("name", ""))
            || StringUtils.hasText(queryParam("version", ""));
    }

    Ref toRef() {
        var ref = new Ref();
        ref.setGroup(queryParam("group", ""));
        ref.setKind(requiredQueryParam("kind"));
        ref.setName(requiredQueryParam("name"));
        ref.setVersion(requiredQueryParam("version"));
        return ref;
    }

    private int intQueryParam(String name, int defaultValue) {
        var value = queryParam(name, "");
        return StringUtils.hasText(value) ? Integer.parseInt(value) : defaultValue;
    }

    private String requiredQueryParam(String name) {
        var value = queryParam(name, "");
        if (!StringUtils.hasText(value)) {
            throw new ServerWebInputException("The query parameter '" + name + "' is required.");
        }
        return value;
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
