package com.xhhao.comment.widget.iplocation;

import java.util.IllformedLocaleException;
import java.util.List;
import java.util.Locale;
import java.util.regex.Pattern;
import org.springframework.util.StringUtils;

/**
 * 属地文本的公共处理规则：国家/地区识别、中文长串解析与展示规范化。
 *
 * <p>在线接口返回的字段命名各不相同（pconline 的 pro、ip-api 的 regionName），ip2region 离线库
 * 则返回「国家|省份|城市|ISP|国家代码」这样的竖线文本，这里统一收敛成一处可直接展示的中文文本。
 */
final class CommentNextIpLocationText {

    private static final int MAX_LOCATION_LENGTH = 48;

    private static final Pattern PROVINCE_PATTERN =
        Pattern.compile("^(.{1,10}?(?:省|市|自治区|特别行政区))");

    private static final List<String> CHINA_NAMES = List.of("中国", "china", "中国大陆");

    private static final String EMPTY_REGION_PART = "0";

    private static final String UNKNOWN_COUNTRY = "未知地区";

    private CommentNextIpLocationText() {
    }

    static String normalize(String value) {
        if (!StringUtils.hasText(value)) {
            return "";
        }

        var stripped = value.strip().replaceAll("\\s+", " ");
        if (EMPTY_REGION_PART.equals(stripped)) {
            return "";
        }
        if (stripped.length() > MAX_LOCATION_LENGTH) {
            return stripped.substring(0, MAX_LOCATION_LENGTH);
        }
        return stripped;
    }

    static String firstNonBlank(String... candidates) {
        for (String candidate : candidates) {
            if (StringUtils.hasText(candidate)) {
                return candidate;
            }
        }
        return "";
    }

    /**
     * 国家代码由 2~3 个 ASCII 字母组成，中文国家名（如「中国」）不会被误判。
     */
    static boolean isCountryCode(String value) {
        if (value.length() < 2 || value.length() > 3) {
            return false;
        }
        return value.chars().allMatch(character ->
            (character >= 'a' && character <= 'z')
                || (character >= 'A' && character <= 'Z'));
    }

    static boolean isChina(String country, String countryCode) {
        if ("CN".equalsIgnoreCase(countryCode)) {
            return true;
        }
        if (!StringUtils.hasText(country)) {
            return false;
        }
        var normalizedCountry = country.strip().toLowerCase();
        return CHINA_NAMES.stream().anyMatch(normalizedCountry::contains);
    }

    /**
     * 判断文本本身是否已经带有中国行政区划后缀，例如 pconline 只返回「广东省」而没有国家字段。
     */
    static boolean looksChineseRegion(String value) {
        if (!StringUtils.hasText(value)) {
            return false;
        }
        return value.endsWith("省")
            || value.endsWith("市")
            || value.endsWith("自治区")
            || value.endsWith("特别行政区");
    }

    /**
     * 去掉行政区划后缀，让不同接口的展示风格一致：「广东省」→「广东」。
     */
    static String trimAdministrativeSuffix(String value) {
        var trimmed = value.strip();
        var result = trimmed
            .replaceAll("特别行政区$", "")
            .replaceAll("自治区$", "")
            .replaceAll("(省|市)$", "");
        if (!result.equals(trimmed)) {
            result = result.replaceAll("(壮族|维吾尔|回族)$", "");
        }
        return result;
    }

    /**
     * 把「广东省广州市天河区 电信」「中国–广东–广州–天河区 电信」「 美国」这类长串收敛为
     * 可展示的省份或国家（「广东」「美国」）。
     */
    static String regionFromText(String text) {
        var normalized = normalize(text);
        if (!StringUtils.hasText(normalized)) {
            return "";
        }

        var head = normalized.split("\\s")[0]
            .replace('–', '-')
            .replace('—', '-')
            .replace('－', '-');

        var parts = head.split("-");
        if (parts.length >= 2) {
            if (isChina(parts[0], "")) {
                return parts.length >= 3
                    ? firstNonBlank(trimAdministrativeSuffix(parts[1]), parts[1])
                    : trimAdministrativeSuffix(parts[1]);
            }
            return parts[0];
        }

        var provinceMatcher = PROVINCE_PATTERN.matcher(head);
        if (provinceMatcher.find()) {
            return trimAdministrativeSuffix(provinceMatcher.group(1));
        }
        return trimAdministrativeSuffix(head);
    }

    /**
     * 解析 ip2region 的 region 文本。
     *
     * <p>ip2region 3.x 数据为「国家|省份|城市|ISP|国家代码」，例如「中国|广东省|广州市|电信|CN」；
     * 2.x 数据为「国家|区域|省份|城市|ISP」。境外数据里的国家名是英文（如 United States），
     * 这里用末段的国家代码转换出中文（「美国」）。
     */
    static String fromIp2Region(String region) {
        if (!StringUtils.hasText(region)) {
            return "";
        }

        var parts = region.split("\\|");
        if (parts.length < 5) {
            return "";
        }

        String country;
        String province;
        String city;
        String countryCode;

        var last = normalize(parts[parts.length - 1]);
        if (isCountryCode(last)) {
            country = normalize(parts[0]);
            province = normalize(parts[1]);
            city = normalize(parts[2]);
            countryCode = last;
        } else {
            country = normalize(parts[0]);
            province = normalize(parts[2]);
            city = normalize(parts[3]);
            countryCode = "";
        }

        if (isChina(country, countryCode)) {
            return firstNonBlank(trimAdministrativeSuffix(province),
                trimAdministrativeSuffix(city), "中国");
        }

        return firstNonBlank(localizedCountry(country, countryCode), country,
            trimAdministrativeSuffix(province), trimAdministrativeSuffix(city));
    }

    /**
     * 把国家代码转成中文名称，已经是中文的国家名则原样返回。
     */
    private static String localizedCountry(String country, String countryCode) {
        if (containsChinese(country)) {
            return country;
        }
        if (!isCountryCode(countryCode)) {
            return "";
        }

        try {
            var display = new Locale.Builder()
                .setRegion(countryCode.toUpperCase(Locale.ROOT))
                .build()
                .getDisplayCountry(Locale.SIMPLIFIED_CHINESE);
            if (!StringUtils.hasText(display)
                || display.equalsIgnoreCase(countryCode)
                || UNKNOWN_COUNTRY.equals(display)) {
                return "";
            }
            return display;
        } catch (IllformedLocaleException e) {
            return "";
        }
    }

    private static boolean containsChinese(String value) {
        if (!StringUtils.hasText(value)) {
            return false;
        }
        return value.codePoints().anyMatch(codePoint ->
            Character.UnicodeScript.of(codePoint) == Character.UnicodeScript.HAN);
    }
}
