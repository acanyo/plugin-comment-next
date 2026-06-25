package com.xhhao.comment.widget.interaction;

import static io.swagger.v3.oas.annotations.media.Schema.RequiredMode.REQUIRED;

import io.swagger.v3.oas.annotations.media.Schema;
import java.time.Instant;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.ToString;
import run.halo.app.extension.AbstractExtension;
import run.halo.app.extension.GVK;

@Data
@ToString(callSuper = true)
@EqualsAndHashCode(callSuper = true)
@GVK(
    group = CommentNextReaction.GROUP,
    version = CommentNextReaction.VERSION,
    kind = CommentNextReaction.KIND,
    plural = "reactions",
    singular = "reaction"
)
public class CommentNextReaction extends AbstractExtension {

    public static final String GROUP = "api.commentnext.xhhao.com";

    public static final String VERSION = "v1alpha1";

    public static final String KIND = "Reaction";

    @Schema(requiredMode = REQUIRED)
    private Spec spec = new Spec();

    @Data
    @Schema(name = "CommentNextReactionSpec")
    public static class Spec {

        @Schema(requiredMode = REQUIRED, allowableValues = {"SUBJECT", "COMMENT", "REPLY"})
        private String targetType = TargetType.SUBJECT.name();

        @Schema(requiredMode = REQUIRED, minLength = 1)
        private String targetKey;

        @Schema(requiredMode = REQUIRED, minLength = 1)
        private String reaction;

        @Schema(requiredMode = REQUIRED, allowableValues = {"USER", "ANONYMOUS"})
        private String identityType = IdentityType.ANONYMOUS.name();

        @Schema(requiredMode = REQUIRED, minLength = 1)
        private String identityHash;

        private Instant creationTime = Instant.now();
    }

    public enum TargetType {
        SUBJECT,
        COMMENT,
        REPLY
    }

    public enum IdentityType {
        USER,
        ANONYMOUS
    }
}
