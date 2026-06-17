package com.xhhao.comment.widget.emote;

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
public class CommentNextEmoteEndpoint implements CustomEndpoint {

    private final CommentNextEmoteService emoteService;

    @Override
    public RouterFunction<ServerResponse> endpoint() {
        return RouterFunctions.route()
            .GET("emotes", this::listEnabledEmotes)
            .build();
    }

    private Mono<ServerResponse> listEnabledEmotes(ServerRequest request) {
        return emoteService.enabledEmotePacks()
            .flatMap(emotes -> ServerResponse.ok().bodyValue(emotes));
    }

    @Override
    public GroupVersion groupVersion() {
        return GroupVersion.parseAPIVersion("api.commentnext.xhhao.com/v1alpha1");
    }
}
