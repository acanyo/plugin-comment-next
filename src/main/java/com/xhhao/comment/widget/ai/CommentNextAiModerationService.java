package com.xhhao.comment.widget.ai;

import com.xhhao.comment.widget.CommentNextRoles;
import com.xhhao.comment.widget.SettingConfigGetter;
import com.xhhao.comment.widget.security.CommentNextSecurityReviewAction;
import com.xhhao.comment.widget.security.CommentNextSecurityReviewResult;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Instant;
import java.util.Collection;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.OptimisticLockingFailureException;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;
import org.springframework.web.util.HtmlUtils;
import reactor.core.Exceptions;
import reactor.core.publisher.Mono;
import reactor.util.retry.Retry;
import run.halo.app.core.extension.User;
import run.halo.app.core.extension.content.Comment;
import run.halo.app.core.extension.content.Reply;
import run.halo.app.core.user.service.RoleService;
import run.halo.app.extension.AbstractExtension;
import run.halo.app.extension.ExtensionUtil;
import run.halo.app.extension.MetadataUtil;
import run.halo.app.extension.ReactiveExtensionClient;

@Slf4j
@Service
@ConditionalOnHaloAiFoundation
@RequiredArgsConstructor
class CommentNextAiModerationService {

    private static final int MAX_REASON_LENGTH = 300;

    private static final int UPDATE_MAX_ATTEMPTS = 2;

    private final ReactiveExtensionClient client;

    private final RoleService roleService;

    private final SettingConfigGetter settingConfigGetter;

    private final CommentNextAiService aiService;

    private final CommentNextAiModerationNotificationService notificationService;

    Mono<Void> reconcileComment(String name) {
        return settingConfigGetter.getAiModerationConfig()
            .filter(config -> config.isEnabled()
                && config.isAutoReviewEnabled()
                && config.isReviewCommentsEnabled())
            .flatMap(config -> client.fetch(Comment.class, name)
                .filter(this::isProcessable)
                .flatMap(comment -> reviewComment(comment, config)))
            .then();
    }

    Mono<Void> reconcileReply(String name) {
        return settingConfigGetter.getAiModerationConfig()
            .filter(config -> config.isEnabled()
                && config.isAutoReviewEnabled()
                && config.isReviewRepliesEnabled())
            .flatMap(config -> client.fetch(Reply.class, name)
                .filter(this::isProcessable)
                .flatMap(reply -> reviewReply(reply, config)))
            .then();
    }

    private Mono<Void> reviewComment(Comment comment, SettingConfigGetter.AiConfig config) {
        var spec = comment.getSpec();
        var owner = spec.getOwner();
        var subject = new CommentNextAiModerationSubject(
            "comment",
            comment.getMetadata().getName(),
            ownerDisplayName(owner),
            ownerKind(owner),
            ownerIdentifier(owner),
            ownerWebsite(owner),
            Comment.toSubjectRefKey(spec.getSubjectRef()),
            plainText(spec.getContent())
        );
        return isTrustedOwner(owner)
            .flatMap(trusted -> trusted
                ? clearReviewResult(comment).then()
                : review(comment, subject, config).then());
    }

    private Mono<Void> reviewReply(Reply reply, SettingConfigGetter.AiConfig config) {
        var spec = reply.getSpec();
        var owner = spec.getOwner();
        var subject = new CommentNextAiModerationSubject(
            "reply",
            reply.getMetadata().getName(),
            ownerDisplayName(owner),
            ownerKind(owner),
            ownerIdentifier(owner),
            ownerWebsite(owner),
            spec.getCommentName(),
            plainText(spec.getContent())
        );
        return isTrustedOwner(owner)
            .flatMap(trusted -> trusted
                ? clearReviewResult(reply).then()
                : review(reply, subject, config).then());
    }

    private Mono<? extends AbstractExtension> review(AbstractExtension extension,
                                                    CommentNextAiModerationSubject subject,
                                                    SettingConfigGetter.AiConfig config) {
        if (!StringUtils.hasText(subject.content())) {
            return Mono.empty();
        }

        var contentHash = sha256(subject.fingerprint());
        if (hasReviewedCurrentContent(extension, contentHash)) {
            return Mono.empty();
        }

        return aiService.reviewComment(config, subject)
            .flatMap(result -> applyReviewResult(extension, contentHash, result, config))
            .retryWhen(Retry.max(UPDATE_MAX_ATTEMPTS)
                .filter(this::isOptimisticLockingFailure)
                .doBeforeRetry(signal -> log.debug(
                    "Retrying AI review update after optimistic locking failure. sourceType={}, name={}, attempt={}",
                    subject.sourceType(),
                    subject.name(),
                    signal.totalRetries() + 1
                )))
            .doOnError(error -> log.warn(
                "Failed to AI review {} {}",
                subject.sourceType(),
                subject.name(),
                error
            ))
            .onErrorResume(error -> Mono.empty());
    }

