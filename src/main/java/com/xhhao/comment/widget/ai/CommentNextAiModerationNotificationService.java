package com.xhhao.comment.widget.ai;

import com.xhhao.comment.widget.SettingConfigGetter;
import com.xhhao.comment.widget.security.CommentNextSecurityReviewAction;
import com.xhhao.comment.widget.security.CommentNextSecurityReviewResult;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;
import org.springframework.web.util.HtmlUtils;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import run.halo.app.core.extension.content.Comment;
import run.halo.app.core.extension.content.Reply;
import run.halo.app.core.extension.notification.Reason;
import run.halo.app.core.extension.notification.Subscription;
import run.halo.app.extension.AbstractExtension;
import run.halo.app.notification.NotificationCenter;
import run.halo.app.notification.NotificationReasonEmitter;
import run.halo.app.notification.UserIdentity;

@Slf4j
@Service
@RequiredArgsConstructor
class CommentNextAiModerationNotificationService {

    private static final String ADMIN_REASON_TYPE = "comment-next-ai-review-intercepted-admin";

    private static final String COMMENTER_REASON_TYPE = "comment-next-ai-review-intercepted-commenter";

    private static final String ADMIN_MANAGE_URL =
        "/console/comments/comment-next-ai-moderation-records";

    private static final int MAX_CONTENT_LENGTH = 500;

    private final NotificationCenter notificationCenter;

    private final NotificationReasonEmitter notificationReasonEmitter;

    Mono<Void> notifyIntercepted(AbstractExtension extension,
                                 CommentNextSecurityReviewResult result,
                                 SettingConfigGetter.AiConfig config,
                                 CommentNextSecurityReviewAction action) {
        var context = NotificationContext.from(extension, result, action);
        if (context == null) {
            return Mono.empty();
        }

        return Mono.when(
                notifyAdmins(context, config),
                notifyCommenter(context, config)
            )
            .onErrorResume(error -> {
                log.warn("Failed to send AI moderation notification. targetType={}, name={}",
                    context.targetType(), context.targetName(), error);
                return Mono.empty();
            });
    }

    private Mono<Void> notifyAdmins(NotificationContext context,
                                    SettingConfigGetter.AiConfig config) {
        if (!config.isReviewAdminNotificationEnabled()) {
            return Mono.empty();
        }
        var recipients = config.getReviewAdminNotifyUsernames();
        if (recipients.isEmpty()) {
            return Mono.empty();
        }

        var author = context.ownerIdentity();
        var subject = context.adminSubject();
        return Flux.fromIterable(recipients)
            .filter(StringUtils::hasText)
            .map(String::strip)
            .distinct()
            .flatMap(username -> emitToSubscriber(
                ADMIN_REASON_TYPE,
                username,
                author == null ? UserIdentity.of(username) : author,
                subject,
                context.attributesFor(username, true)
            ))
            .then();
    }

    private Mono<Void> notifyCommenter(NotificationContext context,
                                       SettingConfigGetter.AiConfig config) {
        if (!config.isReviewCommenterNotificationEnabled()
            || context.ownerIdentity() == null
            || !StringUtils.hasText(context.ownerIdentity().name())) {
            return Mono.empty();
        }

        var commenter = context.ownerIdentity().name();
        if (config.getReviewAdminNotifyUsernames().contains(commenter)) {
            return Mono.empty();
        }

        return emitToSubscriber(
            COMMENTER_REASON_TYPE,
            commenter,
            context.ownerIdentity(),
            context.commenterSubject(),
            context.attributesFor(commenter, false)
        );
    }

    private Mono<Void> emitToSubscriber(String reasonType,
                                        String subscriberName,
                                        UserIdentity author,
                                        Reason.Subject subject,
                                        Map<String, Object> attributes) {
        var subscriber = new Subscription.Subscriber();
        subscriber.setName(subscriberName);

        var interestReason = new Subscription.InterestReason();
        interestReason.setReasonType(reasonType);
        interestReason.setExpression(exactTargetExpression(subscriberName));

        return notificationCenter.subscribe(subscriber, interestReason)
            .then(notificationReasonEmitter.emit(reasonType, builder -> builder
                .author(author)
                .subject(subject)
                .attributes(attributes)))
            .onErrorResume(error -> {
                log.warn("Failed to emit AI moderation notification. reasonType={}, subscriber={}",
                    reasonType, subscriberName, error);
                return Mono.empty();
            });
    }

    private String exactTargetExpression(String targetUser) {
        return "props.targetUser == '%s'".formatted(escapeExpressionLiteral(targetUser));
    }

    private String escapeExpressionLiteral(String value) {
        return value == null ? "" : value.replace("'", "''");
    }

