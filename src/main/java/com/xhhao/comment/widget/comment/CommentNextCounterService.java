package com.xhhao.comment.widget.comment;

import java.util.Collection;
import java.util.LinkedHashSet;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import run.halo.app.core.extension.Counter;
import run.halo.app.extension.ReactiveExtensionClient;

@Service
@RequiredArgsConstructor
class CommentNextCounterService {

    static final String CONTENT_GROUP = "content.halo.run";

    static final String COMMENTS_PLURAL = "comments";

    static final String REPLIES_PLURAL = "replies";

    private final ReactiveExtensionClient client;

    Mono<Map<String, Integer>> fetchUpvotes(String plural, Collection<String> names) {
        var uniqueNames = new LinkedHashSet<>(names);
        uniqueNames.removeIf(name -> !StringUtils.hasText(name));

        if (uniqueNames.isEmpty()) {
            return Mono.just(Map.of());
        }

        return Flux.fromIterable(uniqueNames)
            .flatMap(name -> fetchUpvote(plural, name)
                .map(upvote -> Map.entry(name, upvote)), 8)
            .collectMap(Map.Entry::getKey, Map.Entry::getValue);
    }

    private Mono<Integer> fetchUpvote(String plural, String name) {
        return client.fetch(Counter.class, counterNameOf(plural, name))
            .map(this::upvoteOrZero)
            .defaultIfEmpty(0);
    }

    private int upvoteOrZero(Counter counter) {
        return counter.getUpvote() == null ? 0 : counter.getUpvote();
    }

    private String counterNameOf(String plural, String name) {
        return plural + "." + CONTENT_GROUP + "/" + name;
    }
}
