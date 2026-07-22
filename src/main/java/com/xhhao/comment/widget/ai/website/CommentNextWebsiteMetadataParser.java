package com.xhhao.comment.widget.ai.website;

import com.xhhao.comment.widget.ai.ConditionalOnHaloAiFoundation;
import java.net.URI;
import java.util.stream.Collectors;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

@Component
@ConditionalOnHaloAiFoundation
class CommentNextWebsiteMetadataParser {
    private static final int MAX_TITLE_LENGTH = 300;

    private static final int MAX_DESCRIPTION_LENGTH = 800;

    CommentNextWebsiteMetadata parse(URI website, CommentNextFetchedPage page, String source) {
        var document = Jsoup.parse(page.html(), page.finalUri().toASCIIString());
        var title = firstText(
            metaContent(document, "meta[property=og:title]"),
            metaContent(document, "meta[name=twitter:title]"),
            document.title()
        );
        var description = firstText(
            metaContent(document, "meta[property=og:description]"),
            metaContent(document, "meta[name=description]"),
            metaContent(document, "meta[name=twitter:description]")
        );
        return new CommentNextWebsiteMetadata(
            website.toASCIIString(),
            limit(normalize(title), MAX_TITLE_LENGTH),
            limit(normalize(description), MAX_DESCRIPTION_LENGTH),
            source
        );
    }

    private String metaContent(Document document, String selector) {
        var element = document.selectFirst(selector);
        return element == null ? "" : element.attr("content");
    }

    private String firstText(String... values) {
        for (var value : values) {
            if (StringUtils.hasText(value)) {
                return value;
            }
        }
        return "";
    }

    private String normalize(String value) {
        if (!StringUtils.hasText(value)) {
            return "";
        }
        return value.codePoints()
            .mapToObj(codePoint -> {
                if (Character.isWhitespace(codePoint) || Character.isSpaceChar(codePoint)) {
                    return " ";
                }
                return Character.isISOControl(codePoint)
                    ? ""
                    : new String(Character.toChars(codePoint));
            })
            .collect(Collectors.joining())
            .replaceAll(" +", " ")
            .strip();
    }

    private String limit(String value, int maxCodePoints) {
        if (value.codePointCount(0, value.length()) <= maxCodePoints) {
            return value;
        }
        var end = value.offsetByCodePoints(0, maxCodePoints);
        return value.substring(0, end).strip();
    }
}
