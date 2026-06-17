package com.xhhao.comment.widget.ai;

import java.util.Arrays;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

enum CommentNextAiMode {
    POLISH("polish", "润色建议", true),
    EXPAND("expand", "补充观点", true),
    QUESTION("question", "提问角度", true),
    REPLY("reply", "生成回复", false),
    SUMMARY("summary", "总结观点", true);

    private final String key;

    private final String label;

    private final boolean contentRequired;

    CommentNextAiMode(String key, String label, boolean contentRequired) {
        this.key = key;
        this.label = label;
        this.contentRequired = contentRequired;
    }

    public String key() {
        return key;
    }

    public String label() {
        return label;
    }

    public boolean contentRequired() {
        return contentRequired;
    }

    public static CommentNextAiMode from(String key) {
        return Arrays.stream(values())
            .filter(mode -> mode.key.equalsIgnoreCase(key == null ? "" : key.trim()))
            .findFirst()
            .orElseThrow(() -> new ResponseStatusException(
                HttpStatus.BAD_REQUEST,
                "不支持的 AI 写作模式"
            ));
    }
}
