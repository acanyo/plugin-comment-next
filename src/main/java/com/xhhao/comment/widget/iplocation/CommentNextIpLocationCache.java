package com.xhhao.comment.widget.iplocation;

import com.google.common.cache.Cache;
import com.google.common.cache.CacheBuilder;
import java.util.Optional;
import org.springframework.stereotype.Component;

/**
 * 属地解析结果缓存，同时缓存解析失败的空结果（负缓存）以避免重复请求接口。
 */
@Component
class CommentNextIpLocationCache {

    private static final int MAX_ENTRIES = 5_000;

    private static final long MILLIS_PER_MINUTE = 60_000L;

    private final Cache<String, Entry> entries = CacheBuilder.newBuilder()
        .maximumSize(MAX_ENTRIES)
        .build();

    Optional<String> get(String key) {
        var entry = entries.getIfPresent(key);
        if (entry == null) {
            return Optional.empty();
        }
        if (entry.expiresAtMillis() <= System.currentTimeMillis()) {
            entries.asMap().remove(key, entry);
            return Optional.empty();
        }
        return Optional.of(entry.location());
    }

    void put(String key, String location, int ttlMinutes) {
        var ttlMillis = Math.max(1, ttlMinutes) * MILLIS_PER_MINUTE;
        entries.put(
            key,
            new Entry(
                location == null ? "" : location,
                System.currentTimeMillis() + ttlMillis
            )
        );
    }

    void clearMisses() {
        entries.asMap().entrySet().removeIf(entry -> entry.getValue().location().isBlank());
    }

    private record Entry(String location, long expiresAtMillis) {
    }
}
