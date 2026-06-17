package com.xhhao.comment.widget.emote;

import static io.swagger.v3.oas.annotations.media.Schema.RequiredMode.REQUIRED;

import io.swagger.v3.oas.annotations.media.Schema;
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
    group = CommentNextEmoteGroup.GROUP,
    version = CommentNextEmoteGroup.VERSION,
    kind = CommentNextEmoteGroup.KIND,
    plural = "emotegroups",
    singular = "emotegroup"
)
public class CommentNextEmoteGroup extends AbstractExtension {

    public static final String GROUP = "api.commentnext.xhhao.com";

    public static final String VERSION = "v1alpha1";

    public static final String KIND = "EmoteGroup";

    @Schema(requiredMode = REQUIRED)
    private Spec spec = new Spec();

    @Data
    @Schema(name = "CommentNextEmoteGroupSpec")
    public static class Spec {

        private boolean enabled = true;

        @Schema(requiredMode = REQUIRED, minLength = 1, maxLength = 64)
        private String displayName;

        @Schema(requiredMode = REQUIRED, allowableValues = {"emoticon", "image"})
        private String type = EmoteType.EMOTICON.value;

        @Schema(requiredMode = REQUIRED, allowableValues = {"DEFAULT", "CUSTOM"})
        private String sourceType = SourceType.CUSTOM.name();

        private String sourceUrl;

        private Integer priority = 0;

        private List<Item> items = new ArrayList<>();
    }

    @Data
    @Schema(name = "CommentNextEmoteItem")
    public static class Item {

        @Schema(requiredMode = REQUIRED)
        private String icon;

        private String text;
    }

    public enum SourceType {
        DEFAULT,
        CUSTOM
    }

    public enum EmoteType {
        EMOTICON("emoticon"),
        IMAGE("image");

        private final String value;

        EmoteType(String value) {
            this.value = value;
        }

        public String value() {
            return value;
        }
    }
}
