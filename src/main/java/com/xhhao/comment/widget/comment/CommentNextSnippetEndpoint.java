package com.xhhao.comment.widget.comment;

import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.web.reactive.function.server.RouterFunction;
import org.springframework.web.reactive.function.server.RouterFunctions;
import org.springframework.web.reactive.function.server.ServerResponse;
import reactor.core.publisher.Mono;
import run.halo.app.core.extension.endpoint.CustomEndpoint;
import run.halo.app.extension.GroupVersion;

@Component
@RequiredArgsConstructor
class CommentNextSnippetEndpoint implements CustomEndpoint {
    private final CommentNextSnippetService service;

    @Override
    public RouterFunction<ServerResponse> endpoint() {
        return RouterFunctions.route().GET("comments/snippets", request -> {
            int page;
            try {
                page = Integer.parseInt(request.queryParam("page").orElse("1"));
                if (page < 1 || page > 100000) throw new NumberFormatException();
            } catch (NumberFormatException exception) {
                return Mono.error(new ResponseStatusException(HttpStatus.BAD_REQUEST, "页码无效"));
            }
            return service.list(new CommentNextCommentQuery(request),
                    request.queryParam("key").orElse(""), page)
                .flatMap(value -> ServerResponse.ok().header("Cache-Control", "private, no-store")
                    .bodyValue(value));
        }).build();
    }

    @Override
    public GroupVersion groupVersion() {
        return GroupVersion.parseAPIVersion("api.commentnext.xhhao.com/v1alpha1");
    }
}
