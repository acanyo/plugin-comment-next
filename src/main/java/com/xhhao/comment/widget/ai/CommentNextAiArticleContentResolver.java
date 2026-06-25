package com.xhhao.comment.widget.ai;

import java.util.regex.Pattern;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.web.util.HtmlUtils;
import reactor.core.publisher.Mono;
import run.halo.app.content.ContentWrapper;
import run.halo.app.core.extension.content.Post;
import run.halo.app.core.extension.content.SinglePage;
import run.halo.app.core.extension.content.Snapshot;
import run.halo.app.extension.ReactiveExtensionClient;

@Service
@RequiredArgsConstructor
public class CommentNextAiArticleContentResolver {
    private static final int MAX_ARTICLE_CONTENT_LENGTH = 12000;

    private static final Pattern SCRIPT_OR_STYLE_PATTERN =
        Pattern.compile("(?is)<(script|style)[^>]*>.*?</\\1>");

    private static final Pattern BLOCK_TAG_PATTERN =
        Pattern.compile(
            "(?i)</?(p|div|section|article|header|footer|blockquote|li|ul|ol|h[1-6]|br|pre|table|tr)[^>]*>"
        );

    private static final Pattern HTML_TAG_PATTERN = Pattern.compile("(?s)<[^>]+>");

    private static final Pattern INLINE_SPACE_PATTERN = Pattern.compile("[\\t\\x0B\\f\\r ]+");

    private final ReactiveExtensionClient client;

    public Mono<CommentNextAiArticleContext> resolve(String subject, int maxInputLength) {
        var ref = CommentNextAiSubjectRef.parse(subject);
        if (!ref.isSupportedContent()) {
            return Mono.error(new ResponseStatusException(
                HttpStatus.BAD_REQUEST,
                "总结文章评论仅支持文章或页面"
            ));
        }

        return ref.isPost()
            ? resolvePost(ref, maxInputLength)
            : resolveSinglePage(ref, maxInputLength);
    }

    private Mono<CommentNextAiArticleContext> resolvePost(CommentNextAiSubjectRef ref,
                                                          int maxInputLength) {
        return client.fetch(Post.class, ref.name())
            .switchIfEmpty(Mono.error(() -> new ResponseStatusException(
                HttpStatus.NOT_FOUND,
                "未找到要总结的文章"
            )))
            .flatMap(post -> {
                var spec = post.getSpec();
                var title = spec == null ? "" : spec.getTitle();
                var snapshotName = spec == null
                    ? ""
                    : firstText(spec.getReleaseSnapshot(), spec.getHeadSnapshot(), spec.getBaseSnapshot());
                var baseSnapshotName = spec == null ? "" : spec.getBaseSnapshot();
                var excerpt = spec == null ? "" : excerptRaw(spec.getExcerpt());
                return resolveContent(snapshotName, baseSnapshotName, excerpt, maxInputLength)
                    .map(content -> new CommentNextAiArticleContext(title, ref.displayType(), content));
            });
    }

    private Mono<CommentNextAiArticleContext> resolveSinglePage(CommentNextAiSubjectRef ref,
                                                               int maxInputLength) {
        return client.fetch(SinglePage.class, ref.name())
            .switchIfEmpty(Mono.error(() -> new ResponseStatusException(
                HttpStatus.NOT_FOUND,
                "未找到要总结的页面"
            )))
            .flatMap(singlePage -> {
                var spec = singlePage.getSpec();
                var title = spec == null ? "" : spec.getTitle();
                var snapshotName = spec == null
                    ? ""
                    : firstText(spec.getReleaseSnapshot(), spec.getHeadSnapshot(), spec.getBaseSnapshot());
                var baseSnapshotName = spec == null ? "" : spec.getBaseSnapshot();
                var excerpt = spec == null ? "" : excerptRaw(spec.getExcerpt());
                return resolveContent(snapshotName, baseSnapshotName, excerpt, maxInputLength)
                    .map(content -> new CommentNextAiArticleContext(title, ref.displayType(), content));
            });
    }

