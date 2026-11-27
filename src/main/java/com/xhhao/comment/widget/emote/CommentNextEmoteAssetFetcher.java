package com.xhhao.comment.widget.emote;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.xhhao.comment.utils.JsonUtils;
import com.xhhao.comment.widget.network.CommentNextOutboundUriPolicy;
import io.netty.resolver.DefaultAddressResolverGroup;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.URI;
import java.net.URLDecoder;
import java.net.URLEncoder;
import java.net.UnknownHostException;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Arrays;
import java.util.Locale;
import java.util.Map;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.io.buffer.DataBufferUtils;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.client.reactive.ReactorClientHttpConnector;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Mono;
import reactor.core.scheduler.Schedulers;
import reactor.netty.http.client.HttpClient;

/**
 * Safely fetches remote emote JSON catalogs and image assets with SSRF protection.
 */
@Slf4j
@Component
@RequiredArgsConstructor
class CommentNextEmoteAssetFetcher {

    static final int MAX_IMAGE_BYTES = 5 * 1024 * 1024;
    static final int MAX_JSON_BYTES = 5 * 1024 * 1024;
    private static final int MAX_REDIRECTS = 3;
    private static final Duration FETCH_TIMEOUT = Duration.ofSeconds(15);

    private static final Map<String, String> MEDIA_TYPE_EXTENSIONS = Map.of(
        "image/png", "png",
        "image/jpeg", "jpg",
        "image/gif", "gif",
        "image/webp", "webp",
        "image/avif", "avif"
    );

    private final CommentNextOutboundUriPolicy outboundUriPolicy;

    private final ObjectMapper objectMapper = JsonUtils.createObjectMapper();

    private final WebClient webClient = WebClient.builder()
        .clientConnector(new ReactorClientHttpConnector(HttpClient.create()
            .responseTimeout(FETCH_TIMEOUT)
            .resolver(DefaultAddressResolverGroup.INSTANCE)
            .resolvedAddressesSelector((config, addresses) -> {
                var selected = addresses.stream()
                    .filter(address -> address instanceof InetSocketAddress inetAddress
                        && isAllowedConnectAddress(inetAddress.getAddress()))
                    .toList();
                if (selected.isEmpty()) {
                    log.warn("No allowed connect addresses remained after filtering: {}", addresses);
                }
                return selected;
            })))
        .codecs(configurer -> configurer.defaultCodecs().maxInMemorySize(MAX_IMAGE_BYTES + 16 * 1024))
        .defaultHeader(HttpHeaders.USER_AGENT, "Halo-Comment-Next-Emote-Localizer/1.0")
        .build();

    Mono<DownloadedImage> fetchImage(String rawUrl) {
        URI uri;
        try {
            uri = toSafeUri(rawUrl);
        } catch (IllegalArgumentException e) {
            return Mono.error(e);
        }
        return fetchImage(uri, MAX_REDIRECTS).timeout(FETCH_TIMEOUT);
    }

    Mono<String> fetchJson(String rawUrl) {
        URI uri;
        try {
            uri = toSafeUri(rawUrl);
        } catch (IllegalArgumentException e) {
            return Mono.error(e);
        }
        return fetchJson(uri, MAX_REDIRECTS).timeout(FETCH_TIMEOUT);
    }

