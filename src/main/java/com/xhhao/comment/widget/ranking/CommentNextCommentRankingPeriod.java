package com.xhhao.comment.widget.ranking;

import java.time.Instant;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.util.Locale;

/** Time windows supported by the public comment ranking component. */
public enum CommentNextCommentRankingPeriod {
    ALL,
    WEEK,
    MONTH,
    QUARTER,
    YEAR;

    public static CommentNextCommentRankingPeriod from(String value) {
        if (value == null || value.isBlank()) {
            return ALL;
        }

        try {
            return valueOf(value.trim().toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException ignored) {
            return ALL;
        }
    }

    public Instant start(ZoneId zoneId) {
        if (this == ALL) {
            return Instant.EPOCH;
        }

        var now = ZonedDateTime.now(zoneId);
        var start = switch (this) {
            case WEEK -> now.toLocalDate().minusDays(now.getDayOfWeek().getValue() - 1L)
                .atStartOfDay(zoneId);
            case MONTH -> now.withDayOfMonth(1).toLocalDate().atStartOfDay(zoneId);
            case QUARTER -> {
                var firstMonth = ((now.getMonthValue() - 1) / 3) * 3 + 1;
                yield now.withMonth(firstMonth).withDayOfMonth(1).toLocalDate()
                    .atStartOfDay(zoneId);
            }
            case YEAR -> now.withDayOfYear(1).toLocalDate().atStartOfDay(zoneId);
            case ALL -> now;
        };
        return start.toInstant();
    }

    public String value() {
        return name().toLowerCase(Locale.ROOT);
    }
}
