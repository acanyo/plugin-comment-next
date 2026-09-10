package com.xhhao.comment.widget.comment;

import com.xhhao.comment.widget.SettingConfigGetter;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.springframework.http.HttpStatus;
import org.springframework.http.server.reactive.ServerHttpRequest;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ResponseStatusException;

@Component
class CommentLottieSubmissionSanitizer {

    private static final String LOTTIE_METADATA_PREFIX = "comment-next-lottie:";

    private final CommentLottieSourcePolicy sourcePolicy;

    CommentLottieSubmissionSanitizer(CommentLottieSourcePolicy sourcePolicy) {
        this.sourcePolicy = sourcePolicy;
    }

    String sanitize(String html, SettingConfigGetter.EmoteConfig config,
        ServerHttpRequest request) {
        if (html == null || html.isBlank()) {
            return html;
        }

        Document document = Jsoup.parseBodyFragment(html);
        document.outputSettings().prettyPrint(false);
        boolean changed = false;
        for (Element element : document.body().select("halo-lottie[src], img[src]")) {
            if (!isLottie(element)) {
                continue;
            }
            var source = element.attr("src");
            if (!sourcePolicy.isAllowed(source, request, config.allowedHostValues())) {
                throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "Lottie 动画来源不在允许列表中"
                );
            }
            changed |= limitDimensions(
                element,
                config.normalizedMaxWidth(),
                config.normalizedMaxHeight()
            );
        }

        return changed ? document.body().html() : html;
    }

    private boolean isLottie(Element element) {
        if ("halo-lottie".equals(element.tagName())) {
            return true;
        }
        return element.attr("title").startsWith(LOTTIE_METADATA_PREFIX)
            || sourcePolicy.isLottieSource(element.attr("src"));
    }

    private boolean limitDimensions(Element element, int maxWidth, int maxHeight) {
        var width = positiveDimension(element.attr("width"), 160);
        var height = positiveDimension(element.attr("height"), 160);
        var scale = Math.min(1D, Math.min(
            (double) maxWidth / width,
            (double) maxHeight / height
        ));
        if (scale >= 1D) {
            return false;
        }

        element.attr("width", Integer.toString(Math.max(1, (int) Math.round(width * scale))));
        element.attr("height", Integer.toString(Math.max(1, (int) Math.round(height * scale))));
        return true;
    }

    private int positiveDimension(String value, int fallback) {
        try {
            var dimension = Integer.parseInt(value);
            return dimension > 0 ? dimension : fallback;
        } catch (NumberFormatException ignored) {
            return fallback;
        }
    }
}
