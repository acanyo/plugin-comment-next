package com.xhhao.comment.widget.ai.website;

import org.springframework.util.StringUtils;

public record CommentNextWebsiteMetadata(
    String website,
    String title,
    String description,
    String source
) {
    public static CommentNextWebsiteMetadata unavailable(String website) {
        return new CommentNextWebsiteMetadata(website, "", "", "");
    }

    public boolean available() {
        return StringUtils.hasText(title) || StringUtils.hasText(description);
    }
}
