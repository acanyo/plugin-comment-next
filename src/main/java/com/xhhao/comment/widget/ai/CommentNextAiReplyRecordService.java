package com.xhhao.comment.widget.ai;

import static run.halo.app.extension.index.query.Queries.equal;

import com.xhhao.comment.widget.SettingConfigGetter;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Comparator;
import java.util.List;
import java.util.UUID;
import java.util.regex.Pattern;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.data.domain.Sort;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.web.util.HtmlUtils;
import reactor.core.publisher.Mono;
import run.halo.app.core.extension.User;
import run.halo.app.core.extension.content.Comment;
import run.halo.app.core.extension.content.Reply;
import run.halo.app.extension.ExtensionUtil;
import run.halo.app.extension.ListOptions;
import run.halo.app.extension.Metadata;
import run.halo.app.extension.ReactiveExtensionClient;

@Slf4j
@Service
@RequiredArgsConstructor
class CommentNextAiReplyRecordService {
    private static final String CONTENT_API_VERSION = "content.halo.run/v1alpha1";

    private static final String ASSISTANT_EMAIL = "comment-next-ai@localhost.invalid";

    private static final String FAILED_PLACEHOLDER = "AI 生成失败，暂无可发布内容。";

    private static final Pattern SCRIPT_OR_STYLE_PATTERN =
        Pattern.compile("(?is)<(script|style)[^>]*>.*?</\\1>");

    private static final Pattern BLOCK_TAG_PATTERN =
        Pattern.compile("(?i)</?(p|div|section|article|header|footer|blockquote|li|ul|ol|h[1-6]|br|pre)[^>]*>");

    private static final Pattern HTML_TAG_PATTERN = Pattern.compile("(?s)<[^>]+>");

    private static final Pattern INLINE_SPACE_PATTERN = Pattern.compile("[\\t\\x0B\\f\\r ]+");

    private final ReactiveExtensionClient client;

    private final SettingConfigGetter settingConfigGetter;

    private final CommentNextAiAssistantProfileResolver assistantProfileResolver;

    private final ObjectProvider<CommentNextAiService> aiServiceProvider;

    private record ReplyGenerationOptions(String style, int candidateCount) {
    }

    Mono<Void> reconcileComment(String name) {
        return client.fetch(Comment.class, name)
            .filter(this::isProcessableComment)
            .flatMap(comment -> settingConfigGetter.getAiAutoReplyConfig()
                .flatMap(config -> assistantProfileResolver.resolve(config)
                    .flatMap(assistant -> reconcileComment(comment, config, assistant))))
            .then();
    }

    Mono<Void> reconcileReply(String name) {
        return client.fetch(Reply.class, name)
            .filter(this::isProcessableReply)
            .flatMap(reply -> settingConfigGetter.getAiAutoReplyConfig()
                .flatMap(config -> assistantProfileResolver.resolve(config)
                    .flatMap(assistant -> reconcileReply(reply, config, assistant))))
            .then();
    }

    Mono<CommentNextAiReplyRecordPage> list(CommentNextAiReplyRecordQuery query) {
        return client.listAll(CommentNextAiReplyRecord.class, notDeletingOptions(), Sort.by("metadata.name"))
            .filter(record -> matchesQuery(record, query))
            .filter(record -> matchesKeyword(record, query.keyword()))
            .sort(recordComparator())
            .collectList()
            .map(records -> CommentNextAiReplyRecordPage.of(query.page(), query.size(), records));
    }

    Mono<CommentNextAiReplyRecord> publish(String name, Integer candidateIndex) {
        return client.fetch(CommentNextAiReplyRecord.class, name)
            .switchIfEmpty(Mono.error(new ResponseStatusException(HttpStatus.NOT_FOUND)))
            .flatMap(record -> selectCandidateIfNecessary(record, candidateIndex))
            .flatMap(record -> {
                var spec = record.getSpec();
                if (CommentNextAiReplyRecord.Status.PUBLISHED.name().equals(spec.getStatus())) {
                    return Mono.just(record);
                }
                if (!StringUtils.hasText(spec.getReplyContent())
                    || FAILED_PLACEHOLDER.equals(spec.getReplyContent())) {
                    return Mono.error(new ResponseStatusException(
                        HttpStatus.BAD_REQUEST,
                        "AI 回复内容为空，无法发布"
                    ));
                }
                return assistantProfileResolver.resolve(spec.getAssistantUserName(), spec.getAssistantName())
                    .flatMap(assistant -> createAssistantReply(record, assistant))
                    .flatMap(reply -> markPublished(record.getMetadata().getName(),
                        reply.getMetadata().getName()))
                    .onErrorResume(error -> markFailed(record.getMetadata().getName(), error)
                        .then(Mono.error(error)));
            });
    }

