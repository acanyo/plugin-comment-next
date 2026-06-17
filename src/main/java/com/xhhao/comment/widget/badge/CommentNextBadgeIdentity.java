package com.xhhao.comment.widget.badge;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.HexFormat;
import java.util.Locale;
import java.util.Optional;
import org.springframework.util.StringUtils;
import run.halo.app.core.extension.User;
import run.halo.app.core.extension.content.Comment;

public record CommentNextBadgeIdentity(String type, String identity) {

    public static final String TYPE_USER = "USER";

    public static final String TYPE_EMAIL = "EMAIL";

    private static final String PROFILE_NAME_PREFIX = "comment-next-profile-";

    public CommentNextBadgeIdentity {
        type = StringUtils.hasText(type) ? type.trim().toUpperCase(Locale.ROOT) : "";
        identity = normalize(type, identity);
    }

    public static Optional<CommentNextBadgeIdentity> of(String type, String identity) {
        var badgeIdentity = new CommentNextBadgeIdentity(type, identity);
        if (!badgeIdentity.valid()) {
            return Optional.empty();
        }
        return Optional.of(badgeIdentity);
    }

    public static Optional<CommentNextBadgeIdentity> fromOwner(Comment.CommentOwner owner) {
        if (owner == null) {
            return Optional.empty();
        }
        if (User.KIND.equals(owner.getKind())) {
            return user(owner.getName());
        }
        if (Comment.CommentOwner.KIND_EMAIL.equals(owner.getKind())) {
            return email(owner.getName());
        }
        return Optional.empty();
    }

    public static Optional<CommentNextBadgeIdentity> user(String identity) {
        return of(TYPE_USER, identity);
    }

    public static Optional<CommentNextBadgeIdentity> email(String email) {
        return of(TYPE_EMAIL, email);
    }

    public boolean user() {
        return TYPE_USER.equals(type);
    }

    public String profileName() {
        return PROFILE_NAME_PREFIX + sha256(type + ":" + identity);
    }

    private boolean valid() {
        return (TYPE_USER.equals(type) || TYPE_EMAIL.equals(type))
            && StringUtils.hasText(identity);
    }

    private static String normalize(String type, String identity) {
        if (!StringUtils.hasText(identity)) {
            return "";
        }
        var normalized = identity.trim();
        if (TYPE_EMAIL.equals(type)) {
            return normalized.toLowerCase(Locale.ROOT);
        }
        return normalized;
    }

    private static String sha256(String value) {
        try {
            var digest = MessageDigest.getInstance("SHA-256");
            return HexFormat.of().formatHex(digest.digest(value.getBytes(StandardCharsets.UTF_8)));
        } catch (Exception e) {
            return Integer.toHexString(value.hashCode());
        }
    }
}
