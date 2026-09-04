package com.xhhao.comment.widget.interactionplus;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.timxs.interactionplus.api.PublicIdentity;
import com.timxs.interactionplus.api.PublicIdentityQueryApi;
import com.xhhao.comment.utils.JsonUtils;
import com.xhhao.comment.widget.SettingConfigGetter;
import java.util.concurrent.ConcurrentHashMap;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import reactor.core.publisher.Mono;
import run.halo.app.plugin.extensionpoint.ExtensionGetter;

/**
 * 全插件唯一引用 interaction-plus 类的组件。
 * hip-* 的 data 模式需要完整 PublicIdentity（含 userName）才能拼用户页跳转。
 */
@Component
@ConditionalOnInteractionPlus
@RequiredArgsConstructor
public class AuthorIdentityClient implements AuthorIdentityProvider {

    private final ExtensionGetter extensionGetter;

    private final SettingConfigGetter settingConfigGetter;

    private final ObjectMapper objectMapper = JsonUtils.createObjectMapper();

    @Override
    public Mono<AuthorIdentityLoader> newLoader() {
        return settingConfigGetter.getInteractionPlusConfig()
            .defaultIfEmpty(SettingConfigGetter.InteractionPlusConfig.empty())
            .flatMap(config -> {
                if (!config.isEnabled()) {
                    return Mono.just(AuthorIdentityLoader.disabled());
                }
                return extensionGetter.getEnabledExtension(PublicIdentityQueryApi.class)
                    .map(this::memoizedLoader)
                    .defaultIfEmpty(AuthorIdentityLoader.disabled())
                    .onErrorReturn(AuthorIdentityLoader.disabled());
            })
            .onErrorReturn(AuthorIdentityLoader.disabled());
    }

    private AuthorIdentityLoader memoizedLoader(PublicIdentityQueryApi queryApi) {
        var cache = new ConcurrentHashMap<String, Mono<ObjectNode>>();
        return userName -> {
            if (!StringUtils.hasText(userName)) {
                return Mono.empty();
            }
            return cache.computeIfAbsent(userName, key -> queryApi.getIdentity(key)
                .map(this::toIdentityNode)
                .onErrorResume(error -> Mono.empty())
                .cache());
        };
    }

    private ObjectNode toIdentityNode(PublicIdentity identity) {
        return objectMapper.valueToTree(identity);
    }
}
