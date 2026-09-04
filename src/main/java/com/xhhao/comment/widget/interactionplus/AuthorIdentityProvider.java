package com.xhhao.comment.widget.interactionplus;

import reactor.core.publisher.Mono;

public interface AuthorIdentityProvider {

    Mono<AuthorIdentityLoader> newLoader();
}
