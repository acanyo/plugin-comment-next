package com.xhhao.comment.widget.ranking;

import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.server.RouterFunction;
import org.springframework.web.reactive.function.server.RouterFunctions;
import org.springframework.web.reactive.function.server.ServerRequest;
import org.springframework.web.reactive.function.server.ServerResponse;
import reactor.core.publisher.Mono;
import run.halo.app.core.extension.endpoint.CustomEndpoint;
import run.halo.app.extension.GroupVersion;

/** Public endpoint consumed by the comment ranking editor block. */
@Component
public class CommentNextCommentRankingEndpoint implements CustomEndpoint {

    private final CommentNextCommentRankingService rankingService;

    public CommentNextCommentRankingEndpoint(CommentNextCommentRankingService rankingService) {
        this.rankingService = rankingService;
    }

    @Override
    public RouterFunction<ServerResponse> endpoint() {
        return RouterFunctions.route()
            .GET("comments/ranking", this::listRanking)
            .build();
    }

    private Mono<ServerResponse> listRanking(ServerRequest request) {
        return rankingService.list(
                request.queryParam("period").orElse(null),
                request.queryParam("limit").orElse(null)
            )
            .flatMap(response -> ServerResponse.ok().bodyValue(response));
    }

    @Override
    public GroupVersion groupVersion() {
        return GroupVersion.parseAPIVersion("api.commentnext.xhhao.com/v1alpha1");
    }
}
