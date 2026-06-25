package com.xhhao.comment.widget.security;

import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Instant;
import java.util.Comparator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.regex.Pattern;
import java.util.regex.PatternSyntaxException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.OptimisticLockingFailureException;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;
import org.springframework.web.util.HtmlUtils;
import reactor.core.Exceptions;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.util.retry.Retry;
import run.halo.app.core.extension.content.Comment;
import run.halo.app.core.extension.content.Reply;
import run.halo.app.extension.AbstractExtension;
import run.halo.app.extension.ExtensionUtil;
import run.halo.app.extension.ListOptions;
import run.halo.app.extension.MetadataUtil;
import run.halo.app.extension.ReactiveExtensionClient;

@Slf4j
@Service
@RequiredArgsConstructor
public class CommentNextSecurityReviewService {

    private static final int UPDATE_MAX_ATTEMPTS = 2;

    private static final Pattern DOMAIN_PATTERN = Pattern.compile(
        "(?i)(?:https?://)?(?:[a-z0-9-]+\\.)+[a-z]{2,}(?::\\d{1,5})?(?:/[^\\s<]*)?"
    );

    private final ReactiveExtensionClient client;

    public Mono<Void> reconcileComment(String name) {
        return client.fetch(Comment.class, name)
            .filter(this::isProcessable)
            .flatMap(this::review)
            .then();
    }

    public Mono<Void> reconcileReply(String name) {
        return client.fetch(Reply.class, name)
            .filter(this::isProcessable)
            .flatMap(this::review)
            .then();
    }

    private Mono<AbstractExtension> review(AbstractExtension extension) {
        var subject = ReviewSubject.from(extension);
        if (subject == null) {
            return Mono.empty();
        }

        var contentHash = sha256(subject.fingerprint());
        if (hasReviewedCurrentContent(extension, contentHash)) {
            return Mono.empty();
        }

        return findMatch(subject)
            .flatMap(match -> applyReviewResult(extension, contentHash, match))
            .switchIfEmpty(applyReviewResult(extension, contentHash, null))
            .retryWhen(Retry.max(UPDATE_MAX_ATTEMPTS).filter(this::isOptimisticLockingFailure))
            .doOnError(error -> log.warn(
                "Failed to security review comment target. name={}",
                extension.getMetadata().getName(),
                error
            ))
            .onErrorResume(error -> Mono.empty());
    }

    private Mono<AbstractExtension> applyReviewResult(AbstractExtension extension,
                                                      String contentHash,
                                                      MatchedRule match) {
        return fetchLatest(extension)
            .filter(this::isProcessable)
            .filter(latest -> !hasReviewedCurrentContent(latest, contentHash))
            .flatMap(latest -> updateReviewResult(latest, contentHash, match));
    }

    private Mono<MatchedRule> findMatch(ReviewSubject subject) {
        return enabledRules()
            .map(rule -> matchRule(rule, subject))
            .filter(MatchedRule::matched)
            .sort(matchedRuleComparator())
            .next();
    }

    private Flux<CommentNextSecurityRule> enabledRules() {
        var options = ListOptions.builder()
            .andQuery(ExtensionUtil.notDeleting())
            .build();
        return client.listAll(CommentNextSecurityRule.class, options, Sort.by("metadata.name"))
            .filter(rule -> rule.getSpec() != null && rule.getSpec().isEnabled())
            .filter(rule -> StringUtils.hasText(rule.getSpec().getValue()));
    }

    private MatchedRule matchRule(CommentNextSecurityRule rule, ReviewSubject subject) {
        var spec = rule.getSpec();
        var values = subject.valuesFor(field(spec.getField()));
        var needle = spec.getValue();
        if (values.isEmpty() || !StringUtils.hasText(needle)) {
            return MatchedRule.unmatched(rule);
        }

        var matched = values.stream()
            .filter(StringUtils::hasText)
            .anyMatch(value -> matches(value, needle, matchType(spec.getMatchType())));
        return matched ? MatchedRule.matched(rule) : MatchedRule.unmatched(rule);
    }

    private boolean matches(String value,
                            String ruleValue,
                            CommentNextSecurityRule.MatchType matchType) {
        var normalizedValue = value.strip().toLowerCase(Locale.ROOT);
        var normalizedRuleValue = ruleValue.strip().toLowerCase(Locale.ROOT);

        return switch (matchType) {
            case EXACT -> normalizedValue.equals(normalizedRuleValue);
            case CONTAINS -> normalizedValue.contains(normalizedRuleValue);
            case REGEX -> regexMatches(ruleValue, value);
        };
    }

    private boolean regexMatches(String pattern, String value) {
        try {
            return Pattern.compile(pattern, Pattern.CASE_INSENSITIVE | Pattern.UNICODE_CASE)
                .matcher(value)
                .find();
        } catch (PatternSyntaxException e) {
            log.warn("Invalid security rule regex ignored: {}", pattern, e);
            return false;
        }
    }

