package com.xhhao.comment.widget.iplocation;

import java.util.Arrays;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.regex.Pattern;
import org.springframework.util.StringUtils;

/**
 * 将属地接口返回的地区名规范成展示文本。
 */
final class CommentNextIpLocationText {

    private static final int MAX_LOCATION_LENGTH = 48;

    private static final Pattern PROVINCE_PATTERN =
        Pattern.compile("^(.{1,10}?(?:省|市|自治区|特别行政区))");

    private static final Pattern LOCATION_LABEL =
        Pattern.compile("\\p{L}[\\p{L}\\p{M}\\p{Zs}\\p{Pd}.,'’·()]*");

    private static final List<String> CHINA_NAMES = List.of("中国", "china", "中国大陆");

    private static final List<String> CHINESE_COUNTRY_NAMES = Arrays.stream(Locale.getISOCountries())
        .map(code -> Locale.of("", code).getDisplayCountry(Locale.SIMPLIFIED_CHINESE))
        .filter(name -> !isChina(name, ""))
        .sorted(Comparator.comparingInt(String::length).reversed())
        .toList();

    private static final String EMPTY_REGION_PART = "0";

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

    static String displayLabel(String value) {
        var label = normalize(value);
        return LOCATION_LABEL.matcher(label).matches() ? label : "";
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
        var normalizedCountry = country.strip().toLowerCase(Locale.ROOT);
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

        // Some providers append a city or network operator without a separator.
        for (var country : CHINESE_COUNTRY_NAMES) {
            if (head.startsWith(country)) {
                return country;
            }
        }

        var provinceMatcher = PROVINCE_PATTERN.matcher(head);
        if (provinceMatcher.find()) {
            return trimAdministrativeSuffix(provinceMatcher.group(1));
        }
        return trimAdministrativeSuffix(head);
    }
}