    Mono<CommentNextAiReplyRecord> selectCandidate(String name, Integer candidateIndex) {
        if (candidateIndex == null || candidateIndex <= 0) {
            return Mono.error(new ResponseStatusException(
                HttpStatus.BAD_REQUEST,
                "请选择需要使用的 AI 回复候选"
            ));
        }

        return client.fetch(CommentNextAiReplyRecord.class, name)
            .switchIfEmpty(Mono.error(new ResponseStatusException(HttpStatus.NOT_FOUND)))
            .flatMap(record -> selectCandidate(record, candidateIndex))
            .flatMap(client::update);
    }

    Mono<CommentNextAiReplyRecord> reject(String name) {
        return client.fetch(CommentNextAiReplyRecord.class, name)
            .switchIfEmpty(Mono.error(new ResponseStatusException(HttpStatus.NOT_FOUND)))
            .flatMap(record -> {
                var spec = record.getSpec();
                if (CommentNextAiReplyRecord.Status.PUBLISHED.name().equals(spec.getStatus())) {
                    return Mono.error(new ResponseStatusException(
                        HttpStatus.BAD_REQUEST,
                        "已发布的 AI 回复不能驳回"
                    ));
                }
                spec.setStatus(CommentNextAiReplyRecord.Status.REJECTED.name());
                spec.setReviewedAt(Instant.now());
                spec.setError(null);
                return client.update(record);
            });
    }

    Mono<CommentNextAiReplyRecord> generateForCommentManually(String name,
                                                              CommentNextAiReplyGenerateRequest request) {
        return client.fetch(Comment.class, name)
            .filter(this::isUsableComment)
            .switchIfEmpty(Mono.error(new ResponseStatusException(HttpStatus.NOT_FOUND)))
            .flatMap(comment -> settingConfigGetter.getAiAutoReplyConfig()
                .flatMap(config -> assistantProfileResolver.resolve(config)
                    .flatMap(assistant -> {
                        if (isAssistantOwner(comment.getSpec().getOwner(), assistant)) {
                            return Mono.error(new ResponseStatusException(
                                HttpStatus.BAD_REQUEST,
                                "AI 助手自己的评论无需生成 AI 回复"
                            ));
                        }
                        return generateManualForComment(comment, config, assistant, request);
                    })));
    }

    Mono<CommentNextAiReplyRecord> generateForReplyManually(String name,
                                                            CommentNextAiReplyGenerateRequest request) {
        return client.fetch(Reply.class, name)
            .filter(this::isUsableReply)
            .switchIfEmpty(Mono.error(new ResponseStatusException(HttpStatus.NOT_FOUND)))
            .flatMap(reply -> settingConfigGetter.getAiAutoReplyConfig()
                .flatMap(config -> assistantProfileResolver.resolve(config)
                    .flatMap(assistant -> {
                        if (isAssistantOwner(reply.getSpec().getOwner(), assistant)) {
                            return Mono.error(new ResponseStatusException(
                                HttpStatus.BAD_REQUEST,
                                "AI 助手自己的回复无需生成 AI 回复"
                            ));
                        }
                        return client.fetch(Comment.class, reply.getSpec().getCommentName())
                            .filter(this::isUsableComment)
                            .switchIfEmpty(Mono.error(new ResponseStatusException(
                                HttpStatus.NOT_FOUND,
                                "未找到所属评论"
                            )))
                            .flatMap(comment -> generateManualForReply(reply, comment, config, assistant, request));
                    })));
    }

