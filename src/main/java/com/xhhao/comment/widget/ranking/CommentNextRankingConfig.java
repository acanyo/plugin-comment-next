package com.xhhao.comment.widget.ranking;

import com.xhhao.comment.widget.badge.CommentNextBadgeIdentity;
import java.util.ArrayList;
import java.util.List;
import lombok.Data;
import org.springframework.util.StringUtils;

/** Private settings controlling participation in the public reader ranking. */
@Data
public class CommentNextRankingConfig {

    public static final String GROUP = "ranking";

    private List<ExcludedUser> excludedUsers = new ArrayList<>();
    private List<ExcludedEmail> excludedEmails = new ArrayList<>();

    public void setExcludedUsers(List<ExcludedUser> excludedUsers) {
        this.excludedUsers = excludedUsers == null ? List.of() : excludedUsers;
    }

    public void setExcludedEmails(List<ExcludedEmail> excludedEmails) {
        this.excludedEmails = excludedEmails == null ? List.of() : excludedEmails;
    }

    public boolean excludes(CommentNextBadgeIdentity identity, String memberEmail) {
        if (identity.user() && excludedUsers.stream().anyMatch(entry -> entry != null
            && StringUtils.hasText(entry.username())
            && identity.identity().equals(entry.username().trim()))) {
            return true;
        }
        var email = identity.user() ? memberEmail : identity.identity();
        return StringUtils.hasText(email) && excludedEmails.stream().anyMatch(entry -> entry != null
            && StringUtils.hasText(entry.email())
            && email.trim().equalsIgnoreCase(entry.email().trim()));
    }

    public record ExcludedUser(String username) {
    }

    public record ExcludedEmail(String email) {
    }
}
