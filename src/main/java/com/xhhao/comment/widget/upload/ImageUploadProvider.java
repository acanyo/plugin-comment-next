package com.xhhao.comment.widget.upload;

import reactor.core.publisher.Mono;

public interface ImageUploadProvider {

    ImageUploadProviderType type();

    Mono<ImageUploadResult> upload(ImageUploadRequest request);
}
