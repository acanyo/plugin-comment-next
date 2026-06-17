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
    group = CommentNextBadgeRule.GROUP,
    version = CommentNextBadgeRule.VERSION,
    kind = CommentNextBadgeRule.KIND,
    plural = "badgerules",
    singular = "badgerule"
)
public class CommentNextBadgeRule extends AbstractExtension {

    public static final String GROUP = "api.commentnext.xhhao.com";

    public static final String VERSION = "v1alpha1";

    public static final String KIND = "BadgeRule";

    @Schema(requiredMode = REQUIRED)
    private Spec spec = new Spec();

    @Data
    @Schema(name = "CommentNextBadgeRuleSpec")
    public static class Spec {

        @Schema(requiredMode = REQUIRED, allowableValues = {"USER", "LEVEL"})
        private String type = BadgeRuleType.USER.name();

        private boolean enabled = true;

        @Schema(requiredMode = REQUIRED, minLength = 1, maxLength = 32)
        private String label;

        private String icon;

        private String color;

        private String title;

        /**
         * Used when type is LEVEL. The count is maintained outside list queries.
         */
        private Integer minComments;
    }

    public enum BadgeRuleType {
        USER,
        LEVEL
    }
}
