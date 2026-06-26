package com.xhhao.comment.widget.report;

import static run.halo.app.extension.index.query.Queries.and;
import static run.halo.app.extension.index.query.Queries.equal;

import com.xhhao.comment.utils.CommonUtils;
import com.xhhao.comment.widget.SettingConfigGetter;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Instant;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import org.springframework.dao.OptimisticLockingFailureException;
import org.springframework.data.domain.Sort;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.web.util.HtmlUtils;
import reactor.core.Exceptions;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.util.retry.Retry;
import run.halo.app.core.extension.User;
import run.halo.app.core.extension.content.Comment;
import run.halo.app.core.extension.content.Reply;
import run.halo.app.extension.AbstractExtension;
import run.halo.app.extension.ExtensionUtil;
import run.halo.app.extension.ListOptions;
import run.halo.app.extension.Metadata;
import run.halo.app.extension.MetadataUtil;
import run.halo.app.extension.ReactiveExtensionClient;
import run.halo.app.infra.AnonymousUserConst;

@Service
@RequiredArgsConstructor
public class CommentNextReportService {

    public static final String ANONYMOUS_COOKIE_NAME = "comment_next_report_id";

    private static final int UPDATE_MAX_ATTEMPTS = 2;

    private final ReactiveExtensionClient client;

    private final SettingConfigGetter settingConfigGetter;

    public Mono<CommentNextReportResult> report(CommentNextReportRequest request,
                                                String anonymousId) {
        var target = target(request);
        var reason = reason(request.reason());
        var description = description(request.description());

        return settingConfigGetter.getReportConfig()
            .filter(SettingConfigGetter.ReportConfig::isEnabled)
            .switchIfEmpty(Mono.error(new ResponseStatusException(HttpStatus.FORBIDDEN,
                "评论举报未启用。")))
            .flatMap(config -> {
                if (!isTargetEnabled(config, target.type())) {
                    return Mono.error(new ResponseStatusException(HttpStatus.FORBIDDEN,
                        "当前类型不允许举报。"));
                }
                return identity(anonymousId)
                    .flatMap(identity -> {
                        if (!config.isAllowAnonymous() && identity.anonymous()) {
                            return Mono.error(new ResponseStatusException(HttpStatus.FORBIDDEN,
                                "Anonymous reports are not allowed."));
                        }
                        if (!StringUtils.hasText(identity.hash())) {
                            return Mono.error(new ResponseStatusException(HttpStatus.FORBIDDEN,
                                "Cannot identify reporter."));
                        }
                        return ensureTargetExists(target)
                            .then(createReportIfAbsent(target, reason, description, identity))
                            .flatMap(created -> reportCount(target)
                                .flatMap(count -> applyAutoPendingIfNeeded(target, count, config)
                                    .map(autoPending -> new CommentNextReportResult(
                                        target.type().name(),
                                        target.name(),
                                        count,
                                        true,
                                        !created,
                                        autoPending
                                    ))));
                    });
            });
    }

    public Mono<CommentNextReportRecordPage> listRecords(CommentNextReportRecordQuery query) {
        return client.listAll(CommentNextReport.class, notDeletingOptions(), Sort.by("metadata.name"))
            .collectList()
            .flatMapMany(reports -> {
                var reportCounts = reportCounts(reports);
                return Flux.fromIterable(reports)
                    .filter(report -> matchesReportFilter(report, query))
                    .flatMap(report -> toRecord(
                        report,
                        reportCounts.getOrDefault(reportTargetKey(report), 0)
                    ));
            })
            .filter(record -> matchesKeyword(record, query.keyword()))
            .sort(recordComparator())
            .collectList()
            .map(records -> CommentNextReportRecordPage.of(
                query.page(),
                query.size(),
                records
            ));
    }

    private Mono<Boolean> createReportIfAbsent(Target target,
                                               CommentNextReport.Reason reason,
                                               String description,
                                               ReportIdentity identity) {
        var reportName = reportName(target, identity);
        return client.fetch(CommentNextReport.class, reportName)
            .hasElement()
            .flatMap(exists -> {
                if (exists) {
                    return Mono.just(false);
                }
                return client.create(newReport(target, reason, description, identity, reportName))
                    .thenReturn(true)
                    .onErrorResume(this::isAlreadyExists, error -> Mono.just(false));
            });
    }

