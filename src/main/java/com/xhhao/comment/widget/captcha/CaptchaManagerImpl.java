package com.xhhao.comment.widget.captcha;

import com.google.common.cache.Cache;
import com.google.common.cache.CacheBuilder;
import com.xhhao.comment.widget.SettingConfigGetter;
import java.awt.image.BufferedImage;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.TimeUnit;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;
import reactor.core.scheduler.Schedulers;

@Component
@RequiredArgsConstructor
public class CaptchaManagerImpl implements CaptchaManager {
    public static final long CODE_EXPIRATION_MINUTES = 1;

    private final Cache<String, Captcha> captchaCache =
        CacheBuilder.newBuilder()
            .expireAfterWrite(CODE_EXPIRATION_MINUTES, TimeUnit.MINUTES)
            .maximumSize(100)
            .build();

    private final CaptchaCookieResolver captchaCookieResolver;
    private final List<CaptchaProviderVerifier> providerVerifiers;

    @Override
    public Mono<Boolean> verify(String id, String captchaCode,
                                SettingConfigGetter.CaptchaConfig captchaConfig,
                                ServerWebExchange exchange) {
        if (captchaConfig.getType().isLocalImage()) {
            return verifyLocalImageCaptcha(id, captchaCode, captchaConfig);
        }

        return resolveProviderVerifier(captchaConfig.getType())
            .map(verifier -> verifier.verify(captchaCode, captchaConfig, exchange))
            .orElse(Mono.just(false))
            .onErrorReturn(false);
    }

    @Override
    public Mono<Void> invalidate(String id) {
        captchaCache.invalidate(id);
        return Mono.empty();
    }

    @Override
    public Mono<Captcha> generate(ServerWebExchange exchange,
                                  SettingConfigGetter.CaptchaConfig captchaConfig) {
        if (!captchaConfig.getType().isLocalImage()) {
            return Mono.just(new Captcha("", "", "", captchaConfig.getType()));
        }

        return doGenerate(captchaConfig)
            .doOnNext(captcha -> captchaCookieResolver.setCookie(exchange, captcha.id()));
    }

    private Mono<Boolean> verifyLocalImageCaptcha(String id, String captchaCode,
                                                  SettingConfigGetter.CaptchaConfig captchaConfig) {
        return Mono.justOrEmpty(captchaCache.getIfPresent(id))
            .filter(captcha -> captcha.type() == captchaConfig.getType())
            .filter(captcha -> matchesCaptchaCode(captcha, captchaCode, captchaConfig))
            .hasElement();
    }

    private Mono<Captcha> doGenerate(SettingConfigGetter.CaptchaConfig captchaConfig) {
        return Mono.fromSupplier(() -> {
                var captcha = switch (captchaConfig.getType()) {
                    case ALPHANUMERIC -> CaptchaGenerator.generateSimpleCaptcha(captchaConfig.getCaptchaLength());
                    case ARITHMETIC -> CaptchaGenerator.generateMathCaptcha(captchaConfig.getArithmeticRange());
                    case GEETEST, ALTCHA, CAP ->
                        throw new IllegalStateException("External captcha type does not generate local image");
                };
                var imageBase64 = encodeBufferedImageToDataUri(captcha.image());
                var id = UUID.randomUUID().toString();
                return new Captcha(id, captcha.code(), imageBase64, captchaConfig.getType());
            })
            .subscribeOn(Schedulers.boundedElastic())
            .doOnNext(captcha -> captchaCache.put(captcha.id(), captcha));
    }

    private static String encodeBufferedImageToDataUri(BufferedImage image) {
        var imageBase64 = CaptchaGenerator.encodeToBase64(image);
        return "data:image/png;base64," + imageBase64;
    }

    private boolean matchesCaptchaCode(Captcha captcha, String captchaCode,
                                       SettingConfigGetter.CaptchaConfig captchaConfig) {
        return captchaConfig.isIgnoreCase()
            ? captcha.code().equalsIgnoreCase(captchaCode)
            : captcha.code().equals(captchaCode);
    }

    private java.util.Optional<CaptchaProviderVerifier> resolveProviderVerifier(CaptchaType type) {
        return providerVerifiers.stream()
            .filter(verifier -> verifier.supports(type))
            .findFirst();
    }
}
