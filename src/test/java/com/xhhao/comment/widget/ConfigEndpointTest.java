package com.xhhao.comment.widget;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.xhhao.comment.utils.JsonUtils;
import com.xhhao.comment.widget.ai.CommentNextAiAssistantProfileResolver;
import com.xhhao.comment.widget.ai.HaloAiFoundationAvailability;
import com.xhhao.comment.widget.interactionplus.InteractionPlusAvailability;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.test.web.reactive.server.WebTestClient;
import org.springframework.http.codec.json.Jackson2JsonEncoder;
import org.springframework.web.reactive.function.server.HandlerStrategies;
import reactor.core.publisher.Mono;
import run.halo.app.core.extension.User;
import run.halo.app.extension.Metadata;
import run.halo.app.extension.ConfigMap;
import run.halo.app.extension.ReactiveExtensionClient;
import run.halo.app.plugin.PluginContext;

class ConfigEndpointTest {

    @Test
    void publishesAvatarPoolWithoutExposingLegacyPrivateAvatarSettings() {
        var client = mock(ReactiveExtensionClient.class);
        var context = mock(PluginContext.class);
        var aiAvailability = mock(HaloAiFoundationAvailability.class);
        var interactionAvailability = mock(InteractionPlusAvailability.class);
        var configMap = new ConfigMap();
        configMap.setData(Map.of(
            "avatar", """
                {"fallbackAvatars":[{"url":"/upload/avatar.png"}],
                 "apiKey":"private-value","provider":"legacy"}
                """,
            "qqProfile", "{\"apiUrlTemplate\":\"private-value\"}",
            "ai", "{\"assistant\":{\"assistantUserName\":\"bot\"}}"
        ));
        var assistant = new User();
        var metadata = new Metadata();
        metadata.setName("bot");
        assistant.setMetadata(metadata);
        var spec = new User.UserSpec();
        spec.setDisplayName("拾光 bot");
        spec.setAvatar(" /upload/bot.png ");
        assistant.setSpec(spec);
        when(client.fetch(User.class, "bot")).thenReturn(Mono.just(assistant));
        when(context.getConfigMapName()).thenReturn("comment-config");
        when(client.fetch(ConfigMap.class, "comment-config")).thenReturn(Mono.just(configMap));
        when(aiAvailability.isEnabled()).thenReturn(Mono.just(false));
        when(interactionAvailability.isEnabled()).thenReturn(Mono.just(false));
        var endpoint = new ConfigEndpoint(client, context, aiAvailability,
            new CommentNextAiAssistantProfileResolver(client), interactionAvailability);

        // Match Halo's Jackson 2 codec for plugin JsonNode responses.
        var strategies = HandlerStrategies.builder()
            .codecs(codecs -> codecs.defaultCodecs()
                .jackson2JsonEncoder(new Jackson2JsonEncoder(JsonUtils.createObjectMapper())))
            .build();
        WebTestClient.bindToRouterFunction(endpoint.endpoint())
            .handlerStrategies(strategies).build()
            .get().uri("/config").exchange()
            .expectStatus().isOk()
            .expectBody()
            .jsonPath("$.avatar.fallbackAvatars[0].url").isEqualTo("/upload/avatar.png")
            .jsonPath("$.avatar.apiKey").doesNotExist()
            .jsonPath("$.avatar.provider").doesNotExist()
            .jsonPath("$.qqProfile").doesNotExist()
            .jsonPath("$.ai.assistantDisplayName").isEqualTo("拾光 bot")
            .jsonPath("$.ai.assistantMentionName").isEqualTo("@拾光 bot")
            .jsonPath("$.ai.assistantAvatar").isEqualTo("/upload/bot.png");
    }
}
