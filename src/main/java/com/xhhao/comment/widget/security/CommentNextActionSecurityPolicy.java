package com.xhhao.comment.widget.security;

import java.time.Duration;

public record CommentNextActionSecurityPolicy(
    boolean enabled,
    boolean allowAnonymous,
    int anonymousRateLimit,
    int anonymousRateWindowSeconds,
    int authenticatedRateLimit,
    int authenticatedRateWindowSeconds
) {

    public int limitFor(CommentNextActionActor actor) {
        return Math.max(1, actor.anonymous() ? anonymousRateLimit : authenticatedRateLimit);
    }

    public Duration windowFor(CommentNextActionActor actor) {
        var seconds = Math.max(1,
            actor.anonymous() ? anonymousRateWindowSeconds : authenticatedRateWindowSeconds);
        return Duration.ofSeconds(seconds);
    }
}
