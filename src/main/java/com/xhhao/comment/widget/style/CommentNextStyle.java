package com.xhhao.comment.widget.style;

import com.fasterxml.jackson.databind.JsonNode;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.regex.Pattern;

public record CommentNextStyle(
    String lightAccent,
    String darkAccent,
    Integer radius,
    String density,
    String listStyle
) {
    static final CommentNextStyle DEFAULT = new CommentNextStyle(null, null, null, null, null);
    private static final Set<String> FIELDS = Set.of(
        "lightAccent", "darkAccent", "radius", "density", "listStyle");
    private static final List<String> DENSITIES = List.of("compact", "spacious");
    private static final List<String> LIST_STYLES = List.of("flat", "cards");
    private static final Pattern HEX_COLOR = Pattern.compile("#[0-9a-fA-F]{6}");

    public CommentNextStyle {
        lightAccent = color(lightAccent);
        darkAccent = color(darkAccent);
        if (radius != null && (radius < 0 || radius > 24)) {
            throw new IllegalArgumentException("圆角须为 0 到 24 之间的整数。");
        }
        requireOption(density, DENSITIES);
        requireOption(listStyle, LIST_STYLES);
    }

    static CommentNextStyle read(JsonNode node) {
        return DEFAULT.apply(node);
    }

    CommentNextStyle apply(JsonNode patch) {
        if (patch == null || !patch.isObject()) {
            throw new IllegalArgumentException("请提供有效的样式。");
        }
        patch.fieldNames().forEachRemaining(field -> {
            if (!FIELDS.contains(field)) {
                throw new IllegalArgumentException("样式包含不受支持的字段。");
            }
        });
        return new CommentNextStyle(
            string(patch, "lightAccent", lightAccent),
            string(patch, "darkAccent", darkAccent),
            integer(patch, "radius", radius),
            string(patch, "density", density),
            string(patch, "listStyle", listStyle));
    }

    static Map<String, Object> patchSchema() {
        return patchSchema(Map.of("type", List.of("integer", "null"), "minimum", 0, "maximum", 24));
    }

    static Map<String, Object> mcpPatchSchema() {
        // MCP Server 1.3.0 cannot load numeric bound validators on Halo 2.27.
        // The style constructor still checks the range for every save.
        return patchSchema(Map.of("type", List.of("integer", "null"), "description", "0 到 24 像素。"));
    }

    private static Map<String, Object> patchSchema(Map<String, Object> radius) {
        var color = Map.of("type", List.of("string", "null"), "pattern", HEX_COLOR.pattern());
        return Map.of("type", "object", "additionalProperties", false, "properties", Map.of(
            "lightAccent", color,
            "darkAccent", color,
            "radius", radius,
            "density", Map.of("type", List.of("string", "null"), "enum",
                java.util.Arrays.asList("compact", "spacious", null)),
            "listStyle", Map.of("type", List.of("string", "null"), "enum",
                java.util.Arrays.asList("flat", "cards", null))));
    }

    private static String color(String value) {
        if (value == null) {
            return null;
        }
        if (!HEX_COLOR.matcher(value).matches()) {
            throw new IllegalArgumentException("主色须为六位 HEX 颜色，例如 #356249。");
        }
        return value.toLowerCase(Locale.ROOT);
    }

    private static void requireOption(String value, List<String> allowed) {
        if (value != null && !allowed.contains(value)) {
            throw new IllegalArgumentException("请选择工作台提供的间距或列表样式。");
        }
    }

    private static String string(JsonNode patch, String field, String fallback) {
        if (!patch.has(field)) {
            return fallback;
        }
        var node = patch.get(field);
        if (node.isNull()) {
            return null;
        }
        if (!node.isTextual()) {
            throw new IllegalArgumentException("样式字段格式有误。");
        }
        return node.asText();
    }

    private static Integer integer(JsonNode patch, String field, Integer fallback) {
        if (!patch.has(field)) {
            return fallback;
        }
        var node = patch.get(field);
        if (node.isNull()) {
            return null;
        }
        if (!node.isIntegralNumber() || !node.canConvertToInt()) {
            throw new IllegalArgumentException("圆角须为整数。");
        }
        return node.intValue();
    }
}
