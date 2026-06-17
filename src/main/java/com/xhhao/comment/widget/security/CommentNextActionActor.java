package com.xhhao.comment.widget.security;

import org.springframework.util.StringUtils;
import run.halo.app.infra.AnonymousUserConst;

public record CommentNextActionActor(String username, boolean anonymous, String clientIp) {

    public static CommentNextActionActor of(String username, String clientIp) {
        var resolvedUsername = StringUtils.hasText(username)
            ? username
            : AnonymousUserConst.PRINCIPAL;
        return new CommentNextActionActor(
            resolvedUsername,
            AnonymousUserConst.isAnonymousUser(resolvedUsername),
            StringUtils.hasText(clientIp) ? clientIp : ClientIpUtils.UNKNOWN
        );
    }

    public String rateLimitIdentity() {
        return anonymous ? clientIp : username;
    }
}
