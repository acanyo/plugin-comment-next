package com.xhhao.comment.widget.captcha;

import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;
import com.xhhao.comment.widget.SettingConfigGetter;

public interface CaptchaManager {
    Mono<Boolean> verify(String id, String captchaCode, SettingConfigGetter.CaptchaConfig captchaConfig,
                         ServerWebExchange exchange);

    Mono<Void> invalidate(String id);

    Mono<Captcha> generate(ServerWebExchange exchange, SettingConfigGetter.CaptchaConfig captchaConfig);

    record Captcha(
        String id,
        String code,
        String imageBase64,
        CaptchaType type
    ) {
    }
}