    private record NotificationContext(
        String targetType,
        String targetLabel,
        String targetName,
        String parentName,
        String authorName,
        String content,
        String reason,
        String categories,
        String confidence,
        String action,
        UserIdentity ownerIdentity,
        String apiVersion,
        String kind
    ) {

        static NotificationContext from(AbstractExtension extension,
                                        CommentNextSecurityReviewResult result,
                                        CommentNextSecurityReviewAction action) {
            if (extension == null || extension.getMetadata() == null) {
                return null;
            }

            var spec = spec(extension);
            if (spec == null) {
                return null;
            }

            var targetType = extension instanceof Reply ? "reply" : "comment";
            var targetLabel = extension instanceof Reply ? "回复" : "评论";
            var parentName = extension instanceof Reply reply ? reply.getSpec().getCommentName() : "";
            var targetName = extension.getMetadata().getName();
            var owner = spec.getOwner();

            return new NotificationContext(
                targetType,
                targetLabel,
                targetName,
                parentName,
                ownerDisplayName(owner),
                truncate(plainText(spec.getContent()), MAX_CONTENT_LENGTH),
                firstText(result.reason(), "AI 判定该内容需要人工审核。"),
                categoryText(result),
                "%d%%".formatted(Math.round(result.confidence() * 100)),
                actionText(action),
                ownerIdentity(owner),
                firstText(extension.getApiVersion(), "content.halo.run/v1alpha1"),
                firstText(extension.getKind(), targetLabel)
            );
        }

        Reason.Subject adminSubject() {
            return Reason.Subject.builder()
                .apiVersion(apiVersion)
                .kind(kind)
                .name(targetName)
                .title("AI 拦截%s".formatted(targetLabel))
                .url(ADMIN_MANAGE_URL)
                .build();
        }

        Reason.Subject commenterSubject() {
            return Reason.Subject.builder()
                .apiVersion(apiVersion)
                .kind(kind)
                .name(targetName)
                .title("你的%s已进入审核".formatted(targetLabel))
                .build();
        }

        Map<String, Object> attributesFor(String targetUser, boolean admin) {
            var attributes = new LinkedHashMap<String, Object>();
            attributes.put("targetUser", targetUser);
            attributes.put("targetType", targetType);
            attributes.put("targetLabel", targetLabel);
            attributes.put("targetName", targetName);
            attributes.put("parentName", parentName);
            attributes.put("authorName", authorName);
            attributes.put("content", content);
            attributes.put("reason", reason);
            attributes.put("categories", categories);
            attributes.put("confidence", confidence);
            attributes.put("action", action);
            if (admin) {
                attributes.put("manageUrl", ADMIN_MANAGE_URL);
            }
            return attributes;
        }

        private static Comment.BaseCommentSpec spec(AbstractExtension extension) {
            if (extension instanceof Comment comment) {
                return comment.getSpec();
            }
            if (extension instanceof Reply reply) {
                return reply.getSpec();
            }
            return null;
        }

        private static UserIdentity ownerIdentity(Comment.CommentOwner owner) {
            if (owner == null || !StringUtils.hasText(owner.getName())) {
                return null;
            }
            if (Comment.CommentOwner.KIND_EMAIL.equals(owner.getKind())) {
                return UserIdentity.anonymousWithEmail(owner.getName().strip());
            }
            return UserIdentity.of(owner.getName().strip());
        }

        private static String ownerDisplayName(Comment.CommentOwner owner) {
            if (owner == null) {
                return "匿名用户";
            }
            if (StringUtils.hasText(owner.getDisplayName())) {
                return owner.getDisplayName().strip();
            }
            if (StringUtils.hasText(owner.getName())) {
                return owner.getName().strip();
            }
            return "匿名用户";
        }

        private static String categoryText(CommentNextSecurityReviewResult result) {
            if (result.categories().isEmpty()) {
                return "其他";
            }
            return result.categories().stream()
                .map(NotificationContext::categoryLabel)
                .distinct()
                .reduce((left, right) -> left + "、" + right)
                .orElse("其他");
        }

        private static String categoryLabel(String category) {
            if (!StringUtils.hasText(category)) {
                return "其他";
            }
            return switch (category.strip().toLowerCase(Locale.ROOT)) {
                case "spam" -> "垃圾";
                case "ads" -> "广告";
                case "abuse" -> "辱骂";
                case "provocation" -> "引战";
                case "porn" -> "色情";
                case "flood" -> "灌水";
                case "illegal" -> "违法";
                case "malicious_link" -> "恶意链接";
                default -> category.strip();
            };
        }

        private static String actionText(CommentNextSecurityReviewAction action) {
            if (action == null) {
                return "进审核";
            }
            return switch (action) {
                case PENDING_REVIEW -> "进审核";
                case REJECT -> "自动驳回";
                case TAG -> "打标签";
                case NOTICE -> "仅提醒";
            };
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

        private static String truncate(String value, int maxLength) {
            if (value == null || value.length() <= maxLength) {
                return value;
            }
            return value.substring(0, maxLength) + "...";
        }

        private static String firstText(String value, String fallback) {
            return StringUtils.hasText(value) ? value.strip() : fallback;
        }
    }
}
