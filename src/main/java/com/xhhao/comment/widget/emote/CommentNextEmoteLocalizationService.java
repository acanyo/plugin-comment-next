package com.xhhao.comment.widget.emote;

import static run.halo.app.extension.index.query.Queries.equal;

import com.xhhao.comment.widget.SettingConfigGetter;
import jakarta.annotation.PreDestroy;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicBoolean;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Sort;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.web.server.ServerWebInputException;
import reactor.core.Disposable;
import reactor.core.Disposables;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.core.scheduler.Schedulers;
import run.halo.app.core.extension.attachment.Attachment;
import run.halo.app.core.extension.attachment.Group;
import run.halo.app.core.extension.attachment.Policy;
import run.halo.app.extension.ExtensionUtil;
import run.halo.app.extension.ListOptions;
import run.halo.app.extension.ReactiveExtensionClient;

/**
 * Runs background emote image localization jobs into Halo attachments with progress reporting.
 */
@Slf4j
@Service
@RequiredArgsConstructor
class CommentNextEmoteLocalizationService {

    private static final int DOWNLOAD_CONCURRENCY = 4;
    private static final int MAX_RECORDED_FAILURES = 20;
    private static final String DEFAULT_POLICY_NAME = "default-policy";

    private final ReactiveExtensionClient client;
    private final SettingConfigGetter settings;
    private final CommentNextEmoteAssetFetcher assetFetcher;
    private final CommentNextEmoteAttachmentUploader uploader;

    private final Disposable.Swap activeTask = Disposables.swap();
    private volatile Progress progress = Progress.idle();

    Mono<LocalizationOptions> options(URI requestBaseUri) {
        var notDeleting = ListOptions.builder()
            .andQuery(ExtensionUtil.notDeleting())
            .build();

        var policiesMono = client.listAll(Policy.class, notDeleting, Sort.by("metadata.name"))
            .map(policy -> new PolicyOption(
                policy.getMetadata().getName(),
                policy.getSpec() != null && StringUtils.hasText(policy.getSpec().getDisplayName())
                    ? policy.getSpec().getDisplayName()
                    : policy.getMetadata().getName()
            ))
            .sort(Comparator.comparing(PolicyOption::displayName, String.CASE_INSENSITIVE_ORDER))
            .collectList();

        var groupsMono = client.listAll(Group.class, notDeleting, Sort.by("metadata.name"))
            .filter(group -> group.getMetadata() == null
                || group.getMetadata().getLabels() == null
                || !"true".equalsIgnoreCase(group.getMetadata().getLabels().get(Group.HIDDEN_LABEL)))
            .map(group -> new AttachmentGroupOption(
                group.getMetadata().getName(),
                group.getSpec() != null && StringUtils.hasText(group.getSpec().getDisplayName())
                    ? group.getSpec().getDisplayName()
                    : group.getMetadata().getName()
            ))
            .sort(Comparator.comparing(AttachmentGroupOption::displayName, String.CASE_INSENSITIVE_ORDER))
            .collectList();

        var uploadConfigMono = settings.getUploadConfig()
            .defaultIfEmpty(SettingConfigGetter.UploadConfig.empty());

        return Mono.zip(policiesMono, groupsMono, uploadConfigMono)
            .map(tuple -> {
                var policies = tuple.getT1();
                var groups = tuple.getT2();
                var haloConfig = tuple.getT3().getHaloAttachment();

                var defaultPolicy = resolveDefaultPolicyName(policies, haloConfig.getPolicyName());
                var defaultGroup = resolveDefaultGroupName(groups, haloConfig.getGroupName());
                var resolvedBaseUri = uploader.resolveSiteBaseUri(requestBaseUri);

                return new LocalizationOptions(
                    policies,
                    groups,
                    defaultPolicy,
                    defaultGroup,
                    resolvedBaseUri == null ? "" : stripTrailingSlash(resolvedBaseUri.toString()),
                    uploader.hasConfiguredExternalUrl()
                );
            });
    }

    Mono<Progress> status() {
        return Mono.fromSupplier(() -> progress);
    }

    Mono<Progress> cancel() {
        return Mono.fromSupplier(() -> {
            synchronized (this) {
                activeTask.update(Disposables.disposed());
                if (progress.state() == State.RUNNING) {
                    progress = progress.finish(State.CANCELLED, null);
                }
                return progress;
            }
        });
    }

