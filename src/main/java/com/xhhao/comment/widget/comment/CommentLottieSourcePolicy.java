package com.xhhao.comment.widget.comment;

import java.net.IDN;
import java.net.URI;
import java.net.URL;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import org.springframework.http.server.reactive.ServerHttpRequest;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import run.halo.app.infra.ExternalUrlSupplier;

@Component
class CommentLottieSourcePolicy {

    private static final String PUBLIC_CONTENT_PREFIX =
        "/apis/api.lottie.halo.run/v1alpha1/animations/";

    private final ExternalUrlSupplier externalUrlSupplier;

    CommentLottieSourcePolicy(ExternalUrlSupplier externalUrlSupplier) {
        this.externalUrlSupplier = externalUrlSupplier;
    }

    boolean isAllowed(String source, ServerHttpRequest request, List<String> allowedHosts) {
        if (!StringUtils.hasText(source) || source.indexOf('\\') >= 0) {
            return false;
        }

        try {
            var siteUri = siteUri(request);
            var sourceUri = resolveSource(source.strip(), siteUri);
            if (!isSupportedPath(sourceUri.getRawPath())) {
                return false;
            }

            var sourceOrigin = Origin.from(sourceUri);
            var siteOrigin = Origin.from(siteUri);
            if (sourceOrigin == null || siteOrigin == null || sourceUri.getUserInfo() != null) {
                return false;
            }
            if (sourceOrigin.equals(siteOrigin)) {
                return true;
            }
            if (!"https".equals(sourceOrigin.scheme())) {
                return false;
            }

            return allowedHosts != null && allowedHosts.stream()
                .map(this::normalizeAllowedHost)
                .filter(Objects::nonNull)
                .anyMatch(sourceOrigin::sameHostAndPort);
        } catch (IllegalArgumentException ignored) {
            return false;
        }
    }

    boolean isLottieSource(String source) {
        if (!StringUtils.hasText(source) || source.indexOf('\\') >= 0) {
            return false;
        }
        try {
            var uri = URI.create(source.strip());
            return isSupportedPath(uri.getRawPath())
                || "1".equals(queryParameter(uri.getRawQuery(), "comment-next-lottie"));
        } catch (IllegalArgumentException ignored) {
            return false;
        }
    }

    private URI siteUri(ServerHttpRequest request) {
        URL configuredOrRequestUrl = externalUrlSupplier.getURL(request);
        try {
            var uri = configuredOrRequestUrl.toURI();
            var origin = Origin.from(uri);
            if (origin == null) {
                throw new IllegalArgumentException("Halo external URL has no valid origin");
            }
            return URI.create(origin.toUriString());
        } catch (Exception ignored) {
            var fallback = request.getURI();
            var origin = Origin.from(fallback);
            if (origin == null) {
                throw new IllegalArgumentException("Request URL has no valid origin");
            }
            return URI.create(origin.toUriString());
        }
    }

    private URI resolveSource(String source, URI siteUri) {
        var uri = URI.create(source);
        return uri.isAbsolute() ? uri : siteUri.resolve(uri);
    }

    private Origin normalizeAllowedHost(String value) {
        if (!StringUtils.hasText(value) || value.indexOf('\\') >= 0) {
            return null;
        }
        try {
            var normalized = value.contains("://") ? value.strip() : "https://" + value.strip();
            var uri = URI.create(normalized);
            if (uri.getUserInfo() != null || !"https".equalsIgnoreCase(uri.getScheme())) {
                return null;
            }
            return Origin.from(uri);
        } catch (IllegalArgumentException ignored) {
            return null;
        }
    }

    private boolean isSupportedPath(String path) {
        if (!StringUtils.hasText(path)) {
            return false;
        }
        var lowerPath = path.toLowerCase(Locale.ROOT);
        if (lowerPath.endsWith(".json") || lowerPath.endsWith(".tgs")
            || lowerPath.endsWith(".lottie")) {
            return true;
        }
        if (!path.startsWith(PUBLIC_CONTENT_PREFIX) || !path.endsWith("/content")) {
            return false;
        }
        var animationName = path.substring(
            PUBLIC_CONTENT_PREFIX.length(),
            path.length() - "/content".length()
        );
        return !animationName.isBlank() && animationName.indexOf('/') < 0;
    }

    private String queryParameter(String query, String name) {
        if (!StringUtils.hasText(query)) {
            return null;
        }
        for (var pair : query.split("&")) {
            var separator = pair.indexOf('=');
            var key = separator < 0 ? pair : pair.substring(0, separator);
            if (name.equals(key)) {
                return separator < 0 ? "" : pair.substring(separator + 1);
            }
        }
        return null;
    }

    private record Origin(String scheme, String host, int port) {

        static Origin from(URI uri) {
            if (uri == null || !StringUtils.hasText(uri.getScheme())
                || !StringUtils.hasText(uri.getHost())) {
                return null;
            }
            var scheme = uri.getScheme().toLowerCase(Locale.ROOT);
            if (!"http".equals(scheme) && !"https".equals(scheme)) {
                return null;
            }
            try {
                var host = IDN.toASCII(stripTrailingDot(uri.getHost()))
                    .toLowerCase(Locale.ROOT);
                if (!StringUtils.hasText(host)) {
                    return null;
                }
                var port = uri.getPort() >= 0 ? uri.getPort() : defaultPort(scheme);
                return new Origin(scheme, host, port);
            } catch (IllegalArgumentException ignored) {
                return null;
            }
        }

        boolean sameHostAndPort(Origin other) {
            return other != null && host.equals(other.host) && port == other.port;
        }

        String toUriString() {
            var defaultPort = defaultPort(scheme);
            return scheme + "://" + host + (port == defaultPort ? "" : ":" + port) + "/";
        }

        private static int defaultPort(String scheme) {
            return "https".equals(scheme) ? 443 : 80;
        }

        private static String stripTrailingDot(String host) {
            var result = host;
            while (result.endsWith(".")) {
                result = result.substring(0, result.length() - 1);
            }
            return result;
        }
    }
}
