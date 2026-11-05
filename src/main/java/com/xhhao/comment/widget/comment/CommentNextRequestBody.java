package com.xhhao.comment.widget.comment;

import org.springframework.core.io.buffer.DataBufferLimitException;
import org.springframework.core.io.buffer.DataBufferUtils;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.server.reactive.ServerHttpRequestDecorator;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

/** Bounded request buffering shared by comment submission filters. */
public final class CommentNextRequestBody {

    public static final int MAX_BYTES = 1024 * 1024;

    private CommentNextRequestBody() {
    }

    public static Mono<byte[]> read(ServerWebExchange exchange) {
        if (exchange.getRequest().getHeaders().getContentLength() > MAX_BYTES) {
            return Mono.error(payloadTooLarge());
        }
        return DataBufferUtils.join(exchange.getRequest().getBody(), MAX_BYTES)
            .map(buffer -> {
                try {
                    var bytes = new byte[buffer.readableByteCount()];
                    buffer.read(bytes);
                    return bytes;
                } finally {
                    DataBufferUtils.release(buffer);
                }
            })
            .defaultIfEmpty(new byte[0])
            .onErrorMap(DataBufferLimitException.class, error -> payloadTooLarge());
    }

    public static ServerWebExchange replay(ServerWebExchange exchange, byte[] bytes) {
        var request = new ServerHttpRequestDecorator(exchange.getRequest()) {
            @Override
            public HttpHeaders getHeaders() {
                var headers = new HttpHeaders();
                headers.putAll(super.getHeaders());
                headers.remove(HttpHeaders.TRANSFER_ENCODING);
                headers.setContentLength(bytes.length);
                return headers;
            }

            @Override
            public Flux<org.springframework.core.io.buffer.DataBuffer> getBody() {
                return Flux.defer(() -> Flux.just(exchange.getResponse().bufferFactory().wrap(bytes)));
            }
        };
        return exchange.mutate().request(request).build();
    }

    private static ResponseStatusException payloadTooLarge() {
        return new ResponseStatusException(HttpStatus.PAYLOAD_TOO_LARGE, "评论请求体不能超过 1 MiB");
    }
}
