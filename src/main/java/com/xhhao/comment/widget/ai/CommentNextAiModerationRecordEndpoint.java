package com.xhhao.comment.widget.ai;

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
public class CommentNextAiModerationRecordEndpoint implements CustomEndpoint {

    private final CommentNextAiModerationRecordService recordService;

    @Override
    public RouterFunction<ServerResponse> endpoint() {
        return RouterFunctions.route()
            .GET("ai/moderation-records", this::listRecords)
            .PUT("ai/moderation-records/{targetType}/{name}/approve", this::approveRecord)
            .build();
    }

    private Mono<ServerResponse> listRecords(ServerRequest request) {
        return recordService.list(new CommentNextAiModerationRecordQuery(request))
            .flatMap(records -> ServerResponse.ok().bodyValue(records));
    }

    private Mono<ServerResponse> approveRecord(ServerRequest request) {
        return recordService.approve(
                request.pathVariable("targetType"),
                request.pathVariable("name")
            )
            .then(ServerResponse.noContent().build());
    }

    @Override
    public GroupVersion groupVersion() {
        return GroupVersion.parseAPIVersion("api.commentnext.xhhao.com/v1alpha1");
    }
}
