package com.xhhao.comment.widget.qq;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.xhhao.comment.widget.SettingConfigGetter;
import com.xhhao.comment.widget.security.CommentNextActionGuard;
import io.github.resilience4j.ratelimiter.RateLimiterRegistry;
import org.junit.jupiter.api.Test;
import org.springframework.test.web.reactive.server.WebTestClient;
import reactor.core.publisher.Mono;

class CommentNextQqProfileEndpointTest {

    @Test
    void allowsNicknameLookupWithoutRequestSourceValidation() {
        var config = new SettingConfigGetter.QqProfileConfig()
            .setEnabled(true)
            .setApiUrlTemplate("https://example.com/qq/{qq}");
        var settingConfigGetter = mock(SettingConfigGetter.class);
        when(settingConfigGetter.getQqProfileConfig()).thenReturn(Mono.just(config));

        var profileService = mock(CommentNextQqProfileService.class);
        when(profileService.lookup(anyString(), any()))
            .thenReturn(Mono.just(new CommentNextQqProfile("QQ User")));

        var actionGuard = new CommentNextActionGuard(RateLimiterRegistry.ofDefaults());
        var endpoint = new CommentNextQqProfileEndpoint(
            settingConfigGetter,
            actionGuard,
            profileService
        );

        WebTestClient.bindToRouterFunction(endpoint.endpoint())
            .build()
            .get()
            .uri("/qq-profiles?email=10001@qq.com")
            .header("Origin", "https://theme.example.com")
            .header("Sec-Fetch-Site", "cross-site")
            .exchange()
            .expectStatus().isOk()
            .expectBody()
            .jsonPath("$.nickname").isEqualTo("QQ User");
    }
}
