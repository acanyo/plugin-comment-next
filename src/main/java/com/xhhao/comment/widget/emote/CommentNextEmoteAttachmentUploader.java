package com.xhhao.comment.widget.emote;

import java.net.URI;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.Locale;
import lombok.RequiredArgsConstructor;
import org.springframework.core.io.buffer.DataBuffer;
import org.springframework.core.io.buffer.DefaultDataBufferFactory;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.codec.multipart.FilePart;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.core.scheduler.Schedulers;
import run.halo.app.core.extension.attachment.Attachment;
import run.halo.app.core.extension.service.AttachmentService;
import run.halo.app.infra.ExternalUrlSupplier;

/**
 * Uploads localized emote images into Halo attachments and resolves their public URLs.
 */
@Component
@RequiredArgsConstructor
class CommentNextEmoteAttachmentUploader {

    static final String SOURCE_URL_ANNOTATION = "comment-next.xhhao.com/emote-source-url";

    private final AttachmentService attachmentService;

    private final ExternalUrlSupplier externalUrlSupplier;

    Mono<String> upload(String username,
                        String policyName,
                        String attachmentGroupName,
                        String filename,
                        byte[] bytes,
                        MediaType mediaType,
                        String sourceUrl,
                        boolean useAbsoluteUrl,
                        URI fallbackBaseUri) {
        var filePart = new ByteArrayFilePart(filename, bytes, mediaType);
        var normalizedGroup = StringUtils.hasText(attachmentGroupName)
            ? attachmentGroupName.trim()
            : null;
        return attachmentService.upload(
                username,
                policyName.trim(),
                normalizedGroup,
                filePart,
                attachment -> attachSourceAnnotation(attachment, sourceUrl)
            )
            .flatMap(this::ensurePermalink)
            .map(permalink -> formatAccessibleUrl(permalink, useAbsoluteUrl, fallbackBaseUri));
    }

    Mono<String> resolveExistingPermalink(Attachment attachment,
                                          boolean useAbsoluteUrl,
                                          URI fallbackBaseUri) {
        return ensurePermalink(attachment)
            .map(permalink -> formatAccessibleUrl(permalink, useAbsoluteUrl, fallbackBaseUri));
    }

    URI resolveSiteBaseUri(URI fallbackBaseUri) {
        var configured = configuredExternalUri();
        if (configured != null) {
            return configured;
        }
        return normalizeOriginUri(fallbackBaseUri);
    }

    boolean hasConfiguredExternalUrl() {
        return configuredExternalUri() != null;
    }

    String formatAccessibleUrl(String permalink, boolean useAbsoluteUrl, URI fallbackBaseUri) {
        if (!StringUtils.hasText(permalink)) {
            throw new IllegalStateException("Halo 附件上传成功但未返回访问地址");
        }
        var trimmed = permalink.trim();
        if (trimmed.startsWith("//")) {
            return "https:" + trimmed;
        }
        if (trimmed.regionMatches(true, 0, "http://", 0, 7)
            || trimmed.regionMatches(true, 0, "https://", 0, 8)) {
            return trimmed;
        }
        if (!useAbsoluteUrl) {
            return trimmed;
        }
        var baseUri = resolveSiteBaseUri(fallbackBaseUri);
        if (baseUri == null) {
            return trimmed;
        }
        try {
            return baseUri.resolve(trimmed).toString();
        } catch (IllegalArgumentException ignored) {
            return trimmed;
        }
    }

    private URI configuredExternalUri() {
        try {
            var raw = externalUrlSupplier.getRaw();
            if (raw != null) {
                var normalized = normalizeOriginUri(raw.toURI());
                if (normalized != null) {
                    return normalized;
                }
            }
        } catch (Exception ignored) {
            // Fall through to get()
        }

        try {
            var uri = externalUrlSupplier.get();
            return normalizeOriginUri(uri);
        } catch (Exception ignored) {
            return null;
        }
    }

    private static URI normalizeOriginUri(URI uri) {
        if (uri == null || !StringUtils.hasText(uri.getScheme()) || !StringUtils.hasText(uri.getHost())) {
            return null;
        }
        var scheme = uri.getScheme().toLowerCase(Locale.ROOT);
        if (!"http".equals(scheme) && !"https".equals(scheme)) {
            return null;
        }
        var defaultPort = "https".equals(scheme) ? 443 : 80;
        var port = uri.getPort();
        var portPart = port > 0 && port != defaultPort ? ":" + port : "";
        return URI.create(scheme + "://" + uri.getHost() + portPart + "/");
    }

    private void attachSourceAnnotation(Attachment attachment, String sourceUrl) {
        if (attachment == null || attachment.getMetadata() == null || !StringUtils.hasText(sourceUrl)) {
            return;
        }
        var annotations = attachment.getMetadata().getAnnotations();
        var mutable = annotations == null ? new HashMap<String, String>() : new HashMap<>(annotations);
        mutable.put(SOURCE_URL_ANNOTATION, sourceUrl);
        attachment.getMetadata().setAnnotations(mutable);
    }

    private Mono<String> ensurePermalink(Attachment attachment) {
        var status = attachment.getStatus();
        if (status != null && StringUtils.hasText(status.getPermalink())) {
            return Mono.just(status.getPermalink());
        }
        return attachmentService.getPermalink(attachment)
            .map(URI::toString)
            .filter(StringUtils::hasText)
            .switchIfEmpty(Mono.error(new IllegalStateException("Halo 附件上传成功但未返回访问地址")));
    }

    private static final class ByteArrayFilePart implements FilePart {

        private final String filename;
        private final byte[] bytes;
        private final HttpHeaders headers;

        private ByteArrayFilePart(String filename, byte[] bytes, MediaType mediaType) {
            this.filename = filename;
            this.bytes = bytes;
            this.headers = new HttpHeaders();
            if (mediaType != null) {
                this.headers.setContentType(mediaType);
            }
            this.headers.setContentLength(bytes.length);
        }

        @Override
        public String filename() {
            return filename;
        }

        @Override
        public Mono<Void> transferTo(Path dest) {
            return Mono.fromCallable(() -> {
                    Files.write(dest, bytes);
                    return null;
                })
                .subscribeOn(Schedulers.boundedElastic())
                .then();
        }

        @Override
        public String name() {
            return "file";
        }

        @Override
        public HttpHeaders headers() {
            return headers;
        }

        @Override
        public Flux<DataBuffer> content() {
            return Flux.defer(() -> Flux.just(DefaultDataBufferFactory.sharedInstance.wrap(bytes)));
        }
    }
}
