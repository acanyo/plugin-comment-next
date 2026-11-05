package com.xhhao.comment.widget.security;

import lombok.experimental.UtilityClass;
import org.springframework.util.StringUtils;
import org.springframework.web.reactive.function.server.ServerRequest;
import org.springframework.web.server.ServerWebExchange;

@UtilityClass
public class ClientIpUtils {
    public static final String UNKNOWN = "unknown";

    private static final String[] IP_HEADER_NAMES = {
        "X-Forwarded-For",
        "X-Real-IP",
        "CF-Connecting-IP",
        "Proxy-Client-IP",
        "WL-Proxy-Client-IP",
        "HTTP_X_FORWARDED_FOR",
        "HTTP_X_FORWARDED",
        "HTTP_X_CLUSTER_CLIENT_IP",
        "HTTP_CLIENT_IP",
        "HTTP_FORWARDED_FOR",
        "HTTP_FORWARDED",
        "HTTP_VIA",
        "REMOTE_ADDR"
    };

    public static String getClientIp(ServerRequest request) {
        return getClientIp(request.exchange());
    }

    public static String getClientIp(ServerWebExchange exchange) {
        for (String header : IP_HEADER_NAMES) {
            var ipList = exchange.getRequest().getHeaders().getFirst(header);
            var ip = firstValidIp(ipList);
            if (StringUtils.hasText(ip)) {
                return ip;
            }
        }

        var remoteAddress = exchange.getRequest().getRemoteAddress();
        if (remoteAddress == null || remoteAddress.isUnresolved()
            || remoteAddress.getAddress() == null) {
            return UNKNOWN;
        }

        return remoteAddress.getAddress().getHostAddress();
    }

    private static String firstValidIp(String ipList) {
        if (!StringUtils.hasText(ipList) || UNKNOWN.equalsIgnoreCase(ipList)) {
            return "";
        }

        for (String ip : ipList.trim().split("[,;]")) {
            if (StringUtils.hasText(ip) && !UNKNOWN.equalsIgnoreCase(ip.trim())) {
                return ip.trim();
            }
        }

        return "";
    }
}
