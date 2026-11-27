package com.xhhao.comment.widget.emote;

import java.net.URI;
import java.util.Locale;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.springframework.util.StringUtils;

/**
 * Parses and rewrites image URLs embedded in OwO emote item icons.
 */
final class CommentNextEmoteHtmlUrlCodec {

    private CommentNextEmoteHtmlUrlCodec() {
    }

    static String extractPreferredUrl(String icon) {
        if (!StringUtils.hasText(icon)) {
            return "";
        }

        var trimmed = icon.trim();
        var image = firstImage(trimmed);
        if (image != null) {
            var origin = normalizeProtocolRelative(image.attr("origin"));
            if (StringUtils.hasText(origin)) {
                return origin;
            }
            return normalizeProtocolRelative(image.attr("src"));
        }

        if (looksLikeUrl(trimmed)) {
            return normalizeProtocolRelative(trimmed);
        }
        return "";
    }

    static boolean isExternalUrl(String rawUrl, URI siteBaseUri) {
        if (!StringUtils.hasText(rawUrl)) {
            return false;
        }

        var normalized = normalizeProtocolRelative(rawUrl);
        if (!normalized.regionMatches(true, 0, "http://", 0, 7)
            && !normalized.regionMatches(true, 0, "https://", 0, 8)) {
            return false;
        }

        if (siteBaseUri == null || !StringUtils.hasText(siteBaseUri.getHost())) {
            return true;
        }

        var candidate = parseUriHostPort(normalized);
        if (candidate == null) {
            return true;
        }

        var siteHost = siteBaseUri.getHost().toLowerCase(Locale.ROOT);
        var sitePort = effectivePort(siteBaseUri.getScheme(), siteBaseUri.getPort());
        return !(candidate.host().equals(siteHost) && candidate.port() == sitePort);
    }

    static String replaceImageUrl(String icon, String localizedUrl) {
        if (!StringUtils.hasText(icon) || !StringUtils.hasText(localizedUrl)) {
            return icon;
        }

        var trimmed = icon.trim();
        var document = Jsoup.parseBodyFragment(trimmed);
        document.outputSettings(new Document.OutputSettings().prettyPrint(false));
        var image = document.selectFirst("img");
        if (image != null) {
            image.attr("src", localizedUrl.trim());
            image.removeAttr("origin");
            return document.body().html();
        }

        if (looksLikeUrl(trimmed)) {
            return localizedUrl.trim();
        }
        return icon;
    }

    static String normalizeProtocolRelative(String value) {
        if (!StringUtils.hasText(value)) {
            return "";
        }
        var trimmed = value.trim();
        if (trimmed.startsWith("//")) {
            return "https:" + trimmed;
        }
        return trimmed;
    }

    private static Element firstImage(String html) {
        if (html.indexOf('<') < 0) {
            return null;
        }
        return Jsoup.parseBodyFragment(html).selectFirst("img");
    }

    private static boolean looksLikeUrl(String value) {
        return value.startsWith("//")
            || value.regionMatches(true, 0, "http://", 0, 7)
            || value.regionMatches(true, 0, "https://", 0, 8)
            || value.startsWith("/");
    }

    private static HostPort parseUriHostPort(String value) {
        try {
            var uri = CommentNextEmoteAssetFetcher.toSafeUri(value);
            if (!StringUtils.hasText(uri.getHost())) {
                return null;
            }
            return new HostPort(
                uri.getHost().toLowerCase(Locale.ROOT),
                effectivePort(uri.getScheme(), uri.getPort())
            );
        } catch (IllegalArgumentException ignored) {
            return null;
        }
    }

    private static int effectivePort(String scheme, int port) {
        if (port > 0) {
            return port;
        }
        return "https".equalsIgnoreCase(scheme) ? 443 : 80;
    }

    private record HostPort(String host, int port) {
    }
}
