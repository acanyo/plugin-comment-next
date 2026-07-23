package com.xhhao.comment.widget.ai.website;

import com.xhhao.comment.widget.ai.ConditionalOnHaloAiFoundation;
import com.xhhao.comment.widget.network.CommentNextOutboundUriPolicy;
import java.net.URI;
import java.net.URISyntaxException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Mono;

@Component
@ConditionalOnHaloAiFoundation
@RequiredArgsConstructor
class CommentNextWebsiteUriPolicy {

    private final CommentNextOutboundUriPolicy outboundUriPolicy;

    URI normalizeWebsiteRoot(String website) {
        if (website == null || website.isBlank()) {
            throw new IllegalArgumentException("评论者网址为空");
        }

        var value = website.strip();
        if (!hasScheme(value)) {
            value = "https://" + value;
        }

        var normalized = outboundUriPolicy.normalize(URI.create(value));
        try {
            return new URI(
                normalized.getScheme(),
                null,
                normalized.getHost(),
                -1,
                "/",
                null,
                null
            );
        } catch (URISyntaxException e) {
            throw new IllegalArgumentException("评论者网址格式无效", e);
        }
    }

    Mono<URI> validateOutbound(URI candidate) {
        return outboundUriPolicy.validate(candidate);
    }

    private boolean hasScheme(String value) {
        var separator = value.indexOf("://");
        if (separator <= 0) {
            return false;
        }
        for (int i = 0; i < separator; i++) {
            var character = value.charAt(i);
            if (!(Character.isLetterOrDigit(character)
                || character == '+'
                || character == '-'
                || character == '.')) {
                return false;
            }
        }
        return true;
    }
}
