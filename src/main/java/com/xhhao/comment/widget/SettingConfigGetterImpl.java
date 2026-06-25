package com.xhhao.comment.widget;

import com.xhhao.comment.widget.security.CommentNextSecurityReviewAction;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Mono;
import run.halo.app.plugin.ReactiveSettingFetcher;

@Component
@RequiredArgsConstructor
public class SettingConfigGetterImpl implements SettingConfigGetter {
    private final ReactiveSettingFetcher settingFetcher;

    @Override
    public Mono<BasicConfig> getBasicConfig() {
        return settingFetcher.fetch(BasicConfig.GROUP, BasicConfig.class)
            .defaultIfEmpty(new BasicConfig());
    }

    @Override
    public Mono<SecurityConfig> getSecurityConfig() {
        return settingFetcher.fetch(SecurityConfig.GROUP, SecurityConfig.class)
            .defaultIfEmpty(SecurityConfig.empty())
            .onErrorReturn(SecurityConfig.empty());
    }

    @Override
    public Mono<BadgeConfig> getBadgeConfig() {
        return settingFetcher.fetch(BadgeConfig.GROUP, BadgeConfig.class)
            .defaultIfEmpty(BadgeConfig.empty());
    }

    @Override
    public Mono<UploadConfig> getUploadConfig() {
        return settingFetcher.fetch(UploadConfig.GROUP, UploadConfig.class)
            .defaultIfEmpty(UploadConfig.empty());
    }

    @Override
    public Mono<ReactionConfig> getReactionConfig() {
        return settingFetcher.fetch(ReactionConfig.GROUP, ReactionConfig.class)
            .defaultIfEmpty(ReactionConfig.empty());
    }

    @Override
    public Mono<AiConfig> getAiConfig() {
        var aiConfig = settingFetcher.fetch(AiConfig.GROUP, AiConfig.class)
            .map(Optional::of)
            .defaultIfEmpty(Optional.empty());
        var aiPromptSettings = fetchAiPromptSettings();

        return Mono.zip(aiConfig, aiPromptSettings)
            .map(tuple -> applyAiPromptSettings(tuple.getT1().orElseGet(AiConfig::empty), tuple.getT2()));
    }

    @Override
    public Mono<AiConfig> getAiModerationConfig() {
        var aiConfig = settingFetcher.fetch(AiConfig.GROUP, AiConfig.class)
            .map(Optional::of)
            .defaultIfEmpty(Optional.empty());
        var aiReviewSettings = settingFetcher.fetch(AiReviewSettings.GROUP, AiReviewSettings.class)
            .map(Optional::of)
            .defaultIfEmpty(Optional.empty());
        var aiPromptSettings = fetchAiPromptSettings();

        return Mono.zip(aiConfig, aiReviewSettings, aiPromptSettings)
            .map(tuple -> {
                var baseConfig = applyAiPromptSettings(tuple.getT1().orElseGet(AiConfig::empty), tuple.getT3());
                var reviewConfig = tuple.getT2()
                    .map(settings -> applyAiReviewSettings(baseConfig, settings))
                    .orElseGet(AiConfig::empty);
                return applyAiReviewPromptSettings(reviewConfig, tuple.getT3());
            });
    }

    @Override
    public Mono<AiConfig> getAiAutoReplyConfig() {
        var aiConfig = settingFetcher.fetch(AiConfig.GROUP, AiConfig.class)
            .map(Optional::of)
            .defaultIfEmpty(Optional.empty());
        var autoReplySettings = settingFetcher.fetch(AiAutoReplySettings.GROUP, AiAutoReplySettings.class)
            .map(Optional::of)
            .defaultIfEmpty(Optional.empty());
        var aiPromptSettings = fetchAiPromptSettings();

        return Mono.zip(aiConfig, autoReplySettings, aiPromptSettings)
            .map(tuple -> {
                var baseConfig = applyAiPromptSettings(tuple.getT1().orElseGet(AiConfig::empty), tuple.getT3());
                var autoReplyConfig = tuple.getT2()
                    .map(settings -> applyAiAutoReplySettings(baseConfig, settings))
                    .orElseGet(AiConfig::empty);
                return applyAiAutoReplyPromptSettings(autoReplyConfig, tuple.getT3());
            });
    }

