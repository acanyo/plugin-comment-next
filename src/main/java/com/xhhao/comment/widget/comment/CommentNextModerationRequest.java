package com.xhhao.comment.widget.comment;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

@JsonIgnoreProperties(ignoreUnknown = true)
public record CommentNextModerationRequest(
    Boolean top,
    Boolean featured,
    Integer priority
) {
}
