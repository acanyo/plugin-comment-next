package com.xhhao.comment.widget.comment;

import org.springframework.util.StringUtils;
import org.springframework.web.reactive.function.server.ServerRequest;
import org.springframework.web.server.ServerWebInputException;
import run.halo.app.extension.Ref;

record CommentNextCommentLocationQuery(Ref subject, Target target, String targetName, int size,
                                       int replySize, CommentNextCommentQuery.SortMode sort,
                                       boolean separatePinned) {

    static CommentNextCommentLocationQuery from(ServerRequest request) {
        var list = new CommentNextCommentQuery(request);
        var target = switch (request.queryParam("target").orElse("")) {
            case "comment" -> Target.COMMENT;
            case "reply" -> Target.REPLY;
            default -> throw new ServerWebInputException("'target' must be comment or reply.");
        };
        var targetName = request.queryParam("targetName").filter(StringUtils::hasText)
            .orElseThrow(() -> new ServerWebInputException("'targetName' is required."));
        try {
            var size = list.getSize();
            var replySize = list.getReplySize();
            if (size < 1 || replySize < 1) {
                throw new ServerWebInputException("Page sizes must be positive integers.");
            }
            return new CommentNextCommentLocationQuery(list.toRef(), target, targetName, size,
                replySize, list.getSortMode(), list.separatesPinned());
        } catch (NumberFormatException error) {
            throw new ServerWebInputException("Page sizes must be positive integers.", null, error);
        }
    }

    enum Target {
        COMMENT,
        REPLY
    }
}