    Mono<CommentNextAiReplyRecord> createMentionReplyRecord(String sourceName,
                                                            String commentName,
                                                            String quoteReplyName,
                                                            CommentNextAiMentionContext context,
                                                            String replyContent,
                                                            CommentNextAiAssistantProfile assistant) {
        var targetType = StringUtils.hasText(quoteReplyName)
            ? CommentNextAiReplyRecord.TargetType.REPLY
            : CommentNextAiReplyRecord.TargetType.COMMENT;
        var draft = draftRecord(
            targetType,
            CommentNextAiReplyRecord.TriggerType.MENTION,
            sourceName,
            commentName,
            quoteReplyName,
            context.subject(),
            context.authorName(),
            context.sourceContent(),
            assistant,
            CommentNextAiReplyRecord.PublishMode.AUTO.name()
        );
        var spec = draft.getSpec();
        spec.setReplyContent(replyContent);
        spec.setStatus(CommentNextAiReplyRecord.Status.PENDING_REVIEW.name());
        spec.setGeneratedAt(Instant.now());

        return client.create(draft)
            .onErrorResume(error -> client.fetch(CommentNextAiReplyRecord.class, draft.getMetadata().getName()))
            .flatMap(record -> publish(record.getMetadata().getName(), null));
    }

    private Mono<Void> reconcileComment(Comment comment,
                                        SettingConfigGetter.AiConfig config,
                                        CommentNextAiAssistantProfile assistant) {
        if (!config.isAutoReplyEnabled() || !config.isAutoReplyCommentEnabled()) {
            return Mono.empty();
        }
        if (shouldSkip(comment, comment.getSpec().getOwner(), assistant)) {
            return Mono.empty();
        }

        var targetType = CommentNextAiReplyRecord.TargetType.COMMENT.name();
        var targetName = comment.getMetadata().getName();
        return hasRecord(CommentNextAiReplyRecord.TriggerType.AUTO.name(), targetType, targetName)
            .flatMap(exists -> exists
                ? Mono.empty()
                : generateForComment(comment, config, assistant).then());
    }

    private Mono<Void> reconcileReply(Reply reply,
                                      SettingConfigGetter.AiConfig config,
                                      CommentNextAiAssistantProfile assistant) {
        if (!config.isAutoReplyEnabled() || !config.isAutoReplyReplyEnabled()) {
            return Mono.empty();
        }
        if (shouldSkip(reply, reply.getSpec().getOwner(), assistant)) {
            return Mono.empty();
        }

        var targetType = CommentNextAiReplyRecord.TargetType.REPLY.name();
        var targetName = reply.getMetadata().getName();
        return hasRecord(CommentNextAiReplyRecord.TriggerType.AUTO.name(), targetType, targetName)
            .flatMap(exists -> {
                if (exists) {
                    return Mono.empty();
                }
                return client.fetch(Comment.class, reply.getSpec().getCommentName())
                    .filter(this::isProcessableComment)
                    .flatMap(comment -> generateForReply(reply, comment, config, assistant))
                    .then();
            });
    }

    private Mono<CommentNextAiReplyRecord> generateForComment(Comment comment,
                                                              SettingConfigGetter.AiConfig config,
                                                              CommentNextAiAssistantProfile assistant) {
        var context = new CommentNextAiMentionContext(
            "评论",
            subject(comment),
            ownerDisplayName(comment.getSpec().getOwner()),
            sourceText(comment.getSpec().getContent(), config),
            "请以 AI 助手身份自然回复这条评论。"
        );

        var draft = draftRecord(
            CommentNextAiReplyRecord.TargetType.COMMENT,
            CommentNextAiReplyRecord.TriggerType.AUTO,
            comment.getMetadata().getName(),
            comment.getMetadata().getName(),
            null,
            subject(comment),
            context.authorName(),
            context.sourceContent(),
            assistant,
            config.isAutoReplyAutoPublishEnabled()
                ? CommentNextAiReplyRecord.PublishMode.AUTO.name()
                : CommentNextAiReplyRecord.PublishMode.REVIEW.name()
        );

        return generateAndSave(draft, config, autoReplyGenerationOptions(config));
    }

