package com.xhhao.comment.widget.comment;

import java.time.Instant;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;
import reactor.core.publisher.Mono;
import run.halo.app.core.extension.content.Comment;
import run.halo.app.core.extension.content.Reply;
import run.halo.app.extension.AbstractExtension;
import run.halo.app.extension.MetadataUtil;
import run.halo.app.extension.ReactiveExtensionClient;

@Service
@RequiredArgsConstructor
class CommentNextModerationService {

    private static final int DEFAULT_PRIORITY = 0;

    private final ReactiveExtensionClient client;

    Mono<CommentNextModerationState> updateComment(String name,
        CommentNextModerationRequest request) {
        return client.fetch(Comment.class, name)
            .switchIfEmpty(Mono.error(new ResponseStatusException(HttpStatus.NOT_FOUND)))
            .flatMap(comment -> {
                applySpec(comment.getSpec(), request);
                applyFeatured(comment, request.featured());
                return client.update(comment);
            })
            .map(CommentNextModerationState::from);
    }

    Mono<CommentNextModerationState> updateReply(String name,
        CommentNextModerationRequest request) {
        return client.fetch(Reply.class, name)
            .switchIfEmpty(Mono.error(new ResponseStatusException(HttpStatus.NOT_FOUND)))
            .flatMap(reply -> {
                applySpec(reply.getSpec(), request);
                applyFeatured(reply, request.featured());
                return client.update(reply);
            })
            .map(CommentNextModerationState::from);
    }

    private void applySpec(Comment.BaseCommentSpec spec,
        CommentNextModerationRequest request) {
        if (spec == null) {
            return;
        }

        if (spec.getTop() == null) {
            spec.setTop(false);
        }
        if (spec.getPriority() == null) {
            spec.setPriority(DEFAULT_PRIORITY);
        }

        if (request.top() != null) {
            spec.setTop(request.top());
        }
        if (request.priority() != null) {
            spec.setPriority(request.priority());
        }
    }

    private void applyFeatured(AbstractExtension extension, Boolean featured) {
        if (featured == null) {
            return;
        }

        var annotations = MetadataUtil.nullSafeAnnotations(extension);
        if (Boolean.TRUE.equals(featured)) {
            annotations.put(CommentNextCommentAnnotations.FEATURED, "true");
            annotations.put(CommentNextCommentAnnotations.FEATURED_AT, Instant.now().toString());
            return;
        }

        annotations.remove(CommentNextCommentAnnotations.FEATURED);
        annotations.remove(CommentNextCommentAnnotations.FEATURED_AT);
    }
}
