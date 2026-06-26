package com.xhhao.comment.widget;

import com.xhhao.comment.widget.badge.CommentNextBadgeAssignment;
import com.xhhao.comment.widget.badge.CommentNextBadgeRule;
import com.xhhao.comment.widget.badge.CommentNextBadgeProfile;
import com.xhhao.comment.widget.ai.CommentNextAiReplyRecord;
import com.xhhao.comment.widget.emote.CommentNextEmoteGroup;
import com.xhhao.comment.widget.interaction.CommentNextReaction;
import com.xhhao.comment.widget.report.CommentNextReport;
import com.xhhao.comment.widget.security.CommentNextSecurityRule;
import java.util.Optional;
import org.springframework.stereotype.Component;
import run.halo.app.extension.Extension;
import run.halo.app.extension.Scheme;
import run.halo.app.extension.SchemeManager;
import run.halo.app.extension.index.IndexSpecs;
import run.halo.app.plugin.BasePlugin;
import run.halo.app.plugin.PluginContext;

/**
 * @author ryanwang
 * @since 2.0.0
 */
@Component
public class CommentWidgetPlugin extends BasePlugin {

    private final SchemeManager schemeManager;

    public CommentWidgetPlugin(PluginContext pluginContext, SchemeManager schemeManager) {
        super(pluginContext);
        this.schemeManager = schemeManager;
    }

    @Override
    public void start() {
        registerBadgeRule();
        registerBadgeAssignment();
        registerBadgeProfile();
        registerEmoteGroup();
        registerSecurityRule();
        registerReaction();
        registerReport();
        registerAiReplyRecord();
    }

    private void registerBadgeRule() {
        schemeManager.register(CommentNextBadgeRule.class, indexSpecs -> {
            indexSpecs.add(IndexSpecs.<CommentNextBadgeRule, String>single("spec.type", String.class)
                .indexFunc(rule -> Optional.ofNullable(rule.getSpec())
                    .map(CommentNextBadgeRule.Spec::getType)
                    .orElse(null)));
            indexSpecs.add(IndexSpecs.<CommentNextBadgeRule, Boolean>single("spec.enabled",
                    Boolean.class)
                .indexFunc(rule -> Optional.ofNullable(rule.getSpec())
                    .map(CommentNextBadgeRule.Spec::isEnabled)
                    .orElse(false)));
            indexSpecs.add(IndexSpecs.<CommentNextBadgeRule, Integer>single("spec.minComments",
                    Integer.class)
                .indexFunc(rule -> Optional.ofNullable(rule.getSpec())
                    .map(CommentNextBadgeRule.Spec::getMinComments)
                    .orElse(null)));
        });
    }

    private void registerBadgeProfile() {
        schemeManager.register(CommentNextBadgeProfile.class, indexSpecs -> {
            indexSpecs.add(IndexSpecs.<CommentNextBadgeProfile, String>single("spec.identityType",
                    String.class)
                .indexFunc(profile -> Optional.ofNullable(profile.getSpec())
                    .map(CommentNextBadgeProfile.Spec::getIdentityType)
                    .orElse(null)));
            indexSpecs.add(IndexSpecs.<CommentNextBadgeProfile, String>single("spec.identity",
                    String.class)
                .indexFunc(profile -> Optional.ofNullable(profile.getSpec())
                    .map(CommentNextBadgeProfile.Spec::getIdentity)
                    .orElse(null)));
        });
    }