    private Mono<CommentNextAiReplyRecord> generateManualForComment(Comment comment,
                                                                    SettingConfigGetter.AiConfig config,
                                                                    CommentNextAiAssistantProfile assistant,
                                                                    CommentNextAiReplyGenerateRequest request) {
        var context = new CommentNextAiMentionContext(
            "评论",
            subject(comment),
            ownerDisplayName(comment.getSpec().getOwner()),
            sourceText(comment.getSpec().getContent(), config),
            "请以 AI 助手身份自然回复这条评论。"
        );

        var draft = draftRecord(
            CommentNextAiReplyRecord.TargetType.COMMENT,
            CommentNextAiReplyRecord.TriggerType.MANUAL,
            comment.getMetadata().getName(),
            comment.getMetadata().getName(),
            null,
            subject(comment),
            context.authorName(),
            context.sourceContent(),
            assistant,
            CommentNextAiReplyRecord.PublishMode.REVIEW.name()
        );

        return generateAndSave(draft, config, manualGenerationOptions(request));
    }

    private Mono<CommentNextAiReplyRecord> generateManualForReply(Reply reply,
                                                                  Comment comment,
                                                                  SettingConfigGetter.AiConfig config,
                                                                  CommentNextAiAssistantProfile assistant,
                                                                  CommentNextAiReplyGenerateRequest request) {
        var context = new CommentNextAiMentionContext(
            "回复",
            subject(comment),
            ownerDisplayName(reply.getSpec().getOwner()),
            sourceText(reply.getSpec().getContent(), config),
            "请以 AI 助手身份自然回复这条楼中楼回复。"
        );

        var draft = draftRecord(
            CommentNextAiReplyRecord.TargetType.REPLY,
            CommentNextAiReplyRecord.TriggerType.MANUAL,
            reply.getMetadata().getName(),
            reply.getSpec().getCommentName(),
            reply.getMetadata().getName(),
            subject(comment),
            context.authorName(),
            context.sourceContent(),
            assistant,
            CommentNextAiReplyRecord.PublishMode.REVIEW.name()
        );

        return generateAndSave(draft, config, manualGenerationOptions(request));
    }

    private Mono<CommentNextAiReplyRecord> generateForReply(Reply reply,
                                                            Comment comment,
                                                            SettingConfigGetter.AiConfig config,
                                                            CommentNextAiAssistantProfile assistant) {
        var context = new CommentNextAiMentionContext(
            "回复",
            subject(comment),
            ownerDisplayName(reply.getSpec().getOwner()),
            sourceText(reply.getSpec().getContent(), config),
            "请以 AI 助手身份自然回复这条楼中楼回复。"
        );

        var draft = draftRecord(
            CommentNextAiReplyRecord.TargetType.REPLY,
            CommentNextAiReplyRecord.TriggerType.AUTO,
            reply.getMetadata().getName(),
            reply.getSpec().getCommentName(),
            reply.getMetadata().getName(),
            subject(comment),
            context.authorName(),
            context.sourceContent(),
            assistant,
            config.isAutoReplyAutoPublishEnabled()
                ? CommentNextAiReplyRecord.PublishMode.AUTO.name()
                : CommentNextAiReplyRecord.PublishMode.REVIEW.name()
        );

        return generateAndSave(draft, config, autoReplyGenerationOptions(config));
    }

    private Mono<CommentNextAiReplyRecord> generateAndSave(CommentNextAiReplyRecord draft,
                                                           SettingConfigGetter.AiConfig config,
                                                           ReplyGenerationOptions options) {
        var aiService = aiServiceProvider.getIfAvailable();
        if (aiService == null) {
            return Mono.error(new ResponseStatusException(
                HttpStatus.SERVICE_UNAVAILABLE,
                "Halo AI Foundation 插件未安装或未启用"
            ));
        }

        var spec = draft.getSpec();
        var context = new CommentNextAiMentionContext(
            displayTargetType(spec.getTargetType()),
            spec.getSubject(),
            spec.getAuthorName(),
            spec.getSourceContent(),
            "请以 AI 助手身份自然回复这条内容。"
        );

        return generateReplyCandidates(aiService, config, context, options)
            .flatMap(candidates -> {
                applyReplyCandidates(spec, candidates, options);
                return client.create(draft);
            })
            .flatMap(record -> CommentNextAiReplyRecord.PublishMode.AUTO.name()
                .equals(record.getSpec().getPublishMode())
                ? publish(record.getMetadata().getName(), null)
                    .onErrorResume(error -> client.fetch(CommentNextAiReplyRecord.class,
                            record.getMetadata().getName())
                        .defaultIfEmpty(record))
                : Mono.just(record))
            .onErrorResume(error -> saveFailedRecord(draft, error));
    }

