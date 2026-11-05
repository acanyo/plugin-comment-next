package com.xhhao.comment.widget.security.email;

import static org.springframework.security.web.server.util.matcher.ServerWebExchangeMatchers.pathMatchers;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.xhhao.comment.utils.JsonUtils;
import com.xhhao.comment.widget.SettingConfigGetter;
import com.xhhao.comment.widget.comment.CommentNextRequestBody;
import java.io.IOException;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpMethod;
import org.springframework.lang.NonNull;
import org.springframework.security.authentication.AnonymousAuthenticationToken;
import org.springframework.security.config.web.server.SecurityWebFiltersOrder;
import org.springframework.security.core.Authentication;
import org.springframework.security.web.server.util.matcher.OrServerWebExchangeMatcher;
import org.springframework.security.web.server.util.matcher.ServerWebExchangeMatcher;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ServerWebExchange;
import org.springframework.web.server.WebFilterChain;
import reactor.core.publisher.Mono;
import run.halo.app.infra.AnonymousUserConst;
import run.halo.app.security.AdditionalWebFilter;

/** Rejects restricted anonymous addresses before comments or replies can be created. */
@Component
@RequiredArgsConstructor
public class CommentNextEmailRestrictionFilter implements AdditionalWebFilter {

    private final SettingConfigGetter settings;
    private final CommentNextEmailRestrictionService restrictions;

    private final ObjectMapper objectMapper = JsonUtils.createObjectMapper();
    private final ServerWebExchangeMatcher pathMatcher = new OrServerWebExchangeMatcher(
        pathMatchers(HttpMethod.POST, "/apis/api.halo.run/v1alpha1/comments"),
        pathMatchers(HttpMethod.POST, "/apis/api.halo.run/v1alpha1/comments/{name}/reply")
    );

    @Override
    @NonNull
    public Mono<Void> filter(@NonNull ServerWebExchange exchange, @NonNull WebFilterChain chain) {
        return pathMatcher.matches(exchange).flatMap(match -> {
            if (!match.isMatch()) {
                return chain.filter(exchange);
            }
            return isAuthenticated(exchange).flatMap(authenticated -> {
                if (authenticated) {
                    return chain.filter(exchange);
                }
                return settings.getEmailRestrictionConfig().flatMap(config -> {
                    if (!config.isEnabled()) {
                        return chain.filter(exchange);
                    }
                    return CommentNextRequestBody.read(exchange).flatMap(bytes ->
                        restrictions.validate(emailFrom(bytes), config)
                            .then(Mono.defer(() -> chain.filter(CommentNextRequestBody.replay(exchange, bytes)))));
                });
            });
        });
    }

    private Mono<Boolean> isAuthenticated(ServerWebExchange exchange) {
        return exchange.getPrincipal().map(principal -> principal instanceof Authentication authentication
                && authentication.isAuthenticated()
                && !(authentication instanceof AnonymousAuthenticationToken)
                && !AnonymousUserConst.isAnonymousUser(authentication.getName()))
            .defaultIfEmpty(false);
    }

    private String emailFrom(byte[] bytes) {
        try {
            var root = objectMapper.readTree(bytes);
            if (root == null) {
                return "";
            }
            var email = root.path("owner").path("email");
            return email.isTextual() ? email.textValue() : "";
        } catch (IOException ignored) {
            // Replay malformed input unchanged; Halo supplies the JSON validation error.
            return "";
        }
    }

    @Override
    public int getOrder() {
        return SecurityWebFiltersOrder.AUTHORIZATION.getOrder() - 2;
    }
}