    private void registerBadgeAssignment() {
        schemeManager.register(CommentNextBadgeAssignment.class, indexSpecs -> {
            indexSpecs.add(IndexSpecs.<CommentNextBadgeAssignment, String>single("spec.badgeName",
                    String.class)
                .indexFunc(assignment -> Optional.ofNullable(assignment.getSpec())
                    .map(CommentNextBadgeAssignment.Spec::getBadgeName)
                    .orElse(null)));
            indexSpecs.add(IndexSpecs.<CommentNextBadgeAssignment, String>single("spec.identityType",
                    String.class)
                .indexFunc(assignment -> Optional.ofNullable(assignment.getSpec())
                    .map(CommentNextBadgeAssignment.Spec::getIdentityType)
                    .orElse(null)));
            indexSpecs.add(IndexSpecs.<CommentNextBadgeAssignment, String>single("spec.identity",
                    String.class)
                .indexFunc(assignment -> Optional.ofNullable(assignment.getSpec())
                    .map(CommentNextBadgeAssignment.Spec::getIdentity)
                    .orElse(null)));
            indexSpecs.add(IndexSpecs.<CommentNextBadgeAssignment, Boolean>single("spec.enabled",
                    Boolean.class)
                .indexFunc(assignment -> Optional.ofNullable(assignment.getSpec())
                    .map(CommentNextBadgeAssignment.Spec::isEnabled)
                    .orElse(false)));
        });
    }

    private void registerEmoteGroup() {
        schemeManager.register(CommentNextEmoteGroup.class, indexSpecs -> {
            indexSpecs.add(IndexSpecs.<CommentNextEmoteGroup, Boolean>single("spec.enabled",
                    Boolean.class)
                .indexFunc(group -> Optional.ofNullable(group.getSpec())
                    .map(CommentNextEmoteGroup.Spec::isEnabled)
                    .orElse(false)));
            indexSpecs.add(IndexSpecs.<CommentNextEmoteGroup, String>single("spec.type",
                    String.class)
                .indexFunc(group -> Optional.ofNullable(group.getSpec())
                    .map(CommentNextEmoteGroup.Spec::getType)
                    .orElse(null)));
            indexSpecs.add(IndexSpecs.<CommentNextEmoteGroup, String>single("spec.sourceType",
                    String.class)
                .indexFunc(group -> Optional.ofNullable(group.getSpec())
                    .map(CommentNextEmoteGroup.Spec::getSourceType)
                    .orElse(null)));
            indexSpecs.add(IndexSpecs.<CommentNextEmoteGroup, Integer>single("spec.priority",
                    Integer.class)
                .indexFunc(group -> Optional.ofNullable(group.getSpec())
                    .map(CommentNextEmoteGroup.Spec::getPriority)
                    .orElse(0)));
        });
    }

    private void registerSecurityRule() {
        schemeManager.register(CommentNextSecurityRule.class, indexSpecs -> {
            indexSpecs.add(IndexSpecs.<CommentNextSecurityRule, Boolean>single("spec.enabled",
                    Boolean.class)
                .indexFunc(rule -> Optional.ofNullable(rule.getSpec())
                    .map(CommentNextSecurityRule.Spec::isEnabled)
                    .orElse(false)));
            indexSpecs.add(IndexSpecs.<CommentNextSecurityRule, String>single("spec.listType",
                    String.class)
                .indexFunc(rule -> Optional.ofNullable(rule.getSpec())
                    .map(CommentNextSecurityRule.Spec::getListType)
                    .orElse(null)));
            indexSpecs.add(IndexSpecs.<CommentNextSecurityRule, String>single("spec.field",
                    String.class)
                .indexFunc(rule -> Optional.ofNullable(rule.getSpec())
                    .map(CommentNextSecurityRule.Spec::getField)
                    .orElse(null)));
            indexSpecs.add(IndexSpecs.<CommentNextSecurityRule, Integer>single("spec.priority",
                    Integer.class)
                .indexFunc(rule -> Optional.ofNullable(rule.getSpec())
                    .map(CommentNextSecurityRule.Spec::getPriority)
                    .orElse(0)));
        });
    }

