package com.xhhao.comment.widget.ai;

import com.xhhao.comment.widget.SettingConfigGetter;
import java.time.Instant;
import java.util.Map;
import java.util.Optional;
import java.util.regex.Pattern;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;
import org.springframework.web.util.HtmlUtils;
import reactor.core.publisher.Mono;
import run.halo.app.core.extension.User;
import run.halo.app.core.extension.content.Comment;
import run.halo.app.core.extension.content.Reply;
import run.halo.app.extension.ExtensionUtil;
import run.halo.app.extension.MetadataUtil;
import run.halo.app.extension.ReactiveExtensionClient;
import run.halo.app.infra.AnonymousUserConst;

@Slf4j
@Service
@ConditionalOnHaloAiFoundation
@RequiredArgsConstructor
class CommentNextAiMentionService {
    private static final String STATE_ANNOTATION = "commentnext.xhhao.com/ai-mention-state";

    private static final String REPLY_ANNOTATION = "commentnext.xhhao.com/ai-mention-reply";

    private static final String ERROR_ANNOTATION = "commentnext.xhhao.com/ai-mention-error";

    private static final String UPDATED_AT_ANNOTATION = "commentnext.xhhao.com/ai-mention-updated-at";

    private static final String STATE_PROCESSING = "processing";

    private static final String STATE_REPLIED = "replied";

    private static final String STATE_FAILED = "failed";

    private static final String ASSISTANT_EMAIL = "comment-next-ai@localhost.invalid";

    private static final Pattern SCRIPT_OR_STYLE_PATTERN =
        Pattern.compile("(?is)<(script|style)[^>]*>.*?</\\1>");

    private static final Pattern BLOCK_TAG_PATTERN =
        Pattern.compile("(?i)</?(p|div|section|article|header|footer|blockquote|li|ul|ol|h[1-6]|br|pre)[^>]*>");

    private static final Pattern HTML_TAG_PATTERN = Pattern.compile("(?s)<[^>]+>");

    private static final Pattern INLINE_SPACE_PATTERN = Pattern.compile("[\\t\\x0B\\f\\r ]+");

    private final ReactiveExtensionClient client;

    private final SettingConfigGetter settingConfigGetter;

    private final CommentNextAiAssistantProfileResolver assistantProfileResolver;

    private final CommentNextAiService aiService;

    private final CommentNextAiReplyRecordService replyRecordService;

    Mono<Void> reconcileComment(String name) {
        return client.fetch(Comment.class, name)
            .filter(this::isProcessableComment)
            .flatMap(comment -> settingConfigGetter.getAiConfig()
                .flatMap(config -> assistantProfileResolver.resolve(config)
                    .flatMap(assistant -> reconcileComment(comment, config, assistant))))
            .then();
    }

    Mono<Void> reconcileReply(String name) {
        return client.fetch(Reply.class, name)
            .filter(this::isProcessableReply)
            .flatMap(reply -> settingConfigGetter.getAiConfig()
                .flatMap(config -> assistantProfileResolver.resolve(config)
                    .flatMap(assistant -> reconcileReply(reply, config, assistant))))
            .then();
    }

    private Mono<Void> reconcileComment(Comment comment,
                                        SettingConfigGetter.AiConfig config,
                                        CommentNextAiAssistantProfile assistant) {
        var trigger = resolveTrigger(comment.getSpec().getContent(), assistant);
        if (trigger.isEmpty() || shouldSkip(comment, comment.getSpec().getOwner(), config, assistant)) {
            return Mono.empty();
        }

        var context = new CommentNextAiMentionContext(
            "评论",
            subject(comment),
            ownerDisplayName(comment.getSpec().getOwner()),
            trigger.get().sourceText(),
            trigger.get().instruction()
        );

        return processMention(comment, config, assistant, context, comment.getMetadata().getName(), null);
    }

