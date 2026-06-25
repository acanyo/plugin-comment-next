package com.xhhao.comment.widget.ai;

import java.time.Instant;
import java.util.Arrays;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;
import org.springframework.web.util.HtmlUtils;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import run.halo.app.core.extension.User;
import run.halo.app.core.extension.content.Comment;
import run.halo.app.core.extension.content.Reply;
import run.halo.app.extension.ExtensionUtil;
import run.halo.app.extension.ListOptions;
import run.halo.app.extension.ReactiveExtensionClient;

@Service
@RequiredArgsConstructor
class CommentNextAiModerationRecordService {

    private final ReactiveExtensionClient client;

    Mono<CommentNextAiModerationRecordPage> list(CommentNextAiModerationRecordQuery query) {
        return records(query)
            .filter(record -> matchesKeyword(record, query.keyword()))
            .sort(recordComparator())
            .collectList()
            .map(records -> CommentNextAiModerationRecordPage.of(
                query.page(),
                query.size(),
                records
            ));
    }

    private Flux<CommentNextAiModerationRecord> records(CommentNextAiModerationRecordQuery query) {
        return switch (query.target()) {
            case COMMENT -> commentRecords(query);
            case REPLY -> replyRecords(query);
            case ALL -> Flux.merge(commentRecords(query), replyRecords(query));
        };
    }

    private Flux<CommentNextAiModerationRecord> commentRecords(CommentNextAiModerationRecordQuery query) {
        return client.listAll(Comment.class, notDeletingOptions(), Sort.by("metadata.name"))
            .filter(comment -> hasAiReview(comment.getMetadata().getAnnotations(), query))
            .map(this::toCommentRecord);
    }

    private Flux<CommentNextAiModerationRecord> replyRecords(CommentNextAiModerationRecordQuery query) {
        return client.listAll(Reply.class, notDeletingOptions(), Sort.by("metadata.name"))
            .filter(reply -> hasAiReview(reply.getMetadata().getAnnotations(), query))
            .map(this::toReplyRecord);
    }

    private ListOptions notDeletingOptions() {
        return ListOptions.builder()
            .andQuery(ExtensionUtil.notDeleting())
            .build();
    }

    private boolean hasAiReview(Map<String, String> annotations,
                                CommentNextAiModerationRecordQuery query) {
        if (annotations == null
            || !"true".equals(annotations.get(CommentNextAiModerationAnnotations.REVIEWED))) {
            return false;
        }
        return !query.interceptedOnly()
            || "true".equals(annotations.get(CommentNextAiModerationAnnotations.INTERCEPTED));
    }

    private CommentNextAiModerationRecord toCommentRecord(Comment comment) {
        var spec = comment.getSpec();
        var annotations = comment.getMetadata().getAnnotations();
        return new CommentNextAiModerationRecord(
            "comment",
            comment.getMetadata().getName(),
            "",
            ownerDisplayName(spec.getOwner()),
            spec.getSubjectRef() == null ? "" : Comment.toSubjectRefKey(spec.getSubjectRef()),
            plainText(spec.getContent()),
            Boolean.TRUE.equals(spec.getApproved()),
            Boolean.TRUE.equals(spec.getHidden()),
            spec.getCreationTime(),
            bool(annotations, CommentNextAiModerationAnnotations.INTERCEPTED),
            bool(annotations, CommentNextAiModerationAnnotations.REJECTED),
            text(annotations, CommentNextAiModerationAnnotations.ACTION),
            csv(annotations, CommentNextAiModerationAnnotations.CATEGORIES),
            csv(annotations, CommentNextAiModerationAnnotations.LABELS),
            doubleValue(annotations, CommentNextAiModerationAnnotations.CONFIDENCE),
            text(annotations, CommentNextAiModerationAnnotations.REASON),
            instant(annotations, CommentNextAiModerationAnnotations.REVIEWED_AT)
        );
    }

    private CommentNextAiModerationRecord toReplyRecord(Reply reply) {
        var spec = reply.getSpec();
        var annotations = reply.getMetadata().getAnnotations();
        return new CommentNextAiModerationRecord(
            "reply",
            reply.getMetadata().getName(),
            spec.getCommentName(),
            ownerDisplayName(spec.getOwner()),
            spec.getCommentName(),
            plainText(spec.getContent()),
            Boolean.TRUE.equals(spec.getApproved()),
            Boolean.TRUE.equals(spec.getHidden()),
            spec.getCreationTime(),
            bool(annotations, CommentNextAiModerationAnnotations.INTERCEPTED),
            bool(annotations, CommentNextAiModerationAnnotations.REJECTED),
            text(annotations, CommentNextAiModerationAnnotations.ACTION),
            csv(annotations, CommentNextAiModerationAnnotations.CATEGORIES),
            csv(annotations, CommentNextAiModerationAnnotations.LABELS),
            doubleValue(annotations, CommentNextAiModerationAnnotations.CONFIDENCE),
            text(annotations, CommentNextAiModerationAnnotations.REASON),
            instant(annotations, CommentNextAiModerationAnnotations.REVIEWED_AT)
        );
    }

    private Comparator<CommentNextAiModerationRecord> recordComparator() {
        return Comparator
            .comparing(
                (CommentNextAiModerationRecord record) -> safeInstant(record.reviewedAt()),
                Comparator.reverseOrder()
            )
            .thenComparing(
                record -> safeInstant(record.creationTime()),
                Comparator.reverseOrder()
            )
            .thenComparing(CommentNextAiModerationRecord::name);
    }

    private boolean matchesKeyword(CommentNextAiModerationRecord record, String keyword) {
        if (!StringUtils.hasText(keyword)) {
            return true;
        }
        var normalizedKeyword = keyword.strip().toLowerCase();
        return contains(record.name(), normalizedKeyword)
            || contains(record.parentName(), normalizedKeyword)
            || contains(record.authorName(), normalizedKeyword)
            || contains(record.content(), normalizedKeyword)
            || contains(record.reason(), normalizedKeyword)
            || record.categories().stream().anyMatch(value -> contains(value, normalizedKeyword))
            || record.labels().stream().anyMatch(value -> contains(value, normalizedKeyword));
    }

    private boolean contains(String value, String keyword) {
        return StringUtils.hasText(value)
            && value.toLowerCase().contains(keyword);
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

    private String text(Map<String, String> annotations, String key) {
        return annotations == null ? "" : annotations.getOrDefault(key, "");
    }

    private boolean bool(Map<String, String> annotations, String key) {
        return annotations != null && Boolean.parseBoolean(annotations.get(key));
    }

    private List<String> csv(Map<String, String> annotations, String key) {
        var value = text(annotations, key);
        if (!StringUtils.hasText(value)) {
            return List.of();
        }
        return Arrays.stream(value.split(","))
            .filter(StringUtils::hasText)
            .map(String::strip)
            .distinct()
            .toList();
    }

    private double doubleValue(Map<String, String> annotations, String key) {
        var value = text(annotations, key);
        if (!StringUtils.hasText(value)) {
            return 0D;
        }
        try {
            return Double.parseDouble(value);
        } catch (NumberFormatException e) {
            return 0D;
        }
    }

    private Instant instant(Map<String, String> annotations, String key) {
        var value = text(annotations, key);
        if (!StringUtils.hasText(value)) {
            return null;
        }
        try {
            return Instant.parse(value);
        } catch (Exception e) {
            return null;
        }
    }

    private Instant safeInstant(Instant instant) {
        return instant == null ? Instant.EPOCH : instant;
    }
}
