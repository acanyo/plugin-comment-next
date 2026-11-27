package com.xhhao.comment.widget.emote;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.net.URI;
import java.net.URL;
import java.util.function.Consumer;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.http.codec.multipart.FilePart;
import reactor.core.publisher.Mono;
import run.halo.app.core.extension.attachment.Attachment;
import run.halo.app.core.extension.service.AttachmentService;
import run.halo.app.extension.Metadata;
import run.halo.app.infra.ExternalUrlSupplier;

class CommentNextEmoteAttachmentUploaderTest {

    private final AttachmentService attachmentService = mock(AttachmentService.class);
    private final ExternalUrlSupplier externalUrlSupplier = mock(ExternalUrlSupplier.class);
    private final CommentNextEmoteAttachmentUploader uploader =
        new CommentNextEmoteAttachmentUploader(attachmentService, externalUrlSupplier);

    @Test
    @SuppressWarnings("unchecked")
    void resolvesRelativePermalinkWithConfiguredExternalUrlAndSetsSourceAnnotation() throws Exception {
        when(externalUrlSupplier.getRaw()).thenReturn(new URL("https://blog.example.com/"));

        var uploadedAttachment = new Attachment();
        uploadedAttachment.setMetadata(new Metadata());
        var status = new Attachment.AttachmentStatus();
        status.setPermalink("/upload/emote-alu-123.png");
        uploadedAttachment.setStatus(status);

        when(attachmentService.upload(
            eq("admin"),
            eq("default-policy"),
            eq("emote-group"),
            any(FilePart.class),
            any(Consumer.class)
        )).thenAnswer(invocation -> {
            Consumer<Attachment> consumer = invocation.getArgument(4);
            consumer.accept(uploadedAttachment);
            return Mono.just(uploadedAttachment);
        });

        var result = uploader.upload(
            "admin",
            "default-policy",
            "emote-group",
            "emote-alu-123.png",
            new byte[] {1, 2, 3},
            MediaType.IMAGE_PNG,
            "https://raw.githubusercontent.com/test/1.png",
            true,
            URI.create("http://localhost:8090/")
        ).block();

        assertThat(result).isEqualTo("https://blog.example.com/upload/emote-alu-123.png");
        assertThat(uploadedAttachment.getMetadata().getAnnotations())
            .containsEntry(
                CommentNextEmoteAttachmentUploader.SOURCE_URL_ANNOTATION,
                "https://raw.githubusercontent.com/test/1.png"
            );
    }

    @Test
    void keepsRelativePermalinkWhenAbsoluteUrlDisabledAndPreservesCloudStorageUrl() {
        when(externalUrlSupplier.getRaw()).thenReturn(null);
        when(externalUrlSupplier.get()).thenReturn(URI.create("/"));

        assertThat(uploader.formatAccessibleUrl(
            "/upload/a.png",
            false,
            URI.create("https://fallback.example.com/")
        )).isEqualTo("/upload/a.png");

        assertThat(uploader.formatAccessibleUrl(
            "/upload/a.png",
            true,
            URI.create("https://fallback.example.com/")
        )).isEqualTo("https://fallback.example.com/upload/a.png");

        assertThat(uploader.formatAccessibleUrl(
            "https://cos.example.com/emotes/a.png",
            true,
            URI.create("https://fallback.example.com/")
        )).isEqualTo("https://cos.example.com/emotes/a.png");
    }
}