    private Comparator<MatchedRule> matchedRuleComparator() {
        return Comparator
            .comparing((MatchedRule matched) -> listType(matched.rule().getSpec().getListType()) != CommentNextSecurityRule.ListType.BLACK)
            .thenComparingInt(matched -> priority(matched.rule()))
            .thenComparing(matched -> matched.rule().getMetadata().getName());
    }

    private Mono<AbstractExtension> updateReviewResult(AbstractExtension extension,
                                                       String contentHash,
                                                       MatchedRule match) {
        var annotations = MetadataUtil.nullSafeAnnotations(extension);
        annotations.put(CommentNextSecurityReviewAnnotations.REVIEWED, "true");
        annotations.put(CommentNextSecurityReviewAnnotations.REVIEWED_AT, Instant.now().toString());
        annotations.put(CommentNextSecurityReviewAnnotations.CONTENT_HASH, contentHash);

        if (match == null || !match.matched()) {
            annotations.put(CommentNextSecurityReviewAnnotations.INTERCEPTED, "false");
            annotations.remove(CommentNextSecurityReviewAnnotations.LIST_TYPE);
            annotations.remove(CommentNextSecurityReviewAnnotations.RULE_NAME);
            annotations.remove(CommentNextSecurityReviewAnnotations.RULE_FIELD);
            annotations.remove(CommentNextSecurityReviewAnnotations.MATCH_TYPE);
            annotations.remove(CommentNextSecurityReviewAnnotations.REASON);
            annotations.remove(CommentNextSecurityReviewAnnotations.REJECTED);
            return update(extension);
        }

        var rule = match.rule();
        var spec = rule.getSpec();
        var listType = listType(spec.getListType());
        var commentSpec = commentSpec(extension);

        annotations.put(CommentNextSecurityReviewAnnotations.INTERCEPTED, "true");
        annotations.put(CommentNextSecurityReviewAnnotations.LIST_TYPE, listType.name());
        annotations.put(CommentNextSecurityReviewAnnotations.RULE_NAME, rule.getMetadata().getName());
        annotations.put(CommentNextSecurityReviewAnnotations.RULE_FIELD, field(spec.getField()).name());
        annotations.put(CommentNextSecurityReviewAnnotations.MATCH_TYPE, matchType(spec.getMatchType()).name());
        annotations.put(CommentNextSecurityReviewAnnotations.REASON,
            firstText(spec.getReason(), defaultReason(listType, rule)));

        if (listType == CommentNextSecurityRule.ListType.BLACK) {
            annotations.put(CommentNextSecurityReviewAnnotations.REJECTED, "true");
        } else {
            annotations.remove(CommentNextSecurityReviewAnnotations.REJECTED);
        }

        if (commentSpec != null) {
            commentSpec.setApproved(false);
        }

        log.info(
            "Security rule intercepted comment target. target={}, rule={}, listType={}",
            extension.getMetadata().getName(),
            rule.getMetadata().getName(),
            listType
        );
        return update(extension);
    }

    private Mono<AbstractExtension> update(AbstractExtension extension) {
        if (extension instanceof Comment comment) {
            return client.update(comment).cast(AbstractExtension.class);
        }
        if (extension instanceof Reply reply) {
            return client.update(reply).cast(AbstractExtension.class);
        }
        return Mono.empty();
    }