    private Mono<DownloadedImage> fetchImage(URI uri, int redirectsRemaining) {
        return validateEmoteUri(uri)
            .flatMap(validated -> webClient.get()
                .uri(validated)
                .header(HttpHeaders.ACCEPT, "image/avif,image/webp,image/png,image/jpeg,image/gif,*/*;q=0.5")
                .exchangeToMono(response -> {
                    if (response.statusCode().is3xxRedirection()) {
                        var location = response.headers().asHttpHeaders().getLocation();
                        if (location == null || redirectsRemaining <= 0) {
                            return response.releaseBody()
                                .then(Mono.error(new IllegalStateException("图片重定向次数过多或缺少目标地址")));
                        }
                        return response.releaseBody()
                            .then(Mono.defer(() -> {
                                var resolved = resolveRedirect(validated, location.toString());
                                return fetchImage(resolved, redirectsRemaining - 1);
                            }));
                    }

                    if (!response.statusCode().is2xxSuccessful()) {
                        return response.releaseBody()
                            .then(Mono.error(new IllegalStateException(
                                "图片下载失败，HTTP " + response.statusCode().value()
                            )));
                    }

                    var contentLength = response.headers().contentLength();
                    if (contentLength.isPresent() && contentLength.getAsLong() > MAX_IMAGE_BYTES) {
                        return response.releaseBody()
                            .then(Mono.error(new IllegalStateException("图片体积超过 5MB 上限")));
                    }

                    var headerMediaType = response.headers().contentType().orElse(null);
                    return DataBufferUtils.join(response.bodyToFlux(org.springframework.core.io.buffer.DataBuffer.class),
                            MAX_IMAGE_BYTES)
                        .switchIfEmpty(Mono.error(new IllegalStateException("图片内容为空")))
                        .map(buffer -> {
                            try {
                                var bytes = new byte[buffer.readableByteCount()];
                                buffer.read(bytes);
                                return bytes;
                            } finally {
                                DataBufferUtils.release(buffer);
                            }
                        })
                        .flatMap(bytes -> {
                            if (bytes.length == 0) {
                                return Mono.error(new IllegalStateException("图片内容为空"));
                            }
                            if (bytes.length > MAX_IMAGE_BYTES) {
                                return Mono.error(new IllegalStateException("图片体积超过 5MB 上限"));
                            }
                            var detected = detectImageType(bytes, headerMediaType);
                            if (detected == null) {
                                return Mono.error(new IllegalStateException("目标资源不是受支持的图片格式"));
                            }
                            return Mono.just(new DownloadedImage(
                                validated,
                                bytes,
                                detected.mediaType(),
                                detected.extension()
                            ));
                        });
                }));
    }

    private Mono<String> fetchJson(URI uri, int redirectsRemaining) {
        return validateEmoteUri(uri)
            .flatMap(validated -> webClient.get()
                .uri(validated)
                .header(HttpHeaders.ACCEPT, "application/json,text/plain;q=0.8,*/*;q=0.5")
                .exchangeToMono(response -> {
                    if (response.statusCode().is3xxRedirection()) {
                        var location = response.headers().asHttpHeaders().getLocation();
                        if (location == null || redirectsRemaining <= 0) {
                            return response.releaseBody()
                                .then(Mono.error(new IllegalStateException("在线表情源重定向次数过多或缺少目标地址")));
                        }
                        return response.releaseBody()
                            .then(Mono.defer(() -> {
                                var resolved = resolveRedirect(validated, location.toString());
                                return fetchJson(resolved, redirectsRemaining - 1);
                            }));
                    }

                    if (!response.statusCode().is2xxSuccessful()) {
                        return response.releaseBody()
                            .then(Mono.error(new IllegalStateException(
                                "在线表情源请求失败，HTTP " + response.statusCode().value()
                            )));
                    }

                    return DataBufferUtils.join(response.bodyToFlux(org.springframework.core.io.buffer.DataBuffer.class),
                            MAX_JSON_BYTES)
                        .switchIfEmpty(Mono.error(new IllegalStateException("在线表情源内容为空")))
                        .map(buffer -> {
                            try {
                                var bytes = new byte[buffer.readableByteCount()];
                                buffer.read(bytes);
                                return new String(bytes, StandardCharsets.UTF_8);
                            } finally {
                                DataBufferUtils.release(buffer);
                            }
                        })
                        .flatMap(body -> validateJsonObject(body));
                }));
    }

    private Mono<String> validateJsonObject(String body) {
        try {
            var node = objectMapper.readTree(body);
            if (node == null || !node.isObject()) {
                return Mono.error(new IllegalArgumentException("表情 JSON 必须是对象格式"));
            }
            return Mono.just(body);
        } catch (Exception e) {
            return Mono.error(new IllegalArgumentException("表情 JSON 格式不正确", e));
        }
    }

    private Mono<URI> validateEmoteUri(URI candidate) {
        return Mono.fromCallable(() -> {
                var normalized = outboundUriPolicy.normalize(candidate);
                validateResolvedAddresses(normalized.getHost(), InetAddress.getAllByName(normalized.getHost()));
                return normalized;
            })
            .subscribeOn(Schedulers.boundedElastic());
    }