    private Mono<Void> reconcileReply(Reply reply,
                                      SettingConfigGetter.AiConfig config,
                                      CommentNextAiAssistantProfile assistant) {
        var trigger = resolveTrigger(reply.getSpec().getContent(), assistant);
        if (trigger.isEmpty() || shouldSkip(reply, reply.getSpec().getOwner(), config, assistant)) {
            return Mono.empty();
        }

        return client.fetch(Comment.class, reply.getSpec().getCommentName())
            .filter(this::isProcessableComment)
            .flatMap(comment -> {
                var context = new CommentNextAiMentionContext(
                    "回复",
                    subject(comment),
                    ownerDisplayName(reply.getSpec().getOwner()),
                    trigger.get().sourceText(),
                    trigger.get().instruction()
                );
                return processMention(reply, config, assistant, context,
                    reply.getSpec().getCommentName(), reply.getMetadata().getName());
            });
    }

    private Mono<Void> processMention(run.halo.app.extension.AbstractExtension source,
                                      SettingConfigGetter.AiConfig config,
                                      CommentNextAiAssistantProfile assistant,
                                      CommentNextAiMentionContext context,
                                      String commentName,
                                      String quoteReplyName) {
        return markState(source, STATE_PROCESSING, null)
            .flatMap(updatedSource -> aiService.generateMentionReply(config, context)
                .flatMap(replyText -> replyRecordService.createMentionReplyRecord(
                    source.getMetadata().getName(),
                    commentName,
                    quoteReplyName,
                    context,
                    replyText,
                    assistant
                ))
                .flatMap(record -> markState(updatedSource, STATE_REPLIED, record.getSpec().getReplyName()))
                .onErrorResume(error -> {
                    log.warn("Failed to generate AI mention reply for {}", source.getMetadata().getName(), error);
                    return markFailed(updatedSource, error);
                }))
            .then();
    }

    private Mono<run.halo.app.extension.AbstractExtension> markState(
        run.halo.app.extension.AbstractExtension source,
        String state,
        String replyName) {
        return fetchLatest(source)
            .flatMap(latestSource -> {
                var annotations = MetadataUtil.nullSafeAnnotations(latestSource);
                annotations.put(STATE_ANNOTATION, state);
                annotations.put(UPDATED_AT_ANNOTATION, Instant.now().toString());
                annotations.remove(ERROR_ANNOTATION);

                if (StringUtils.hasText(replyName)) {
                    annotations.put(REPLY_ANNOTATION, replyName);
                }

                return client.update(latestSource)
                    .cast(run.halo.app.extension.AbstractExtension.class);
            });
    }

    private Mono<run.halo.app.extension.AbstractExtension> markFailed(
        run.halo.app.extension.AbstractExtension source,
        Throwable error) {
        return fetchLatest(source)
            .flatMap(latestSource -> {
                var annotations = MetadataUtil.nullSafeAnnotations(latestSource);
                annotations.put(STATE_ANNOTATION, STATE_FAILED);
                annotations.put(ERROR_ANNOTATION, errorMessage(error));
                annotations.put(UPDATED_AT_ANNOTATION, Instant.now().toString());
                return client.update(latestSource)
                    .cast(run.halo.app.extension.AbstractExtension.class);
            })
            .onErrorResume(updateError -> {
                log.warn("Failed to mark AI mention state for {}", source.getMetadata().getName(), updateError);
                return Mono.empty();
            });
    }

    private Mono<run.halo.app.extension.AbstractExtension> fetchLatest(
        run.halo.app.extension.AbstractExtension source) {
        var name = source.getMetadata().getName();
        if (source instanceof Comment) {
            return client.fetch(Comment.class, name)
                .cast(run.halo.app.extension.AbstractExtension.class);
        }
        if (source instanceof Reply) {
            return client.fetch(Reply.class, name)
                .cast(run.halo.app.extension.AbstractExtension.class);
        }
        return Mono.just(source);
    }

    private boolean shouldSkip(run.halo.app.extension.AbstractExtension source,
                               Comment.CommentOwner owner,
                               SettingConfigGetter.AiConfig config,
                               CommentNextAiAssistantProfile assistant) {
        if (!config.isEnabled() || !config.isMentionAutoReplyEnabled()) {
            return true;
        }
        if (handled(source)) {
            return true;
        }
        if (isAssistantOwner(owner, assistant)) {
            return true;
        }
        return !config.isAllowAnonymous() && isAnonymousOwner(owner);
    }

