package com.xhhao.comment.widget.upload;

import com.xhhao.comment.widget.SettingConfigGetter;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import org.springframework.web.server.ResponseStatusException;
import reactor.core.publisher.Mono;
import run.halo.app.core.extension.attachment.Attachment;
import run.halo.app.core.extension.service.AttachmentService;

@Component
@RequiredArgsConstructor
public class HaloAttachmentImageUploadProvider implements ImageUploadProvider {
    private final AttachmentService attachmentService;

    @Override
    public ImageUploadProviderType type() {
        return ImageUploadProviderType.HALO_ATTACHMENT;
    }

    @Override
    public Mono<ImageUploadResult> upload(ImageUploadRequest request) {
        var haloConfig = request.config().getHaloAttachment();
        if (!StringUtils.hasText(haloConfig.getPolicyName())) {
            return Mono.error(new ResponseStatusException(
                HttpStatus.BAD_REQUEST,
                "请先配置 Halo 附件存储策略"
            ));
        }

        return attachmentService.upload(
                request.actor().username(),
                haloConfig.getPolicyName(),
                normalizeGroupName(haloConfig),
                request.file(),
                null
            )
            .flatMap(this::ensurePermalink)
            .map(attachment -> toResult(request, attachment));
    }

    private String normalizeGroupName(SettingConfigGetter.HaloAttachmentUploadConfig haloConfig) {
        return StringUtils.hasText(haloConfig.getGroupName()) ? haloConfig.getGroupName() : null;
    }

    private Mono<Attachment> ensurePermalink(Attachment attachment) {
        var status = attachment.getStatus();
        if (status != null && StringUtils.hasText(status.getPermalink())) {
            return Mono.just(attachment);
        }

        return attachmentService.getPermalink(attachment)
            .doOnNext(permalink -> {
                var currentStatus = attachment.getStatus();
                if (currentStatus == null) {
                    currentStatus = new Attachment.AttachmentStatus();
                    attachment.setStatus(currentStatus);
                }
                currentStatus.setPermalink(permalink.toString());
            })
            .thenReturn(attachment);
    }

    private ImageUploadResult toResult(ImageUploadRequest request, Attachment attachment) {
        var status = attachment.getStatus();
        var spec = attachment.getSpec();
        var url = status == null ? "" : status.getPermalink();
        if (!StringUtils.hasText(url)) {
            throw new ResponseStatusException(
                HttpStatus.BAD_GATEWAY,
                "Halo 附件上传成功但未返回访问地址"
            );
        }

        return new ImageUploadResult(
            url,
            type(),
            spec == null || !StringUtils.hasText(spec.getDisplayName())
                ? request.file().filename()
                : spec.getDisplayName(),
            spec == null ? request.file().headers().getContentLength() : spec.getSize(),
            spec == null ? contentType(request) : spec.getMediaType(),
            attachment.getMetadata() == null ? null : attachment.getMetadata().getName()
        );
    }

    private String contentType(ImageUploadRequest request) {
        var contentType = request.file().headers().getContentType();
        return contentType == null ? null : contentType.toString();
    }
}
