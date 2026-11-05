package com.xhhao.comment.widget.security.email;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import lombok.Data;

/** Private settings for email checks on anonymous comment submissions. */
@Data
public class CommentNextEmailRestrictionConfig {

    public static final String GROUP = "emailRestriction";

    private static final Set<String> EXAMPLE_DOMAINS = Set.of(
        "example.com", "example.net", "example.org", "example", "invalid", "test", "localhost"
    );

    private boolean enabled = true;
    private boolean blockExampleEmails = true;
    private boolean protectAdminEmails = true;
    private List<BlockedEmail> blockedEmails = new ArrayList<>();

    public void setBlockedEmails(List<BlockedEmail> blockedEmails) {
        this.blockedEmails = blockedEmails == null ? new ArrayList<>() : blockedEmails;
    }

    boolean blocks(String normalizedEmail) {
        if (blockedEmails.stream().filter(item -> item != null)
            .anyMatch(item -> normalize(item.email()).equals(normalizedEmail))) {
            return true;
        }
        if (!blockExampleEmails) {
            return false;
        }
        var at = normalizedEmail.lastIndexOf('@');
        var domain = at < 0 ? "" : normalizedEmail.substring(at + 1);
        return EXAMPLE_DOMAINS.stream().anyMatch(example ->
            domain.equals(example) || domain.endsWith("." + example));
    }

    static String normalize(String value) {
        return value == null ? "" : value.strip().toLowerCase(Locale.ROOT);
    }

    public record BlockedEmail(String email) {
    }
}
