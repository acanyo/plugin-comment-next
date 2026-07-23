package com.xhhao.comment.widget.qq;

import com.fasterxml.jackson.databind.JsonNode;
import org.springframework.util.StringUtils;

final class CommentNextQqProfileResponseParser {

    private static final int MAX_NICKNAME_LENGTH = 64;

    private CommentNextQqProfileResponseParser() {
    }

    static String nickname(JsonNode response) {
        if (response == null || !response.isObject()) {
            return "";
        }

        var nickname = response.path("nickname").asText("").strip();
        if (!StringUtils.hasText(nickname) || nickname.length() > MAX_NICKNAME_LENGTH) {
            return "";
        }
        return nickname;
    }
}
