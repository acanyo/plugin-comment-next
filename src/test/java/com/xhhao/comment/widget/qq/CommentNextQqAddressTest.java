package com.xhhao.comment.widget.qq;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class CommentNextQqAddressTest {

    @Test
    void parsesNumericQqEmail() {
        var address = CommentNextQqAddress.parse(" 123456@QQ.COM ").orElseThrow();

        assertEquals("123456", address.number());
        assertEquals("123456@qq.com", address.email());
    }

    @Test
    void rejectsNonQqAndNonNumericAddresses() {
        assertTrue(CommentNextQqAddress.parse("hello@qq.com").isEmpty());
        assertTrue(CommentNextQqAddress.parse("123456@example.com").isEmpty());
        assertTrue(CommentNextQqAddress.parse("012345@qq.com").isEmpty());
    }
}
