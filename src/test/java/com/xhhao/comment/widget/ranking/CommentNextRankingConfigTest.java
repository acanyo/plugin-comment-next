package com.xhhao.comment.widget.ranking;

import static org.assertj.core.api.Assertions.assertThat;

import com.xhhao.comment.utils.JsonUtils;
import com.xhhao.comment.widget.badge.CommentNextBadgeIdentity;
import org.junit.jupiter.api.Test;

class CommentNextRankingConfigTest {

    @Test
    void readsTheSettingsFormShapeAndKeepsUserAndEmailIdentitiesSeparate() throws Exception {
        var config = JsonUtils.createObjectMapper().readValue("""
            {
                "excludedUsers": [null, {"username": " host "}, {"username": ""}],
                "excludedEmails": [null, {"email": " Guest@Example.com "}, {"email": ""}]
            }
            """, CommentNextRankingConfig.class);

        assertThat(config.excludes(new CommentNextBadgeIdentity("USER", "host"), null)).isTrue();
        assertThat(config.excludes(new CommentNextBadgeIdentity("EMAIL", "guest@example.com"), null)).isTrue();
        assertThat(config.excludes(new CommentNextBadgeIdentity("USER", "member"), "GUEST@example.com")).isTrue();
        assertThat(config.excludes(new CommentNextBadgeIdentity("USER", "Guest@Example.com"), null)).isFalse();
        assertThat(config.excludes(new CommentNextBadgeIdentity("USER", "reader"), null)).isFalse();
    }

    @Test
    void missingOrNullSettingsLeaveEveryoneEligible() throws Exception {
        for (var json : new String[] {"{}", "{\"excludedUsers\": null, \"excludedEmails\": null}"}) {
            var config = JsonUtils.createObjectMapper().readValue(json, CommentNextRankingConfig.class);
            assertThat(config.excludes(new CommentNextBadgeIdentity("USER", "host"), "host@example.com")).isFalse();
            assertThat(config.excludes(new CommentNextBadgeIdentity("EMAIL", "guest@example.com"), null)).isFalse();
        }
    }
}
