package com.xhhao.comment.widget.ai.website;

import com.xhhao.comment.widget.ai.ConditionalOnHaloAiFoundation;
import java.net.IDN;
import java.net.InetAddress;
import java.net.URI;
import java.net.URISyntaxException;
import java.net.UnknownHostException;
import java.util.Arrays;
import java.util.Locale;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Mono;
import reactor.core.scheduler.Schedulers;

@Component
@ConditionalOnHaloAiFoundation
class CommentNextWebsiteUriPolicy {

    URI normalizeWebsiteRoot(String website) {
        if (website == null || website.isBlank()) {
            throw new IllegalArgumentException("评论者网址为空");
        }

        var value = website.strip();
        if (!hasScheme(value)) {
            value = "https://" + value;
        }

        var normalized = normalizeOutboundUri(URI.create(value));
        try {
            return new URI(
                normalized.getScheme(),
                null,
                normalized.getHost(),
                -1,
                "/",
                null,
                null
            );
        } catch (URISyntaxException e) {
            throw new IllegalArgumentException("评论者网址格式无效", e);
        }
    }

    Mono<URI> validateOutbound(URI candidate) {
        return Mono.fromCallable(() -> {
                var normalized = normalizeOutboundUri(candidate);
                validateResolvedHost(normalized.getHost());
                return normalized;
            })
            .subscribeOn(Schedulers.boundedElastic());
    }

    private URI normalizeOutboundUri(URI candidate) {
        if (candidate == null || candidate.isOpaque()) {
            throw new IllegalArgumentException("网址格式无效");
        }

        var scheme = lower(candidate.getScheme());
        if (!"http".equals(scheme) && !"https".equals(scheme)) {
            throw new IllegalArgumentException("只允许 HTTP 或 HTTPS 网址");
        }
        if (candidate.getRawUserInfo() != null) {
            throw new IllegalArgumentException("网址不能包含用户凭据");
        }

        var host = normalizedHost(candidate);
        var port = candidate.getPort();
        if (port != -1 && port != defaultPort(scheme)) {
            throw new IllegalArgumentException("网址不能使用非标准端口");
        }

        var path = candidate.getRawPath();
        if (path == null || path.isBlank()) {
            path = "/";
        }

        try {
            return new URI(
                scheme,
                null,
                host,
                -1,
                path,
                candidate.getRawQuery(),
                null
            );
        } catch (URISyntaxException e) {
            throw new IllegalArgumentException("网址格式无效", e);
        }
    }

    private String normalizedHost(URI uri) {
        var host = uri.getHost();
        if (host == null) {
            var authority = uri.getRawAuthority();
            if (authority == null
                || authority.contains("@")
                || authority.contains("\\")
                || authority.contains("%")
                || authority.contains(":")) {
                throw new IllegalArgumentException("网址域名无效");
            }
            host = authority;
        }

        if (host.startsWith("[") && host.endsWith("]")) {
            host = host.substring(1, host.length() - 1);
        }
        if (host.endsWith(".")) {
            host = host.substring(0, host.length() - 1);
        }
        if (host.isBlank() || "localhost".equalsIgnoreCase(host)) {
            throw new IllegalArgumentException("网址域名无效");
        }

        try {
            return IDN.toASCII(host, IDN.USE_STD3_ASCII_RULES).toLowerCase(Locale.ROOT);
        } catch (IllegalArgumentException e) {
            throw new IllegalArgumentException("网址域名无效", e);
        }
    }

    private void validateResolvedHost(String host) throws UnknownHostException {
        var addresses = InetAddress.getAllByName(host);
        if (addresses.length == 0 || Arrays.stream(addresses).anyMatch(address -> !isPublicAddress(address))) {
            throw new IllegalArgumentException("网址解析到了非公网地址");
        }
    }

    static boolean isPublicAddress(InetAddress address) {
        if (address == null
            || address.isAnyLocalAddress()
            || address.isLoopbackAddress()
            || address.isLinkLocalAddress()
            || address.isSiteLocalAddress()
            || address.isMulticastAddress()) {
            return false;
        }

        var bytes = address.getAddress();
        if (bytes.length == 4) {
            return isPublicIpv4(bytes);
        }
        if (bytes.length != 16) {
            return false;
        }

        if (isIpv4Mapped(bytes)) {
            return isPublicIpv4(Arrays.copyOfRange(bytes, 12, 16));
        }

        var first = unsigned(bytes[0]);
        var second = unsigned(bytes[1]);
        return (first & 0xfe) != 0xfc
            && !(first == 0x20 && second == 0x01
                && unsigned(bytes[2]) == 0x0d && unsigned(bytes[3]) == 0xb8);
    }

    private static boolean isPublicIpv4(byte[] bytes) {
        var first = unsigned(bytes[0]);
        var second = unsigned(bytes[1]);
        var third = unsigned(bytes[2]);

        return first != 0
            && first != 10
            && first != 127
            && !(first == 100 && second >= 64 && second <= 127)
            && !(first == 169 && second == 254)
            && !(first == 172 && second >= 16 && second <= 31)
            && !(first == 192 && second == 0 && third == 0)
            && !(first == 192 && second == 0 && third == 2)
            && !(first == 192 && second == 168)
            && !(first == 198 && (second == 18 || second == 19))
            && !(first == 198 && second == 51 && third == 100)
            && !(first == 203 && second == 0 && third == 113)
            && first < 224;
    }

    private static boolean isIpv4Mapped(byte[] bytes) {
        for (int i = 0; i < 10; i++) {
            if (bytes[i] != 0) {
                return false;
            }
        }
        return unsigned(bytes[10]) == 0xff && unsigned(bytes[11]) == 0xff;
    }

    private static int unsigned(byte value) {
        return Byte.toUnsignedInt(value);
    }

    private boolean hasScheme(String value) {
        var separator = value.indexOf("://");
        if (separator <= 0) {
            return false;
        }
        for (int i = 0; i < separator; i++) {
            var character = value.charAt(i);
            if (!(Character.isLetterOrDigit(character)
                || character == '+'
                || character == '-'
                || character == '.')) {
                return false;
            }
        }
        return true;
    }

    private int defaultPort(String scheme) {
        return "https".equals(scheme) ? 443 : 80;
    }

    private String lower(String value) {
        return value == null ? "" : value.toLowerCase(Locale.ROOT);
    }
}
