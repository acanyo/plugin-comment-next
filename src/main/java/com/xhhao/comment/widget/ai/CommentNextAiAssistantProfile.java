package com.xhhao.comment.widget.ai;

import org.springframework.util.StringUtils;

public record CommentNextAiAssistantProfile(String username, String displayName) {

    public String mentionName() {
        return displayName.startsWith("@") ? displayName : "@" + displayName;
    }

    public boolean hasUser() {
        return StringUtils.hasText(username);
    }
}