    private Mono<? extends AbstractExtension> applyReviewResult(AbstractExtension extension,
                                                               String contentHash,
                                                               CommentNextSecurityReviewResult result,
                                                               SettingConfigGetter.AiConfig config) {
        return fetchLatest(extension)
            .filter(this::isProcessable)
            .filter(latestExtension -> !hasReviewedCurrentContent(latestExtension, contentHash))
            .flatMap(latestExtension -> updateReviewResult(
                latestExtension,
                contentHash,
                result,
                config
            ));
    }

    private Mono<? extends AbstractExtension> clearReviewResult(AbstractExtension extension) {
        return fetchLatest(extension)
            .filter(this::isProcessable)
            .filter(latestExtension -> CommentNextAiModerationAnnotations.hasReviewAnnotations(
                latestExtension.getMetadata().getAnnotations()
            ))
            .flatMap(latestExtension -> {
                var annotations = MetadataUtil.nullSafeAnnotations(latestExtension);
                var wasIntercepted =
                    "true".equals(annotations.get(CommentNextAiModerationAnnotations.INTERCEPTED));
                CommentNextAiModerationAnnotations.clear(annotations);

                if (wasIntercepted) {
                    var spec = commentSpec(latestExtension);
                    if (spec != null) {
                        spec.setApproved(true);
                    }
                }

                if (latestExtension instanceof Comment comment) {
                    return client.update(comment).cast(AbstractExtension.class);
                }
                if (latestExtension instanceof Reply reply) {
                    return client.update(reply).cast(AbstractExtension.class);
                }
                return Mono.empty();
            });
    }

    private Mono<? extends AbstractExtension> updateReviewResult(AbstractExtension extension,
                                                                 String contentHash,
                                                                 CommentNextSecurityReviewResult result,
                                                                 SettingConfigGetter.AiConfig config) {
        var action = config.getReviewAction();
        var intercepted = result.matchesThreshold(config.normalizedReviewConfidenceThreshold());
        var spec = commentSpec(extension);

        var annotations = MetadataUtil.nullSafeAnnotations(extension);
        annotations.put(CommentNextAiModerationAnnotations.REVIEWED, "true");
        annotations.put(CommentNextAiModerationAnnotations.REVIEWED_AT, Instant.now().toString());
        annotations.put(CommentNextAiModerationAnnotations.CONTENT_HASH, contentHash);
        annotations.put(CommentNextAiModerationAnnotations.INTERCEPTED, Boolean.toString(intercepted));
        annotations.put(CommentNextAiModerationAnnotations.ACTION, intercepted ? action.name() : "");
        putJoined(annotations, CommentNextAiModerationAnnotations.CATEGORIES, result.categories());
        putJoined(annotations, CommentNextAiModerationAnnotations.LABELS, result.labels());
        annotations.put(CommentNextAiModerationAnnotations.CONFIDENCE,
            Double.toString(result.confidence()));
        putText(annotations, CommentNextAiModerationAnnotations.REASON, result.reason(), MAX_REASON_LENGTH);

        if (intercepted) {
            applyAction(spec, annotations, action);
            log.info(
                "AI review intercepted comment content. name={}, action={}, categories={}, confidence={}",
                extension.getMetadata().getName(),
                action,
                result.categories(),
                result.confidence()
            );
        } else {
            annotations.remove(CommentNextAiModerationAnnotations.REJECTED);
        }

        if (extension instanceof Comment comment) {
            return client.update(comment)
                .flatMap(updated -> notifyIfIntercepted(
                    updated,
                    intercepted,
                    result,
                    config,
                    action
                ));
        }
        if (extension instanceof Reply reply) {
            return client.update(reply)
                .flatMap(updated -> notifyIfIntercepted(
                    updated,
                    intercepted,
                    result,
                    config,
                    action
                ));
        }
        return Mono.empty();
    }

    private <T extends AbstractExtension> Mono<T> notifyIfIntercepted(T extension,
                                                                      boolean intercepted,
                                                                      CommentNextSecurityReviewResult result,
                                                                      SettingConfigGetter.AiConfig config,
                                                                      CommentNextSecurityReviewAction action) {
        if (!intercepted) {
            return Mono.just(extension);
        }
        return notificationService.notifyIntercepted(extension, result, config, action)
            .thenReturn(extension);
    }

    private Mono<? extends AbstractExtension> fetchLatest(AbstractExtension extension) {
        var name = extension.getMetadata().getName();
        if (extension instanceof Comment) {
            return client.fetch(Comment.class, name)
                .cast(AbstractExtension.class);
        }
        if (extension instanceof Reply) {
            return client.fetch(Reply.class, name)
                .cast(AbstractExtension.class);
        }
        return Mono.just(extension);
    }

    private Comment.BaseCommentSpec commentSpec(AbstractExtension extension) {
        if (extension instanceof Comment comment) {
            return comment.getSpec();
        }
        if (extension instanceof Reply reply) {
            return reply.getSpec();
        }
        return null;
    }

    private void applyAction(Comment.BaseCommentSpec spec,
                             Map<String, String> annotations,
                             CommentNextSecurityReviewAction action) {
        if (action == CommentNextSecurityReviewAction.REJECT) {
            annotations.put(CommentNextAiModerationAnnotations.REJECTED, "true");
        } else {
            annotations.remove(CommentNextAiModerationAnnotations.REJECTED);
        }

        if (spec != null && action.shouldMarkPending()) {
            spec.setApproved(false);
        }
    }

