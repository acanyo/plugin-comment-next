package com.xhhao.comment.widget.interaction;

import static run.halo.app.extension.index.query.Queries.and;
import static run.halo.app.extension.index.query.Queries.equal;

import com.xhhao.comment.utils.CommonUtils;
import com.xhhao.comment.widget.SettingConfigGetter;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Sort;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;
import org.springframework.web.server.ResponseStatusException;
import reactor.core.publisher.Mono;
import run.halo.app.extension.ExtensionUtil;
import run.halo.app.extension.ListOptions;
import run.halo.app.extension.Metadata;
import run.halo.app.extension.ReactiveExtensionClient;
import run.halo.app.infra.AnonymousUserConst;

@Service
@RequiredArgsConstructor
public class CommentNextReactionService {

    public static final String ANONYMOUS_COOKIE_NAME = "comment_next_reaction_id";

    private static final List<CommentNextReactionOption> DEFAULT_OPTIONS = List.of(
        new CommentNextReactionOption("like", "EMOJI", "👍", "点赞"),
        new CommentNextReactionOption("dislike", "EMOJI", "👎", "踩"),
        new CommentNextReactionOption("happy", "EMOJI", "😄", "开心"),
        new CommentNextReactionOption("celebrate", "EMOJI", "🎉", "庆祝"),
        new CommentNextReactionOption("love", "EMOJI", "💗", "喜欢"),
        new CommentNextReactionOption("confused", "EMOJI", "😳", "困惑")
    );

    private final ReactiveExtensionClient client;

    private final SettingConfigGetter settingConfigGetter;

    public Mono<CommentNextReactionSummary> subjectSummary(CommentNextReactionRequest request,
                                                           String anonymousId) {
        return summary(request, anonymousId);
    }

    public Mono<CommentNextReactionSummary> summary(CommentNextReactionRequest request,
                                                    String anonymousId) {
        var target = target(request);
        return settingConfigGetter.getReactionConfig()
            .flatMap(config -> identity(anonymousId)
                .flatMap(identity -> summary(target, config, identity)));
    }

    public Mono<CommentNextReactionSummary> toggleSubjectReaction(CommentNextReactionRequest request,
                                                                  String anonymousId) {
        return toggleReaction(request, anonymousId);
    }

    public Mono<CommentNextReactionSummary> toggleReaction(CommentNextReactionRequest request,
                                                           String anonymousId) {
        var target = target(request);
        var requestedReaction = normalizeReaction(request.reaction());

        return settingConfigGetter.getReactionConfig()
            .filter(SettingConfigGetter.ReactionConfig::isEnabled)
            .switchIfEmpty(Mono.error(new ResponseStatusException(HttpStatus.NOT_FOUND)))
            .flatMap(config -> identity(anonymousId)
                .flatMap(identity -> {
                    if (!isTargetEnabled(config, target.type())) {
                        return Mono.error(new ResponseStatusException(HttpStatus.NOT_FOUND));
                    }
                    var reactionOptions = reactionOptions(config, target.type());
                    if (!isSupportedReaction(requestedReaction, reactionOptions)) {
                        return Mono.error(new ResponseStatusException(HttpStatus.BAD_REQUEST,
                            "Unsupported reaction."));
                    }
                    if (!config.isAllowAnonymous() && identity.anonymous()) {
                        return Mono.error(new ResponseStatusException(HttpStatus.FORBIDDEN,
                            "Anonymous reactions are not allowed."));
                    }
                    return listIdentityReactions(target, identity)
                        .collectList()
                        .flatMap(existingReactions -> {
                            var selectedSameReaction = existingReactions.stream()
                                .anyMatch(existing -> requestedReaction.equals(reactionName(existing)));
                            var deleteExisting = reactor.core.publisher.Flux
                                .fromIterable(existingReactions)
                                .flatMap(client::delete)
                                .then();
                            if (selectedSameReaction) {
                                return deleteExisting.thenReturn(false);
                            }
                            var reactionName = reactionName(target, requestedReaction, identity);
                            return deleteExisting
                                .then(client.create(newReaction(target, requestedReaction, identity,
                                    reactionName)))
                                .thenReturn(true);
                        })
                        .flatMap(ignored -> summary(target, config, identity));
                }));
    }

