package com.xhhao.comment.widget.style;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;
import lombok.EqualsAndHashCode;
import run.halo.app.extension.AbstractExtension;
import run.halo.app.extension.GVK;

@Data
@EqualsAndHashCode(callSuper = true)
@GVK(group = "commentnext.xhhao.com", version = "v1alpha1", kind = "CommentStyleProfile",
    plural = "commentstyleprofiles", singular = "commentstyleprofile")
public class CommentNextStyleProfile extends AbstractExtension {

    @Schema(requiredMode = Schema.RequiredMode.REQUIRED)
    private Spec spec;

    public record Spec(CommentNextStyle draft, CommentNextStyle published, CommentNextStyle previous) {
        public Spec {
            draft = draft == null ? CommentNextStyle.DEFAULT : draft;
            published = published == null ? CommentNextStyle.DEFAULT : published;
        }
    }
}
