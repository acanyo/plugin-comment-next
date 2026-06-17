package com.xhhao.comment.widget.badge;

import static io.swagger.v3.oas.annotations.media.Schema.RequiredMode.REQUIRED;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.ToString;
import run.halo.app.extension.AbstractExtension;
import run.halo.app.extension.GVK;

@Data
@ToString(callSuper = true)
@EqualsAndHashCode(callSuper = true)
@GVK(
    group = CommentNextBadgeAssignment.GROUP,
    version = CommentNextBadgeAssignment.VERSION,
    kind = CommentNextBadgeAssignment.KIND,
    plural = "badgeassignments",
    singular = "badgeassignment"
)
public class CommentNextBadgeAssignment extends AbstractExtension {

    public static final String GROUP = "api.commentnext.xhhao.com";

    public static final String VERSION = "v1alpha1";

    public static final String KIND = "BadgeAssignment";

    @Schema(requiredMode = REQUIRED)
    private Spec spec = new Spec();

    @Data
    @Schema(name = "CommentNextBadgeAssignmentSpec")
    public static class Spec {

        @Schema(requiredMode = REQUIRED)
        private String badgeName;

        @Schema(requiredMode = REQUIRED, allowableValues = {"USER", "EMAIL"})
        private String identityType = "USER";

        @Schema(requiredMode = REQUIRED)
        private String identity;

        private boolean enabled = true;
    }
}
