package com.xhhao.comment.widget.security;

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
    group = CommentNextSecurityRule.GROUP,
    version = CommentNextSecurityRule.VERSION,
    kind = CommentNextSecurityRule.KIND,
    plural = "securityrules",
    singular = "securityrule"
)
public class CommentNextSecurityRule extends AbstractExtension {

    public static final String GROUP = "api.commentnext.xhhao.com";

    public static final String VERSION = "v1alpha1";

    public static final String KIND = "SecurityRule";

    @Schema(requiredMode = REQUIRED)
    private Spec spec = new Spec();

    @Data
    @Schema(name = "CommentNextSecurityRuleSpec")
    public static class Spec {

        private boolean enabled = true;

        @Schema(requiredMode = REQUIRED, allowableValues = {"BLACK", "GRAY"})
        private String listType = ListType.GRAY.name();

        @Schema(requiredMode = REQUIRED, allowableValues = {
            "IP",
            "EMAIL",
            "USERNAME",
            "KEYWORD",
            "DOMAIN",
            "UA"
        })
        private String field = Field.KEYWORD.name();

        @Schema(requiredMode = REQUIRED, allowableValues = {"EXACT", "CONTAINS", "REGEX"})
        private String matchType = MatchType.CONTAINS.name();

        @Schema(requiredMode = REQUIRED, minLength = 1)
        private String value;

        private String reason;

        private Integer priority = 0;
    }

    public enum ListType {
        BLACK,
        GRAY
    }

    public enum Field {
        IP,
        EMAIL,
        USERNAME,
        KEYWORD,
        DOMAIN,
        UA
    }

    public enum MatchType {
        EXACT,
        CONTAINS,
        REGEX
    }
}
