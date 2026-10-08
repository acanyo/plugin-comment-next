package com.xhhao.comment.widget.iplocation;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.xhhao.comment.utils.JsonUtils;
import com.xhhao.comment.widget.network.CommentNextOutboundUriPolicy;
import java.net.InetSocketAddress;
import java.net.URI;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpHeaders;
import org.springframework.http.client.reactive.ReactorClientHttpConnector;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Mono;
import reactor.netty.http.client.HttpClient;

@Component
@RequiredArgsConstructor
class CommentNextIpLocationClient {

    private static final int MAX_RESPONSE_BYTES = 16 * 1024;

    private static final Charset FALLBACK_CHARSET = Charset.forName("GB18030");

    private static final ObjectMapper OBJECT_MAPPER = JsonUtils.createObjectMapper();

    private final CommentNextOutboundUriPolicy outboundUriPolicy;

    private final WebClient webClient = WebClient.builder()
        .clientConnector(new ReactorClientHttpConnector(HttpClient.create()
            .resolvedAddressesSelector((config, addresses) -> addresses.stream()
                .filter(address -> address instanceof InetSocketAddress inetAddress
                    && CommentNextOutboundUriPolicy.isPublicAddress(inetAddress.getAddress()))
                .toList())))
        .codecs(configurer -> configurer.defaultCodecs().maxInMemorySize(MAX_RESPONSE_BYTES))
        .defaultHeader(HttpHeaders.USER_AGENT, "Halo-Comment-Next-IP-Location/1.0")
        .defaultHeader(HttpHeaders.ACCEPT, "*/*")
        .build();

    Mono<String> fetchLocation(String ipAddress, String apiUrlTemplate, Duration timeout) {
        return Mono.fromCallable(() -> locationUri(ipAddress, apiUrlTemplate))
            .flatMap(outboundUriPolicy::validate)
            .flatMap(uri -> webClient.get()
                .uri(uri)
                .exchangeToMono(response -> {
                    if (!response.statusCode().is2xxSuccessful()) {
                        return response.releaseBody()
                            .then(Mono.error(new IllegalStateException(
                                "IP 属地接口返回非成功状态：" + response.statusCode().value()
                            )));
                    }
                    var contentType = response.headers().asHttpHeaders().getContentType();
                    Charset charset = contentType == null ? null : contentType.getCharset();
                    return response.bodyToMono(byte[].class)
                        .map(bytes -> locationFromBody(bytes, charset));
                }))
            .filter(location -> !location.isBlank())
            .timeout(timeout);
    }

    /**
     * 属地接口的响应格式并不统一：pconline 返回 text/html + GBK，mir6/zxinc 返回 text/json。
     * 因此先按响应声明的编码解码，再解析 JSON，避免中文乱码。
     */
    static String locationFromBody(byte[] bytes, Charset charset) {
        if (bytes == null || bytes.length == 0) {
            return "";
        }
        return CommentNextIpLocationResponseParser.location(parseJson(decode(bytes, charset)));
    }

    private static String decode(byte[] bytes, Charset charset) {
        if (charset != null) {
            return new String(bytes, charset);
        }

        var utf8 = new String(bytes, StandardCharsets.UTF_8);
        if (utf8.indexOf('\uFFFD') < 0) {
            return utf8;
        }
        return new String(bytes, FALLBACK_CHARSET);
    }

    private static JsonNode parseJson(String body) {
        if (body == null) {
            return null;
        }

        var trimmed = body.strip();
        if (!trimmed.isEmpty() && trimmed.charAt(0) == '\uFEFF') {
            trimmed = trimmed.substring(1).strip();
        }
        if (trimmed.isEmpty() || trimmed.charAt(0) != '{') {
            return null;
        }

        try {
            return OBJECT_MAPPER.readTree(trimmed);
        } catch (Exception e) {
            return null;
        }
    }

    static URI locationUri(String ipAddress, String apiUrlTemplate) {
        if (apiUrlTemplate == null
            || apiUrlTemplate.isBlank()
            || !apiUrlTemplate.contains("{ip}")) {
            throw new IllegalArgumentException("IP 属地接口 URL 模板必须包含 {ip}");
        }
        return URI.create(apiUrlTemplate.strip().replace("{ip}", ipAddress));
    }
}
