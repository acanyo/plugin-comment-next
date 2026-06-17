package com.xhhao.comment.widget.ai;

import static org.springframework.web.reactive.function.server.RequestPredicates.contentType;

import lombok.RequiredArgsConstructor;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.server.RouterFunction;
import org.springframework.web.reactive.function.server.RouterFunctions;
import org.springframework.web.reactive.function.server.ServerRequest;
import org.springframework.web.reactive.function.server.ServerResponse;
import reactor.core.publisher.Mono;
import run.halo.app.core.extension.endpoint.CustomEndpoint;
import run.halo.app.extension.GroupVersion;

@Component
@ConditionalOnHaloAiFoundation
@RequiredArgsConstructor
public class CommentNextAiEndpoint implements CustomEndpoint {
    private final CommentNextAiService aiService;

    @Override
    public RouterFunction<ServerResponse> endpoint() {
        return RouterFunctions.route()
            .POST("ai/-/suggestions", contentType(MediaType.APPLICATION_JSON), this::generateSuggestion)
            .build();
    }

    private Mono<ServerResponse> generateSuggestion(ServerRequest request) {
        return request.bodyToMono(CommentNextAiSuggestionRequest.class)
            .defaultIfEmpty(new CommentNextAiSuggestionRequest())
            .flatMap(body -> aiService.generateSuggestion(request, body))
            .flatMap(result -> ServerResponse.ok()
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue(result));
    }

    @Override
    public GroupVersion groupVersion() {
        return GroupVersion.parseAPIVersion("api.commentnext.xhhao.com/v1alpha1");
    }
}
