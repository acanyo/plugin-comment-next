package com.xhhao.comment.widget.comment;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.server.RouterFunction;
import org.springframework.web.reactive.function.server.RouterFunctions;
import org.springframework.web.reactive.function.server.ServerRequest;
import org.springframework.web.reactive.function.server.ServerResponse;
import reactor.core.publisher.Mono;
import run.halo.app.core.extension.endpoint.CustomEndpoint;
import run.halo.app.extension.GroupVersion;

@Component
@RequiredArgsConstructor
public class CommentNextModerationEndpoint implements CustomEndpoint {

    private final CommentNextModerationService moderationService;

    @Override
    public RouterFunction<ServerResponse> endpoint() {
        return RouterFunctions.route()
            .PUT("comments/{name}/moderation", this::updateComment)
            .PUT("replies/{name}/moderation", this::updateReply)
            .build();
    }

    private Mono<ServerResponse> updateComment(ServerRequest request) {
        return request.bodyToMono(CommentNextModerationRequest.class)
            .defaultIfEmpty(emptyRequest())
            .flatMap(param -> moderationService.updateComment(request.pathVariable("name"), param))
            .flatMap(state -> ServerResponse.ok().bodyValue(state));
    }

    private Mono<ServerResponse> updateReply(ServerRequest request) {
        return request.bodyToMono(CommentNextModerationRequest.class)
            .defaultIfEmpty(emptyRequest())
            .flatMap(param -> moderationService.updateReply(request.pathVariable("name"), param))
            .flatMap(state -> ServerResponse.ok().bodyValue(state));
    }

    private CommentNextModerationRequest emptyRequest() {
        return new CommentNextModerationRequest(null, null, null);
    }

    @Override
    public GroupVersion groupVersion() {
        return GroupVersion.parseAPIVersion("api.commentnext.xhhao.com/v1alpha1");
    }
}
