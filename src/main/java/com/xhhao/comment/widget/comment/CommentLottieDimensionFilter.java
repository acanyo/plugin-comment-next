package com.xhhao.comment.widget.comment;

import static org.springframework.security.web.server.util.matcher.ServerWebExchangeMatchers.pathMatchers;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.xhhao.comment.utils.JsonUtils;
import com.xhhao.comment.widget.SettingConfigGetter;
import java.io.IOException;
import org.springframework.core.io.buffer.DataBufferLimitException;
import org.springframework.core.io.buffer.DataBufferUtils;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.server.reactive.ServerHttpRequestDecorator;
import org.springframework.lang.NonNull;
import org.springframework.security.config.web.server.SecurityWebFiltersOrder;
import org.springframework.security.web.server.util.matcher.OrServerWebExchangeMatcher;
import org.springframework.security.web.server.util.matcher.ServerWebExchangeMatcher;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ServerWebExchange;
import org.springframework.web.server.WebFilterChain;
import org.springframework.web.server.ResponseStatusException;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import run.halo.app.security.AdditionalWebFilter;

@Component
public class CommentLottieDimensionFilter implements AdditionalWebFilter {

    static final int MAX_REQUEST_BODY_BYTES = 1024 * 1024;

    private final ServerWebExchangeMatcher pathMatcher = new OrServerWebExchangeMatcher(
        pathMatchers(HttpMethod.POST, "/apis/api.halo.run/v1alpha1/comments"),
        pathMatchers(HttpMethod.POST, "/apis/api.halo.run/v1alpha1/comments/{name}/reply")
    );

    private final ObjectMapper objectMapper = JsonUtils.createObjectMapper();

    private final SettingConfigGetter settingConfigGetter;

    private final CommentLottieSubmissionSanitizer submissionSanitizer;

    public CommentLottieDimensionFilter(SettingConfigGetter settingConfigGetter,
        CommentLottieSubmissionSanitizer submissionSanitizer) {
        this.settingConfigGetter = settingConfigGetter;
        this.submissionSanitizer = submissionSanitizer;
    }

    @Override
    @NonNull
    public Mono<Void> filter(@NonNull ServerWebExchange exchange, @NonNull WebFilterChain chain) {
        return pathMatcher.matches(exchange)
            .flatMap(match -> {
                if (!match.isMatch()) {
                    return chain.filter(exchange);
                }
                return settingConfigGetter.getEmoteConfig()
                    .flatMap(config -> rewriteBody(exchange, chain, config));
            });
    }

    private Mono<Void> rewriteBody(ServerWebExchange exchange, WebFilterChain chain,
        SettingConfigGetter.EmoteConfig config) {
        var contentLength = exchange.getRequest().getHeaders().getContentLength();
        if (contentLength > MAX_REQUEST_BODY_BYTES) {
            return Mono.error(payloadTooLarge());
        }

        return DataBufferUtils.join(
                exchange.getRequest().getBody(),
                MAX_REQUEST_BODY_BYTES
            )
            .map(buffer -> {
                try {
                    var bytes = new byte[buffer.readableByteCount()];
                    buffer.read(bytes);
                    return bytes;
                } finally {
                    DataBufferUtils.release(buffer);
                }
            })
            .defaultIfEmpty(new byte[0])
            .flatMap(bytes -> {
                var rewritten = rewriteJson(bytes, config, exchange);
                var request = new ServerHttpRequestDecorator(exchange.getRequest()) {
                    @Override
                    public HttpHeaders getHeaders() {
                        var headers = new HttpHeaders();
                        headers.putAll(super.getHeaders());
                        headers.remove(HttpHeaders.TRANSFER_ENCODING);
                        headers.setContentLength(rewritten.length);
                        return headers;
                    }

                    @Override
                    public Flux<org.springframework.core.io.buffer.DataBuffer> getBody() {
                        return Flux.defer(() -> Flux.just(exchange.getResponse().bufferFactory().wrap(rewritten)));
                    }
                };
                return chain.filter(exchange.mutate().request(request).build());
            })
            .onErrorMap(DataBufferLimitException.class, error -> payloadTooLarge());
    }

    private byte[] rewriteJson(byte[] bytes, SettingConfigGetter.EmoteConfig config,
        ServerWebExchange exchange) {
        if (bytes.length == 0) {
            return bytes;
        }

        try {
            var root = objectMapper.readTree(bytes);
            if (!(root instanceof ObjectNode objectNode)) {
                return bytes;
            }

            var changed = sanitizeField(objectNode, "content", config, exchange)
                | sanitizeField(objectNode, "raw", config, exchange);
            return changed ? objectMapper.writeValueAsBytes(objectNode) : bytes;
        } catch (IOException ignored) {
            return bytes;
        }
    }

    private boolean sanitizeField(ObjectNode root, String fieldName,
        SettingConfigGetter.EmoteConfig config, ServerWebExchange exchange) {
        var value = root.get(fieldName);
        if (value == null || !value.isTextual()) {
            return false;
        }

        var original = value.textValue();
        var sanitized = submissionSanitizer.sanitize(
            original,
            config,
            exchange.getRequest()
        );
        if (original.equals(sanitized)) {
            return false;
        }

        root.put(fieldName, sanitized);
        return true;
    }

    private ResponseStatusException payloadTooLarge() {
        return new ResponseStatusException(
            HttpStatus.PAYLOAD_TOO_LARGE,
            "评论请求体不能超过 1 MiB"
        );
    }

    @Override
    public int getOrder() {
        return SecurityWebFiltersOrder.AUTHORIZATION.getOrder() - 1;
    }
}
