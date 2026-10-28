package com.xhhao.comment.widget.comment;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
import com.xhhao.comment.utils.JsonUtils;
import com.xhhao.comment.widget.SettingConfigGetter;
import com.xhhao.comment.widget.snippet.CommentNextSnippetAnchor;
import com.xhhao.comment.widget.snippet.CommentNextSnippetCodec;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.reactive.function.server.MockServerRequest;
import reactor.core.publisher.Mono;
import run.halo.app.core.extension.content.Comment;
import run.halo.app.extension.Metadata;

class CommentNextSnippetServiceTest {
    private final CommentNextCommentService comments = mock(CommentNextCommentService.class);
    private final CommentNextAuthorService authors = mock(CommentNextAuthorService.class);
    private final CommentNextCommentMapper mapper = mock(CommentNextCommentMapper.class);
    private final SettingConfigGetter settings = mock(SettingConfigGetter.class);
    private final CommentNextSnippetService service = new CommentNextSnippetService(comments, authors, mapper, settings);
    private final CommentNextSnippetAnchor anchor = new CommentNextSnippetAnchor(1, "原文", "", "", 0);

    @BeforeEach
    void setUp() {
        when(settings.getBasicConfig()).thenReturn(Mono.just(new SettingConfigGetter.BasicConfig()));
        when(authors.badgeContext()).thenReturn(Mono.just(new CommentNextBadgeContext(null, null)));
        when(mapper.toCommentNode(any(), any(), anyInt())).thenAnswer(invocation -> {
            Comment comment = invocation.getArgument(0);
            return Mono.just(JsonUtils.createObjectMapper().createObjectNode().put("id",comment.getMetadata().getName()));
        });
    }

    @Test
    void countsAllVisibleSnippetCommentsAndPaginatesTheirContentsIndependently() {
        var items = new ArrayList<Comment>();
        for (int i = 0; i < 7; i++) items.add(comment("comment-" + i));
        var ordinary = comment("ordinary");
        ordinary.getSpec().setRaw("普通评论");
        items.add(ordinary);
        when(comments.visibleComments(any())).thenReturn(Mono.just(items));
        var summary = service.list(query(), "", 1).block();
        assertEquals(1, summary.path("threads").size());
        assertEquals(7, summary.path("threads").get(0).path("total").asInt());
        verifyNoInteractions(mapper);
        String key = CommentNextSnippetCodec.key(anchor);
        var first = service.list(query(), key, 1).block();
        assertEquals(5, first.path("items").size());
        assertEquals("comment-6", first.path("items").get(0).path("id").asText());
        assertTrue(first.path("hasNext").asBoolean());
        var second = service.list(query(), key, 2).block();
        assertEquals(2, second.path("items").size());
        assertFalse(second.path("hasNext").asBoolean());
        assertEquals(0, service.list(query(), "missing", 1).block().path("total").asInt());
    }

    @Test
    void disabledFeatureDoesNotReadComments() {
        var config = new SettingConfigGetter.BasicConfig();
        config.setEnableSnippetComment(false);
        when(settings.getBasicConfig()).thenReturn(Mono.just(config));
        assertEquals(0, service.list(query(), "", 1).block().path("threads").size());
        verifyNoInteractions(comments);
    }

    private CommentNextCommentQuery query() {
        return new CommentNextCommentQuery(MockServerRequest.builder().queryParam("group","content.halo.run")
            .queryParam("kind","Post").queryParam("version","v1alpha1").queryParam("name","post").build());
    }

    private Comment comment(String name) {
        var comment = new Comment();
        var metadata = new Metadata();
        metadata.setName(name);
        comment.setMetadata(metadata);
        var spec = new Comment.CommentSpec();
        String json;
        try { json = JsonUtils.createObjectMapper().writeValueAsString(anchor); }
        catch (Exception e) { throw new IllegalStateException(e); }
        spec.setRaw("<!--comment-next-snippet:" + URLEncoder.encode(json,StandardCharsets.UTF_8) + "--><blockquote>原文</blockquote>评论");
        spec.setContent("<blockquote>原文</blockquote>评论");
        comment.setSpec(spec);
        return comment;
    }
}
