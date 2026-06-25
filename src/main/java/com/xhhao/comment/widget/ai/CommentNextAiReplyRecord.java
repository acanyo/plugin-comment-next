package com.xhhao.comment.widget.ai;

import static io.swagger.v3.oas.annotations.media.Schema.RequiredMode.REQUIRED;

import io.swagger.v3.oas.annotations.media.Schema;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.ToString;
import run.halo.app.extension.AbstractExtension;
import run.halo.app.extension.GVK;

@Data
@ToString(callSuper = true)
@EqualsAndHashCode(callSuper = true)
@GVK(
    group = CommentNextAiReplyRecord.GROUP,
    version = CommentNextAiReplyRecord.VERSION,
    kind = CommentNextAiReplyRecord.KIND,
    plural = "aireplyrecords",
    singular = "aireplyrecord"
)
public class CommentNextAiReplyRecord extends AbstractExtension {

    public static final String GROUP = "api.commentnext.xhhao.com";

    public static final String VERSION = "v1alpha1";

    public static final String KIND = "AiReplyRecord";

    @Schema(requiredMode = REQUIRED)
    private Spec spec = new Spec();

    @Data
    @Schema(name = "CommentNextAiReplyRecordSpec")
    public static class Spec {

        @Schema(requiredMode = REQUIRED, allowableValues = {"COMMENT", "REPLY"})
        private String targetType = TargetType.COMMENT.name();

        @Schema(requiredMode = REQUIRED, allowableValues = {"AUTO", "MANUAL", "MENTION"})
        private String triggerType = TriggerType.AUTO.name();

        @Schema(requiredMode = REQUIRED, minLength = 1)
        private String targetName;

        @Schema(requiredMode = REQUIRED, minLength = 1)
        private String commentName;

        private String quoteReplyName;

        private String subject;

        private String authorName;

        private String sourceContent;

        @Schema(requiredMode = REQUIRED, minLength = 1)
        private String replyContent;

        private List<ReplyCandidate> replyCandidates = new ArrayList<>();

        private String replyStyle;

        private Integer selectedCandidateIndex;

        private Integer candidateCount;

        private String assistantName;

        private String assistantUserName;

        @Schema(requiredMode = REQUIRED, allowableValues = {"PENDING_REVIEW", "PUBLISHED", "REJECTED", "FAILED"})
        private String status = Status.PENDING_REVIEW.name();

        @Schema(requiredMode = REQUIRED, allowableValues = {"REVIEW", "AUTO"})
        private String publishMode = PublishMode.REVIEW.name();

        private String replyName;

        private String error;

        private Instant creationTime = Instant.now();

        private Instant generatedAt = Instant.now();

        private Instant reviewedAt;

        private Instant publishedAt;
    }

    @Data
    @Schema(name = "CommentNextAiReplyCandidate")
    public static class ReplyCandidate {

        @Schema(requiredMode = REQUIRED)
        private Integer index;

        private String style;

        @Schema(requiredMode = REQUIRED, minLength = 1)
        private String content;
    }

    public enum TargetType {
        COMMENT,
        REPLY
    }

    public enum TriggerType {
        AUTO,
        MANUAL,
        MENTION
    }

    public enum Status {
        PENDING_REVIEW,
        PUBLISHED,
        REJECTED,
        FAILED
    }

    public enum PublishMode {
        REVIEW,
        AUTO
    }
}
