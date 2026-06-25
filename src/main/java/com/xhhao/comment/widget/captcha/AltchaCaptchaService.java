package com.xhhao.comment.widget.captcha;

import com.google.common.cache.Cache;
import com.google.common.cache.CacheBuilder;
import com.xhhao.comment.widget.SettingConfigGetter;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.util.Base64;
import java.util.HexFormat;
import java.util.Set;
import java.util.concurrent.TimeUnit;
import lombok.extern.slf4j.Slf4j;
import org.altcha.altcha.v2.Altcha;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import reactor.core.publisher.Mono;
import reactor.core.scheduler.Schedulers;

@Component
@Slf4j
public class AltchaCaptchaService {

    private static final Set<String> SUPPORTED_ALGORITHMS = Set.of(
        "PBKDF2/SHA-256",
        "PBKDF2/SHA-384",
        "PBKDF2/SHA-512",
        "SHA-256",
        "SHA-384",
        "SHA-512"
    );
    private static final int MIN_COST = 1000;
    private static final int MAX_COST = 50000;
    private static final int MIN_EXPIRES_IN_SECONDS = 60;
    private static final int MAX_EXPIRES_IN_SECONDS = 3600;
    private static final SecureRandom SECURE_RANDOM = new SecureRandom();

    private final Cache<String, Boolean> usedPayloads = CacheBuilder.newBuilder()
        .expireAfterWrite(MAX_EXPIRES_IN_SECONDS, TimeUnit.SECONDS)
        .maximumSize(5000)
        .build();

    private final String automaticSecret = generateAutomaticSecret();

    public Mono<Altcha.Challenge> createChallenge(SettingConfigGetter.CaptchaConfig captchaConfig) {
        return Mono.fromCallable(() -> {
                var config = captchaConfig.getAltcha();
                var secret = resolveSecret(config);
                return Altcha.createChallenge(new Altcha.CreateChallengeOptions()
                    .algorithm(resolveAlgorithm(config))
                    .cost(resolveCost(config))
                    .expiresInSeconds(resolveExpiresInSeconds(config))
                    .hmacSignatureSecret(secret));
            })
            .subscribeOn(Schedulers.boundedElastic());
    }

    public Mono<Boolean> verify(String payload, SettingConfigGetter.CaptchaConfig captchaConfig) {
        if (!StringUtils.hasText(payload)) {
            return Mono.just(false);
        }

        return Mono.fromCallable(() -> verifyPayload(payload.trim(), captchaConfig))
            .subscribeOn(Schedulers.boundedElastic())
            .onErrorResume(error -> {
                log.warn("Failed to verify ALTCHA captcha.", error);
                return Mono.just(false);
            });
    }

    private boolean verifyPayload(String payload, SettingConfigGetter.CaptchaConfig captchaConfig) throws Exception {
        var payloadHash = sha256Hex(payload);
        if (usedPayloads.getIfPresent(payloadHash) != null) {
            return false;
        }

        var config = captchaConfig.getAltcha();
        var result = Altcha.verifySolution(
            payload,
            resolveSecret(config),
            Altcha.kdf(resolveAlgorithm(config))
        );
        if (result.verified()) {
            usedPayloads.put(payloadHash, true);
        }
        return result.verified();
    }

    private String resolveSecret(SettingConfigGetter.AltchaCaptchaConfig config) {
        if (config != null && StringUtils.hasText(config.getSecret())) {
            return config.getSecret().trim();
        }
        return automaticSecret;
    }

    private String resolveAlgorithm(SettingConfigGetter.AltchaCaptchaConfig config) {
        var algorithm = config == null ? null : config.getAlgorithm();
        if (StringUtils.hasText(algorithm) && SUPPORTED_ALGORITHMS.contains(algorithm.trim())) {
            return algorithm.trim();
        }
        return SettingConfigGetter.AltchaCaptchaConfig.DEFAULT_ALGORITHM;
    }

    private int resolveCost(SettingConfigGetter.AltchaCaptchaConfig config) {
        var value = config == null ? SettingConfigGetter.AltchaCaptchaConfig.DEFAULT_COST : config.getCost();
        return clamp(value, MIN_COST, MAX_COST);
    }

    private int resolveExpiresInSeconds(SettingConfigGetter.AltchaCaptchaConfig config) {
        var value = config == null
            ? SettingConfigGetter.AltchaCaptchaConfig.DEFAULT_EXPIRES_IN_SECONDS
            : config.getExpiresInSeconds();
        return clamp(value, MIN_EXPIRES_IN_SECONDS, MAX_EXPIRES_IN_SECONDS);
    }

    private static int clamp(int value, int min, int max) {
        return Math.min(max, Math.max(min, value));
    }

    private static String sha256Hex(String value) {
        try {
            var digest = MessageDigest.getInstance("SHA-256")
                .digest(value.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(digest);
        } catch (Exception e) {
            throw new IllegalStateException("Failed to hash ALTCHA payload", e);
        }
    }

    private static String generateAutomaticSecret() {
        var bytes = new byte[32];
        SECURE_RANDOM.nextBytes(bytes);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }
}
