package com.xhhao.comment.widget.identity;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.Locale;

/** Shared email hash used by public comments, rankings, and avatars. */
public final class CommentNextEmailHash {

    private CommentNextEmailHash() {
    }

    /** Returns a lowercase SHA-256 hex digest, or null when no email is available. */
    public static String fromEmail(String email) {
        if (email == null || email.isBlank()) {
            return null;
        }
        var normalized = email.strip().toLowerCase(Locale.ROOT);
        try {
            var digest = MessageDigest.getInstance("SHA-256");
            return HexFormat.of().formatHex(digest.digest(normalized.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 is unavailable", e);
        }
    }
}
