package com.xhhao.comment.widget.captcha;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.xhhao.comment.widget.SettingConfigGetter;
import com.xhhao.comment.widget.security.ClientIpUtils;
import java.time.Duration;
import java.util.List;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.StringUtils;
import org.springframework.web.reactive.function.BodyInserters;
import org.springframework.web.reactive.function.client.WebClient;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

/**
 * Verifies Cloudflare Turnstile tokens against the siteverify endpoint.
 *
 * <p>Turnstile tokens are single-use and expire after 300 seconds. The secret key
 * stays on the server; only the site key is published to the browser.</p>
 */
@Component
@Slf4j
public class TurnstileCaptchaProviderVerifier implements CaptchaProviderVerifier {

    private static final String SITE_VERIFY_URL =
        "https://challenges.cloudflare.com/turnstile/v0/siteverify";
    private static final Duration VERIFY_TIMEOUT = Duration.ofSeconds(8);

    private final WebClient webClient = WebClient.builder().build();

    @Override
    public boolean supports(CaptchaType type) {
        return type == CaptchaType.TURNSTILE;
    }

    @Override
    public Mono<Boolean> verify(String token, SettingConfigGetter.CaptchaConfig captchaConfig,
                                ServerWebExchange exchange) {
        var config = captchaConfig.getTurnstile();
        if (!StringUtils.hasText(token)
            || config == null
            || !StringUtils.hasText(config.getSecretKey())) {
            return Mono.just(false);
        }

        var formData = new LinkedMultiValueMap<String, String>();
        formData.add("secret", config.getSecretKey().trim());
        formData.add("response", token.trim());
        var clientIp = ClientIpUtils.getClientIp(exchange);
        if (StringUtils.hasText(clientIp) && !ClientIpUtils.UNKNOWN.equals(clientIp)) {
            formData.add("remoteip", clientIp);
        }

        return webClient.post()
            .uri(SITE_VERIFY_URL)
            .accept(MediaType.APPLICATION_JSON)
            .contentType(MediaType.APPLICATION_FORM_URLENCODED)
            .body(BodyInserters.fromFormData(formData))
            .retrieve()
            .bodyToMono(TurnstileSiteVerifyResponse.class)
            .timeout(VERIFY_TIMEOUT)
            .doOnNext(response -> {
                if (!response.success()) {
                    log.warn("Turnstile captcha validation failed. errorCodes={}",
                        response.errorCodes());
                }
            })
            .map(TurnstileSiteVerifyResponse::success)
            .defaultIfEmpty(false)
            .onErrorResume(error -> {
                log.warn("Failed to verify Turnstile captcha.", error);
                return Mono.just(false);
            });
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    record TurnstileSiteVerifyResponse(
        boolean success,
        String hostname,
        String action,
        @JsonProperty("error-codes")
        List<String> errorCodes
    ) {
    }
}
