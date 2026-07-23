package com.xhhao.comment.widget.qq;

import static org.springdoc.core.fn.builders.apiresponse.Builder.responseBuilder;
import static org.springdoc.core.fn.builders.parameter.Builder.parameterBuilder;
import static org.springdoc.webflux.core.fn.SpringdocRouteBuilder.route;

import com.xhhao.comment.widget.SettingConfigGetter;
import com.xhhao.comment.widget.security.CommentNextAction;
import com.xhhao.comment.widget.security.CommentNextActionGuard;
import com.xhhao.comment.widget.security.CommentNextActionSecurityPolicy;
import io.swagger.v3.oas.annotations.enums.ParameterIn;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.server.RouterFunction;
import org.springframework.web.reactive.function.server.ServerRequest;
import org.springframework.web.reactive.function.server.ServerResponse;
import reactor.core.publisher.Mono;
import run.halo.app.core.extension.endpoint.CustomEndpoint;
import run.halo.app.extension.GroupVersion;

@Component
@RequiredArgsConstructor
public class CommentNextQqProfileEndpoint implements CustomEndpoint {

    private static final CommentNextActionSecurityPolicy SECURITY_POLICY =
        new CommentNextActionSecurityPolicy(
            true,
            true,
            10,
            60,
            30,
            60,
            false,
            true,
            List.of(),
            true
        );

    private final SettingConfigGetter settingConfigGetter;

    private final CommentNextActionGuard actionGuard;

    private final CommentNextQqProfileService profileService;

    @Override
    public RouterFunction<ServerResponse> endpoint() {
        final var tag = "api.commentnext.xhhao.com/v1alpha1/QQProfile";
        return route()
            .GET("qq-profiles", this::getProfile, builder -> builder
                .operationId("GetCommentNextQqProfile")
                .description("Resolve a nickname for an anonymous QQ email address.")
                .tag(tag)
                .parameter(parameterBuilder()
                    .in(ParameterIn.QUERY)
                    .name("email")
                    .implementation(String.class)
                    .required(true))
                .response(responseBuilder().implementation(CommentNextQqProfile.class)))
            .build();
    }

    private Mono<ServerResponse> getProfile(ServerRequest request) {
        var email = request.queryParam("email").orElse("");
        return settingConfigGetter.getQqProfileConfig()
            .flatMap(config -> {
                if (!config.isEnabled()) {
                    return Mono.just(CommentNextQqProfile.empty());
                }
                return actionGuard.verify(request, CommentNextAction.QQ_PROFILE, SECURITY_POLICY)
                    .then(profileService.lookup(email, config));
            })
            .flatMap(profile -> ServerResponse.ok().bodyValue(profile));
    }

    @Override
    public GroupVersion groupVersion() {
        return GroupVersion.parseAPIVersion("api.commentnext.xhhao.com/v1alpha1");
    }
}
