package com.xhhao.comment.widget.ai.website;

import com.fasterxml.jackson.databind.JsonNode;
import com.xhhao.comment.widget.ai.ConditionalOnHaloAiFoundation;
import java.net.URI;
import java.net.URISyntaxException;
import java.time.Duration;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpHeaders;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Mono;

@Component
@ConditionalOnHaloAiFoundation
@RequiredArgsConstructor
class CommentNextWaybackClient {
    private static final int MAX_RESPONSE_BYTES = 64 * 1024;

    private static final Duration LOOKUP_TIMEOUT = Duration.ofSeconds(5);

    private final CommentNextSafeHtmlFetcher htmlFetcher;

    private final WebClient webClient = WebClient.builder()
        .codecs(configurer -> configurer.defaultCodecs().maxInMemorySize(MAX_RESPONSE_BYTES))
        .defaultHeader(HttpHeaders.USER_AGENT, "Halo-Comment-Next-Website-Review/1.0")
        .build();

    Mono<CommentNextFetchedPage> fetchLatest(URI website) {
        return webClient.get()
            .uri(builder -> builder
                .scheme("https")
                .host("archive.org")
                .path("/wayback/available")
                .queryParam("url", website.toASCIIString())
                .build())
            .retrieve()
            .bodyToMono(JsonNode.class)
            .timeout(LOOKUP_TIMEOUT)
            .flatMap(this::snapshotUri)
            .flatMap(htmlFetcher::fetch);
    }

    Mono<URI> snapshotUri(JsonNode response) {
        var closest = response.path("archived_snapshots").path("closest");
        if (!closest.path("available").asBoolean(false)
            || !"200".equals(closest.path("status").asText())) {
            return Mono.empty();
        }

        var value = closest.path("url").asText("");
        if (value.isBlank()) {
            return Mono.empty();
        }

        try {
            var uri = URI.create(value);
            if (uri.getRawUserInfo() != null
                || !"web.archive.org".equalsIgnoreCase(uri.getHost())) {
                return Mono.empty();
            }
            return Mono.just(new URI(
                "https",
                null,
                "web.archive.org",
                -1,
                uri.getRawPath(),
                uri.getRawQuery(),
                null
            ));
        } catch (IllegalArgumentException | URISyntaxException e) {
            return Mono.empty();
        }
    }
}
