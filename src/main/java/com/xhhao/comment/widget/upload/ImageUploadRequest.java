package com.xhhao.comment.widget.upload;

import com.xhhao.comment.widget.SettingConfigGetter;
import com.xhhao.comment.widget.security.CommentNextActionActor;
import org.springframework.http.codec.multipart.FilePart;

public record ImageUploadRequest(
    CommentNextActionActor actor,
    FilePart file,
    SettingConfigGetter.UploadConfig config,
    ImageUploadProviderType provider
) {
}
