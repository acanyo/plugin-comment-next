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
public class CommentNextCommentEndpoint implements CustomEndpoint {

    private final CommentNextCommentService commentService;

    @Override
    public RouterFunction<ServerResponse> endpoint() {
        return RouterFunctions.route()
            .GET("comments", this::listComments)
            .GET("comments/{name}/replies", this::listReplies)
            .build();
    }

    private Mono<ServerResponse> listComments(ServerRequest request) {
        return commentService.list(new CommentNextCommentQuery(request))
            .flatMap(comments -> ServerResponse.ok().bodyValue(comments));
    }

    private Mono<ServerResponse> listReplies(ServerRequest request) {
        return commentService.listReplies(new CommentNextReplyQuery(request))
            .flatMap(replies -> ServerResponse.ok().bodyValue(replies));
    }

    @Override
    public GroupVersion groupVersion() {
        return GroupVersion.parseAPIVersion("api.commentnext.xhhao.com/v1alpha1");
    }
}
