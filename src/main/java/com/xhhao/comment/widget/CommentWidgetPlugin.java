package com.xhhao.comment.widget;

import com.xhhao.comment.widget.badge.CommentNextBadgeAssignment;
import com.xhhao.comment.widget.badge.CommentNextBadgeRule;
import com.xhhao.comment.widget.badge.CommentNextBadgeProfile;
import com.xhhao.comment.widget.emote.CommentNextEmoteGroup;
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

    @Override
    public void stop() {
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