    private Mono<String> resolveContent(String snapshotName,
                                        String baseSnapshotName,
                                        String fallbackText,
                                        int maxInputLength) {
        var normalizedFallback = normalizeArticleText(fallbackText);
        var content = StringUtils.hasText(snapshotName)
            ? fetchSnapshotContent(snapshotName, baseSnapshotName)
                .map(this::extractContent)
                .onErrorResume(error -> StringUtils.hasText(normalizedFallback)
                    ? Mono.just(normalizedFallback)
                    : Mono.error(error))
            : Mono.<String>empty();

        return content
            .filter(StringUtils::hasText)
            .switchIfEmpty(StringUtils.hasText(normalizedFallback)
                ? Mono.just(normalizedFallback)
                : Mono.empty())
            .map(value -> truncateContent(value, maxInputLength))
            .filter(StringUtils::hasText)
            .switchIfEmpty(Mono.error(() -> new ResponseStatusException(
                HttpStatus.BAD_REQUEST,
                "未找到可用于总结的文章内容"
            )));
    }

    private Mono<ContentWrapper> fetchSnapshotContent(String snapshotName, String baseSnapshotName) {
        return client.fetch(Snapshot.class, snapshotName)
            .switchIfEmpty(Mono.error(() -> new ResponseStatusException(
                HttpStatus.NOT_FOUND,
                "未找到文章内容快照"
            )))
            .flatMap(snapshot -> {
                var resolvedBaseSnapshotName = firstText(
                    baseSnapshotName,
                    snapshot.getSpec() == null ? "" : snapshot.getSpec().getParentSnapshotName()
                );

                if (Snapshot.isBaseSnapshot(snapshot)
                    || !StringUtils.hasText(resolvedBaseSnapshotName)
                    || snapshotName.equals(resolvedBaseSnapshotName)) {
                    return Mono.just(ContentWrapper.patchSnapshot(snapshot, snapshot));
                }

                return client.fetch(Snapshot.class, resolvedBaseSnapshotName)
                    .map(baseSnapshot -> ContentWrapper.patchSnapshot(snapshot, baseSnapshot))
                    .switchIfEmpty(Mono.error(() -> new ResponseStatusException(
                        HttpStatus.NOT_FOUND,
                        "未找到文章基础内容快照"
                    )));
            });
    }

    private String extractContent(ContentWrapper wrapper) {
        return normalizeArticleText(firstText(wrapper.getContent(), wrapper.getRaw()));
    }

    private String excerptRaw(Post.Excerpt excerpt) {
        return excerpt == null ? "" : excerpt.getRaw();
    }

    private String normalizeArticleText(String value) {
        if (!StringUtils.hasText(value)) {
            return "";
        }

        var text = SCRIPT_OR_STYLE_PATTERN.matcher(value).replaceAll(" ");
        text = BLOCK_TAG_PATTERN.matcher(text).replaceAll("\n");
        text = HTML_TAG_PATTERN.matcher(text).replaceAll(" ");
        text = HtmlUtils.htmlUnescape(text).replace('\u00A0', ' ');
        text = INLINE_SPACE_PATTERN.matcher(text).replaceAll(" ");
        return text.lines()
            .map(String::strip)
            .filter(StringUtils::hasText)
            .collect(Collectors.joining("\n"))
            .strip();
    }

    private String truncateContent(String value, int maxInputLength) {
        var limit = Math.max(1, Math.min(maxInputLength, MAX_ARTICLE_CONTENT_LENGTH));
        if (value.length() <= limit) {
            return value;
        }

        return value.substring(0, limit).strip()
            + "\n\n（文章内容较长，已按站点 AI 输入设置截取前 " + limit + " 字。）";
    }

    private String firstText(String... values) {
        for (var value : values) {
            if (StringUtils.hasText(value)) {
                return value.trim();
            }
        }
        return "";
    }
}
