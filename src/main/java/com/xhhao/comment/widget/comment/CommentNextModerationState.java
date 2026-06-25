package com.xhhao.comment.widget.comment;

import java.util.Map;
import run.halo.app.core.extension.content.Comment;
import run.halo.app.core.extension.content.Reply;

public record CommentNextModerationState(
    String name,
    boolean top,
    boolean featured,
    int priority
) {

    static CommentNextModerationState from(Comment comment) {
        return new CommentNextModerationState(
            comment.getMetadata().getName(),
            isTop(comment.getSpec()),
            isFeatured(comment.getMetadata().getAnnotations()),
            priority(comment.getSpec())
        );
    }

    static CommentNextModerationState from(Reply reply) {
        return new CommentNextModerationState(
            reply.getMetadata().getName(),
            isTop(reply.getSpec()),
            isFeatured(reply.getMetadata().getAnnotations()),
            priority(reply.getSpec())
        );
    }

    private static boolean isTop(Comment.BaseCommentSpec spec) {
        return spec != null && Boolean.TRUE.equals(spec.getTop());
    }

    private static int priority(Comment.BaseCommentSpec spec) {
        return spec == null || spec.getPriority() == null ? 0 : spec.getPriority();
    }

    private static boolean isFeatured(Map<String, String> annotations) {
        return annotations != null
            && Boolean.parseBoolean(annotations.get(CommentNextCommentAnnotations.FEATURED));
    }
}
