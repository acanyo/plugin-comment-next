package com.xhhao.comment.widget.captcha;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.xhhao.comment.widget.SettingConfigGetter;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import org.springframework.web.reactive.function.client.WebClient;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

@Component
@Slf4j
public class CapCaptchaProviderVerifier implements CaptchaProviderVerifier {

    private static final Duration VERIFY_TIMEOUT = Duration.ofSeconds(8);

    private final WebClient webClient = WebClient.builder().build();

    @Override
    public boolean supports(CaptchaType type) {
        return type == CaptchaType.CAP;
    }

    @Override
    public Mono<Boolean> verify(String token, SettingConfigGetter.CaptchaConfig captchaConfig,
                                ServerWebExchange exchange) {
        var config = captchaConfig.getCap();
        if (!StringUtils.hasText(token)
            || config == null
            || !StringUtils.hasText(config.getApiEndpoint())
            || !StringUtils.hasText(config.getSecretKey())) {
            return Mono.just(false);
        }

        return webClient.post()
            .uri(siteVerifyUrl(config.getApiEndpoint()))
            .accept(MediaType.APPLICATION_JSON)
            .contentType(MediaType.APPLICATION_JSON)
            .bodyValue(Map.of(
                "secret", config.getSecretKey().trim(),
                "response", token.trim()
            ))
            .retrieve()
            .bodyToMono(CapSiteVerifyResponse.class)
            .timeout(VERIFY_TIMEOUT)
            .doOnNext(response -> {
                if (!response.success()) {
                    log.warn("Cap captcha validation failed. errors={}", response.errorCodes());
                }
            })
            .map(CapSiteVerifyResponse::success)
            .defaultIfEmpty(false)
            .onErrorResume(error -> {
                log.warn("Failed to verify Cap captcha.", error);
                return Mono.just(false);
            });
    }

    private String siteVerifyUrl(String apiEndpoint) {
        var normalized = apiEndpoint.trim();
        if (!normalized.endsWith("/")) {
            normalized += "/";
        }
        return normalized + "siteverify";
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    record CapSiteVerifyResponse(
        boolean success,
        @JsonProperty("error-codes")
        List<String> errorCodes
    ) {
    }
}
