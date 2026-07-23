package com.xhhao.comment.widget.qq;

import com.google.common.cache.Cache;
import com.google.common.cache.CacheBuilder;
import java.util.Optional;
import java.util.concurrent.TimeUnit;
import org.springframework.stereotype.Component;

@Component
class CommentNextQqProfileCache {

    private static final int MAX_ENTRIES = 2_000;

    private final Cache<String, String> nicknames = CacheBuilder.newBuilder()
        .maximumSize(MAX_ENTRIES)
        .expireAfterWrite(6, TimeUnit.HOURS)
        .build();

    Optional<String> get(String apiUrlTemplate, String qqNumber) {
        return Optional.ofNullable(nicknames.getIfPresent(key(apiUrlTemplate, qqNumber)));
    }

    void put(String apiUrlTemplate, String qqNumber, String nickname) {
        nicknames.put(key(apiUrlTemplate, qqNumber), nickname);
    }

    private String key(String apiUrlTemplate, String qqNumber) {
        return String.valueOf(apiUrlTemplate) + '\0' + qqNumber;
    }
}
