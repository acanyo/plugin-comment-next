package com.xhhao.comment.widget.iplocation;

import com.google.common.net.InetAddresses;
import com.xhhao.comment.widget.SettingConfigGetter;
import com.xhhao.comment.widget.network.CommentNextOutboundUriPolicy;
import java.time.Duration;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Semaphore;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;
import reactor.core.publisher.Mono;

/**
 * 查询站长配置的属地服务，并复用同一 IP 的查询结果。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class CommentNextIpLocationService {

    private static final int NEGATIVE_CACHE_MINUTES = 15;

    private static final int MIN_CACHE_MINUTES = 1;

    private static final int MAX_CACHE_MINUTES = 43_200;

    private static final int MIN_TIMEOUT_MILLIS = 500;

    private static final int MAX_TIMEOUT_MILLIS = 10_000;

    private static final int MAX_IN_FLIGHT_REQUESTS = 8;

    private final CommentNextIpLocationClient client;

    private final CommentNextIpLocationCache cache;

    private final SettingConfigGetter settingConfigGetter;

    private final Map<String, Mono<String>> inFlightRequests = new ConcurrentHashMap<>();

    private final Semaphore onlineRequests = new Semaphore(MAX_IN_FLIGHT_REQUESTS);

    /**
     * 解析属地，永不返回错误。
     */
    public Mono<String> resolve(String ipAddress) {
        var address = publicIp(ipAddress);
        if (address.isEmpty()) {
            return Mono.just("");
        }

        return settingConfigGetter.getIpLocationConfig()
            .filter(config -> config.isEnabled() && config.isOnlineLookupEnabled())
            .flatMap(config -> resolveOnline(address, config))
            .defaultIfEmpty("")
            .onErrorResume(error -> {
                log.debug("Could not load or apply IP location settings: {}",
                    error.getClass().getSimpleName());
                return Mono.just("");
            });
    }

    private Mono<String> resolveOnline(String ipAddress, SettingConfigGetter.IpLocationConfig config) {
        var apiUrlTemplate = resolveApiUrlTemplate(config);
        if (ipAddress.contains(":")
            && SettingConfigGetter.IpLocationConfig.DEFAULT_API_URL_TEMPLATE.equals(apiUrlTemplate)) {
            return Mono.empty();
        }
        var key = apiUrlTemplate + '\0' + ipAddress;
        var cached = cache.get(key);
        if (cached.isPresent()) {
            return Mono.just(cached.get());
        }
        var request = inFlightRequests.computeIfAbsent(key, ignored -> {
            if (cache.get(key).isPresent() || !onlineRequests.tryAcquire()) {
                return null;
            }
            return fetchLocation(key, ipAddress, apiUrlTemplate, config);
        });
        return request == null ? Mono.just(cache.get(key).orElse("")) : request;
    }

    private Mono<String> fetchLocation(String key, String ipAddress, String apiUrlTemplate,
        SettingConfigGetter.IpLocationConfig config) {
        return Mono.defer(() -> client.fetchLocation(ipAddress, apiUrlTemplate, timeout(config)))
            .map(CommentNextIpLocationText::displayLabel)
            .defaultIfEmpty("")
            .onErrorResume(error -> {
                log.debug("IP location provider lookup failed: {}", error.getClass().getSimpleName());
                return Mono.just("");
            })
            .doOnNext(location -> cache.put(
                key,
                location,
                location.isBlank() ? NEGATIVE_CACHE_MINUTES : cacheMinutes(config)
            ))
            .doFinally(signal -> {
                inFlightRequests.remove(key);
                onlineRequests.release();
            })
            .cache();
    }

    private String resolveApiUrlTemplate(SettingConfigGetter.IpLocationConfig config) {
        return StringUtils.hasText(config.getApiUrlTemplate())
            ? config.getApiUrlTemplate().strip()
            : SettingConfigGetter.IpLocationConfig.DEFAULT_API_URL_TEMPLATE;
    }

    private Duration timeout(SettingConfigGetter.IpLocationConfig config) {
        return Duration.ofMillis(
            clamp(config.getTimeoutMillis(), MIN_TIMEOUT_MILLIS, MAX_TIMEOUT_MILLIS)
        );
    }

    private int cacheMinutes(SettingConfigGetter.IpLocationConfig config) {
        return clamp(config.getCacheMinutes(), MIN_CACHE_MINUTES, MAX_CACHE_MINUTES);
    }

    private int clamp(int value, int min, int max) {
        return Math.min(Math.max(value, min), max);
    }

    private String publicIp(String ipAddress) {
        if (!StringUtils.hasText(ipAddress)) {
            return "";
        }

        var candidate = ipAddress.strip();
        if (!InetAddresses.isInetAddress(candidate)) {
            return "";
        }

        var address = InetAddresses.forString(candidate);
        return CommentNextOutboundUriPolicy.isPublicAddress(address)
            ? InetAddresses.toAddrString(address)
            : "";
    }
}
