package com.xhhao.comment.widget.ai.website;

import com.xhhao.comment.widget.SettingConfigGetter;
import com.xhhao.comment.widget.ai.ConditionalOnHaloAiFoundation;
import java.net.URI;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;
import reactor.core.publisher.Mono;

@Slf4j
@Service
@ConditionalOnHaloAiFoundation
@RequiredArgsConstructor
public class CommentNextWebsiteMetadataService {
    private static final String LIVE_SOURCE = "实时网页";

    private static final String WAYBACK_SOURCE = "Wayback 快照";

    private final CommentNextWebsiteUriPolicy uriPolicy;

    private final CommentNextSafeHtmlFetcher htmlFetcher;

    private final CommentNextWaybackClient waybackClient;

    private final CommentNextWebsiteMetadataParser metadataParser;

    private final CommentNextWebsiteMetadataCache cache;

    public Mono<CommentNextWebsiteMetadata> inspect(SettingConfigGetter.AiConfig config,
                                                    String website) {
        if (!config.isReviewAuthorWebsiteEnabled() || !StringUtils.hasText(website)) {
            return Mono.just(CommentNextWebsiteMetadata.unavailable(""));
        }

        final URI normalized;
        try {
            normalized = uriPolicy.normalizeWebsiteRoot(website);
        } catch (IllegalArgumentException e) {
            log.debug("Skipped invalid comment author website: {}", e.getMessage());
            return Mono.just(CommentNextWebsiteMetadata.unavailable(website.strip()));
        }

        var cacheKey = normalized.toASCIIString();
        var cached = cache.get(cacheKey);
        if (cached.isPresent()) {
            return Mono.just(cached.get());
        }

        return uriPolicy.validateOutbound(normalized)
            .flatMap(validated -> inspectLive(validated)
                .onErrorResume(error -> inspectFallback(config, validated, error)))
            .onErrorResume(error -> {
                log.debug(
                    "Blocked comment author website before fetching. host={}, reason={}",
                    normalized.getHost(),
                    error.getMessage()
                );
                return Mono.empty();
            })
            .defaultIfEmpty(CommentNextWebsiteMetadata.unavailable(cacheKey))
            .doOnNext(cache::put);
    }

    private Mono<CommentNextWebsiteMetadata> inspectLive(URI website) {
        return htmlFetcher.fetch(website)
            .map(page -> metadataParser.parse(website, page, LIVE_SOURCE))
            .filter(CommentNextWebsiteMetadata::available)
            .switchIfEmpty(Mono.error(new IllegalStateException("网页没有可用的标题或描述")));
    }

    private Mono<CommentNextWebsiteMetadata> inspectFallback(SettingConfigGetter.AiConfig config,
                                                             URI website,
                                                             Throwable liveError) {
        log.debug(
            "Failed to read comment author website metadata. host={}, reason={}",
            website.getHost(),
            liveError.getMessage()
        );
        if (!config.isReviewWaybackFallbackEnabled()) {
            return Mono.empty();
        }

        return waybackClient.fetchLatest(website)
            .map(page -> metadataParser.parse(website, page, WAYBACK_SOURCE))
            .filter(CommentNextWebsiteMetadata::available)
            .doOnError(error -> log.debug(
                "Failed to read Wayback website metadata. host={}, reason={}",
                website.getHost(),
                error.getMessage()
            ))
            .onErrorResume(error -> Mono.empty());
    }
}
