package com.xhhao.comment.widget.security;

import java.time.Duration;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import run.halo.app.core.extension.content.Reply;
import run.halo.app.extension.controller.Controller;
import run.halo.app.extension.controller.ControllerBuilder;
import run.halo.app.extension.controller.Reconciler;

@Slf4j
@Component
@RequiredArgsConstructor
class CommentNextSecurityReplyReconciler implements Reconciler<Reconciler.Request> {

    private final CommentNextSecurityReviewService reviewService;

    @Override
    public Result reconcile(Request request) {
        try {
            reviewService.reconcileReply(request.name()).block(Duration.ofMinutes(1));
        } catch (Exception e) {
            log.warn("Failed to reconcile security review reply {}", request.name(), e);
        }
        return Result.doNotRetry();
    }

    @Override
    public Controller setupWith(ControllerBuilder builder) {
        return builder
            .extension(new Reply())
            .syncAllOnStart(false)
            .workerCount(1)
            .build();
    }
}
