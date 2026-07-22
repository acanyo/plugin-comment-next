package com.xhhao.comment.widget.ai.website;

import com.xhhao.comment.widget.ai.ConditionalOnHaloAiFoundation;
import java.net.InetSocketAddress;
import java.net.URI;
import java.time.Duration;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.client.reactive.ReactorClientHttpConnector;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Mono;
import reactor.netty.http.client.HttpClient;

@Component
@ConditionalOnHaloAiFoundation
@RequiredArgsConstructor
class CommentNextSafeHtmlFetcher {
    private static final int MAX_REDIRECTS = 3;

    private static final int MAX_BODY_BYTES = 256 * 1024;

    private static final Duration FETCH_TIMEOUT = Duration.ofSeconds(8);

    private final CommentNextWebsiteUriPolicy uriPolicy;

    private final WebClient webClient = WebClient.builder()
        .clientConnector(new ReactorClientHttpConnector(HttpClient.create()
            .resolvedAddressesSelector((config, addresses) -> addresses.stream()
                .filter(address -> address instanceof InetSocketAddress inetAddress
                    && CommentNextWebsiteUriPolicy.isPublicAddress(inetAddress.getAddress()))
                .toList())))
        .codecs(configurer -> configurer.defaultCodecs().maxInMemorySize(MAX_BODY_BYTES))
        .defaultHeader(HttpHeaders.USER_AGENT, "Halo-Comment-Next-Website-Review/1.0")
        .defaultHeader(HttpHeaders.ACCEPT, "text/html,application/xhtml+xml")
        .build();

    Mono<CommentNextFetchedPage> fetch(URI uri) {
        return fetch(uri, MAX_REDIRECTS).timeout(FETCH_TIMEOUT);
    }

    private Mono<CommentNextFetchedPage> fetch(URI uri, int redirectsRemaining) {
        return uriPolicy.validateOutbound(uri)
            .flatMap(validated -> webClient.get()
                .uri(validated)
                .exchangeToMono(response -> {
                    if (response.statusCode().is3xxRedirection()) {
                        var location = response.headers().asHttpHeaders().getLocation();
                        if (location == null || redirectsRemaining <= 0) {
                            return response.releaseBody()
                                .then(Mono.error(new IllegalStateException("网页重定向次数过多或缺少地址")));
                        }
                        return response.releaseBody()
                            .then(fetch(validated.resolve(location), redirectsRemaining - 1));
                    }

                    if (!response.statusCode().is2xxSuccessful()) {
                        return response.releaseBody()
                            .then(Mono.error(new IllegalStateException(
                                "网页返回非成功状态：" + response.statusCode().value()
                            )));
                    }

                    var contentType = response.headers().contentType().orElse(null);
                    if (!isHtml(contentType)) {
                        return response.releaseBody()
                            .then(Mono.error(new IllegalStateException("网址返回的不是 HTML 页面")));
                    }

                    return response.bodyToMono(String.class)
                        .filter(html -> !html.isBlank())
                        .switchIfEmpty(Mono.error(new IllegalStateException("网页内容为空")))
                        .map(html -> new CommentNextFetchedPage(validated, html));
                }));
    }

    private boolean isHtml(MediaType contentType) {
        return contentType == null
            || MediaType.TEXT_HTML.isCompatibleWith(contentType)
            || MediaType.APPLICATION_XHTML_XML.isCompatibleWith(contentType);
    }
}
