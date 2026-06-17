package com.xhhao.comment.widget.security;

import com.xhhao.comment.utils.CommonUtils;
import io.github.resilience4j.ratelimiter.RateLimiterConfig;
import io.github.resilience4j.ratelimiter.RateLimiterRegistry;
import io.github.resilience4j.ratelimiter.RequestNotPermitted;
import io.github.resilience4j.reactor.ratelimiter.operator.RateLimiterOperator;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.server.ServerRequest;
import org.springframework.web.server.ResponseStatusException;
import reactor.core.publisher.Mono;

@Component
@RequiredArgsConstructor
public class CommentNextActionGuard {
    private static final String RATE_LIMITER_KEY_PREFIX = "plugin-comment-next-action-";

    private final RateLimiterRegistry rateLimiterRegistry;

    public Mono<CommentNextActionActor> verify(ServerRequest request,
                                               CommentNextAction action,
                                               CommentNextActionSecurityPolicy policy) {
        if (!policy.enabled()) {
            return Mono.error(new ResponseStatusException(
                HttpStatus.FORBIDDEN,
                action.getDisabledMessage()
            ));
        }

        return CommonUtils.getCurrentUserName()
            .map(username -> CommentNextActionActor.of(username, ClientIpUtils.getClientIp(request)))
            .flatMap(actor -> verifyActor(action, policy, actor));
    }

    private Mono<CommentNextActionActor> verifyActor(CommentNextAction action,
                                                    CommentNextActionSecurityPolicy policy,
                                                    CommentNextActionActor actor) {
        if (actor.anonymous() && !policy.allowAnonymous()) {
            return Mono.error(new ResponseStatusException(
                HttpStatus.UNAUTHORIZED,
                action.getAnonymousForbiddenMessage()
            ));
        }

        return applyRateLimit(action, policy, actor);
    }

    private Mono<CommentNextActionActor> applyRateLimit(CommentNextAction action,
                                                       CommentNextActionSecurityPolicy policy,
                                                       CommentNextActionActor actor) {
        var limit = policy.limitFor(actor);
        var window = policy.windowFor(actor);
        var config = new RateLimiterConfig.Builder()
            .limitForPeriod(limit)
            .limitRefreshPeriod(window)
            .timeoutDuration(java.time.Duration.ZERO)
            .build();
        var rateLimiter = rateLimiterRegistry.rateLimiter(rateLimiterKey(action, actor, limit, window.toSeconds()),
            config);

        return Mono.just(actor)
            .transformDeferred(RateLimiterOperator.of(rateLimiter))
            .onErrorMap(RequestNotPermitted.class, e -> new ResponseStatusException(
                HttpStatus.TOO_MANY_REQUESTS,
                action.getRateLimitedMessage()
            ));
    }

    private String rateLimiterKey(CommentNextAction action,
                                  CommentNextActionActor actor,
                                  int limit,
                                  long windowSeconds) {
        return RATE_LIMITER_KEY_PREFIX
            + action.getKey()
            + "-"
            + (actor.anonymous() ? "anonymous" : "authenticated")
            + "-"
            + actor.rateLimitIdentity()
            + "-"
            + limit
            + "-"
            + windowSeconds;
    }
}
