package com.xhhao.comment.widget.avatar;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.Locale;

public final class CommentNextWeAvatarUrl {

    private static final String BASE_URL = "https://weavatar.com/avatar/";

    private static final String QUERY = "?d=mp&f=webp&s=96";

    private CommentNextWeAvatarUrl() {
    }

    public static String forEmail(String email) {
        if (email == null || email.isBlank()) {
            return defaultAvatar();
        }
        var normalized = email.strip().toLowerCase(Locale.ROOT);
        return BASE_URL + sha256(normalized) + QUERY;
    }

    public static String defaultAvatar() {
        return BASE_URL + QUERY;
    }

    private static String sha256(String value) {
        try {
            var digest = MessageDigest.getInstance("SHA-256");
            return HexFormat.of().formatHex(digest.digest(value.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 is unavailable", e);
        }
    }
}
