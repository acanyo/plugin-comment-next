package com.xhhao.comment.widget.badge;

import static io.swagger.v3.oas.annotations.media.Schema.RequiredMode.REQUIRED;

import com.xhhao.comment.widget.comment.CommentNextBadge;
import io.swagger.v3.oas.annotations.media.Schema;
import java.util.ArrayList;
import java.util.List;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.ToString;
import org.springframework.lang.NonNull;
import run.halo.app.extension.AbstractExtension;
import run.halo.app.extension.GVK;

@Data
@ToString(callSuper = true)
@EqualsAndHashCode(callSuper = true)
@GVK(
    group = CommentNextBadgeProfile.GROUP,
    version = CommentNextBadgeProfile.VERSION,
    kind = CommentNextBadgeProfile.KIND,
    plural = "badgeprofiles",
    singular = "badgeprofile"
)
public class CommentNextBadgeProfile extends AbstractExtension {

    public static final String GROUP = "api.commentnext.xhhao.com";

    public static final String VERSION = "v1alpha1";

    public static final String KIND = "BadgeProfile";

    @Schema(requiredMode = REQUIRED)
    private Spec spec = new Spec();

    @Data
    @Schema(name = "CommentNextBadgeProfileSpec")
    public static class Spec {

        @Schema(requiredMode = REQUIRED, allowableValues = {"USER", "EMAIL"})
        private String identityType;

        @Schema(requiredMode = REQUIRED)
        private String identity;

        private long activeCommentCount;

        @Getter(onMethod_ = @NonNull)
        private List<CommentNextBadge> badges = new ArrayList<>();

        public void setBadges(List<CommentNextBadge> badges) {
            this.badges = badges == null ? new ArrayList<>() : new ArrayList<>(badges);
        }
    }
}