    private Mono<Optional<AiPromptSettings>> fetchAiPromptSettings() {
        return settingFetcher.fetch(AiPromptSettings.GROUP, AiPromptSettings.class)
            .map(Optional::of)
            .defaultIfEmpty(Optional.empty());
    }

    private AiConfig applyAiPromptSettings(AiConfig baseConfig,
                                           Optional<AiPromptSettings> promptSettings) {
        if (promptSettings.isEmpty()) {
            return baseConfig;
        }

        var promptConfig = AiPromptConfig.empty();
        promptConfig.setSystemPrompt(firstText(
            promptSettings.get().getSystemPrompt(),
            baseConfig.getSystemPrompt(),
            AiConfig.defaultSystemPrompt()
        ));
        baseConfig.setPrompt(promptConfig);
        return baseConfig;
    }

    private AiConfig applyAiReviewPromptSettings(AiConfig baseConfig,
                                                 Optional<AiPromptSettings> promptSettings) {
        if (promptSettings.isEmpty()) {
            return baseConfig;
        }

        var reviewConfig = baseConfig.getReview();
        reviewConfig.setRolePrompt(firstText(
            promptSettings.get().getReviewRolePrompt(),
            reviewConfig.getRolePrompt(),
            AiReviewConfig.defaultRolePrompt()
        ));
        baseConfig.setReview(reviewConfig);
        return baseConfig;
    }

    private AiConfig applyAiAutoReplyPromptSettings(AiConfig baseConfig,
                                                    Optional<AiPromptSettings> promptSettings) {
        if (promptSettings.isEmpty()) {
            return baseConfig;
        }

        var autoReplyConfig = baseConfig.getAutoReply();
        autoReplyConfig.setRolePrompt(firstText(
            promptSettings.get().getAutoReplyRolePrompt(),
            autoReplyConfig.getRolePrompt(),
            AiAutoReplyConfig.defaultRolePrompt()
        ));
        baseConfig.setAutoReply(autoReplyConfig);
        return baseConfig;
    }

    private AiConfig applyAiReviewSettings(AiConfig baseConfig, AiReviewSettings settings) {
        var enabled = Boolean.TRUE.equals(settings.getEnabled());
        var reviewConfig = AiReviewConfig.empty();
        var fallbackReviewConfig = baseConfig.getReview();

        reviewConfig.setAutoReviewEnabled(enabled);
        reviewConfig.setAction(firstText(
            settings.getAction(),
            fallbackReviewConfig.getAction(),
            CommentNextSecurityReviewAction.PENDING_REVIEW.name()
        ));
        reviewConfig.setReviewComments(firstBoolean(
            settings.getReviewComments(),
            fallbackReviewConfig.getReviewComments(),
            true
        ));
        reviewConfig.setReviewReplies(firstBoolean(
            settings.getReviewReplies(),
            fallbackReviewConfig.getReviewReplies(),
            true
        ));
        reviewConfig.setMaxInputLength(firstInteger(
            settings.getMaxInputLength(),
            fallbackReviewConfig.getMaxInputLength()
        ));
        reviewConfig.setConfidenceThreshold(firstDouble(
            settings.getConfidenceThreshold(),
            fallbackReviewConfig.getConfidenceThreshold()
        ));
        reviewConfig.setRolePrompt(firstText(
            settings.getRolePrompt(),
            fallbackReviewConfig.getRolePrompt(),
            AiReviewConfig.defaultRolePrompt()
        ));
        reviewConfig.setNotifyAdmins(firstBoolean(
            settings.getNotifyAdmins(),
            fallbackReviewConfig.getNotifyAdmins(),
            true
        ));
        reviewConfig.setNotifyCommenter(firstBoolean(
            settings.getNotifyCommenter(),
            fallbackReviewConfig.getNotifyCommenter(),
            true
        ));
        reviewConfig.setAdminNotifyUsers(firstNotifyUsers(
            settings.getAdminNotifyUsers(),
            fallbackReviewConfig.getAdminNotifyUsers()
        ));

        baseConfig.setEnabled(enabled);
        baseConfig.setAutoReviewEnabled(enabled);
        baseConfig.setReview(reviewConfig);
        baseConfig.setModel(mergeAiModel(settings.getModel(), baseConfig.getModel()));
        return baseConfig;
    }

