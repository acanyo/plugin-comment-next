package com.xhhao.comment.widget.search;

import java.time.Instant;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import run.halo.app.core.extension.User;
import run.halo.app.core.extension.content.Comment;
import run.halo.app.core.extension.content.Reply;
import run.halo.app.extension.Ref;

@Component
class CommentNextCommentSearchItemMapper {

    CommentNextCommentSearchItem fromComment(Comment comment, String registeredEmail) {
        var spec = comment.getSpec();
        return fromSpec(
            "comment",
            comment.getMetadata().getName(),
            "",
            subject(spec.getSubjectRef()),
            spec,
            comment.getMetadata().getCreationTimestamp(),
            registeredEmail
        );
    }

    CommentNextCommentSearchItem fromReply(
        Reply reply,
        Comment parent,
        String registeredEmail
    ) {
        var spec = reply.getSpec();
        return fromSpec(
            "reply",
            reply.getMetadata().getName(),
            spec.getCommentName(),
            parent == null || parent.getSpec() == null
                ? ""
                : subject(parent.getSpec().getSubjectRef()),
            spec,
            reply.getMetadata().getCreationTimestamp(),
            registeredEmail
        );
    }

    private CommentNextCommentSearchItem fromSpec(
        String targetType,
        String name,
        String parentName,
        String subject,
        Comment.BaseCommentSpec spec,
        Instant metadataCreationTime,
        String registeredEmail
    ) {
        var owner = spec.getOwner();
        var ownerKind = owner == null ? "" : text(owner.getKind());
        var ownerName = owner == null ? "" : text(owner.getName());

        return new CommentNextCommentSearchItem(
            targetType,
            text(name),
            text(parentName),
            subject,
            ownerDisplayName(owner),
            ownerKind,
            Comment.CommentOwner.KIND_EMAIL.equals(ownerKind)
                ? ownerName
                : text(registeredEmail),
            User.KIND.equals(ownerKind) ? ownerName : "",
            text(spec.getIpAddress()),
            text(spec.getContent()),
            text(spec.getUserAgent()),
            Boolean.TRUE.equals(spec.getApproved()),
            Boolean.TRUE.equals(spec.getHidden()),
            spec.getCreationTime() == null ? metadataCreationTime : spec.getCreationTime()
        );
    }

    private String ownerDisplayName(Comment.CommentOwner owner) {
        if (owner == null) {
            return "匿名用户";
        }
        if (StringUtils.hasText(owner.getDisplayName())) {
            return owner.getDisplayName().strip();
        }
        if (StringUtils.hasText(owner.getName())) {
            return owner.getName().strip();
        }
        return "匿名用户";
    }

    private String subject(Ref ref) {
        return ref == null ? "" : Comment.toSubjectRefKey(ref);
    }

    private String text(String value) {
        return value == null ? "" : value;
    }
}