    private Mono<CommentNextAiReplyRecord> saveFailedRecord(CommentNextAiReplyRecord draft,
                                                            Throwable error) {
        var spec = draft.getSpec();
        spec.setReplyContent(FAILED_PLACEHOLDER);
        spec.setStatus(CommentNextAiReplyRecord.Status.FAILED.name());
        spec.setError(errorMessage(error));
        spec.setGeneratedAt(Instant.now());
        return client.create(draft)
            .onErrorResume(createError -> {
                log.warn("Failed to save AI reply failure record", createError);
                return Mono.error(error);
            });
    }

    private ReplyGenerationOptions autoReplyGenerationOptions(SettingConfigGetter.AiConfig config) {
        if (config.isAutoReplyAutoPublishEnabled()) {
            return new ReplyGenerationOptions("智能推荐", 1);
        }
        return new ReplyGenerationOptions("智能推荐", 4);
    }

    private ReplyGenerationOptions manualGenerationOptions(CommentNextAiReplyGenerateRequest request) {
        var safeRequest = request == null ? new CommentNextAiReplyGenerateRequest() : request;
        return new ReplyGenerationOptions(
            safeRequest.normalizedStyle(),
            safeRequest.normalizedCandidateCount()
        );
    }

    private Mono<List<CommentNextAiReplyRecord.ReplyCandidate>> generateReplyCandidates(
        CommentNextAiService aiService,
        SettingConfigGetter.AiConfig config,
        CommentNextAiMentionContext context,
        ReplyGenerationOptions options
    ) {
        if (options.candidateCount() <= 1) {
            return aiService.generateAutoReply(config, context)
                .map(replyText -> List.of(replyCandidate(1, options.style(), replyText)));
        }
        return aiService.generateAutoReplyCandidates(config, context, options.style(), options.candidateCount());
    }

    private void applyReplyCandidates(CommentNextAiReplyRecord.Spec spec,
                                      List<CommentNextAiReplyRecord.ReplyCandidate> candidates,
                                      ReplyGenerationOptions options) {
        if (candidates == null || candidates.isEmpty()) {
            throw new ResponseStatusException(HttpStatus.BAD_GATEWAY, "AI 未返回可用回复候选");
        }
        var normalizedCandidates = normalizeCandidates(candidates, options.style());
        if (normalizedCandidates.isEmpty()) {
            throw new ResponseStatusException(HttpStatus.BAD_GATEWAY, "AI 未返回可用回复候选");
        }
        var first = normalizedCandidates.get(0);
        spec.setReplyCandidates(normalizedCandidates);
        spec.setReplyContent(first.getContent());
        spec.setSelectedCandidateIndex(first.getIndex());
        spec.setCandidateCount(normalizedCandidates.size());
        spec.setReplyStyle(options.style());
        spec.setStatus(CommentNextAiReplyRecord.Status.PENDING_REVIEW.name());
        spec.setGeneratedAt(Instant.now());
    }

    private List<CommentNextAiReplyRecord.ReplyCandidate> normalizeCandidates(
        List<CommentNextAiReplyRecord.ReplyCandidate> candidates,
        String style
    ) {
        var normalized = new java.util.ArrayList<CommentNextAiReplyRecord.ReplyCandidate>();
        for (var candidate : candidates) {
            if (candidate == null || !StringUtils.hasText(candidate.getContent())) {
                continue;
            }
            normalized.add(replyCandidate(
                normalized.size() + 1,
                StringUtils.hasText(candidate.getStyle()) ? candidate.getStyle() : style,
                candidate.getContent()
            ));
        }
        return normalized;
    }

