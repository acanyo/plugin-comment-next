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

        @Schema(requiredMode = REQUIRED, allowableValues = {"emoticon", "image", "lottie"})
        private String type = EmoteType.EMOTICON.value;

        @Schema(requiredMode = REQUIRED, allowableValues = {"OWO", "LOTTIE"})
        private String provider = Provider.OWO.name();

        @Schema(requiredMode = REQUIRED, allowableValues = {"DEFAULT", "CUSTOM", "PLUGIN"})
        private String sourceType = SourceType.CUSTOM.name();

        private String sourceUrl;

        /** External catalog identifier, such as a plugin-lottie group name. */
        private String sourceRef;

        private Integer priority = 0;

        private List<Item> items = new ArrayList<>();
    }

    @Data
    @Schema(name = "CommentNextEmoteItem")
    public static class Item {

        /** OwO items store their original icon (plain text or img HTML). */
        private String icon;

        private String text;

        /** Lottie items use structured fields instead of embedding HTML. */
        private String animationName;

        private String contentUrl;

        private String format;

        private LottieDefaults defaults;
    }

    @Data
    @Schema(name = "CommentNextLottieDefaults")
    public static class LottieDefaults {

        private Integer width = 160;
        private Integer height = 160;
        private Boolean autoplay = true;
        private Boolean loop = true;
        private Double speed = 1.0;
        private String fit = "contain";
        private String align = "center";
        private Boolean controls = false;
        private Boolean hoverPlay = false;
        private Boolean freezeOnOffscreen = true;
        private String ariaLabel;
    }

    public enum SourceType {
        DEFAULT,
        CUSTOM,
        PLUGIN
    }

    public enum Provider {
        OWO,
        LOTTIE
    }

    public enum EmoteType {
        EMOTICON("emoticon"),
        IMAGE("image"),
        LOTTIE("lottie");

        private final String value;

        EmoteType(String value) {
            this.value = value;
        }

        public String value() {
            return value;
        }
    }
}
