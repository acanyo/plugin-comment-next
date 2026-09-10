package com.xhhao.comment.widget.comment;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.net.URI;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.mock.http.server.reactive.MockServerHttpRequest;
import run.halo.app.infra.ExternalUrlSupplier;

class CommentLottieSourcePolicyTest {

    private CommentLottieSourcePolicy policy;
    private MockServerHttpRequest request;

    @BeforeEach
    void setUp() throws Exception {
        var externalUrlSupplier = mock(ExternalUrlSupplier.class);
        when(externalUrlSupplier.getURL(any()))
            .thenReturn(URI.create("https://blog.example.com").toURL());
        policy = new CommentLottieSourcePolicy(externalUrlSupplier);
        request = MockServerHttpRequest
            .post("http://internal:8090/apis/api.halo.run/v1alpha1/comments")
            .build();
    }

    @Test
    void allowsSameOriginContentAndSupportedFormats() {
        assertThat(policy.isAllowed(
            "/apis/api.lottie.halo.run/v1alpha1/animations/wave/content",
            request,
            List.of()
        )).isTrue();
        assertThat(policy.isAllowed(
            "https://blog.example.com/assets/wave.json",
            request,
            List.of()
        )).isTrue();
        assertThat(policy.isAllowed(
            "https://blog.example.com/assets/wave.tgs",
            request,
            List.of()
        )).isTrue();
        assertThat(policy.isAllowed(
            "https://blog.example.com/assets/wave.lottie",
            request,
            List.of()
        )).isTrue();
    }

    @Test
    void allowsOnlyExactConfiguredExternalHttpsHostAndPort() {
        var allowedHosts = List.of("CDN.Example.com.:443", "media.example.com:8443");

        assertThat(policy.isAllowed(
            "https://cdn.example.com/wave.lottie",
            request,
            allowedHosts
        )).isTrue();
        assertThat(policy.isAllowed(
            "https://media.example.com:8443/wave.json",
            request,
            allowedHosts
        )).isTrue();
        assertThat(policy.isAllowed(
            "https://sub.cdn.example.com/wave.lottie",
            request,
            allowedHosts
        )).isFalse();
        assertThat(policy.isAllowed(
            "https://cdn.example.com.evil.test/wave.lottie",
            request,
            allowedHosts
        )).isFalse();
        assertThat(policy.isAllowed(
            "https://cdn.example.com:8443/wave.lottie",
            request,
            allowedHosts
        )).isFalse();
    }

    @Test
    void rejectsUnsafeOrUnsupportedExternalSources() {
        var allowedHosts = List.of("cdn.example.com");

        assertThat(policy.isAllowed(
            "http://cdn.example.com/wave.lottie",
            request,
            allowedHosts
        )).isFalse();
        assertThat(policy.isAllowed(
            "https://user@cdn.example.com/wave.lottie",
            request,
            allowedHosts
        )).isFalse();
        assertThat(policy.isAllowed(
            "https:\\cdn.example.com\\wave.lottie",
            request,
            allowedHosts
        )).isFalse();
        assertThat(policy.isAllowed(
            "https://cdn.example.com/not-animation.png",
            request,
            allowedHosts
        )).isFalse();
        assertThat(policy.isAllowed(
            "https://evil.example/wave.lottie?comment-next-lottie=1",
            request,
            allowedHosts
        )).isFalse();
    }
}