    Mono<Progress> start(StartRequest request, String username, URI requestBaseUri) {
        if (request == null) {
            return Mono.error(new ServerWebInputException("本地化请求不能为空"));
        }
        var groupNames = sanitizeGroupNames(request.groupNames());
        if (groupNames.isEmpty()) {
            return Mono.error(new ServerWebInputException("请至少选择一个表情分类"));
        }
        var policyName = request.policyName() == null ? "" : request.policyName().trim();
        if (!StringUtils.hasText(policyName)) {
            return Mono.error(new ServerWebInputException("请选择 Halo 附件存储策略"));
        }
        var attachmentGroupName = StringUtils.hasText(request.attachmentGroupName())
            ? request.attachmentGroupName().trim()
            : "";
        var useAbsoluteUrl = request.useAbsoluteUrl() == null || request.useAbsoluteUrl();
        var effectiveUsername = StringUtils.hasText(username) ? username.trim() : "admin";

        return client.fetch(Policy.class, policyName)
            .filter(policy -> !ExtensionUtil.isDeleted(policy))
            .switchIfEmpty(Mono.error(new ServerWebInputException("所选存储策略不存在：" + policyName)))
            .then(StringUtils.hasText(attachmentGroupName)
                ? client.fetch(Group.class, attachmentGroupName)
                    .filter(group -> !ExtensionUtil.isDeleted(group))
                    .switchIfEmpty(Mono.error(new ServerWebInputException("所选附件分组不存在：" + attachmentGroupName)))
                    .then()
                : Mono.empty())
            .then(Mono.fromSupplier(() -> startBackgroundJob(
                groupNames,
                policyName,
                attachmentGroupName,
                useAbsoluteUrl,
                effectiveUsername,
                requestBaseUri
            )));
    }

