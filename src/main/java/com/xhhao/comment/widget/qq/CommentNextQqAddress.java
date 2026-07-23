package com.xhhao.comment.widget.qq;

import java.util.Locale;
import java.util.Optional;
import java.util.regex.Pattern;

record CommentNextQqAddress(String number, String email) {

    private static final Pattern QQ_EMAIL_PATTERN = Pattern.compile(
        "^([1-9][0-9]{4,11})@qq\\.com$",
        Pattern.CASE_INSENSITIVE
    );

    static Optional<CommentNextQqAddress> parse(String value) {
        if (value == null || value.isBlank()) {
            return Optional.empty();
        }

        var normalized = value.strip().toLowerCase(Locale.ROOT);
        var matcher = QQ_EMAIL_PATTERN.matcher(normalized);
        if (!matcher.matches()) {
            return Optional.empty();
        }
        return Optional.of(new CommentNextQqAddress(matcher.group(1), normalized));
    }
}
