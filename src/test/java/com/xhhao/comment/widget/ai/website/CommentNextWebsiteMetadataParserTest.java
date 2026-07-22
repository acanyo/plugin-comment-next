package com.xhhao.comment.widget.ai.website;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.net.URI;
import org.junit.jupiter.api.Test;

class CommentNextWebsiteMetadataParserTest {

    private final CommentNextWebsiteMetadataParser parser = new CommentNextWebsiteMetadataParser();

    @Test
    void prefersOpenGraphMetadataAndNormalizesWhitespace() {
        var website = URI.create("https://example.com/");
        var page = new CommentNextFetchedPage(
            website,
            """
                <!doctype html>
                <html>
                  <head>
                    <title>Fallback title</title>
                    <meta name="description" content="Fallback description">
                    <meta property="og:title" content="  Casino   Promotion  ">
                    <meta property="og:description" content="Bonus&#10;for new users">
                  </head>
                </html>
                """
        );

        var metadata = parser.parse(website, page, "实时网页");

        assertEquals("Casino Promotion", metadata.title());
        assertEquals("Bonus for new users", metadata.description());
        assertEquals("实时网页", metadata.source());
        assertTrue(metadata.available());
    }

    @Test
    void fallsBackToHtmlTitleAndMetaDescription() {
        var website = URI.create("https://example.com/");
        var page = new CommentNextFetchedPage(
            website,
            """
                <html>
                  <head>
                    <title>Personal Blog</title>
                    <meta name="description" content="Notes about software development">
                  </head>
                </html>
                """
        );

        var metadata = parser.parse(website, page, "Wayback 快照");

        assertEquals("Personal Blog", metadata.title());
        assertEquals("Notes about software development", metadata.description());
        assertEquals("Wayback 快照", metadata.source());
    }

    @Test
    void limitsMetadataBeforeSendingItToAi() {
        var website = URI.create("https://example.com/");
        var page = new CommentNextFetchedPage(
            website,
            "<title>" + "广".repeat(400) + "</title>"
                + "<meta name=description content='" + "告".repeat(900) + "'>"
        );

        var metadata = parser.parse(website, page, "实时网页");

        assertEquals(300, metadata.title().codePointCount(0, metadata.title().length()));
        assertEquals(800, metadata.description().codePointCount(0, metadata.description().length()));
    }
}