    private CommentNextAiReplyRecord.ReplyCandidate replyCandidate(int index,
                                                                   String style,
                                                                   String content) {
        var candidate = new CommentNextAiReplyRecord.ReplyCandidate();
        candidate.setIndex(index <= 0 ? 1 : index);
        candidate.setStyle(StringUtils.hasText(style) ? style.strip() : "智能推荐");
        candidate.setContent(StringUtils.hasText(content) ? content.strip() : "");
        return candidate;
    }

    private Mono<CommentNextAiReplyRecord> selectCandidateIfNecessary(CommentNextAiReplyRecord record,
                                                                      Integer candidateIndex) {
        if (candidateIndex == null || candidateIndex <= 0) {
            return Mono.just(record);
        }
        return selectCandidate(record, candidateIndex)
            .flatMap(client::update);
    }

    private Mono<CommentNextAiReplyRecord> selectCandidate(CommentNextAiReplyRecord record,
                                                           Integer candidateIndex) {
        var spec = record.getSpec();
        if (spec == null || spec.getReplyCandidates() == null || spec.getReplyCandidates().isEmpty()) {
            return Mono.error(new ResponseStatusException(
                HttpStatus.BAD_REQUEST,
                "当前 AI 回复记录没有可选候选"
            ));
        }

        return spec.getReplyCandidates().stream()
            .filter(candidate -> candidateIndex.equals(candidate.getIndex()))
            .findFirst()
            .map(candidate -> {
                spec.setReplyContent(candidate.getContent());
                spec.setSelectedCandidateIndex(candidate.getIndex());
                spec.setReplyStyle(candidate.getStyle());
                spec.setError(null);
                return Mono.just(record);
            })
            .orElseGet(() -> Mono.error(new ResponseStatusException(
                HttpStatus.BAD_REQUEST,
                "未找到指定的 AI 回复候选"
            )));
    }

    private CommentNextAiReplyRecord draftRecord(CommentNextAiReplyRecord.TargetType targetType,
                                                 CommentNextAiReplyRecord.TriggerType triggerType,
                                                 String targetName,
                                                 String commentName,
                                                 String quoteReplyName,
                                                 String subject,
                                                 String authorName,
                                                 String sourceContent,
                                                 CommentNextAiAssistantProfile assistant,
                                                 String publishMode) {
        var record = new CommentNextAiReplyRecord();
        var metadata = new Metadata();
        if (CommentNextAiReplyRecord.TriggerType.MANUAL == triggerType) {
            metadata.setGenerateName("comment-next-ai-reply-");
        } else {
            metadata.setName(recordName(triggerType.name(), targetType.name(), targetName));
        }
        record.setMetadata(metadata);
        record.setApiVersion(CommentNextAiReplyRecord.GROUP + "/" + CommentNextAiReplyRecord.VERSION);
        record.setKind(CommentNextAiReplyRecord.KIND);

        var spec = new CommentNextAiReplyRecord.Spec();
        spec.setTargetType(targetType.name());
        spec.setTriggerType(triggerType.name());
        spec.setTargetName(targetName);
        spec.setCommentName(commentName);
        spec.setQuoteReplyName(quoteReplyName);
        spec.setSubject(subject);
        spec.setAuthorName(authorName);
        spec.setSourceContent(sourceContent);
        spec.setReplyContent(" ");
        spec.setAssistantName(assistant.displayName());
        spec.setAssistantUserName(assistant.username());
        spec.setStatus(CommentNextAiReplyRecord.Status.PENDING_REVIEW.name());
        spec.setPublishMode(CommentNextAiReplyRecord.PublishMode.AUTO.name().equals(publishMode)
            ? CommentNextAiReplyRecord.PublishMode.AUTO.name()
            : CommentNextAiReplyRecord.PublishMode.REVIEW.name());
        spec.setReplyStyle("智能推荐");
        spec.setSelectedCandidateIndex(1);
        spec.setCandidateCount(0);
        spec.setCreationTime(Instant.now());
        spec.setGeneratedAt(Instant.now());
        record.setSpec(spec);
        return record;
    }

    private Mono<Boolean> hasRecord(String triggerType, String targetType, String targetName) {
        return client.listAll(CommentNextAiReplyRecord.class, targetOptions(triggerType, targetType, targetName),
                Sort.by("metadata.name"))
            .hasElements();
    }