    private AiConfig applyAiAutoReplySettings(AiConfig baseConfig, AiAutoReplySettings settings) {
        var enabled = Boolean.TRUE.equals(settings.getEnabled());
        var autoReplyConfig = AiAutoReplyConfig.empty();
        var fallbackAutoReplyConfig = baseConfig.getAutoReply();

        autoReplyConfig.setEnabled(enabled);
        autoReplyConfig.setReplyComments(firstBoolean(
            settings.getReplyComments(),
            fallbackAutoReplyConfig.getReplyComments(),
            true
        ));
        autoReplyConfig.setReplyReplies(firstBoolean(
            settings.getReplyReplies(),
            fallbackAutoReplyConfig.getReplyReplies(),
            false
        ));
        autoReplyConfig.setPublishMode(firstText(
            settings.getPublishMode(),
            fallbackAutoReplyConfig.getPublishMode(),
            AiAutoReplyConfig.PUBLISH_MODE_REVIEW
        ));
        autoReplyConfig.setMaxInputLength(firstInteger(
            settings.getMaxInputLength(),
            fallbackAutoReplyConfig.getMaxInputLength()
        ));
        autoReplyConfig.setRolePrompt(firstText(
            settings.getRolePrompt(),
            fallbackAutoReplyConfig.getRolePrompt(),
            AiAutoReplyConfig.defaultRolePrompt()
        ));

        baseConfig.setEnabled(enabled);
        baseConfig.setAutoReply(autoReplyConfig);
        var modelConfig = mergeAiModel(settings.getModel(), baseConfig.getModel());
        if (settings.getMaxInputLength() != null) {
            modelConfig.setMaxInputLength(settings.getMaxInputLength());
        }
        baseConfig.setModel(modelConfig);
        return baseConfig;
    }

    private AiModelConfig mergeAiModel(AiModelConfig override, AiModelConfig fallback) {
        var modelConfig = AiModelConfig.empty();
        var safeOverride = override == null ? AiModelConfig.empty() : override;
        var safeFallback = fallback == null ? AiModelConfig.empty() : fallback;

        modelConfig.setLanguageModelName(firstText(
            safeOverride.getLanguageModelName(),
            safeFallback.getLanguageModelName(),
            null
        ));
        modelConfig.setMaxInputLength(firstInteger(
            safeOverride.getMaxInputLength(),
            safeFallback.getMaxInputLength()
        ));
        modelConfig.setMaxOutputTokens(firstInteger(
            safeOverride.getMaxOutputTokens(),
            safeFallback.getMaxOutputTokens()
        ));
        modelConfig.setTemperature(firstDouble(
            safeOverride.getTemperature(),
            safeFallback.getTemperature()
        ));
        return modelConfig;
    }

    private String firstText(String first, String second, String fallback) {
        if (first != null && !first.isBlank()) {
            return first.strip();
        }
        if (second != null && !second.isBlank()) {
            return second.strip();
        }
        return fallback;
    }

    private Boolean firstBoolean(Boolean first, Boolean second, Boolean fallback) {
        if (first != null) {
            return first;
        }
        if (second != null) {
            return second;
        }
        return fallback;
    }

    private Integer firstInteger(Integer first, Integer second) {
        return first == null ? second : first;
    }

    private Double firstDouble(Double first, Double second) {
        return first == null ? second : first;
    }

    private <T> List<T> firstNotifyUsers(List<T> first, List<T> second) {
        if (first != null && !first.isEmpty()) {
            return first;
        }
        if (second != null && !second.isEmpty()) {
            return second;
        }
        return new ArrayList<>();
    }
}
