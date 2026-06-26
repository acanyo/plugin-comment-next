package com.xhhao.comment.widget.report;

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
    group = CommentNextReport.GROUP,
    version = CommentNextReport.VERSION,
    kind = CommentNextReport.KIND,
    plural = "reports",
    singular = "report"
)
public class CommentNextReport extends AbstractExtension {

    public static final String GROUP = "api.commentnext.xhhao.com";

    public static final String VERSION = "v1alpha1";

    public static final String KIND = "Report";

    @Schema(requiredMode = REQUIRED)
    private Spec spec = new Spec();

    @Data
    @Schema(name = "CommentNextReportSpec")
    public static class Spec {

        @Schema(requiredMode = REQUIRED, allowableValues = {"COMMENT", "REPLY"})
        private String targetType = TargetType.COMMENT.name();

        @Schema(requiredMode = REQUIRED, minLength = 1)
        private String targetName;

        @Schema(allowableValues = {"SPAM", "AD", "ABUSE", "PORN", "ILLEGAL", "OTHER"})
        private String reason = Reason.OTHER.name();

        @Schema(minLength = 1, maxLength = 500)
        private String description;

        @Schema(requiredMode = REQUIRED, allowableValues = {"USER", "ANONYMOUS"})
        private String identityType = IdentityType.ANONYMOUS.name();

        @Schema(requiredMode = REQUIRED, minLength = 1)
        private String identityHash;

        private Instant creationTime = Instant.now();
    }

    public enum TargetType {
        COMMENT,
        REPLY
    }

    public enum Reason {
        SPAM,
        AD,
        ABUSE,
        PORN,
        ILLEGAL,
        OTHER
    }

    public enum IdentityType {
        USER,
        ANONYMOUS
    }
}
