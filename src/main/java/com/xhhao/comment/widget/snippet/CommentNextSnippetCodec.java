package com.xhhao.comment.widget.snippet;

import com.xhhao.comment.utils.JsonUtils;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.HexFormat;
import java.util.Optional;
import org.jsoup.Jsoup;
import run.halo.app.core.extension.content.Comment;

public final class CommentNextSnippetCodec {
    private static final String MARKER = "<!--comment-next-snippet:";

    private CommentNextSnippetCodec() {
    }

    public static Optional<CommentNextSnippetAnchor> read(Comment comment) {
        if (comment.getSpec() == null) return Optional.empty();
        var spec = comment.getSpec();
        var raw = spec.getRaw();
        if (raw == null || !raw.startsWith(MARKER)) return Optional.empty();
        int end = raw.indexOf("-->", MARKER.length());
        if (end < 0 || end > 30000) return Optional.empty();
        try {
            var json = URLDecoder.decode(raw.substring(MARKER.length(), end), StandardCharsets.UTF_8);
            var node = JsonUtils.createObjectMapper().readTree(json);
            if (node.path("version").asInt() != 1 || !node.path("exact").isTextual()
                || !node.path("prefix").isTextual() || !node.path("suffix").isTextual()
                || !node.path("start").canConvertToInt()) return Optional.empty();
            var anchor = new CommentNextSnippetAnchor(1, node.path("exact").asText(),
                node.path("prefix").asText(), node.path("suffix").asText(), node.path("start").asInt());
            if (anchor.exact().isBlank() || anchor.exact().codePointCount(0, anchor.exact().length()) > 1200
                || anchor.prefix().length() > 64 || anchor.suffix().length() > 64
                || anchor.start() < 0 || anchor.start() > 10000000) return Optional.empty();
            // A raw marker alone cannot turn an unrelated comment into a snippet comment.
            var quote = Jsoup.parseBodyFragment(spec.getContent() == null ? "" : spec.getContent())
                .body().selectFirst("blockquote");
            if (quote == null || !quote.text().replaceAll("\\s+", " ").strip().equals(anchor.exact())) {
                return Optional.empty();
            }
            return Optional.of(anchor);
        } catch (Exception ignored) {
            return Optional.empty();
        }
    }

    public static String key(CommentNextSnippetAnchor anchor) {
        try {
            var value = anchor.exact() + "\u0000" + anchor.prefix() + "\u0000" + anchor.suffix();
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256")
                .digest(value.getBytes(StandardCharsets.UTF_8)));
        } catch (java.security.NoSuchAlgorithmException exception) {
            throw new IllegalStateException(exception);
        }
    }
}