    private boolean isProcessable(Comment comment) {
        return !ExtensionUtil.isDeleted(comment)
            && comment.getMetadata() != null
            && comment.getSpec() != null
            && StringUtils.hasText(comment.getMetadata().getName());
    }

    private boolean isProcessable(Reply reply) {
        return !ExtensionUtil.isDeleted(reply)
            && reply.getMetadata() != null
            && reply.getSpec() != null
            && StringUtils.hasText(reply.getMetadata().getName());
    }

    private boolean isProcessable(AbstractExtension extension) {
        if (extension instanceof Comment comment) {
            return isProcessable(comment);
        }
        if (extension instanceof Reply reply) {
            return isProcessable(reply);
        }
        return false;
    }

    private boolean isOptimisticLockingFailure(Throwable error) {
        var current = Exceptions.unwrap(error);
        while (current != null) {
            if (current instanceof OptimisticLockingFailureException) {
                return true;
            }
            current = current.getCause();
        }
        return false;
    }

    private boolean hasReviewedCurrentContent(AbstractExtension extension, String contentHash) {
        var annotations = extension.getMetadata() == null
            ? null
            : extension.getMetadata().getAnnotations();
        return annotations != null
            && "true".equals(annotations.get(CommentNextAiModerationAnnotations.REVIEWED))
            && contentHash.equals(annotations.get(CommentNextAiModerationAnnotations.CONTENT_HASH));
    }

    private Mono<Boolean> isTrustedOwner(Comment.CommentOwner owner) {
        if (owner == null
            || !User.KIND.equals(owner.getKind())
            || !StringUtils.hasText(owner.getName())) {
            return Mono.just(false);
        }

        var username = owner.getName().strip();
        return settingConfigGetter.getBadgeConfig()
            .map(config -> config.getAdminIdentifiers().stream()
                .anyMatch(identifier -> identifier != null
                    && StringUtils.hasText(identifier.getUsername())
                    && username.equalsIgnoreCase(identifier.getUsername().strip())))
            .flatMap(configuredAdmin -> configuredAdmin
                ? Mono.just(true)
                : roleService.getRolesByUsername(username)
                    .any(CommentNextRoles.SUPER_ADMIN::equals)
                    .defaultIfEmpty(false))
            .onErrorReturn(false);
    }

    private String ownerDisplayName(Comment.CommentOwner owner) {
        if (owner == null) {
            return "匿名用户";
        }
        if (StringUtils.hasText(owner.getDisplayName())) {
            return owner.getDisplayName().strip();
        }
        if (User.KIND.equals(owner.getKind()) && StringUtils.hasText(owner.getName())) {
            return owner.getName().strip();
        }
        return "匿名用户";
    }

    private String ownerKind(Comment.CommentOwner owner) {
        return owner == null ? "" : firstText(owner.getKind(), "");
    }

    private String ownerIdentifier(Comment.CommentOwner owner) {
        return owner == null ? "" : firstText(owner.getName(), "");
    }

    private String ownerWebsite(Comment.CommentOwner owner) {
        return owner == null
            ? ""
            : firstText(owner.getAnnotation(Comment.CommentOwner.WEBSITE_ANNO), "");
    }

    private String plainText(String html) {
        if (!StringUtils.hasText(html)) {
            return "";
        }
        var withoutTags = html
            .replaceAll("(?is)<(script|style)[^>]*>.*?</\\1>", " ")
            .replaceAll("(?i)<br\\s*/?>", "\n")
            .replaceAll("(?i)</p\\s*>", "\n")
            .replaceAll("<[^>]+>", " ");
        return HtmlUtils.htmlUnescape(withoutTags)
            .replaceAll("[\\t\\x0B\\f\\r ]+", " ")
            .replaceAll("\\n{3,}", "\n\n")
            .strip();
    }

    private String firstText(String value, String fallback) {
        return StringUtils.hasText(value) ? value.strip() : fallback;
    }

    private void putJoined(Map<String, String> annotations,
                           String key,
                           Collection<String> values) {
        if (values == null || values.isEmpty()) {
            annotations.remove(key);
            return;
        }
        annotations.put(key, String.join(",", values));
    }

    private void putText(Map<String, String> annotations,
                         String key,
                         String value,
                         int maxLength) {
        if (!StringUtils.hasText(value)) {
            annotations.remove(key);
            return;
        }
        var normalized = value.strip();
        annotations.put(key, normalized.length() <= maxLength
            ? normalized
            : normalized.substring(0, maxLength));
    }

    private String sha256(String value) {
        try {
            var digest = MessageDigest.getInstance("SHA-256");
            var bytes = digest.digest(value.getBytes(StandardCharsets.UTF_8));
            var result = new StringBuilder(bytes.length * 2);
            for (byte item : bytes) {
                result.append(String.format("%02x", item));
            }
            return result.toString();
        } catch (Exception e) {
            return "";
        }
    }
}