    private Mono<Integer> reportCount(Target target) {
        return listTargetReports(target)
            .map(report -> report.getSpec() == null ? "" : report.getSpec().getIdentityHash())
            .filter(StringUtils::hasText)
            .distinct()
            .count()
            .map(Long::intValue);
    }

    private Flux<CommentNextReport> listTargetReports(Target target) {
        var options = ListOptions.builder()
            .andQuery(ExtensionUtil.notDeleting())
            .andQuery(and(
                equal("spec.targetType", target.type().name()),
                equal("spec.targetName", target.name())
            ))
            .build();
        return client.listAll(CommentNextReport.class, options, Sort.by("metadata.name"));
    }

    private ListOptions notDeletingOptions() {
        return ListOptions.builder()
            .andQuery(ExtensionUtil.notDeleting())
            .build();
    }

    private Mono<Boolean> applyAutoPendingIfNeeded(Target target,
                                                   int count,
                                                   SettingConfigGetter.ReportConfig config) {
        var threshold = config.normalizedAutoPendingThreshold();
        var autoPending = config.isAutoPendingEnabled() && threshold > 0 && count >= threshold;

        return fetchTarget(target)
            .flatMap(extension -> updateTargetReportState(extension, count, autoPending))
            .retryWhen(Retry.max(UPDATE_MAX_ATTEMPTS).filter(this::isOptimisticLockingFailure))
            .thenReturn(autoPending);
    }

    private Mono<AbstractExtension> updateTargetReportState(AbstractExtension extension,
                                                            int count,
                                                            boolean autoPending) {
        var annotations = MetadataUtil.nullSafeAnnotations(extension);
        annotations.put(CommentNextReportAnnotations.REPORT_COUNT, Integer.toString(Math.max(count, 0)));
        annotations.put(CommentNextReportAnnotations.REPORTED_AT, Instant.now().toString());
        if (autoPending) {
            annotations.put(CommentNextReportAnnotations.AUTO_PENDING, "true");
        } else {
            annotations.remove(CommentNextReportAnnotations.AUTO_PENDING);
        }

        var spec = commentSpec(extension);
        if (spec != null && autoPending) {
            spec.setApproved(false);
        }

        if (extension instanceof Comment comment) {
            return client.update(comment).cast(AbstractExtension.class);
        }
        if (extension instanceof Reply reply) {
            return client.update(reply).cast(AbstractExtension.class);
        }
        return Mono.empty();
    }

    private Mono<Void> ensureTargetExists(Target target) {
        return fetchTarget(target).then();
    }

    private Mono<AbstractExtension> fetchTarget(Target target) {
        if (target.type() == CommentNextReport.TargetType.COMMENT) {
            return client.fetch(Comment.class, target.name())
                .filter(comment -> !ExtensionUtil.isDeleted(comment))
                .cast(AbstractExtension.class)
                .switchIfEmpty(Mono.error(new ResponseStatusException(HttpStatus.NOT_FOUND,
                    "举报目标不存在。")));
        }
        return client.fetch(Reply.class, target.name())
            .filter(reply -> !ExtensionUtil.isDeleted(reply))
            .cast(AbstractExtension.class)
            .switchIfEmpty(Mono.error(new ResponseStatusException(HttpStatus.NOT_FOUND,
                "举报目标不存在。")));
    }

    private Mono<CommentNextReportRecord> toRecord(CommentNextReport report, int targetReportCount) {
        var spec = report.getSpec();
        if (spec == null) {
            return Mono.empty();
        }

        var target = new Target(targetType(spec.getTargetType()), text(spec.getTargetName()));
        return targetInfo(target)
            .defaultIfEmpty(TargetInfo.empty())
            .map(targetInfo -> new CommentNextReportRecord(
                report.getMetadata().getName(),
                target.type().name().toLowerCase(Locale.ROOT),
                target.name(),
                targetInfo.parentName(),
                targetInfo.authorName(),
                targetInfo.subject(),
                targetInfo.content(),
                targetInfo.exists(),
                targetInfo.approved(),
                targetInfo.hidden(),
                targetInfo.creationTime(),
                text(spec.getReason()),
                text(spec.getDescription()),
                text(spec.getIdentityType()),
                spec.getCreationTime() == null
                    ? report.getMetadata().getCreationTimestamp()
                    : spec.getCreationTime(),
                targetReportCount,
                targetInfo.autoPending()
            ));
    }

