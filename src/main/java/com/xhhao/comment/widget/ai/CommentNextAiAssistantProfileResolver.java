package com.xhhao.comment.widget.ai;

import com.xhhao.comment.widget.SettingConfigGetter;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;
import reactor.core.publisher.Mono;
import run.halo.app.core.extension.User;
import run.halo.app.extension.ReactiveExtensionClient;

@Service
@RequiredArgsConstructor
public class CommentNextAiAssistantProfileResolver {

    private final ReactiveExtensionClient client;

    public Mono<CommentNextAiAssistantProfile> resolve(SettingConfigGetter.AiConfig config) {
        return resolve(config.getAssistantUserName(), config.getAssistantName());
    }

    public Mono<CommentNextAiAssistantProfile> resolve(String username, String fallbackDisplayName) {
        var fallback = normalizedDisplayName(fallbackDisplayName);
        var normalizedUsername = normalizedUsername(username);

        if (!StringUtils.hasText(normalizedUsername)) {
            return Mono.just(new CommentNextAiAssistantProfile(null, fallback));
        }

        return client.fetch(User.class, normalizedUsername)
            .map(user -> toProfile(user, fallback))
            .defaultIfEmpty(new CommentNextAiAssistantProfile(normalizedUsername, fallback))
            .onErrorReturn(new CommentNextAiAssistantProfile(normalizedUsername, fallback));
    }

    private CommentNextAiAssistantProfile toProfile(User user, String fallbackDisplayName) {
        var metadata = user.getMetadata();
        var spec = user.getSpec();
        var username = metadata == null ? null : metadata.getName();
        var displayName = spec == null ? null : spec.getDisplayName();
        return new CommentNextAiAssistantProfile(
            normalizedUsername(username),
            normalizedDisplayName(displayName, fallbackDisplayName)
        );
    }

    private String normalizedDisplayName(String value) {
        return normalizedDisplayName(value, SettingConfigGetter.AiConfig.DEFAULT_ASSISTANT_NAME);
    }

    private String normalizedDisplayName(String value, String fallback) {
        return StringUtils.hasText(value) ? value.strip() : fallback;
    }

    private String normalizedUsername(String value) {
        return StringUtils.hasText(value) ? value.strip() : null;
    }
}