    static void validateResolvedAddresses(String host, InetAddress[] addresses) throws UnknownHostException {
        if (addresses == null || addresses.length == 0) {
            throw new UnknownHostException(host);
        }
        if (Arrays.stream(addresses).anyMatch(CommentNextEmoteAssetFetcher::isStrictlyForbiddenAddress)) {
            var resolved = formatAddresses(addresses);
            log.warn("Blocked emote request to host '{}' resolved to forbidden address(es): {}", host, resolved);
            throw new IllegalArgumentException("网址解析到了非公网地址（" + resolved + "）");
        }
        boolean hasAllowed = Arrays.stream(addresses)
            .anyMatch(address -> isAllowedResolvedAddress(host, address));
        if (!hasAllowed) {
            var resolved = formatAddresses(addresses);
            log.warn("Blocked emote request to host '{}' resolved to non-public address(es): {}", host, resolved);
            throw new IllegalArgumentException("网址解析到了非公网地址（" + resolved + "）");
        }
    }

    private static String formatAddresses(InetAddress[] addresses) {
        return Arrays.stream(addresses)
            .map(InetAddress::getHostAddress)
            .collect(Collectors.joining(", "));
    }

    static boolean isAllowedConnectAddress(InetAddress address) {
        return CommentNextOutboundUriPolicy.isPublicAddress(address) || isFakeIpAddress(address);
    }

    static boolean isAllowedResolvedAddress(String host, InetAddress address) {
        if (address == null) {
            return false;
        }
        if (CommentNextOutboundUriPolicy.isPublicAddress(address)) {
            return true;
        }
        return !isIpLiteral(host) && isFakeIpAddress(address);
    }

    static boolean isFakeIpAddress(InetAddress address) {
        if (address == null) {
            return false;
        }
        var bytes = address.getAddress();
        if (bytes.length == 4) {
            return isFakeIpv4(bytes);
        }
        if (bytes.length == 16 && isIpv4Mapped(bytes)) {
            return isFakeIpv4(Arrays.copyOfRange(bytes, 12, 16));
        }
        return false;
    }

    static boolean isIpLiteral(String host) {
        if (!StringUtils.hasText(host)) {
            return true;
        }
        var trimmed = host.trim();
        if (trimmed.startsWith("[") && trimmed.endsWith("]")) {
            trimmed = trimmed.substring(1, trimmed.length() - 1);
        }
        if (trimmed.indexOf(':') >= 0) {
            return true;
        }
        var parts = trimmed.split("\\.", -1);
        if (parts.length != 4) {
            return false;
        }
        for (var part : parts) {
            if (part.isEmpty() || part.length() > 3) {
                return false;
            }
            for (int i = 0; i < part.length(); i++) {
                if (!Character.isDigit(part.charAt(i))) {
                    return false;
                }
            }
        }
        return true;
    }

    private static boolean isStrictlyForbiddenAddress(InetAddress address) {
        if (address == null) {
            return true;
        }
        return address.isAnyLocalAddress()
            || address.isLoopbackAddress()
            || address.isLinkLocalAddress()
            || address.isSiteLocalAddress()
            || address.isMulticastAddress()
            || isCgnatOrMetadataIpv4(address);
    }

    private static boolean isCgnatOrMetadataIpv4(InetAddress address) {
        var bytes = address.getAddress();
        if (bytes.length == 16 && isIpv4Mapped(bytes)) {
            bytes = Arrays.copyOfRange(bytes, 12, 16);
        }
        if (bytes.length != 4) {
            return false;
        }
        var first = Byte.toUnsignedInt(bytes[0]);
        var second = Byte.toUnsignedInt(bytes[1]);
        return first == 100 && second >= 64 && second <= 127;
    }

    private static boolean isFakeIpv4(byte[] bytes) {
        var first = Byte.toUnsignedInt(bytes[0]);
        var second = Byte.toUnsignedInt(bytes[1]);
        return first == 198 && (second == 18 || second == 19);
    }

    private static boolean isIpv4Mapped(byte[] bytes) {
        for (int i = 0; i < 10; i++) {
            if (bytes[i] != 0) {
                return false;
            }
        }
        return Byte.toUnsignedInt(bytes[10]) == 0xff && Byte.toUnsignedInt(bytes[11]) == 0xff;
    }