    private Mono<TargetInfo> targetInfo(Target target) {
        if (target.type() == CommentNextReport.TargetType.COMMENT) {
            return client.fetch(Comment.class, target.name())
                .filter(comment -> !ExtensionUtil.isDeleted(comment))
                .map(comment -> {
                    var spec = comment.getSpec();
                    var annotations = MetadataUtil.nullSafeAnnotations(comment);
                    return new TargetInfo(
                        "",
                        ownerDisplayName(spec.getOwner()),
                        spec.getSubjectRef() == null
                            ? ""
                            : Comment.toSubjectRefKey(spec.getSubjectRef()),
                        plainText(spec.getContent()),
                        true,
                        Boolean.TRUE.equals(spec.getApproved()),
                        Boolean.TRUE.equals(spec.getHidden()),
                        spec.getCreationTime(),
                        Boolean.parseBoolean(annotations.get(CommentNextReportAnnotations.AUTO_PENDING))
                    );
                });
        }
        return client.fetch(Reply.class, target.name())
            .filter(reply -> !ExtensionUtil.isDeleted(reply))
            .map(reply -> {
                var spec = reply.getSpec();
                var annotations = MetadataUtil.nullSafeAnnotations(reply);
                return new TargetInfo(
                    text(spec.getCommentName()),
                    ownerDisplayName(spec.getOwner()),
                    text(spec.getCommentName()),
                    plainText(spec.getContent()),
                    true,
                    Boolean.TRUE.equals(spec.getApproved()),
                    Boolean.TRUE.equals(spec.getHidden()),
                    spec.getCreationTime(),
                    Boolean.parseBoolean(annotations.get(CommentNextReportAnnotations.AUTO_PENDING))
                );
            });
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

    private Map<String, Integer> reportCounts(List<CommentNextReport> reports) {
        return reports.stream()
            .collect(Collectors.groupingBy(
                this::reportTargetKey,
                Collectors.collectingAndThen(Collectors.counting(), Long::intValue)
            ));
    }

    private String reportTargetKey(CommentNextReport report) {
        var spec = report.getSpec();
        if (spec == null) {
            return "";
        }
        return text(spec.getTargetType()) + "\n" + text(spec.getTargetName());
    }

    private boolean matchesReportFilter(CommentNextReport report,
                                        CommentNextReportRecordQuery query) {
        var spec = report.getSpec();
        if (spec == null) {
            return false;
        }
        if (query.target() == CommentNextReportRecordQuery.Target.COMMENT
            && !"COMMENT".equalsIgnoreCase(spec.getTargetType())) {
            return false;
        }
        if (query.target() == CommentNextReportRecordQuery.Target.REPLY
            && !"REPLY".equalsIgnoreCase(spec.getTargetType())) {
            return false;
        }
        return query.reason() == CommentNextReportRecordQuery.Reason.ALL
            || query.reason().name().equalsIgnoreCase(spec.getReason());
    }

    private boolean matchesKeyword(CommentNextReportRecord record, String keyword) {
        if (!StringUtils.hasText(keyword)) {
            return true;
        }
        var normalizedKeyword = keyword.strip().toLowerCase(Locale.ROOT);
        return contains(record.name(), normalizedKeyword)
            || contains(record.targetName(), normalizedKeyword)
            || contains(record.parentName(), normalizedKeyword)
            || contains(record.authorName(), normalizedKeyword)
            || contains(record.subject(), normalizedKeyword)
            || contains(record.content(), normalizedKeyword)
            || contains(record.reason(), normalizedKeyword)
            || contains(record.description(), normalizedKeyword);
    }

    private Comparator<CommentNextReportRecord> recordComparator() {
        return Comparator
            .comparing(
                (CommentNextReportRecord record) -> safeInstant(record.creationTime()),
                Comparator.reverseOrder()
            )
            .thenComparing(CommentNextReportRecord::name);
    }

    private boolean contains(String value, String keyword) {
        return StringUtils.hasText(value)
            && value.toLowerCase(Locale.ROOT).contains(keyword);
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

    private String text(String value) {
        return StringUtils.hasText(value) ? value.strip() : "";
    }

    private Instant safeInstant(Instant instant) {
        return instant == null ? Instant.EPOCH : instant;
    }

    private Mono<ReportIdentity> identity(String anonymousId) {
        return CommonUtils.getCurrentUserName()
            .map(username -> {
                if (!AnonymousUserConst.isAnonymousUser(username)) {
                    return new ReportIdentity(
                        CommentNextReport.IdentityType.USER,
                        sha256("user:" + username)
                    );
                }
                return new ReportIdentity(
                    CommentNextReport.IdentityType.ANONYMOUS,
                    StringUtils.hasText(anonymousId) ? sha256("anonymous:" + anonymousId) : ""
                );
            });
    }

    private CommentNextReport newReport(Target target,
                                        CommentNextReport.Reason reason,
                                        String description,
                                        ReportIdentity identity,
                                        String reportName) {
        var extension = new CommentNextReport();
        var metadata = new Metadata();
        metadata.setName(reportName);
        extension.setMetadata(metadata);
        extension.setApiVersion(CommentNextReport.GROUP + "/" + CommentNextReport.VERSION);
        extension.setKind(CommentNextReport.KIND);

        var spec = new CommentNextReport.Spec();
        spec.setTargetType(target.type().name());
        spec.setTargetName(target.name());
        spec.setReason(reason.name());
        spec.setDescription(description);
        spec.setIdentityType(identity.type().name());
        spec.setIdentityHash(identity.hash());
        spec.setCreationTime(Instant.now());
        extension.setSpec(spec);
        return extension;
    }

    private Target target(CommentNextReportRequest request) {
        return new Target(
            targetType(request.targetType()),
            requiredText(request.name(), "name")
        );
    }

    private CommentNextReport.TargetType targetType(String value) {
        if (!StringUtils.hasText(value)) {
            return CommentNextReport.TargetType.COMMENT;
        }
        try {
            return CommentNextReport.TargetType.valueOf(value.strip().toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException e) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Unsupported report target type.");
        }
    }

    private CommentNextReport.Reason reason(String value) {
        if (!StringUtils.hasText(value)) {
            return CommentNextReport.Reason.OTHER;
        }
        try {
            return CommentNextReport.Reason.valueOf(value.strip().toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException e) {
            return CommentNextReport.Reason.OTHER;
        }
    }

    private boolean isTargetEnabled(SettingConfigGetter.ReportConfig config,
                                    CommentNextReport.TargetType targetType) {
        return switch (targetType) {
            case COMMENT -> config.isCommentEnabled();
            case REPLY -> config.isReplyEnabled();
        };
    }

    private String reportName(Target target, ReportIdentity identity) {
        return "comment-next-report-" + sha256(
            target.type().name() + "\n" + target.name() + "\n" + identity.hash()
        ).substring(0, 48);
    }

    private String requiredText(String value, String fieldName) {
        if (!StringUtils.hasText(value)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, fieldName + " is required.");
        }
        return value.strip();
    }

    private String description(String value) {
        var description = requiredText(value, "description");
        if (description.length() > 500) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "description is too long.");
        }
        return description;
    }

