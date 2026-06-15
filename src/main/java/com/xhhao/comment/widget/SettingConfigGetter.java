package com.xhhao.comment.widget;

import java.util.ArrayList;
import java.util.List;
import lombok.Data;
import lombok.Getter;
import lombok.experimental.Accessors;
import org.springframework.lang.NonNull;
import reactor.core.publisher.Mono;
import com.xhhao.comment.widget.captcha.CaptchaType;

public interface SettingConfigGetter {

    /**
     * Never {@link Mono#empty()}.
     */
    Mono<BasicConfig> getBasicConfig();

    /**
     * Never {@link Mono#empty()}.
     */
    Mono<AvatarConfig> getAvatarConfig();

    /**
     * Never {@link Mono#empty()}.
     */
    Mono<SecurityConfig> getSecurityConfig();

    /**
     * Never {@link Mono#empty()}.
     */
    Mono<BadgeConfig> getBadgeConfig();

    @Data
    @Accessors(chain = true)
    class SecurityConfig {
        public static final String GROUP = "security";

        @Getter(onMethod_ = @NonNull)
        private CaptchaConfig captcha = CaptchaConfig.empty();

        public SecurityConfig setCaptcha(CaptchaConfig captcha) {
            this.captcha = (captcha == null ? CaptchaConfig.empty() : captcha);
            return this;
        }

        public static SecurityConfig empty() {
            return new SecurityConfig()
                .setCaptcha(CaptchaConfig.empty());
        }
    }

    @Data
    @Accessors(chain = true)
    class CaptchaConfig {

        private boolean anonymousCommentCaptcha;

        @Getter(onMethod_ = @NonNull)
        private CaptchaType type = CaptchaType.ALPHANUMERIC;

        private boolean ignoreCase = true;

        private int captchaLength = 4;

        private int arithmeticRange = 90;

        public CaptchaConfig setType(CaptchaType type) {
            this.type = (type == null ? CaptchaType.ALPHANUMERIC : type);
            return this;
        }

        public static CaptchaConfig empty() {
            return new CaptchaConfig();
        }
    }

    @Data
    class BasicConfig {
        public static final String GROUP = "basic";
        private int size;
        private int replySize;
        private boolean withReplies;
        private int withReplySize;
    }

    @Data
    class AvatarConfig {
        public static final String GROUP = "avatar";
        private boolean enable;
        private String provider;
        private String providerMirror;
        private String policy;
    }

    @Data
    @Accessors(chain = true)
    class BadgeConfig {
        public static final String GROUP = "badge";

        private boolean enableFirstCommentBadge = true;

        @Getter(onMethod_ = @NonNull)
        private BadgeSetting firstCommentBadge = BadgeSetting.firstComment();

        @Getter(onMethod_ = @NonNull)
        private BadgeSetting adminBadge = BadgeSetting.admin();

        @Getter(onMethod_ = @NonNull)
        private List<BadgeIdentifier> adminIdentifiers = new ArrayList<>();

        @Getter(onMethod_ = @NonNull)
        private List<BadgeLevelRule> levelRules = new ArrayList<>();

        @Getter(onMethod_ = @NonNull)
        private List<BadgeIdentityRule> customRules = new ArrayList<>();

        public BadgeConfig setFirstCommentBadge(BadgeSetting firstCommentBadge) {
            this.firstCommentBadge = firstCommentBadge == null ? BadgeSetting.firstComment() : firstCommentBadge;
            return this;
        }

        public BadgeConfig setAdminBadge(BadgeSetting adminBadge) {
            this.adminBadge = adminBadge == null ? BadgeSetting.admin() : adminBadge;
            return this;
        }

        public BadgeConfig setAdminIdentifiers(List<BadgeIdentifier> adminIdentifiers) {
            this.adminIdentifiers = adminIdentifiers == null ? new ArrayList<>() : adminIdentifiers;
            return this;
        }

        public BadgeConfig setLevelRules(List<BadgeLevelRule> levelRules) {
            this.levelRules = levelRules == null ? new ArrayList<>() : levelRules;
            return this;
        }

        public BadgeConfig setCustomRules(List<BadgeIdentityRule> customRules) {
            this.customRules = customRules == null ? new ArrayList<>() : customRules;
            return this;
        }

        public static BadgeConfig empty() {
            return new BadgeConfig();
        }
    }

    @Data
    class BadgeSetting {
        private String label;
        private String icon;
        private String title;

        public static BadgeSetting firstComment() {
            var badge = new BadgeSetting();
            badge.setLabel("首评");
            badge.setIcon("medal");
            badge.setTitle("本文第一位评论者");
            return badge;
        }

        public static BadgeSetting admin() {
            var badge = new BadgeSetting();
            badge.setLabel("站长");
            badge.setIcon("shield");
            badge.setTitle("站点管理员");
            return badge;
        }
    }

    @Data
    class BadgeIdentifier {
        private String username;
        private String email;
        private String displayName;
    }

    @Data
    class BadgeLevelRule {
        private String id;
        private String label;
        private int minComments;
        private String icon;
        private String title;
    }

    @Data
    class BadgeIdentityRule {
        private String id;
        private String label;

        @Getter(onMethod_ = @NonNull)
        private BadgeIdentifier match = new BadgeIdentifier();

        private String icon;
        private String title;

        public BadgeIdentityRule setMatch(BadgeIdentifier match) {
            this.match = match == null ? new BadgeIdentifier() : match;
            return this;
        }
    }
}
