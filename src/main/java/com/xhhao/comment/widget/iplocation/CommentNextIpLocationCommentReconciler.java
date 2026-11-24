package com.xhhao.comment.widget.iplocation;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import run.halo.app.core.extension.content.Comment;
import run.halo.app.extension.controller.Controller;
import run.halo.app.extension.controller.ControllerBuilder;
import run.halo.app.extension.controller.Reconciler;

@Component
@RequiredArgsConstructor
class CommentNextIpLocationCommentReconciler implements Reconciler<Reconciler.Request> {

    private final CommentNextIpLocationRecorder recorder;

    @Override
    public Result reconcile(Request request) {
        recorder.record(Comment.class, request.name(), Comment::getSpec).block();
        return Result.doNotRetry();
    }

    @Override
    public Controller setupWith(ControllerBuilder builder) {
        return builder
            .extension(new Comment())
            .syncAllOnStart(false)
            .onUpdateMatcher(extension -> false)
            .onDeleteMatcher(extension -> false)
            .build();
    }
}
