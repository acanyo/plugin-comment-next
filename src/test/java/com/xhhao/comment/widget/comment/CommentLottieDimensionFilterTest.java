package com.xhhao.comment.widget.comment;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.xhhao.comment.widget.SettingConfigGetter;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.core.io.buffer.DataBuffer;
import org.springframework.core.io.buffer.DataBufferUtils;
import org.springframework.core.io.buffer.DefaultDataBufferFactory;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.mock.http.server.reactive.MockServerHttpRequest;
import org.springframework.mock.web.server.MockServerWebExchange;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.web.server.WebFilterChain;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import run.halo.app.infra.ExternalUrlSupplier;

class CommentLottieDimensionFilterTest {

    private SettingConfigGetter settingConfigGetter;
    private CommentLottieDimensionFilter filter;

    @BeforeEach
    void setUp() throws Exception {
        settingConfigGetter = mock(SettingConfigGetter.class);
        when(settingConfigGetter.getEmoteConfig())
            .thenReturn(Mono.just(SettingConfigGetter.EmoteConfig.empty()));

        var externalUrlSupplier = mock(ExternalUrlSupplier.class);
        when(externalUrlSupplier.getURL(any()))
            .thenReturn(URI.create("https://blog.example.com").toURL());
        var sanitizer = new CommentLottieSubmissionSanitizer(
            new CommentLottieSourcePolicy(externalUrlSupplier)
        );
        filter = new CommentLottieDimensionFilter(settingConfigGetter, sanitizer);
    }

    @Test
    void rewritesContentAndRawFromMultipartUtf8Body() {
        var json = "{\"content\":\"<img src='https://blog.example.com/动画.tgs' "
            + "width='1024' height='512'>\",\"raw\":\"<img "
            + "src='/wave.lottie' width='1024' height='512'>\"}";
        var bytes = json.getBytes(StandardCharsets.UTF_8);
        var unicodeIndex = json.indexOf("动");
        var split = json.substring(0, unicodeIndex).getBytes(StandardCharsets.UTF_8).length + 1;
        var body = Flux.just(
            buffer(bytes, 0, split),
            buffer(bytes, split, bytes.length)
        );
        var request = MockServerHttpRequest
            .post("https://blog.example.com/apis/api.halo.run/v1alpha1/comments")
            .contentType(MediaType.APPLICATION_JSON)
            .body(body);
        var capturedBody = new AtomicReference<String>();
        WebFilterChain chain = exchange -> readBody(exchange.getRequest().getBody())
            .doOnNext(capturedBody::set)
            .then();

        filter.filter(MockServerWebExchange.from(request), chain).block();

        assertThat(capturedBody.get())
            .contains("width=\\\"512\\\"")
            .contains("height=\\\"256\\\"");
    }

    @Test
    void rejectsChunkedBodyAboveLimitBeforeInvokingChain() {
        var halfPlusOne = CommentLottieDimensionFilter.MAX_REQUEST_BODY_BYTES / 2 + 1;
        var chainInvoked = new AtomicBoolean();
        var request = MockServerHttpRequest
            .post("https://blog.example.com/apis/api.halo.run/v1alpha1/comments")
            .body(Flux.just(
                DefaultDataBufferFactory.sharedInstance.wrap(new byte[halfPlusOne]),
                DefaultDataBufferFactory.sharedInstance.wrap(new byte[halfPlusOne])
            ));

        assertThatThrownBy(() -> filter.filter(
            MockServerWebExchange.from(request),
            exchange -> {
                chainInvoked.set(true);
                return Mono.empty();
            }
        ).block()).isInstanceOfSatisfying(ResponseStatusException.class, error ->
            assertThat(error.getStatusCode()).isEqualTo(HttpStatus.PAYLOAD_TOO_LARGE)
        );
        assertThat(chainInvoked).isFalse();
    }

    @Test
    void rejectsDeclaredOversizeWithoutReadingBody() {
        var bodySubscribed = new AtomicBoolean();
        var request = MockServerHttpRequest
            .post("https://blog.example.com/apis/api.halo.run/v1alpha1/comments")
            .header(
                HttpHeaders.CONTENT_LENGTH,
                Integer.toString(CommentLottieDimensionFilter.MAX_REQUEST_BODY_BYTES + 1)
            )
            .body(Flux.defer(() -> {
                bodySubscribed.set(true);
                return Flux.never();
            }));
        var chain = mock(WebFilterChain.class);

        assertThatThrownBy(() -> filter.filter(
            MockServerWebExchange.from(request),
            chain
        ).block()).isInstanceOfSatisfying(ResponseStatusException.class, error ->
            assertThat(error.getStatusCode()).isEqualTo(HttpStatus.PAYLOAD_TOO_LARGE)
        );
        assertThat(bodySubscribed).isFalse();
        verify(chain, never()).filter(any());
    }

    @Test
    void nonMatchingPathDoesNotReadConfigurationOrBody() {
        var bodySubscribed = new AtomicBoolean();
        var request = MockServerHttpRequest
            .post("https://blog.example.com/not-comments")
            .body(Flux.defer(() -> {
                bodySubscribed.set(true);
                return Flux.empty();
            }));

        filter.filter(
            MockServerWebExchange.from(request),
            exchange -> Mono.empty()
        ).block();

        assertThat(bodySubscribed).isFalse();
        verifyNoInteractions(settingConfigGetter);
    }

    private DataBuffer buffer(byte[] bytes, int start, int end) {
        var part = new byte[end - start];
        System.arraycopy(bytes, start, part, 0, part.length);
        return DefaultDataBufferFactory.sharedInstance.wrap(part);
    }

    private Mono<String> readBody(Flux<DataBuffer> body) {
        return DataBufferUtils.join(body)
            .map(buffer -> {
                try {
                    var bytes = new byte[buffer.readableByteCount()];
                    buffer.read(bytes);
                    return new String(bytes, StandardCharsets.UTF_8);
                } finally {
                    DataBufferUtils.release(buffer);
                }
            });
    }
}
