package com.xhhao.comment.widget.qq;

public record CommentNextQqProfile(String nickname) {

    static CommentNextQqProfile empty() {
        return new CommentNextQqProfile("");
    }
}