    private void registerReaction() {
        schemeManager.register(CommentNextReaction.class, indexSpecs -> {
            indexSpecs.add(IndexSpecs.<CommentNextReaction, String>single("spec.targetType",
                    String.class)
                .indexFunc(reaction -> Optional.ofNullable(reaction.getSpec())
                    .map(CommentNextReaction.Spec::getTargetType)
                    .orElse(null)));
            indexSpecs.add(IndexSpecs.<CommentNextReaction, String>single("spec.targetKey",
                    String.class)
                .indexFunc(reaction -> Optional.ofNullable(reaction.getSpec())
                    .map(CommentNextReaction.Spec::getTargetKey)
                    .orElse(null)));
            indexSpecs.add(IndexSpecs.<CommentNextReaction, String>single("spec.reaction",
                    String.class)
                .indexFunc(reaction -> Optional.ofNullable(reaction.getSpec())
                    .map(CommentNextReaction.Spec::getReaction)
                    .orElse(null)));
            indexSpecs.add(IndexSpecs.<CommentNextReaction, String>single("spec.identityHash",
                    String.class)
                .indexFunc(reaction -> Optional.ofNullable(reaction.getSpec())
                    .map(CommentNextReaction.Spec::getIdentityHash)
                    .orElse(null)));
        });
    }

    private void registerReport() {
        schemeManager.register(CommentNextReport.class, indexSpecs -> {
            indexSpecs.add(IndexSpecs.<CommentNextReport, String>single("spec.targetType",
                    String.class)
                .indexFunc(report -> Optional.ofNullable(report.getSpec())
                    .map(CommentNextReport.Spec::getTargetType)
                    .orElse(null)));
            indexSpecs.add(IndexSpecs.<CommentNextReport, String>single("spec.targetName",
                    String.class)
                .indexFunc(report -> Optional.ofNullable(report.getSpec())
                    .map(CommentNextReport.Spec::getTargetName)
                    .orElse(null)));
            indexSpecs.add(IndexSpecs.<CommentNextReport, String>single("spec.identityHash",
                    String.class)
                .indexFunc(report -> Optional.ofNullable(report.getSpec())
                    .map(CommentNextReport.Spec::getIdentityHash)
                    .orElse(null)));
        });
    }

    private void registerAiReplyRecord() {
        schemeManager.register(CommentNextAiReplyRecord.class, indexSpecs -> {
            indexSpecs.add(IndexSpecs.<CommentNextAiReplyRecord, String>single("spec.targetType",
                    String.class)
                .indexFunc(record -> Optional.ofNullable(record.getSpec())
                    .map(CommentNextAiReplyRecord.Spec::getTargetType)
                    .orElse(null)));
            indexSpecs.add(IndexSpecs.<CommentNextAiReplyRecord, String>single("spec.triggerType",
                    String.class)
                .indexFunc(record -> Optional.ofNullable(record.getSpec())
                    .map(CommentNextAiReplyRecord.Spec::getTriggerType)
                    .orElse(null)));
            indexSpecs.add(IndexSpecs.<CommentNextAiReplyRecord, String>single("spec.targetName",
                    String.class)
                .indexFunc(record -> Optional.ofNullable(record.getSpec())
                    .map(CommentNextAiReplyRecord.Spec::getTargetName)
                    .orElse(null)));
            indexSpecs.add(IndexSpecs.<CommentNextAiReplyRecord, String>single("spec.status",
                    String.class)
                .indexFunc(record -> Optional.ofNullable(record.getSpec())
                    .map(CommentNextAiReplyRecord.Spec::getStatus)
                    .orElse(null)));
            indexSpecs.add(IndexSpecs.<CommentNextAiReplyRecord, java.time.Instant>single("spec.creationTime",
                    java.time.Instant.class)
                .indexFunc(record -> Optional.ofNullable(record.getSpec())
                    .map(CommentNextAiReplyRecord.Spec::getCreationTime)
                    .orElse(null)));
        });
    }

    @Override
    public void stop() {
        unregister(CommentNextAiReplyRecord.class);
        unregister(CommentNextReport.class);
        unregister(CommentNextReaction.class);
        unregister(CommentNextSecurityRule.class);
        unregister(CommentNextEmoteGroup.class);
        unregister(CommentNextBadgeProfile.class);
        unregister(CommentNextBadgeAssignment.class);
        unregister(CommentNextBadgeRule.class);
    }

    private void unregister(Class<? extends Extension> extensionClass) {
        Scheme scheme = schemeManager.get(extensionClass);
        if (scheme != null) {
            schemeManager.unregister(scheme);
        }
    }
}
