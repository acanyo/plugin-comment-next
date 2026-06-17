package com.xhhao.comment.widget.upload;

import java.nio.file.Path;
import org.springframework.core.io.buffer.DataBuffer;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.codec.multipart.FilePart;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

class ContentTypedFilePart implements FilePart {
    private final FilePart delegate;
    private final MediaType contentType;

    ContentTypedFilePart(FilePart delegate, MediaType contentType) {
        this.delegate = delegate;
        this.contentType = contentType;
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
        var headers = new HttpHeaders();
        headers.putAll(delegate.headers());
        headers.setContentType(contentType);
        return headers;
    }

    @Override
    public Flux<DataBuffer> content() {
        return delegate.content();
    }
}
