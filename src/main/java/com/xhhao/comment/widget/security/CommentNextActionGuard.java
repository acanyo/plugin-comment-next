package com.xhhao.comment.widget.security;

import com.xhhao.comment.utils.CommonUtils;
import io.github.resilience4j.ratelimiter.RateLimiterConfig;
import io.github.resilience4j.ratelimiter.RateLimiterRegistry;
import io.github.resilience4j.ratelimiter.RequestNotPermitted;
import io.github.resilience4j.reactor.ratelimiter.operator.RateLimiterOperator;
import java.net.URI;
import java.util.HashSet;
import java.util.Locale;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import org.springframework.web.reactive.function.server.ServerRequest;
import org.springframework.web.server.ResponseStatusException;
import reactor.core.publisher.Mono;

@Component
@RequiredArgsConstructor
public class CommentNextActionGuard {
    private static final String RATE_LIMITER_KEY_PREFIX = "plugin-comment-next-action-";

    private static final Set<String> TRUSTED_FETCH_SITES = Set.of("same-origin", "same-site", "none");

    private final RateLimiterRegistry rateLimiterRegistry;

    private final Set<String> rateLimiterNames = ConcurrentHashMap.newKeySet();

    public Mono<CommentNextActionActor> verify(ServerRequest request,
                                               CommentNextAction action,
                                               CommentNextActionSecurityPolicy policy) {
        if (!policy.enabled()) {
            return Mono.error(new ResponseStatusException(
                HttpStatus.FORBIDDEN,
                action.getDisabledMessage()
            ));
        }

        try {
            verifyRequestSource(request, policy);
        } catch (ResponseStatusException e) {
            return Mono.error(e);
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
        if (!policy.rateLimitEnabled()) {
            return Mono.just(actor);
        }

        var limit = policy.limitFor(actor);
        var window = policy.windowFor(actor);
        var config = new RateLimiterConfig.Builder()
            .limitForPeriod(limit)
            .limitRefreshPeriod(window)
            .timeoutDuration(java.time.Duration.ZERO)
            .build();
        var rateLimiterName = rateLimiterKey(action, actor, limit, window.toSeconds());
        rateLimiterNames.add(rateLimiterName);
        var rateLimiter = rateLimiterRegistry.rateLimiter(rateLimiterName, config);

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

    public void dispose() {
        new HashSet<>(rateLimiterNames).forEach(name -> {
            rateLimiterRegistry.remove(name);
            rateLimiterNames.remove(name);
        });
    }

    private void verifyRequestSource(ServerRequest request, CommentNextActionSecurityPolicy policy) {
        if (!policy.antiHotlinkEnabled()) {
            return;
        }

        var fetchSite = firstHeader(request, "Sec-Fetch-Site");
        if (StringUtils.hasText(fetchSite)
            && !TRUSTED_FETCH_SITES.contains(fetchSite.toLowerCase(Locale.ROOT))) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "不允许跨站调用该接口");
        }

        var requestOrigin = requestOrigin(request);
        if (!StringUtils.hasText(requestOrigin)) {
            if (policy.allowMissingOrigin()) {
                return;
            }
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "请求来源缺失");
        }

        var serverOrigin = serverOrigin(request);
        if (sameOrigin(requestOrigin, serverOrigin) || allowedOrigin(policy, requestOrigin)) {
            return;
        }

        throw new ResponseStatusException(HttpStatus.FORBIDDEN, "请求来源不被允许");
    }

    private String requestOrigin(ServerRequest request) {
        var origin = normalizeOrigin(firstHeader(request, "Origin"));
        if (StringUtils.hasText(origin)) {
            return origin;
        }
        return originFromUrl(firstHeader(request, "Referer"));
    }

    private String serverOrigin(ServerRequest request) {
        var scheme = firstForwardedValue(firstHeader(request, "X-Forwarded-Proto"));
        if (!StringUtils.hasText(scheme)) {
            scheme = request.uri().getScheme();
        }

        var host = firstForwardedValue(firstHeader(request, "X-Forwarded-Host"));
        if (!StringUtils.hasText(host)) {
            host = firstHeader(request, "Host");
        }
        if (!StringUtils.hasText(host)) {
            host = request.uri().getAuthority();
        }

        if (StringUtils.hasText(scheme) && StringUtils.hasText(host)) {
            return normalizeOrigin(scheme + "://" + host);
        }
        return originFromUrl(request.uri().toString());
    }

    private boolean allowedOrigin(CommentNextActionSecurityPolicy policy, String requestOrigin) {
        return policy.allowedOrigins().stream()
            .map(this::normalizeOrigin)
            .filter(StringUtils::hasText)
            .anyMatch(allowedOrigin -> sameOrigin(requestOrigin, allowedOrigin));
    }

    private boolean sameOrigin(String left, String right) {
        return StringUtils.hasText(left)
            && StringUtils.hasText(right)
            && left.equalsIgnoreCase(right);
    }

    private String originFromUrl(String url) {
        if (!StringUtils.hasText(url)) {
            return "";
        }
        try {
            var uri = URI.create(url.trim());
            if (!StringUtils.hasText(uri.getScheme()) || !StringUtils.hasText(uri.getRawAuthority())) {
                return "";
            }
            return normalizeOrigin(uri.getScheme() + "://" + uri.getRawAuthority());
        } catch (IllegalArgumentException e) {
            return "";
        }
    }

    private String normalizeOrigin(String origin) {
        if (!StringUtils.hasText(origin)) {
            return "";
        }
        try {
            var uri = URI.create(origin.trim());
            if (!StringUtils.hasText(uri.getScheme()) || !StringUtils.hasText(uri.getRawAuthority())) {
                return "";
            }
            var scheme = uri.getScheme().toLowerCase(Locale.ROOT);
            var authority = uri.getRawAuthority();
            var host = uri.getHost();
            var port = uri.getPort();
            if (StringUtils.hasText(host)) {
                authority = host.contains(":")
                    ? "[" + host.toLowerCase(Locale.ROOT) + "]"
                    : host.toLowerCase(Locale.ROOT);
                if (port > 0 && !isDefaultPort(scheme, port)) {
                    authority = authority + ":" + port;
                }
            }
            var normalized = scheme + "://" + authority;
            if (normalized.endsWith("/")) {
                return normalized.substring(0, normalized.length() - 1);
            }
            return normalized;
        } catch (IllegalArgumentException e) {
            return "";
        }
    }

    private String firstHeader(ServerRequest request, String name) {
        return request.headers().firstHeader(name);
    }

    private String firstForwardedValue(String value) {
        if (!StringUtils.hasText(value)) {
            return "";
        }
        return value.split(",", 2)[0].trim();
    }

    private boolean isDefaultPort(String scheme, int port) {
        return ("http".equalsIgnoreCase(scheme) && port == 80)
            || ("https".equalsIgnoreCase(scheme) && port == 443);
    }
}
