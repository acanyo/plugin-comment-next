package com.xhhao.comment.widget.qq;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import org.junit.jupiter.api.Test;

class CommentNextQqProfileClientTest {

    @Test
    void replacesOnlyTheConfiguredQqPlaceholder() {
        assertEquals(
            "https://example.com/profile?qq=123456",
            CommentNextQqProfileClient.profileUri(
                "123456",
                "https://example.com/profile?qq={qq}"
            ).toASCIIString()
        );
    }

    @Test
    void requiresQqPlaceholder() {
        assertThrows(
            IllegalArgumentException.class,
            () -> CommentNextQqProfileClient.profileUri(
                "123456",
                "https://example.com/profile"
            )
        );
    }
}
