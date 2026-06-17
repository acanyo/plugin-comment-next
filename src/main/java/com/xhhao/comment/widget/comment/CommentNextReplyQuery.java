package com.xhhao.comment.widget.comment;

import org.springframework.util.StringUtils;
import org.springframework.web.reactive.function.server.ServerRequest;

class CommentNextReplyQuery {

    private final ServerRequest request;

    CommentNextReplyQuery(ServerRequest request) {
        this.request = request;
    }

    String getCommentName() {
        return request.pathVariable("name");
    }

    int getPage() {
        return intQueryParam("page", 1);
    }

    int getSize() {
        return intQueryParam("size", 10);
    }

    private int intQueryParam(String name, int defaultValue) {
        var value = request.queryParam(name).orElse(null);
        return StringUtils.hasText(value) ? Integer.parseInt(value) : defaultValue;
    }
}
