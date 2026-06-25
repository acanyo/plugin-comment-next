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
public class CommentNextAiReplyRecordEndpoint implements CustomEndpoint {

    private final CommentNextAiReplyRecordService recordService;

    @Override
    public RouterFunction<ServerResponse> endpoint() {
        return RouterFunctions.route()
            .GET("ai/reply-records", this::listRecords)
            .POST("ai/reply-records/comments/{name}/generate", this::generateForComment)
            .POST("ai/reply-records/replies/{name}/generate", this::generateForReply)
            .POST("ai/reply-records/{name}/select", this::selectCandidate)
            .POST("ai/reply-records/{name}/publish", this::publish)
            .POST("ai/reply-records/{name}/reject", this::reject)
            .build();
    }

    private Mono<ServerResponse> listRecords(ServerRequest request) {
        return recordService.list(new CommentNextAiReplyRecordQuery(request))
            .flatMap(records -> ServerResponse.ok().bodyValue(records));
    }

    private Mono<ServerResponse> publish(ServerRequest request) {
        return request.bodyToMono(CommentNextAiReplyCandidateSelection.class)
            .defaultIfEmpty(new CommentNextAiReplyCandidateSelection())
            .flatMap(selection -> recordService.publish(request.pathVariable("name"), selection.getIndex()))
            .flatMap(record -> ServerResponse.ok().bodyValue(record));
    }

    private Mono<ServerResponse> generateForComment(ServerRequest request) {
        return request.bodyToMono(CommentNextAiReplyGenerateRequest.class)
            .defaultIfEmpty(new CommentNextAiReplyGenerateRequest())
            .flatMap(body -> recordService.generateForCommentManually(request.pathVariable("name"), body))
            .flatMap(record -> ServerResponse.ok().bodyValue(record));
    }

    private Mono<ServerResponse> generateForReply(ServerRequest request) {
        return request.bodyToMono(CommentNextAiReplyGenerateRequest.class)
            .defaultIfEmpty(new CommentNextAiReplyGenerateRequest())
            .flatMap(body -> recordService.generateForReplyManually(request.pathVariable("name"), body))
            .flatMap(record -> ServerResponse.ok().bodyValue(record));
    }

    private Mono<ServerResponse> selectCandidate(ServerRequest request) {
        return request.bodyToMono(CommentNextAiReplyCandidateSelection.class)
            .flatMap(body -> recordService.selectCandidate(request.pathVariable("name"), body.getIndex()))
            .flatMap(record -> ServerResponse.ok().bodyValue(record));
    }

    private Mono<ServerResponse> reject(ServerRequest request) {
        return recordService.reject(request.pathVariable("name"))
            .flatMap(record -> ServerResponse.ok().bodyValue(record));
    }

    @Override
    public GroupVersion groupVersion() {
        return GroupVersion.parseAPIVersion("api.commentnext.xhhao.com/v1alpha1");
    }
}
