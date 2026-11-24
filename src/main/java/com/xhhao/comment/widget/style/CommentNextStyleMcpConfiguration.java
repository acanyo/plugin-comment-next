package com.xhhao.comment.widget.style;

import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import run.halo.app.infra.ExternalUrlSupplier;

@Configuration(proxyBeanMethods = false)
@ConditionalOnClass(name = "run.halo.mcpserver.api.McpToolProvider")
class CommentNextStyleMcpConfiguration {

    @Bean
    CommentNextStyleMcpToolProvider commentNextStyleMcpToolProvider(CommentNextStyleStore store,
        ExternalUrlSupplier externalUrl) {
        return new CommentNextStyleMcpToolProvider(store, externalUrl);
    }
}
