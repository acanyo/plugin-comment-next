package com.xhhao.comment.widget.search;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.server.RouterFunction;
import org.springframework.web.reactive.function.server.RouterFunctions;
import org.springframework.web.reactive.function.server.ServerRequest;
import org.springframework.web.reactive.function.server.ServerResponse;
import org.springframework.web.server.ServerWebInputException;
import reactor.core.publisher.Mono;
import run.halo.app.core.extension.endpoint.CustomEndpoint;
import run.halo.app.extension.GroupVersion;

@Component
@RequiredArgsConstructor
public class CommentNextCommentSearchEndpoint implements CustomEndpoint {

    private final CommentNextCommentSearchService searchService;

    private final CommentNextCommentEmailReplacementService emailReplacementService;

    private final CommentNextReplyQueueService replyQueueService;

    @Override
    public RouterFunction<ServerResponse> endpoint() {
        return RouterFunctions.route()
            .GET("comment-search", this::search)
            .POST("comment-search/replace-email", this::replaceEmail)
            .PUT("comment-search/{name}/reply-queue", this::updateReplyQueue)
            .build();
    }

    private Mono<ServerResponse> search(ServerRequest request) {
        return searchService.search(new CommentNextCommentSearchQuery(request))
            .flatMap(result -> ServerResponse.ok().bodyValue(result));
    }

    private Mono<ServerResponse> replaceEmail(ServerRequest request) {
        return request.bodyToMono(CommentNextCommentEmailReplacementRequest.class)
            .switchIfEmpty(Mono.error(new ServerWebInputException("邮箱替换请求不能为空。")))
            .flatMap(emailReplacementService::replaceEmail)
            .flatMap(result -> ServerResponse.ok().bodyValue(result));
    }

    private Mono<ServerResponse> updateReplyQueue(ServerRequest request) {
        return request.bodyToMono(ReplyQueueUpdate.class)
            .filter(update -> update.dismissed() != null)
            .switchIfEmpty(Mono.error(new ServerWebInputException("请选择待回复状态。")))
            .flatMap(update -> replyQueueService.setDismissed(request.pathVariable("name"), update.dismissed()))
            .then(ServerResponse.noContent().build());
    }

    record ReplyQueueUpdate(Boolean dismissed) {
    }

    @Override
    public GroupVersion groupVersion() {
        return new GroupVersion("console.api.commentnext.xhhao.com", "v1alpha1");
    }
}