    private Optional<MentionTrigger> resolveTrigger(String html, CommentNextAiAssistantProfile assistant) {
        var sourceText = htmlToText(html);
        if (!StringUtils.hasText(sourceText)) {
            return Optional.empty();
        }

        var mentionName = assistant.mentionName();
        var index = sourceText.toLowerCase().indexOf(mentionName.toLowerCase());
        if (index < 0) {
            return Optional.empty();
        }

        var instruction = sourceText.substring(index + mentionName.length()).strip();
        if (!StringUtils.hasText(instruction)) {
            instruction = sourceText.replace(mentionName, "").strip();
        }

        return Optional.of(new MentionTrigger(sourceText, instruction));
    }

    private boolean handled(run.halo.app.extension.AbstractExtension source) {
        var annotations = source.getMetadata() == null ? Map.<String, String>of() : source.getMetadata().getAnnotations();
        var state = annotations == null ? "" : annotations.get(STATE_ANNOTATION);
        return STATE_PROCESSING.equals(state) || STATE_REPLIED.equals(state) || STATE_FAILED.equals(state);
    }

    private boolean isProcessableComment(Comment comment) {
        return comment != null
            && !ExtensionUtil.isDeleted(comment)
            && comment.getMetadata() != null
            && comment.getSpec() != null
            && Boolean.TRUE.equals(comment.getSpec().getApproved())
            && !Boolean.TRUE.equals(comment.getSpec().getHidden());
    }

    private boolean isProcessableReply(Reply reply) {
        return reply != null
            && !ExtensionUtil.isDeleted(reply)
            && reply.getMetadata() != null
            && reply.getSpec() != null
            && StringUtils.hasText(reply.getSpec().getCommentName())
            && Boolean.TRUE.equals(reply.getSpec().getApproved())
            && !Boolean.TRUE.equals(reply.getSpec().getHidden());
    }

    private boolean isAssistantOwner(Comment.CommentOwner owner, CommentNextAiAssistantProfile assistant) {
        if (owner == null) {
            return false;
        }

        if (assistant.hasUser()) {
            return User.KIND.equals(owner.getKind())
                && assistant.username().equals(owner.getName());
        }

        return Comment.CommentOwner.KIND_EMAIL.equals(owner.getKind())
            && ASSISTANT_EMAIL.equals(owner.getName());
    }

    private boolean isAnonymousOwner(Comment.CommentOwner owner) {
        return owner == null
            || !User.KIND.equals(owner.getKind())
            || !StringUtils.hasText(owner.getName())
            || AnonymousUserConst.isAnonymousUser(owner.getName());
    }

    private String subject(Comment comment) {
        var subjectRef = comment.getSpec().getSubjectRef();
        if (subjectRef == null) {
            return "";
        }
        return String.join("/",
            nullToEmpty(subjectRef.getGroup()),
            nullToEmpty(subjectRef.getVersion()),
            nullToEmpty(subjectRef.getKind()),
            nullToEmpty(subjectRef.getName())
        );
    }

    private String ownerDisplayName(Comment.CommentOwner owner) {
        if (owner == null) {
            return "匿名用户";
        }
        return StringUtils.hasText(owner.getDisplayName())
            ? owner.getDisplayName()
            : StringUtils.hasText(owner.getName()) ? owner.getName() : "匿名用户";
    }

    private String htmlToText(String html) {
        if (!StringUtils.hasText(html)) {
            return "";
        }

        var text = SCRIPT_OR_STYLE_PATTERN.matcher(html).replaceAll(" ");
        text = BLOCK_TAG_PATTERN.matcher(text).replaceAll("\n");
        text = HTML_TAG_PATTERN.matcher(text).replaceAll(" ");
        text = HtmlUtils.htmlUnescape(text).replace('\u00A0', ' ');
        text = INLINE_SPACE_PATTERN.matcher(text).replaceAll(" ");
        return text.lines()
            .map(String::strip)
            .filter(StringUtils::hasText)
            .reduce((left, right) -> left + "\n" + right)
            .orElse("")
            .strip();
    }

    private String errorMessage(Throwable error) {
        var message = error == null ? "" : error.getMessage();
        if (!StringUtils.hasText(message)) {
            return "AI 自动回复失败";
        }
        return message.length() > 180 ? message.substring(0, 180) : message;
    }

    private String nullToEmpty(String value) {
        return value == null ? "" : value;
    }

    private record MentionTrigger(String sourceText, String instruction) {
    }
}
