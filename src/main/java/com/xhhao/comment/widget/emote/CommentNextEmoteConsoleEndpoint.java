package com.xhhao.comment.widget.emote;

import java.net.URI;
import java.security.Principal;
import lombok.RequiredArgsConstructor;
import org.springframework.http.CacheControl;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import org.springframework.web.reactive.function.server.RouterFunction;
import org.springframework.web.reactive.function.server.RouterFunctions;
import org.springframework.web.reactive.function.server.ServerRequest;
import org.springframework.web.reactive.function.server.ServerResponse;
import org.springframework.web.server.ServerWebInputException;
import reactor.core.publisher.Mono;
import run.halo.app.core.extension.endpoint.CustomEndpoint;
import run.halo.app.extension.GroupVersion;
import run.halo.app.infra.ExternalUrlSupplier;

/**
 * Console endpoints for emote source fetching and Halo attachment localization jobs.
 */
@Component
@RequiredArgsConstructor
public class CommentNextEmoteConsoleEndpoint implements CustomEndpoint {

    private final CommentNextEmoteLocalizationService localizationService;
    private final CommentNextEmoteAssetFetcher assetFetcher;
    private final ExternalUrlSupplier externalUrlSupplier;

    @Override
    public RouterFunction<ServerResponse> endpoint() {
        return RouterFunctions.route()
            .GET("emotes/localization-options", this::localizationOptions)
            .GET("emotes/localization", this::localizationStatus)
            .POST("emotes/localization", this::startLocalization)
            .DELETE("emotes/localization", this::cancelLocalization)
            .POST("emotes/source-fetch", this::fetchSourceJson)
            .build();
    }

    private Mono<ServerResponse> localizationOptions(ServerRequest request) {
        return localizationService.options(resolveRequestBaseUri(request))
            .flatMap(options -> ServerResponse.ok()
                .cacheControl(CacheControl.noStore())
                .bodyValue(options));
    }

    private Mono<ServerResponse> localizationStatus(ServerRequest request) {
        return localizationService.status()
            .flatMap(status -> ServerResponse.ok()
                .cacheControl(CacheControl.noStore())
                .bodyValue(status));
    }

    private Mono<ServerResponse> startLocalization(ServerRequest request) {
        var requestBaseUri = resolveRequestBaseUri(request);
        var bodyMono = request.bodyToMono(CommentNextEmoteLocalizationService.StartRequest.class)
            .switchIfEmpty(Mono.error(new ServerWebInputException("本地化请求不能为空")));
        var usernameMono = request.principal()
            .map(Principal::getName)
            .filter(StringUtils::hasText)
            .defaultIfEmpty("admin");

        return Mono.zip(bodyMono, usernameMono)
            .flatMap(tuple -> localizationService.start(tuple.getT1(), tuple.getT2(), requestBaseUri))
            .flatMap(status -> ServerResponse.accepted()
                .cacheControl(CacheControl.noStore())
                .bodyValue(status));
    }

    private Mono<ServerResponse> cancelLocalization(ServerRequest request) {
        return localizationService.cancel()
            .flatMap(status -> ServerResponse.ok()
                .cacheControl(CacheControl.noStore())
                .bodyValue(status));
    }

    private Mono<ServerResponse> fetchSourceJson(ServerRequest request) {
        return request.bodyToMono(SourceFetchRequest.class)
            .switchIfEmpty(Mono.error(new ServerWebInputException("请输入在线表情源地址")))
            .map(body -> body.url() == null ? "" : body.url().trim())
            .filter(StringUtils::hasText)
            .switchIfEmpty(Mono.error(new ServerWebInputException("请输入在线表情源地址")))
            .flatMap(assetFetcher::fetchJson)
            .onErrorMap(IllegalArgumentException.class, e -> new ServerWebInputException(e.getMessage(), null, e))
            .flatMap(json -> ServerResponse.ok()
                .contentType(MediaType.APPLICATION_JSON)
                .cacheControl(CacheControl.noStore())
                .bodyValue(json));
    }

    private URI resolveRequestBaseUri(ServerRequest request) {
        try {
            var url = externalUrlSupplier.getURL(request.exchange().getRequest());
            if (url != null) {
                return url.toURI();
            }
        } catch (Exception ignored) {
            // Fall back to request URI
        }
        return request.uri();
    }

    @Override
    public GroupVersion groupVersion() {
        return new GroupVersion("console.api.commentnext.xhhao.com", "v1alpha1");
    }

    record SourceFetchRequest(String url) {
    }
}
