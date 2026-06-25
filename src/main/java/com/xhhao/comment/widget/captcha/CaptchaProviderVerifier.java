package com.xhhao.comment.widget.captcha;

import com.xhhao.comment.widget.SettingConfigGetter;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

public interface CaptchaProviderVerifier {

    boolean supports(CaptchaType type);

    Mono<Boolean> verify(String token, SettingConfigGetter.CaptchaConfig captchaConfig,
                         ServerWebExchange exchange);
}
