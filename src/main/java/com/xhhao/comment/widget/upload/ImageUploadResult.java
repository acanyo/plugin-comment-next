package com.xhhao.comment.widget.upload;

public record ImageUploadResult(
    String url,
    ImageUploadProviderType provider,
    String filename,
    Long size,
    String contentType,
    String attachmentName
) {
}
