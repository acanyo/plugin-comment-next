package com.xhhao.comment.widget.captcha;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.xhhao.comment.widget.SettingConfigGetter;
import com.xhhao.comment.widget.security.ClientIpUtils;
import java.net.URI;
import java.time.Duration;
import java.util.List;
import java.util.Locale;
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
 * Verifies Google reCAPTCHA tokens (v2 checkbox and v3 score) against the
 * siteverify endpoint.
 *
 * <p>v2 only returns a binary result. v3 additionally returns a risk score in
 * the range {@code [0.0, 1.0]}, so the configured threshold and action must be
 * checked as well. Tokens are single-use and expire after 120 seconds.</p>
 *
 * <p>The api server defaults to {@code www.recaptcha.net}, Google's official
 * alternative domain, because {@code www.google.com} is not reachable from
 * mainland China. Administrators can switch it back in the plugin settings.</p>
 */
@Component
@Slf4j
public class RecaptchaCaptchaProviderVerifier implements CaptchaProviderVerifier {

    private static final String SITE_VERIFY_PATH = "/recaptcha/api/siteverify";
    private static final Duration VERIFY_TIMEOUT = Duration.ofSeconds(8);

    private final WebClient webClient = WebClient.builder().build();

    @Override
    public boolean supports(CaptchaType type) {
        return type == CaptchaType.RECAPTCHA;
    }

    @Override
    public Mono<Boolean> verify(String token, SettingConfigGetter.CaptchaConfig captchaConfig,
                                ServerWebExchange exchange) {
        var config = captchaConfig.getRecaptcha();
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
            .uri(siteVerifyUrl(config))
            .accept(MediaType.APPLICATION_JSON)
            .contentType(MediaType.APPLICATION_FORM_URLENCODED)
            .body(BodyInserters.fromFormData(formData))
            .retrieve()
            .bodyToMono(RecaptchaSiteVerifyResponse.class)
            .timeout(VERIFY_TIMEOUT)
            .doOnNext(response -> {
                if (!response.success()) {
                    log.warn("reCAPTCHA validation failed. errorCodes={}", response.errorCodes());
                }
            })
            .map(response -> isValid(config, response))
            .defaultIfEmpty(false)
            .onErrorResume(error -> {
                log.warn("Failed to verify reCAPTCHA.", error);
                return Mono.just(false);
            });
    }

    /**
     * Builds the siteverify endpoint from the configured base address. Both
     * {@code www.recaptcha.net} (Google's alternative domain, reachable from
     * mainland China) and {@code www.google.com} expose the same path, so only
     * the origin is taken from the configured value.
     */
    static String siteVerifyUrl(SettingConfigGetter.RecaptchaCaptchaConfig config) {
        return normalizeApiServer(config == null ? null : config.getApiServer())
            + SITE_VERIFY_PATH;
    }

    static String normalizeApiServer(String apiServer) {
        if (!StringUtils.hasText(apiServer)) {
            return SettingConfigGetter.RecaptchaCaptchaConfig.DEFAULT_API_SERVER;
        }

        try {
            var uri = URI.create(apiServer.trim());
            var scheme = uri.getScheme();
            if (scheme == null || uri.getHost() == null) {
                log.warn("Ignoring invalid reCAPTCHA api server: {}", apiServer);
                return SettingConfigGetter.RecaptchaCaptchaConfig.DEFAULT_API_SERVER;
            }

            var normalizedScheme = scheme.toLowerCase(Locale.ROOT);
            if (!"https".equals(normalizedScheme) && !"http".equals(normalizedScheme)) {
                log.warn("Ignoring unsupported reCAPTCHA api server scheme: {}", apiServer);
                return SettingConfigGetter.RecaptchaCaptchaConfig.DEFAULT_API_SERVER;
            }

            return normalizedScheme + "://" + uri.getAuthority();
        } catch (IllegalArgumentException e) {
            log.warn("Ignoring invalid reCAPTCHA api server: {}", apiServer);
            return SettingConfigGetter.RecaptchaCaptchaConfig.DEFAULT_API_SERVER;
        }
    }

    boolean isValid(SettingConfigGetter.RecaptchaCaptchaConfig config,
                    RecaptchaSiteVerifyResponse response) {
        if (!response.success()) {
            return false;
        }
        if (!config.usesV3()) {
            return true;
        }

        var expectedAction = StringUtils.hasText(config.getAction())
            ? config.getAction().trim()
            : SettingConfigGetter.RecaptchaCaptchaConfig.DEFAULT_ACTION;
        if (StringUtils.hasText(response.action()) && !expectedAction.equals(response.action())) {
            log.warn("reCAPTCHA action mismatch. expected={}, actual={}",
                expectedAction, response.action());
            return false;
        }

        var threshold = clampScore(config.getScoreThreshold());
        var score = response.score();
        if (score == null) {
            log.warn("reCAPTCHA v3 response did not contain a score.");
            return false;
        }
        if (score < threshold) {
            log.warn("reCAPTCHA v3 score below threshold. score={}, threshold={}",
                score, threshold);
            return false;
        }
        return true;
    }

    private static double clampScore(double score) {
        return Math.min(1.0, Math.max(0.0, score));
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    record RecaptchaSiteVerifyResponse(
        boolean success,
        Double score,
        String action,
        String hostname,
        @JsonProperty("error-codes")
        List<String> errorCodes
    ) {
    }
}
