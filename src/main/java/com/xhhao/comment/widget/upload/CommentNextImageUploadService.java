package com.xhhao.comment.widget.upload;

import com.xhhao.comment.widget.SettingConfigGetter;
import com.xhhao.comment.widget.security.CommentNextAction;
import com.xhhao.comment.widget.security.CommentNextActionActor;
import com.xhhao.comment.widget.security.CommentNextActionGuard;
import com.xhhao.comment.widget.security.CommentNextActionSecurityPolicy;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.codec.multipart.FilePart;
import org.springframework.http.codec.multipart.Part;
import org.springframework.stereotype.Service;
import org.springframework.util.MultiValueMap;
import org.springframework.web.reactive.function.server.ServerRequest;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.web.server.ServerWebInputException;
import reactor.core.publisher.Mono;

@Service
public class CommentNextImageUploadService {
    private final SettingConfigGetter settingConfigGetter;
    private final CommentNextActionGuard actionGuard;
    private final Map<ImageUploadProviderType, ImageUploadProvider> providers;

    public CommentNextImageUploadService(SettingConfigGetter settingConfigGetter,
                                         CommentNextActionGuard actionGuard,
                                         List<ImageUploadProvider> providers) {
        this.settingConfigGetter = settingConfigGetter;
        this.actionGuard = actionGuard;
        this.providers = providers.stream()
            .collect(Collectors.toUnmodifiableMap(ImageUploadProvider::type, Function.identity()));
    }

    public Mono<ImageUploadResult> upload(ServerRequest request) {
        return Mono.zip(request.multipartData().map(this::filePart), settingConfigGetter.getUploadConfig())
            .flatMap(tuple -> {
                var file = tuple.getT1();
                var config = tuple.getT2();
                return actionGuard.verify(request, CommentNextAction.IMAGE_UPLOAD, securityPolicy(config))
                    .flatMap(actor -> upload(file, config, actor));
            });
    }

    private Mono<ImageUploadResult> upload(FilePart file,
                                           SettingConfigGetter.UploadConfig config,
                                           CommentNextActionActor actor) {
        var providerType = providerType(config, actor);
        if (providerType == ImageUploadProviderType.DISABLED) {
            return Mono.error(new ResponseStatusException(
                HttpStatus.FORBIDDEN,
                "当前用户不允许上传图片"
            ));
        }

        var contentType = validateFile(file, config, actor);

        var provider = providers.get(providerType);
        if (provider == null) {
            return Mono.error(new ResponseStatusException(
                HttpStatus.INTERNAL_SERVER_ERROR,
                "图片上传服务未注册"
            ));
        }

        var maxSizeBytes = maxSizeBytes(config, actor);
        var uploadFile = new ContentTypedFilePart(file, contentType);
        return provider.upload(new ImageUploadRequest(
            actor,
            new SizeLimitedFilePart(uploadFile, maxSizeBytes),
            config,
            providerType
        ));
    }

    private FilePart filePart(MultiValueMap<String, Part> multipartData) {
        var part = multipartData.getFirst("file");
        if (part instanceof FilePart file) {
            return file;
        }
        throw new ServerWebInputException("Invalid multipart type of file");
    }

    private CommentNextActionSecurityPolicy securityPolicy(SettingConfigGetter.UploadConfig config) {
        var security = config.getSecurity();
        return new CommentNextActionSecurityPolicy(
            config.isEnabled(),
            config.isAllowAnonymousUpload(),
            security.getAnonymousRateLimit(),
            security.getAnonymousRateWindowSeconds(),
            security.getAuthenticatedRateLimit(),
            security.getAuthenticatedRateWindowSeconds(),
            security.isAntiHotlinkEnabled(),
            security.isAllowMissingOrigin(),
            security.allowedOriginValues(),
            security.isRateLimitEnabled()
        );
    }

    private ImageUploadProviderType providerType(SettingConfigGetter.UploadConfig config,
                                                CommentNextActionActor actor) {
        if (actor.anonymous()) {
            return config.isAllowAnonymousUpload()
                ? config.getAnonymousProvider()
                : ImageUploadProviderType.DISABLED;
        }
        return config.getAuthenticatedProvider();
    }

    private MediaType validateFile(FilePart file,
                                   SettingConfigGetter.UploadConfig config,
                                   CommentNextActionActor actor) {
        var contentType = CommentNextImageContentTypes.resolve(file);
        if (contentType == null
            || !CommentNextImageContentTypes.isImage(contentType)) {
            throw new ResponseStatusException(
                HttpStatus.BAD_REQUEST,
                "仅支持上传图片文件"
            );
        }

        var declaredSize = file.headers().getContentLength();
        if (declaredSize > maxSizeBytes(config, actor)) {
            throw new ResponseStatusException(
                HttpStatus.PAYLOAD_TOO_LARGE,
                "图片大小超过限制"
            );
        }
        return contentType;
    }

    private long maxSizeBytes(SettingConfigGetter.UploadConfig config, CommentNextActionActor actor) {
        return actor.anonymous()
            ? config.anonymousMaxSizeBytes()
            : config.authenticatedMaxSizeBytes();
    }
}
