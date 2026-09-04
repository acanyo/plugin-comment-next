package com.xhhao.comment.widget.interactionplus;

import com.fasterxml.jackson.databind.node.ObjectNode;
import reactor.core.publisher.Mono;

/**
 * 请求级身份加载器。同作者记忆化，缺席 / 关闭 / 失败时返回空。
 */
public interface AuthorIdentityLoader {

    Mono<ObjectNode> load(String userName);

    static AuthorIdentityLoader disabled() {
        return userName -> Mono.empty();
    }
}
