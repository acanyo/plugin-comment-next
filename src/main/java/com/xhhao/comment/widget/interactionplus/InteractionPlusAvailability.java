package com.xhhao.comment.widget.interactionplus;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.util.ClassUtils;
import reactor.core.publisher.Mono;
import run.halo.app.core.extension.Plugin;
import run.halo.app.extension.ReactiveExtensionClient;

@Component
@RequiredArgsConstructor
public class InteractionPlusAvailability {

    public static final String PLUGIN_NAME = "interaction-plus";

    public static final String QUERY_API_CLASS =
        "com.timxs.interactionplus.api.PublicIdentityQueryApi";

    public static final String RUNTIME_SCRIPT =
        "/plugins/interaction-plus/assets/runtime/interaction-plus.runtime.js";

    private final ReactiveExtensionClient client;

    public Mono<Boolean> isEnabled() {
        return client.fetch(Plugin.class, PLUGIN_NAME)
            .map(this::isStarted)
            .defaultIfEmpty(false)
            .onErrorReturn(false);
    }

    public static boolean isClassPresent(ClassLoader classLoader) {
        var targetClassLoader = classLoader == null
            ? InteractionPlusAvailability.class.getClassLoader()
            : classLoader;
        return ClassUtils.isPresent(QUERY_API_CLASS, targetClassLoader);
    }

    private boolean isStarted(Plugin plugin) {
        return plugin.getSpec() != null
            && Boolean.TRUE.equals(plugin.getSpec().getEnabled())
            && plugin.getStatus() != null
            && Plugin.Phase.STARTED.equals(plugin.getStatus().getPhase());
    }
}
