package com.xhhao.comment.widget.upload;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Data;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.core.io.buffer.DataBuffer;
import org.springframework.core.io.buffer.DataBufferLimitException;
import org.springframework.core.io.buffer.DataBufferUtils;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.client.MultipartBodyBuilder;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import org.springframework.web.reactive.function.BodyInserters;
import org.springframework.web.reactive.function.client.WebClient;
import org.springframework.web.server.ResponseStatusException;
import reactor.core.publisher.Mono;

@Component
public class ImgBbImageUploadProvider implements ImageUploadProvider {
    private static final String IMGBB_UPLOAD_URL = "https://api.imgbb.com/1/upload";

    private final WebClient webClient = WebClient.builder().build();

    @Override
    public ImageUploadProviderType type() {
        return ImageUploadProviderType.IMGBB;
    }

    @Override
    public Mono<ImageUploadResult> upload(ImageUploadRequest request) {
        var imgBbConfig = request.config().getImgBb();
        if (!StringUtils.hasText(imgBbConfig.getApiKey())) {
            return Mono.error(new ResponseStatusException(
                HttpStatus.BAD_REQUEST,
                "请先配置 ImgBB API Key"
            ));
        }

        return readFileBytes(request)
            .flatMap(bytes -> uploadToImgBb(request, bytes));
    }

    private Mono<byte[]> readFileBytes(ImageUploadRequest request) {
        return DataBufferUtils.join(request.file().content(), maxByteCount(request))
            .map(this::toBytes)
            .onErrorMap(DataBufferLimitException.class, e -> new ResponseStatusException(
                HttpStatus.PAYLOAD_TOO_LARGE,
                "图片大小超过限制"
            ));
    }

    private int maxByteCount(ImageUploadRequest request) {
        var maxSizeBytes = request.actor().anonymous()
            ? request.config().anonymousMaxSizeBytes()
            : request.config().authenticatedMaxSizeBytes();
        return (int) Math.min(Integer.MAX_VALUE, maxSizeBytes);
    }

    private byte[] toBytes(DataBuffer dataBuffer) {
        try {
            var bytes = new byte[dataBuffer.readableByteCount()];
            dataBuffer.read(bytes);
            return bytes;
        } finally {
            DataBufferUtils.release(dataBuffer);
        }
    }

    private Mono<ImageUploadResult> uploadToImgBb(ImageUploadRequest request, byte[] bytes) {
        var multipart = new MultipartBodyBuilder();
        var imagePart = multipart.part("image", new NamedByteArrayResource(bytes, request.file().filename()))
            .filename(request.file().filename());
        var contentType = request.file().headers().getContentType();
        if (contentType != null) {
            imagePart.contentType(contentType);
        }
        var expirationSeconds = request.config().getImgBb().getExpirationSeconds();
        if (expirationSeconds > 0) {
            multipart.part("expiration", String.valueOf(expirationSeconds));
        }

        return webClient.post()
            .uri(IMGBB_UPLOAD_URL + "?key={key}", request.config().getImgBb().getApiKey())
            .contentType(MediaType.MULTIPART_FORM_DATA)
            .body(BodyInserters.fromMultipartData(multipart.build()))
            .retrieve()
            .onStatus(status -> status.isError(), response -> response.bodyToMono(String.class)
                .defaultIfEmpty("")
                .map(body -> new ResponseStatusException(
                    HttpStatus.BAD_GATEWAY,
                    "ImgBB 上传失败"
                )))
            .bodyToMono(ImgBbUploadResponse.class)
            .map(response -> toResult(request, response, bytes.length));
    }

    private ImageUploadResult toResult(ImageUploadRequest request,
                                       ImgBbUploadResponse response,
                                       long size) {
        if (response == null || response.getData() == null) {
            throw new ResponseStatusException(HttpStatus.BAD_GATEWAY, "ImgBB 未返回上传结果");
        }

        var url = response.getData().getUrl();
        if (!StringUtils.hasText(url)) {
            url = response.getData().getDisplayUrl();
        }

        if (!StringUtils.hasText(url)) {
            throw new ResponseStatusException(HttpStatus.BAD_GATEWAY, "ImgBB 未返回图片地址");
        }

        return new ImageUploadResult(
            url,
            type(),
            request.file().filename(),
            response.getData().getSize() == null ? size : response.getData().getSize(),
            contentType(request),
            null
        );
    }

    private String contentType(ImageUploadRequest request) {
        var contentType = request.file().headers().getContentType();
        return contentType == null ? null : contentType.toString();
    }

    private static class NamedByteArrayResource extends ByteArrayResource {
        private final String filename;

        NamedByteArrayResource(byte[] byteArray, String filename) {
            super(byteArray);
            this.filename = filename;
        }

        @Override
        public String getFilename() {
            return filename;
        }
    }

    @Data
    @JsonIgnoreProperties(ignoreUnknown = true)
    private static class ImgBbUploadResponse {
        private ImgBbUploadData data;
        private boolean success;
    }

    @Data
    @JsonIgnoreProperties(ignoreUnknown = true)
    private static class ImgBbUploadData {
        private String url;
        private Long size;

        @JsonProperty("display_url")
        private String displayUrl;
    }
}