    private Mono<CommentNextReactionSummary> summary(Target target,
                                                     SettingConfigGetter.ReactionConfig config,
                                                     ReactionIdentity identity) {
        var reactionOptions = reactionOptions(config, target.type());
        var enabled = config.isEnabled() && isTargetEnabled(config, target.type());
        return listTargetReactions(target)
            .collectList()
            .map(reactions -> {
                var counts = new LinkedHashMap<String, Long>();
                var selected = new java.util.HashSet<String>();

                for (var option : reactionOptions) {
                    counts.put(option.name(), 0L);
                }

                var latestReactions = new LinkedHashMap<String, CommentNextReaction>();
                for (var reaction : reactions) {
                    var spec = reaction.getSpec();
                    if (spec == null
                        || !StringUtils.hasText(spec.getIdentityHash())
                        || !isSupportedReaction(spec.getReaction(), reactionOptions)) {
                        continue;
                    }
                    var existing = latestReactions.get(spec.getIdentityHash());
                    if (existing == null
                        || creationTime(spec).compareTo(creationTime(existing.getSpec())) >= 0) {
                        latestReactions.put(spec.getIdentityHash(), reaction);
                    }
                }

                for (var reaction : latestReactions.values()) {
                    var spec = reaction.getSpec();
                    var reactionName = reactionName(reaction);
                    counts.computeIfPresent(reactionName, (key, value) -> value + 1);
                    if (identity.matches(spec.getIdentityHash())) {
                        selected.add(reactionName);
                    }
                }

                var items = reactionOptions.stream()
                    .map(option -> new CommentNextReactionSummary.CommentNextReactionItem(
                        option.name(),
                        option.type(),
                        option.value(),
                        option.label(),
                        enabled ? counts.getOrDefault(option.name(), 0L) : 0L,
                        enabled && selected.contains(option.name())
                    ))
                    .toList();

                return new CommentNextReactionSummary(
                    target.type().name(),
                    target.key(),
                    config.getSubjectPrompt(),
                    enabled,
                    config.isAllowAnonymous(),
                    items
                );
            });
    }

    private reactor.core.publisher.Flux<CommentNextReaction> listTargetReactions(Target target) {
        var options = ListOptions.builder()
            .andQuery(ExtensionUtil.notDeleting())
            .andQuery(and(
                equal("spec.targetType", target.type().name()),
                equal("spec.targetKey", target.key())
            ))
            .build();
        return client.listAll(CommentNextReaction.class, options, Sort.by("metadata.name"));
    }

    private reactor.core.publisher.Flux<CommentNextReaction> listIdentityReactions(
        Target target,
        ReactionIdentity identity
    ) {
        if (!StringUtils.hasText(identity.hash())) {
            return reactor.core.publisher.Flux.empty();
        }
        var options = ListOptions.builder()
            .andQuery(ExtensionUtil.notDeleting())
            .andQuery(and(
                equal("spec.targetType", target.type().name()),
                equal("spec.targetKey", target.key()),
                equal("spec.identityHash", identity.hash())
            ))
            .build();
        return client.listAll(CommentNextReaction.class, options, Sort.by("metadata.name"));
    }

    private CommentNextReaction newReaction(Target target,
                                            String reaction,
                                            ReactionIdentity identity,
                                            String reactionName) {
        var extension = new CommentNextReaction();
        var metadata = new Metadata();
        metadata.setName(reactionName);
        extension.setMetadata(metadata);
        extension.setApiVersion(CommentNextReaction.GROUP + "/" + CommentNextReaction.VERSION);
        extension.setKind(CommentNextReaction.KIND);

        var spec = new CommentNextReaction.Spec();
        spec.setTargetType(target.type().name());
        spec.setTargetKey(target.key());
        spec.setReaction(reaction);
        spec.setIdentityType(identity.type().name());
        spec.setIdentityHash(identity.hash());
        spec.setCreationTime(Instant.now());
        extension.setSpec(spec);
        return extension;
    }

    private Target subjectTarget(CommentNextReactionRequest request) {
        var group = firstText(request.group(), "content.halo.run");
        var version = requiredText(request.version(), "version");
        var kind = requiredText(request.kind(), "kind");
        var name = requiredText(request.name(), "name");
        return new Target(
            CommentNextReaction.TargetType.SUBJECT,
            "%s/%s/%s/%s".formatted(group, version, kind, name)
        );
    }

    private Target target(CommentNextReactionRequest request) {
        var targetType = targetType(request.targetType());
        if (targetType == CommentNextReaction.TargetType.SUBJECT) {
            return subjectTarget(request);
        }
        return new Target(targetType, requiredText(request.name(), "name"));
    }

    private Mono<ReactionIdentity> identity(String anonymousId) {
        return CommonUtils.getCurrentUserName()
            .map(username -> {
                if (!AnonymousUserConst.isAnonymousUser(username)) {
                    return new ReactionIdentity(
                        CommentNextReaction.IdentityType.USER,
                        sha256("user:" + username)
                    );
                }
                return new ReactionIdentity(
                    CommentNextReaction.IdentityType.ANONYMOUS,
                    StringUtils.hasText(anonymousId) ? sha256("anonymous:" + anonymousId) : ""
                );
            });
    }

    private String reactionName(Target target, String reaction, ReactionIdentity identity) {
        return "comment-next-reaction-" + sha256(
            target.type().name() + "\n" + target.key() + "\n" + reaction + "\n" + identity.hash()
        ).substring(0, 48);
    }

