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

    Optional<String> get(String apiUrlTemplate, String ipAddress) {
        var key = key(apiUrlTemplate, ipAddress);
        var entry = entries.getIfPresent(key);
        if (entry == null) {
            return Optional.empty();
        }
        if (entry.expiresAtMillis() <= System.currentTimeMillis()) {
            entries.invalidate(key);
            return Optional.empty();
        }
        return Optional.of(entry.location());
    }

    void put(String apiUrlTemplate, String ipAddress, String location, int ttlMinutes) {
        var ttlMillis = Math.max(1, ttlMinutes) * MILLIS_PER_MINUTE;
        entries.put(
            key(apiUrlTemplate, ipAddress),
            new Entry(
                location == null ? "" : location,
                System.currentTimeMillis() + ttlMillis
            )
        );
    }

    private String key(String apiUrlTemplate, String ipAddress) {
        return String.valueOf(apiUrlTemplate) + '\0' + ipAddress;
    }

    private record Entry(String location, long expiresAtMillis) {
    }
}