    private synchronized Progress startBackgroundJob(List<String> groupNames,
                                                     String policyName,
                                                     String attachmentGroupName,
                                                     boolean useAbsoluteUrl,
                                                     String username,
                                                     URI requestBaseUri) {
        if (progress.state() == State.RUNNING) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "已有表情本地化任务正在运行，请等待完成或先停止");
        }

        var initial = Progress.starting(
            groupNames,
            policyName,
            attachmentGroupName,
            useAbsoluteUrl
        );
        progress = initial;

        var disposable = Mono.defer(() -> executeJob(
                groupNames,
                policyName,
                attachmentGroupName,
                useAbsoluteUrl,
                username,
                requestBaseUri
            ))
            .subscribeOn(Schedulers.boundedElastic())
            .subscribe(
                null,
                this::finishWithError,
                this::finishCompleted
            );
        activeTask.update(disposable);
        return initial;
    }

    private Mono<Void> executeJob(List<String> groupNames,
                                  String policyName,
                                  String attachmentGroupName,
                                  boolean useAbsoluteUrl,
                                  String username,
                                  URI requestBaseUri) {
        var siteBaseUri = uploader.resolveSiteBaseUri(requestBaseUri);

        return Flux.fromIterable(groupNames)
            .concatMap(name -> client.fetch(CommentNextEmoteGroup.class, name)
                .filter(group -> !ExtensionUtil.isDeleted(group))
                .filter(this::isLocalizableImageGroup))
            .collectList()
            .flatMap(groups -> {
                var plans = groups.stream()
                    .map(group -> planGroup(group, siteBaseUri))
                    .toList();
                var totalItems = plans.stream().mapToInt(GroupPlan::totalItems).sum();
                var initialSkipped = plans.stream().mapToInt(GroupPlan::skippedItems).sum();
                updateProgress(current -> current.withPlan(
                    plans.size(),
                    totalItems,
                    initialSkipped
                ));

                if (plans.isEmpty() || totalItems == initialSkipped) {
                    return Mono.empty();
                }

                return loadExistingSourceMap(attachmentGroupName, useAbsoluteUrl, requestBaseUri)
                    .flatMap(existingByUrl -> {
                        var urlCache = new ConcurrentHashMap<String, Mono<UploadOutcome>>();
                        return Flux.fromIterable(plans)
                            .concatMap(plan -> processGroup(
                                plan,
                                policyName,
                                attachmentGroupName,
                                useAbsoluteUrl,
                                username,
                                requestBaseUri,
                                existingByUrl,
                                urlCache
                            ))
                            .then();
                    });
            });
    }

    private Mono<Map<String, String>> loadExistingSourceMap(String attachmentGroupName,
                                                            boolean useAbsoluteUrl,
                                                            URI requestBaseUri) {
        if (!StringUtils.hasText(attachmentGroupName)) {
            return Mono.just(Map.of());
        }
        var options = ListOptions.builder()
            .andQuery(ExtensionUtil.notDeleting())
            .andQuery(equal("spec.groupName", attachmentGroupName))
            .build();

        return client.listAll(Attachment.class, options, Sort.by("metadata.name"))
            .filter(attachment -> attachment.getMetadata() != null
                && attachment.getMetadata().getAnnotations() != null
                && StringUtils.hasText(attachment.getMetadata().getAnnotations()
                    .get(CommentNextEmoteAttachmentUploader.SOURCE_URL_ANNOTATION)))
            .concatMap(attachment -> {
                var sourceUrl = attachment.getMetadata().getAnnotations()
                    .get(CommentNextEmoteAttachmentUploader.SOURCE_URL_ANNOTATION);
                return uploader.resolveExistingPermalink(attachment, useAbsoluteUrl, requestBaseUri)
                    .map(url -> Map.entry(sourceUrl, url))
                    .onErrorResume(error -> Mono.empty());
            })
            .<Map<String, String>>collect(
                LinkedHashMap::new,
                (map, entry) -> map.putIfAbsent(entry.getKey(), entry.getValue())
            )
            .onErrorResume(error -> {
                log.debug("Failed to preload existing attachments for group {}: {}",
                    attachmentGroupName, summarizeError(error));
                return Mono.just(Map.of());
            });
    }

    private Mono<Void> processGroup(GroupPlan plan,
                                    String policyName,
                                    String attachmentGroupName,
                                    boolean useAbsoluteUrl,
                                    String username,
                                    URI requestBaseUri,
                                    Map<String, String> existingByUrl,
                                    ConcurrentHashMap<String, Mono<UploadOutcome>> urlCache) {
        updateProgress(current -> current.withCurrentGroup(plan.groupName(), plan.displayName()));

        if (plan.targets().isEmpty()) {
            updateProgress(Progress::incrementCompletedGroups);
            return Mono.empty();
        }

        return Flux.fromIterable(plan.targets())
            .flatMap(target -> localizeItem(
                    plan,
                    target,
                    policyName,
                    attachmentGroupName,
                    useAbsoluteUrl,
                    username,
                    requestBaseUri,
                    existingByUrl,
                    urlCache
                ), DOWNLOAD_CONCURRENCY)
            .collectList()
            .flatMap(results -> applyGroupUpdates(plan, results))
            .doOnSuccess(ignored -> updateProgress(Progress::incrementCompletedGroups));
    }

    private Mono<ItemOutcome> localizeItem(GroupPlan plan,
                                           TargetItem target,
                                           String policyName,
                                           String attachmentGroupName,
                                           boolean useAbsoluteUrl,
                                           String username,
                                           URI requestBaseUri,
                                           Map<String, String> existingByUrl,
                                           ConcurrentHashMap<String, Mono<UploadOutcome>> urlCache) {
        var existingUrl = existingByUrl.get(target.sourceUrl());
        if (StringUtils.hasText(existingUrl)) {
            updateProgress(Progress::incrementReused);
            return Mono.just(ItemOutcome.success(target.itemIndex(), existingUrl));
        }

        var shared = urlCache.computeIfAbsent(target.sourceUrl(), url ->
            assetFetcher.fetchImage(url)
                .flatMap(downloaded -> {
                    var filename = buildAttachmentFilename(
                        plan.displayName(),
                        target.itemText(),
                        url,
                        downloaded.extension()
                    );
                    return uploader.upload(
                        username,
                        policyName,
                        attachmentGroupName,
                        filename,
                        downloaded.bytes(),
                        downloaded.mediaType(),
                        url,
                        useAbsoluteUrl,
                        requestBaseUri
                    );
                })
                .map(localizedUrl -> new UploadOutcome(localizedUrl, new AtomicBoolean(false)))
                .cache()
        );

        return shared
            .map(outcome -> {
                if (outcome.countedAsUploaded().compareAndSet(false, true)) {
                    updateProgress(Progress::incrementLocalized);
                } else {
                    updateProgress(Progress::incrementReused);
                }
                return ItemOutcome.success(target.itemIndex(), outcome.localizedUrl());
            })
            .onErrorResume(error -> {
                var reason = summarizeError(error);
                log.debug("Failed to localize emote image {} in group {}: {}",
                    target.sourceUrl(), plan.groupName(), reason);
                updateProgress(current -> current.incrementFailed(new FailureItem(
                    plan.groupName(),
                    plan.displayName(),
                    target.itemText(),
                    target.sourceUrl(),
                    reason
                )));
                return Mono.just(ItemOutcome.failed(target.itemIndex()));
            });
    }

    private Mono<Void> applyGroupUpdates(GroupPlan plan, List<ItemOutcome> outcomes) {
        var replacements = new LinkedHashMap<Integer, String>();
        for (var outcome : outcomes) {
            if (outcome.succeeded() && StringUtils.hasText(outcome.localizedUrl())) {
                replacements.put(outcome.itemIndex(), outcome.localizedUrl());
            }
        }
        if (replacements.isEmpty()) {
            return Mono.empty();
        }

        return client.fetch(CommentNextEmoteGroup.class, plan.groupName())
            .filter(group -> !ExtensionUtil.isDeleted(group))
            .flatMap(group -> {
                var spec = group.getSpec();
                if (spec == null || spec.getItems() == null) {
                    return Mono.empty();
                }
                var items = spec.getItems();
                boolean changed = false;
                for (var entry : replacements.entrySet()) {
                    int index = entry.getKey();
                    if (index >= 0 && index < items.size()) {
                        var item = items.get(index);
                        if (item != null && StringUtils.hasText(item.getIcon())) {
                            var updatedIcon = CommentNextEmoteHtmlUrlCodec.replaceImageUrl(
                                item.getIcon(),
                                entry.getValue()
                            );
                            if (!updatedIcon.equals(item.getIcon())) {
                                item.setIcon(updatedIcon);
                                changed = true;
                            }
                        }
                    }
                }
                if (!changed) {
                    return Mono.empty();
                }
                return client.update(group).then();
            });
    }

    private GroupPlan planGroup(CommentNextEmoteGroup group, URI siteBaseUri) {
        var groupName = group.getMetadata().getName();
        var displayName = Optional.ofNullable(group.getSpec())
            .map(CommentNextEmoteGroup.Spec::getDisplayName)
            .filter(StringUtils::hasText)
            .orElse(groupName);
        var items = Optional.ofNullable(group.getSpec())
            .map(CommentNextEmoteGroup.Spec::getItems)
            .orElseGet(List::of);

        var targets = new ArrayList<TargetItem>();
        int skipped = 0;
        for (int index = 0; index < items.size(); index++) {
            var item = items.get(index);
            if (item == null || !StringUtils.hasText(item.getIcon())) {
                skipped += 1;
                continue;
            }
            var preferredUrl = CommentNextEmoteHtmlUrlCodec.extractPreferredUrl(item.getIcon());
            if (!CommentNextEmoteHtmlUrlCodec.isExternalUrl(preferredUrl, siteBaseUri)) {
                skipped += 1;
                continue;
            }
            targets.add(new TargetItem(
                index,
                Optional.ofNullable(item.getText()).orElse(""),
                preferredUrl
            ));
        }

        return new GroupPlan(groupName, displayName, items.size(), skipped, targets);
    }

    private boolean isLocalizableImageGroup(CommentNextEmoteGroup group) {
        var spec = group.getSpec();
        if (spec == null) {
            return false;
        }
        return !"LOTTIE".equalsIgnoreCase(spec.getProvider())
            && "image".equalsIgnoreCase(spec.getType());
    }

    private static List<String> sanitizeGroupNames(List<String> raw) {
        if (raw == null || raw.isEmpty()) {
            return List.of();
        }
        var unique = new LinkedHashSet<String>();
        for (var item : raw) {
            if (StringUtils.hasText(item)) {
                unique.add(item.trim());
            }
        }
        return List.copyOf(unique);
    }

    private static String resolveDefaultPolicyName(List<PolicyOption> policies, String configuredPolicy) {
        if (StringUtils.hasText(configuredPolicy)
            && policies.stream().anyMatch(p -> p.name().equals(configuredPolicy))) {
            return configuredPolicy;
        }
        if (policies.stream().anyMatch(p -> DEFAULT_POLICY_NAME.equals(p.name()))) {
            return DEFAULT_POLICY_NAME;
        }
        return policies.isEmpty() ? "" : policies.getFirst().name();
    }

    private static String resolveDefaultGroupName(List<AttachmentGroupOption> groups, String configuredGroup) {
        if (StringUtils.hasText(configuredGroup)
            && groups.stream().anyMatch(g -> g.name().equals(configuredGroup))) {
            return configuredGroup;
        }
        return "";
    }

    static String buildAttachmentFilename(String groupDisplayName,
                                          String itemText,
                                          String sourceUrl,
                                          String extension) {
        var groupSlug = slugify(groupDisplayName, 18);
        var itemSlug = slugify(itemText, 24);
        var hash = shortSha256(sourceUrl);
        var builder = new StringBuilder("emote");
        if (StringUtils.hasText(groupSlug)) {
            builder.append('-').append(groupSlug);
        }
        if (StringUtils.hasText(itemSlug)) {
            builder.append('-').append(itemSlug);
        }
        builder.append('-').append(hash).append('.').append(extension);
        return builder.toString();
    }

    private static String slugify(String value, int maxLength) {
        if (!StringUtils.hasText(value)) {
            return "";
        }
        var normalized = value.trim()
            .toLowerCase(Locale.ROOT)
            .replaceAll("[^a-z0-9]+", "-")
            .replaceAll("^-+|-+$", "");
        if (normalized.length() > maxLength) {
            normalized = normalized.substring(0, maxLength).replaceAll("-+$", "");
        }
        return normalized;
    }

    private static String shortSha256(String value) {
        try {
            var digest = MessageDigest.getInstance("SHA-256")
                .digest(value.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(digest).substring(0, 12);
        } catch (NoSuchAlgorithmException e) {
            return Integer.toHexString(value.hashCode());
        }
    }

    private static String stripTrailingSlash(String value) {
        var result = value;
        while (result.endsWith("/")) {
            result = result.substring(0, result.length() - 1);
        }
        return result;
    }

    private static String summarizeError(Throwable error) {
        if (error == null) {
            return "未知错误";
        }
        var current = error;
        while (current.getCause() != null && !StringUtils.hasText(current.getMessage())) {
            current = current.getCause();
        }
        return StringUtils.hasText(current.getMessage())
            ? current.getMessage()
            : current.getClass().getSimpleName();
    }

    private synchronized void updateProgress(java.util.function.UnaryOperator<Progress> updater) {
        progress = updater.apply(progress);
    }

    private synchronized void finishCompleted() {
        if (progress.state() == State.RUNNING) {
            progress = progress.finish(State.COMPLETED, null);
        }
    }

    private synchronized void finishWithError(Throwable error) {
        if (progress.state() == State.CANCELLED) {
            return;
        }
        var message = summarizeError(error);
        log.warn("Emote localization job failed: {}", message);
        progress = progress.finish(State.FAILED, message);
    }

    @PreDestroy
    synchronized void stop() {
        activeTask.dispose();
    }

    enum State {
        IDLE,
        RUNNING,
        COMPLETED,
        CANCELLED,
        FAILED
    }

    record StartRequest(
        List<String> groupNames,
        String policyName,
        String attachmentGroupName,
        Boolean useAbsoluteUrl
    ) {
    }

    record PolicyOption(String name, String displayName) {
    }

    record AttachmentGroupOption(String name, String displayName) {
    }

    record LocalizationOptions(
        List<PolicyOption> policies,
        List<AttachmentGroupOption> attachmentGroups,
        String defaultPolicyName,
        String defaultAttachmentGroupName,
        String externalBaseUrl,
        boolean externalUrlConfigured
    ) {
    }

    record FailureItem(
        String groupName,
        String groupDisplayName,
        String itemText,
        String sourceUrl,
        String reason
    ) {
    }

    record Progress(
        State state,
        List<String> targetGroupNames,
        String policyName,
        String attachmentGroupName,
        boolean useAbsoluteUrl,
        int totalGroups,
        int completedGroups,
        String currentGroupName,
        String currentGroupDisplayName,
        int totalItems,
        int processedItems,
        int localizedItems,
        int reusedItems,
        int skippedItems,
        int failedItems,
        List<FailureItem> failures,
        Instant startedAt,
        Instant finishedAt,
        String errorMessage
    ) {
        static Progress idle() {
            return new Progress(
                State.IDLE,
                List.of(),
                "",
                "",
                true,
                0,
                0,
                "",
                "",
                0,
                0,
                0,
                0,
                0,
                0,
                List.of(),
                null,
                null,
                null
            );
        }

        static Progress starting(List<String> groupNames,
                                 String policyName,
                                 String attachmentGroupName,
                                 boolean useAbsoluteUrl) {
            return new Progress(
                State.RUNNING,
                List.copyOf(groupNames),
                policyName,
                attachmentGroupName,
                useAbsoluteUrl,
                groupNames.size(),
                0,
                "",
                "",
                0,
                0,
                0,
                0,
                0,
                0,
                List.of(),
                Instant.now(),
                null,
                null
            );
        }

        Progress withPlan(int totalGroups, int totalItems, int skippedItems) {
            return new Progress(
                state,
                targetGroupNames,
                policyName,
                attachmentGroupName,
                useAbsoluteUrl,
                totalGroups,
                completedGroups,
                currentGroupName,
                currentGroupDisplayName,
                totalItems,
                skippedItems,
                localizedItems,
                reusedItems,
                skippedItems,
                failedItems,
                failures,
                startedAt,
                finishedAt,
                errorMessage
            );
        }

        Progress withCurrentGroup(String groupName, String groupDisplayName) {
            return new Progress(
                state,
                targetGroupNames,
                policyName,
                attachmentGroupName,
                useAbsoluteUrl,
                totalGroups,
                completedGroups,
                groupName,
                groupDisplayName,
                totalItems,
                processedItems,
                localizedItems,
                reusedItems,
                skippedItems,
                failedItems,
                failures,
                startedAt,
                finishedAt,
                errorMessage
            );
        }

        Progress incrementCompletedGroups() {
            return new Progress(
                state,
                targetGroupNames,
                policyName,
                attachmentGroupName,
                useAbsoluteUrl,
                totalGroups,
                Math.min(totalGroups, completedGroups + 1),
                currentGroupName,
                currentGroupDisplayName,
                totalItems,
                processedItems,
                localizedItems,
                reusedItems,
                skippedItems,
                failedItems,
                failures,
                startedAt,
                finishedAt,
                errorMessage
            );
        }

        Progress incrementLocalized() {
            return new Progress(
                state,
                targetGroupNames,
                policyName,
                attachmentGroupName,
                useAbsoluteUrl,
                totalGroups,
                completedGroups,
                currentGroupName,
                currentGroupDisplayName,
                totalItems,
                processedItems + 1,
                localizedItems + 1,
                reusedItems,
                skippedItems,
                failedItems,
                failures,
                startedAt,
                finishedAt,
                errorMessage
            );
        }

        Progress incrementReused() {
            return new Progress(
                state,
                targetGroupNames,
                policyName,
                attachmentGroupName,
                useAbsoluteUrl,
                totalGroups,
                completedGroups,
                currentGroupName,
                currentGroupDisplayName,
                totalItems,
                processedItems + 1,
                localizedItems,
                reusedItems + 1,
                skippedItems,
                failedItems,
                failures,
                startedAt,
                finishedAt,
                errorMessage
            );
        }

        Progress incrementFailed(FailureItem failure) {
            var nextFailures = failures;
            if (failures.size() < MAX_RECORDED_FAILURES) {
                var list = new ArrayList<>(failures);
                list.add(failure);
                nextFailures = List.copyOf(list);
            }
            return new Progress(
                state,
                targetGroupNames,
                policyName,
                attachmentGroupName,
                useAbsoluteUrl,
                totalGroups,
                completedGroups,
                currentGroupName,
                currentGroupDisplayName,
                totalItems,
                processedItems + 1,
                localizedItems,
                reusedItems,
                skippedItems,
                failedItems + 1,
                nextFailures,
                startedAt,
                finishedAt,
                errorMessage
            );
        }

        Progress finish(State terminalState, String error) {
            return new Progress(
                terminalState,
                targetGroupNames,
                policyName,
                attachmentGroupName,
                useAbsoluteUrl,
                totalGroups,
                completedGroups,
                "",
                "",
                totalItems,
                processedItems,
                localizedItems,
                reusedItems,
                skippedItems,
                failedItems,
                failures,
                startedAt,
                Instant.now(),
                error
            );
        }
    }

    private record GroupPlan(
        String groupName,
        String displayName,
        int totalItems,
        int skippedItems,
        List<TargetItem> targets
    ) {
    }

    private record TargetItem(int itemIndex, String itemText, String sourceUrl) {
    }

    private record UploadOutcome(String localizedUrl, AtomicBoolean countedAsUploaded) {
    }

    private record ItemOutcome(int itemIndex, boolean succeeded, String localizedUrl) {
        static ItemOutcome success(int index, String url) {
            return new ItemOutcome(index, true, url);
        }

        static ItemOutcome failed(int index) {
            return new ItemOutcome(index, false, null);
        }
    }
}