    private String sha256(String value) {
        try {
            var digest = MessageDigest.getInstance("SHA-256");
            var bytes = digest.digest(value.getBytes(StandardCharsets.UTF_8));
            var builder = new StringBuilder(bytes.length * 2);
            for (byte b : bytes) {
                builder.append(String.format("%02x", b));
            }
            return builder.toString();
        } catch (Exception e) {
            throw new IllegalStateException("Failed to calculate sha256.", e);
        }
    }

    private boolean isAlreadyExists(Throwable error) {
        var unwrapped = Exceptions.unwrap(error);
        var message = unwrapped.getMessage();
        return message != null
            && message.toLowerCase(Locale.ROOT).contains("already")
            && message.toLowerCase(Locale.ROOT).contains("exist");
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

    private record Target(CommentNextReport.TargetType type, String name) {
    }

    private record ReportIdentity(CommentNextReport.IdentityType type, String hash) {
        boolean anonymous() {
            return type == CommentNextReport.IdentityType.ANONYMOUS;
        }
    }

    private record TargetInfo(String parentName,
                              String authorName,
                              String subject,
                              String content,
                              boolean exists,
                              boolean approved,
                              boolean hidden,
                              Instant creationTime,
                              boolean autoPending) {
        static TargetInfo empty() {
            return new TargetInfo("", "", "", "", false, false, false, null, false);
        }
    }
}
