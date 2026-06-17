package com.xhhao.comment.utils;

import lombok.experimental.UtilityClass;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.ReactiveSecurityContextHolder;
import org.springframework.security.core.context.SecurityContext;
import reactor.core.publisher.Mono;
import run.halo.app.infra.AnonymousUserConst;

import java.util.Arrays;

/**
 * CommonUtils
 *
 * @author <a href="https://lywq.muyin.site">lywq</a>
 * @since 2025/05/23 11:45
 **/
@Slf4j
@UtilityClass
public class CommonUtils {

    /**
     * 获取当前用户名
     * @return reactor.core.publisher.Mono<java.lang.String>
     * @author <a href="https://lywq.muyin.site">lywq</a>
     * @since 2025/05/20 18:20
     **/
    public static Mono<String> getCurrentUserName() {
        return ReactiveSecurityContextHolder.getContext()
            .map(SecurityContext::getAuthentication)
            .filter(Authentication::isAuthenticated)
            .map(Authentication::getName)
            .defaultIfEmpty(AnonymousUserConst.PRINCIPAL);
    }

    /**
     * 判断是否登录
     * @return reactor.core.publisher.Mono<java.lang.Boolean>
     * @author <a href="https://lywq.muyin.site">lywq</a>
     * @since 2025/05/13 00:01
     **/
    public static Mono<Boolean> hasLogin() {
        return getCurrentUserName()
            .map(username -> !AnonymousUserConst.isAnonymousUser(username));
    }

    /**
     * 判断是否有指定角色
     * @param role:
     * @return reactor.core.publisher.Mono<java.lang.Boolean>
     * @author <a href="https://lywq.muyin.site">lywq</a>
     * @since 2025/05/23 11:51
     **/
    public static Mono<Boolean> hasRole(String role) {
        return hasAnyRole(role);
    }

    /**
     * 判断是否有一批角色中的任意一个
     * @param roles:
     * @return reactor.core.publisher.Mono<java.lang.Boolean>
     * @author <a href="https://lywq.muyin.site">lywq</a>
     * @since 2025/05/23 11:51
     **/
    public static Mono<Boolean> hasAnyRole(String... roles) {
        return ReactiveSecurityContextHolder.getContext()
            .map(SecurityContext::getAuthentication)
            .filter(Authentication::isAuthenticated)
            .map(authentication -> authentication.getAuthorities().stream()
                .map(authority -> authority.getAuthority().replace("ROLE_", ""))
                .anyMatch(Arrays.asList(roles)::contains)
            )
            .defaultIfEmpty(false);
    }

}
