package com.xhhao.comment.widget.badge;

import com.xhhao.comment.widget.comment.CommentNextBadge;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import run.halo.app.core.extension.content.Comment;
import run.halo.app.core.extension.content.Reply;
import run.halo.app.extension.ExtensionUtil;
import run.halo.app.extension.ListOptions;
import run.halo.app.extension.ReactiveExtensionClient;

@Component
@RequiredArgsConstructor
public class CommentNextBadgeStatsService {

    private final ReactiveExtensionClient client;

    private final CommentNextBadgeRuleService badgeRuleService;

    private final CommentNextBadgeAssignmentService badgeAssignmentService;

    private final CommentNextBadgeProfileService badgeProfileService;

    public Mono<BadgeProfileSyncResult> syncBadgeProfiles() {
        return Mono.zip(
                commentCounts(),
                replyCounts(),
                badgeRuleService.listEnabledRules(),
                badgeAssignmentService.listEnabledAssignments()
            )
            .flatMap(tuple -> syncProfiles(
                mergeCounts(tuple.getT1(), tuple.getT2()),
                tuple.getT3(),
                tuple.getT4()
            ));
    }

    private Mono<Map<CommentNextBadgeIdentity, Long>> commentCounts() {
        return countByUsername(client.listAll(Comment.class, notDeletingOptions(), Sort.by("metadata.name"))
            .filter(comment -> isActiveComment(comment.getSpec()))
            .flatMap(comment -> Mono.justOrEmpty(
                CommentNextBadgeIdentity.fromOwner(comment.getSpec().getOwner())
            )));
    }

    private Mono<Map<CommentNextBadgeIdentity, Long>> replyCounts() {
        return countByUsername(client.listAll(Reply.class, notDeletingOptions(), Sort.by("metadata.name"))
            .filter(reply -> isActiveComment(reply.getSpec()))
            .flatMap(reply -> Mono.justOrEmpty(
                CommentNextBadgeIdentity.fromOwner(reply.getSpec().getOwner())
            )));
    }

    private Mono<Map<CommentNextBadgeIdentity, Long>> countByUsername(
        Flux<CommentNextBadgeIdentity> identities) {
        return identities.collect(HashMap::new, (counts, identity) -> counts.merge(identity, 1L, Long::sum));
    }

    private Map<CommentNextBadgeIdentity, Long> mergeCounts(
        Map<CommentNextBadgeIdentity, Long> commentCounts,
        Map<CommentNextBadgeIdentity, Long> replyCounts) {
        var counts = new HashMap<>(commentCounts);
        replyCounts.forEach((identity, count) -> counts.merge(identity, count, Long::sum));
        return counts;
    }

    private Mono<BadgeProfileSyncResult> syncProfiles(
        Map<CommentNextBadgeIdentity, Long> counts,
        List<CommentNextBadgeRule> rules,
        List<CommentNextBadgeAssignment> assignments) {
        return badgeProfileService.listAll()
            .collectList()
            .flatMap(existingProfiles -> {
                var targetIdentities = syncTargetIdentities(counts, assignments, existingProfiles);
                return Flux.fromIterable(targetIdentities)
                .flatMap(identity -> badgeProfileService.syncProfile(
                    identity,
                    counts.getOrDefault(identity, 0L),
                    managedBadges(
                        identity,
                        counts.getOrDefault(identity, 0L),
                        rules,
                        assignments
                    )
                ), 8)
                .collectList()
                .map(results -> {
                    var updatedProfiles = results.stream().filter(Boolean::booleanValue).count();
                    var activeComments = counts.values().stream().mapToLong(Long::longValue).sum();
                    return new BadgeProfileSyncResult(
                        targetIdentities.size(),
                        updatedProfiles,
                        activeComments
                    );
                });
            });
    }

    private Set<CommentNextBadgeIdentity> syncTargetIdentities(
        Map<CommentNextBadgeIdentity, Long> counts,
        List<CommentNextBadgeAssignment> assignments,
        List<CommentNextBadgeProfile> existingProfiles) {
        var identities = new LinkedHashSet<CommentNextBadgeIdentity>();
        identities.addAll(counts.keySet());
        existingProfiles.stream()
            .map(CommentNextBadgeProfileService::identityOf)
            .flatMap(java.util.Optional::stream)
            .forEach(identities::add);
        assignments.stream()
            .flatMap(assignment -> identityOf(assignment).stream())
            .forEach(identities::add);
        return identities;
    }

