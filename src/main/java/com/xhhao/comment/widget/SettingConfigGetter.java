package com.xhhao.comment.widget;

import java.util.ArrayList;
import java.util.List;
import lombok.Data;
import lombok.Getter;
import lombok.experimental.Accessors;
import org.springframework.lang.NonNull;
import reactor.core.publisher.Mono;
import com.xhhao.comment.widget.upload.ImageUploadProviderType;
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

    /**
     * Never {@link Mono#empty()}.
     */
    Mono<UploadConfig> getUploadConfig();

    /**
     * Never {@link Mono#empty()}.
     */
    Mono<AiConfig> getAiConfig();

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
        private boolean showCommenterDevice = true;
        private boolean enablePrivateComment;
        private boolean showPrivateCommentBadge = true;
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
    class UploadConfig {
        public static final String GROUP = "upload";

        private boolean enabled;

        private boolean allowAnonymousUpload;

        @Getter(onMethod_ = @NonNull)
        private ImageUploadProviderType anonymousProvider = ImageUploadProviderType.DISABLED;

        @Getter(onMethod_ = @NonNull)
        private ImageUploadProviderType authenticatedProvider = ImageUploadProviderType.HALO_ATTACHMENT;

        private long anonymousMaxSizeKb = 2048;

        private long authenticatedMaxSizeKb = 5120;

        private String allowedContentTypes;

        @Getter(onMethod_ = @NonNull)
        private HaloAttachmentUploadConfig haloAttachment = HaloAttachmentUploadConfig.empty();

        @Getter(onMethod_ = @NonNull)
        private ImgBbUploadConfig imgBb = ImgBbUploadConfig.empty();

        @Getter(onMethod_ = @NonNull)
        private UploadSecurityConfig security = UploadSecurityConfig.empty();

        public UploadConfig setAnonymousProvider(ImageUploadProviderType anonymousProvider) {
            this.anonymousProvider =
                anonymousProvider == null ? ImageUploadProviderType.DISABLED : anonymousProvider;
            return this;
        }

        public UploadConfig setAuthenticatedProvider(ImageUploadProviderType authenticatedProvider) {
            this.authenticatedProvider =
                authenticatedProvider == null ? ImageUploadProviderType.HALO_ATTACHMENT : authenticatedProvider;
            return this;
        }

        public UploadConfig setHaloAttachment(HaloAttachmentUploadConfig haloAttachment) {
            this.haloAttachment =
                haloAttachment == null ? HaloAttachmentUploadConfig.empty() : haloAttachment;
            return this;
        }

        public UploadConfig setImgBb(ImgBbUploadConfig imgBb) {
            this.imgBb = imgBb == null ? ImgBbUploadConfig.empty() : imgBb;
            return this;
        }

        public UploadConfig setSecurity(UploadSecurityConfig security) {
            this.security = security == null ? UploadSecurityConfig.empty() : security;
            return this;
        }

        public long anonymousMaxSizeBytes() {
            return Math.max(1, anonymousMaxSizeKb) * 1024;
        }

        public long authenticatedMaxSizeBytes() {
            return Math.max(1, authenticatedMaxSizeKb) * 1024;
        }

        public static UploadConfig empty() {
            return new UploadConfig()
                .setEnabled(false)
                .setAllowAnonymousUpload(false)
                .setAnonymousProvider(ImageUploadProviderType.DISABLED)
                .setAuthenticatedProvider(ImageUploadProviderType.HALO_ATTACHMENT)
                .setHaloAttachment(HaloAttachmentUploadConfig.empty())
                .setImgBb(ImgBbUploadConfig.empty())
                .setSecurity(UploadSecurityConfig.empty());
        }
    }

    @Data
    class HaloAttachmentUploadConfig {
        private String policyName;
        private String groupName;

        public static HaloAttachmentUploadConfig empty() {
            return new HaloAttachmentUploadConfig();
        }
    }

    @Data
    class ImgBbUploadConfig {
        private String apiKey;
        private int expirationSeconds;

        public static ImgBbUploadConfig empty() {
            return new ImgBbUploadConfig();
        }
    }

    @Data
    class UploadSecurityConfig {
        private int anonymousRateLimit = 5;
        private int anonymousRateWindowSeconds = 60;
        private int authenticatedRateLimit = 30;
        private int authenticatedRateWindowSeconds = 60;

        public static UploadSecurityConfig empty() {
            return new UploadSecurityConfig();
        }
    }

    @Data
    @Accessors(chain = true)
    class AiConfig {
        public static final String GROUP = "ai";

        private boolean enabled;

        private boolean allowAnonymous;

        private String languageModelName;

        private int maxInputLength = 2000;

        private int maxOutputTokens = 512;

        private double temperature = 0.7;

        private String systemPrompt = defaultSystemPrompt();

        @Getter(onMethod_ = @NonNull)
        private AiSecurityConfig security = AiSecurityConfig.empty();

        public AiConfig setSecurity(AiSecurityConfig security) {
            this.security = security == null ? AiSecurityConfig.empty() : security;
            return this;
        }

        public AiConfig setSystemPrompt(String systemPrompt) {
            this.systemPrompt = systemPrompt == null || systemPrompt.isBlank()
                ? defaultSystemPrompt()
                : systemPrompt;
            return this;
        }

        public int normalizedMaxInputLength() {
            return Math.max(1, maxInputLength);
        }

        public int normalizedMaxOutputTokens() {
            return Math.max(1, maxOutputTokens);
        }

        public double normalizedTemperature() {
            if (temperature < 0) {
                return 0;
            }
            return Math.min(temperature, 2);
        }

        public static AiConfig empty() {
            return new AiConfig()
                .setEnabled(false)
                .setAllowAnonymous(false)
                .setMaxInputLength(2000)
                .setMaxOutputTokens(512)
                .setTemperature(0.7)
                .setSystemPrompt(defaultSystemPrompt())
                .setSecurity(AiSecurityConfig.empty());
        }

        private static String defaultSystemPrompt() {
            return "你是一个评论写作助手。你只输出可直接放进评论框的中文内容，不要解释过程，不要使用 Markdown 标题，不要编造事实。";
        }
    }

    @Data
    class AiSecurityConfig {
        private int anonymousRateLimit = 3;
        private int anonymousRateWindowSeconds = 60;
        private int authenticatedRateLimit = 20;
        private int authenticatedRateWindowSeconds = 60;

        public static AiSecurityConfig empty() {
            return new AiSecurityConfig();
        }
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

        public static BadgeConfig empty() {
            return new BadgeConfig();
        }
    }

    @Data
    class BadgeSetting {
        private String label;
        private String icon;
        private String color;
        private String title;

        public static BadgeSetting firstComment() {
            var badge = new BadgeSetting();
            badge.setLabel("首评");
            badge.setIcon("mdi:medal-outline");
            badge.setColor("#f59e0b");
            badge.setTitle("本文第一位评论者");
            return badge;
        }

        public static BadgeSetting admin() {
            var badge = new BadgeSetting();
            badge.setLabel("站长");
            badge.setIcon("mdi:shield-star-outline");
            badge.setColor("#3b82f6");
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
}
