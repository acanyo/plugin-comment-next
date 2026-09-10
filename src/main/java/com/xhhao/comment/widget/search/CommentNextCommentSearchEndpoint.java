package com.xhhao.comment.widget.search;

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
public class CommentNextCommentSearchEndpoint implements CustomEndpoint {

    private final CommentNextCommentSearchService searchService;

    @Override
    public RouterFunction<ServerResponse> endpoint() {
        return RouterFunctions.route()
            .GET("comment-search", this::search)
            .build();
    }

    private Mono<ServerResponse> search(ServerRequest request) {
        return searchService.search(new CommentNextCommentSearchQuery(request))
            .flatMap(result -> ServerResponse.ok().bodyValue(result));
    }

    @Override
    public GroupVersion groupVersion() {
        return new GroupVersion("console.api.commentnext.xhhao.com", "v1alpha1");
    }
}
