package com.xhhao.comment.widget.identity;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;

class CommentNextEmailHashTest {

    @ParameterizedTest
    @ValueSource(strings = {"reader@example.com", "Reader@Example.com", " \tREADER@example.com\n"})
    void usesTheSameSha256ForEquivalentEmails(String email) {
        assertThat(CommentNextEmailHash.fromEmail(email))
            .isEqualTo("d108b279434fe1d54ac0f1da633564604b26c2e0e221d108b0fbadb87aba02c0");
    }

    @ParameterizedTest
    @NullAndEmptySource
    @ValueSource(strings = {" ", "\t\n"})
    void doesNotInventAnIdentityWhenNoEmailIsAvailable(String email) {
        assertThat(CommentNextEmailHash.fromEmail(email)).isNull();
    }
}
