package com.xhhao.comment.widget.comment;

import com.xhhao.comment.widget.interaction.CommentNextReactionRequest;
import com.xhhao.comment.widget.interaction.CommentNextReactionService;
import java.time.Duration;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseCookie;
import org.springframework.util.StringUtils;
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

    private final CommentNextReactionService reactionService;

    @Override
    public RouterFunction<ServerResponse> endpoint() {
        return RouterFunctions.route()
            .GET("comments", this::listComments)
            .GET("comments/featured", this::listFeaturedComments)
            .GET("comments/{name}/replies", this::listReplies)
            .GET("target-reactions", this::reactionSummary)
            .POST("target-reactions", this::toggleReaction)
            .GET("subject-reactions", this::subjectReactionSummary)
            .POST("subject-reactions", this::toggleSubjectReaction)
            .build();
    }

    private Mono<ServerResponse> listComments(ServerRequest request) {
        return commentService.list(new CommentNextCommentQuery(request))
            .flatMap(comments -> ServerResponse.ok().bodyValue(comments));
    }

    private Mono<ServerResponse> listFeaturedComments(ServerRequest request) {
        return commentService.listFeatured(new CommentNextFeaturedCommentQuery(request))
            .flatMap(comments -> ServerResponse.ok().bodyValue(comments));
    }

    private Mono<ServerResponse> listReplies(ServerRequest request) {
        return commentService.listReplies(new CommentNextReplyQuery(request))
            .flatMap(replies -> ServerResponse.ok().bodyValue(replies));
    }

    private Mono<ServerResponse> subjectReactionSummary(ServerRequest request) {
        return reactionService.subjectSummary(reactionRequestFromQuery(request), anonymousId(request))
            .flatMap(summary -> ServerResponse.ok().bodyValue(summary));
    }

    private Mono<ServerResponse> reactionSummary(ServerRequest request) {
        return reactionService.summary(reactionRequestFromQuery(request), anonymousId(request))
            .flatMap(summary -> ServerResponse.ok().bodyValue(summary));
    }

    private Mono<ServerResponse> toggleSubjectReaction(ServerRequest request) {
        return toggleReaction(request);
    }

    private Mono<ServerResponse> toggleReaction(ServerRequest request) {
        var anonymousId = anonymousId(request);
        var nextAnonymousId = StringUtils.hasText(anonymousId)
            ? anonymousId
            : UUID.randomUUID().toString();

        return request.bodyToMono(CommentNextReactionRequest.class)
            .flatMap(reactionRequest -> reactionService.toggleReaction(
                reactionRequest,
                nextAnonymousId
            ))
            .flatMap(summary -> ServerResponse.ok()
                .cookie(reactionCookie(request, nextAnonymousId))
                .bodyValue(summary));
    }

    private CommentNextReactionRequest reactionRequestFromQuery(ServerRequest request) {
        return new CommentNextReactionRequest(
            queryParam(request, "targetType"),
            queryParam(request, "group"),
            queryParam(request, "kind"),
            queryParam(request, "version"),
            queryParam(request, "name"),
            queryParam(request, "reaction")
        );
    }

    private String queryParam(ServerRequest request, String name) {
        return request.queryParam(name).orElse(null);
    }

    private String anonymousId(ServerRequest request) {
        var cookie = request.cookies().getFirst(CommentNextReactionService.ANONYMOUS_COOKIE_NAME);
        return cookie == null ? "" : cookie.getValue();
    }

    private ResponseCookie reactionCookie(ServerRequest request, String value) {
        var secure = "https".equalsIgnoreCase(request.uri().getScheme());
        return ResponseCookie.from(CommentNextReactionService.ANONYMOUS_COOKIE_NAME, value)
            .path("/")
            .httpOnly(true)
            .secure(secure)
            .sameSite("Lax")
            .maxAge(Duration.ofDays(365))
            .build();
    }

    @Override
    public GroupVersion groupVersion() {
        return GroupVersion.parseAPIVersion("api.commentnext.xhhao.com/v1alpha1");
    }
}