    private ListOptions targetOptions(String triggerType, String targetType, String targetName) {
        return ListOptions.builder()
            .andQuery(ExtensionUtil.notDeleting())
            .andQuery(equal("spec.triggerType", triggerType))
            .andQuery(equal("spec.targetType", targetType))
            .andQuery(equal("spec.targetName", targetName))
            .build();
    }

    private ListOptions notDeletingOptions() {
        return ListOptions.builder()
            .andQuery(ExtensionUtil.notDeleting())
            .build();
    }

    private boolean matchesQuery(CommentNextAiReplyRecord record,
                                 CommentNextAiReplyRecordQuery query) {
        var spec = record.getSpec();
        if (spec == null) {
            return false;
        }
        if (query.target() != CommentNextAiReplyRecordQuery.Target.ALL
            && !query.target().name().equals(spec.getTargetType())) {
            return false;
        }
        if (query.trigger() != CommentNextAiReplyRecordQuery.Trigger.ALL
            && !query.trigger().name().equals(spec.getTriggerType())) {
            return false;
        }
        return query.status() == CommentNextAiReplyRecordQuery.Status.ALL
            || query.status().name().equals(spec.getStatus());
    }

    private boolean matchesKeyword(CommentNextAiReplyRecord record, String keyword) {
        if (!StringUtils.hasText(keyword)) {
            return true;
        }
        var normalizedKeyword = keyword.strip().toLowerCase();
        var spec = record.getSpec();
        return contains(record.getMetadata().getName(), normalizedKeyword)
            || contains(spec.getTargetName(), normalizedKeyword)
            || contains(spec.getCommentName(), normalizedKeyword)
            || contains(spec.getQuoteReplyName(), normalizedKeyword)
            || contains(spec.getAuthorName(), normalizedKeyword)
            || contains(spec.getSourceContent(), normalizedKeyword)
            || contains(spec.getReplyContent(), normalizedKeyword)
            || contains(spec.getAssistantName(), normalizedKeyword)
            || contains(spec.getError(), normalizedKeyword);
    }

    private Comparator<CommentNextAiReplyRecord> recordComparator() {
        return Comparator
            .comparing(
                (CommentNextAiReplyRecord record) -> safeInstant(record.getSpec().getCreationTime()),
                Comparator.reverseOrder()
            )
            .thenComparing(record -> record.getMetadata().getName());
    }

    private Mono<Reply> createAssistantReply(CommentNextAiReplyRecord record,
                                             CommentNextAiAssistantProfile assistant) {
        var sourceSpec = record.getSpec();
        var reply = new Reply();
        var metadata = new Metadata();
        metadata.setGenerateName("comment-next-ai-");
        reply.setMetadata(metadata);
        reply.setApiVersion(CONTENT_API_VERSION);
        reply.setKind(Reply.KIND);

        var spec = new Reply.ReplySpec();
        var normalizedContent = toCommentHtml(sourceSpec.getReplyContent());
        spec.setCommentName(sourceSpec.getCommentName());
        spec.setQuoteReply(sourceSpec.getQuoteReplyName());
        spec.setRaw(normalizedContent);
        spec.setContent(normalizedContent);
        spec.setOwner(assistantOwner(assistant));
        spec.setAllowNotification(true);
        spec.setApproved(true);
        spec.setApprovedTime(Instant.now());
        spec.setCreationTime(Instant.now());
        spec.setHidden(false);
        spec.setPriority(0);
        spec.setTop(false);
        reply.setSpec(spec);
        return client.create(reply);
    }

    private Mono<CommentNextAiReplyRecord> markPublished(String recordName, String replyName) {
        return client.fetch(CommentNextAiReplyRecord.class, recordName)
            .switchIfEmpty(Mono.error(new ResponseStatusException(HttpStatus.NOT_FOUND)))
            .flatMap(record -> {
                var spec = record.getSpec();
                spec.setStatus(CommentNextAiReplyRecord.Status.PUBLISHED.name());
                spec.setReplyName(replyName);
                spec.setError(null);
                spec.setReviewedAt(Instant.now());
                spec.setPublishedAt(Instant.now());
                return client.update(record);
            });
    }

