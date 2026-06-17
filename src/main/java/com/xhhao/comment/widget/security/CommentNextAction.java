package com.xhhao.comment.widget.security;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

@Getter
@RequiredArgsConstructor
public enum CommentNextAction {
    IMAGE_UPLOAD("image-upload");

    private final String key;
}
