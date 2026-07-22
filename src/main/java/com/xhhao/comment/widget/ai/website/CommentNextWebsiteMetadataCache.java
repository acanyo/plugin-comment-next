package com.xhhao.comment.widget.ai.website;

import com.xhhao.comment.widget.ai.ConditionalOnHaloAiFoundation;
import java.time.Duration;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;
import org.springframework.stereotype.Component;

@Component
@ConditionalOnHaloAiFoundation
class CommentNextWebsiteMetadataCache {
    private static final int MAX_ENTRIES = 512;

    private static final Duration AVAILABLE_TTL = Duration.ofHours(24);

    private static final Duration UNAVAILABLE_TTL = Duration.ofMinutes(30);

    private final Map<String, CacheEntry> entries = new LinkedHashMap<>(16, 0.75F, true);

    synchronized Optional<CommentNextWebsiteMetadata> get(String website) {
        var entry = entries.get(website);
        if (entry == null) {
            return Optional.empty();
        }
        if (entry.expiresAt().isBefore(Instant.now())) {
            entries.remove(website);
            return Optional.empty();
        }
        return Optional.of(entry.metadata());
    }

    synchronized void put(CommentNextWebsiteMetadata metadata) {
        var ttl = metadata.available() ? AVAILABLE_TTL : UNAVAILABLE_TTL;
        entries.put(metadata.website(), new CacheEntry(metadata, Instant.now().plus(ttl)));
        while (entries.size() > MAX_ENTRIES) {
            var iterator = entries.keySet().iterator();
            iterator.next();
            iterator.remove();
        }
    }

    private record CacheEntry(CommentNextWebsiteMetadata metadata, Instant expiresAt) {
    }
}
