package com.xhhao.comment.widget.avatar;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.Test;

class CommentNextWeAvatarUrlTest {

    @Test
    void hashesNormalizedEmailWithSha256() {
        assertEquals(
            "https://weavatar.com/avatar/"
                + "70e291390f894d53bb3be88862c123752f7155c493e4103b6212842de0e4b78e"
                + "?d=mp&f=webp&s=96",
            CommentNextWeAvatarUrl.forEmail(" 12345@QQ.COM ")
        );
    }

    @Test
    void returnsDefaultAvatarForBlankEmail() {
        assertEquals(
            "https://weavatar.com/avatar/?d=mp&f=webp&s=96",
            CommentNextWeAvatarUrl.forEmail(" ")
        );
    }
}
