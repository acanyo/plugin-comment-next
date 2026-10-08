package com.xhhao.comment.widget.iplocation;

import com.fasterxml.jackson.databind.JsonNode;
import org.springframework.util.StringUtils;

/**
 * 从 IP 属地接口的响应中提取属地文本。
 *
 * <p>兼容 pconline（pro/city/addr）、ip-api.com（country/regionName）、ipwho.is、ipinfo.io、
 * ip.zxinc.org（location）、api.mir6.com（country/province/city/location）等常见免费接口字段命名。
 * 国内 IP 只展示省份（如「广东」），境外 IP 展示国家（如「日本」）。
 */
final class CommentNextIpLocationResponseParser {

    private CommentNextIpLocationResponseParser() {
    }

    static String location(JsonNode node) {
        if (node == null || !node.isObject() || isFailure(node)) {
            return "";
        }

        var direct = locationFromObject(node);
        if (StringUtils.hasText(direct)) {
            return direct;
        }

        // api.mir6.com、ip.zxinc.org 把结果放在 data 字段里。
        var data = node.path("data");
        return data.isObject() ? locationFromObject(data) : "";
    }

    private static String locationFromObject(JsonNode node) {
        var country = firstText(node, "country", "countryName", "country_name");
        var countryCode = firstText(node, "countryCode", "country_code");
        if (!StringUtils.hasText(countryCode)
            && CommentNextIpLocationText.isCountryCode(country)) {
            // 部分接口（如 ipinfo.io）把国家代码放在 country 字段里。
            countryCode = country;
            country = "";
        }

        var province = firstText(node,
            "regionName", "region_name", "province", "pro", "state", "region");
        var city = firstText(node, "city", "cityName", "city_name", "districts");
        var whole = firstText(node, "location", "addr");

        if (CommentNextIpLocationText.isChina(country, countryCode)
            || CommentNextIpLocationText.looksChineseRegion(province)
            || CommentNextIpLocationText.looksChineseRegion(city)) {
            return CommentNextIpLocationText.firstNonBlank(
                CommentNextIpLocationText.trimAdministrativeSuffix(province),
                CommentNextIpLocationText.trimAdministrativeSuffix(city),
                CommentNextIpLocationText.regionFromText(whole),
                "中国"
            );
        }

        return CommentNextIpLocationText.firstNonBlank(
            country,
            CommentNextIpLocationText.trimAdministrativeSuffix(province),
            CommentNextIpLocationText.trimAdministrativeSuffix(city),
            countryCode,
            CommentNextIpLocationText.regionFromText(whole)
        );
    }

    private static boolean isFailure(JsonNode node) {
        var status = node.path("status");
        if (status.isTextual() && "fail".equalsIgnoreCase(status.asText())) {
            return true;
        }

        var success = node.path("success");
        if (success.isBoolean() && !success.asBoolean()) {
            return true;
        }

        var error = node.path("error");
        if (error.isValueNode() && !error.isNull() && StringUtils.hasText(error.asText(""))) {
            return true;
        }

        // ip.zxinc.org 用 code=0 表示成功，api.mir6.com 用 code=200。
        var code = node.path("code");
        if (code.isIntegralNumber()) {
            var value = code.asInt();
            return value != 0 && value != 200;
        }
        return false;
    }

    private static String firstText(JsonNode node, String... fieldNames) {
        for (String fieldName : fieldNames) {
            var value = node.path(fieldName);
            if (value.isTextual()) {
                var text = CommentNextIpLocationText.normalize(value.asText());
                if (StringUtils.hasText(text)) {
                    return text;
                }
            }
        }
        return "";
    }
}
