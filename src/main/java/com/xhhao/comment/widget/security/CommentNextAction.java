package com.xhhao.comment.widget.security;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

@Getter
@RequiredArgsConstructor
public enum CommentNextAction {
    IMAGE_UPLOAD(
        "image-upload",
        "评论图片上传未开启",
        "请先登录后再上传图片",
        "上传过于频繁，请稍后再试"
    ),
    AI_GENERATE(
        "ai-generate",
        "AI 写作助手未开启",
        "请先登录后再使用 AI 写作",
        "AI 生成过于频繁，请稍后再试"
    );

    private final String key;

    private final String disabledMessage;

    private final String anonymousForbiddenMessage;

    private final String rateLimitedMessage;
}