    private URI resolveRedirect(URI base, String rawLocation) {
        try {
            var normalizedLocation = rawLocation.startsWith("//") ? "https:" + rawLocation : rawLocation;
            if (normalizedLocation.regionMatches(true, 0, "http://", 0, 7)
                || normalizedLocation.regionMatches(true, 0, "https://", 0, 8)) {
                return toSafeUri(normalizedLocation);
            }
            return toSafeUri(base.resolve(encodePathOnly(normalizedLocation)).toString());
        } catch (IllegalArgumentException e) {
            throw new IllegalStateException("重定向目标地址无效", e);
        }
    }

    static URI toSafeUri(String rawUrl) {
        if (!StringUtils.hasText(rawUrl)) {
            throw new IllegalArgumentException("图片地址为空");
        }
        var value = CommentNextEmoteHtmlUrlCodec.normalizeProtocolRelative(rawUrl.trim());
        var schemeSep = value.indexOf("://");
        if (schemeSep <= 0) {
            throw new IllegalArgumentException("网址格式无效");
        }

        var scheme = value.substring(0, schemeSep).toLowerCase(Locale.ROOT);
        var rest = value.substring(schemeSep + 3);
        var hashIndex = rest.indexOf('#');
        if (hashIndex >= 0) {
            rest = rest.substring(0, hashIndex);
        }

        var queryIndex = rest.indexOf('?');
        var authorityAndPath = queryIndex >= 0 ? rest.substring(0, queryIndex) : rest;
        var rawQuery = queryIndex >= 0 ? rest.substring(queryIndex + 1) : null;

        var slashIndex = authorityAndPath.indexOf('/');
        var authority = slashIndex >= 0 ? authorityAndPath.substring(0, slashIndex) : authorityAndPath;
        var rawPath = slashIndex >= 0 ? authorityAndPath.substring(slashIndex) : "/";

        if (!StringUtils.hasText(authority)) {
            throw new IllegalArgumentException("网址域名无效");
        }

        var encodedPath = encodePathOnly(rawPath);
        var encodedQuery = rawQuery == null ? null : encodeQueryOnly(rawQuery);
        var reconstructed = scheme + "://" + authority + encodedPath
            + (encodedQuery != null ? "?" + encodedQuery : "");

        try {
            return URI.create(reconstructed);
        } catch (IllegalArgumentException e) {
            throw new IllegalArgumentException("网址格式无效", e);
        }
    }

    static ImageType detectImageType(byte[] bytes, MediaType headerMediaType) {
        var sniffed = sniffMagic(bytes);
        if (sniffed != null) {
            return sniffed;
        }
        if (headerMediaType != null) {
            var mime = (headerMediaType.getType() + "/" + headerMediaType.getSubtype())
                .toLowerCase(Locale.ROOT);
            if ("image/jpg".equals(mime)) {
                return new ImageType(MediaType.IMAGE_JPEG, "jpg");
            }
            var ext = MEDIA_TYPE_EXTENSIONS.get(mime);
            if (ext != null) {
                return new ImageType(MediaType.parseMediaType(mime), ext);
            }
        }
        return null;
    }

