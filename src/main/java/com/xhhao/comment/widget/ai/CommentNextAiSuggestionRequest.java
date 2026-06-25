package com.xhhao.comment.widget.ai;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import lombok.Data;

@Data
@JsonIgnoreProperties(ignoreUnknown = true)
public class CommentNextAiSuggestionRequest {
    private String mode;

    private String content;

    private String variant;

    private String subject;

    private String replyToName;
}
