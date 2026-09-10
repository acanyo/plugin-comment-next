package com.xhhao.comment.widget.search;

import java.util.Locale;
import org.jsoup.Jsoup;
import org.springframework.util.StringUtils;

record CommentNextCommentSearchCriteria(
    Target target,
    Status status,
    String keyword,
    String email,
    String ipAddress,
    String author,
    String content,
    String subject
) {

    CommentNextCommentSearchCriteria {
        target = target == null ? Target.ALL : target;
        status = status == null ? Status.ALL : status;
        keyword = normalize(keyword);
        email = normalize(email);
        ipAddress = normalize(ipAddress);
        author = normalize(author);
        content = normalize(content);
        subject = normalize(subject);
    }

    boolean matches(CommentNextCommentSearchItem item) {
        return target.matches(item)
            && status.matches(item)
            && matchesKeyword(item)
            && contains(item.email(), email)
            && contains(item.ipAddress(), ipAddress)
            && matchesAuthor(item)
            && matchesContent(item)
            && contains(item.subject(), subject);
    }

    private boolean matchesKeyword(CommentNextCommentSearchItem item) {
        if (!StringUtils.hasText(keyword)) {
            return true;
        }
        return contains(item.name(), keyword)
            || contains(item.parentName(), keyword)
            || contains(item.subject(), keyword)
            || contains(item.authorName(), keyword)
            || contains(item.email(), keyword)
            || contains(item.username(), keyword)
            || contains(item.ipAddress(), keyword)
            || contains(plainText(item.content()), keyword)
            || contains(item.userAgent(), keyword);
    }

    private boolean matchesAuthor(CommentNextCommentSearchItem item) {
        if (!StringUtils.hasText(author)) {
            return true;
        }
        return contains(item.authorName(), author)
            || contains(item.email(), author)
            || contains(item.username(), author);
    }

    private boolean matchesContent(CommentNextCommentSearchItem item) {
        return !StringUtils.hasText(content)
            || contains(plainText(item.content()), content);
    }

    private static boolean contains(String value, String query) {
        return !StringUtils.hasText(query)
            || normalize(value).contains(query);
    }

    private static String plainText(String html) {
        return StringUtils.hasText(html) ? Jsoup.parseBodyFragment(html).text() : "";
    }

    private static String normalize(String value) {
        return StringUtils.hasText(value)
            ? value.strip().toLowerCase(Locale.ROOT)
            : "";
    }

    enum Target {
        ALL,
        COMMENT,
        REPLY;

        static Target from(String value) {
            if ("comment".equalsIgnoreCase(value)) {
                return COMMENT;
            }
            if ("reply".equalsIgnoreCase(value)) {
                return REPLY;
            }
            return ALL;
        }

        boolean matches(CommentNextCommentSearchItem item) {
            return this == ALL || name().equalsIgnoreCase(item.targetType());
        }
    }

    enum Status {
        ALL,
        VISIBLE,
        PENDING,
        HIDDEN;

        static Status from(String value) {
            if ("visible".equalsIgnoreCase(value)) {
                return VISIBLE;
            }
            if ("pending".equalsIgnoreCase(value)) {
                return PENDING;
            }
            if ("hidden".equalsIgnoreCase(value)) {
                return HIDDEN;
            }
            return ALL;
        }

        boolean matches(CommentNextCommentSearchItem item) {
            return switch (this) {
                case ALL -> true;
                case VISIBLE -> item.approved() && !item.hidden();
                case PENDING -> !item.approved() && !item.hidden();
                case HIDDEN -> item.hidden();
            };
        }
    }
}
