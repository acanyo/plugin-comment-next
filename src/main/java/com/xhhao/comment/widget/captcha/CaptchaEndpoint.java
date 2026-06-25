package com.xhhao.comment.widget.captcha;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.xhhao.comment.utils.JsonUtils;
import com.xhhao.comment.widget.SettingConfigGetter;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.server.RouterFunction;
import org.springframework.web.reactive.function.server.RouterFunctions;
import org.springframework.web.reactive.function.server.ServerRequest;
import org.springframework.web.reactive.function.server.ServerResponse;
import org.springframework.web.server.ResponseStatusException;
import reactor.core.publisher.Mono;
import run.halo.app.core.extension.endpoint.CustomEndpoint;
import run.halo.app.extension.GroupVersion;

@Component
@RequiredArgsConstructor
public class CaptchaEndpoint implements CustomEndpoint {

    private final CaptchaManager captchaManager;
    private final SettingConfigGetter settingConfigGetter;
    private final AltchaCaptchaService altchaCaptchaService;
    private final ObjectMapper objectMapper = JsonUtils.createObjectMapper()
        .setSerializationInclusion(JsonInclude.Include.NON_NULL);

    @Override
    public RouterFunction<ServerResponse> endpoint() {
        return RouterFunctions.route()
            .GET("captcha/-/generate", this::generateCaptcha)
            .GET("captcha/-/altcha-challenge", this::generateAltchaChallenge)
            .GET("captcha/altcha/challenge", this::generateAltchaChallenge)
            .build();
    }

    private Mono<ServerResponse> generateCaptcha(ServerRequest request) {
        return settingConfigGetter.getSecurityConfig()
            .map(SettingConfigGetter.SecurityConfig::getCaptcha)
            .flatMap(captchaConfig -> captchaManager.generate(request.exchange(), captchaConfig))
            .flatMap(captcha -> ServerResponse.ok().bodyValue(captcha.imageBase64()));
    }

    private Mono<ServerResponse> generateAltchaChallenge(ServerRequest request) {
        return settingConfigGetter.getSecurityConfig()
            .map(SettingConfigGetter.SecurityConfig::getCaptcha)
            .filter(captchaConfig -> captchaConfig.getType() == CaptchaType.ALTCHA)
            .switchIfEmpty(Mono.error(new ResponseStatusException(HttpStatus.BAD_REQUEST, "ALTCHA 验证码未启用")))
            .flatMap(altchaCaptchaService::createChallenge)
            .map(challenge -> objectMapper.convertValue(challenge, ObjectNode.class))
            .flatMap(challenge -> ServerResponse.ok()
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue(challenge));
    }

    @Override
    public GroupVersion groupVersion() {
        return GroupVersion.parseAPIVersion("api.commentnext.xhhao.com/v1alpha1");
    }
}
