package com.xhhao.comment.widget.qq;

import com.xhhao.comment.widget.SettingConfigGetter;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Mono;

@Slf4j
@Service
@RequiredArgsConstructor
class CommentNextQqProfileService {

    private final CommentNextQqProfileClient client;

    private final CommentNextQqProfileCache cache;

    Mono<CommentNextQqProfile> lookup(String email, SettingConfigGetter.QqProfileConfig config) {
        var address = CommentNextQqAddress.parse(email).orElse(null);
        if (address == null || config == null || !config.isEnabled()) {
            return Mono.just(CommentNextQqProfile.empty());
        }

        var apiUrlTemplate = config.getApiUrlTemplate();
        var cachedNickname = cache.get(apiUrlTemplate, address.number());
        if (cachedNickname.isPresent()) {
            return Mono.just(new CommentNextQqProfile(cachedNickname.get()));
        }

        return client.fetchNickname(address.number(), apiUrlTemplate)
            .doOnNext(nickname -> cache.put(apiUrlTemplate, address.number(), nickname))
            .map(CommentNextQqProfile::new)
            .defaultIfEmpty(CommentNextQqProfile.empty())
            .onErrorResume(error -> {
                log.debug("Failed to fetch QQ profile. qq={}", address.number(), error);
                return Mono.just(CommentNextQqProfile.empty());
            });
    }
}
