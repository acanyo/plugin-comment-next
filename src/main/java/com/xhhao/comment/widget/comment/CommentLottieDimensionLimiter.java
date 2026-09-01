package com.xhhao.comment.widget.comment;

import java.net.URI;
import java.util.Locale;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;

final class CommentLottieDimensionLimiter {

    private static final String LOTTIE_METADATA_PREFIX = "comment-next-lottie:";

    private CommentLottieDimensionLimiter() {
    }

    static String limit(String html, int maxWidth, int maxHeight) {
        if (html == null || html.isBlank()) {
            return html;
        }

        Document document = Jsoup.parseBodyFragment(html);
        boolean changed = false;
        for (Element element : document.body().select("halo-lottie, img[src]")) {
            if (!isLottie(element)) {
                continue;
            }

            var width = positiveDimension(element.attr("width"), 160);
            var height = positiveDimension(element.attr("height"), 160);
            var scale = Math.min(1D, Math.min(
                (double) maxWidth / width,
                (double) maxHeight / height
            ));
            if (scale >= 1D) {
                continue;
            }

            element.attr("width", Integer.toString(Math.max(1, (int) Math.round(width * scale))));
            element.attr("height", Integer.toString(Math.max(1, (int) Math.round(height * scale))));
            changed = true;
        }

        return changed ? document.body().html() : html;
    }

    private static boolean isLottie(Element element) {
        if ("halo-lottie".equals(element.tagName())) {
            return true;
        }

        var src = element.attr("src");
        var title = element.attr("title");
        if (title.startsWith(LOTTIE_METADATA_PREFIX)) {
            return true;
        }

        try {
            var path = URI.create(src).getPath();
            if (path != null && path.toLowerCase(Locale.ROOT).endsWith(".lottie")) {
                return true;
            }
            return path != null && path.startsWith("/apis/api.lottie.halo.run/v1alpha1/animations/");
        } catch (IllegalArgumentException ignored) {
            return false;
        }
    }

    private static int positiveDimension(String value, int fallback) {
        try {
            var dimension = Integer.parseInt(value);
            return dimension > 0 ? dimension : fallback;
        } catch (NumberFormatException ignored) {
            return fallback;
        }
    }
}
