package com.xhhao.comment.widget.captcha;

import com.fasterxml.jackson.annotation.JsonAlias;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.xhhao.comment.utils.JsonUtils;
import com.xhhao.comment.widget.SettingConfigGetter;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.HexFormat;
import java.util.Map;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import org.springframework.web.reactive.function.BodyInserters;
import org.springframework.web.reactive.function.client.WebClient;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;
import lombok.extern.slf4j.Slf4j;

@Component
@Slf4j
public class GeeTestCaptchaProviderVerifier implements CaptchaProviderVerifier {
    private static final String DEFAULT_API_SERVER = "https://gcaptcha4.geetest.com";
    private static final String SUCCESS_RESULT = "success";

    private final WebClient webClient = WebClient.builder().build();
    private final ObjectMapper objectMapper = JsonUtils.createObjectMapper();

    @Override
    public boolean supports(CaptchaType type) {
        return type == CaptchaType.GEETEST;
    }

    @Override
    public Mono<Boolean> verify(String token, SettingConfigGetter.CaptchaConfig captchaConfig,
                                ServerWebExchange exchange) {
        var config = captchaConfig.getGeeTest();
        if (!StringUtils.hasText(token)
            || !StringUtils.hasText(config.getCaptchaId())
            || !StringUtils.hasText(config.getCaptchaKey())) {
            return Mono.just(false);
        }

        return Mono.fromCallable(() -> parseToken(token))
            .flatMap(validateToken -> {
                if (!validateToken.isComplete()) {
                    log.warn("GeeTest captcha token is incomplete.");
                    return Mono.just(false);
                }
                return verifyWithGeeTest(validateToken, config);
            })
            .onErrorResume(error -> {
                log.warn("Failed to verify GeeTest captcha.", error);
                return Mono.just(false);
            });
    }

    private Mono<Boolean> verifyWithGeeTest(GeeTestValidateToken token,
                                            SettingConfigGetter.GeeTestCaptchaConfig config) {
        var signToken = sign(token.lotNumber(), config.getCaptchaKey().trim());
        var url = validateUrl(config);

        return webClient.post()
            .uri(url)
            .accept(MediaType.APPLICATION_JSON, MediaType.valueOf("text/javascript"))
            .contentType(MediaType.APPLICATION_FORM_URLENCODED)
            .body(BodyInserters
                .fromFormData("lot_number", token.lotNumber().trim())
                .with("captcha_output", token.captchaOutput().trim())
                .with("pass_token", token.passToken().trim())
                .with("gen_time", token.genTime().trim())
                .with("sign_token", signToken))
            .retrieve()
            .bodyToMono(String.class)
            .timeout(Duration.ofSeconds(8))
            .map(this::parseValidateResponse)
            .doOnNext(response -> {
                if (!SUCCESS_RESULT.equalsIgnoreCase(response.result())) {
                    log.warn("GeeTest captcha validation failed. result={}, reason={}",
                        response.result(), response.reason());
                }
            })
            .map(response -> SUCCESS_RESULT.equalsIgnoreCase(response.result()))
            .defaultIfEmpty(false);
    }

    private GeeTestValidateToken parseToken(String token) throws Exception {
        return objectMapper.readValue(token, GeeTestValidateToken.class);
    }

    private GeeTestValidateResponse parseValidateResponse(String responseBody) {
        try {
            return objectMapper.readValue(extractJsonBody(responseBody), GeeTestValidateResponse.class);
        } catch (Exception e) {
            throw new IllegalStateException("Failed to parse GeeTest validate response", e);
        }
    }

    private static String extractJsonBody(String responseBody) {
        var body = responseBody == null ? "" : responseBody.trim();
        if (body.startsWith("{")) {
            return body;
        }

        var start = body.indexOf('{');
        var end = body.lastIndexOf('}');
        if (start >= 0 && end > start) {
            return body.substring(start, end + 1);
        }

        return body;
    }

    private String validateUrl(SettingConfigGetter.GeeTestCaptchaConfig config) {
        var apiServer = StringUtils.hasText(config.getApiServer())
            ? config.getApiServer().trim()
            : DEFAULT_API_SERVER;
        var normalizedApiServer = apiServer.endsWith("/")
            ? apiServer.substring(0, apiServer.length() - 1)
            : apiServer;
        var captchaId = URLEncoder.encode(config.getCaptchaId().trim(), StandardCharsets.UTF_8);
        return normalizedApiServer + "/validate?captcha_id=" + captchaId;
    }

    private static String sign(String lotNumber, String captchaKey) {
        try {
            var mac = Mac.getInstance("HmacSHA256");
            var secretKey = new SecretKeySpec(
                captchaKey.getBytes(StandardCharsets.UTF_8),
                "HmacSHA256"
            );
            mac.init(secretKey);
            return HexFormat.of().formatHex(mac.doFinal(lotNumber.getBytes(StandardCharsets.UTF_8)));
        } catch (Exception e) {
            throw new IllegalStateException("Failed to sign GeeTest captcha token", e);
        }
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    record GeeTestValidateToken(
        @JsonProperty("lot_number")
        @JsonAlias("lotNumber")
        String lotNumber,
        @JsonProperty("pass_token")
        @JsonAlias("passToken")
        String passToken,
        @JsonProperty("gen_time")
        @JsonAlias("genTime")
        String genTime,
        @JsonProperty("captcha_output")
        @JsonAlias("captchaOutput")
        String captchaOutput
    ) {
        boolean isComplete() {
            return StringUtils.hasText(lotNumber)
                && StringUtils.hasText(passToken)
                && StringUtils.hasText(genTime)
                && StringUtils.hasText(captchaOutput);
        }
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    record GeeTestValidateResponse(
        String result,
        String reason,
        @JsonProperty("captcha_args")
        Map<String, Object> captchaArgs
    ) {
    }
}
