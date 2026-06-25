package com.xhhao.comment.widget.captcha;

import com.xhhao.comment.widget.SettingConfigGetter;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

@Component
@RequiredArgsConstructor
public class AltchaCaptchaProviderVerifier implements CaptchaProviderVerifier {

    private final AltchaCaptchaService altchaCaptchaService;

    @Override
    public boolean supports(CaptchaType type) {
        return type == CaptchaType.ALTCHA;
    }

    @Override
    public Mono<Boolean> verify(String token, SettingConfigGetter.CaptchaConfig captchaConfig,
                                ServerWebExchange exchange) {
        return altchaCaptchaService.verify(token, captchaConfig);
    }
}
