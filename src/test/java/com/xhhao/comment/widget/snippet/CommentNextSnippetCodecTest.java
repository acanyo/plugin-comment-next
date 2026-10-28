package com.xhhao.comment.widget.snippet;

import static org.junit.jupiter.api.Assertions.*;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import org.junit.jupiter.api.Test;
import run.halo.app.core.extension.content.Comment;

class CommentNextSnippetCodecTest {
    @Test
    void readsOnlyValidAnchorsThatMatchTheVisibleQuote() {
        var json = "{\"version\":1,\"exact\":\"原文\",\"prefix\":\"前文\",\"suffix\":\"后文\",\"start\":2}";
        var comment = comment(json, "<blockquote><p>原文</p></blockquote><p>评论</p>");
        var anchor = CommentNextSnippetCodec.read(comment).orElseThrow();
        assertEquals("原文", anchor.exact());
        assertEquals(64, CommentNextSnippetCodec.key(anchor).length());
        comment.getSpec().setContent("<p>完全不同的内容</p>");
        assertTrue(CommentNextSnippetCodec.read(comment).isEmpty());
    }

    @Test
    void toleratesOrdinaryLegacyAndMalformedComments() {
        var comment = comment("invalid-json", "<blockquote>原文</blockquote>");
        assertTrue(CommentNextSnippetCodec.read(comment).isEmpty());
        comment.getSpec().setRaw("<blockquote>普通引用</blockquote>");
        assertTrue(CommentNextSnippetCodec.read(comment).isEmpty());
        comment.getSpec().setRaw("<!--comment-next-snippet:%XX-->");
        assertTrue(CommentNextSnippetCodec.read(comment).isEmpty());
        assertTrue(CommentNextSnippetCodec.read(new Comment()).isEmpty());
    }

    @Test
    void rejectsOversizedAndUnsupportedAnchors() {
        var json = "{\"version\":1,\"exact\":\"" + "字".repeat(1201) + "\",\"prefix\":\"\",\"suffix\":\"\",\"start\":0}";
        assertTrue(CommentNextSnippetCodec.read(comment(json, "<blockquote>" + "字".repeat(1201) + "</blockquote>")).isEmpty());
        json = "{\"version\":2,\"exact\":\"原文\",\"prefix\":\"\",\"suffix\":\"\",\"start\":0}";
        assertTrue(CommentNextSnippetCodec.read(comment(json,"<blockquote>原文</blockquote>")).isEmpty());
    }

    private Comment comment(String json, String content) {
        var comment = new Comment();
        var spec = new Comment.CommentSpec();
        spec.setRaw("<!--comment-next-snippet:" + URLEncoder.encode(json, StandardCharsets.UTF_8) + "-->" + content);
        spec.setContent(content);
        comment.setSpec(spec);
        return comment;
    }
}
