package com.xhhao.comment.widget.qq;

import com.fasterxml.jackson.databind.JsonNode;
import com.xhhao.comment.widget.network.CommentNextOutboundUriPolicy;
import java.net.InetSocketAddress;
import java.net.URI;
import java.time.Duration;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.client.reactive.ReactorClientHttpConnector;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Mono;
import reactor.netty.http.client.HttpClient;

@Component
@RequiredArgsConstructor
class CommentNextQqProfileClient {

    private static final int MAX_RESPONSE_BYTES = 16 * 1024;

    private static final Duration FETCH_TIMEOUT = Duration.ofSeconds(5);

    private final CommentNextOutboundUriPolicy outboundUriPolicy;

    private final WebClient webClient = WebClient.builder()
        .clientConnector(new ReactorClientHttpConnector(HttpClient.create()
            .resolvedAddressesSelector((config, addresses) -> addresses.stream()
                .filter(address -> address instanceof InetSocketAddress inetAddress
                    && CommentNextOutboundUriPolicy.isPublicAddress(inetAddress.getAddress()))
                .toList())))
        .codecs(configurer -> configurer.defaultCodecs().maxInMemorySize(MAX_RESPONSE_BYTES))
        .defaultHeader(HttpHeaders.USER_AGENT, "Halo-Comment-Next-QQ-Profile/1.0")
        .defaultHeader(HttpHeaders.ACCEPT, MediaType.APPLICATION_JSON_VALUE)
        .build();

    Mono<String> fetchNickname(String qqNumber, String apiUrlTemplate) {
        return Mono.fromCallable(() -> profileUri(qqNumber, apiUrlTemplate))
            .flatMap(outboundUriPolicy::validate)
            .flatMap(uri -> webClient.get()
                .uri(uri)
                .exchangeToMono(response -> {
                    if (!response.statusCode().is2xxSuccessful()) {
                        return response.releaseBody()
                            .then(Mono.error(new IllegalStateException(
                                "QQ 昵称接口返回非成功状态：" + response.statusCode().value()
                            )));
                    }
                    return response.bodyToMono(JsonNode.class)
                        .map(CommentNextQqProfileResponseParser::nickname);
                }))
            .filter(nickname -> !nickname.isBlank())
            .timeout(FETCH_TIMEOUT);
    }

    static URI profileUri(String qqNumber, String apiUrlTemplate) {
        if (apiUrlTemplate == null
            || apiUrlTemplate.isBlank()
            || !apiUrlTemplate.contains("{qq}")) {
            throw new IllegalArgumentException("QQ 昵称接口 URL 模板必须包含 {qq}");
        }
        return URI.create(apiUrlTemplate.strip().replace("{qq}", qqNumber));
    }
}