    private boolean isSupportedReaction(String reaction, List<CommentNextReactionOption> options) {
        var normalizedReaction = normalizeReaction(reaction);
        return options.stream().anyMatch(option -> option.name().equals(normalizedReaction));
    }

    private String reactionName(CommentNextReaction reaction) {
        var spec = reaction.getSpec();
        return spec == null ? "" : normalizeReaction(spec.getReaction());
    }

    private Instant creationTime(CommentNextReaction.Spec spec) {
        return spec == null || spec.getCreationTime() == null ? Instant.EPOCH : spec.getCreationTime();
    }

    private boolean isTargetEnabled(SettingConfigGetter.ReactionConfig config,
                                    CommentNextReaction.TargetType targetType) {
        return switch (targetType) {
            case SUBJECT -> config.isSubjectEnabled();
            case COMMENT -> config.isCommentEnabled();
            case REPLY -> config.isReplyEnabled();
        };
    }

    private List<CommentNextReactionOption> reactionOptions(SettingConfigGetter.ReactionConfig config,
                                                            CommentNextReaction.TargetType targetType) {
        var configuredItems = configuredItems(config, targetType);
        if (configuredItems == null || configuredItems.isEmpty()) {
            return DEFAULT_OPTIONS;
        }

        var options = new ArrayList<CommentNextReactionOption>();
        var usedNames = new HashSet<String>();
        for (var index = 0; index < configuredItems.size(); index++) {
            var configuredItem = configuredItems.get(index);
            if (configuredItem == null) {
                continue;
            }
            var value = firstText(configuredItem.getValue(), "");
            if (!StringUtils.hasText(value)) {
                continue;
            }

            var label = firstText(configuredItem.getLabel(), value);
            var name = uniqueReactionName(
                normalizeOptionName(firstText(configuredItem.getName(), label)),
                index,
                usedNames
            );
            usedNames.add(name);
            options.add(new CommentNextReactionOption(
                name,
                normalizeOptionType(configuredItem.getType()),
                value,
                label
            ));
        }

        return options.isEmpty() ? DEFAULT_OPTIONS : List.copyOf(options);
    }

    private List<SettingConfigGetter.ReactionItemConfig> configuredItems(
        SettingConfigGetter.ReactionConfig config,
        CommentNextReaction.TargetType targetType
    ) {
        var targetItems = switch (targetType) {
            case SUBJECT -> config.getSubjectItems();
            case COMMENT, REPLY -> config.getCommentItems();
        };
        return targetItems == null || targetItems.isEmpty() ? config.getItems() : targetItems;
    }

    private String uniqueReactionName(String name, int index, Set<String> usedNames) {
        var baseName = StringUtils.hasText(name) ? name : "reaction-" + (index + 1);
        var candidate = baseName;
        var suffix = 2;
        while (usedNames.contains(candidate)) {
            candidate = baseName + "-" + suffix;
            suffix++;
        }
        return candidate;
    }

    private String normalizeOptionName(String value) {
        if (!StringUtils.hasText(value)) {
            return "";
        }
        return value.strip()
            .toLowerCase(Locale.ROOT)
            .replaceAll("[^a-z0-9\\u3400-\\u9fff]+", "-")
            .replaceAll("(^-+|-+$)", "");
    }

    private String normalizeOptionType(String value) {
        var normalizedValue = firstText(value, "EMOJI").toUpperCase(Locale.ROOT);
        return "IMAGE".equals(normalizedValue) ? "IMAGE" : "EMOJI";
    }

    private CommentNextReaction.TargetType targetType(String value) {
        if (!StringUtils.hasText(value)) {
            return CommentNextReaction.TargetType.SUBJECT;
        }
        try {
            return CommentNextReaction.TargetType.valueOf(value.strip().toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException e) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Unsupported target type.");
        }
    }

    private String normalizeReaction(String reaction) {
        return firstText(reaction, "").toLowerCase(Locale.ROOT);
    }

    private String requiredText(String value, String field) {
        if (!StringUtils.hasText(value)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Missing " + field + ".");
        }
        return value.strip();
    }

    private String firstText(String value, String fallback) {
        return StringUtils.hasText(value) ? value.strip() : fallback;
    }

    private String sha256(String value) {
        try {
            var digest = MessageDigest.getInstance("SHA-256")
                .digest(value.getBytes(StandardCharsets.UTF_8));
            var builder = new StringBuilder(digest.length * 2);
            for (byte item : digest) {
                builder.append(String.format("%02x", item));
            }
            return builder.toString();
        } catch (Exception e) {
            throw new IllegalStateException("Failed to calculate reaction hash", e);
        }
    }

    private record Target(CommentNextReaction.TargetType type, String key) {
    }

    private record ReactionIdentity(CommentNextReaction.IdentityType type, String hash) {
        boolean anonymous() {
            return type == CommentNextReaction.IdentityType.ANONYMOUS;
        }

        boolean matches(String otherHash) {
            return StringUtils.hasText(hash) && hash.equals(otherHash);
        }
    }
}