    private Mono<CommentNextAiReplyRecord> markFailed(String recordName, Throwable error) {
        return client.fetch(CommentNextAiReplyRecord.class, recordName)
            .switchIfEmpty(Mono.empty())
            .flatMap(record -> {
                var spec = record.getSpec();
                spec.setStatus(CommentNextAiReplyRecord.Status.FAILED.name());
                spec.setError(errorMessage(error));
                return client.update(record);
            });
    }

    private boolean shouldSkip(run.halo.app.extension.AbstractExtension source,
                               Comment.CommentOwner owner,
                               CommentNextAiAssistantProfile assistant) {
        if (isAssistantOwner(owner, assistant)) {
            return true;
        }
        var content = source instanceof Comment comment
            ? comment.getSpec().getContent()
            : source instanceof Reply reply ? reply.getSpec().getContent() : "";
        return containsAssistantMention(content, assistant);
    }

    private boolean containsAssistantMention(String html, CommentNextAiAssistantProfile assistant) {
        var sourceText = htmlToText(html);
        if (!StringUtils.hasText(sourceText)) {
            return false;
        }
        return sourceText.toLowerCase().contains(assistant.mentionName().toLowerCase());
    }

    private boolean isProcessableComment(Comment comment) {
        return isUsableComment(comment)
            && Boolean.TRUE.equals(comment.getSpec().getApproved())
            && !Boolean.TRUE.equals(comment.getSpec().getHidden());
    }

    private boolean isProcessableReply(Reply reply) {
        return isUsableReply(reply)
            && Boolean.TRUE.equals(reply.getSpec().getApproved())
            && !Boolean.TRUE.equals(reply.getSpec().getHidden());
    }

    private boolean isUsableComment(Comment comment) {
        return comment != null
            && !ExtensionUtil.isDeleted(comment)
            && comment.getMetadata() != null
            && comment.getSpec() != null
            && StringUtils.hasText(comment.getMetadata().getName());
    }

    private boolean isUsableReply(Reply reply) {
        return reply != null
            && !ExtensionUtil.isDeleted(reply)
            && reply.getMetadata() != null
            && reply.getSpec() != null
            && StringUtils.hasText(reply.getMetadata().getName())
            && StringUtils.hasText(reply.getSpec().getCommentName());
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

    private Comment.CommentOwner assistantOwner(CommentNextAiAssistantProfile assistant) {
        var owner = new Comment.CommentOwner();
        owner.setKind(assistant.hasUser() ? User.KIND : Comment.CommentOwner.KIND_EMAIL);
        owner.setName(assistant.hasUser() ? assistant.username() : ASSISTANT_EMAIL);
        owner.setDisplayName(assistant.displayName());
        return owner;
    }

    private String sourceText(String html, SettingConfigGetter.AiConfig config) {
        var limit = config.getAutoReply().normalizedMaxInputLength(config.normalizedMaxInputLength());
        var text = htmlToText(html);
        if (text.length() <= limit) {
            return text;
        }
        return text.substring(0, Math.max(1, limit)).strip();
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

    private String toCommentHtml(String value) {
        var text = StringUtils.hasText(value) ? value.strip() : "";
        return HtmlUtils.htmlEscape(text)
            .replace("\n", "<br>");
    }

    private String recordName(String triggerType, String targetType, String targetName) {
        var key = triggerType + ":" + targetType + ":" + targetName;
        return "comment-next-ai-reply-"
            + UUID.nameUUIDFromBytes(key.getBytes(StandardCharsets.UTF_8));
    }

    private String displayTargetType(String targetType) {
        return CommentNextAiReplyRecord.TargetType.REPLY.name().equals(targetType) ? "回复" : "评论";
    }

    private boolean contains(String value, String keyword) {
        return StringUtils.hasText(value)
            && value.toLowerCase().contains(keyword);
    }

    private String errorMessage(Throwable error) {
        var message = error == null ? "" : error.getMessage();
        if (!StringUtils.hasText(message)) {
            return "AI 自动回复失败";
        }
        return message.length() > 180 ? message.substring(0, 180) : message;
    }

    private Instant safeInstant(Instant instant) {
        return instant == null ? Instant.EPOCH : instant;
    }

    private String nullToEmpty(String value) {
        return value == null ? "" : value;
    }
}