    private static ImageType sniffMagic(byte[] bytes) {
        if (bytes == null || bytes.length < 4) {
            return null;
        }
        // PNG: 89 50 4E 47 0D 0A 1A 0A
        if (bytes.length >= 8
            && (bytes[0] & 0xFF) == 0x89
            && bytes[1] == 'P' && bytes[2] == 'N' && bytes[3] == 'G'
            && bytes[4] == 0x0D && bytes[5] == 0x0A && bytes[6] == 0x1A && bytes[7] == 0x0A) {
            return new ImageType(MediaType.IMAGE_PNG, "png");
        }
        // JPEG: FF D8 FF
        if ((bytes[0] & 0xFF) == 0xFF && (bytes[1] & 0xFF) == 0xD8 && (bytes[2] & 0xFF) == 0xFF) {
            return new ImageType(MediaType.IMAGE_JPEG, "jpg");
        }
        // GIF: GIF87a / GIF89a
        if (bytes.length >= 6
            && bytes[0] == 'G' && bytes[1] == 'I' && bytes[2] == 'F' && bytes[3] == '8'
            && (bytes[4] == '7' || bytes[4] == '9') && bytes[5] == 'a') {
            return new ImageType(MediaType.IMAGE_GIF, "gif");
        }
        // WebP: RIFF....WEBP
        if (bytes.length >= 12
            && bytes[0] == 'R' && bytes[1] == 'I' && bytes[2] == 'F' && bytes[3] == 'F'
            && bytes[8] == 'W' && bytes[9] == 'E' && bytes[10] == 'B' && bytes[11] == 'P') {
            return new ImageType(MediaType.parseMediaType("image/webp"), "webp");
        }
        // AVIF: ....ftypavif or ....ftypavis
        if (bytes.length >= 12
            && bytes[4] == 'f' && bytes[5] == 't' && bytes[6] == 'y' && bytes[7] == 'p'
            && bytes[8] == 'a' && bytes[9] == 'v' && bytes[10] == 'i'
            && (bytes[11] == 'f' || bytes[11] == 's')) {
            return new ImageType(MediaType.parseMediaType("image/avif"), "avif");
        }
        return null;
    }

    private static String encodePathOnly(String path) {
        if (!StringUtils.hasText(path)) {
            return "/";
        }
        var segments = path.split("/", -1);
        var builder = new StringBuilder(path.length() + 16);
        for (int i = 0; i < segments.length; i++) {
            if (i > 0) {
                builder.append('/');
            }
            var segment = segments[i];
            if (segment.isEmpty()) {
                continue;
            }
            builder.append(encodeSegmentPreservingValidPercent(segment));
        }
        return builder.toString();
    }

    private static String encodeQueryOnly(String query) {
        var builder = new StringBuilder(query.length() + 16);
        for (int i = 0; i < query.length(); i++) {
            var ch = query.charAt(i);
            if (isSafeQueryChar(ch)) {
                builder.append(ch);
            } else if (ch == '%' && i + 2 < query.length()
                && isHex(query.charAt(i + 1)) && isHex(query.charAt(i + 2))) {
                builder.append(ch).append(query.charAt(i + 1)).append(query.charAt(i + 2));
                i += 2;
            } else {
                builder.append(URLEncoder.encode(String.valueOf(ch), StandardCharsets.UTF_8)
                    .replace("+", "%20"));
            }
        }
        return builder.toString();
    }

    private static String encodeSegmentPreservingValidPercent(String segment) {
        String decoded;
        try {
            decoded = URLDecoder.decode(segment.replace("+", "%2B"), StandardCharsets.UTF_8);
        } catch (IllegalArgumentException ignored) {
            decoded = segment;
        }
        var encoded = URLEncoder.encode(decoded, StandardCharsets.UTF_8)
            .replace("+", "%20")
            .replace("%40", "@")
            .replace("%21", "!")
            .replace("%24", "$")
            .replace("%26", "&")
            .replace("%27", "'")
            .replace("%28", "(")
            .replace("%29", ")")
            .replace("%2A", "*")
            .replace("%2B", "+")
            .replace("%2C", ",")
            .replace("%3B", ";")
            .replace("%3D", "=")
            .replace("%3A", ":");
        return encoded;
    }

    private static boolean isSafeQueryChar(char ch) {
        return (ch >= 'a' && ch <= 'z')
            || (ch >= 'A' && ch <= 'Z')
            || (ch >= '0' && ch <= '9')
            || ch == '-' || ch == '.' || ch == '_' || ch == '~'
            || ch == '&' || ch == '=' || ch == '+' || ch == ','
            || ch == ':' || ch == '@' || ch == '/' || ch == '?'
            || ch == '!' || ch == '$' || ch == '\'' || ch == '('
            || ch == ')' || ch == '*' || ch == ';';
    }

    private static boolean isHex(char ch) {
        return (ch >= '0' && ch <= '9')
            || (ch >= 'a' && ch <= 'f')
            || (ch >= 'A' && ch <= 'F');
    }

    record ImageType(MediaType mediaType, String extension) {
    }

    record DownloadedImage(URI finalUri, byte[] bytes, MediaType mediaType, String extension) {
    }
}