    private Mono<AbstractExtension> fetchLatest(AbstractExtension extension) {
        var name = extension.getMetadata().getName();
        if (extension instanceof Comment) {
            return client.fetch(Comment.class, name).cast(AbstractExtension.class);
        }
        if (extension instanceof Reply) {
            return client.fetch(Reply.class, name).cast(AbstractExtension.class);
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

    private boolean hasReviewedCurrentContent(AbstractExtension extension, String contentHash) {
        var annotations = extension.getMetadata() == null
            ? null
            : extension.getMetadata().getAnnotations();
        return annotations != null
            && "true".equals(annotations.get(CommentNextSecurityReviewAnnotations.REVIEWED))
            && contentHash.equals(annotations.get(CommentNextSecurityReviewAnnotations.CONTENT_HASH));
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

    private boolean isProcessable(Comment comment) {
        return !ExtensionUtil.isDeleted(comment)
            && comment.getMetadata() != null
            && StringUtils.hasText(comment.getMetadata().getName())
            && comment.getSpec() != null;
    }

    private boolean isProcessable(Reply reply) {
        return !ExtensionUtil.isDeleted(reply)
            && reply.getMetadata() != null
            && StringUtils.hasText(reply.getMetadata().getName())
            && reply.getSpec() != null;
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

    private int priority(CommentNextSecurityRule rule) {
        var priority = rule.getSpec() == null ? null : rule.getSpec().getPriority();
        return priority == null ? 0 : priority;
    }

    private String defaultReason(CommentNextSecurityRule.ListType listType,
                                 CommentNextSecurityRule rule) {
        return "%s规则命中：%s".formatted(
            listType == CommentNextSecurityRule.ListType.BLACK ? "黑名单" : "灰名单",
            rule.getMetadata().getName()
        );
    }

    private String firstText(String value, String fallback) {
        return StringUtils.hasText(value) ? value.strip() : fallback;
    }

    private CommentNextSecurityRule.ListType listType(String value) {
        return enumValue(value, CommentNextSecurityRule.ListType.class, CommentNextSecurityRule.ListType.GRAY);
    }

    private CommentNextSecurityRule.Field field(String value) {
        return enumValue(value, CommentNextSecurityRule.Field.class, CommentNextSecurityRule.Field.KEYWORD);
    }

    private CommentNextSecurityRule.MatchType matchType(String value) {
        return enumValue(value, CommentNextSecurityRule.MatchType.class, CommentNextSecurityRule.MatchType.CONTAINS);
    }

    private <T extends Enum<T>> T enumValue(String value, Class<T> type, T fallback) {
        if (!StringUtils.hasText(value)) {
            return fallback;
        }
        try {
            return Enum.valueOf(type, value.strip().toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException e) {
            return fallback;
        }
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
            throw new IllegalStateException("Failed to calculate content hash", e);
        }
    }

    private record MatchedRule(CommentNextSecurityRule rule, boolean matched) {
        static MatchedRule matched(CommentNextSecurityRule rule) {
            return new MatchedRule(rule, true);
        }

        static MatchedRule unmatched(CommentNextSecurityRule rule) {
            return new MatchedRule(rule, false);
        }
    }

    private record ReviewSubject(
        String ip,
        String email,
        String username,
        String displayName,
        String content,
        String website,
        String userAgent,
        Set<String> domains
    ) {
        static ReviewSubject from(AbstractExtension extension) {
            Comment.BaseCommentSpec spec;
            if (extension instanceof Comment comment) {
                spec = comment.getSpec();
            } else if (extension instanceof Reply reply) {
                spec = reply.getSpec();
            } else {
                return null;
            }
            if (spec == null) {
                return null;
            }

            var owner = spec.getOwner();
            var content = plainText(firstNonBlank(spec.getContent(), spec.getRaw()));
            var website = owner == null ? "" : owner.getAnnotation(Comment.CommentOwner.WEBSITE_ANNO);
            var domains = new LinkedHashSet<String>();
            domains.addAll(extractDomains(content));
            domains.addAll(extractDomains(website));

            return new ReviewSubject(
                firstText(spec.getIpAddress(), ""),
                owner != null && Comment.CommentOwner.KIND_EMAIL.equals(owner.getKind())
                    ? firstText(owner.getName(), "")
                    : "",
                owner == null ? "" : firstText(owner.getName(), ""),
                owner == null ? "" : firstText(owner.getDisplayName(), ""),
                content,
                firstText(website, ""),
                firstText(spec.getUserAgent(), ""),
                domains
            );
        }

        List<String> valuesFor(CommentNextSecurityRule.Field field) {
            return switch (field) {
                case IP -> List.of(ip);
                case EMAIL -> List.of(email);
                case USERNAME -> List.of(username, displayName);
                case KEYWORD -> List.of(content);
                case DOMAIN -> domains.stream().toList();
                case UA -> List.of(userAgent);
            };
        }

        String fingerprint() {
            return String.join("\n", ip, email, username, displayName, content, website, userAgent);
        }

        private static String firstNonBlank(String first, String second) {
            return StringUtils.hasText(first) ? first.strip() : firstText(second, "");
        }

        private static String firstText(String value, String fallback) {
            return StringUtils.hasText(value) ? value.strip() : fallback;
        }

        private static String plainText(String html) {
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

        private static Set<String> extractDomains(String value) {
            var domains = new LinkedHashSet<String>();
            if (!StringUtils.hasText(value)) {
                return domains;
            }
            var matcher = DOMAIN_PATTERN.matcher(value);
            while (matcher.find()) {
                var domain = normalizeDomain(matcher.group());
                if (StringUtils.hasText(domain)) {
                    domains.add(domain);
                }
            }
            return domains;
        }

        private static String normalizeDomain(String value) {
            if (!StringUtils.hasText(value)) {
                return "";
            }
            var candidate = value.strip();
            try {
                var uri = URI.create(candidate.matches("(?i)^https?://.*") ? candidate : "https://" + candidate);
                var host = uri.getHost();
                if (!StringUtils.hasText(host)) {
                    return "";
                }
                return host.toLowerCase(Locale.ROOT).replaceFirst("^www\\.", "");
            } catch (IllegalArgumentException e) {
                return "";
            }
        }
    }
}
