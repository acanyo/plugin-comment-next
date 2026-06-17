package com.xhhao.comment.widget.upload;

import java.util.Locale;
import java.util.Map;
import org.springframework.http.MediaType;
import org.springframework.http.codec.multipart.FilePart;
import org.springframework.util.StringUtils;

final class CommentNextImageContentTypes {
    private static final Map<String, String> CONTENT_TYPES_BY_EXTENSION = Map.ofEntries(
        Map.entry("jpg", MediaType.IMAGE_JPEG_VALUE),
        Map.entry("jpeg", MediaType.IMAGE_JPEG_VALUE),
        Map.entry("png", MediaType.IMAGE_PNG_VALUE),
        Map.entry("gif", MediaType.IMAGE_GIF_VALUE),
        Map.entry("webp", "image/webp"),
        Map.entry("avif", "image/avif"),
        Map.entry("bmp", "image/bmp"),
        Map.entry("heic", "image/heic"),
        Map.entry("heif", "image/heif"),
        Map.entry("ico", "image/x-icon"),
        Map.entry("svg", "image/svg+xml"),
        Map.entry("tif", "image/tiff"),
        Map.entry("tiff", "image/tiff")
    );

    private static final Map<String, String> CONTENT_TYPE_ALIASES = Map.of(
        "image/jpg", MediaType.IMAGE_JPEG_VALUE,
        "image/pjpeg", MediaType.IMAGE_JPEG_VALUE,
        "image/x-png", MediaType.IMAGE_PNG_VALUE,
        "image/vnd.microsoft.icon", "image/x-icon"
    );

    private CommentNextImageContentTypes() {
    }

    static MediaType resolve(FilePart file) {
        var declaredContentType = normalize(file.headers().getContentType());
        if (declaredContentType != null
            && !MediaType.APPLICATION_OCTET_STREAM.equals(declaredContentType)) {
            return declaredContentType;
        }

        var extension = StringUtils.getFilenameExtension(file.filename());
        if (!StringUtils.hasText(extension)) {
            return declaredContentType;
        }

        var inferredContentType = CONTENT_TYPES_BY_EXTENSION.get(extension.toLowerCase(Locale.ROOT));
        return StringUtils.hasText(inferredContentType)
            ? MediaType.parseMediaType(inferredContentType)
            : declaredContentType;
    }

    static boolean isImage(MediaType contentType) {
        var normalizedContentType = normalize(contentType);
        return normalizedContentType != null
            && "image".equalsIgnoreCase(normalizedContentType.getType());
    }

    private static MediaType normalize(MediaType contentType) {
        if (contentType == null) {
            return null;
        }

        var normalizedValue = contentType.getType().toLowerCase(Locale.ROOT)
            + "/"
            + contentType.getSubtype().toLowerCase(Locale.ROOT);
        var alias = CONTENT_TYPE_ALIASES.get(normalizedValue);

        return StringUtils.hasText(alias)
            ? MediaType.parseMediaType(alias)
            : MediaType.parseMediaType(normalizedValue);
    }
}
