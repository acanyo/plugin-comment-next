package com.xhhao.comment.widget.upload;

import java.nio.file.Path;
import java.util.concurrent.atomic.AtomicLong;
import org.springframework.core.io.buffer.DataBuffer;
import org.springframework.core.io.buffer.DataBufferUtils;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.codec.multipart.FilePart;
import org.springframework.web.server.ResponseStatusException;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

class SizeLimitedFilePart implements FilePart {
    private final FilePart delegate;
    private final long maxSizeBytes;

    SizeLimitedFilePart(FilePart delegate, long maxSizeBytes) {
        this.delegate = delegate;
        this.maxSizeBytes = maxSizeBytes;
    }

    @Override
    public String filename() {
        return delegate.filename();
    }

    @Override
    public Mono<Void> transferTo(Path dest) {
        return delegate.transferTo(dest);
    }

    @Override
    public String name() {
        return delegate.name();
    }

    @Override
    public HttpHeaders headers() {
        return delegate.headers();
    }

    @Override
    public Flux<DataBuffer> content() {
        var consumed = new AtomicLong();
        return delegate.content()
            .handle((buffer, sink) -> {
                var nextSize = consumed.addAndGet(buffer.readableByteCount());
                if (nextSize > maxSizeBytes) {
                    DataBufferUtils.release(buffer);
                    sink.error(new ResponseStatusException(
                        HttpStatus.PAYLOAD_TOO_LARGE,
                        "图片大小超过限制"
                    ));
                    return;
                }
                sink.next(buffer);
            });
    }
}