    private List<CommentNextBadge> managedBadges(CommentNextBadgeIdentity identity,
        long activeCommentCount,
        List<CommentNextBadgeRule> rules,
        List<CommentNextBadgeAssignment> assignments) {
        var badges = new LinkedHashMap<String, CommentNextBadge>();
        assignedBadges(identity, rules, assignments)
            .forEach(badge -> badges.putIfAbsent(badge.id(), badge));
        levelRuleBadge(activeCommentCount, rules).forEach(badge -> badges.putIfAbsent(badge.id(), badge));
        return List.copyOf(badges.values());
    }

    private List<CommentNextBadge> assignedBadges(CommentNextBadgeIdentity identity,
        List<CommentNextBadgeRule> rules,
        List<CommentNextBadgeAssignment> assignments) {
        if (rules.isEmpty() || assignments.isEmpty()) {
            return List.of();
        }

        var ruleByName = rules.stream()
            .filter(rule -> isRuleType(rule, CommentNextBadgeRule.BadgeRuleType.USER))
            .filter(this::hasLabel)
            .collect(java.util.stream.Collectors.toMap(
                rule -> rule.getMetadata().getName(),
                rule -> rule,
                (left, right) -> left,
                LinkedHashMap::new
            ));

        return assignments.stream()
            .filter(assignment -> identityOf(assignment)
                .map(identity::equals)
                .orElse(false))
            .map(assignment -> ruleByName.get(assignment.getSpec().getBadgeName()))
            .filter(java.util.Objects::nonNull)
            .map(rule -> toBadge(rule, "custom"))
            .toList();
    }

    private java.util.Optional<CommentNextBadgeIdentity> identityOf(
        CommentNextBadgeAssignment assignment) {
        if (assignment == null || assignment.getSpec() == null) {
            return java.util.Optional.empty();
        }
        var spec = assignment.getSpec();
        return CommentNextBadgeIdentity.of(spec.getIdentityType(), spec.getIdentity());
    }

    private List<CommentNextBadge> levelRuleBadge(long activeCommentCount,
        List<CommentNextBadgeRule> rules) {
        if (activeCommentCount <= 0 || rules.isEmpty()) {
            return List.of();
        }

        return rules.stream()
            .filter(rule -> isRuleType(rule, CommentNextBadgeRule.BadgeRuleType.LEVEL))
            .filter(rule -> rule.getSpec().getMinComments() != null)
            .filter(rule -> activeCommentCount >= rule.getSpec().getMinComments())
            .filter(this::hasLabel)
            .max((left, right) -> Integer.compare(
                left.getSpec().getMinComments(),
                right.getSpec().getMinComments()
            ))
            .map(rule -> List.of(toBadge(rule, "level")))
            .orElseGet(List::of);
    }

    private boolean isRuleType(CommentNextBadgeRule rule, CommentNextBadgeRule.BadgeRuleType type) {
        return rule != null
            && rule.getSpec() != null
            && type.name().equals(rule.getSpec().getType());
    }

    private boolean hasLabel(CommentNextBadgeRule rule) {
        return rule.getSpec() != null && StringUtils.hasText(rule.getSpec().getLabel());
    }

    private CommentNextBadge toBadge(CommentNextBadgeRule rule, String tone) {
        var spec = rule.getSpec();
        return new CommentNextBadge(
            rule.getMetadata().getName(),
            spec.getLabel(),
            tone,
            spec.getIcon(),
            spec.getColor(),
            spec.getTitle()
        );
    }

    private boolean isActiveComment(Comment.BaseCommentSpec spec) {
        if (spec == null || !Boolean.TRUE.equals(spec.getApproved()) || Boolean.TRUE.equals(spec.getHidden())) {
            return false;
        }

        var owner = spec.getOwner();
        return CommentNextBadgeIdentity.fromOwner(owner).isPresent();
    }

    private ListOptions notDeletingOptions() {
        return ListOptions.builder()
            .andQuery(ExtensionUtil.notDeleting())
            .build();
    }

    public record BadgeProfileSyncResult(
        long activeProfiles,
        long updatedProfiles,
        long activeComments
    ) {
    }
}
