package com.xhhao.comment.widget.security;

import java.util.List;
import org.springframework.util.StringUtils;

public record CommentNextSecurityReviewResult(
    boolean intercepted,
    List<String> categories,
    double confidence,
    String reason,
    List<String> labels
) {

    public CommentNextSecurityReviewResult {
        categories = cleanList(categories);
        labels = cleanList(labels);
        confidence = Math.max(0D, Math.min(confidence, 1D));
        reason = StringUtils.hasText(reason) ? reason.strip() : "";
    }

    public boolean matchesThreshold(double threshold) {
        if (!intercepted) {
            return false;
        }
        var normalizedThreshold = Math.max(0D, Math.min(threshold, 1D));
        return confidence <= 0D || confidence >= normalizedThreshold;
    }

    private static List<String> cleanList(List<String> values) {
        if (values == null || values.isEmpty()) {
            return List.of();
        }
        return values.stream()
            .filter(StringUtils::hasText)
            .map(String::strip)
            .distinct()
            .toList();
    }
}
