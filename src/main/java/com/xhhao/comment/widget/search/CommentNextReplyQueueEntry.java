package com.xhhao.comment.widget.search;

import static io.swagger.v3.oas.annotations.media.Schema.RequiredMode.REQUIRED;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;
import lombok.EqualsAndHashCode;
import run.halo.app.extension.AbstractExtension;
import run.halo.app.extension.GVK;

@Data
@EqualsAndHashCode(callSuper = true)
@GVK(
    group = "api.commentnext.xhhao.com",
    version = "v1alpha1",
    kind = "ReplyQueueEntry",
    plural = "replyqueueentries",
    singular = "replyqueueentry"
)
public class CommentNextReplyQueueEntry extends AbstractExtension {

    @Schema(requiredMode = REQUIRED)
    private Spec spec = new Spec();

    @Data
    @Schema(name = "CommentNextReplyQueueEntrySpec")
    public static class Spec {

        @Schema(requiredMode = REQUIRED, minLength = 1)
        private String username;

        @Schema(requiredMode = REQUIRED, minLength = 1)
        private String commentName;

        private boolean dismissed;
    }
}
