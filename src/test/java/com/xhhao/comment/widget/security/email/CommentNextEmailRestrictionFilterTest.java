package com.xhhao.comment.widget.security.email;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.xhhao.comment.widget.SettingConfigGetter;
import com.xhhao.comment.widget.comment.CommentNextRequestBody;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.core.io.buffer.DataBufferUtils;
import org.springframework.core.io.buffer.DefaultDataBufferFactory;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.mock.http.server.reactive.MockServerHttpRequest;
import org.springframework.mock.web.server.MockServerWebExchange;
import org.springframework.security.authentication.AnonymousAuthenticationToken;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.web.server.ResponseStatusException;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import run.halo.app.core.user.service.RoleService;
import run.halo.app.extension.ReactiveExtensionClient;

class CommentNextEmailRestrictionFilterTest {

    private final SettingConfigGetter settings = mock(SettingConfigGetter.class);
    private final CommentNextEmailRestrictionConfig config = new CommentNextEmailRestrictionConfig();
    private final CommentNextEmailRestrictionFilter filter = new CommentNextEmailRestrictionFilter(settings,
        new CommentNextEmailRestrictionService(mock(ReactiveExtensionClient.class), mock(RoleService.class), settings));

    @BeforeEach
    void defaults() {
        config.setProtectAdminEmails(false);
        when(settings.getEmailRestrictionConfig()).thenReturn(Mono.just(config));
    }

    @ParameterizedTest
    @ValueSource(strings = {"/apis/api.halo.run/v1alpha1/comments", "/apis/api.halo.run/v1alpha1/comments/parent/reply"})
    void rejectsBothSubmissionPathsBeforeTheEndpointCanCreateAnything(String path) {
        var exchange = MockServerWebExchange.from(MockServerHttpRequest.post(path)
            .contentType(MediaType.APPLICATION_JSON).body("{\"owner\":{\"email\":\"TEST@example.com\"}}"));
        var invoked = new AtomicBoolean();
        assertThatThrownBy(() -> filter.filter(exchange, ignored -> {
            invoked.set(true);
            return Mono.empty();
        }).block()).isInstanceOfSatisfying(ResponseStatusException.class, error ->
            assertThat(error.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST));
        assertThat(invoked).isFalse();
    }

    @Test
    void preservesUnicodeBodyAndCorrectLengthForDownstreamFiltersAndEndpoint() {
        var body = "{\"content\":\"中文评论\",\"owner\":{\"email\":\"123456789@qq.com\"}}";
        var bytes = body.getBytes(StandardCharsets.UTF_8);
        var buffers = Flux.just(
            DefaultDataBufferFactory.sharedInstance.wrap(java.util.Arrays.copyOfRange(bytes, 0, 15)),
            DefaultDataBufferFactory.sharedInstance.wrap(java.util.Arrays.copyOfRange(bytes, 15, bytes.length)));
        var exchange = MockServerWebExchange.from(MockServerHttpRequest
            .post("/apis/api.halo.run/v1alpha1/comments").contentType(MediaType.APPLICATION_JSON)
            .header(HttpHeaders.TRANSFER_ENCODING, "chunked").body(buffers));
        var captured = new AtomicReference<String>();
        filter.filter(exchange, replayed -> {
            assertThat(replayed.getRequest().getHeaders().getContentLength()).isEqualTo(bytes.length);
            assertThat(replayed.getRequest().getHeaders().getFirst(HttpHeaders.TRANSFER_ENCODING)).isNull();
            return DataBufferUtils.join(replayed.getRequest().getBody()).doOnNext(buffer -> {
                try {
                    captured.set(buffer.toString(StandardCharsets.UTF_8));
                } finally {
                    DataBufferUtils.release(buffer);
                }
            }).then();
        }).block();
        assertThat(captured.get()).isEqualTo(body);
    }

    @Test
    void authenticatedAdministratorUsesTheLoginIdentityAndBypassesAnonymousChecks() {
        var exchange = MockServerWebExchange.builder(MockServerHttpRequest
            .post("/apis/api.halo.run/v1alpha1/comments").body("{\"owner\":{\"email\":\"test@example.com\"}}"))
            .principal(new UsernamePasswordAuthenticationToken("admin", "", List.of(new SimpleGrantedAuthority("super-role"))))
            .build();
        var invoked = new AtomicBoolean();
        filter.filter(exchange, ignored -> { invoked.set(true); return Mono.empty(); }).block();
        assertThat(invoked).isTrue();
        verifyNoInteractions(settings);
    }

    @Test
    void anonymousAuthenticationCannotBypassTheRestrictionByClaimingAnAdministratorName() {
        var exchange = MockServerWebExchange.builder(MockServerHttpRequest
            .post("/apis/api.halo.run/v1alpha1/comments").body("{\"owner\":{\"email\":\"test@example.com\"}}"))
            .principal(new AnonymousAuthenticationToken("key", "admin",
                List.of(new SimpleGrantedAuthority("ROLE_ANONYMOUS")))).build();
        assertThatThrownBy(() -> filter.filter(exchange, ignored -> Mono.empty()).block())
            .isInstanceOf(ResponseStatusException.class);
    }

    @Test
    void disablingTheFeatureDoesNotReadTheRequestBody() {
        config.setEnabled(false);
        var subscribed = new AtomicBoolean();
        var exchange = MockServerWebExchange.from(MockServerHttpRequest
            .post("/apis/api.halo.run/v1alpha1/comments").body(Flux.defer(() -> {
                subscribed.set(true);
                return Flux.empty();
            })));
        filter.filter(exchange, ignored -> Mono.empty()).block();
        assertThat(subscribed).isFalse();
    }

    @ParameterizedTest
    @ValueSource(strings = {"", "{", "[]", "{}", "{\"owner\":{\"email\":123}}"})
    void leavesMalformedOrIncompleteRequestsToHaloValidation(String body) {
        var exchange = MockServerWebExchange.from(MockServerHttpRequest
            .post("/apis/api.halo.run/v1alpha1/comments").body(body));
        var invoked = new AtomicBoolean();
        filter.filter(exchange, ignored -> { invoked.set(true); return Mono.empty(); }).block();
        assertThat(invoked).isTrue();
    }

    @Test
    void rejectsOversizedChunkedRequestsBeforeCallingTheEndpoint() {
        var size = CommentNextRequestBody.MAX_BYTES / 2 + 1;
        var exchange = MockServerWebExchange.from(MockServerHttpRequest
            .post("/apis/api.halo.run/v1alpha1/comments").body(Flux.just(
                DefaultDataBufferFactory.sharedInstance.wrap(new byte[size]),
                DefaultDataBufferFactory.sharedInstance.wrap(new byte[size]))));
        var invoked = new AtomicBoolean();
        assertThatThrownBy(() -> filter.filter(exchange, ignored -> {
            invoked.set(true); return Mono.empty();
        }).block()).isInstanceOfSatisfying(ResponseStatusException.class, error ->
            assertThat(error.getStatusCode()).isEqualTo(HttpStatus.PAYLOAD_TOO_LARGE));
        assertThat(invoked).isFalse();
    }

    @Test
    void ignoresReadRequestsAndOtherEndpoints() {
        for (var request : List.of(MockServerHttpRequest.get("/apis/api.halo.run/v1alpha1/comments"),
            MockServerHttpRequest.post("/other-api"))) {
            filter.filter(MockServerWebExchange.from(request), ignored -> Mono.empty()).block();
        }
        verifyNoInteractions(settings);
    }
}
