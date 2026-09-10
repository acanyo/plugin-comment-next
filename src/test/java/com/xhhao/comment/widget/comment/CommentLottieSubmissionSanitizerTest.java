package com.xhhao.comment.widget.comment;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.xhhao.comment.widget.SettingConfigGetter;
import java.net.URI;
import java.util.List;
import org.jsoup.Jsoup;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.mock.http.server.reactive.MockServerHttpRequest;
import org.springframework.web.server.ResponseStatusException;
import run.halo.app.infra.ExternalUrlSupplier;

class CommentLottieSubmissionSanitizerTest {

    private CommentLottieSubmissionSanitizer sanitizer;
    private MockServerHttpRequest request;

    @BeforeEach
    void setUp() throws Exception {
        var externalUrlSupplier = mock(ExternalUrlSupplier.class);
        when(externalUrlSupplier.getURL(any()))
            .thenReturn(URI.create("https://blog.example.com").toURL());
        sanitizer = new CommentLottieSubmissionSanitizer(
            new CommentLottieSourcePolicy(externalUrlSupplier)
        );
        request = MockServerHttpRequest
            .post("https://blog.example.com/apis/api.halo.run/v1alpha1/comments")
            .build();
    }

    @Test
    void validatesSourceAndScalesDimensionsInOnePass() {
        var config = emoteConfig(List.of("cdn.example.com"));
        var result = sanitizer.sanitize(
            "<p>Hi</p><img src=\"https://cdn.example.com/a.tgs\" "
                + "title=\"comment-next-lottie:format=tgs\" width=\"1024\" height=\"512\">",
            config,
            request
        );

        var image = Jsoup.parseBodyFragment(result).body().selectFirst("img");
        assertThat(image).isNotNull();
        assertThat(image.attr("width")).isEqualTo("512");
        assertThat(image.attr("height")).isEqualTo("256");
    }

    @Test
    void rejectsUnconfiguredHostEvenWithTrustedLookingMetadata() {
        var config = emoteConfig(List.of());

        assertThatThrownBy(() -> sanitizer.sanitize(
            "<img src=\"https://evil.example/a.lottie\" "
                + "title=\"comment-next-lottie:format=lottie\">",
            config,
            request
        )).isInstanceOfSatisfying(ResponseStatusException.class, error ->
            assertThat(error.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST)
        );
    }

    @Test
    void leavesOrdinaryImagesAndInBoundsLottieUnchanged() {
        var html = "<img src=\"https://images.example.com/a.png\" width=\"1024\">"
            + "<img src=\"/assets/a.json\" width=\"512\" height=\"512\">";

        assertThat(sanitizer.sanitize(html, emoteConfig(List.of()), request))
            .isEqualTo(html);
    }

    private SettingConfigGetter.EmoteConfig emoteConfig(List<String> hosts) {
        var config = SettingConfigGetter.EmoteConfig.empty();
        config.setAllowedHosts(hosts.stream().map(host -> {
            var allowedHost = new SettingConfigGetter.AllowedLottieHostConfig();
            allowedHost.setHost(host);
            return allowedHost;
        }).toList());
        return config;
    }
}
